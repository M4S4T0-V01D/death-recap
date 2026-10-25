package com.deathrecap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Lifetime totals over every recorded death and close call, overall and per killer.
 */
final class DeathStats
{
	@Value
	static class Killer
	{
		String name;
		int deaths;
		int closeCalls;
		int averageDamage;
		int withoutEating;
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
	}

	private DeathStats()
	{
	}

	/** @param sessionStart epoch millis; deaths from then on count toward the session totals */
	static Report compute(List<StatEntry> entries, long sessionStart)
	{
		long lost = 0;
		int sessionDeaths = 0;
		long sessionLost = 0;
		StatEntry mostExpensive = null;
		int withSupplies = 0;
		Map<String, int[]> byKiller = new LinkedHashMap<>();
		int deaths = 0;
		int closeCalls = 0;
		long damage = 0;
		int hungry = 0;
		for (StatEntry e : entries)
		{
			// deaths, close calls, death damage, deaths without eating
			int[] k = byKiller.computeIfAbsent(e.getKiller() == null ? "Unknown" : e.getKiller(), n -> new int[4]);
			if (e.isCloseCall())
			{
				closeCalls++;
				k[1]++;
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
			k[0]++;
			k[2] += e.getDamage();
			if (!e.isAte())
			{
				hungry++;
				k[3]++;
			}
		}
		List<Killer> killers = new ArrayList<>();
		for (Map.Entry<String, int[]> en : byKiller.entrySet())
		{
			int[] k = en.getValue();
			killers.add(new Killer(en.getKey(), k[0], k[1], k[0] == 0 ? 0 : Math.round((float) k[2] / k[0]), k[3]));
		}
		killers.sort(Comparator.comparingInt(Killer::getDeaths).thenComparingInt(Killer::getCloseCalls).reversed());
		return new Report(deaths, closeCalls, deaths == 0 ? 0 : (int) Math.round((double) damage / deaths), hungry, killers,
			lost, sessionDeaths, sessionLost, mostExpensive, withSupplies);
	}
}
