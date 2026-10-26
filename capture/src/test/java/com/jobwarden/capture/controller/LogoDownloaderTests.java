package com.jobwarden.capture.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import com.jobwarden.jobs.dto.PngImage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against a web server of its own, on this machine: the downloader under test may go there, the real one never would.
 */
class LogoDownloaderTests {

	private static HttpServer server;

	private final LogoDownloader logos = new LogoDownloader(address -> true);

	@BeforeAll
	static void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/logo.jpg", exchange -> respond(exchange, "image/jpeg", image(200, 100, "jpg")));
		server.createContext("/small.png", exchange -> respond(exchange, "image/png", image(20, 20, "png")));
		server.createContext("/huge.png", exchange -> respond(exchange, "image/png", image(5000, 2, "png")));
		server.createContext("/page.html", exchange -> respond(exchange, "text/html",
				"<svg onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8)));
		server.createContext("/moved", exchange -> {
			exchange.getResponseHeaders().add("Location", "/logo.jpg");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});
		server.start();
	}

	@AfterAll
	static void stopServer() {
		server.stop(0);
	}

	@Test
	void shrinksTheLogoKeepingItsShape() throws IOException {
		BufferedImage logo = read(this.logos.download(address("/logo.jpg")).orElseThrow());

		assertThat(logo.getWidth()).isEqualTo(LogoDownloader.SIZE);
		assertThat(logo.getHeight()).isEqualTo(LogoDownloader.SIZE / 2);
	}

	@Test
	void keepsASmallLogoAsItIs() throws IOException {
		BufferedImage logo = read(this.logos.download(address("/small.png")).orElseThrow());

		assertThat(logo.getWidth()).isEqualTo(20);
	}

	@Test
	void followsARedirect() {
		assertThat(this.logos.download(address("/moved"))).isPresent();
	}

	@Test
	void givesNothingForWhatIsNotALogo() {
		assertThat(this.logos.download(address("/page.html"))).isEmpty();
		assertThat(this.logos.download(address("/missing.png"))).isEmpty();
		assertThat(this.logos.download(address("/huge.png"))).as("too many pixels").isEmpty();
		assertThat(this.logos.download("ftp://example.com/logo.png")).isEmpty();
		assertThat(this.logos.download("not an address")).isEmpty();
		assertThat(this.logos.download(null)).isEmpty();
	}

	@Test
	void neverDownloadsFromThisMachine() {
		assertThat(new LogoDownloader().download(address("/logo.jpg"))).isEmpty();
	}

	@Test
	void goesOnlyToThePublicInternet() throws IOException {
		for (String local : new String[] { "127.0.0.1", "0.0.0.0", "10.1.2.3", "172.16.0.1", "192.168.1.1",
				"169.254.169.254", "100.64.0.1", "224.0.0.1", "::1", "fd00::1", "fe80::1" }) {
			assertThat(LogoDownloader.isPublic(InetAddress.getByName(local))).as(local).isFalse();
		}
		for (String remote : new String[] { "8.8.8.8", "151.101.1.1", "2001:4860:4860::8888" }) {
			assertThat(LogoDownloader.isPublic(InetAddress.getByName(remote))).as(remote).isTrue();
		}
	}

	private static String address(String path) {
		return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + path;
	}

	private static BufferedImage read(PngImage logo) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(logo.png()));
	}

	private static byte[] image(int width, int height, String format) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, bytes);
			return bytes.toByteArray();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static void respond(HttpExchange exchange, String contentType, byte[] body) throws IOException {
		exchange.getResponseHeaders().add("Content-Type", contentType);
		exchange.sendResponseHeaders(200, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

}
