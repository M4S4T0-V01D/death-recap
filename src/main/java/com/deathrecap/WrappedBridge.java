package com.deathrecap;

import java.util.HashMap;
import java.util.Map;

/**
 * The messages shared with the Gielinor Wrapped plugin over RuneLite's {@code PluginMessage} event. Plugins can't
 * read each other's folders, so deaths are sent as data. Values are plain JDK types so either plugin can read
 * them whichever class loader it was loaded by.
 *
 * <ul>
 *   <li>{@value #HELLO} from Wrapped (with {@code accountHash} and {@code have}, the timestamps it already has):
 *   Wrapped is running. Death Recap replies with a {@value #DEATH} for every saved death it doesn't have.</li>
 *   <li>{@value #GOODBYE} from Wrapped: it stopped, so deaths are no longer sent.</li>
 *   <li>{@value #READY} from Death Recap: an account's deaths finished loading, or Death Recap started.</li>
 *   <li>{@value #DEATH} from Death Recap: one death, sent again when its screenshot and replay are saved.</li>
 * </ul>
 */
final class WrappedBridge
{
	static final String NAMESPACE = "death-recap";
	static final String HELLO = "hello";
	static final String GOODBYE = "goodbye";
	static final String READY = "ready";
	static final String DEATH = "death";
	static final int VERSION = 1;

	private WrappedBridge()
	{
	}

	/** The {@value #DEATH} message data. Any of the images may be null. */
	static Map<String, Object> death(long accountHash, DeathRecord r, RecapAnalyzer.Summary s, byte[] card, byte[] screenshot,
		byte[] replay, int[] replayTimes)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("version", VERSION);
		data.put("accountHash", accountHash);
		data.put("timestamp", r.getTimestamp());
		data.put("world", r.getWorld());
		data.put("killer", RecapText.killer(r, s));
		data.put("damage", s.getTotalDamage());
		data.put("gpLost", gpLost(r));
		data.put("place", RecapText.place(r));
		put(data, "card", card);
		put(data, "screenshot", screenshot);
		if (replay != null && replayTimes != null)
		{
			data.put("replay", replay);
			data.put("replayTimes", replayTimes.clone());
		}
		return data;
	}

	static Map<String, Object> ready(long accountHash)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("version", VERSION);
		data.put("accountHash", accountHash);
		return data;
	}

	/** Value of the items lost, or -1 when it isn't known. */
	static long gpLost(DeathRecord r)
	{
		if (!r.isDeath() || r.getState() == null)
		{
			return -1;
		}
		return ItemsOnDeath.compute(r.getState()).getRiskValue();
	}

	/** The account a {@value #HELLO} is for, or -1 if it doesn't name one. */
	static long account(Map<String, Object> data)
	{
		Object v = data == null ? null : data.get("accountHash");
		return v instanceof Number ? ((Number) v).longValue() : -1;
	}

	/** Whether a {@value #HELLO} says Wrapped already has the death at {@code timestamp}. */
	static boolean has(Map<String, Object> data, long timestamp)
	{
		Object have = data == null ? null : data.get("have");
		if (have instanceof long[])
		{
			for (long t : (long[]) have)
			{
				if (t == timestamp)
				{
					return true;
				}
			}
		}
		return false;
	}

	private static void put(Map<String, Object> data, String key, byte[] value)
	{
		if (value != null)
		{
			data.put(key, value);
		}
	}
}
