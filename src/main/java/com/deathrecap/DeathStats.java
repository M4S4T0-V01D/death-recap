package com.deathrecap;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import lombok.Value;

/**
 * Lifetime totals over every recorded death and close call, overall and per killer.
 */
final class DeathStats
{
	/** Entries needed before a killer's trend is worked out. */
	static final int TREND_MIN = 4;

	@Value
	static class Killer
	{
		String name;
		int deaths;
		int closeCalls;
		int averageDamage;
		int withoutEating;
		/** Estimated gp lost to this killer. */
		long lost;
		/** Average HP percent when its hits landed; -1 when unknown. */
		int hpWhenHit;
		/** Average ticks from the first damage to the death or close call; 0 when unknown. */
		int fightTicks;
		/** Its deaths and close calls, oldest first. */
		List<StatEntry> entries;
		/** Null with fewer than {@link #TREND_MIN} entries. */
		Trend trend;
	}

	@Value
	static class Trend
	{
		/** Positive when the recent half went better than the earlier half, negative when worse. */
		int score;
		/** One line per thing that changed, e.g. "You eat before dying more often". */
		List<String> reasons;

		String verdict()
		{
			return score > 0 ? "Improving" : score < 0 ? "Getting worse" : "About the same";
		}
	}

	@Value
	static class Report
	{
		int deaths;
		int closeCalls;
		/** Average damage taken in the recap window before a death. */
		int averageDamage;
		/** Deaths with no food or potion in the recap window. */
		int withoutEating;
		/** Sorted by deaths, then close calls. */
		List<Killer> killers;
		/** Estimated gp lost over every death. */
		long lost;
		/** Deaths and gp lost since the session started. */
		int sessionDeaths;
		long sessionLost;
		/** The death that cost the most gp; null when no death lost anything. */
		StatEntry mostExpensive;
		/** Deaths with food or potions still in the inventory. */
		int withSupplies;

		Killer killer(String name)
		{
			for (Killer k : killers)
			{
				if (k.getName().equals(name))
				{
					return k;
				}
			}
			return null;
		}
	}

	private DeathStats()
	{
	}

	/** A steady colour for a killer, the same on the world map and in the panel. */
	static Color colour(String killer)
	{
		int h = (killer == null ? "Unknown" : killer).hashCode() * 0x9E3779B1;
		return Color.getHSBColor((h >>> 16) / 65536f, 0.7f, 0.95f);
	}

	static String name(StatEntry e)
	{
		return e.getKiller() == null ? "Unknown" : e.getKiller();
	}

	/** @param sessionStart epoch millis; deaths from then on count toward the session totals */
	static Report compute(List<StatEntry> entries, long sessionStart)
	{
		long lost = 0;
		int sessionDeaths = 0;
		long sessionLost = 0;
		StatEntry mostExpensive = null;
		int withSupplies = 0;
		Map<String, List<StatEntry>> byKiller = new LinkedHashMap<>();
		int deaths = 0;
		int closeCalls = 0;
		long damage = 0;
		int hungry = 0;
		List<StatEntry> sorted = new ArrayList<>(entries);
		sorted.sort(Comparator.comparingLong(StatEntry::getTimestamp));
		for (StatEntry e : sorted)
		{
			byKiller.computeIfAbsent(name(e), n -> new ArrayList<>()).add(e);
			if (e.isCloseCall())
			{
				closeCalls++;
				continue;
			}
			deaths++;
			damage += e.getDamage();
			lost += e.getLost();
			if (e.isSuppliesLeft())
			{
				withSupplies++;
			}
			if (e.getTimestamp() >= sessionStart)
			{
				sessionDeaths++;
				sessionLost += e.getLost();
			}
			if (e.getLost() > 0 && (mostExpensive == null || e.getLost() > mostExpensive.getLost()))
			{
				mostExpensive = e;
			}
			if (!e.isAte())
			{
				hungry++;
			}
		}
		List<Killer> killers = new ArrayList<>();
		for (Map.Entry<String, List<StatEntry>> en : byKiller.entrySet())
		{
			killers.add(killer(en.getKey(), en.getValue()));
		}
		killers.sort(Comparator.comparingInt(Killer::getDeaths).thenComparingInt(Killer::getCloseCalls).reversed());
		return new Report(deaths, closeCalls, deaths == 0 ? 0 : (int) Math.round((double) damage / deaths), hungry, killers,
			lost, sessionDeaths, sessionLost, mostExpensive, withSupplies);
	}

	private static Killer killer(String name, List<StatEntry> list)
	{
		int deaths = 0;
		int damage = 0;
		int hungry = 0;
		long lost = 0;
		for (StatEntry e : list)
		{
			if (e.isCloseCall())
			{
				continue;
			}
			deaths++;
			damage += e.getDamage();
			lost += e.getLost();
			if (!e.isAte())
			{
				hungry++;
			}
		}
		double hp = average(list, e -> e.getHpWhenHit() >= 0, StatEntry::getHpWhenHit);
		double fight = average(list, e -> e.getFightTicks() > 0, StatEntry::getFightTicks);
		return new Killer(name, deaths, list.size() - deaths, deaths == 0 ? 0 : Math.round((float) damage / deaths),
			hungry, lost, Double.isNaN(hp) ? -1 : (int) Math.round(hp), Double.isNaN(fight) ? 0 : (int) Math.round(fight),
			List.copyOf(list), trend(list));
	}

	/** Compares the earlier and the recent half of a killer's entries (oldest first). */
	static Trend trend(List<StatEntry> list)
	{
		if (list.size() < TREND_MIN)
		{
			return null;
		}
		List<StatEntry> early = list.subList(0, list.size() / 2);
		List<StatEntry> recent = list.subList(list.size() / 2, list.size());
		int score = 0;
		List<String> reasons = new ArrayList<>();

		double survived = share(recent, StatEntry::isCloseCall) - share(early, StatEntry::isCloseCall);
		if (Math.abs(survived) >= 0.15)
		{
			score += survived > 0 ? 1 : -1;
			reasons.add(survived > 0 ? "You survive more of its close calls" : "More of its close calls end in death");
		}

		Predicate<StatEntry> death = e -> !e.isCloseCall();
		double ate = share(filter(recent, death), StatEntry::isAte) - share(filter(early, death), StatEntry::isAte);
		if (!Double.isNaN(ate) && Math.abs(ate) >= 0.25)
		{
			score += ate > 0 ? 1 : -1;
			reasons.add(ate > 0 ? "You eat before dying more often" : "You eat before dying less often");
		}

		double hp = average(recent, e -> e.getHpWhenHit() >= 0, StatEntry::getHpWhenHit)
			- average(early, e -> e.getHpWhenHit() >= 0, StatEntry::getHpWhenHit);
		if (!Double.isNaN(hp) && Math.abs(hp) >= 8)
		{
			score += hp > 0 ? 1 : -1;
			reasons.add(String.format("Your HP when it hits is %s (%+d%%)", hp > 0 ? "higher" : "lower", (int) Math.round(hp)));
		}

		double fight = average(recent, e -> e.getFightTicks() > 0, StatEntry::getFightTicks)
			- average(early, e -> e.getFightTicks() > 0, StatEntry::getFightTicks);
		double base = average(early, e -> e.getFightTicks() > 0, StatEntry::getFightTicks);
		if (!Double.isNaN(fight) && base > 0 && Math.abs(fight) / base >= 0.3)
		{
			reasons.add(fight > 0 ? "Fights last longer" : "Fights end sooner");
		}
		return new Trend(score, reasons);
	}

	private static List<StatEntry> filter(List<StatEntry> list, Predicate<StatEntry> keep)
	{
		List<StatEntry> out = new ArrayList<>();
		for (StatEntry e : list)
		{
			if (keep.test(e))
			{
				out.add(e);
			}
		}
		return out;
	}

	/** Fraction of {@code list} matching, NaN when empty. */
	private static double share(List<StatEntry> list, Predicate<StatEntry> match)
	{
		return list.isEmpty() ? Double.NaN : (double) filter(list, match).size() / list.size();
	}

	/** Average over the entries that have a value, NaN when none do. */
	private static double average(List<StatEntry> list, Predicate<StatEntry> has, ToDoubleFunction<StatEntry> value)
	{
		return filter(list, has).stream().mapToDouble(value).average().orElse(Double.NaN);
	}
}
