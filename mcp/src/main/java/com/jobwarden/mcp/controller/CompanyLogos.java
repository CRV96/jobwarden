package com.jobwarden.mcp.controller;

import java.io.IOException;
import java.time.Duration;

import com.jobwarden.common.image.ImageDownloader;
import com.jobwarden.jobs.dto.PngImage;
import lombok.CustomLog;

import org.springframework.stereotype.Component;

/**
 * Downloads a company logo the assistant found on the web, as a small PNG, like the one a posting shows. The address
 * is the assistant's, read from pages anyone could write, so it only goes to the public internet ({@link
 * ImageDownloader}).
 */
@Component
@CustomLog
class CompanyLogos {

	/** The logo's largest side in pixels, as the browser extension's are kept. */
	static final int SIZE = 64;

	/** For connecting and for the answer: the assistant waits, and can try another address. */
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private final ImageDownloader images = new ImageDownloader(TIMEOUT);

	/**
	 * @return the logo at the address
	 * @throws ToolException saying why there's none, for the assistant to try another address
	 */
	PngImage download(String address) {
		try {
			return new PngImage(this.images.fittedPng(address, SIZE));
		} catch (IOException | IllegalArgumentException failed) {
			log.debug("No company logo from {}: {}", address, failed.getMessage());
			throw new ToolException("JobWarden couldn't use that image: " + failed.getMessage()
					+ ". Try another address: a PNG, JPEG or GIF, not SVG or WebP");
		} catch (InterruptedException _) {
			Thread.currentThread().interrupt();
			throw new ToolException("Interrupted while downloading the image: try again");
		}
	}

}
