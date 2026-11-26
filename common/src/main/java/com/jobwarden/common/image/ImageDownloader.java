package com.jobwarden.common.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;

import com.jobwarden.common.net.HttpClients;
import com.jobwarden.common.net.PublicAddresses;

/**
 * Downloads an image from an address someone else gave, a posting or the AI Assistant, and makes it a small PNG: a
 * company's logo. The address could point anywhere, so it only goes to the public internet: never to this machine or
 * the local network ({@link PublicAddresses}), also after a redirect, which it follows by hand to check each address.
 */
public final class ImageDownloader {

	/** More than any logo takes; anything bigger isn't one. */
	private static final int MAX_BYTES = 1024 * 1024;

	private static final int MAX_REDIRECTS = 3;

	private final HttpClient http;

	private final Duration timeout;

	private final Predicate<InetAddress> allowed;

	/**
	 * @param timeout for connecting, and for the answer
	 */
	public ImageDownloader(Duration timeout) {
		this(timeout, PublicAddresses::isPublic);
	}

	/**
	 * @param allowed which addresses it may download from: a test's own server is on this machine
	 */
	public ImageDownloader(Duration timeout, Predicate<InetAddress> allowed) {
		this.http = HttpClients.notFollowingRedirects(timeout);
		this.timeout = timeout;
		this.allowed = allowed;
	}

	/**
	 * @param address an http or https address
	 * @param size the largest side the PNG may have, in pixels
	 * @return the image as a PNG, scaled down to fit {@code size}
	 * @throws IOException saying why there's none: not a web address, not public, an error, too big, or not an image
	 * Java reads (PNG, JPEG, GIF, BMP)
	 */
	public byte[] fittedPng(String address, int size) throws IOException, InterruptedException {
		URI uri;
		try {
			uri = URI.create(address.strip());
		} catch (IllegalArgumentException notAnAddress) {
			throw new IOException("Not a web address", notAnAddress);
		}
		return Images.fittedPng(fetch(uri), size);
	}

	private BufferedImage fetch(URI address) throws IOException, InterruptedException {
		URI current = address;
		for (int redirects = 0;; redirects++) {
			requireAllowed(current);
			HttpRequest request = HttpRequest.newBuilder(current)
				.timeout(this.timeout)
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
				return Images.read(bytes);
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

}
