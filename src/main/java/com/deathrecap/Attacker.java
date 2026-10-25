package com.deathrecap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Who attacked the player in a recap: an NPC or, in PvP, another player.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Attacker
{
	private String name;
	/** Combat level, or 0 when unknown. */
	private int combatLevel;
	/** True for another player. */
	private boolean player;
}
