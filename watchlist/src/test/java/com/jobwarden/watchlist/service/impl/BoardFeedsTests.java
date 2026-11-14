package com.jobwarden.watchlist.service.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.jobwarden.search.EmploymentType;
import com.jobwarden.search.WorkType;
import com.jobwarden.watchlist.enums.Ats;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Against a server here that answers as Greenhouse's, Lever's and Ashby's feeds do, with a job or two each.
 */
class BoardFeedsTests {

	private HttpServer boards;

	private BoardFeeds feeds;

	/** Let go once the test is over: the board that sends a little, then stops. */
	private final CountDownLatch stalled = new CountDownLatch(1);

	/** The User-Agent each path was asked with. */
	private final Map<String, String> asked = new ConcurrentHashMap<>();

	@BeforeEach
	void startBoards() throws IOException {
		this.boards = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		this.boards.createContext("/", this::answer);
		// A thread for each answer, so the one that stalls doesn't hold up the others
		this.boards.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		this.boards.start();
		URI here = URI.create("http://127.0.0.1:" + this.boards.getAddress().getPort());
		this.feeds = new BoardFeeds("1.2.3",
				new WatchlistProperties(Duration.ofHours(6), here, here, URI.create(here + "/eu"), here), Duration.ofSeconds(1));
	}

	@AfterEach
	void stopBoards() {
		this.stalled.countDown();
		this.boards.stop(0);
		this.feeds.stop();
	}

	@Test
	void readsAGreenhouseBoardAndAJobsDescription() {
		Board board = new Board(Ats.GREENHOUSE, "acme");

		assertThat(this.feeds.postings(board)).singleElement().satisfies(posting -> {
			assertThat(posting.id()).isEqualTo("4012");
			assertThat(posting.title()).isEqualTo("Senior Java Engineer");
			assertThat(posting.location()).isEqualTo("Remote, Germany");
			assertThat(posting.link()).isEqualTo("https://job-boards.greenhouse.io/acme/jobs/4012");
			assertThat(posting.company()).isEqualTo("Acme");
			assertThat(posting.publishedAt()).isEqualTo(Instant.parse("2026-11-02T13:38:40Z"));
			assertThat(posting.description()).isNull();
		});
		assertThat(this.feeds.description(board, "4012")).isEqualTo("We build payments.\n\n• Java\n• Kafka");
		assertThat(this.feeds.description(board, "999")).isNull();
		assertThat(this.asked).containsEntry("/v1/boards/acme/jobs",
				"JobWarden/1.2.3 (+https://github.com/CRV96/jobwarden)");
	}

	@Test
	void readsALeverBoardInTheUsOrInEurope() {
		assertThat(this.feeds.postings(new Board(Ats.LEVER, "acme"))).singleElement().satisfies(posting -> {
			assertThat(posting.title()).isEqualTo("Backend Developer");
			assertThat(posting.location()).isEqualTo("Bucharest; Cluj-Napoca");
			assertThat(posting.countries()).containsExactly("RO");
			assertThat(posting.workType()).isEqualTo(WorkType.HYBRID);
			assertThat(posting.employmentStated()).isTrue();
			assertThat(posting.employmentType()).isEqualTo(EmploymentType.CONTRACT);
			assertThat(posting.link()).isEqualTo("https://jobs.lever.co/acme/5f1c");
			assertThat(posting.publishedAt()).isEqualTo(Instant.ofEpochMilli(1792767640768L));
			assertThat(posting.salary()).isEqualTo("EUR 60,000–80,000 per year");
			assertThat(posting.description()).isEqualTo("We move money.\n\nWhat you need\n• Go\n• Postgres\n\nBenefits: many.");
		});
		assertThat(this.feeds.postings(new Board(Ats.LEVER_EU, "acme"))).isEmpty();
		assertThat(this.asked).containsKeys("/v0/postings/acme", "/eu/v0/postings/acme");
	}

	@Test
	void readsAnAshbyBoardWithoutItsUnlistedJobs() {
		assertThat(this.feeds.postings(new Board(Ats.ASHBY, "acme"))).singleElement().satisfies(posting -> {
			assertThat(posting.title()).isEqualTo("Platform Engineer");
			assertThat(posting.location()).isEqualTo("NYC; London");
			assertThat(posting.countries()).containsExactlyInAnyOrder("US", "GB");
			assertThat(posting.workType()).isEqualTo(WorkType.REMOTE);
			assertThat(posting.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
			assertThat(posting.salary()).isEqualTo("$160K - $275K");
			assertThat(posting.description()).isEqualTo("Build the platform.");
		});
	}

	@Test
	void saysWhenThereIsNoSuchBoardOrItCantBeRead() {
		Board nobody = new Board(Ats.LEVER, "nobody");
		Board broken = new Board(Ats.ASHBY, "broken");
		Board html = new Board(Ats.ASHBY, "html");
		Board huge = new Board(Ats.ASHBY, "huge");

		assertThatExceptionOfType(BoardFailure.class).isThrownBy(() -> this.feeds.postings(nobody))
			.satisfies(failed -> assertThat(failed.notFound()).isTrue());
		assertThatExceptionOfType(BoardFailure.class).isThrownBy(() -> this.feeds.postings(broken))
			.satisfies(failed -> assertThat(failed.notFound()).isFalse())
			.withMessageContaining("500");
		assertThatExceptionOfType(BoardFailure.class).isThrownBy(() -> this.feeds.postings(html))
			.withMessageContaining("isn't a board's jobs");
		assertThatExceptionOfType(BoardFailure.class).isThrownBy(() -> this.feeds.postings(huge))
			.withMessageContaining("bigger than 40 MB");
	}

	@Test
	void givesUpOnABoardThatStopsSendingItsAnswer() {
		Board stopping = new Board(Ats.ASHBY, "stalled");
		long start = System.nanoTime();

		assertThatExceptionOfType(BoardFailure.class).isThrownBy(() -> this.feeds.postings(stopping))
			.withMessageContaining("couldn't be read");
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
	}

	private void answer(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		this.asked.put(path, String.valueOf(exchange.getRequestHeaders().getFirst("User-Agent")));
		switch (path) {
			case "/v1/boards/acme/jobs" -> send(exchange, 200, """
					{"jobs": [{"absolute_url": "https://job-boards.greenhouse.io/acme/jobs/4012", "id": 4012,
					  "location": {"name": "Remote, Germany"}, "title": "Senior Java Engineer", "company_name": "Acme",
					  "first_published": "2026-11-02T08:38:40-05:00", "updated_at": "2026-11-06T15:25:07-05:00",
					  "metadata": [{"id": 1, "value": null}]},
					 {"id": 4013, "title": "No link"}],
					 "meta": {"total": 2}}""");
			case "/v1/boards/acme/jobs/4012" -> send(exchange, 200, """
					{"id": 4012, "content": "&lt;p&gt;We build payments.&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Java&lt;/li&gt;&lt;li&gt;Kafka&lt;/li&gt;&lt;/ul&gt;"}""");
			case "/v0/postings/acme" -> send(exchange, 200, """
					[{"id": "5f1c", "text": "Backend Developer", "country": "ro", "workplaceType": "hybrid",
					  "categories": {"commitment": "Contract - Contract", "location": "Bucharest", "allLocations": ["Bucharest", "Cluj-Napoca"]},
					  "hostedUrl": "https://jobs.lever.co/acme/5f1c", "createdAt": 1792767640768,
					  "salaryRange": {"currency": "EUR", "interval": "per-year-salary", "min": 60000, "max": 80000},
					  "descriptionPlain": "We move money.",
					  "lists": [{"text": "What you need", "content": "<li>Go</li><li>Postgres</li>"}],
					  "additionalPlain": "Benefits: many."}]""");
			case "/eu/v0/postings/acme" -> send(exchange, 200, "[]");
			case "/posting-api/job-board/acme" -> send(exchange, 200, """
					{"apiVersion": "1", "jobs": [
					  {"id": "a1", "title": "Platform Engineer", "location": "NYC", "isRemote": true, "workplaceType": "Hybrid",
					   "employmentType": "FullTime", "isListed": true, "jobUrl": "https://jobs.ashbyhq.com/acme/a1",
					   "publishedAt": "2026-10-12T13:48:36.560+00:00", "descriptionPlain": "Build the platform.",
					   "address": {"postalAddress": {"addressCountry": "USA"}},
					   "secondaryLocations": [{"location": "London", "address": {"postalAddress": {"addressCountry": "United Kingdom"}}}],
					   "compensation": {"scrapeableCompensationSalarySummary": "$160K - $275K"}},
					  {"id": "a2", "title": "Hidden", "isListed": false, "jobUrl": "https://jobs.ashbyhq.com/acme/a2"}]}""");
			case "/posting-api/job-board/broken" -> send(exchange, 500, "{}");
			case "/posting-api/job-board/html" -> send(exchange, 200, "<html>Maintenance</html>");
			case "/posting-api/job-board/stalled" -> {
				// Sends a little, then nothing until the test is over
				exchange.sendResponseHeaders(200, 0);
				OutputStream out = exchange.getResponseBody();
				out.write("{\"jobs\": [{\"title\": ".getBytes(StandardCharsets.UTF_8));
				out.flush();
				awaitTheEnd();
				exchange.close();
			}
			case "/posting-api/job-board/huge" -> {
				// Says it's bigger than any board, and sends a little: the feed reads the length first
				exchange.sendResponseHeaders(200, 50L * 1024 * 1024);
				OutputStream out = exchange.getResponseBody();
				out.write("{\"jobs\": [".getBytes(StandardCharsets.UTF_8));
				out.flush();
				try {
					out.close();
				} catch (IOException _) {
					// Shorter than it said: the server drops the connection, and the feed has given up by then
				}
			}
			default -> send(exchange, 404, "{\"ok\": false, \"error\": \"Document not found\"}");
		}
	}

	private void awaitTheEnd() {
		try {
			this.stalled.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException _) {
			Thread.currentThread().interrupt();
		}
	}

	private static void send(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

}
