package com.deathrecap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class OverhaulTest
{
	private static StatEntry entry(long time, String killer, boolean closeCall, boolean ate, int hpWhenHit)
	{
		StatEntry e = new StatEntry(time, killer, 40, ate, closeCall, closeCall ? 5 : 0, closeCall ? 0 : 1000, false);
		e.setHpWhenHit(hpWhenHit);
		return e;
	}

	private static DeathRecord record(long time, String killer, int[] hp, int hitAmount)
	{
		DeathRecord r = new DeathRecord();
		r.setTimestamp(time);
		r.setDeathTick(hp.length - 1);
		for (int t = 0; t < hp.length; t++)
		{
			r.getTicks().add(new TickSnapshot(t, hp[t], 99, 50, null));
		}
		for (int t = 1; t < hp.length; t++)
		{
			r.getEvents().add(new RecapEvent(t, RecapEvent.Type.HIT, hitAmount, killer, null));
		}
		return r;
	}

	@Test
	public void trendNeedsEnoughEntries()
	{
		List<StatEntry> list = new ArrayList<>();
		for (int i = 0; i < DeathStats.TREND_MIN - 1; i++)
		{
			list.add(entry(i, "Jad", false, false, 30));
		}
		assertNull(DeathStats.trend(list));
	}

	@Test
	public void trendSeesImprovementAndDecline()
	{
		List<StatEntry> better = List.of(
			entry(1, "Jad", false, false, 20), entry(2, "Jad", false, false, 25),
			entry(3, "Jad", true, true, 60), entry(4, "Jad", true, true, 70));
		DeathStats.Trend up = DeathStats.trend(better);
		assertTrue(up.getScore() > 0);
		assertEquals("Improving", up.verdict());
		assertTrue(up.getReasons().stream().anyMatch(r -> r.contains("survive more")));
		assertTrue(up.getReasons().stream().anyMatch(r -> r.contains("HP when it hits is higher")));

		List<StatEntry> worse = List.of(
			entry(1, "Jad", true, true, 70), entry(2, "Jad", true, true, 60),
			entry(3, "Jad", false, false, 25), entry(4, "Jad", false, false, 20));
		assertEquals("Getting worse", DeathStats.trend(worse).verdict());

		List<StatEntry> same = List.of(
			entry(1, "Jad", false, true, 40), entry(2, "Jad", false, true, 40),
			entry(3, "Jad", false, true, 41), entry(4, "Jad", false, true, 40));
		DeathStats.Trend flat = DeathStats.trend(same);
		assertEquals("About the same", flat.verdict());
		assertTrue(flat.getReasons().isEmpty());
	}

	@Test
	public void reportGroupsKillersOldestFirst()
	{
		DeathStats.Report r = DeathStats.compute(List.of(
			entry(30, "Jad", false, false, 10), entry(10, "Jad", true, true, 50), entry(20, null, false, true, -1)), 0);
		DeathStats.Killer jad = r.killer("Jad");
		assertEquals(1, jad.getDeaths());
		assertEquals(1, jad.getCloseCalls());
		assertEquals(30, jad.getHpWhenHit());
		assertEquals(10, jad.getEntries().get(0).getTimestamp());
		assertEquals(-1, r.killer("Unknown").getHpWhenHit());
		assertNull(r.killer("Zulrah"));
		assertEquals("1 death, 1 close call", DeathRecapPanel.counts(jad));
		assertTrue(DeathRecapPanel.killerLines(jad).contains("Average HP when it hit: 30%"));
	}

	@Test
	public void hpWhenHitUsesTheTickBeforeEachHit()
	{
		DeathRecord r = record(1, "Jad", new int[]{99, 49, 0}, 50);
		// Hits at ticks 1 and 2 see 99/99 and 49/99 before them.
		assertEquals(Math.round((100 + 49) / 2f), StatEntry.hpWhenHit(r, "Jad"));
		assertEquals(-1, StatEntry.hpWhenHit(r, "Zulrah"));
		assertEquals(-1, StatEntry.hpWhenHit(r, null));
	}

	@Test
	public void fillDetailsBackfillsOldEntries()
	{
		DeathRecord r = record(500, "Jad", new int[]{99, 49, 0}, 50);
		r.setX(3200);
		r.setY(3200);
		StatEntry old = new StatEntry(500, "Jad", 99, false, false, 0, 0, false);
		StatEntry other = new StatEntry(600, "Jad", 99, false, false, 0, 0, false);
		assertTrue(old.missingDetails());
		assertTrue(DeathRecapPlugin.fillDetails(List.of(old, other), List.of(r)));
		assertEquals(3200, old.getX());
		assertTrue(old.getHpWhenHit() > 0);
		assertTrue(old.getFightTicks() > 0);
		assertFalse(old.missingDetails());
		assertFalse(other.hasLocation());
		assertFalse("nothing left to fill", DeathRecapPlugin.fillDetails(List.of(old), List.of(r)));
	}

	@Test
	public void killerColoursAreSteady()
	{
		assertEquals(DeathStats.colour("Jad"), DeathStats.colour("Jad"));
		assertEquals(DeathStats.colour("Unknown"), DeathStats.colour(null));
		assertFalse(DeathStats.colour("Jad").equals(DeathStats.colour("Zulrah")));
	}

	@Test
	public void mapMarkers()
	{
		BufferedImage dot = DeathMap.draw(Color.RED, false);
		BufferedImage ring = DeathMap.draw(Color.RED, true);
		int mid = dot.getWidth() / 2;
		assertEquals(Color.RED.getRGB(), dot.getRGB(mid, mid));
		assertEquals(0xFF, ring.getRGB(mid, mid) >>> 24); // the black backing
		assertTrue((ring.getRGB(mid, mid) & 0xFFFFFF) == 0);
		assertEquals(0, dot.getRGB(0, 0) >>> 24); // transparent corner

		StatEntry death = entry(0, "Jad", false, false, 10);
		death.setLost(1_500_000);
		String tip = DeathMap.tooltip(death, "Jad");
		assertTrue(tip.startsWith("Died to Jad<br>"));
		assertTrue(tip.endsWith(" gp"));
		String close = DeathMap.tooltip(entry(0, "Jad", true, true, 10), "Jad");
		assertTrue(close.startsWith("Close call: Jad<br>"));
		assertFalse(close.contains("gp"));
	}

	@Test
	public void compareListsOnlyRealDifferences()
	{
		CompareDialog.Facts a = new CompareDialog.Facts("Jad", 100, 2, 0, 40, 20, 50_000, 5);
		assertTrue(CompareDialog.differences(a, a).isEmpty());

		CompareDialog.Facts b = new CompareDialog.Facts("Zuk", 160, 6, 0, 20, 40, 0, 0);
		List<String> diff = CompareDialog.differences(a, b);
		assertTrue(diff.contains("Different attacker: Jad, then Zuk"));
		assertTrue(diff.contains("Took 60 more damage the second time (100 vs 160)"));
		assertTrue(diff.contains("Used more food and potions (2 vs 6)"));
		assertTrue(diff.contains("HP was lower when hits landed (40% vs 20%)"));
		assertTrue(diff.stream().anyMatch(s -> s.startsWith("The fight was longer")));
		assertTrue(diff.contains("Ran out of food and potions the second time"));
		assertTrue(diff.stream().anyMatch(s -> s.startsWith("Lost about")));
		// Looks back only: no advice.
		assertTrue(diff.stream().noneMatch(s -> s.toLowerCase().contains("should") || s.toLowerCase().contains("try ")));

		CompareDialog.Facts small = new CompareDialog.Facts("Jad", 103, 2, 0, 44, 21, 50_000, 5);
		assertTrue("small changes are ignored", CompareDialog.differences(a, small).isEmpty());
	}

	@Test
	public void compareFactsFromRecords()
	{
		DeathRecord r = record(1, "Jad", new int[]{99, 49, 0}, 50);
		r.getEvents().add(new RecapEvent(1, RecapEvent.Type.EAT, 0, "Shark", null));
		CompareDialog.Facts f = CompareDialog.facts(r);
		assertEquals("Jad", f.getKiller());
		assertEquals(1, f.getConsumed());
		assertEquals(0, f.getLowestHp());
		assertEquals(-1, f.getSuppliesLeft());
	}

	@Test
	public void filters()
	{
		DeathRecord death = new DeathRecord();
		DeathRecord close = new DeathRecord();
		close.setCloseCall(true);
		DeathRecord clip = new DeathRecord();
		clip.setClip(true);
		assertTrue(DeathRecapPanel.filter(0).test(clip));
		assertTrue(DeathRecapPanel.filter(1).test(death));
		assertFalse(DeathRecapPanel.filter(1).test(close));
		assertTrue(DeathRecapPanel.filter(2).test(close));
		assertFalse(DeathRecapPanel.filter(2).test(clip));
		assertTrue(DeathRecapPanel.filter(3).test(clip));
	}

	@Test
	public void replayExportsAPlayableMp4() throws Exception
	{
		BufferedImage[] frames = new BufferedImage[3];
		for (int i = 0; i < frames.length; i++)
		{
			frames[i] = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = frames[i].createGraphics();
			g.setColor(new Color(i * 100, 50, 0));
			g.fillRect(0, 0, 64, 48);
			g.dispose();
		}
		ByteArrayOutputStream gif = new ByteArrayOutputStream();
		GifWriter.write(i -> frames[i], new int[]{60, 60, 60}, gif);
		DeathRecord r = record(1, "Jad", new int[]{99, 49, 0}, 50);
		r.setReplayTimes(new int[]{-1200, -600, 0});

		ByteArrayOutputStream mp4 = new ByteArrayOutputStream();
		List<Integer> progress = new ArrayList<>();
		ReplayVideo.write(gif.toByteArray(), r, 0, new Font(Font.SANS_SERIF, Font.PLAIN, 12), mp4, progress::add);
		assertEquals(100, (int) progress.get(progress.size() - 1));

		Map<String, Integer> boxes = topLevelBoxes(mp4.toByteArray());
		assertEquals(List.of("ftyp", "moov", "mdat"), List.copyOf(boxes.keySet()).subList(0, 3));
		assertTrue(boxes.get("mdat") > 8);
	}

	@Test(expected = java.io.InterruptedIOException.class)
	public void replayExportStopsWhenInterrupted() throws Exception
	{
		BufferedImage frame = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream gif = new ByteArrayOutputStream();
		GifWriter.write(i -> frame, new int[]{100}, gif);
		DeathRecord r = record(1, "Jad", new int[]{99, 0}, 99);
		Thread.currentThread().interrupt();
		try
		{
			ReplayVideo.write(gif.toByteArray(), r, 0, new Font(Font.SANS_SERIF, Font.PLAIN, 12), new ByteArrayOutputStream(), p -> { });
		}
		finally
		{
			Thread.interrupted();
		}
	}

	/** Box type to size, in file order. */
	private static Map<String, Integer> topLevelBoxes(byte[] file)
	{
		Map<String, Integer> boxes = new LinkedHashMap<>();
		ByteBuffer b = ByteBuffer.wrap(file);
		while (b.remaining() >= 8)
		{
			int size = b.getInt();
			byte[] type = new byte[4];
			b.get(type);
			boxes.put(new String(type, StandardCharsets.US_ASCII), size);
			assertTrue(size >= 8 && size - 8 <= b.remaining());
			b.position(b.position() + size - 8);
		}
		assertEquals(0, b.remaining());
		return boxes;
	}
}
