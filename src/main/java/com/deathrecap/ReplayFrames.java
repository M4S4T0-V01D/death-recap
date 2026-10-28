package com.deathrecap;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
	/** Starts a replay's full-quality frames file: "DRF1". */
	private static final int MAGIC = 0x44524631;
	/** How long the last frame of a frames file is shown, matching the GIF's final delay. */
	static final int LAST_FRAME_MS = 1500;

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
	 * Writes the frames as recorded, so videos made later don't suffer the GIF's 256 colours.
	 *
	 * @param times when each frame was captured, relative to the death in ms
	 */
	static void writeFrames(OutputStream out, List<byte[]> jpegs, int[] times) throws IOException
	{
		DataOutputStream data = new DataOutputStream(out);
		data.writeInt(MAGIC);
		data.writeInt(jpegs.size());
		for (int i = 0; i < jpegs.size(); i++)
		{
			data.writeInt(times[i]);
			data.writeInt(jpegs.get(i).length);
			data.write(jpegs.get(i));
		}
		data.flush();
	}

	/** Decodes either a frames file or a replay GIF. */
	static Decoded load(byte[] data, IntConsumer progress) throws IOException
	{
		if (data.length < 8 || data[0] != 'D' || data[1] != 'R' || data[2] != 'F' || data[3] != '1')
		{
			return decode(data, progress);
		}
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
		in.readInt();
		int count = in.readInt();
		if (count < 1 || count > 10_000)
		{
			throw new IOException("Bad frame count " + count);
		}
		List<byte[]> frames = new ArrayList<>(count);
		int[] times = new int[count];
		for (int i = 0; i < count; i++)
		{
			times[i] = in.readInt();
			int length = in.readInt();
			if (length < 0 || length > in.available())
			{
				throw new IOException("Truncated frames file");
			}
			byte[] jpeg = new byte[length];
			in.readFully(jpeg);
			frames.add(jpeg);
			progress.accept(i + 1);
		}
		int[] durations = new int[count];
		for (int i = 0; i < count - 1; i++)
		{
			durations[i] = Math.max(20, times[i + 1] - times[i]);
		}
		durations[count - 1] = LAST_FRAME_MS;
		return new Decoded(frames, durations);
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
