package com.jobwarden.capture.controller;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Predicate;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.jobwarden.jobs.dto.PngImage;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/**
 * Downloads the company logo a posting points to, and makes it a small PNG. The extension can't do it itself: most
 * sites don't let other pages read their images.
 * <p>
 * The address comes from the posting, which anyone could have written, so it only goes to the public internet: never
 * to this machine or the local network ({@link #isPublic}), also after a redirect. Whatever goes wrong, there's no
 * logo, and the posting is saved without one.
 */
@Component
@CustomLog
class LogoDownloader {

	/** The logo's largest side in pixels: what the pages show, on a screen with twice the pixels. */
	static final int SIZE = 64;

	/** For connecting and for the answer: saving a posting waits for the logo. */
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	/** More than any logo takes; anything bigger isn't one. */
	private static final int MAX_BYTES = 1024 * 1024;

	/** Nor are larger images, and decoding one could take more memory than the server should give it. */
	private static final int MAX_PIXELS_A_SIDE = 4096;

	private static final int MAX_REDIRECTS = 3;

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT)
		// Followed by hand, so each address is checked
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();

	private final Predicate<InetAddress> allowed;

	LogoDownloader() {
		this(LogoDownloader::isPublic);
	}

	/**
	 * @param allowed which addresses it may download from: the tests' own server is on this machine
	 */
	LogoDownloader(Predicate<InetAddress> allowed) {
		this.allowed = allowed;
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
			return Optional.of(new PngImage(toPng(fetch(URI.create(address)))));
		} catch (IOException | IllegalArgumentException exception) {
			log.debug("No company logo from {}: {}", address, exception.getMessage());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
		return Optional.empty();
	}

	private BufferedImage fetch(URI address) throws IOException, InterruptedException {
		URI current = address;
		for (int redirects = 0;; redirects++) {
			requireAllowed(current);
			HttpRequest request = HttpRequest.newBuilder(current)
				.timeout(TIMEOUT)
				// Formats Java reads: some image servers would rather send WebP
				.header("Accept", "image/png,image/jpeg,image/gif;q=0.9")
				.GET()
				.build();
			HttpResponse<InputStream> response = this.http.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = response.body()) {
				int status = response.statusCode();
				Optional<String> location = response.headers().firstValue("Location");
				if (status / 100 == 3 && location.isPresent() && redirects < MAX_REDIRECTS) {
					current = current.resolve(location.get());
					continue;
				}
				if (status != 200) {
					throw new IOException("It answered " + status);
				}
				byte[] bytes = body.readNBytes(MAX_BYTES + 1);
				if (bytes.length > MAX_BYTES) {
					throw new IOException("It's over " + MAX_BYTES + " bytes");
				}
				return decode(bytes);
			}
		}
	}

	private void requireAllowed(URI address) throws IOException {
		if ((!"https".equals(address.getScheme()) && !"http".equals(address.getScheme())) || address.getHost() == null) {
			throw new IOException("Not a web address");
		}
		for (InetAddress resolved : InetAddress.getAllByName(address.getHost())) {
			if (!this.allowed.test(resolved)) {
				throw new IOException("Not on the public internet: " + resolved.getHostAddress());
			}
		}
	}

	/**
	 * Reads the image's size before the image, so a huge one is refused before it takes the memory.
	 */
	private static BufferedImage decode(byte[] bytes) throws IOException {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw new IOException("Not an image Java reads");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				if (reader.getWidth(0) > MAX_PIXELS_A_SIDE || reader.getHeight(0) > MAX_PIXELS_A_SIDE) {
					throw new IOException("It's over " + MAX_PIXELS_A_SIDE + " pixels a side");
				}
				return reader.read(0);
			} finally {
				reader.dispose();
			}
		}
	}

	/**
	 * Scales the image down to fit {@link #SIZE} pixels, keeping its shape, and encodes it as a PNG. Encoding it anew
	 * also leaves out anything the file had besides the picture.
	 */
	private static byte[] toPng(BufferedImage image) throws IOException {
		double scale = Math.min(1, (double) SIZE / Math.max(image.getWidth(), image.getHeight()));
		int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = scaled.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(image, 0, 0, width, height, null);
		} finally {
			graphics.dispose();
		}
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(scaled, "png", png);
		return png.toByteArray();
	}

	/**
	 * Whether the address is on the public internet: not this machine, not the local network (including carrier-grade
	 * NAT and IPv6 unique local addresses), not multicast.
	 */
	static boolean isPublic(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return false;
		}
		byte[] bytes = address.getAddress();
		if (bytes.length == 4) {
			// 100.64.0.0/10
			return !((bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64);
		}
		// fc00::/7
		return (bytes[0] & 0xFE) != 0xFC;
	}

}
