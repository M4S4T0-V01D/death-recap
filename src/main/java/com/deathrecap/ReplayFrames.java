package com.deathrecap;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import lombok.Value;

/**
 * Turns a saved replay GIF back into whole frames for the replay player. The GIF stores only the area
 * that changed, so each frame is drawn over the previous one; the results are kept as JPEGs so a long
 * full-size replay fits in a few tens of megabytes.
 */
final class ReplayFrames
{
	private static final String FORMAT = "javax_imageio_gif_image_1.0";

	@Value
	static class Decoded
	{
		List<byte[]> frames;
		/** Display time of each frame, in milliseconds. */
		int[] durations;

		BufferedImage frame(int index) throws IOException
		{
			return FrameCodec.decode(frames.get(index));
		}
	}

	private ReplayFrames()
	{
	}

	/**
	 * @param progress told the number of frames done so far
	 */
	static Decoded decode(byte[] gif, IntConsumer progress) throws IOException
	{
		ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(gif)))
		{
			reader.setInput(in, false, false);
			List<byte[]> frames = new ArrayList<>();
			List<Integer> durations = new ArrayList<>();
			BufferedImage canvas = null;
			for (int i = 0; ; i++)
			{
				BufferedImage part;
				try
				{
					part = reader.read(i);
				}
				catch (IndexOutOfBoundsException e)
				{
					break;
				}
				IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(i).getAsTree(FORMAT);
				IIOMetadataNode d = child(root, "ImageDescriptor");
				IIOMetadataNode gce = child(root, "GraphicControlExtension");
				int left = d != null ? Integer.parseInt(d.getAttribute("imageLeftPosition")) : 0;
				int top = d != null ? Integer.parseInt(d.getAttribute("imageTopPosition")) : 0;
				if (canvas == null)
				{
					canvas = new BufferedImage(left + part.getWidth(), top + part.getHeight(), BufferedImage.TYPE_INT_RGB);
				}
				Graphics2D g = canvas.createGraphics();
				g.drawImage(part, left, top, null);
				g.dispose();
				frames.add(FrameCodec.encode(canvas));
				int delay = gce != null ? Integer.parseInt(gce.getAttribute("delayTime")) : 10;
				durations.add(Math.max(20, delay * 10));
				progress.accept(frames.size());
			}
			if (frames.isEmpty())
			{
				throw new IOException("The replay has no frames");
			}
			return new Decoded(frames, durations.stream().mapToInt(Integer::intValue).toArray());
		}
		finally
		{
			reader.dispose();
		}
	}

	/**
	 * When each frame was captured, relative to the death in milliseconds. Uses the times saved with the
	 * recap when they match the replay; otherwise rebuilds them from the frame durations, assuming the
	 * recording ran {@link DeathCapture#AFTER_DEATH_MS} past the death.
	 */
	static int[] times(int[] saved, int[] durations)
	{
		if (saved != null && saved.length == durations.length)
		{
			return saved;
		}
		int[] times = new int[durations.length];
		int t = (int) DeathCapture.AFTER_DEATH_MS;
		for (int i = durations.length - 1; i >= 0; i--)
		{
			times[i] = t;
			if (i > 0)
			{
				t -= durations[i - 1];
			}
		}
		return times;
	}

	/** The last frame captured at or before {@code time}, or the first frame. */
	static int frameAt(int[] times, long time)
	{
		int found = 0;
		for (int i = 0; i < times.length; i++)
		{
			if (times[i] > time)
			{
				break;
			}
			found = i;
		}
		return found;
	}

	private static IIOMetadataNode child(IIOMetadataNode root, String name)
	{
		for (int i = 0; i < root.getLength(); i++)
		{
			if (root.item(i).getNodeName().equals(name))
			{
				return (IIOMetadataNode) root.item(i);
			}
		}
		return null;
	}
}
