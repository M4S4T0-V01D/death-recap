package com.deathrecap;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One image to share a death, in one of several {@link ShareCardLayout layouts}: the screenshot at full
 * size with the killer, the killing blow, what was lost, damage by source, the main findings, the items
 * lost and the HP graph. Every layout is laid out twice: once to measure, once to draw.
 */
final class ShareCard
{
	private static final Color BACKGROUND = new Color(0x24, 0x24, 0x24);
	private static final Color PANEL = new Color(0x1E, 0x1E, 0x1E);
	private static final Color TEXT = new Color(0xE6, 0xE6, 0xE6);
	private static final Color MUTED = new Color(0x9A, 0x9A, 0x9A);
	private static final Color BAR = new Color(0x48, 0x48, 0x48);
	private static final int MIN_WIDTH = 900;
	private static final int COMPACT_WIDTH = 1100;
	private static final int SOURCES = 5;
	private static final int FINDINGS = 5;
	private static final int ITEMS = 6;
	private static final String CREDIT = "Death Recap for RuneLite";

	private ShareCard()
	{
	}

	/** One part of the card, drawn at (x, y) within width w; returns its height, 0 if it has nothing to show. */
	private interface Block
	{
		int paint(int x, int y, int w);
	}

	/**
	 * @param screenshot the death screenshot, or null to draw the recap alone
	 * @param font text font, scaled to the card's size
	 * @param bold title font, scaled to the card's size
	 */
	static BufferedImage render(BufferedImage screenshot, DeathRecord record, RecapAnalyzer.Summary summary, Font font, Font bold, ShareCardLayout layout)
	{
		if (screenshot == null || layout == ShareCardLayout.COMPACT)
		{
			return layout == ShareCardLayout.TALL ? tall(null, record, summary, font, bold) : wide(null, record, summary, font, bold);
		}
		switch (layout)
		{
			case SIDE:
				return side(screenshot, record, summary, font, bold);
			case TALL:
				return tall(screenshot, record, summary, font, bold);
			default:
				return wide(screenshot, record, summary, font, bold);
		}
	}

	static BufferedImage render(BufferedImage screenshot, DeathRecord record, RecapAnalyzer.Summary summary, Font font, Font bold)
	{
		return render(screenshot, record, summary, font, bold, ShareCardLayout.WIDE);
	}

	/** The screenshot with three columns underneath: who and how much, what happened, the graph. */
	private static BufferedImage wide(BufferedImage shot, DeathRecord record, RecapAnalyzer.Summary summary, Font font, Font bold)
	{
		int width = shot != null ? Math.max(MIN_WIDTH, shot.getWidth()) : COMPACT_WIDTH;
		float k = clamp(width / 1000f, 1f, 2f);
		Painter m = Painter.measure(record, summary, font, bold, k);
		int pad = m.pad;
		int colGap = pad * 2;
		int inner = width - pad * 2 - colGap * 2;
		int w1 = (int) (inner * 0.30);
		int w2 = (int) (inner * 0.33);
		int w3 = inner - w1 - w2;
		int content = Math.max(Math.round(240 * k), Math.max(
			m.column(0, 0, w1, m::header, m::loss, m::sources),
			m.column(0, 0, w2, m::tiles, m::findings, m::items)));
		int shotHeight = shot != null ? shot.getHeight() : 0;
		int stripHeight = pad + content + m.creditHeight();

		BufferedImage out = new BufferedImage(width, shotHeight + stripHeight, BufferedImage.TYPE_INT_RGB);
		Painter p = Painter.draw(out, record, summary, font, bold, k);
		try
		{
			p.fill(BACKGROUND, 0, 0, width, out.getHeight());
			if (shot != null)
			{
				p.g.drawImage(shot, (width - shot.getWidth()) / 2, 0, null);
			}
			int top = shotHeight + pad;
			p.column(pad, top, w1, p::header, p::loss, p::sources);
			p.column(pad + w1 + colGap, top, w2, p::tiles, p::findings, p::items);
			p.graph(width - pad - w3, top, w3, content);
			p.credit(width, out.getHeight());
		}
		finally
		{
			p.g.dispose();
		}
		return out;
	}

	/** The screenshot with everything in one column to its right. */
	private static BufferedImage side(BufferedImage shot, DeathRecord record, RecapAnalyzer.Summary summary, Font font, Font bold)
	{
		float k = clamp(shot.getHeight() / 720f, 1f, 2f);
		Painter m = Painter.measure(record, summary, font, bold, k);
		int pad = m.pad;
		int panelWidth = Math.round(470 * k);
		int inner = panelWidth - pad * 2;
		int graphHeight = Math.round(190 * k);
		Block graph = (x, y, w) -> m.graph(x, y, w, graphHeight);
		int content = m.column(0, 0, inner, m::header, m::loss, m::tiles, graph, m::sources, m::findings, m::items);
		int width = shot.getWidth() + panelWidth;
		int height = Math.max(shot.getHeight(), pad + content + m.creditHeight());

		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Painter p = Painter.draw(out, record, summary, font, bold, k);
		try
		{
			p.fill(BACKGROUND, 0, 0, width, height);
			p.fill(PANEL, shot.getWidth(), 0, panelWidth, height);
			p.g.drawImage(shot, 0, (height - shot.getHeight()) / 2, null);
			p.column(shot.getWidth() + pad, pad, inner, p::header, p::loss, p::tiles, (x, y, w) -> p.graph(x, y, w, graphHeight),
				p::sources, p::findings, p::items);
			p.credit(width, height);
		}
		finally
		{
			p.g.dispose();
		}
		return out;
	}

	/** The screenshot on top, then the headline, a full-width graph and two columns, in larger text. */
	private static BufferedImage tall(BufferedImage shot, DeathRecord record, RecapAnalyzer.Summary summary, Font font, Font bold)
	{
		int width = shot != null ? Math.max(1080, shot.getWidth()) : 1080;
		float k = clamp(width / 850f, 1.2f, 2.4f);
		Painter m = Painter.measure(record, summary, font, bold, k);
		int pad = m.pad;
		int inner = width - pad * 2;
		int colWidth = (inner - pad * 2) / 2;
		int graphHeight = Math.round(230 * k);
		int header = m.column(0, 0, inner, m::header, m::loss, m::tiles);
		int columns = Math.max(m.column(0, 0, colWidth, m::sources, m::items), m.column(0, 0, colWidth, m::findings));
		int shotHeight = shot != null ? shot.getHeight() : 0;
		int height = shotHeight + pad + header + m.gap + graphHeight + (columns > 0 ? m.gap + columns : 0) + m.creditHeight();

		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Painter p = Painter.draw(out, record, summary, font, bold, k);
		try
		{
			p.fill(BACKGROUND, 0, 0, width, height);
			if (shot != null)
			{
				p.g.drawImage(shot, (width - shot.getWidth()) / 2, 0, null);
			}
			int y = shotHeight + pad;
			p.column(pad, y, inner, p::header, p::loss, p::tiles);
			y += header + p.gap;
			p.graph(pad, y, inner, graphHeight);
			y += graphHeight + p.gap;
			p.column(pad, y, colWidth, p::sources, p::items);
			p.column(width - pad - colWidth, y, colWidth, p::findings);
			p.credit(width, height);
		}
		finally
		{
			p.g.dispose();
		}
		return out;
	}

	/** Lays out and, unless measuring, draws the card's blocks. */
	private static final class Painter
	{
		final Graphics2D g;
		final boolean draw;
		final DeathRecord record;
		final RecapAnalyzer.Summary summary;
		final ItemsOnDeath.Result items;
		final Font body;
		final Font small;
		final Font title;
		final Font heading;
		final float k;
		final int pad;
		final int gap;

		private Painter(Graphics2D g, boolean draw, DeathRecord record, RecapAnalyzer.Summary summary, Font font, Font bold, float k)
		{
			this.g = g;
			this.draw = draw;
			this.record = record;
			this.summary = summary;
			this.items = !record.isDeath() || record.getState() == null ? null : ItemsOnDeath.compute(record.getState());
			this.k = k;
			body = font.deriveFont(16 * k);
			small = font.deriveFont(14 * k);
			title = bold.deriveFont(26 * k);
			heading = bold.deriveFont(17 * k);
			pad = Math.round(18 * k);
			gap = Math.round(14 * k);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		}

		static Painter measure(DeathRecord record, RecapAnalyzer.Summary summary, Font font, Font bold, float k)
		{
			return new Painter(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics(), false, record, summary, font, bold, k);
		}

		static Painter draw(BufferedImage out, DeathRecord record, RecapAnalyzer.Summary summary, Font font, Font bold, float k)
		{
			return new Painter(out.createGraphics(), true, record, summary, font, bold, k);
		}

		/** Blocks stacked top to bottom with a gap between those that show something. */
		int column(int x, int y, int w, Block... blocks)
		{
			int h = 0;
			for (Block b : blocks)
			{
				int space = h > 0 ? gap : 0;
				int bh = b.paint(x, y + h + space, w);
				if (bh > 0)
				{
					h += space + bh;
				}
			}
			return h;
		}

		/** Who did it in large type, then their level and weapon, the killing blow, and when and where. */
		int header(int x, int y, int w)
		{
			Color colour = record.isCloseCall() ? HpGraph.FOOD : record.isClip() ? HpGraph.PRAYER : HpGraph.HIT;
			String name = summary.getKiller();
			boolean fought = summary.getTotalDamage() > 0 && name != null;
			String head = record.isClip() ? (fought ? "Clip, fighting " + name : "Clip")
				: (record.isCloseCall() ? "Close call against " : "Killed by ") + name;
			int h = lines(head, title, colour, x, y, w);
			String who = fought ? who(name) : "";
			if (!who.isEmpty())
			{
				h += lines(who, heading, TEXT, x, y + h, w);
			}
			String line = headline(record, summary);
			if (!line.isEmpty())
			{
				h += lines(line, body, Color.LIGHT_GRAY, x, y + h, w);
			}
			h += put(RecapText.date(record.getTimestamp()) + " · " + RecapText.place(record), body, MUTED, x, y + h, w);
			return h;
		}

		/** "Level 126 player · Armadyl godsword", or just the level for NPCs; empty when nothing is known. */
		private String who(String name)
		{
			Attacker a = record.attacker(name);
			List<String> parts = new ArrayList<>();
			if (a != null && a.getCombatLevel() > 0)
			{
				parts.add("Level " + a.getCombatLevel() + (a.isPlayer() ? " player" : ""));
			}
			else if (a != null && a.isPlayer())
			{
				parts.add("Player");
			}
			String weapon = RecapText.killerWeapon(record, summary);
			if (weapon != null)
			{
				parts.add(weapon);
			}
			return String.join(" · ", parts);
		}

		/** A row of key numbers: damage taken, damage per second, biggest hit and food eaten. */
		int tiles(int x, int y, int w)
		{
			if (summary.getTotalDamage() == 0)
			{
				return 0;
			}
			List<String[]> list = new ArrayList<>();
			int deathTick = record.getDeathTick();
			int first = Integer.MAX_VALUE;
			int biggest = 0;
			int eaten = 0;
			for (RecapEvent e : record.getEvents())
			{
				if (e.getTick() > deathTick)
				{
					continue;
				}
				if (e.isDamage())
				{
					first = Math.min(first, e.getTick());
					biggest = Math.max(biggest, e.getAmount());
				}
				else if (e.getType() == RecapEvent.Type.EAT || e.getType() == RecapEvent.Type.DRINK)
				{
					eaten++;
				}
			}
			list.add(new String[]{String.valueOf(summary.getTotalDamage()), "damage"});
			// Counted the same way as the damage finding: every tick from the first hit to the death.
			int span = deathTick - first + 1;
			if (first != Integer.MAX_VALUE && span >= 3)
			{
				list.add(new String[]{String.format(Locale.ROOT, "%.1f", summary.getTotalDamage() / (span * 0.6)), "dmg / sec"});
			}
			list.add(new String[]{String.valueOf(biggest), "max hit"});
			if (record.isCloseCall())
			{
				int lowest = record.getTicks().stream().mapToInt(TickSnapshot::getHp).filter(v -> v > 0).min().orElse(0);
				list.add(new String[]{String.valueOf(lowest), "lowest HP"});
			}
			else
			{
				list.add(new String[]{String.valueOf(eaten), "eaten"});
			}

			FontMetrics fm = g.getFontMetrics(small);
			int tileGap = Math.round(6 * k);
			int tw = (w - tileGap * (list.size() - 1)) / list.size();
			int inner = Math.round(6 * k);
			int th = inner * 2 + g.getFontMetrics(title).getHeight() + fm.getHeight();
			for (int i = 0; i < list.size(); i++)
			{
				int tx = x + i * (tw + tileGap);
				if (draw)
				{
					g.setColor(PANEL);
					g.fillRoundRect(tx, y, tw, th, inner, inner);
				}
				Color c = i == 0 ? HpGraph.HIT : TEXT;
				int vh = put(list.get(i)[0], title, c, tx + inner, y + inner, tw - inner * 2);
				put(list.get(i)[1], small, MUTED, tx + inner, y + inner + vh, tw - inner * 2);
			}
			return th;
		}

		/** The value lost, in large type. */
		int loss(int x, int y, int w)
		{
			if (items == null)
			{
				return 0;
			}
			String lost = items.getAtRisk().isEmpty() ? "Nothing lost" : "Lost " + ItemsOnDeath.gp(items.getRiskValue()) + " gp";
			int h = lines(lost, title, HpGraph.FOOD, x, y, w);
			String note = items.getAtRisk().isEmpty() ? "" : "Estimate at Grand Exchange prices";
			if (items.getKeptValue() > 0)
			{
				note = "Kept " + ItemsOnDeath.gp(items.getKeptValue()) + " gp" + (note.isEmpty() ? "" : " · " + note);
			}
			if (!note.isEmpty())
			{
				h += lines(note, small, MUTED, x, y + h, w);
			}
			return h;
		}

		int sources(int x, int y, int w)
		{
			List<RecapAnalyzer.SourceDamage> list = summary.getBySource();
			if (list.isEmpty())
			{
				return 0;
			}
			int h = put("Damage by source", heading, TEXT, x, y, w);
			FontMetrics fm = g.getFontMetrics(body);
			int max = Math.max(1, list.get(0).getDamage());
			for (int i = 0; i < Math.min(SOURCES, list.size()); i++)
			{
				RecapAnalyzer.SourceDamage s = list.get(i);
				String amount = s.getDamage() + " (" + s.getHits() + (s.getHits() == 1 ? " hit)" : " hits)");
				int amountWidth = fm.stringWidth(amount);
				put(s.getSource(), body, TEXT, x, y + h, w - amountWidth - pad);
				put(amount, body, TEXT, x + w - amountWidth, y + h, amountWidth);
				h += fm.getHeight();
				int barHeight = Math.round(4 * k);
				fill(BAR, x, y + h, w, barHeight);
				fill(HpGraph.HIT, x, y + h, Math.max(1, w * s.getDamage() / max), barHeight);
				h += barHeight + Math.round(5 * k);
			}
			return h;
		}

		int findings(int x, int y, int w)
		{
			List<String> list = ShareCard.findings(summary);
			if (list.isEmpty())
			{
				return 0;
			}
			int h = put(record.isDeath() ? "What happened" : "How it went", heading, TEXT, x, y, w);
			for (String f : list)
			{
				for (String line : wrap("• " + f, g.getFontMetrics(body), w))
				{
					h += put(line, body, Color.LIGHT_GRAY, x, y + h, w);
				}
			}
			return h;
		}

		/** The most valuable items lost, with their value. */
		int items(int x, int y, int w)
		{
			if (items == null || items.getAtRisk().isEmpty())
			{
				return 0;
			}
			List<DeathState.ItemStack> lost = items.getAtRisk();
			int h = put("Items lost", heading, TEXT, x, y, w);
			FontMetrics fm = g.getFontMetrics(body);
			int shown = lost.size() > ITEMS ? ITEMS - 1 : lost.size();
			for (int i = 0; i < shown; i++)
			{
				DeathState.ItemStack s = lost.get(i);
				long value = s.getPrice() * s.getQuantity();
				String gp = value > 0 ? ItemsOnDeath.gp(value) : "-";
				int gpWidth = fm.stringWidth(gp);
				put(RecapText.stack(s), body, TEXT, x, y + h, w - gpWidth - pad);
				h += put(gp, body, HpGraph.FOOD, x + w - gpWidth, y + h, gpWidth);
			}
			if (shown < lost.size())
			{
				long rest = 0;
				for (int i = shown; i < lost.size(); i++)
				{
					rest += lost.get(i).getPrice() * lost.get(i).getQuantity();
				}
				h += put("+ " + (lost.size() - shown) + " more · " + ItemsOnDeath.gp(rest) + " gp", body, MUTED, x, y + h, w);
			}
			return h;
		}

		int graph(int x, int y, int w, int h)
		{
			if (draw)
			{
				g.setFont(small);
				HpGraph.paint(g, new Rectangle(x, y, w, h), record);
			}
			return h;
		}

		int creditHeight()
		{
			return g.getFontMetrics(small).getHeight() + pad / 2;
		}

		void credit(int width, int height)
		{
			FontMetrics fm = g.getFontMetrics(small);
			g.setFont(small);
			g.setColor(MUTED);
			g.drawString(CREDIT, width - pad - fm.stringWidth(CREDIT), height - pad / 2 - fm.getDescent());
		}

		void fill(Color colour, int x, int y, int w, int h)
		{
			if (draw)
			{
				g.setColor(colour);
				g.fillRect(x, y, w, h);
			}
		}

		/** Text wrapped to the width; returns the height of its lines. */
		private int lines(String text, Font font, Color colour, int x, int y, int w)
		{
			int h = 0;
			for (String line : wrap(text, g.getFontMetrics(font), w, ""))
			{
				h += put(line, font, colour, x, y + h, w);
			}
			return h;
		}

		/** One line of text, shortened to fit; returns the line's height. */
		private int put(String text, Font font, Color colour, int x, int y, int w)
		{
			FontMetrics fm = g.getFontMetrics(font);
			if (draw)
			{
				g.setFont(font);
				g.setColor(colour);
				g.drawString(fit(text, fm, w), x, y + fm.getAscent());
			}
			return fm.getHeight();
		}
	}

	private static float clamp(float v, float min, float max)
	{
		return Math.max(min, Math.min(max, v));
	}

	private static String headline(DeathRecord record, RecapAnalyzer.Summary summary)
	{
		if (record.isCloseCall())
		{
			int lowest = record.getTicks().stream().mapToInt(TickSnapshot::getHp).filter(h -> h > 0).min().orElse(0);
			return "Survived on " + lowest + " HP";
		}
		RecapEvent blow = summary.getKillingBlow();
		if (record.isClip() || blow == null)
		{
			return "";
		}
		// HP just before the killing blow shows whether it was one big hit or the last of many.
		int before = 0;
		for (TickSnapshot t : record.getTicks())
		{
			if (t.getTick() < blow.getTick())
			{
				before = t.getHp();
			}
		}
		return "Killing blow: " + blow.getAmount() + " damage" + (before > 0 ? ", you had " + before + " HP" : "");
	}

	/** The findings worth a place on the card; the killing blow is already in the headline. */
	private static List<String> findings(RecapAnalyzer.Summary summary)
	{
		List<String> out = new ArrayList<>();
		for (String f : summary.getFindings())
		{
			if (!f.startsWith("Killing blow") && out.size() < FINDINGS)
			{
				out.add(f);
			}
		}
		return out;
	}

	static List<String> wrap(String text, FontMetrics fm, int width)
	{
		return wrap(text, fm, width, "  ");
	}

	/** Words wrapped to the width, with {@code indent} before each line after the first. */
	static List<String> wrap(String text, FontMetrics fm, int width, String indent)
	{
		List<String> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String word : text.split(" "))
		{
			String next = current.length() == 0 ? word : current + " " + word;
			if (fm.stringWidth(next) > width && current.length() > 0)
			{
				lines.add(current.toString());
				current = new StringBuilder(indent + word);
			}
			else
			{
				current = new StringBuilder(next);
			}
		}
		if (current.length() > 0)
		{
			lines.add(current.toString());
		}
		return lines;
	}

	private static String fit(String text, FontMetrics fm, int width)
	{
		if (fm.stringWidth(text) <= width)
		{
			return text;
		}
		String s = text;
		while (s.length() > 1 && fm.stringWidth(s + "...") > width)
		{
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}
}
