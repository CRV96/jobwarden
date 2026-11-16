package com.jobwarden.common.image;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import com.jobwarden.common.image.Images.TooLargeImageException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;

class ImagesTests {

	@Test
	void keepsTheSquareInTheMiddleOfAPhotoMadeSmall() throws IOException {
		// Red at the sides, blue in the middle: only the blue is kept
		BufferedImage photo = new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < 600; x++) {
			for (int y = 0; y < 300; y++) {
				photo.setRGB(x, y, (x >= 150 && x < 450) ? Color.BLUE.getRGB() : Color.RED.getRGB());
			}
		}

		BufferedImage square = ImageIO.read(new ByteArrayInputStream(Images.squarePng(Images.read(jpeg(photo)), 160)));

		assertThat(square.getWidth()).isEqualTo(160);
		assertThat(square.getHeight()).isEqualTo(160);
		assertThat(new Color(square.getRGB(5, 80)).getBlue()).isGreaterThan(200);
		assertThat(new Color(square.getRGB(155, 80)).getBlue()).isGreaterThan(200);
	}

	@Test
	void neverMakesASmallImageLarger() throws IOException {
		BufferedImage small = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);

		BufferedImage square = ImageIO.read(new ByteArrayInputStream(Images.squarePng(small, 160)));
		BufferedImage fitted = ImageIO.read(new ByteArrayInputStream(Images.fittedPng(small, 64)));

		assertThat(square.getWidth()).isEqualTo(20);
		assertThat(fitted.getWidth()).isEqualTo(40);
		assertThat(fitted.getHeight()).isEqualTo(20);
	}

	@Test
	void fitsALargeImageKeepingItsShape() throws IOException {
		BufferedImage wide = new BufferedImage(400, 100, BufferedImage.TYPE_INT_ARGB);

		BufferedImage fitted = ImageIO.read(new ByteArrayInputStream(Images.fittedPng(wide, 64)));

		assertThat(fitted.getWidth()).isEqualTo(64);
		assertThat(fitted.getHeight()).isEqualTo(16);
	}

	@Test
	void refusesWhatIsntAnImage() {
		byte[] text = "Not a photo".getBytes(StandardCharsets.UTF_8);

		assertThatIOException().isThrownBy(() -> Images.read(text)).isNotInstanceOf(TooLargeImageException.class);
		assertThatIOException().isThrownBy(() -> Images.read(new byte[0]));
	}

	@Test
	void refusesAnImageWithTooManyPixelsBeforeReadingThem() throws IOException {
		byte[] huge = png(new BufferedImage(Images.MAX_PIXELS_A_SIDE + 1, 1, BufferedImage.TYPE_BYTE_BINARY));

		assertThatExceptionOfType(TooLargeImageException.class).isThrownBy(() -> Images.read(huge));
	}

	private static byte[] jpeg(BufferedImage image) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, "jpeg", bytes);
		return bytes.toByteArray();
	}

	private static byte[] png(BufferedImage image) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, "png", bytes);
		return bytes.toByteArray();
	}

}
