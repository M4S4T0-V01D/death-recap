package com.deathrecap;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.BiFunction;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Worn gear as item icons, laid out like the equipment screen when slots are known, otherwise in a row
 * of icons. Hover an item for its name and value.
 */
final class GearGrid extends JPanel
{
	private static final int CELL_W = 40;
	private static final int CELL_H = 36;
	private static final Color SLOT = ColorScheme.DARK_GRAY_COLOR;

	/** @param images item id and quantity to its icon */
	GearGrid(List<DeathState.ItemStack> worn, BiFunction<Integer, Integer, AsyncBufferedImage> images)
	{
		setOpaque(false);
		if (Gear.hasSlots(worn))
		{
			setLayout(new GridLayout(Gear.LAYOUT.length, 3, 2, 2));
			for (EquipmentInventorySlot[] row : Gear.LAYOUT)
			{
				for (EquipmentInventorySlot slot : row)
				{
					add(slot == null ? blank() : cell(Gear.inSlot(worn, slot), Gear.slotName(slot), images));
				}
			}
		}
		else
		{
			setLayout(new GridLayout(0, 4, 2, 2));
			for (DeathState.ItemStack s : worn)
			{
				add(cell(s, null, images));
			}
		}
		Dimension size = getPreferredSize();
		setMaximumSize(size);
	}

	private static JLabel blank()
	{
		JLabel l = new JLabel();
		l.setPreferredSize(new Dimension(CELL_W, CELL_H));
		return l;
	}

	private static JLabel cell(DeathState.ItemStack s, String slot,
		BiFunction<Integer, Integer, AsyncBufferedImage> images)
	{
		JLabel l = new JLabel("", JLabel.CENTER);
		l.setPreferredSize(new Dimension(CELL_W, CELL_H));
		l.setOpaque(true);
		l.setBackground(SLOT);
		l.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR.darker()));
		if (s == null)
		{
			l.setToolTipText(slot + ": nothing");
			return l;
		}
		images.apply(s.getId(), s.getQuantity()).addTo(l);
		long value = s.getPrice() * s.getQuantity();
		l.setToolTipText((slot != null ? slot + ": " : "") + RecapText.stack(s)
			+ (value > 0 ? " (" + ItemsOnDeath.gp(value) + " gp)" : ""));
		return l;
	}
}
