package com.deathrecap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The player's vitals at the end of a game tick.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TickSnapshot
{
	private int tick;
	private int hp;
	private int maxHp;
	private int prayer;
	/** Active protection prayer name, or null. */
	private String protection;
}
