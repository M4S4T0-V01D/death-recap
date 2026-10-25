package com.deathrecap;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * Writes a looping animated GIF with the JDK's own encoder. Frames are read one at a time so only two
 * are ever in memory, and after the first frame only the area that changed is written, which keeps
 * full-size replays small when the camera is still.
 */
final class GifWriter
{
	private static final String FORMAT = "javax_imageio_gif_image_1.0";

	interface Frames
	{
		BufferedImage get(int index) throws IOException;
	}

	private GifWriter()
	{
	}

	/**
	 * @param delays per-frame display time in hundredths of a second; its length is the frame count
	 */
	static void write(Frames frames, int[] delays, OutputStream out) throws IOException
	{
		ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(out))
		{
			writer.setOutput(ios);
			writer.prepareWriteSequence(null);
			int[] previous = null;
			int width = 0;
			int height = 0;
			for (int i = 0; i < delays.length; i++)
			{
				BufferedImage frame = frames.get(i);
				if (i == 0)
				{
					width = frame.getWidth();
					height = frame.getHeight();
				}
				int[] pixels = pixels(frame, width, height);
				Rectangle area = previous == null ? new Rectangle(0, 0, width, height) : changed(previous, pixels, width, height);
				previous = pixels;

				BufferedImage part = crop(pixels, width, area);
				IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(part), null);
				IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(FORMAT);

				IIOMetadataNode descriptor = child(root, "ImageDescriptor");
				descriptor.setAttribute("imageLeftPosition", Integer.toString(area.x));
				descriptor.setAttribute("imageTopPosition", Integer.toString(area.y));
				descriptor.setAttribute("imageWidth", Integer.toString(area.width));
				descriptor.setAttribute("imageHeight", Integer.toString(area.height));
				descriptor.setAttribute("interlaceFlag", "FALSE");

				IIOMetadataNode gce = child(root, "GraphicControlExtension");
				gce.setAttribute("disposalMethod", "doNotDispose");
				gce.setAttribute("userInputFlag", "FALSE");
				gce.setAttribute("transparentColorFlag", "FALSE");
				gce.setAttribute("transparentColorIndex", "0");
				gce.setAttribute("delayTime", Integer.toString(delays[i]));

				if (i == 0)
				{
					// NETSCAPE2.0 extension with a loop count of 0: loop forever.
					IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
					app.setAttribute("applicationID", "NETSCAPE");
					app.setAttribute("authenticationCode", "2.0");
					app.setUserObject(new byte[]{1, 0, 0});
					child(root, "ApplicationExtensions").appendChild(app);
				}

				meta.setFromTree(FORMAT, root);
				writer.writeToSequence(new IIOImage(part, null, meta), null);
			}
			writer.endWriteSequence();
		}
		finally
		{
			writer.dispose();
		}
	}

	/** Pixels of the frame at the replay's size; a frame of another size (the window was resized) is scaled. */
	private static int[] pixels(BufferedImage frame, int width, int height)
	{
		if (frame.getWidth() != width || frame.getHeight() != height)
		{
			BufferedImage fitted = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = fitted.createGraphics();
			g.drawImage(frame, 0, 0, width, height, null);
			g.dispose();
			frame = fitted;
		}
		return frame.getRGB(0, 0, width, height, null, 0, width);
	}

	/** The smallest rectangle holding every pixel that differs, or a single pixel if nothing changed. */
	static Rectangle changed(int[] a, int[] b, int width, int height)
	{
		int minX = width;
		int minY = height;
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < height; y++)
		{
			int row = y * width;
			for (int x = 0; x < width; x++)
			{
				if (a[row + x] != b[row + x])
				{
					if (x < minX)
					{
						minX = x;
					}
					if (x > maxX)
					{
						maxX = x;
					}
					if (y < minY)
					{
						minY = y;
					}
					maxY = y;
				}
			}
		}
		if (maxX < 0)
		{
			return new Rectangle(0, 0, 1, 1);
		}
		return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	private static BufferedImage crop(int[] pixels, int width, Rectangle area)
	{
		BufferedImage part = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_RGB);
		part.setRGB(0, 0, area.width, area.height, pixels, area.y * width + area.x, width);
		return part;
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
		IIOMetadataNode node = new IIOMetadataNode(name);
		root.appendChild(node);
		return node;
	}
}
