package com.jobwarden.capture.controller;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;

import com.jobwarden.common.image.ImageDownloader;
import com.jobwarden.common.net.PublicAddresses;
import com.jobwarden.jobs.dto.PngImage;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/**
 * Downloads the company logo a posting points to, and makes it a small PNG. The extension can't do it itself: most
 * sites don't let other pages read their images.
 * <p>
 * The address comes from the posting, which anyone could have written, so it only goes to the public internet
 * ({@link ImageDownloader}). Whatever goes wrong, there's no logo, and the posting is saved without one.
 */
@Component
@CustomLog
class LogoDownloader {

	/** The logo's largest side in pixels: what the pages show, on a screen with twice the pixels. */
	static final int SIZE = 64;

	/** For connecting and for the answer: saving a posting waits for the logo. */
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	private final ImageDownloader images;

	LogoDownloader() {
		this(PublicAddresses::isPublic);
	}

	/**
	 * @param allowed which addresses it may download from: the tests' own server is on this machine
	 */
	LogoDownloader(Predicate<InetAddress> allowed) {
		this.images = new ImageDownloader(TIMEOUT, allowed);
	}

	/**
	 * @param address an http or https address; nothing for {@code null}
	 * @return the logo, at most {@link #SIZE} pixels a side, or empty if it couldn't be downloaded or read
	 */
	Optional<PngImage> download(@Nullable String address) {
		if (address == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(new PngImage(this.images.fittedPng(address, SIZE)));
		} catch (IOException | IllegalArgumentException exception) {
			log.debug("No company logo from {}: {}", address, exception.getMessage());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
		return Optional.empty();
	}

}
