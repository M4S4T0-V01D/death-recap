package com.deathrecap;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A few numbers about one death or close call, kept for lifetime stats after the full recap is gone.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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

	static StatEntry of(DeathRecord record, RecapAnalyzer.Summary summary)
	{
		DeathState state = record.getState();
		boolean death = !record.isCloseCall() && state != null;
		long lost = death ? ItemsOnDeath.compute(state).getRiskValue() : 0;
		boolean left = death && (count(state.getFood()) > 0 || count(state.getPotions()) > 0);
		boolean ate = record.getEvents().stream()
			.anyMatch(e -> e.getType() == RecapEvent.Type.EAT || e.getType() == RecapEvent.Type.DRINK);
		int lowest = record.getTicks().stream().mapToInt(TickSnapshot::getHp).min().orElse(0);
		return new StatEntry(record.getTimestamp(), summary.getKiller(), summary.getTotalDamage(), ate,
			record.isCloseCall(), record.isCloseCall() ? lowest : 0, lost, left);
	}

	/** Total quantity of the stacks, 0 for null. */
	static int count(List<DeathState.ItemStack> stacks)
	{
		return stacks == null ? 0 : stacks.stream().mapToInt(DeathState.ItemStack::getQuantity).sum();
	}
}
