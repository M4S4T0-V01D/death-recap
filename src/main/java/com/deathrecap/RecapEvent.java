package com.deathrecap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Something that happened to or by the player in the moments before a death.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecapEvent
{
	public enum Type
	{
		/** Direct damage from an attacker. */
		HIT,
		/** Poison, venom, burn, bleed and other damage over time. */
		DAMAGE_OVER_TIME,
		/** A zero hitsplat. */
		BLOCK,
		HEAL,
		/** An attacker that was targeting the player started an animation. */
		ATTACK,
		/** A projectile was launched at the player. */
		PROJECTILE,
		EAT,
		DRINK,
		PRAYER_ON,
		PRAYER_OFF,
		PRAYER_EMPTY,
	}

	private int tick;
	private Type type;
	private int amount;
	/** Attacker, damage type, item or prayer name. */
	private String source;
	/** How the source was attributed, e.g. "projectile" or "melee range". May be null. */
	private String detail;
	/** The weapon a player attacker was holding, or null for NPCs and older recaps. */
	private String weapon;

	public RecapEvent(int tick, Type type, int amount, String source, String detail)
	{
		this(tick, type, amount, source, detail, null);
	}

	public boolean isDamage()
	{
		return (type == Type.HIT || type == Type.DAMAGE_OVER_TIME) && amount > 0;
	}
}
