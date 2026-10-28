package com.deathrecap;

import com.google.gson.Gson;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Sound effects kept with a replay. */
public class ReplaySoundTest
{
	@Test
	public void keepsSoundsInTheReplayWindow()
	{
		DeathCapture capture = new DeathCapture(null, null, new DeathRecapConfig()
		{
			@Override
			public boolean recordReplay()
			{
				return true;
			}
		}, null);
		long before = System.currentTimeMillis();
		capture.sound(2500, false);
		capture.sound(511, true);
		long after = System.currentTimeMillis();

		List<ReplaySound> heard = capture.soundsBetween(before, after, after);
		assertEquals(2, heard.size());
		assertEquals(2500, heard.get(0).getId());
		assertTrue(heard.get(1).isArea());
		assertTrue(heard.get(0).getTime() <= 0 && heard.get(0).getTime() >= before - after);
		assertTrue(capture.soundsBetween(after + 1, after + 1000, after).isEmpty());

		capture.clear();
		assertTrue(capture.soundsBetween(before, after, after).isEmpty());
	}

	@Test
	public void switchedOff()
	{
		DeathCapture capture = new DeathCapture(null, null, new DeathRecapConfig()
		{
			@Override
			public boolean recordReplay()
			{
				return true;
			}

			@Override
			public boolean replaySounds()
			{
				return false;
			}
		}, null);
		capture.sound(2500, false);
		assertTrue(capture.soundsBetween(0, Long.MAX_VALUE, 0).isEmpty());
	}

	@Test
	public void savedWithTheRecord()
	{
		DeathRecord r = new DeathRecord();
		Gson gson = new Gson();
		assertNull(gson.fromJson(gson.toJson(r), DeathRecord.class).getReplaySounds());
		r.setReplaySounds(java.util.Arrays.asList(new ReplaySound(-1200, 2500, false), new ReplaySound(300, 511, true)));
		DeathRecord back = gson.fromJson(gson.toJson(r), DeathRecord.class);
		assertEquals(r.getReplaySounds(), back.getReplaySounds());
	}
}
