package com.deathrecap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Value;

/**
 * Milestones a death reaches against the deaths before it: round-number deaths, a first or Nth death
 * to a killer, the most expensive death yet and the longest gap between deaths. Close calls don't count.
 */
final class Milestones
{
	private static final long HOUR = 3_600_000L;
	private static final long DAY = 24 * HOUR;
	/** A death this long after the one before is called out even when it isn't the longest gap. */
	private static final long LONG_GAP = 7 * DAY;
	private static final int[] DEATH_COUNTS = {10, 25, 50, 100, 250, 500};
	private static final int[] KILLER_COUNTS = {5, 10, 25, 50, 100};

	/** The gaps between deaths, for the Stats tab. */
	@Value
	static class Gaps
	{
		/** Time of the latest death, 0 with no deaths. */
		long lastDeath;
		/** Longest time between two deaths in a row, 0 with fewer than two deaths. */
		long longest;
		/** The death that ended the longest gap, or null. */
		StatEntry longestEnd;
	}

	private Milestones()
	{
	}

	/**
	 * The milestones {@code death} reaches, given every entry recorded before it.
	 *
	 * @param history earlier deaths and close calls, in any order; entries after {@code death} are ignored
	 */
	static List<String> of(List<StatEntry> history, StatEntry death)
	{
		List<String> out = new ArrayList<>();
		if (death.isCloseCall())
		{
			return out;
		}
		List<StatEntry> deaths = deathsBefore(history, death.getTimestamp());
		int n = deaths.size() + 1;
		if (n == 1)
		{
			out.add("First death recorded");
			return out;
		}
		if (isRound(n, DEATH_COUNTS))
		{
			out.add(ordinal(n) + " death recorded");
		}

		String killer = death.getKiller();
		if (killer != null)
		{
			int byKiller = (int) deaths.stream().filter(e -> killer.equals(e.getKiller())).count() + 1;
			if (byKiller == 1)
			{
				out.add("First death to " + killer);
			}
			else if (isRound(byKiller, KILLER_COUNTS))
			{
				out.add(ordinal(byKiller) + " death to " + killer);
			}
		}

		long mostLost = deaths.stream().mapToLong(StatEntry::getLost).max().orElse(0);
		if (mostLost > 0 && death.getLost() > mostLost)
		{
			out.add("Most expensive death yet: " + ItemsOnDeath.gp(death.getLost()) + " gp (before: "
				+ ItemsOnDeath.gp(mostLost) + " gp)");
		}

		long gap = death.getTimestamp() - deaths.get(deaths.size() - 1).getTimestamp();
		long longest = gaps(deaths).getLongest();
		if (longest > 0 && gap > longest)
		{
			out.add("Longest gap between deaths: " + duration(gap) + " (before: " + duration(longest) + ")");
		}
		else if (gap >= LONG_GAP)
		{
			out.add("First death in " + duration(gap));
		}
		return out;
	}

	/** The latest death and the longest gap between deaths, over every entry. */
	static Gaps gaps(List<StatEntry> entries)
	{
		List<StatEntry> deaths = deathsBefore(entries, Long.MAX_VALUE);
		long longest = 0;
		StatEntry end = null;
		for (int i = 1; i < deaths.size(); i++)
		{
			long gap = deaths.get(i).getTimestamp() - deaths.get(i - 1).getTimestamp();
			if (gap > longest)
			{
				longest = gap;
				end = deaths.get(i);
			}
		}
		long last = deaths.isEmpty() ? 0 : deaths.get(deaths.size() - 1).getTimestamp();
		return new Gaps(last, longest, end);
	}

	/** Real deaths strictly before {@code time}, oldest first. */
	private static List<StatEntry> deathsBefore(List<StatEntry> entries, long time)
	{
		List<StatEntry> out = new ArrayList<>();
		for (StatEntry e : entries)
		{
			if (e != null && !e.isCloseCall() && e.getTimestamp() < time)
			{
				out.add(e);
			}
		}
		out.sort(Comparator.comparingLong(StatEntry::getTimestamp));
		return out;
	}

	private static boolean isRound(int n, int[] counts)
	{
		for (int c : counts)
		{
			if (n == c)
			{
				return true;
			}
		}
		return n > counts[counts.length - 1] && n % 1000 == 0;
	}

	/** 1 -> "1st", 22 -> "22nd", 113 -> "113th". */
	static String ordinal(int n)
	{
		int tens = n % 100;
		String suffix = tens >= 11 && tens <= 13 ? "th"
			: n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
		return n + suffix;
	}

	/** "45 minutes", "5 hours", "3 days 4 hours", "41 days". */
	static String duration(long millis)
	{
		long days = millis / DAY;
		long hours = millis % DAY / HOUR;
		if (days >= 10)
		{
			return plural(days, "day");
		}
		if (days > 0)
		{
			return plural(days, "day") + (hours > 0 ? " " + plural(hours, "hour") : "");
		}
		if (hours > 0)
		{
			return plural(hours, "hour");
		}
		return plural(Math.max(1, millis / 60_000L), "minute");
	}

	private static String plural(long n, String unit)
	{
		return n + " " + unit + (n == 1 ? "" : "s");
	}
}
