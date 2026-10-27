package com.jobwarden.mcp.controller;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import com.jobwarden.mcp.dto.PostingCheck;
import com.jobwarden.mcp.enums.Verdict;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against a web server of its own, on this machine: the checker under test may go there, the real one never would.
 */
class PostingCheckerTests {

	private static HttpServer server;

	private final PostingChecker checker = new PostingChecker(address -> true);

	@BeforeAll
	static void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/gone", exchange -> respond(exchange, 404, "Not found"));
		server.createContext("/blocked", exchange -> respond(exchange, 999, "Request denied"));
		server.createContext("/linkedin-closed", exchange -> respond(exchange, 200,
				"<html><body><h1>Java Developer</h1><span class=\"closed\">No longer accepting applications</span></body></html>"));
		server.createContext("/expired", exchange -> respond(exchange, 200, jobPosting(LocalDate.now().minusDays(3))));
		server.createContext("/open", exchange -> respond(exchange, 200, jobPosting(LocalDate.now().plusDays(30))));
		server.createContext("/plain", exchange -> respond(exchange, 200, "<html><body>Apply now</body></html>"));
		server.createContext("/scripted", exchange -> respond(exchange, 200,
				"<html><script>const message = 'This job is no longer available';</script><body>Apply now</body></html>"));
		server.createContext("/acme/jobs/42", exchange -> redirect(exchange, "/acme"));
		server.createContext("/acme", exchange -> respond(exchange, 200, "<html><body>Open roles at Acme</body></html>"));
		server.createContext("/board/7", exchange -> redirect(exchange, "/board?error=true"));
		server.createContext("/board", exchange -> respond(exchange, 200, "<html><body>Our jobs</body></html>"));
		server.createContext("/jobs/view/4472244855",
				exchange -> redirect(exchange, "/jobs/software-engineer-jobs?trk=expired_jd_redirect"));
		server.createContext("/jobs/software-engineer-jobs",
				exchange -> respond(exchange, 200, "<html><body>Software Engineer jobs</body></html>"));
		server.start();
	}

	@AfterAll
	static void stopServer() {
		server.stop(0);
	}

	@Test
	void aPageThatIsGoneIsClosed() {
		PostingCheck check = this.checker.check(address("/gone"));

		assertThat(check.verdict()).isEqualTo(Verdict.CLOSED);
		assertThat(check.certain()).isTrue();
		assertThat(check.httpStatus()).isEqualTo(404);
	}

	@Test
	void cannotTellWhenTheSiteBlocksAutomatedVisits() {
		PostingCheck check = this.checker.check(address("/blocked"));

		assertThat(check.verdict()).isEqualTo(Verdict.UNKNOWN);
		assertThat(check.reason()).contains("open it in a browser");
	}

	@Test
	void readsWhatThePageSays() {
		PostingCheck check = this.checker.check(address("/linkedin-closed"));

		assertThat(check.verdict()).isEqualTo(Verdict.CLOSED);
		assertThat(check.evidence()).contains("no longer accepting applications");
	}

	@Test
	void readsTheJobDatasEndDate() {
		assertThat(this.checker.check(address("/expired")).verdict()).isEqualTo(Verdict.CLOSED);

		PostingCheck open = this.checker.check(address("/open"));
		assertThat(open.verdict()).isEqualTo(Verdict.OPEN);
		assertThat(open.certain()).isTrue();
	}

	@Test
	void aPageThatOnlyLoadsIsOpenButNotCertainly() {
		PostingCheck check = this.checker.check(address("/plain"));

		assertThat(check.verdict()).isEqualTo(Verdict.OPEN);
		assertThat(check.certain()).isFalse();
	}

	@Test
	void readsOnlyTheTextAReaderSees() {
		assertThat(this.checker.check(address("/scripted")).verdict()).isEqualTo(Verdict.OPEN);
	}

	@Test
	void aRedirectAwayFromThePostingMeansItWasTakenDown() {
		PostingCheck toTheBoard = this.checker.check(address("/acme/jobs/42"));
		assertThat(toTheBoard.verdict()).isEqualTo(Verdict.CLOSED);
		assertThat(toTheBoard.finalUrl()).endsWith("/acme");

		// Greenhouse's error page says it outright
		PostingCheck toTheErrorPage = this.checker.check(address("/board/7"));
		assertThat(toTheErrorPage.verdict()).isEqualTo(Verdict.CLOSED);
		assertThat(toTheErrorPage.certain()).isTrue();
	}

	@Test
	void linkedInsRedirectToASearchMeansThePostingExpired() {
		// Not a page above the posting's, but marked as where LinkedIn sends the expired ones
		PostingCheck check = this.checker.check(address("/jobs/view/4472244855"));

		assertThat(check.verdict()).isEqualTo(Verdict.CLOSED);
		assertThat(check.certain()).isTrue();
		assertThat(check.evidence()).isEqualTo("trk=expired_jd_redirect");
	}

	@Test
	void neverChecksThisMachine() {
		PostingCheck check = new PostingChecker().check(address("/open"));

		assertThat(check.verdict()).isEqualTo(Verdict.UNKNOWN);
		assertThat(check.reason()).contains("not on the public internet");
	}

	@Test
	void checksOnlyWebAddresses() {
		assertThat(this.checker.check("file:///etc/passwd").reason()).contains("Not a public web address");
	}

	private static String jobPosting(LocalDate validThrough) {
		return """
				<html><head><script type="application/ld+json">
				{"@context": "https://schema.org", "@type": "JobPosting", "title": "Java Developer", "validThrough": "%sT00:00"}
				</script></head><body>Java Developer</body></html>""".formatted(validThrough);
	}

	private static String address(String path) {
		return "http://localhost:" + server.getAddress().getPort() + path;
	}

	private static void redirect(HttpExchange exchange, String location) throws IOException {
		exchange.getResponseHeaders().add("Location", location);
		exchange.sendResponseHeaders(302, -1);
		exchange.close();
	}

	private static void respond(HttpExchange exchange, int status, String html) throws IOException {
		byte[] body = html.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
		exchange.sendResponseHeaders(status, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

}
