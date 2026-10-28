package com.deathrecap;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Full-quality frames saved beside a replay GIF. */
public class ReplayFramesFileTest
{
	@Test
	public void roundTrip() throws Exception
	{
		List<byte[]> jpegs = new ArrayList<>();
		for (int i = 0; i < 3; i++)
		{
			jpegs.add(FrameCodec.encode(frame(i)));
		}
		int[] times = {-400, -200, 0};
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ReplayFrames.writeFrames(out, jpegs, times);

		List<Integer> progress = new ArrayList<>();
		ReplayFrames.Decoded d = ReplayFrames.load(out.toByteArray(), progress::add);
		assertEquals(3, d.getFrames().size());
		assertArrayEquals(jpegs.get(2), d.getFrames().get(2));
		assertArrayEquals(new int[]{200, 200, ReplayFrames.LAST_FRAME_MS}, d.getDurations());
		assertEquals(Arrays.asList(1, 2, 3), progress);

		// Exports straight from the frames, at full colour.
		DeathRecord r = new DeathRecord();
		r.setReplayTimes(times);
		ByteArrayOutputStream mp4 = new ByteArrayOutputStream();
		ReplayVideo.write(out.toByteArray(), r, 0, new Font(Font.SANS_SERIF, Font.PLAIN, 12), mp4, p -> { });
		assertTrue(mp4.size() > 1000);
	}

	@Test
	public void namedAfterTheGif()
	{
		assertEquals("123.frames", DeathStore.framesName("123.gif"));
		assertNull(DeathStore.framesName(null));
		assertNull(DeathStore.framesName("123.png"));
	}

	@Test(expected = java.io.IOException.class)
	public void truncated() throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ReplayFrames.writeFrames(out, List.of(FrameCodec.encode(frame(0))), new int[]{0});
		byte[] all = out.toByteArray();
		ReplayFrames.load(Arrays.copyOf(all, all.length - 10), n -> { });
	}

	private static BufferedImage frame(int i)
	{
		BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setPaint(new java.awt.GradientPaint(0, 0, Color.RED, 64, 48, new Color(20 * i, 120, 255)));
		g.fillRect(0, 0, 64, 48);
		g.dispose();
		return img;
	}
}
