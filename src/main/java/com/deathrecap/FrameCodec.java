package com.deathrecap;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.w3c.dom.NodeList;

/**
 * Compresses replay frames while they wait in memory. A full-size frame is several megabytes raw but a
 * few hundred kilobytes as a high-quality JPEG. Chroma subsampling is turned off so coloured text such as
 * hitsplats and overhead messages stays sharp.
 */
final class FrameCodec
{
	private static final String FORMAT = "javax_imageio_jpeg_image_1.0";
	private static final float QUALITY = 0.92f;

	private FrameCodec()
	{
	}

	static byte[] encode(BufferedImage image) throws IOException
	{
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(256 * 1024);
		try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes))
		{
			writer.setOutput(out);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(QUALITY);

			IIOMetadata meta = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), param);
			IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(FORMAT);
			NodeList components = root.getElementsByTagName("componentSpec");
			for (int i = 0; i < components.getLength(); i++)
			{
				IIOMetadataNode c = (IIOMetadataNode) components.item(i);
				c.setAttribute("HsamplingFactor", "1");
				c.setAttribute("VsamplingFactor", "1");
			}
			meta.setFromTree(FORMAT, root);
			writer.write(null, new IIOImage(image, null, meta), param);
		}
		finally
		{
			writer.dispose();
		}
		return bytes.toByteArray();
	}

	static BufferedImage decode(byte[] jpeg) throws IOException
	{
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
		if (image == null)
		{
			throw new IOException("Unreadable replay frame");
		}
		return image;
	}
}
