package com.deathrecap;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A few numbers about one death or close call, kept for lifetime stats after the full recap is gone.
 */
@Data
@NoArgsConstructor
public class StatEntry
{
	private long timestamp;
	private String killer;
	private int damage;
	/** Whether the player ate or drank anything in the recap window. */
	private boolean ate;
	private boolean closeCall;
	/** Lowest HP in the recap window. */
	private int lowestHp;
	/** Estimated value lost, in gp; 0 for close calls and older entries. */
	private long lost;
	/** Whether food or potions were still carried at death. */
	private boolean suppliesLeft;
	/** Where it happened; 0, 0 for older entries. */
	private int x;
	private int y;
	private int plane;
	/** Average HP, in percent, just before each of the killer's hits landed; -1 when unknown. */
	private int hpWhenHit = -1;
	/** Ticks from the first damage taken to the death; 0 when unknown. */
	private int fightTicks;

	StatEntry(long timestamp, String killer, int damage, boolean ate, boolean closeCall, int lowestHp, long lost,
		boolean suppliesLeft)
	{
		this.timestamp = timestamp;
		this.killer = killer;
		this.damage = damage;
		this.ate = ate;
		this.closeCall = closeCall;
		this.lowestHp = lowestHp;
		this.lost = lost;
		this.suppliesLeft = suppliesLeft;
	}

	boolean hasLocation()
	{
		return x != 0 || y != 0;
	}

	static StatEntry of(DeathRecord record, RecapAnalyzer.Summary summary)
	{
		DeathState state = record.getState();
		boolean death = !record.isCloseCall() && state != null;
		long lost = death ? ItemsOnDeath.compute(state).getRiskValue() : 0;
		boolean left = death && (count(state.getFood()) > 0 || count(state.getPotions()) > 0);
		boolean ate = record.getEvents().stream()
			.anyMatch(e -> e.getType() == RecapEvent.Type.EAT || e.getType() == RecapEvent.Type.DRINK);
		int lowest = record.getTicks().stream().mapToInt(TickSnapshot::getHp).min().orElse(0);
		StatEntry entry = new StatEntry(record.getTimestamp(), summary.getKiller(), summary.getTotalDamage(), ate,
			record.isCloseCall(), record.isCloseCall() ? lowest : 0, lost, left);
		entry.fillFrom(record, summary.getKiller());
		return entry;
	}

	/** Sets the fields that older entries lack from the recap they were made from. */
	void fillFrom(DeathRecord record, String killer)
	{
		x = record.getX();
		y = record.getY();
		plane = record.getPlane();
		hpWhenHit = hpWhenHit(record, killer);
		fightTicks = record.getEvents().stream().filter(RecapEvent::isDamage)
			.mapToInt(e -> record.getDeathTick() - e.getTick()).max().orElse(0);
	}

	/** True when {@link #fillFrom} would add something. */
	boolean missingDetails()
	{
		return !hasLocation() || hpWhenHit < 0;
	}

	/** Average HP percent at the tick before each hit from {@code killer}, or -1. */
	static int hpWhenHit(DeathRecord record, String killer)
	{
		List<TickSnapshot> ticks = record.getTicks();
		int sum = 0;
		int n = 0;
		for (RecapEvent e : record.getEvents())
		{
			if (e.getType() != RecapEvent.Type.HIT || e.getAmount() <= 0 || killer == null || !killer.equals(e.getSource()))
			{
				continue;
			}
			TickSnapshot before = null;
			for (TickSnapshot t : ticks)
			{
				if (t.getTick() < e.getTick() && (before == null || t.getTick() > before.getTick()))
				{
					before = t;
				}
			}
			if (before != null && before.getMaxHp() > 0)
			{
				sum += Math.min(100, before.getHp() * 100 / before.getMaxHp());
				n++;
			}
		}
		return n == 0 ? -1 : Math.round((float) sum / n);
	}

	/** Total quantity of the stacks, 0 for null. */
	static int count(List<DeathState.ItemStack> stacks)
	{
		return stacks == null ? 0 : stacks.stream().mapToInt(DeathState.ItemStack::getQuantity).sum();
	}
}
