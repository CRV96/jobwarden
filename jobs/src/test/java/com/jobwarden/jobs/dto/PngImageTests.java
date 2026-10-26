package com.jobwarden.jobs.dto;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PngImageTests {

	/** The signature every PNG starts with, and a few bytes for the image. */
	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13 };

	@Test
	void takesOnlyAPng() {
		assertThat(PngImage.isPng(PNG)).isTrue();
		assertThat(PngImage.isPng("<svg onload=alert(1)>".getBytes())).isFalse();
		assertThat(PngImage.isPng(Arrays.copyOf(PNG, 8))).as("the signature alone").isFalse();
		assertThatIllegalArgumentException().isThrownBy(() -> new PngImage("GIF89a....".getBytes()));
	}

	@Test
	void takesOnlyAnIconSizedImage() {
		byte[] tooBig = Arrays.copyOf(PNG, PngImage.MAX_SIZE + 1);

		assertThat(PngImage.isPng(Arrays.copyOf(PNG, PngImage.MAX_SIZE))).isTrue();
		assertThatIllegalArgumentException().isThrownBy(() -> new PngImage(tooBig));
	}

	@Test
	void comparesTheImagesAndKeepsItsOwnCopy() {
		byte[] image = PNG.clone();
		PngImage icon = new PngImage(image);
		image[9] = 42;

		assertThat(icon).isEqualTo(new PngImage(PNG)).hasSameHashCodeAs(new PngImage(PNG));
		assertThat(icon.png()).isEqualTo(PNG);
		assertThat(icon).hasToString("PngImage[12 bytes]");
	}

}
