package com.deathrecap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import net.runelite.client.ui.FontManager;
import org.junit.Assume;
import org.junit.Test;

/**
 * Renders every share card layout for a few sample recaps into build/card-preview, to look at by eye.
 * Runs only when asked: {@code ./gradlew test --tests '*CardPreview*' -PcardPreview=<dir>}, where the
 * optional {@code <dir>} holds {@code shot.png} (a screenshot) and {@code real.json} (a saved recap list).
 */
public class CardPreviewTest
{
	@Test
	public void renderPreviews() throws Exception
	{
		String dir = System.getProperty("cardPreview");
		Assume.assumeTrue(dir != null);
		File out = new File("build/card-preview");
		out.mkdirs();

		File shotFile = new File(dir, "shot.png");
		BufferedImage shot = shotFile.isFile() ? ImageIO.read(shotFile) : new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);

		List<DeathRecord> records = new ArrayList<>(Arrays.asList(pvp(), boss()));
		File real = new File(dir, "real.json");
		if (real.isFile())
		{
			try (Reader r = new FileReader(real))
			{
				List<DeathRecord> saved = new Gson().fromJson(r, new TypeToken<List<DeathRecord>>()
				{
				}.getType());
				records.add(saved.get(0));
			}
		}

		String[] names = {"pvp", "boss", "real"};
		for (int i = 0; i < records.size(); i++)
		{
			DeathRecord record = records.get(i);
			RecapAnalyzer.Summary summary = RecapAnalyzer.analyze(record);
			for (ShareCardLayout layout : ShareCardLayout.values())
			{
				BufferedImage card = ShareCard.render(shot, record, summary,
					FontManager.getRunescapeFont(), FontManager.getRunescapeBoldFont(), layout);
				ImageIO.write(card, "png", new File(out, names[i] + "-" + layout.name().toLowerCase() + ".png"));
			}
		}
	}

	/** Two players in the Wilderness, with a special attack to finish. */
	static DeathRecord pvp()
	{
		DeathRecord r = new DeathRecord();
		r.setTimestamp(1_792_936_221_969L);
		r.setWorld(330);
		r.setX(3100);
		r.setY(3700);
		r.setDeathTick(125);
		r.setAttackers(new ArrayList<>(Arrays.asList(
			new Attacker("Zezima", 126, true), new Attacker("Pk Lord", 98, true))));
		int[] hp = {99, 99, 99, 84, 84, 84, 71, 71, 89, 89, 76, 64, 64, 64, 82, 82, 70, 58, 58, 41, 41, 41, 22, 22, 0};
		for (int i = 0; i < hp.length; i++)
		{
			r.getTicks().add(new TickSnapshot(101 + i, hp[i], 99, Math.max(0, 70 - i * 2), "Protect from Melee"));
		}
		List<RecapEvent> e = r.getEvents();
		e.add(new RecapEvent(103, RecapEvent.Type.ATTACK, 0, "Zezima", null, "Abyssal tentacle"));
		e.add(new RecapEvent(104, RecapEvent.Type.HIT, 15, "Zezima", "attack animation", "Abyssal tentacle"));
		e.add(new RecapEvent(107, RecapEvent.Type.HIT, 13, "Pk Lord", "attack animation", "Dragon scimitar"));
		e.add(new RecapEvent(108, RecapEvent.Type.EAT, 0, "Shark", null));
		e.add(new RecapEvent(109, RecapEvent.Type.HEAL, 18, null, null));
		e.add(new RecapEvent(111, RecapEvent.Type.HIT, 13, "Zezima", "attack animation", "Abyssal tentacle"));
		e.add(new RecapEvent(112, RecapEvent.Type.HIT, 12, "Pk Lord", "attack animation", "Dragon scimitar"));
		e.add(new RecapEvent(114, RecapEvent.Type.EAT, 0, "Shark", null));
		e.add(new RecapEvent(115, RecapEvent.Type.HEAL, 18, null, null));
		e.add(new RecapEvent(116, RecapEvent.Type.HIT, 12, "Zezima", "attack animation", "Abyssal tentacle"));
		e.add(new RecapEvent(117, RecapEvent.Type.BLOCK, 0, "Pk Lord", "attack animation", "Dragon scimitar"));
		e.add(new RecapEvent(118, RecapEvent.Type.HIT, 12, "Pk Lord", "attack animation", "Dragon scimitar"));
		e.add(new RecapEvent(120, RecapEvent.Type.HIT, 17, "Zezima", "attack animation", "Dragon claws"));
		e.add(new RecapEvent(123, RecapEvent.Type.HIT, 19, "Zezima", "attack animation", "Armadyl godsword"));
		e.add(new RecapEvent(125, RecapEvent.Type.HIT, 22, "Zezima", "attack animation", "Armadyl godsword"));
		DeathState s = new DeathState();
		s.setSkulled(true);
		s.getEquipment().add(new DeathState.ItemStack(1, "Abyssal whip", 1, 1_350_000));
		s.getEquipment().add(new DeathState.ItemStack(2, "Rune platebody", 1, 38_000));
		s.getEquipment().add(new DeathState.ItemStack(3, "Dragon boots", 1, 210_000));
		s.getInventory().add(new DeathState.ItemStack(4, "Shark", 1, 900));
		s.getInventory().add(new DeathState.ItemStack(4, "Shark", 1, 900));
		s.getInventory().add(new DeathState.ItemStack(5, "Saradomin brew(4)", 1, 6_500));
		s.getInventory().add(new DeathState.ItemStack(6, "Coins", 120_000, 1));
		s.setFood(new ArrayList<>(Arrays.asList(new DeathState.ItemStack(4, "Shark", 2, 900))));
		r.setState(s);
		return r;
	}

	/** A long boss fight with steady damage, venom and a big last hit. */
	static DeathRecord boss()
	{
		DeathRecord r = new DeathRecord();
		r.setTimestamp(1_792_936_221_969L);
		r.setWorld(416);
		r.setRegionId(9023);
		r.setX(2272);
		r.setY(4062);
		r.setDeathTick(225);
		r.setAttackers(new ArrayList<>(Arrays.asList(new Attacker("Vorkath", 732, false))));
		int hp = 92;
		int prayer = 60;
		List<RecapEvent> e = r.getEvents();
		for (int t = 200; t <= 225; t++)
		{
			if (t % 5 == 0 && t < 225)
			{
				int dmg = 8 + (t * 7) % 23;
				e.add(new RecapEvent(t - 1, RecapEvent.Type.PROJECTILE, 0, "Vorkath", "dragonfire"));
				e.add(new RecapEvent(t, RecapEvent.Type.HIT, dmg, "Vorkath", "projectile"));
				hp -= dmg;
			}
			if (t % 4 == 0)
			{
				e.add(new RecapEvent(t, RecapEvent.Type.DAMAGE_OVER_TIME, 6, "Venom", null));
				hp -= 6;
			}
			if (t == 207 || t == 216)
			{
				e.add(new RecapEvent(t, RecapEvent.Type.EAT, 0, "Manta ray", null));
				e.add(new RecapEvent(t, RecapEvent.Type.HEAL, 22, null, null));
				hp += 22;
			}
			if (t == 225)
			{
				e.add(new RecapEvent(t, RecapEvent.Type.HIT, Math.max(1, hp), "Vorkath", "projectile"));
				hp = 0;
			}
			hp = Math.max(t == 225 ? 0 : 1, Math.min(99, hp));
			prayer = Math.max(0, prayer - 2);
			r.getTicks().add(new TickSnapshot(t, hp, 99, prayer, prayer > 0 ? "Protect from Magic" : null));
		}
		return r;
	}
}
