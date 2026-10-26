package com.jobwarden.jobs.dto;

import java.util.Arrays;

/**
 * A small PNG: a site's icon, as the browser showed it on a posting's tab, or a company's logo from a posting.
 *
 * @param png the image, at most {@link #MAX_SIZE} bytes
 */
public record PngImage(byte[] png) {

	/** Far more than an icon or a logo takes: they come in at most 64 by 64 pixels. */
	public static final int MAX_SIZE = 64 * 1024;

	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	/**
	 * @throws IllegalArgumentException if it isn't a PNG, or it's too big
	 */
	public PngImage {
		if (!isPng(png)) {
			throw new IllegalArgumentException("Not a PNG of at most " + MAX_SIZE + " bytes");
		}
		png = png.clone();
	}

	/**
	 * Whether the bytes are a PNG {@code PngImage} takes: they start like one, and aren't too big.
	 */
	public static boolean isPng(byte[] bytes) {
		return bytes.length > PNG_SIGNATURE.length && bytes.length <= MAX_SIZE
				&& Arrays.equals(bytes, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length);
	}

	@Override
	public byte[] png() {
		return this.png.clone();
	}

	// A record compares arrays by identity; two images are equal when their bytes are

	@Override
	public boolean equals(Object other) {
		return other instanceof PngImage image && Arrays.equals(this.png, image.png);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.png);
	}

	@Override
	public String toString() {
		return "PngImage[" + this.png.length + " bytes]";
	}

}
