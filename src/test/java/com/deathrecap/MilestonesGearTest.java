package com.deathrecap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.EquipmentInventorySlot;
import org.junit.Test;

public class MilestonesGearTest
{
	private static final long HOUR = 3_600_000L;
	private static final long DAY = 24 * HOUR;

	private static StatEntry death(long time, String killer, long lost)
	{
		return new StatEntry(time, killer, 50, false, false, 0, lost, false);
	}

	private static StatEntry closeCall(long time, String killer)
	{
		return new StatEntry(time, killer, 50, false, true, 3, 0, false);
	}

	private static DeathState.ItemStack worn(EquipmentInventorySlot slot, String name, long price)
	{
		DeathState.ItemStack s = new DeathState.ItemStack(slot.ordinal() + 100, name, 1, price);
		s.setSlot(slot.getSlotIdx());
		return s;
	}

	@Test
	public void firstDeathIsTheOnlyMilestone()
	{
		List<String> m = Milestones.of(List.of(closeCall(1, "Jad")), death(2, "Jad", 5_000_000));
		assertEquals(List.of("First death recorded"), m);
	}

	@Test
	public void closeCallsHaveNoMilestones()
	{
		assertTrue(Milestones.of(List.of(), closeCall(1, "Jad")).isEmpty());
	}

	@Test
	public void firstDeathToANewKiller()
	{
		List<String> m = Milestones.of(List.of(death(1, "Jad", 0), closeCall(2, "Zuk")), death(3, "Zuk", 0));
		assertEquals(List.of("First death to Zuk"), m);
	}

	@Test
	public void roundNumbers()
	{
		List<StatEntry> history = new ArrayList<>();
		for (int i = 0; i < 9; i++)
		{
			history.add(death(i * 1000L, i < 4 ? "Vorkath" : "Zulrah", 0));
		}
		List<String> tenth = Milestones.of(history, death(9_500, "Vorkath", 0));
		assertTrue(tenth.toString(), tenth.contains("10th death recorded"));
		assertTrue(tenth.toString(), tenth.contains("5th death to Vorkath"));
		assertEquals("1st", Milestones.ordinal(1));
		assertEquals("12th", Milestones.ordinal(12));
		assertEquals("22nd", Milestones.ordinal(22));
		assertEquals("113th", Milestones.ordinal(113));
	}

	@Test
	public void mostExpensiveNeedsAnEarlierLoss()
	{
		List<StatEntry> history = List.of(death(1, "Jad", 1_000_000));
		assertTrue(Milestones.of(history, death(2, "Jad", 2_000_000)).contains("Most expensive death yet: 2.00M gp (before: 1.00M gp)"));
		assertTrue(Milestones.of(history, death(2, "Jad", 900_000)).isEmpty());
		assertTrue(Milestones.of(List.of(death(1, "Jad", 0)), death(2, "Jad", 10)).isEmpty());
	}

	@Test
	public void longestGapAndLongBreaks()
	{
		List<StatEntry> history = List.of(death(0, "Jad", 0), death(DAY, "Jad", 0), death(DAY + 2 * HOUR, "Jad", 0));
		List<String> m = Milestones.of(history, death(DAY + 2 * HOUR + 3 * DAY + 4 * HOUR, "Jad", 0));
		assertEquals(List.of("Longest gap between deaths: 3 days 4 hours (before: 1 day)"), m);

		// Two deaths only give one gap, so there's no record to beat yet, but a week away is still noted.
		m = Milestones.of(List.of(death(0, "Jad", 0), death(HOUR, "Jad", 0)), death(HOUR + 12 * DAY, "Jad", 0));
		assertEquals(List.of("Longest gap between deaths: 12 days (before: 1 hour)"), m);
		m = Milestones.of(List.of(death(0, "Jad", 0)), death(8 * DAY, "Jad", 0));
		assertEquals(List.of("First death in 8 days"), m);
	}

	@Test
	public void laterEntriesAreIgnored()
	{
		List<StatEntry> history = List.of(death(1, "Jad", 0), death(10, "Zuk", 0));
		assertEquals(List.of("First death to Zuk"), Milestones.of(history, death(5, "Zuk", 0)));
	}

	@Test
	public void gaps()
	{
		Milestones.Gaps g = Milestones.gaps(List.of(death(0, "a", 0), closeCall(50, "b"), death(100, "c", 0), death(130, "d", 0)));
		assertEquals(130, g.getLastDeath());
		assertEquals(100, g.getLongest());
		assertEquals("c", g.getLongestEnd().getKiller());
		assertNull(Milestones.gaps(List.of(death(0, "a", 0))).getLongestEnd());
		assertEquals("45 minutes", Milestones.duration(45 * 60_000L));
		assertEquals("1 minute", Milestones.duration(5_000));
	}

	@Test
	public void slotDifferences()
	{
		List<DeathState.ItemStack> a = List.of(worn(EquipmentInventorySlot.BODY, "Rune platebody", 38_000),
			worn(EquipmentInventorySlot.RING, "Ring of wealth", 10_000));
		List<DeathState.ItemStack> b = List.of(worn(EquipmentInventorySlot.BODY, "Bandos chestplate", 15_000_000),
			worn(EquipmentInventorySlot.HEAD, "Neitiznot helm", 50_000));
		assertEquals(List.of("Head: nothing, then Neitiznot helm", "Body: Rune platebody, then Bandos chestplate",
			"Ring: Ring of wealth, then nothing"), Gear.differences(a, b));
		assertTrue(Gear.differences(a, a).isEmpty());
		assertTrue(Gear.differences(a, List.of()).isEmpty());
		assertEquals(48_000, Gear.value(a));
	}

	@Test
	public void olderGearWithoutSlots()
	{
		List<DeathState.ItemStack> a = List.of(new DeathState.ItemStack(1, "Whip", 1, 1));
		List<DeathState.ItemStack> b = List.of(new DeathState.ItemStack(2, "Tentacle", 1, 1));
		assertFalse(Gear.hasSlots(a));
		assertEquals(List.of("Wore Tentacle the second time", "Didn't wear Whip the second time"), Gear.differences(a, b));
	}

	@Test
	public void gearValueChangeInCompare()
	{
		DeathRecord a = new DeathRecord();
		a.setState(new DeathState());
		a.getState().getEquipment().add(worn(EquipmentInventorySlot.BODY, "Rune platebody", 38_000));
		DeathRecord b = new DeathRecord();
		b.setState(new DeathState());
		b.getState().getEquipment().add(worn(EquipmentInventorySlot.BODY, "Bandos chestplate", 15_000_000));
		assertEquals(List.of("Body: Rune platebody, then Bandos chestplate", "Gear worth about 38,000 gp, then 15.0M gp"),
			CompareDialog.gearDifferences(a, b));
		assertTrue(CompareDialog.gearDifferences(a, new DeathRecord()).isEmpty());
	}

	@Test
	public void olderRecordsLoadWithoutSlotsOrMilestones()
	{
		DeathRecord r = new Gson().fromJson("{\"state\":{\"equipment\":[{\"id\":4151,\"name\":\"Abyssal whip\",\"quantity\":1,\"price\":5}]}}",
			DeathRecord.class);
		assertEquals(-1, r.getState().getEquipment().get(0).getSlot());
		assertNull(r.getMilestones());
	}
}
