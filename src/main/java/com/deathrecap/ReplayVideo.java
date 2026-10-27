package com.deathrecap;

import com.deathrecap.video.H264Encoder;
import com.deathrecap.video.Mp4Writer;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.function.IntConsumer;

/**
 * Turns a saved replay GIF into an MP4 with the pure-Java encoder in {@code com.deathrecap.video}. The
 * GIF's uneven frame times are resampled to a steady {@link #FPS}, and a clock, HP and the latest event
 * are drawn along the bottom.
 */
final class ReplayVideo
{
	static final int FPS = 10;
	private static final int MS_PER_TICK = 600;
	/** How long an event stays on screen. */
	private static final int CAPTION_MS = 1800;
	private static final Color SHADE = new Color(0, 0, 0, 0xB0);

	private ReplayVideo()
	{
	}

	/**
	 * @param maxWidth frames wider than this are scaled down; 0 keeps the recorded size
	 * @param progress told the percentage done
	 */
	static void write(byte[] gif, DeathRecord record, int maxWidth, Font font, OutputStream out, IntConsumer progress)
		throws IOException
	{
		ReplayFrames.Decoded decoded = ReplayFrames.decode(gif, n -> { });
		int[] durations = decoded.getDurations();
		int[] times = ReplayFrames.times(record.getReplayTimes(), durations);
		int last = times.length - 1;
		long start = times[0];
		long end = times[last] + durations[last];
		int count = (int) Math.max(1, (end - start) * FPS / 1000);

		BufferedImage first = decoded.frame(0);
		double scale = maxWidth > 0 && first.getWidth() > maxWidth ? (double) maxWidth / first.getWidth() : 1;
		int width = Math.max(16, (int) Math.round(first.getWidth() * scale) & ~1);
		int height = Math.max(16, (int) Math.round(first.getHeight() * scale) & ~1);

		H264Encoder encoder = new H264Encoder(width, height, FPS);
		encoder.setKeyInterval(FPS * 2);
		encoder.setQp(23);
		Mp4Writer mp4 = new Mp4Writer(width, height, FPS, encoder.sps(), encoder.pps());
		ByteArrayOutputStream media = new ByteArrayOutputStream();

		BufferedImage base = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Font caption = font.deriveFont(Font.PLAIN, Math.max(12f, height / 24f));
		int[] pixels = new int[width * height];
		int shown = -1;
		for (int i = 0; i < count; i++)
		{
			if (Thread.currentThread().isInterrupted())
			{
				throw new InterruptedIOException("Export cancelled");
			}
			long time = start + (long) i * 1000 / FPS;
			int index = ReplayFrames.frameAt(times, time);
			if (index != shown)
			{
				BufferedImage frame = index == 0 ? first : decoded.frame(index);
				Graphics2D g = base.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(frame, 0, 0, width, height, null);
				g.dispose();
				shown = index;
			}
			Graphics2D g = canvas.createGraphics();
			g.drawImage(base, 0, 0, null);
			caption(g, record, time, width, height, caption);
			g.dispose();
			canvas.getRGB(0, 0, width, height, pixels, 0, width);
			mp4.addFrame(encoder.encode(pixels, i == 0), media);
			progress.accept((i + 1) * 99 / count);
		}
		mp4.finish(out, new ByteArrayInputStream(media.toByteArray()));
		progress.accept(100);
	}

	/** The clock and HP on the left of the bottom bar, the latest event on the right. */
	private static void caption(Graphics2D g, DeathRecord record, long time, int width, int height, Font font)
	{
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int pad = fm.getHeight() / 3;
		int bar = fm.getHeight() + pad * 2;
		g.setColor(SHADE);
		g.fillRect(0, height - bar, width, bar);
		int baseline = height - bar + pad + fm.getAscent();

		String clock = String.format("%s%.1fs", time < 0 ? "-" : "+", Math.abs(time) / 1000.0);
		TickSnapshot hp = null;
		for (TickSnapshot t : record.getTicks())
		{
			long at = (long) (t.getTick() - record.getDeathTick()) * MS_PER_TICK;
			if (at <= time && (hp == null || t.getTick() > hp.getTick()))
			{
				hp = t;
			}
		}
		g.setColor(Color.WHITE);
		g.drawString(clock, pad, baseline);
		int x = pad + fm.stringWidth("-00.0s") + pad * 2;
		if (hp != null)
		{
			g.setColor(HpGraph.HP);
			g.drawString("HP " + hp.getHp() + "/" + hp.getMaxHp(), x, baseline);
		}

		RecapEvent latest = null;
		for (RecapEvent e : record.getEvents())
		{
			long at = (long) (e.getTick() - record.getDeathTick()) * MS_PER_TICK;
			if (at <= time && time - at < CAPTION_MS && shown(e) && (latest == null || e.getTick() >= latest.getTick()))
			{
				latest = e;
			}
		}
		String text = latest != null ? RecapText.describe(latest)
			: record.isDeath() && time >= 0 && time < CAPTION_MS ? "Died" : null;
		if (text != null)
		{
			g.setColor(latest == null || latest.isDamage() ? HpGraph.HIT
				: latest.getType() == RecapEvent.Type.EAT || latest.getType() == RecapEvent.Type.DRINK ? HpGraph.FOOD
				: Color.WHITE);
			g.drawString(text, Math.max(x, width - pad - fm.stringWidth(text)), baseline);
		}
	}

	private static boolean shown(RecapEvent e)
	{
		switch (e.getType())
		{
			case ATTACK:
			case PROJECTILE:
				return false;
			default:
				return true;
		}
	}
}
