package com.jobwarden.common.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Images from outside, such as a posting's logo or a profile's photo, read with care and made small PNGs. Encoding one
 * anew also leaves out anything the file had besides the picture, such as where a photo was taken.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Images {

	/** Larger images aren't read: decoding one could take more memory than the server should give it. */
	public static final int MAX_PIXELS_A_SIDE = 4096;

	/**
	 * Reads a PNG, JPEG, GIF or BMP, after checking its size, which the file says before its pixels.
	 *
	 * @throws IOException if Java doesn't read it as an image, or it has over {@link #MAX_PIXELS_A_SIDE} pixels a side
	 */
	public static BufferedImage read(byte[] bytes) throws IOException {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = (input != null) ? ImageIO.getImageReaders(input) : null;
			if (readers == null || !readers.hasNext()) {
				throw new IOException("Not an image Java reads");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				if (reader.getWidth(0) > MAX_PIXELS_A_SIDE || reader.getHeight(0) > MAX_PIXELS_A_SIDE) {
					throw new TooLargeImageException("It's over " + MAX_PIXELS_A_SIDE + " pixels a side");
				}
				return reader.read(0);
			} finally {
				reader.dispose();
			}
		}
	}

	/**
	 * The image scaled down to fit {@code size} pixels, keeping its shape, as a PNG. A smaller one keeps its size.
	 */
	public static byte[] fittedPng(BufferedImage image, int size) throws IOException {
		double scale = Math.min(1, (double) size / Math.max(image.getWidth(), image.getHeight()));
		int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
		return png(drawn(image, 0, 0, image.getWidth(), image.getHeight(), width, height));
	}

	/**
	 * The square in the middle of the image, scaled down to {@code size} pixels a side, as a PNG: a photo for a round
	 * avatar. A smaller one keeps its size.
	 */
	public static byte[] squarePng(BufferedImage image, int size) throws IOException {
		int side = Math.min(image.getWidth(), image.getHeight());
		int x = (image.getWidth() - side) / 2;
		int y = (image.getHeight() - side) / 2;
		int target = Math.min(size, side);
		return png(drawn(image, x, y, side, side, target, target));
	}

	/** The part of the image from (x, y), width by height pixels, drawn at the new size */
	private static BufferedImage drawn(BufferedImage image, int x, int y, int width, int height, int newWidth,
			int newHeight) {
		BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = scaled.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(image, 0, 0, newWidth, newHeight, x, y, x + width, y + height, null);
		} finally {
			graphics.dispose();
		}
		return scaled;
	}

	private static byte[] png(BufferedImage image) throws IOException {
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(image, "png", png);
		return png.toByteArray();
	}

	/**
	 * An image with too many pixels to read: one Java would read, unlike any other {@link IOException} of
	 * {@link #read(byte[])}.
	 */
	public static final class TooLargeImageException extends IOException {

		TooLargeImageException(String message) {
			super(message);
		}

	}

}
