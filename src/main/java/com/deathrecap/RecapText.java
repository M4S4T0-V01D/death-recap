package com.deathrecap;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plain-text rendering of recaps, shared by the panel, the clipboard export and chat.
 */
final class RecapText
{
	private RecapText()
	{
	}

	static String describe(RecapEvent e)
	{
		String detail = e.getDetail() != null ? " (" + e.getDetail() + ")" : "";
		switch (e.getType())
		{
			case HIT:
				return "Hit " + e.getAmount() + " from " + e.getSource() + detail;
			case DAMAGE_OVER_TIME:
				return e.getSource() + " " + e.getAmount();
			case BLOCK:
				return "Blocked a hit from " + e.getSource() + detail;
			case HEAL:
				return "Healed " + e.getAmount();
			case ATTACK:
				return e.getSource() + " attacked";
			case PROJECTILE:
				return e.getSource() + " launched a projectile";
			case EAT:
				return "Ate " + e.getSource();
			case DRINK:
				return "Drank " + e.getSource();
			case PRAYER_ON:
				return e.getSource() + " on";
			case PRAYER_OFF:
				return e.getSource() + " off";
			case PRAYER_EMPTY:
				return "Out of prayer";
			default:
				return e.getSource();
		}
	}

	static String offset(int tick, int deathTick)
	{
		if (tick == deathTick)
		{
			return "0.0s";
		}
		return tick > deathTick ? "+" + RecapAnalyzer.seconds(tick - deathTick) : "-" + RecapAnalyzer.seconds(deathTick - tick);
	}

	static String location(DeathRecord r)
	{
		return String.format("World %d, region %d (%d, %d, %d)", r.getWorld(), r.getRegionId(), r.getX(), r.getY(), r.getPlane());
	}

	static String date(long timestamp)
	{
		return new SimpleDateFormat("MMM d, HH:mm").format(new Date(timestamp));
	}

	/** "killed by X", "close call against X" or "clip" (with who hit hardest, if anyone did). */
	static String headline(DeathRecord r, RecapAnalyzer.Summary s)
	{
		if (r.isClip())
		{
			return s.getTotalDamage() > 0 ? "clip, fighting " + s.getKiller() : "clip";
		}
		return (r.isCloseCall() ? "close call against " : "killed by ") + s.getKiller();
	}

	static String full(DeathRecord r, RecapAnalyzer.Summary s)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Death Recap: ").append(headline(r, s)).append('\n');
		sb.append(date(r.getTimestamp())).append(" - ").append(location(r)).append("\n\n");
		for (String f : s.getFindings())
		{
			sb.append("* ").append(f).append('\n');
		}
		DeathState state = r.getState();
		if (state != null)
		{
			sb.append(r.isCloseCall() ? "\nAt the low point:\n" : r.isClip() ? "\nWhen the clip was saved:\n" : "\nAt death:\n");
			for (String line : stateLines(state))
			{
				sb.append("  ").append(line).append('\n');
			}
			if (r.isDeath())
			{
				ItemsOnDeath.Result items = ItemsOnDeath.compute(state);
				sb.append(String.format("  Items at risk (estimate): %s gp; kept: %s gp%n",
					ItemsOnDeath.gp(items.getRiskValue()), ItemsOnDeath.gp(items.getKeptValue())));
			}
		}
		sb.append("\nTimeline:\n");
		List<RecapEvent> events = r.getEvents().stream()
			.sorted(Comparator.comparingInt(RecapEvent::getTick))
			.collect(Collectors.toList());
		for (RecapEvent e : events)
		{
			sb.append(String.format("%7s  %s%n", offset(e.getTick(), r.getDeathTick()), describe(e)));
		}
		return sb.toString();
	}

	/** " Lost about 1.2M gp (Abyssal whip, ...)." for chat, or "" when nothing was at risk or it's unknown. */
	static String lostSentence(DeathRecord r)
	{
		if (!r.isDeath() || r.getState() == null)
		{
			return "";
		}
		ItemsOnDeath.Result items = ItemsOnDeath.compute(r.getState());
		if (items.getAtRisk().isEmpty())
		{
			return " Nothing lost.";
		}
		String names = items.getAtRisk().stream().limit(3).map(RecapText::stack).collect(Collectors.joining(", "));
		return " Lost about " + ItemsOnDeath.gp(items.getRiskValue()) + " gp (" + names
			+ (items.getAtRisk().size() > 3 ? ", ..." : "") + ").";
	}

	/** "3 x Shark", or just the name for one. */
	static String stack(DeathState.ItemStack s)
	{
		return (s.getQuantity() > 1 ? s.getQuantity() + " x " : "") + s.getName();
	}

	/** Weapon, specials, prayers and stats at the moment of death, one line each; empty parts are left out. */
	static List<String> stateLines(DeathState s)
	{
		List<String> lines = new ArrayList<>();
		if (s.getWeapon() != null)
		{
			lines.add("Weapon: " + s.getWeapon() + (s.getAttackStyle() != null ? " (" + s.getAttackStyle() + ")" : ""));
		}
		lines.add(String.format("Special attack %d%%, run energy %d%%%s%s", s.getSpecEnergy(), s.getRunEnergy(),
			s.isSkulled() ? ", skulled" : "", s.isProtectItem() ? ", Protect Item on" : ""));
		if (s.getPrayers() != null && !s.getPrayers().isEmpty())
		{
			lines.add("Prayers: " + String.join(", ", s.getPrayers()));
		}
		if (s.getStats() != null && !s.getStats().isEmpty())
		{
			lines.add("Stats: " + s.getStats().stream()
				.map(st -> st.getSkill() + " " + st.getBoosted() + "/" + st.getReal())
				.collect(Collectors.joining(", ")));
		}
		return lines;
	}
}
