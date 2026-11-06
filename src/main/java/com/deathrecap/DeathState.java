package com.deathrecap;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the player had going for them at the moment of death: stats, energy, prayers, weapon and items.
 */
@Data
@NoArgsConstructor
public class DeathState
{
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Stat
	{
		private String skill;
		private int boosted;
		private int real;
	}

	@Data
	@NoArgsConstructor
	public static class ItemStack
	{
		private int id;
		private String name;
		private int quantity;
		/** Value of one item: the GE price, or the high alchemy value if it has none. */
		private long price;
		/** Equipment slot index for worn items; -1 for carried items and recaps from before slots were recorded. */
		private int slot = -1;

		public ItemStack(int id, String name, int quantity, long price)
		{
			this.id = id;
			this.name = name;
			this.quantity = quantity;
			this.price = price;
		}
	}

	private List<Stat> stats = new ArrayList<>();
	/** Special attack energy, 0-100. */
	private int specEnergy;
	/** Run energy, 0-100. */
	private int runEnergy;
	private List<String> prayers = new ArrayList<>();
	private String weapon;
	private String attackStyle;
	private boolean skulled;
	private boolean protectItem;
	/** Worn items; also recorded for close calls and clips since gear was added to the recap. */
	private List<ItemStack> equipment = new ArrayList<>();
	private List<ItemStack> inventory = new ArrayList<>();
	/** Carried items with an Eat option, one stack per item; null for recaps from before this was recorded. */
	private List<ItemStack> food;
	/** Carried items with a Drink option, mostly potions, one stack per item; null for older recaps. */
	private List<ItemStack> potions;
}
