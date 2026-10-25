package com.deathrecap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import org.junit.Test;

public class GifWriterTest
{
	@Test
	public void writesLoopingAnimation() throws Exception
	{
		List<BufferedImage> frames = new ArrayList<>();
		Color[] colours = {Color.RED, Color.GREEN, Color.BLUE};
		for (Color c : colours)
		{
			BufferedImage img = new BufferedImage(48, 27, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = img.createGraphics();
			g.setColor(c);
			g.fillRect(0, 0, 48, 27);
			g.dispose();
			frames.add(img);
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GifWriter.write(frames::get, new int[]{17, 17, 150}, out);
		byte[] gif = out.toByteArray();

		assertTrue(new String(gif, StandardCharsets.ISO_8859_1).contains("NETSCAPE2.0"));
		ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
		try (ImageInputStream in = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(gif)))
		{
			reader.setInput(in);
			assertEquals(3, reader.getNumImages(true));
			assertEquals(48, reader.read(0).getWidth());
			IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(2).getAsTree("javax_imageio_gif_image_1.0");
			IIOMetadataNode gce = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
			assertEquals("150", gce.getAttribute("delayTime"));
			assertEquals(Color.BLUE.getRGB(), reader.read(2).getRGB(10, 10));
		}
		finally
		{
			reader.dispose();
		}
	}

	@Test
	public void writesOnlyTheChangedArea() throws Exception
	{
		BufferedImage first = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
		BufferedImage second = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = second.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(20, 10, 30, 5);
		g.dispose();
		List<BufferedImage> frames = List.of(first, second);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GifWriter.write(frames::get, new int[]{10, 10}, out);

		ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
		try (ImageInputStream in = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(out.toByteArray())))
		{
			reader.setInput(in);
			IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(1).getAsTree("javax_imageio_gif_image_1.0");
			IIOMetadataNode d = (IIOMetadataNode) root.getElementsByTagName("ImageDescriptor").item(0);
			assertEquals("20", d.getAttribute("imageLeftPosition"));
			assertEquals("10", d.getAttribute("imageTopPosition"));
			assertEquals("30", d.getAttribute("imageWidth"));
			assertEquals("5", d.getAttribute("imageHeight"));
		}
		finally
		{
			reader.dispose();
		}
	}
}
