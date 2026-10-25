package com.deathrecap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import net.runelite.api.Prayer;
import org.junit.Test;

public class FeaturesTest
{
	private static DeathState.ItemStack item(int id, String name, int qty, long price)
	{
		return new DeathState.ItemStack(id, name, qty, price);
	}

	@Test
	public void keepsThreeMostValuableUnskulled()
	{
		DeathState s = new DeathState();
		s.getEquipment().add(item(1, "Whip", 1, 1_500_000));
		s.getEquipment().add(item(2, "Rune platebody", 1, 38_000));
		s.getInventory().add(item(3, "Shark", 10, 900));
		s.getInventory().add(item(4, "Dragon boots", 1, 200_000));

		ItemsOnDeath.Result r = ItemsOnDeath.compute(s);
		assertEquals(3, r.getKept().size());
		assertEquals("Whip", r.getKept().get(0).getName());
		assertEquals(1_500_000 + 200_000 + 38_000, r.getKeptValue());
		assertEquals(9_000, r.getRiskValue());
	}

	@Test
	public void skulledWithProtectItemKeepsOne()
	{
		DeathState s = new DeathState();
		s.setSkulled(true);
		s.setProtectItem(true);
		s.getInventory().add(item(5, "Coins", 1000, 1));
		s.getInventory().add(item(6, "Rune arrow", 100, 150));

		ItemsOnDeath.Result r = ItemsOnDeath.compute(s);
		assertEquals(1, r.getKept().size());
		assertEquals(1, r.getKept().get(0).getQuantity());
		assertEquals(150, r.getKeptValue());
		assertEquals(99 * 150 + 1000, r.getRiskValue());
		assertEquals("1.50M", ItemsOnDeath.gp(1_500_000));
		assertEquals("9,000", ItemsOnDeath.gp(9_000));
	}

	@Test
	public void mergesUnstackableItems()
	{
		DeathState s = new DeathState();
		s.setSkulled(true);
		for (int i = 0; i < 5; i++)
		{
			s.getInventory().add(item(7, "Jangerberries", 1, 601));
		}
		s.getInventory().add(item(5, "Coins", 120_000, 1));

		ItemsOnDeath.Result r = ItemsOnDeath.compute(s);
		assertEquals(2, r.getAtRisk().size());
		assertEquals("120,000 x Coins", RecapText.stack(r.getAtRisk().get(0)));
		assertEquals("5 x Jangerberries", RecapText.stack(r.getAtRisk().get(1)));
	}

	@Test
	public void statsByKiller()
	{
		List<StatEntry> entries = List.of(
			new StatEntry(1, "Vorkath", 120, false, false, 0, 2_000_000, true),
			new StatEntry(2, "Vorkath", 80, true, false, 0, 500_000, false),
			new StatEntry(3, "Zulrah", 100, false, false, 0, 0, false),
			new StatEntry(4, "Zulrah", 40, true, true, 5, 0, false));

		DeathStats.Report r = DeathStats.compute(entries, 2);
		assertEquals(2_500_000, r.getLost());
		assertEquals(2, r.getSessionDeaths());
		assertEquals(500_000, r.getSessionLost());
		assertEquals(1, r.getMostExpensive().getTimestamp());
		assertEquals(1, r.getWithSupplies());
		assertEquals(3, r.getDeaths());
		assertEquals(1, r.getCloseCalls());
		assertEquals(100, r.getAverageDamage());
		assertEquals(2, r.getWithoutEating());
		assertEquals("Vorkath", r.getKillers().get(0).getName());
		assertEquals(100, r.getKillers().get(0).getAverageDamage());
		assertEquals(1, r.getKillers().get(1).getCloseCalls());
	}

	@Test
	public void closeCallFindings()
	{
		DeathRecord r = new DeathRecord();
		r.setCloseCall(true);
		r.setDeathTick(20);
		r.getTicks().add(new TickSnapshot(18, 60, 99, 40, null));
		r.getTicks().add(new TickSnapshot(20, 8, 99, 40, "Protect from Missiles"));
		r.getTicks().add(new TickSnapshot(22, 30, 99, 40, null));
		r.getEvents().add(new RecapEvent(19, RecapEvent.Type.HIT, 52, "Zulrah", "projectile"));
		r.getEvents().add(new RecapEvent(21, RecapEvent.Type.EAT, 0, "Shark", null));

		RecapAnalyzer.Summary s = RecapAnalyzer.analyze(r);
		assertEquals("Zulrah", s.getKiller());
		assertEquals(52, s.getKillingBlow().getAmount());
		assertTrue(s.getFindings().stream().anyMatch(f -> f.contains("HP fell to 8 of 99")));
		assertEquals(8, StatEntry.of(r, s).getLowestHp());
		assertTrue(RecapText.full(r, s).startsWith("Death Recap: close call against Zulrah"));
		assertEquals("+1.2s", RecapText.offset(22, 20));
		assertEquals("-0.6s", RecapText.offset(19, 20));
	}

	@Test
	public void suppliesLeftAtDeath()
	{
		DeathRecord r = new DeathRecord();
		r.setDeathTick(10);
		r.getEvents().add(new RecapEvent(10, RecapEvent.Type.HIT, 30, "Vorkath", null));
		DeathState state = new DeathState();
		state.setFood(new java.util.ArrayList<>(List.of(new DeathState.ItemStack(385, "Shark", 6, 0))));
		state.setPotions(new java.util.ArrayList<>(List.of(new DeathState.ItemStack(6687, "Saradomin brew(3)", 2, 0))));
		r.setState(state);

		RecapAnalyzer.Summary s = RecapAnalyzer.analyze(r);
		assertTrue(s.getFindings().contains("You died with food and potions left: 6 x Shark, 2 x Saradomin brew(3)."));
		assertTrue(StatEntry.of(r, s).isSuppliesLeft());

		state.getFood().clear();
		state.getPotions().clear();
		assertTrue(RecapAnalyzer.analyze(r).getFindings().contains("You had no food or potions left."));

		// Recaps saved before supplies were recorded say nothing about them.
		state.setFood(null);
		assertTrue(RecapAnalyzer.analyze(r).getFindings().stream().noneMatch(f -> f.contains("food")));
	}

	@Test
	public void clipFindings()
	{
		DeathRecord r = new DeathRecord();
		r.setClip(true);
		r.setDeathTick(20);
		r.getTicks().add(new TickSnapshot(15, 90, 99, 40, null));
		r.getTicks().add(new TickSnapshot(18, 40, 99, 40, null));
		r.getTicks().add(new TickSnapshot(20, 60, 99, 40, null));
		r.getEvents().add(new RecapEvent(17, RecapEvent.Type.HIT, 50, "Jad", "magic"));
		r.getEvents().add(new RecapEvent(19, RecapEvent.Type.EAT, 0, "Shark", null));

		RecapAnalyzer.Summary s = RecapAnalyzer.analyze(r);
		assertEquals("Jad", s.getKiller());
		assertTrue(s.getFindings().contains("HP went from 90 to 60; the lowest was 40."));
		assertTrue(s.getFindings().contains("You ate or drank 1 time."));
		assertTrue(RecapText.full(r, s).startsWith("Death Recap: clip"));
	}

	@Test
	public void prayerNames()
	{
		assertEquals("Protect from Melee", DeathRecapPlugin.prayerName(Prayer.PROTECT_FROM_MELEE));
		assertEquals("Rejuvenation", DeathRecapPlugin.prayerName(Prayer.RP_REJUVENATION));
	}

	@Test
	public void replayRoundTripAndSeek() throws Exception
	{
		BufferedImage[] frames = new BufferedImage[3];
		for (int i = 0; i < frames.length; i++)
		{
			frames[i] = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = frames[i].createGraphics();
			g.setColor(new Color(i * 100, 0, 0));
			g.fillRect(0, 0, 40, 30);
			g.dispose();
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GifWriter.write(i -> frames[i], new int[]{20, 30, 150}, out);

		ReplayFrames.Decoded d = ReplayFrames.decode(out.toByteArray(), n -> { });
		assertEquals(3, d.getFrames().size());
		assertArrayEquals(new int[]{200, 300, 1500}, d.getDurations());
		BufferedImage last = d.frame(2);
		assertEquals(40, last.getWidth());
		assertTrue(Math.abs(new Color(last.getRGB(20, 15)).getRed() - 200) < 16);

		int[] times = ReplayFrames.times(null, d.getDurations());
		assertArrayEquals(new int[]{1500, 1700, 2000}, times);
		assertArrayEquals(new int[]{-600, 0, 600}, ReplayFrames.times(new int[]{-600, 0, 600}, d.getDurations()));
		assertEquals(0, ReplayFrames.frameAt(times, -5000));
		assertEquals(1, ReplayFrames.frameAt(times, 1800));
	}

	@Test
	public void cardAndGraphDraw()
	{
		DeathRecord r = new DeathRecord();
		r.setDeathTick(10);
		r.getTicks().add(new TickSnapshot(5, 50, 99, 20, null));
		r.getTicks().add(new TickSnapshot(10, 0, 99, 10, null));
		r.getEvents().add(new RecapEvent(10, RecapEvent.Type.HIT, 50, "Jad", null));
		r.setState(new DeathState());
		Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
		r.getState().getInventory().add(new DeathState.ItemStack(1, "Shark", 10, 1000));
		for (int i = 0; i < 4; i++)
		{
			r.getState().getEquipment().add(new DeathState.ItemStack(10 + i, "Item " + i, 1, 50_000 * (i + 1)));
		}
		BufferedImage shot = new BufferedImage(1200, 700, BufferedImage.TYPE_INT_RGB);
		for (ShareCardLayout layout : ShareCardLayout.values())
		{
			BufferedImage card = ShareCard.render(shot, r, RecapAnalyzer.analyze(r), font, font.deriveFont(Font.BOLD), layout);
			assertNotNull(card);
			assertTrue(layout + " too narrow", card.getWidth() >= 900);
			assertEquals(layout + " screenshot", layout != ShareCardLayout.COMPACT, card.getHeight() > 700);
		}
		assertTrue(RecapText.lostSentence(r).contains("Item 0"));
		assertTrue(RecapText.lostSentence(r).startsWith(" Lost about"));

		BufferedImage img = new BufferedImage(240, 130, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		Rectangle area = new Rectangle(0, 0, 240, 130);
		HpGraph.paint(g, area, r);
		assertEquals(5, HpGraph.at(r, 0, area, g.getFontMetrics()).getTick());
		assertEquals(10, HpGraph.at(r, 239, area, g.getFontMetrics()).getTick());
		g.dispose();
	}
}
