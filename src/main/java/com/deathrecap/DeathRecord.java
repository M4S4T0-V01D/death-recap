package com.deathrecap;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Everything recorded in the window before one death. Stored as JSON.
 */
@Data
@NoArgsConstructor
public class DeathRecord
{
	private long timestamp;
	private int world;
	private int regionId;
	private int x;
	private int y;
	private int plane;
	private int deathTick;
	private String killer;
	/** File name of the death screenshot in the media folder, or null. */
	private String screenshot;
	/** File name of the replay GIF in the media folder, or null. */
	private String replay;
	/** When each replay frame was captured, in milliseconds relative to {@link #timestamp}. May be null. */
	private int[] replayTimes;
	/** File name of the share card in the media folder, or null. */
	private String card;
	/**
	 * True for a close call: HP fell to the close-call threshold and the player survived. Its
	 * {@link #deathTick} and {@link #timestamp} mark the moment HP first fell that low.
	 */
	private boolean closeCall;
	/** True for a clip saved with the clip hotkey; its {@link #deathTick} and {@link #timestamp} mark the key press. */
	private boolean clip;
	/** Stats, prayers and items at the moment of death; null for older recaps. */
	private DeathState state;
	/** Everyone who attacked in this recap, with combat levels; null for older recaps. */
	private List<Attacker> attackers;
	private List<TickSnapshot> ticks = new ArrayList<>();
	private List<RecapEvent> events = new ArrayList<>();

	/** The recorded attacker with this name, or null. */
	public Attacker attacker(String name)
	{
		if (attackers != null && name != null)
		{
			for (Attacker a : attackers)
			{
				if (name.equals(a.getName()))
				{
					return a;
				}
			}
		}
		return null;
	}

	/** True for a real death; false for close calls and clips. */
	public boolean isDeath()
	{
		return !closeCall && !clip;
	}
}
