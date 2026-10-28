package com.deathrecap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A game sound effect heard during a replay, played again by the client when the replay is watched. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplaySound
{
	/** When the sound played, in milliseconds relative to the record's timestamp, like the replay frames. */
	private int time;
	private int id;
	/** True for an area sound (hits, spells, monsters), false for one the player's own client plays. */
	private boolean area;
}
