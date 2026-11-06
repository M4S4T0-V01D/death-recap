package com.deathrecap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.EquipmentInventorySlot;

/**
 * Worn gear in a recap: the equipment screen layout, its value and what changed between two recaps.
 */
final class Gear
{
	/** The equipment screen, row by row; null cells are empty. */
	static final EquipmentInventorySlot[][] LAYOUT = {
		{null, EquipmentInventorySlot.HEAD, null},
		{EquipmentInventorySlot.CAPE, EquipmentInventorySlot.AMULET, EquipmentInventorySlot.AMMO},
		{EquipmentInventorySlot.WEAPON, EquipmentInventorySlot.BODY, EquipmentInventorySlot.SHIELD},
		{null, EquipmentInventorySlot.LEGS, null},
		{EquipmentInventorySlot.GLOVES, EquipmentInventorySlot.BOOTS, EquipmentInventorySlot.RING},
	};

	private Gear()
	{
	}

	/** True when every item knows its slot, so the gear can be laid out like the equipment screen. */
	static boolean hasSlots(List<DeathState.ItemStack> worn)
	{
		return !worn.isEmpty() && worn.stream().allMatch(s -> s.getSlot() >= 0);
	}

	/** The worn item in {@code slot}, or null. */
	static DeathState.ItemStack inSlot(List<DeathState.ItemStack> worn, EquipmentInventorySlot slot)
	{
		for (DeathState.ItemStack s : worn)
		{
			if (s.getSlot() == slot.getSlotIdx())
			{
				return s;
			}
		}
		return null;
	}

	static long value(List<DeathState.ItemStack> worn)
	{
		return worn.stream().mapToLong(s -> s.getPrice() * s.getQuantity()).sum();
	}

	/** "Head", "Ammo" ... */
	static String slotName(EquipmentInventorySlot slot)
	{
		String n = slot.name();
		return n.charAt(0) + n.substring(1).toLowerCase();
	}

	/**
	 * What was worn differently in {@code b} (later) than in {@code a} (earlier), one line per slot when
	 * both know their slots, otherwise items that were only worn in one of them. Quantities are ignored.
	 */
	static List<String> differences(List<DeathState.ItemStack> a, List<DeathState.ItemStack> b)
	{
		List<String> out = new ArrayList<>();
		if (a.isEmpty() || b.isEmpty())
		{
			return out;
		}
		if (hasSlots(a) && hasSlots(b))
		{
			for (EquipmentInventorySlot[] row : LAYOUT)
			{
				for (EquipmentInventorySlot slot : row)
				{
					if (slot == null)
					{
						continue;
					}
					DeathState.ItemStack x = inSlot(a, slot);
					DeathState.ItemStack y = inSlot(b, slot);
					if (!Objects.equals(x == null ? null : x.getName(), y == null ? null : y.getName()))
					{
						out.add(slotName(slot) + ": " + (x == null ? "nothing" : x.getName()) + ", then "
							+ (y == null ? "nothing" : y.getName()));
					}
				}
			}
			return out;
		}
		Set<String> before = names(a);
		Set<String> after = names(b);
		List<String> added = new ArrayList<>(after);
		added.removeAll(before);
		List<String> removed = new ArrayList<>(before);
		removed.removeAll(after);
		if (!added.isEmpty())
		{
			out.add("Wore " + RecapAnalyzer.joinAnd(added) + " the second time");
		}
		if (!removed.isEmpty())
		{
			out.add("Didn't wear " + RecapAnalyzer.joinAnd(removed) + " the second time");
		}
		return out;
	}

	private static Set<String> names(List<DeathState.ItemStack> worn)
	{
		Set<String> out = new LinkedHashSet<>();
		worn.forEach(s -> out.add(s.getName()));
		return out;
	}
}
