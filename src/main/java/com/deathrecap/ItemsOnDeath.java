package com.deathrecap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;

/**
 * Estimates which items a death keeps and which are at risk, using the standard rules: the three most
 * valuable items are kept, none when skulled, and Protect Item keeps one more. Each item in a stack
 * counts on its own, so a stack can be split between kept and at risk. Ties go to the item worn or
 * carried first. Ultimate Ironmen, safe deaths and some minigames follow other rules.
 */
final class ItemsOnDeath
{
	@Value
	static class Result
	{
		List<DeathState.ItemStack> kept;
		List<DeathState.ItemStack> atRisk;
		long keptValue;
		long riskValue;
	}

	private ItemsOnDeath()
	{
	}

	static int keepCount(boolean skulled, boolean protectItem)
	{
		return (skulled ? 0 : 3) + (protectItem ? 1 : 0);
	}

	static Result compute(DeathState state)
	{
		List<DeathState.ItemStack> all = new ArrayList<>(state.getEquipment());
		all.addAll(state.getInventory());
		// A stable sort keeps worn items ahead of carried ones, in slot order, when values tie.
		all.sort(Comparator.comparingLong(DeathState.ItemStack::getPrice).reversed());

		int keep = keepCount(state.isSkulled(), state.isProtectItem());
		Map<Integer, DeathState.ItemStack> kept = new LinkedHashMap<>();
		List<DeathState.ItemStack> atRisk = new ArrayList<>();
		long keptValue = 0;
		long riskValue = 0;
		for (DeathState.ItemStack s : all)
		{
			int take = Math.min(keep, s.getQuantity());
			keep -= take;
			if (take > 0)
			{
				DeathState.ItemStack k = kept.get(s.getId());
				if (k != null)
				{
					k.setQuantity(k.getQuantity() + take);
				}
				else
				{
					kept.put(s.getId(), new DeathState.ItemStack(s.getId(), s.getName(), take, s.getPrice()));
				}
				keptValue += take * s.getPrice();
			}
			int rest = s.getQuantity() - take;
			if (rest > 0)
			{
				atRisk.add(new DeathState.ItemStack(s.getId(), s.getName(), rest, s.getPrice()));
				riskValue += rest * s.getPrice();
			}
		}
		atRisk.sort(Comparator.comparingLong((DeathState.ItemStack s) -> s.getPrice() * s.getQuantity()).reversed());
		return new Result(new ArrayList<>(kept.values()), atRisk, keptValue, riskValue);
	}

	/** 1234 -> "1,234", 1_234_567 -> "1.23M", 12_345_678_901 -> "12.3B". */
	static String gp(long value)
	{
		if (value >= 1_000_000_000L)
		{
			return trim(value / 1e9) + "B";
		}
		if (value >= 10_000_000L)
		{
			return trim(value / 1e6) + "M";
		}
		if (value >= 1_000_000L)
		{
			return String.format(Locale.ROOT, "%.2fM", value / 1e6);
		}
		return String.format(Locale.ROOT, "%,d", value);
	}

	private static String trim(double v)
	{
		return String.format(Locale.ROOT, v >= 100 ? "%.0f" : "%.1f", v);
	}
}
