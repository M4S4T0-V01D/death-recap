package com.deathrecap;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

class DeathRecapPanel extends PluginPanel
{
	private static final Color DAMAGE = new Color(0xFF, 0x60, 0x60);
	static final Color DOT = new Color(0x6C, 0xC0, 0x4A);
	private static final Color CONSUME = new Color(0xF0, 0xC0, 0x40);
	private static final Color PRAYER = new Color(0x60, 0xB0, 0xFF);
	private static final int THUMB_WIDTH = PANEL_WIDTH - 40;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();

	private final DeathRecapPlugin plugin;
	private final JPanel list = new JPanel();
	private final JPanel statsView = new JPanel();
	private final CardLayout pages = new CardLayout();
	private final JPanel content = new JPanel(pages);
	/** Timestamps of cards the user expanded, so a refresh keeps them open. */
	private final Set<Long> expanded = new HashSet<>();
	/** Screenshot thumbnails by file name, so refreshing the list doesn't reload them. */
	private final Map<String, BufferedImage> thumbnails = new HashMap<>();

	DeathRecapPanel(DeathRecapPlugin plugin)
	{
		this.plugin = plugin;
		setLayout(new BorderLayout(0, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel header = new JPanel(new BorderLayout());
		JLabel title = new JLabel("Death Recap");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(title, BorderLayout.WEST);
		JButton clear = new JButton("Clear all");
		clear.addActionListener(e ->
		{
			if (JOptionPane.showConfirmDialog(this, "Delete every saved recap for this account?", "Death Recap",
				JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
			{
				plugin.clearRecords();
			}
		});
		header.add(clear, BorderLayout.EAST);

		JPanel tabs = new JPanel(new GridLayout(1, 2, 4, 0));
		tabs.setBorder(new EmptyBorder(8, 0, 0, 0));
		ButtonGroup group = new ButtonGroup();
		for (String name : new String[]{"Recaps", "Stats"})
		{
			JToggleButton tab = new JToggleButton(name, name.equals("Recaps"));
			tab.addActionListener(e -> pages.show(content, name));
			group.add(tab);
			tabs.add(tab);
		}
		header.add(tabs, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		statsView.setLayout(new BoxLayout(statsView, BoxLayout.Y_AXIS));
		JPanel listTop = new JPanel(new BorderLayout());
		listTop.add(list, BorderLayout.NORTH);
		JPanel statsTop = new JPanel(new BorderLayout());
		statsTop.add(statsView, BorderLayout.NORTH);
		content.add(listTop, "Recaps");
		content.add(statsTop, "Stats");
		add(content, BorderLayout.CENTER);
		setRecords(List.of(), DeathStats.compute(List.of(), 0));
	}

	void setRecords(List<DeathRecord> records, DeathStats.Report report)
	{
		showStats(report);
		Set<String> shown = records.stream().map(DeathRecord::getScreenshot).collect(Collectors.toSet());
		thumbnails.keySet().retainAll(shown);
		list.removeAll();
		if (records.isEmpty())
		{
			JLabel empty = new JLabel("<html>No deaths recorded yet.<br>When you die, a recap of the last few seconds appears here."
				+ " Close calls show up here too, once the fight is over.</html>");
			empty.setForeground(MUTED);
			list.add(empty);
		}
		boolean first = true;
		for (DeathRecord r : records)
		{
			list.add(card(r, first || expanded.contains(r.getTimestamp())));
			first = false;
		}
		list.revalidate();
		list.repaint();
	}

	private JPanel card(DeathRecord record, boolean open)
	{
		RecapAnalyzer.Summary summary = RecapAnalyzer.analyze(record);

		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 6, 0, ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(6, 8, 6, 8)));

		JPanel head = new JPanel(new GridLayout(2, 1));
		head.setOpaque(false);
		String blow = summary.getKillingBlow() != null ? " (" + summary.getKillingBlow().getAmount() + ")" : "";
		JLabel killer = new JLabel(record.isCloseCall()
			? "Close call: " + summary.getKiller() + " · HP fell to " + StatEntry.of(record, summary).getLowestHp()
			: record.isClip() ? "Clip" + (summary.getTotalDamage() > 0 ? ": " + summary.getKiller() : "")
			: summary.getKiller() + blow);
		killer.setForeground(record.isCloseCall() ? CONSUME : record.isClip() ? PRAYER : DAMAGE);
		killer.setFont(FontManager.getRunescapeBoldFont());
		JLabel when = new JLabel(RecapText.date(record.getTimestamp()) + " · World " + record.getWorld()
			+ " · " + summary.getTotalDamage() + " dmg");
		when.setForeground(MUTED);
		when.setFont(FontManager.getRunescapeSmallFont());
		head.add(killer);
		head.add(when);
		head.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		head.setToolTipText(RecapText.location(record));
		card.add(head, BorderLayout.NORTH);

		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBorder(new EmptyBorder(6, 0, 0, 0));
		body.setVisible(open);

		String who = RecapText.headline(record, summary);
		if (record.getScreenshot() != null || record.getReplay() != null)
		{
			JPanel media = new JPanel(new BorderLayout(0, 4));
			media.setOpaque(false);
			media.setBorder(new EmptyBorder(0, 0, 6, 0));
			if (record.getScreenshot() != null)
			{
				JLabel thumb = new JLabel("Loading screenshot...", JLabel.CENTER);
				thumb.setForeground(MUTED);
				thumb.setToolTipText("Open the screenshot");
				thumb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				showThumbnail(thumb, record.getScreenshot());
				thumb.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
					{
						view(record.getScreenshot(), "Screenshot: " + who);
					}
				});
				media.add(thumb, BorderLayout.CENTER);
			}
			if (record.getReplay() != null)
			{
				JButton play = new JButton("Watch replay");
				play.addActionListener(e -> replay(record, "Replay: " + who, null));
				media.add(play, BorderLayout.SOUTH);
			}
			body.add(left(media));
		}

		if (!record.getTicks().isEmpty())
		{
			body.add(left(new Graph(record)));
		}

		StringBuilder html = new StringBuilder("<html><body style='width:170px'>");
		for (String f : summary.getFindings())
		{
			html.append("&bull; ").append(esc(f)).append("<br>");
		}
		body.add(left(new JLabel(html.append("</body></html>").toString())));

		DeathState state = record.getState();
		if (state != null)
		{
			body.add(left(title(record.isCloseCall() ? "At the low point" : record.isClip() ? "When the clip was saved" : "At death")));
			body.add(left(small(RecapText.stateLines(state), Color.LIGHT_GRAY)));
			if (record.isDeath() && (!state.getEquipment().isEmpty() || !state.getInventory().isEmpty()))
			{
				ItemsOnDeath.Result items = ItemsOnDeath.compute(state);
				body.add(left(title("Items: " + ItemsOnDeath.gp(items.getRiskValue()) + " gp at risk")));
				JLabel caveat = small(List.of("Estimate: the most valuable items you would keep, by GE price."
					+ " Untradeables, gravestones and PvP rules can change what you actually lose."), MUTED);
				body.add(left(caveat));
				body.add(left(small(itemLines("Kept", items.getKept(), items.getKeptValue()), CONSUME)));
				body.add(left(small(itemLines("At risk", items.getAtRisk(), items.getRiskValue()), DAMAGE)));
			}
		}

		body.add(left(title(record.getReplay() != null ? "Timeline (click to watch)" : "Timeline")));

		List<RecapEvent> events = record.getEvents().stream()
			.sorted(Comparator.comparingInt(RecapEvent::getTick).reversed())
			.collect(Collectors.toList());
		for (RecapEvent e : events)
		{
			JLabel line = new JLabel(String.format("<html><body style='width:170px'><font color='#909090'>%s</font> %s</body></html>",
				RecapText.offset(e.getTick(), record.getDeathTick()), esc(RecapText.describe(e))));
			line.setForeground(colour(e));
			line.setFont(FontManager.getRunescapeSmallFont());
			if (record.getReplay() != null)
			{
				line.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				line.setToolTipText("Watch the replay from this moment");
				line.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent ev)
					{
						replay(record, "Replay: " + who, e.getTick());
					}
				});
			}
			body.add(left(line));
		}

		JPanel buttons = new JPanel(new GridLayout(0, 2, 6, 6));
		buttons.setOpaque(false);
		buttons.setBorder(new EmptyBorder(6, 0, 0, 0));
		JButton copy = new JButton("Copy");
		copy.setToolTipText("Copy this recap as text");
		copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
			.setContents(new StringSelection(RecapText.full(record, summary)), null));
		JButton share = new JButton("Share card");
		share.setToolTipText("Pick a layout, then copy an image of the screenshot and recap, ready to paste anywhere");
		share.addActionListener(e ->
		{
			JPopupMenu menu = new JPopupMenu();
			ShareCardLayout preferred = plugin.shareCardLayout();
			for (ShareCardLayout layout : ShareCardLayout.values())
			{
				JMenuItem item = new JMenuItem(layout == preferred ? layout.getLabel() + " (default)" : layout.getLabel());
				item.setToolTipText(layout.getDescription());
				item.addActionListener(ev -> shareCard(share, record, who, layout));
				menu.add(item);
			}
			menu.show(share, 0, share.getHeight());
		});
		JButton delete = new JButton("Delete");
		delete.addActionListener(e -> plugin.deleteRecord(record));
		buttons.add(copy);
		buttons.add(share);
		buttons.add(delete);
		body.add(left(buttons));
		card.add(body, BorderLayout.CENTER);

		head.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				boolean show = !body.isVisible();
				body.setVisible(show);
				if (show)
				{
					expanded.add(record.getTimestamp());
				}
				else
				{
					expanded.remove(record.getTimestamp());
				}
				card.revalidate();
			}
		});
		return card;
	}

	private void showThumbnail(JLabel label, String name)
	{
		BufferedImage cached = thumbnails.get(name);
		if (cached != null)
		{
			label.setText(null);
			label.setIcon(new ImageIcon(cached));
			return;
		}
		plugin.loadThumbnail(name, THUMB_WIDTH, image ->
		{
			if (image == null)
			{
				label.setText("Screenshot missing");
				return;
			}
			thumbnails.put(name, image);
			label.setText(null);
			label.setIcon(new ImageIcon(image));
			label.revalidate();
		});
	}

	/**
	 * Opens a screenshot or replay in its own window at full size, so every bit of text stays readable;
	 * if it is bigger than the screen, the window scrolls. Animated GIFs play by themselves in a JLabel.
	 */
	private void view(String name, String title)
	{
		plugin.loadMedia(name, bytes ->
		{
			if (bytes == null)
			{
				JOptionPane.showMessageDialog(this, "That file is missing.", "Death Recap", JOptionPane.WARNING_MESSAGE);
				return;
			}
			show(title, new JLabel(new ImageIcon(bytes)));
		});
	}

	/** Opens the replay player, paused at {@code tick} if given. */
	private void replay(DeathRecord record, String title, Integer tick)
	{
		plugin.loadMedia(record.getReplay(), bytes ->
		{
			if (bytes == null)
			{
				JOptionPane.showMessageDialog(this, "That file is missing.", "Death Recap", JOptionPane.WARNING_MESSAGE);
				return;
			}
			new ReplayPlayer(SwingUtilities.getWindowAncestor(this), title, record, bytes, tick, plugin::openMediaFolder,
				plugin.getExecutor())
				.setVisible(true);
		});
	}

	private void show(String title, JComponent content)
	{
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(24);
		scroll.getHorizontalScrollBar().setUnitIncrement(24);

		Window owner = SwingUtilities.getWindowAncestor(this);
		JDialog dialog = new JDialog(owner, title);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setResizable(true);
		dialog.add(scroll, BorderLayout.CENTER);
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton folder = new JButton("Open folder");
		folder.addActionListener(e -> plugin.openMediaFolder());
		bar.add(folder);
		dialog.add(bar, BorderLayout.SOUTH);
		dialog.pack();
		// Fit the window to the screen; anything bigger scrolls instead of being shrunk.
		Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		dialog.setSize(Math.min(dialog.getWidth(), usable.width), Math.min(dialog.getHeight(), usable.height));
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
	}

	private void showStats(DeathStats.Report r)
	{
		statsView.removeAll();
		JButton reset = new JButton("Reset stats");
		reset.setToolTipText("Start the totals over; saved recaps are kept");
		reset.addActionListener(e ->
		{
			if (JOptionPane.showConfirmDialog(this, "Reset the death stats for this account? Saved recaps are kept.",
				"Death Recap", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
			{
				plugin.resetStats();
			}
		});
		if (r.getDeaths() == 0 && r.getCloseCalls() == 0)
		{
			statsView.add(left(small(List.of("No deaths or close calls counted yet."), MUTED)));
			statsView.revalidate();
			statsView.repaint();
			return;
		}
		statsView.add(left(title("All time")));
		List<String> lines = new java.util.ArrayList<>();
		lines.add("Deaths: " + r.getDeaths());
		lines.add("Close calls survived: " + r.getCloseCalls());
		if (r.getDeaths() > 0)
		{
			DeathStats.Killer top = r.getKillers().stream().filter(k -> k.getDeaths() > 0).findFirst().orElse(null);
			if (top != null)
			{
				lines.add("Most deaths to: " + top.getName() + " (" + top.getDeaths() + ")");
			}
			lines.add("Average damage taken before death: " + r.getAverageDamage());
			lines.add(String.format("Died without eating or drinking: %d (%d%%)", r.getWithoutEating(),
				Math.round(100f * r.getWithoutEating() / r.getDeaths())));
			if (r.getWithSupplies() > 0)
			{
				lines.add(String.format("Died with food or potions left: %d (%d%%)", r.getWithSupplies(),
					Math.round(100f * r.getWithSupplies() / r.getDeaths())));
			}
			lines.add("Lost in total: about " + ItemsOnDeath.gp(r.getLost()) + " gp");
			StatEntry worst = r.getMostExpensive();
			if (worst != null)
			{
				lines.add("Most expensive death: " + ItemsOnDeath.gp(worst.getLost()) + " gp to " + worst.getKiller()
					+ ", " + RecapText.date(worst.getTimestamp()));
			}
		}
		statsView.add(left(small(lines, Color.LIGHT_GRAY)));
		statsView.add(left(title("Since RuneLite started")));
		statsView.add(left(small(List.of(
			"Deaths: " + r.getSessionDeaths(),
			"Lost: about " + ItemsOnDeath.gp(r.getSessionLost()) + " gp"), Color.LIGHT_GRAY)));

		statsView.add(left(title("By attacker")));
		for (DeathStats.Killer k : r.getKillers())
		{
			List<String> parts = new java.util.ArrayList<>();
			if (k.getDeaths() > 0)
			{
				parts.add(k.getDeaths() + (k.getDeaths() == 1 ? " death" : " deaths") + ", avg " + k.getAverageDamage() + " dmg"
					+ (k.getWithoutEating() > 0 ? ", " + k.getWithoutEating() + " without eating" : ""));
			}
			if (k.getCloseCalls() > 0)
			{
				parts.add(k.getCloseCalls() + (k.getCloseCalls() == 1 ? " close call" : " close calls"));
			}
			JLabel name = new JLabel(esc(k.getName()));
			name.setForeground(k.getDeaths() > 0 ? DAMAGE : CONSUME);
			name.setBorder(new EmptyBorder(4, 0, 0, 0));
			statsView.add(left(name));
			statsView.add(left(small(parts, MUTED)));
		}
		JPanel bar = new JPanel(new GridLayout(1, 1));
		bar.setBorder(new EmptyBorder(10, 0, 0, 0));
		bar.add(reset);
		statsView.add(left(bar));
		statsView.revalidate();
		statsView.repaint();
	}

	private void shareCard(JButton share, DeathRecord record, String who, ShareCardLayout layout)
	{
		share.setEnabled(false);
		plugin.makeShareCard(record, layout, image ->
		{
			share.setEnabled(true);
			if (image == null)
			{
				JOptionPane.showMessageDialog(this, "Unable to make the share card.", "Death Recap", JOptionPane.WARNING_MESSAGE);
				return;
			}
			copyImage(image);
			show("Share card, " + layout.getLabel() + " (copied to clipboard): " + who, new JLabel(new ImageIcon(image)));
		});
	}

	private static List<String> itemLines(String label, List<DeathState.ItemStack> items, long value)
	{
		List<String> lines = new java.util.ArrayList<>();
		lines.add(label + " (" + ItemsOnDeath.gp(value) + " gp):");
		if (items.isEmpty())
		{
			lines.add("  nothing");
		}
		for (DeathState.ItemStack i : items)
		{
			lines.add("  " + (i.getQuantity() > 1 ? i.getQuantity() + " x " : "") + i.getName()
				+ (i.getPrice() > 0 ? " - " + ItemsOnDeath.gp(i.getPrice() * i.getQuantity()) : ""));
		}
		return lines;
	}

	private static JLabel title(String text)
	{
		JLabel label = new JLabel(esc(text));
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		label.setBorder(new EmptyBorder(6, 0, 2, 0));
		return label;
	}

	private static JLabel small(List<String> lines, Color colour)
	{
		StringBuilder html = new StringBuilder("<html><body style='width:170px'>");
		for (String line : lines)
		{
			html.append(esc(line).replace("  ", "&nbsp;&nbsp;")).append("<br>");
		}
		JLabel label = new JLabel(html.append("</body></html>").toString());
		label.setForeground(colour);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	private static void copyImage(BufferedImage image)
	{
		Transferable t = new Transferable()
		{
			@Override
			public DataFlavor[] getTransferDataFlavors()
			{
				return new DataFlavor[]{DataFlavor.imageFlavor};
			}

			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor)
			{
				return DataFlavor.imageFlavor.equals(flavor);
			}

			@Override
			public Object getTransferData(DataFlavor flavor)
			{
				return image;
			}
		};
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);
		}
		catch (IllegalStateException e)
		{
			// The clipboard is busy; the card is still shown and saved.
		}
	}

	/** The HP and prayer graph; hovering shows the values at that moment. */
	private static final class Graph extends JComponent
	{
		private final DeathRecord record;

		Graph(DeathRecord record)
		{
			this.record = record;
			Dimension size = new Dimension(THUMB_WIDTH, 130);
			setFont(FontManager.getRunescapeSmallFont());
			setPreferredSize(size);
			setMaximumSize(size);
			setToolTipText("");
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setFont(getFont());
			HpGraph.paint((Graphics2D) g, new Rectangle(0, 0, getWidth(), getHeight()), record);
		}

		@Override
		public String getToolTipText(MouseEvent e)
		{
			TickSnapshot t = HpGraph.at(record, e.getX(), new Rectangle(0, 0, getWidth(), getHeight()), getFontMetrics(getFont()));
			return t == null ? null : String.format("%s: HP %d/%d, prayer %d", RecapText.offset(t.getTick(), record.getDeathTick()),
				t.getHp(), t.getMaxHp(), t.getPrayer());
		}
	}

	static Color colour(RecapEvent e)
	{
		switch (e.getType())
		{
			case HIT:
				return DAMAGE;
			case DAMAGE_OVER_TIME:
				return DOT;
			case EAT:
			case DRINK:
			case HEAL:
				return CONSUME;
			case PRAYER_ON:
			case PRAYER_OFF:
			case PRAYER_EMPTY:
				return PRAYER;
			default:
				return MUTED;
		}
	}

	private static <T extends JComponent> T left(T c)
	{
		c.setAlignmentX(LEFT_ALIGNMENT);
		return c;
	}

	private static String esc(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** A small skull-and-clock icon drawn at runtime, so the plugin ships no image files. */
	static BufferedImage icon()
	{
		return ImageUtil.loadImageResource(DeathRecapPanel.class, "panel_icon.png");
	}
}
