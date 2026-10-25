package com.deathrecap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws HP and prayer over the recap window: a filled HP line over a red low-HP band, a bar for every
 * hit (taller for bigger hits, stacked when they land together, numbered when there's room), a dot
 * for food and potions, a labelled line at the death or close call, a seconds axis and a legend.
 * Used by the side panel and the share card.
 */
final class HpGraph
{
	static final Color HP = new Color(0x4C, 0xC0, 0x4C);
	static final Color PRAYER = new Color(0x60, 0xB0, 0xFF);
	static final Color HIT = new Color(0xFF, 0x50, 0x50);
	static final Color FOOD = new Color(0xF0, 0xC0, 0x40);
	private static final Color BACKGROUND_TOP = new Color(0x24, 0x24, 0x24);
	private static final Color BACKGROUND_BOTTOM = new Color(0x16, 0x16, 0x16);
	private static final Color BORDER = new Color(0x40, 0x40, 0x40);
	private static final Color GRID = new Color(0xFF, 0xFF, 0xFF, 0x14);
	private static final Color LABEL = new Color(0xA0, 0xA0, 0xA0);
	private static final Color DANGER = new Color(0xFF, 0x30, 0x30, 0x22);
	/** The biggest hit's bar, as a share of the plot's height. */
	private static final double HIT_BAR_SHARE = 0.22;
	/** HP at or below this share of the maximum is shaded as dangerous. */
	private static final double DANGER_SHARE = 0.2;
	private static final double TICK_SECONDS = 0.6;
	private static final int[] AXIS_STEPS = {1, 2, 5, 10, 15, 30, 60};

	private HpGraph()
	{
	}

	static void paint(Graphics2D g, Rectangle area, DeathRecord record)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			float scale = scale(area);
			float arc = 6 * scale;
			RoundRectangle2D frame = new RoundRectangle2D.Double(area.x, area.y, area.width - 1, area.height - 1, arc, arc);
			g2.setPaint(new GradientPaint(0, area.y, BACKGROUND_TOP, 0, area.y + area.height, BACKGROUND_BOTTOM));
			g2.fill(frame);

			FontMetrics fm = g2.getFontMetrics();
			Rectangle plot = plot(area, fm);
			List<TickSnapshot> ticks = sorted(record);
			if (!ticks.isEmpty())
			{
				g2.clip(frame);
				draw(g2, area, plot, fm, scale, record, ticks);
				g2.setClip(null);
			}
			g2.setStroke(new BasicStroke(1f));
			g2.setColor(BORDER);
			g2.draw(frame);
		}
		finally
		{
			g2.dispose();
		}
	}

	private static void draw(Graphics2D g2, Rectangle area, Rectangle plot, FontMetrics fm, float scale, DeathRecord record, List<TickSnapshot> ticks)
	{
		double[] range = range(record, ticks);
		int maxHp = 1;
		int maxPrayer = 1;
		for (TickSnapshot t : ticks)
		{
			maxHp = Math.max(maxHp, Math.max(t.getMaxHp(), t.getHp()));
			maxPrayer = Math.max(maxPrayer, t.getPrayer());
		}
		int maxHit = 1;
		for (RecapEvent e : record.getEvents())
		{
			if (e.isDamage())
			{
				maxHit = Math.max(maxHit, e.getAmount());
			}
		}
		int deathTick = record.getDeathTick();
		boolean labels = plot.y > area.y + fm.getHeight();

		// Low-HP band and quarter lines.
		double dangerY = y(maxHp * DANGER_SHARE, maxHp, plot);
		g2.setColor(DANGER);
		g2.fill(new Rectangle.Double(area.x, dangerY, area.width, plot.y + plot.height - dangerY));
		g2.setStroke(new BasicStroke(1f));
		g2.setColor(GRID);
		for (int q = 1; q <= 4; q++)
		{
			double gy = y(maxHp * q / 4.0, maxHp, plot);
			g2.draw(new Line2D.Double(area.x, gy, area.x + area.width, gy));
		}

		// Seconds axis, counted from the death.
		double startSec = (range[0] - deathTick) * TICK_SECONDS;
		double endSec = (range[1] - deathTick) * TICK_SECONDS;
		int step = AXIS_STEPS[AXIS_STEPS.length - 1];
		for (int s : AXIS_STEPS)
		{
			if (plot.width * s / Math.max(0.1, endSec - startSec) >= fm.stringWidth("-00s") * 1.8)
			{
				step = s;
				break;
			}
		}
		for (int s = (int) Math.ceil(startSec / step) * step; s <= endSec; s += step)
		{
			double gx = x(deathTick + s / TICK_SECONDS, range, plot);
			g2.setColor(GRID);
			g2.draw(new Line2D.Double(gx, plot.y, gx, plot.y + plot.height));
			if (labels)
			{
				String text = s == 0 ? "0s" : (s > 0 ? "+" : "") + s + "s";
				int tx = (int) Math.round(gx - fm.stringWidth(text) / 2.0);
				tx = Math.max(area.x + 2, Math.min(area.x + area.width - 2 - fm.stringWidth(text), tx));
				g2.setColor(LABEL);
				g2.drawString(text, tx, plot.y + plot.height + Math.round(2 * scale) + fm.getAscent());
			}
		}

		// HP area, prayer line, HP line.
		Path2D hp = line(ticks, range, plot, maxHp, false);
		Path2D fill = new Path2D.Double(hp);
		fill.lineTo(x(ticks.get(ticks.size() - 1).getTick(), range, plot), plot.y + plot.height);
		fill.lineTo(x(ticks.get(0).getTick(), range, plot), plot.y + plot.height);
		fill.closePath();
		g2.setPaint(new GradientPaint(0, plot.y, alpha(HP, 0x70), 0, plot.y + plot.height, alpha(HP, 0x10)));
		g2.fill(fill);
		g2.setStroke(new BasicStroke(1f * scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(alpha(PRAYER, 0x90));
		g2.draw(line(ticks, range, plot, maxPrayer, true));
		g2.setStroke(new BasicStroke(2f * scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(HP);
		g2.draw(hp);
		// Each drop in HP drawn in red and each rise in yellow, so damage and healing stand out on the line.
		g2.setStroke(new BasicStroke(2.5f * scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (int i = 1; i < ticks.size(); i++)
		{
			int before = ticks.get(i - 1).getHp();
			int after = ticks.get(i).getHp();
			if (before != after)
			{
				double sx = x(ticks.get(i).getTick(), range, plot);
				g2.setColor(after < before ? HIT : FOOD);
				g2.draw(new Line2D.Double(sx, y(before, maxHp, plot), sx, y(after, maxHp, plot)));
			}
		}

		// Hits, stacked when several land on one tick, numbered when there's room.
		List<RecapEvent> events = new ArrayList<>(record.getEvents());
		events.sort(Comparator.comparingInt(RecapEvent::getTick));
		Map<Integer, Double> stacked = new HashMap<>();
		double barWidth = Math.max(2, 3 * scale);
		double labelRight = Double.NEGATIVE_INFINITY;
		RecapEvent blow = !record.isDeath() ? null : killingBlow(events, deathTick);
		for (RecapEvent e : events)
		{
			if (!e.isDamage())
			{
				continue;
			}
			double ex = x(e.getTick(), range, plot);
			double h = Math.max(2, (double) e.getAmount() / maxHit * plot.height * HIT_BAR_SHARE);
			double base = stacked.getOrDefault(e.getTick(), 0.0);
			stacked.put(e.getTick(), base + h + 1);
			double top = plot.y + plot.height - base - h;
			Color c = e.getType() == RecapEvent.Type.HIT ? HIT : DeathRecapPanel.DOT;
			g2.setColor(e == blow ? c.brighter() : alpha(c, 0xB0));
			g2.fill(new RoundRectangle2D.Double(ex - barWidth / 2, top, barWidth, h, barWidth / 2, barWidth / 2));
			if (e == blow)
			{
				g2.setStroke(new BasicStroke(1f));
				g2.setColor(Color.WHITE);
				g2.draw(new RoundRectangle2D.Double(ex - barWidth / 2 - 1, top - 1, barWidth + 2, h + 2, barWidth / 2, barWidth / 2));
			}
			String n = String.valueOf(e.getAmount());
			int nw = fm.stringWidth(n);
			// Kept inside the plot, so the killing blow at the right edge isn't cut off.
			double lx = Math.min(ex - nw / 2.0, plot.x + plot.width - nw - 2 * scale);
			double ly = top - 2 * scale;
			if (labels && lx > labelRight + 2 * scale && ly - fm.getAscent() > plot.y)
			{
				// A dark outline keeps the number readable where it crosses the HP line.
				float o = Math.max(1, scale);
				g2.setColor(new Color(0, 0, 0, 0xC0));
				for (int d = 0; d < 4; d++)
				{
					g2.drawString(n, (float) lx + (d == 0 ? -o : d == 1 ? o : 0), (float) ly + (d == 2 ? -o : d == 3 ? o : 0));
				}
				g2.setColor(e == blow ? Color.WHITE : alpha(c, 0xE0));
				g2.drawString(n, (float) lx, (float) ly);
				labelRight = lx + nw;
			}
		}

		// Food and potions: a dot at the top with a faint line down.
		float r = 3.5f * scale;
		for (RecapEvent e : events)
		{
			if (e.getType() != RecapEvent.Type.EAT && e.getType() != RecapEvent.Type.DRINK)
			{
				continue;
			}
			double ex = x(e.getTick(), range, plot);
			g2.setStroke(new BasicStroke(1f));
			g2.setColor(alpha(FOOD, 0x50));
			g2.draw(new Line2D.Double(ex, plot.y + r * 2, ex, plot.y + plot.height));
			g2.setColor(FOOD);
			g2.fill(new Ellipse2D.Double(ex - r, plot.y, r * 2, r * 2));
			g2.setColor(BACKGROUND_BOTTOM);
			g2.draw(new Ellipse2D.Double(ex - r, plot.y, r * 2, r * 2));
		}

		// The death or low point.
		double dx = x(deathTick, range, plot);
		Color marker = record.isCloseCall() ? FOOD : record.isClip() ? PRAYER : Color.WHITE;
		g2.setStroke(new BasicStroke(scale, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4 * scale, 3 * scale}, 0f));
		g2.setColor(marker);
		g2.draw(new Line2D.Double(dx, plot.y, dx, plot.y + plot.height));
		TickSnapshot at = at(ticks, deathTick);
		double py = y(at.getHp(), maxHp, plot);
		g2.setStroke(new BasicStroke(1.5f * scale));
		g2.setColor(marker);
		g2.fill(new Ellipse2D.Double(dx - r, py - r, r * 2, r * 2));
		g2.setColor(BACKGROUND_BOTTOM);
		g2.draw(new Ellipse2D.Double(dx - r, py - r, r * 2, r * 2));

		if (labels)
		{
			int pad = Math.round(4 * scale);
			int baseline = area.y + pad + fm.getAscent();
			String tag = record.isCloseCall() ? "Low point " + at.getHp() + " HP" : record.isClip() ? "Clip saved" : "Death";
			int tw = fm.stringWidth(tag);
			int tx = dx + pad + tw <= area.x + area.width - pad ? (int) dx + pad : (int) dx - pad - tw;
			g2.setColor(marker);
			g2.drawString(tag, tx, baseline);
			legend(g2, fm, area.x + pad, baseline, tx - pad, scale, maxHp);
		}
	}

	/** Colour swatches with names along the top, as many as fit before {@code right}. */
	private static void legend(Graphics2D g2, FontMetrics fm, int x, int baseline, int right, float scale, int maxHp)
	{
		String[] names = {"HP (max " + maxHp + ")", "Prayer", "Hit", "Food"};
		Color[] colours = {HP, PRAYER, HIT, FOOD};
		int swatch = Math.max(6, Math.round(fm.getAscent() * 0.7f));
		int gap = Math.round(8 * scale);
		for (int i = 0; i < names.length; i++)
		{
			int w = swatch + Math.round(3 * scale) + fm.stringWidth(names[i]);
			if (x + w > right)
			{
				return;
			}
			g2.setColor(colours[i]);
			g2.fill(new RoundRectangle2D.Double(x, baseline - swatch, swatch, swatch, swatch / 2.0, swatch / 2.0));
			g2.setColor(LABEL);
			g2.drawString(names[i], x + swatch + Math.round(3 * scale), baseline);
			x += w + gap;
		}
	}

	/** The snapshot shown at {@code px} in a graph painted into {@code area} with {@code fm}, for tooltips. */
	static TickSnapshot at(DeathRecord record, int px, Rectangle area, FontMetrics fm)
	{
		List<TickSnapshot> ticks = sorted(record);
		if (ticks.isEmpty() || area.width <= 0)
		{
			return null;
		}
		Rectangle plot = plot(area, fm);
		double[] range = range(record, ticks);
		double tick = range[0] + (double) (px - plot.x) * (range[1] - range[0]) / Math.max(1, plot.width) + 1e-9;
		return at(ticks, tick);
	}

	private static TickSnapshot at(List<TickSnapshot> ticks, double tick)
	{
		TickSnapshot found = ticks.get(0);
		for (TickSnapshot t : ticks)
		{
			if (t.getTick() > tick)
			{
				break;
			}
			found = t;
		}
		return found;
	}

	private static RecapEvent killingBlow(List<RecapEvent> sorted, int deathTick)
	{
		RecapEvent blow = null;
		for (RecapEvent e : sorted)
		{
			if (e.isDamage() && e.getTick() <= deathTick)
			{
				blow = e;
			}
		}
		return blow;
	}

	private static float scale(Rectangle area)
	{
		return Math.max(1f, Math.min(area.width / 320f, area.height / 110f));
	}

	/** Where the lines go: inside {@code area}, below the legend and above the axis when they fit. */
	private static Rectangle plot(Rectangle area, FontMetrics fm)
	{
		int pad = Math.round(4 * scale(area));
		int text = fm.getHeight();
		int top = area.y + pad + text + pad;
		int bottom = area.y + area.height - pad - text;
		if (bottom - top < text * 2)
		{
			return new Rectangle(area.x + pad, area.y + pad, Math.max(1, area.width - pad * 2), Math.max(1, area.height - pad * 2));
		}
		return new Rectangle(area.x + pad, top, area.width - pad * 2, bottom - top);
	}

	private static List<TickSnapshot> sorted(DeathRecord record)
	{
		List<TickSnapshot> ticks = new ArrayList<>(record.getTicks());
		ticks.sort(Comparator.comparingInt(TickSnapshot::getTick));
		return ticks;
	}

	/** First and last tick on the x axis. */
	private static double[] range(DeathRecord record, List<TickSnapshot> ticks)
	{
		int min = ticks.get(0).getTick();
		int max = Math.max(ticks.get(ticks.size() - 1).getTick(), record.getDeathTick());
		for (RecapEvent e : record.getEvents())
		{
			min = Math.min(min, e.getTick());
			max = Math.max(max, e.getTick());
		}
		return new double[]{min, max > min ? max : min + 1};
	}

	private static double x(double tick, double[] range, Rectangle plot)
	{
		return plot.x + (tick - range[0]) * plot.width / (range[1] - range[0]);
	}

	private static double y(double value, int max, Rectangle plot)
	{
		return plot.y + plot.height - plot.height * Math.max(0, Math.min(1.0, value / max));
	}

	/** A step line: each tick's value holds until the next tick. */
	private static Path2D line(List<TickSnapshot> ticks, double[] range, Rectangle plot, int max, boolean prayer)
	{
		Path2D path = new Path2D.Double();
		for (int i = 0; i < ticks.size(); i++)
		{
			TickSnapshot t = ticks.get(i);
			double y = y(prayer ? t.getPrayer() : t.getHp(), max, plot);
			double x = x(t.getTick(), range, plot);
			if (i == 0)
			{
				path.moveTo(x, y);
			}
			else
			{
				path.lineTo(x, path.getCurrentPoint().getY());
				path.lineTo(x, y);
			}
		}
		return path;
	}

	private static Color alpha(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}
}
