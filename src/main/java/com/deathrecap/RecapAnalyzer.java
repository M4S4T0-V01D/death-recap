package com.deathrecap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * Turns a raw {@link DeathRecord} into a killing blow, a damage breakdown and plain-language findings.
 * Everything here is a statement of what happened; it never suggests what to do in combat.
 */
public final class RecapAnalyzer
{
	public static final double TICK_SECONDS = 0.6;

	@Value
	public static class SourceDamage
	{
		String source;
		int damage;
		int hits;
	}

	@Value
	public static class Summary
	{
		String killer;
		RecapEvent killingBlow;
		int totalDamage;
		List<SourceDamage> bySource;
		RecapEvent biggestHit;
		List<String> findings;
	}

	private RecapAnalyzer()
	{
	}

	public static Summary analyze(DeathRecord record)
	{
		final int deathTick = record.getDeathTick();
		final List<RecapEvent> events = new ArrayList<>(record.getEvents());
		events.sort(Comparator.comparingInt(RecapEvent::getTick));
		final List<TickSnapshot> ticks = new ArrayList<>(record.getTicks());
		ticks.sort(Comparator.comparingInt(TickSnapshot::getTick));

		final List<RecapEvent> damage = events.stream().filter(RecapEvent::isDamage).collect(Collectors.toList());
		if (record.isCloseCall())
		{
			return closeCall(record, events, ticks, damage);
		}
		if (record.isClip())
		{
			return clip(record, events, ticks, damage);
		}
		final RecapEvent killingBlow = damage.isEmpty() ? null : damage.get(damage.size() - 1);
		final String killer = killerOf(damage);

		int total = damage.stream().mapToInt(RecapEvent::getAmount).sum();
		RecapEvent biggest = biggest(damage);
		final List<SourceDamage> bySource = bySource(damage);

		final List<String> findings = new ArrayList<>();
		if (killingBlow != null)
		{
			TickSnapshot before = snapshotAt(ticks, killingBlow.getTick() - 1);
			findings.add(String.format("Killing blow: %d from %s%s%s.%s", killingBlow.getAmount(), label(record, killingBlow.getSource()),
				killingBlow.getWeapon() != null ? " with " + killingBlow.getWeapon() : "",
				killingBlow.getDetail() != null ? " (" + killingBlow.getDetail() + ")" : "",
				before != null && before.getHp() > 0 ? String.format(" You had %d HP.", before.getHp()) : ""));
		}
		else
		{
			findings.add("No damage was recorded before this death.");
		}

		if (total > 0)
		{
			int firstDamageTick = damage.get(0).getTick();
			int span = deathTick - firstDamageTick + 1;
			StringBuilder sb = new StringBuilder(String.format("You took %d damage in %s", total, seconds(span)));
			if (span >= 3)
			{
				sb.append(String.format(Locale.ROOT, " (%.1f per second)", total / (span * TICK_SECONDS)));
			}
			if (bySource.size() > 1)
			{
				sb.append(": ");
				int shown = Math.min(3, bySource.size());
				for (int i = 0; i < shown; i++)
				{
					SourceDamage s = bySource.get(i);
					if (i > 0)
					{
						sb.append(", ");
					}
					sb.append(String.format("%s %d (%d%%)", s.getSource(), s.getDamage(), Math.round(100.0 * s.getDamage() / total)));
				}
			}
			sb.append('.');
			findings.add(sb.toString());
		}

		if (biggest != null && biggest != killingBlow)
		{
			findings.add(String.format("Biggest hit: %d from %s, %s before death.", biggest.getAmount(), biggest.getSource(),
				seconds(deathTick - biggest.getTick())));
		}

		burst(damage, deathTick, "death").ifPresent(findings::add);
		playerFindings(record, events, findings);
		hpFindings(ticks, events, deathTick, findings);
		prayerFindings(ticks, events, killingBlow, deathTick, findings);
		suppliesFinding(record.getState(), findings);

		int dot = damage.stream().filter(e -> e.getType() == RecapEvent.Type.DAMAGE_OVER_TIME).mapToInt(RecapEvent::getAmount).sum();
		if (dot > 0)
		{
			Set<String> kinds = damage.stream().filter(e -> e.getType() == RecapEvent.Type.DAMAGE_OVER_TIME)
				.map(RecapEvent::getSource).collect(Collectors.toCollection(LinkedHashSet::new));
			findings.add(String.format("%s dealt %d damage over time.", String.join(" and ", kinds), dot));
		}

		return new Summary(killer, killingBlow, total, bySource, biggest, findings);
	}

	/**
	 * Findings for a close call. The record's death tick is when HP first fell below the close call
	 * threshold; the record runs on until the fight calmed down.
	 */
	private static Summary closeCall(DeathRecord record, List<RecapEvent> events, List<TickSnapshot> ticks, List<RecapEvent> damage)
	{
		final int lowTick = record.getDeathTick();
		final List<RecapEvent> before = damage.stream().filter(e -> e.getTick() <= lowTick).collect(Collectors.toList());
		// The hit that took HP low: the last direct hit up to the threshold tick.
		RecapEvent low = null;
		for (RecapEvent e : before)
		{
			if (e.getType() == RecapEvent.Type.HIT)
			{
				low = e;
			}
		}
		final String killer = killerOf(before.isEmpty() ? damage : before);
		int total = damage.stream().mapToInt(RecapEvent::getAmount).sum();
		RecapEvent biggest = biggest(damage);
		List<SourceDamage> bySource = bySource(damage);
		List<String> findings = new ArrayList<>();

		TickSnapshot lowest = null;
		int maxHp = 1;
		for (TickSnapshot t : ticks)
		{
			maxHp = Math.max(maxHp, t.getMaxHp());
			if (t.getHp() > 0 && (lowest == null || t.getHp() < lowest.getHp()))
			{
				lowest = t;
			}
		}
		if (lowest != null)
		{
			findings.add(String.format("HP fell to %d of %d (%d%%) and you survived.", lowest.getHp(), maxHp,
				Math.round(100.0 * lowest.getHp() / maxHp)));
		}
		if (low != null)
		{
			findings.add(String.format("The hit that took you low: %d from %s%s%s.", low.getAmount(), label(record, low.getSource()),
				low.getWeapon() != null ? " with " + low.getWeapon() : "", low.getDetail() != null ? " (" + low.getDetail() + ")" : ""));
			TickSnapshot at = snapshotAt(ticks, low.getTick() - 1);
			if (at != null && low.getType() == RecapEvent.Type.HIT)
			{
				findings.add(at.getProtection() != null
					? at.getProtection() + " was active when it landed."
					: "No protection prayer was active when it landed.");
			}
		}
		if (total > 0)
		{
			findings.add(String.format("You took %d damage in %s.", total,
				seconds(damage.get(damage.size() - 1).getTick() - damage.get(0).getTick() + 1)));
		}
		if (biggest != null && biggest != low)
		{
			findings.add(String.format("Biggest hit: %d from %s.", biggest.getAmount(), biggest.getSource()));
		}
		burst(damage, lowTick, "HP dropped low").ifPresent(findings::add);
		playerFindings(record, events, findings);

		List<RecapEvent> after = events.stream()
			.filter(e -> e.getTick() >= lowTick && (e.getType() == RecapEvent.Type.EAT || e.getType() == RecapEvent.Type.DRINK))
			.collect(Collectors.toList());
		if (after.isEmpty())
		{
			findings.add("You didn't eat or drink after dropping low.");
		}
		else
		{
			RecapEvent first = after.get(0);
			findings.add(String.format("You ate or drank %d time%s after dropping low; the first was %s, %s later.", after.size(),
				after.size() == 1 ? "" : "s", first.getSource(), seconds(first.getTick() - lowTick)));
		}
		return new Summary(killer, low, total, bySource, biggest, findings);
	}

	/**
	 * Findings for a clip saved with the hotkey. The record's death tick is the key press; nothing died.
	 */
	private static Summary clip(DeathRecord record, List<RecapEvent> events, List<TickSnapshot> ticks, List<RecapEvent> damage)
	{
		final int pressTick = record.getDeathTick();
		final String killer = damage.isEmpty() ? "Nothing" : killerOf(damage);
		int total = damage.stream().mapToInt(RecapEvent::getAmount).sum();
		RecapEvent biggest = biggest(damage);
		List<SourceDamage> bySource = bySource(damage);
		List<String> findings = new ArrayList<>();
		if (total > 0)
		{
			StringBuilder sb = new StringBuilder(String.format("You took %d damage in %s", total,
				seconds(damage.get(damage.size() - 1).getTick() - damage.get(0).getTick() + 1)));
			if (bySource.size() > 1)
			{
				sb.append(": ");
				int shown = Math.min(3, bySource.size());
				for (int i = 0; i < shown; i++)
				{
					SourceDamage s = bySource.get(i);
					sb.append(i > 0 ? ", " : "").append(String.format("%s %d", s.getSource(), s.getDamage()));
				}
			}
			findings.add(sb.append('.').toString());
		}
		else
		{
			findings.add("You took no damage in this clip.");
		}
		if (biggest != null)
		{
			findings.add(String.format("Biggest hit: %d from %s, %s before the clip was saved.", biggest.getAmount(),
				biggest.getSource(), seconds(pressTick - biggest.getTick())));
		}
		burst(damage, pressTick, "the clip was saved").ifPresent(findings::add);
		playerFindings(record, events, findings);
		if (!ticks.isEmpty())
		{
			TickSnapshot first = ticks.get(0);
			TickSnapshot last = ticks.get(ticks.size() - 1);
			int lowest = ticks.stream().mapToInt(TickSnapshot::getHp).min().orElse(first.getHp());
			findings.add(String.format("HP went from %d to %d; the lowest was %d.", first.getHp(), last.getHp(), lowest));
		}
		long eats = events.stream().filter(e -> e.getType() == RecapEvent.Type.EAT || e.getType() == RecapEvent.Type.DRINK).count();
		if (eats > 0)
		{
			findings.add(String.format("You ate or drank %d time%s.", eats, eats == 1 ? "" : "s"));
		}
		return new Summary(killer, null, total, bySource, biggest, findings);
	}

	/** "Zezima (level 126)" when the attacker's combat level was recorded, otherwise just the name. */
	public static String label(DeathRecord record, String name)
	{
		Attacker a = record.attacker(name);
		return a != null && a.getCombatLevel() > 0 ? name + " (level " + a.getCombatLevel() + ")" : name;
	}

	/** The weapons a player attacker was seen holding, in the order they were first seen. */
	public static List<String> weapons(DeathRecord record, String name)
	{
		return record.getEvents().stream()
			.sorted(Comparator.comparingInt(RecapEvent::getTick))
			.filter(e -> name.equals(e.getSource()) && e.getWeapon() != null)
			.map(RecapEvent::getWeapon)
			.distinct()
			.collect(Collectors.toList());
	}

	/** True when another player dealt damage in this recap. */
	public static boolean isPvp(DeathRecord record)
	{
		return record.getAttackers() != null && record.getAttackers().stream()
			.anyMatch(a -> a.isPlayer() && record.getEvents().stream().anyMatch(e -> e.isDamage() && a.getName().equals(e.getSource())));
	}

	/** For each player that attacked: their hits, zeros, biggest hit and the weapons they used. */
	private static void playerFindings(DeathRecord record, List<RecapEvent> events, List<String> findings)
	{
		if (record.getAttackers() == null)
		{
			return;
		}
		List<Attacker> players = record.getAttackers().stream().filter(Attacker::isPlayer).collect(Collectors.toList());
		List<String> lines = new ArrayList<>();
		for (Attacker p : players)
		{
			List<RecapEvent> hits = events.stream()
				.filter(e -> p.getName().equals(e.getSource()) && (e.getType() == RecapEvent.Type.HIT || e.getType() == RecapEvent.Type.BLOCK))
				.collect(Collectors.toList());
			if (hits.isEmpty())
			{
				continue;
			}
			long zeros = hits.stream().filter(e -> e.getAmount() == 0).count();
			int max = hits.stream().mapToInt(RecapEvent::getAmount).max().orElse(0);
			List<String> used = weapons(record, p.getName());
			StringBuilder sb = new StringBuilder(label(record, p.getName()));
			sb.append(String.format(" hit you %d time%s", hits.size(), hits.size() == 1 ? "" : "s"));
			if (zeros > 0)
			{
				sb.append(String.format(" (%d for 0)", zeros));
			}
			sb.append(String.format(", up to %d", max));
			if (!used.isEmpty())
			{
				sb.append(", using ").append(joinAnd(used));
			}
			lines.add(sb.append('.').toString());
		}
		if (lines.size() > 1)
		{
			findings.add(String.format("%d players attacked you.", lines.size()));
		}
		findings.addAll(lines);
	}

	static String joinAnd(List<String> parts)
	{
		if (parts.size() <= 1)
		{
			return String.join("", parts);
		}
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
	}

	/** HP regained between snapshots: food, potions, heals and regeneration. */
	static int healed(List<TickSnapshot> ticks)
	{
		int healed = 0;
		for (int i = 1; i < ticks.size(); i++)
		{
			healed += Math.max(0, ticks.get(i).getHp() - ticks.get(i - 1).getHp());
		}
		return healed;
	}

	/** What food and potions were still in the inventory at death; skipped for recaps from before this was recorded. */
	static void suppliesFinding(DeathState state, List<String> findings)
	{
		if (state == null || state.getFood() == null || state.getPotions() == null)
		{
			return;
		}
		List<DeathState.ItemStack> all = new ArrayList<>(state.getFood());
		all.addAll(state.getPotions());
		if (all.isEmpty())
		{
			findings.add("You had no food or potions left.");
			return;
		}
		List<String> names = new ArrayList<>();
		for (int i = 0; i < Math.min(3, all.size()); i++)
		{
			DeathState.ItemStack s = all.get(i);
			names.add(s.getQuantity() + " x " + s.getName());
		}
		String more = all.size() > 3 ? String.format(" (+%d more)", all.size() - 3) : "";
		String what = state.getFood().isEmpty() ? "potions" : state.getPotions().isEmpty() ? "food" : "food and potions";
		findings.add(String.format("You died with %s left: %s%s.", what, String.join(", ", names), more));
	}

	private static RecapEvent biggest(List<RecapEvent> damage)
	{
		RecapEvent biggest = null;
		for (RecapEvent e : damage)
		{
			if (biggest == null || e.getAmount() > biggest.getAmount())
			{
				biggest = e;
			}
		}
		return biggest;
	}

	private static List<SourceDamage> bySource(List<RecapEvent> damage)
	{
		final Map<String, int[]> totals = new LinkedHashMap<>();
		for (RecapEvent e : damage)
		{
			int[] t = totals.computeIfAbsent(e.getSource(), k -> new int[2]);
			t[0] += e.getAmount();
			t[1]++;
		}
		return totals.entrySet().stream()
			.map(en -> new SourceDamage(en.getKey(), en.getValue()[0], en.getValue()[1]))
			.sorted(Comparator.comparingInt(SourceDamage::getDamage).reversed())
			.collect(Collectors.toList());
	}

	/**
	 * The killer is whoever dealt the last direct hit; damage over time only counts when nothing else hit.
	 */
	static String killerOf(List<RecapEvent> damage)
	{
		for (int i = damage.size() - 1; i >= 0; i--)
		{
			RecapEvent e = damage.get(i);
			if (e.getType() == RecapEvent.Type.HIT && !"Unknown".equals(e.getSource()))
			{
				return e.getSource();
			}
		}
		return damage.isEmpty() ? "Unknown" : damage.get(damage.size() - 1).getSource();
	}

	private static Optional<String> burst(List<RecapEvent> damage, int deathTick, String moment)
	{
		Map<Integer, List<RecapEvent>> byTick = new TreeMap<>();
		for (RecapEvent e : damage)
		{
			byTick.computeIfAbsent(e.getTick(), k -> new ArrayList<>()).add(e);
		}
		List<RecapEvent> best = null;
		int bestSum = 0;
		for (List<RecapEvent> group : byTick.values())
		{
			int sum = group.stream().mapToInt(RecapEvent::getAmount).sum();
			if (group.size() > 1 && sum > bestSum)
			{
				best = group;
				bestSum = sum;
			}
		}
		if (best == null)
		{
			return Optional.empty();
		}
		Set<String> who = best.stream().map(RecapEvent::getSource).collect(Collectors.toCollection(LinkedHashSet::new));
		int before = deathTick - best.get(0).getTick();
		String when = before >= 0 ? seconds(before) + " before " + moment : seconds(-before) + " after " + moment;
		return Optional.of(String.format("Biggest single-tick burst: %d damage from %d hits (%s), %s.",
			bestSum, best.size(), String.join(", ", who), when));
	}

	private static void hpFindings(List<TickSnapshot> ticks, List<RecapEvent> events, int deathTick, List<String> findings)
	{
		if (ticks.isEmpty())
		{
			return;
		}
		TickSnapshot first = ticks.get(0);
		int maxHp = Math.max(1, first.getMaxHp());
		int peak = ticks.stream().mapToInt(TickSnapshot::getHp).max().orElse(0);
		if (peak > 0)
		{
			int peakTick = 0;
			for (TickSnapshot t : ticks)
			{
				if (t.getHp() == peak)
				{
					peakTick = t.getTick();
				}
			}
			findings.add(String.format("HP went from %d to 0 in %s.", peak, seconds(deathTick - peakTick)));
		}

		List<RecapEvent> consumed = events.stream()
			.filter(e -> e.getType() == RecapEvent.Type.EAT || e.getType() == RecapEvent.Type.DRINK)
			.collect(Collectors.toList());
		int lowSince = sinceBelow(ticks, maxHp * 0.25);
		int halfSince = sinceBelow(ticks, maxHp * 0.5);
		boolean noFood = halfSince >= 0 && deathTick - halfSince >= 2 && consumed.stream().noneMatch(e -> e.getTick() >= halfSince);
		// When HP was already under a quarter for as long as it was under half, one line says both.
		boolean sameSpan = noFood && lowSince == halfSince;
		if (lowSince >= 0 && deathTick - lowSince >= 2)
		{
			findings.add(String.format("You were at or below 25%% HP for the last %s%s.", seconds(deathTick - lowSince),
				sameSpan ? " and didn't eat or drink" : ""));
		}

		if (noFood)
		{
			if (!sameSpan)
			{
				findings.add(String.format("You were below 50%% HP for the last %s and didn't eat or drink.", seconds(deathTick - halfSince)));
			}
		}
		else if (consumed.isEmpty())
		{
			findings.add(String.format("You didn't eat or drink in the last %s.", seconds(deathTick - first.getTick())));
		}
		else
		{
			RecapEvent last = consumed.get(consumed.size() - 1);
			int healed = healed(ticks.stream().filter(t -> t.getTick() <= deathTick).collect(Collectors.toList()));
			findings.add(String.format("You ate or drank %d time%s%s; the last was %s, %s before death.", consumed.size(),
				consumed.size() == 1 ? "" : "s", healed > 0 ? " and healed " + healed + " HP" : "",
				last.getSource(), seconds(deathTick - last.getTick())));
		}
	}

	/**
	 * Returns the tick from which HP stayed at or below {@code threshold} until death, or -1.
	 */
	private static int sinceBelow(List<TickSnapshot> ticks, double threshold)
	{
		int since = -1;
		for (int i = ticks.size() - 1; i >= 0; i--)
		{
			if (ticks.get(i).getHp() > threshold)
			{
				break;
			}
			since = ticks.get(i).getTick();
		}
		return since;
	}

	private static void prayerFindings(List<TickSnapshot> ticks, List<RecapEvent> events, RecapEvent killingBlow, int deathTick, List<String> findings)
	{
		if (killingBlow != null && killingBlow.getType() == RecapEvent.Type.HIT)
		{
			TickSnapshot at = snapshotAt(ticks, killingBlow.getTick() - 1);
			if (at != null)
			{
				findings.add(at.getProtection() != null
					? at.getProtection() + " was active when the killing blow landed."
					: "No protection prayer was active when the killing blow landed.");
			}
		}

		events.stream()
			.filter(e -> e.getType() == RecapEvent.Type.PRAYER_EMPTY)
			.reduce((a, b) -> b)
			.ifPresent(e -> findings.add(String.format("Prayer ran out %s before death.", seconds(deathTick - e.getTick()))));
	}

	/**
	 * Latest snapshot at or before {@code tick}; prayers are read at the end of the tick, so the
	 * state the hit landed against is the previous tick's.
	 */
	private static TickSnapshot snapshotAt(List<TickSnapshot> ticks, int tick)
	{
		TickSnapshot found = null;
		for (TickSnapshot t : ticks)
		{
			if (t.getTick() > tick)
			{
				break;
			}
			found = t;
		}
		return found != null ? found : (ticks.isEmpty() ? null : ticks.get(0));
	}

	public static String seconds(int ticks)
	{
		return String.format(Locale.ROOT, "%.1fs", Math.max(0, ticks) * TICK_SECONDS);
	}
}
