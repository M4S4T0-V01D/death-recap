package com.deathrecap;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.DrawManager;

/**
 * Captures the death screenshot and keeps a rolling buffer of full-size frames for the replay GIF, so
 * text in both is as readable as in the game. Frames are copied on the client thread into one of two
 * reusable buffers and compressed on a dedicated thread. Screenshots and GIFs are written on a second
 * thread, so a slow GIF (say, a close call's just before a death) never stops recording. If the frame
 * thread falls behind, frames are skipped.
 */
@Slf4j
class DeathCapture
{
	/** How long recording continues after the death, so the replay shows the death animation. */
	static final long AFTER_DEATH_MS = 2000;
	private static final int BUFFERS = 2;
	/** The last frame is held this long (hundredths of a second) before the GIF loops. */
	private static final int FINAL_FRAME_DELAY = 150;

	private static final class Frame
	{
		final long time;
		final byte[] jpeg;

		Frame(long time, byte[] jpeg)
		{
			this.time = time;
			this.jpeg = jpeg;
		}
	}

	/** Told the record, the saved file name and, for replays, when each frame was captured relative to the death in ms. */
	interface Saved
	{
		void saved(DeathRecord record, String name, int[] times);
	}

	private final Client client;
	private final DrawManager drawManager;
	private final DeathRecapConfig config;
	private final DeathStore store;
	private final Runnable frameListener = this::onFrame;
	private final Deque<Frame> frames = new ArrayDeque<>();
	/** Copy buffers not currently being compressed; at most {@link #BUFFERS} exist. */
	private final Queue<BufferedImage> spare = new ConcurrentLinkedQueue<>();
	private final AtomicInteger buffers = new AtomicInteger();

	/** Compresses frames and takes each clip when it is due. */
	private ScheduledExecutorService worker;
	/** Writes screenshots and GIFs. */
	private ExecutorService writer;
	private long lastRequest;
	private boolean requestPending;

	DeathCapture(Client client, DrawManager drawManager, DeathRecapConfig config, DeathStore store)
	{
		this.client = client;
		this.drawManager = drawManager;
		this.config = config;
		this.store = store;
	}

	void start()
	{
		worker = Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread t = new Thread(r, "death-recap-frames");
			t.setDaemon(true);
			return t;
		});
		writer = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "death-recap-media");
			t.setDaemon(true);
			return t;
		});
		drawManager.registerEveryFrameListener(frameListener);
	}

	void stop()
	{
		drawManager.unregisterEveryFrameListener(frameListener);
		if (worker != null)
		{
			worker.shutdownNow();
			worker = null;
		}
		if (writer != null)
		{
			writer.shutdownNow();
			writer = null;
		}
		clear();
	}

	void clear()
	{
		synchronized (frames)
		{
			frames.clear();
		}
		// Buffers still being compressed come back to spare afterwards and are counted until dropped here.
		while (spare.poll() != null)
		{
			buffers.decrementAndGet();
		}
	}

	/**
	 * Called on the client thread when the local player dies or has a close call. Each callback runs on
	 * the media thread once its file has been written.
	 */
	void captureDeath(long accountHash, DeathRecord record, Saved onScreenshot, Saved onReplay)
	{
		captureDeath(accountHash, record, AFTER_DEATH_MS, onScreenshot, onReplay);
	}

	/** As above, with the replay running {@code afterMs} past the record's timestamp. */
	void captureDeath(long accountHash, DeathRecord record, long afterMs, Saved onScreenshot, Saved onReplay)
	{
		ScheduledExecutorService w = worker;
		ExecutorService out = writer;
		if (w == null || out == null || !config.saveToDisk())
		{
			return;
		}
		long deathTime = record.getTimestamp();

		if (config.saveScreenshot())
		{
			drawManager.requestNextFrameListener(image ->
			{
				BufferedImage copy = copy(image);
				String name = deathTime + ".png";
				execute(out, () ->
				{
					try
					{
						store.writePng(accountHash, name, copy);
						onScreenshot.saved(record, name, null);
					}
					catch (Exception e)
					{
						log.warn("Unable to save death screenshot", e);
					}
				});
			});
		}

		if (config.recordReplay())
		{
			long from = deathTime - config.replaySeconds() * 1000L;
			long to = deathTime + afterMs;
			// The clip is taken on the frame thread when it's due, before older frames are dropped, and
			// written on the media thread, which may still be busy with an earlier GIF.
			w.schedule(() ->
			{
				List<Frame> clip = new ArrayList<>();
				synchronized (frames)
				{
					for (Frame f : frames)
					{
						if (f.time >= from && f.time <= to)
						{
							clip.add(f);
						}
					}
				}
				log.debug("Replay clip: {} frames, {} to {} ms from the death", clip.size(),
					clip.isEmpty() ? 0 : clip.get(0).time - deathTime, clip.isEmpty() ? 0 : clip.get(clip.size() - 1).time - deathTime);
				if (clip.size() >= 2)
				{
					execute(out, () -> writeReplay(accountHash, record, deathTime, clip, onReplay));
				}
			}, afterMs + 100, TimeUnit.MILLISECONDS);
		}
	}

	private void writeReplay(long accountHash, DeathRecord record, long deathTime, List<Frame> clip, Saved onReplay)
	{
		String name = deathTime + ".gif";
		try
		{
			store.writeGif(accountHash, name, i -> FrameCodec.decode(clip.get(i).jpeg), delays(clip));
			int[] times = new int[clip.size()];
			for (int i = 0; i < times.length; i++)
			{
				times[i] = (int) (clip.get(i).time - deathTime);
			}
			onReplay.saved(record, name, times);
		}
		catch (Exception e)
		{
			log.warn("Unable to save death replay", e);
		}
	}

	private static void execute(ExecutorService executor, Runnable task)
	{
		try
		{
			executor.execute(task);
		}
		catch (RejectedExecutionException e)
		{
			// Shutting down.
		}
	}

	private void onFrame()
	{
		if (!config.recordReplay() || !config.saveToDisk() || client.getGameState() != GameState.LOGGED_IN)
		{
			if (!frames.isEmpty())
			{
				clear();
			}
			return;
		}
		long now = System.currentTimeMillis();
		long interval = 1000L / config.replayFps();
		// If a requested frame never arrived (e.g. the window was minimised), ask again.
		if (now - lastRequest < interval || (requestPending && now - lastRequest < 1000))
		{
			return;
		}
		lastRequest = now;
		requestPending = true;
		drawManager.requestNextFrameListener(image ->
		{
			requestPending = false;
			ScheduledExecutorService w = worker;
			BufferedImage copy = w == null ? null : buffer(image.getWidth(null), image.getHeight(null));
			if (copy == null)
			{
				return;
			}
			Graphics2D g = copy.createGraphics();
			g.drawImage(image, 0, 0, null);
			g.dispose();
			long time = System.currentTimeMillis();
			long keep = config.replaySeconds() * 1000L + AFTER_DEATH_MS + 500;
			try
			{
				w.execute(() ->
				{
					try
					{
						Frame frame = new Frame(time, FrameCodec.encode(copy));
						synchronized (frames)
						{
							frames.addLast(frame);
							while (!frames.isEmpty() && frames.peekFirst().time < time - keep)
							{
								frames.pollFirst();
							}
						}
					}
					catch (Exception e)
					{
						log.debug("Unable to compress replay frame", e);
					}
					finally
					{
						spare.offer(copy);
					}
				});
			}
			catch (RejectedExecutionException e)
			{
				// Shutting down.
				spare.offer(copy);
			}
		});
	}

	/** A free copy buffer of the given size, or null if both are busy. */
	private BufferedImage buffer(int width, int height)
	{
		BufferedImage b;
		while ((b = spare.poll()) != null)
		{
			if (b.getWidth() == width && b.getHeight() == height)
			{
				return b;
			}
			// The window was resized; replace this buffer.
			buffers.decrementAndGet();
		}
		if (buffers.incrementAndGet() > BUFFERS)
		{
			buffers.decrementAndGet();
			return null;
		}
		return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
	}

	private static BufferedImage copy(Image image)
	{
		BufferedImage out = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return out;
	}

	/** Uses the real time between captured frames so the replay plays back at game speed. */
	static int[] delays(List<Frame> clip)
	{
		int[] delays = new int[clip.size()];
		for (int i = 0; i < clip.size() - 1; i++)
		{
			long ms = clip.get(i + 1).time - clip.get(i).time;
			delays[i] = (int) Math.max(2, Math.min(100, Math.round(ms / 10.0)));
		}
		delays[delays.length - 1] = FINAL_FRAME_DELAY;
		return delays;
	}
}
