package com.deathrecap;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WrappedBridgeTest
{
	@Test
	public void deathMessageUsesPlainTypes()
	{
		DeathRecord r = CardPreviewTest.boss();
		RecapAnalyzer.Summary s = RecapAnalyzer.analyze(r);
		byte[] card = {1, 2, 3};
		int[] times = {-2000, -1000, 0};
		Map<String, Object> data = WrappedBridge.death(42L, r, s, card, null, new byte[]{9}, times);

		assertEquals(42L, data.get("accountHash"));
		assertEquals(r.getTimestamp(), data.get("timestamp"));
		assertEquals(416, data.get("world"));
		assertTrue(((String) data.get("killer")).startsWith("Vorkath"));
		assertEquals(s.getTotalDamage(), data.get("damage"));
		assertTrue(data.get("gpLost") instanceof Long);
		assertArrayEquals(card, (byte[]) data.get("card"));
		assertFalse(data.containsKey("screenshot"));
		assertArrayEquals(times, (int[]) data.get("replayTimes"));
		for (Object v : data.values())
		{
			assertTrue(v.getClass().getName(), v.getClass().getName().startsWith("java.") || v.getClass().isArray());
		}
	}

	@Test
	public void replayNeedsItsTimes()
	{
		DeathRecord r = CardPreviewTest.boss();
		Map<String, Object> data = WrappedBridge.death(1L, r, RecapAnalyzer.analyze(r), null, null, new byte[]{9}, null);
		assertNull(data.get("replay"));
		assertNull(data.get("card"));
	}

	@Test
	public void gpLostUnknownWithoutState()
	{
		DeathRecord r = CardPreviewTest.boss();
		r.setState(null);
		assertEquals(-1, WrappedBridge.gpLost(r));
	}

	@Test
	public void readsHello()
	{
		Map<String, Object> hello = new HashMap<>();
		assertEquals(-1, WrappedBridge.account(hello));
		assertEquals(-1, WrappedBridge.account(null));
		hello.put("accountHash", 77L);
		hello.put("have", new long[]{5, 6});
		assertEquals(77, WrappedBridge.account(hello));
		assertTrue(WrappedBridge.has(hello, 6));
		assertFalse(WrappedBridge.has(hello, 7));
		assertFalse(WrappedBridge.has(null, 7));
	}
}
