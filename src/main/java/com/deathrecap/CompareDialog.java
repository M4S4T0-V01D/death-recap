package com.deathrecap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Value;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Two recaps side by side, older on the left, with a list of what was different between them.
 */
final class CompareDialog extends JDialog
{
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();

	/** The numbers compared for one recap. */
	@Value
	static class Facts
	{
		String killer;
		int damage;
		/** Food and potions used in the recap window. */
		int consumed;
		int lowestHp;
		/** Average HP percent when the killer hit; -1 when unknown. */
		int hpWhenHit;
		int fightTicks;
		/** Estimated gp lost; 0 for close calls and clips. */
		long lost;
		/** Food and potions still in the inventory; -1 when unknown. */
		int suppliesLeft;
	}

	CompareDialog(Window owner, DeathRecord first, DeathRecord second)
	{
		super(owner, "Compare recaps");
		DeathRecord a = first.getTimestamp() <= second.getTimestamp() ? first : second;
		DeathRecord b = a == first ? second : first;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		JPanel columns = new JPanel(new GridLayout(1, 2, 12, 0));
		columns.add(column(a, "Earlier"));
		columns.add(column(b, "Later"));

		JPanel diff = new JPanel();
		diff.setLayout(new BoxLayout(diff, BoxLayout.Y_AXIS));
		diff.setBorder(new EmptyBorder(10, 0, 0, 0));
		diff.add(label("What was different", Color.WHITE, true));
		List<String> lines = differences(facts(a), facts(b));
		if (lines.isEmpty())
		{
			lines = List.of("Nothing stands out: the two went much the same way.");
		}
		for (String line : lines)
		{
			diff.add(label("• " + line, Color.LIGHT_GRAY, false));
		}

		JPanel root = new JPanel(new BorderLayout());
		root.setBorder(new EmptyBorder(10, 10, 10, 10));
		root.add(columns, BorderLayout.CENTER);
		root.add(diff, BorderLayout.SOUTH);
		add(root);
		pack();
		setLocationRelativeTo(owner);
	}

	private static JPanel column(DeathRecord r, String title)
	{
		RecapAnalyzer.Summary summary = RecapAnalyzer.analyze(r);
		Facts f = facts(r, summary);
		JPanel col = new JPanel();
		col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
		col.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		col.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		col.add(label(title + ": " + RecapText.date(r.getTimestamp()), MUTED, false));
		col.add(label(RecapText.headline(r, summary), r.isCloseCall() ? HpGraph.FOOD : HpGraph.HIT, true));
		col.add(label(RecapText.place(r), MUTED, false));
		if (!r.getTicks().isEmpty())
		{
			col.add(new Graph(r));
		}
		List<String> lines = new ArrayList<>();
		lines.add("Damage taken: " + f.getDamage());
		lines.add("Lowest HP: " + f.getLowestHp());
		lines.add("Food and potions used: " + f.getConsumed());
		if (f.getHpWhenHit() >= 0)
		{
			lines.add("HP when " + f.getKiller() + " hit: " + f.getHpWhenHit() + "%");
		}
		if (f.getFightTicks() > 0)
		{
			lines.add(String.format("Fight length: %.1fs", f.getFightTicks() * RecapAnalyzer.TICK_SECONDS));
		}
		if (f.getSuppliesLeft() >= 0)
		{
			lines.add("Food and potions left: " + f.getSuppliesLeft());
		}
		if (f.getLost() > 0)
		{
			lines.add("Lost: about " + ItemsOnDeath.gp(f.getLost()) + " gp");
		}
		for (String line : lines)
		{
			col.add(label(line, Color.LIGHT_GRAY, false));
		}
		for (String finding : summary.getFindings())
		{
			col.add(label("• " + finding, MUTED, false));
		}
		return col;
	}

	static Facts facts(DeathRecord r)
	{
		return facts(r, RecapAnalyzer.analyze(r));
	}

	private static Facts facts(DeathRecord r, RecapAnalyzer.Summary summary)
	{
		StatEntry e = StatEntry.of(r, summary);
		int consumed = (int) r.getEvents().stream()
			.filter(ev -> ev.getType() == RecapEvent.Type.EAT || ev.getType() == RecapEvent.Type.DRINK).count();
		int lowest = r.getTicks().stream().mapToInt(TickSnapshot::getHp).min().orElse(0);
		DeathState s = r.getState();
		int left = s == null ? -1 : StatEntry.count(s.getFood()) + StatEntry.count(s.getPotions());
		return new Facts(RecapAnalyzer.label(r, summary.getKiller()), e.getDamage(), consumed, lowest, e.getHpWhenHit(),
			e.getFightTicks(), r.isDeath() ? e.getLost() : 0, left);
	}

	/** What changed from {@code a} (earlier) to {@code b} (later), plainly and without advice. */
	static List<String> differences(Facts a, Facts b)
	{
		List<String> out = new ArrayList<>();
		if (!a.getKiller().equals(b.getKiller()))
		{
			out.add("Different attacker: " + a.getKiller() + ", then " + b.getKiller());
		}
		int damage = b.getDamage() - a.getDamage();
		if (Math.abs(damage) >= Math.max(5, a.getDamage() / 5))
		{
			out.add(String.format("Took %d %s damage the second time (%d vs %d)", Math.abs(damage),
				damage > 0 ? "more" : "less", a.getDamage(), b.getDamage()));
		}
		if (a.getConsumed() != b.getConsumed())
		{
			out.add(String.format("Used %s food and potions (%d vs %d)", b.getConsumed() > a.getConsumed() ? "more" : "fewer",
				a.getConsumed(), b.getConsumed()));
		}
		if (a.getHpWhenHit() >= 0 && b.getHpWhenHit() >= 0 && Math.abs(a.getHpWhenHit() - b.getHpWhenHit()) >= 8)
		{
			out.add(String.format("HP was %s when hits landed (%d%% vs %d%%)",
				b.getHpWhenHit() > a.getHpWhenHit() ? "higher" : "lower", a.getHpWhenHit(), b.getHpWhenHit()));
		}
		if (a.getFightTicks() > 0 && b.getFightTicks() > 0
			&& Math.abs(a.getFightTicks() - b.getFightTicks()) >= Math.max(3, a.getFightTicks() * 3 / 10))
		{
			out.add(String.format("The fight was %s (%.1fs vs %.1fs)", b.getFightTicks() > a.getFightTicks() ? "longer" : "shorter",
				a.getFightTicks() * RecapAnalyzer.TICK_SECONDS, b.getFightTicks() * RecapAnalyzer.TICK_SECONDS));
		}
		if (a.getSuppliesLeft() >= 0 && b.getSuppliesLeft() >= 0 && (a.getSuppliesLeft() == 0) != (b.getSuppliesLeft() == 0))
		{
			out.add(b.getSuppliesLeft() == 0 ? "Ran out of food and potions the second time"
				: "Still had food or potions the second time (" + b.getSuppliesLeft() + ")");
		}
		if (a.getLost() != b.getLost() && (a.getLost() > 0 || b.getLost() > 0))
		{
			out.add("Lost about " + ItemsOnDeath.gp(a.getLost()) + " gp, then " + ItemsOnDeath.gp(b.getLost()) + " gp");
		}
		return out;
	}

	private static JLabel label(String text, Color colour, boolean bold)
	{
		JLabel l = new JLabel("<html><body style='width:280px'>" + text.replace("&", "&amp;").replace("<", "&lt;")
			+ "</body></html>");
		l.setForeground(colour);
		l.setFont(bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
		l.setAlignmentX(LEFT_ALIGNMENT);
		l.setBorder(new EmptyBorder(1, 0, 1, 0));
		return l;
	}

	private static final class Graph extends JComponent
	{
		private final DeathRecord record;

		Graph(DeathRecord record)
		{
			this.record = record;
			Dimension size = new Dimension(300, 140);
			setPreferredSize(size);
			setMaximumSize(size);
			setFont(FontManager.getRunescapeSmallFont());
			setAlignmentX(LEFT_ALIGNMENT);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setFont(getFont());
			HpGraph.paint((Graphics2D) g, new Rectangle(0, 0, getWidth(), getHeight()), record);
		}
	}
}
