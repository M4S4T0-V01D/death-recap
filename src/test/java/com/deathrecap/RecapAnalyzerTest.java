package com.deathrecap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class RecapAnalyzerTest
{
	private static DeathRecord record(int deathTick)
	{
		DeathRecord r = new DeathRecord();
		r.setDeathTick(deathTick);
		return r;
	}

	private static boolean has(List<String> findings, String fragment)
	{
		return findings.stream().anyMatch(f -> f.contains(fragment));
	}

	@Test
	public void killingBlowAndBreakdown()
	{
		DeathRecord r = record(110);
		r.getTicks().add(new TickSnapshot(100, 99, 99, 50, "Protect from Magic"));
		r.getTicks().add(new TickSnapshot(105, 40, 99, 40, "Protect from Magic"));
		r.getTicks().add(new TickSnapshot(109, 20, 99, 30, "Protect from Magic"));
		r.getTicks().add(new TickSnapshot(110, 0, 99, 30, "Protect from Magic"));
		r.getEvents().add(new RecapEvent(101, RecapEvent.Type.HIT, 59, "Vorkath", "projectile"));
		r.getEvents().add(new RecapEvent(106, RecapEvent.Type.DAMAGE_OVER_TIME, 20, "Venom", null));
		r.getEvents().add(new RecapEvent(110, RecapEvent.Type.HIT, 20, "Zombified Spawn", null));
		r.getEvents().add(new RecapEvent(103, RecapEvent.Type.EAT, 0, "Shark", null));

		RecapAnalyzer.Summary s = RecapAnalyzer.analyze(r);

		assertEquals("Zombified Spawn", s.getKiller());
		assertEquals(20, s.getKillingBlow().getAmount());
		assertEquals(99, s.getTotalDamage());
		assertEquals("Vorkath", s.getBySource().get(0).getSource());
		assertEquals(59, s.getBiggestHit().getAmount());
		assertTrue(has(s.getFindings(), "Killing blow: 20 from Zombified Spawn"));
		assertTrue(has(s.getFindings(), "Biggest hit: 59 from Vorkath, 5.4s before death"));
		assertTrue(has(s.getFindings(), "Protect from Magic was active"));
		assertTrue(has(s.getFindings(), "Venom dealt 20 damage over time"));
		assertTrue(has(s.getFindings(), "below 50% HP for the last 3.0s and didn't eat or drink"));
	}

	@Test
	public void unknownHitsDoNotHideNamedKiller()
	{
		DeathRecord r = record(10);
		r.getEvents().add(new RecapEvent(8, RecapEvent.Type.HIT, 30, "TzTok-Jad", "projectile"));
		r.getEvents().add(new RecapEvent(10, RecapEvent.Type.HIT, 5, "Unknown", null));

		assertEquals("TzTok-Jad", RecapAnalyzer.analyze(r).getKiller());
	}

	@Test
	public void burstAndPrayerEmpty()
	{
		DeathRecord r = record(20);
		r.getTicks().add(new TickSnapshot(18, 50, 99, 0, null));
		r.getTicks().add(new TickSnapshot(20, 0, 99, 0, null));
		r.getEvents().add(new RecapEvent(15, RecapEvent.Type.PRAYER_EMPTY, 0, "Prayer", null));
		r.getEvents().add(new RecapEvent(20, RecapEvent.Type.HIT, 25, "Callisto", null));
		r.getEvents().add(new RecapEvent(20, RecapEvent.Type.HIT, 25, "Callisto", null));

		List<String> f = RecapAnalyzer.analyze(r).getFindings();
		assertTrue(has(f, "Biggest single-tick burst: 50 damage from 2 hits (Callisto), 0.0s before death"));
		assertTrue(has(f, "Prayer ran out 3.0s before death"));
		assertTrue(has(f, "No protection prayer was active"));
	}

	@Test
	public void emptyRecord()
	{
		RecapAnalyzer.Summary s = RecapAnalyzer.analyze(record(5));
		assertEquals("Unknown", s.getKiller());
		assertNull(s.getKillingBlow());
		assertEquals(0, s.getTotalDamage());
		assertTrue(has(s.getFindings(), "No damage was recorded"));
	}

	@Test
	public void hitsplatClassification()
	{
		assertEquals("Venom", DeathRecapPlugin.damageOverTime(net.runelite.api.HitsplatID.VENOM));
		assertNull(DeathRecapPlugin.damageOverTime(net.runelite.api.HitsplatID.DAMAGE_ME));
		assertTrue(DeathRecapPlugin.isHealthDamage(net.runelite.api.HitsplatID.DAMAGE_ME));
		assertTrue(!DeathRecapPlugin.isHealthDamage(net.runelite.api.HitsplatID.PRAYER_DRAIN));
	}
}
