package com.jobwarden.watchlist.service.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
 * Against a server here that answers as the systems' feeds do, with a job or two each: Recruitee's and Personio's
 * boards, each a host of its own there, are under a path of their own here.
 */
class BoardFeedsTests {

	private HttpServer boards;

	private BoardFeeds feeds;

	/** Let go once the test is over: the board that sends a little, then stops. */
	private final CountDownLatch stalled = new CountDownLatch(1);

	/** The User-Agent each path was asked with. */
	private final Map<String, String> asked = new ConcurrentHashMap<>();

	/** The answers each path was asked for. */
	private final Map<String, String> accepted = new ConcurrentHashMap<>();

	/** How often each path was asked. */
	private final Map<String, AtomicInteger> times = new ConcurrentHashMap<>();

	@BeforeEach
	void startBoards() throws IOException {
		this.boards = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		this.boards.createContext("/", this::answer);
		// A thread for each answer, so the one that stalls doesn't hold up the others
		this.boards.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		this.boards.start();
		URI here = URI.create("http://127.0.0.1:" + this.boards.getAddress().getPort());
		this.feeds = new BoardFeeds("1.2.3",
				new WatchlistProperties(Duration.ofHours(6), here, here, URI.create(here + "/eu"), here, here, here,
						here + "/recruitee/{board}", here + "/personio/{board}"),
				Duration.ofSeconds(1));
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
			// isRemote is true for a hybrid job too: the workplace type says which
			assertThat(posting.workType()).isEqualTo(WorkType.HYBRID);
			assertThat(posting.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
			assertThat(posting.salary()).isEqualTo("$160K - $275K");
			assertThat(posting.description()).isEqualTo("Build the platform.");
		});
	}

	@Test
	void readsASmartRecruitersBoardAPageAtATimeAndAJobsDescription() {
		Board board = new Board(Ats.SMARTRECRUITERS, "acme");

		List<Posting> postings = this.feeds.postings(board);

		// A full page, then one that isn't: its internal posting left out
		assertThat(postings).hasSize(SmartRecruitersFeed.PAGE_SIZE + 1);
		assertThat(postings.getFirst()).satisfies(posting -> {
			assertThat(posting.id()).isEqualTo("0");
			assertThat(posting.title()).isEqualTo("Java Developer 0");
			assertThat(posting.location()).isEqualTo("Warsaw, Masovia, Poland");
			assertThat(posting.countries()).containsExactly("PL");
			assertThat(posting.workType()).isEqualTo(WorkType.HYBRID);
			assertThat(posting.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
			assertThat(posting.link()).isEqualTo("https://jobs.smartrecruiters.com/Acme/0");
			assertThat(posting.publishedAt()).isEqualTo(Instant.parse("2026-10-28T15:14:43.941Z"));
			assertThat(posting.company()).isEqualTo("Acme Inc");
			assertThat(posting.description()).isNull();
		});
		assertThat(postings.getLast()).satisfies(posting -> {
			assertThat(posting.workType()).isEqualTo(WorkType.REMOTE);
			assertThat(posting.employmentType()).isEqualTo(EmploymentType.INTERNSHIP);
		});
		assertThat(this.feeds.description(board, "100"))
			.isEqualTo("Job Description\nBuild games.\n\nQualifications\n• Java\n• Unity");
		assertThat(this.feeds.description(board, "999")).isNull();
		assertThat(this.times.get("/v1/companies/acme/postings")).hasValue(2);
	}

	@Test
	void readsNoMorePagesThanAnyBoardHas() {
		// Each page full, of the same jobs
		assertThat(this.feeds.postings(new Board(Ats.SMARTRECRUITERS, "endless"))).hasSize(SmartRecruitersFeed.PAGE_SIZE);
		assertThat(this.times.get("/v1/companies/endless/postings")).hasValue(BoardFeeds.MAX_PAGES);
	}

	@Test
	void readsAWorkableBoardWithAJobInTwoPlacesAsOne() {
		List<Posting> postings = this.feeds.postings(new Board(Ats.WORKABLE, "acme"));

		assertThat(postings).hasSize(2);
		assertThat(postings.getFirst()).satisfies(posting -> {
			assertThat(posting.id()).isEqualTo("AB12");
			assertThat(posting.title()).isEqualTo("Backend Developer");
			assertThat(posting.location()).isEqualTo("Athens, Greece; London, United Kingdom");
			assertThat(posting.countries()).containsExactlyInAnyOrder("GR", "GB");
			assertThat(posting.workType()).isNull();
			assertThat(posting.employmentType()).isEqualTo(EmploymentType.CONTRACT);
			assertThat(posting.link()).isEqualTo("https://apply.workable.com/j/AB12");
			assertThat(posting.publishedAt()).isEqualTo(Instant.parse("2026-10-09T00:00:00Z"));
			assertThat(posting.description()).isEqualTo("We build learning.");
		});
		// Its place is hidden on the board: its country still counts
		assertThat(postings.getLast()).satisfies(posting -> {
			assertThat(posting.location()).isNull();
			assertThat(posting.countries()).containsExactly("FR");
			assertThat(posting.workType()).isEqualTo(WorkType.REMOTE);
			assertThat(posting.link()).isEqualTo("https://apply.workable.com/j/CD34");
			assertThat(posting.publishedAt()).isEqualTo(Instant.parse("2026-05-29T00:00:00Z"));
			assertThat(posting.description()).isNull();
		});
		assertThat(this.asked).containsKey("/api/v1/widget/accounts/acme");
	}

	@Test
	void readsARecruiteeBoardOnItsOwnHostWithoutItsDrafts() {
		assertThat(this.feeds.postings(new Board(Ats.RECRUITEE, "acme"))).singleElement().satisfies(posting -> {
			assertThat(posting.id()).isEqualTo("2793226");
			assertThat(posting.title()).isEqualTo("Haskell Engineer");
			assertThat(posting.location()).isEqualTo("Utrecht, Netherlands; Sofia, Bulgaria");
			assertThat(posting.countries()).containsExactlyInAnyOrder("NL", "BG");
			assertThat(posting.workType()).isEqualTo(WorkType.HYBRID);
			// Full-time, for a fixed term
			assertThat(posting.employmentType()).isEqualTo(EmploymentType.CONTRACT);
			assertThat(posting.link()).isEqualTo("https://jobs.acme.com/o/haskell-engineer");
			assertThat(posting.publishedAt()).isEqualTo(Instant.parse("2026-10-22T12:11:23Z"));
			assertThat(posting.salary()).isEqualTo("EUR 4,000–5,700 per month");
			assertThat(posting.description()).isEqualTo("We automate eCommerce.\n\n• Haskell");
			assertThat(posting.company()).isEqualTo("Acme");
		});
		assertThat(this.asked).containsKey("/recruitee/acme/api/offers/");
	}

	@Test
	void readsAPersonioBoardInEnglishThenInTheCompanysLanguage() {
		List<Posting> postings = this.feeds.postings(new Board(Ats.PERSONIO, "acme"));

		assertThat(postings).hasSize(2);
		assertThat(postings.getFirst()).satisfies(posting -> {
			assertThat(posting.id()).isEqualTo("2502124");
			assertThat(posting.title()).isEqualTo("Senior Java Engineer (m/f/d)");
			assertThat(posting.location()).isEqualTo("Munich; Berlin; Remote");
			// Full- or part-time, as the candidate likes: permanent
			assertThat(posting.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
			assertThat(posting.link()).isEqualTo("https://acme.jobs.personio.de/job/2502124");
			assertThat(posting.publishedAt()).isEqualTo(Instant.parse("2026-11-11T17:43:42Z"));
			assertThat(posting.salary()).isEqualTo("EUR 80,000–100,000 per year");
			// The English one, though the company wrote one in German too
			assertThat(posting.description()).isEqualTo("What you'll do\nBuild payments.\n\nWhat you need\n• Java");
		});
		// Written in German only: its description is the German one
		assertThat(postings.getLast()).satisfies(posting -> {
			assertThat(posting.employmentType()).isEqualTo(EmploymentType.INTERNSHIP);
			assertThat(posting.description()).isEqualTo("Was dich erwartet\nBuchhaltung.");
			assertThat(posting.salary()).isNull();
		});
		assertThat(this.times.get("/personio/acme/xml")).hasValue(2);
		assertThat(this.accepted).containsEntry("/personio/acme/xml", "application/xml, text/xml");
		// An empty board is read once
		assertThat(this.feeds.postings(new Board(Ats.PERSONIO, "empty"))).isEmpty();
		assertThat(this.times.get("/personio/empty/xml")).hasValue(1);
	}

	@Test
	void readsNoBoardWhoseNameWouldMakeAnotherHost() {
		Board elsewhere = new Board(Ats.RECRUITEE, "acme.example.com");

		assertThatExceptionOfType(BoardFailure.class).isThrownBy(() -> this.feeds.postings(elsewhere))
			.satisfies(failed -> assertThat(failed.notFound()).isTrue());
		assertThat(this.feeds.description(elsewhere, "1")).isNull();
		assertThat(this.feeds.description(new Board(Ats.RECRUITEE, "acme"), "1")).isNull();
	}

	@Test
	void saysWhenAPersonioBoardIsntXmlOrStopsSendingIt() {
		Board maintenance = new Board(Ats.PERSONIO, "maintenance");
		Board stopping = new Board(Ats.PERSONIO, "stalled");
		long start = System.nanoTime();

		assertThatExceptionOfType(BoardFailure.class).isThrownBy(() -> this.feeds.postings(maintenance))
			.withMessageContaining("isn't a board's jobs");
		assertThatExceptionOfType(BoardFailure.class).isThrownBy(() -> this.feeds.postings(stopping))
			.withMessageContaining("couldn't be read");
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
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
		String query = String.valueOf(exchange.getRequestURI().getQuery());
		this.asked.put(path, String.valueOf(exchange.getRequestHeaders().getFirst("User-Agent")));
		this.accepted.put(path, String.valueOf(exchange.getRequestHeaders().getFirst("Accept")));
		this.times.computeIfAbsent(path, _ -> new AtomicInteger()).incrementAndGet();
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
			case "/v1/companies/acme/postings" -> send(exchange, 200, query.contains("offset=0")
					? smartRecruitersPage(0, SmartRecruitersFeed.PAGE_SIZE, "") : smartRecruitersPage(100, 1, """
							, {"id": "101", "name": "Internal", "visibility": "INTERNAL", "company": {"identifier": "Acme"}}"""));
			case "/v1/companies/endless/postings" ->
				send(exchange, 200, smartRecruitersPage(0, SmartRecruitersFeed.PAGE_SIZE, ""));
			case "/v1/companies/acme/postings/100" -> send(exchange, 200, """
					{"id": "100", "jobAd": {"sections": {
					  "companyDescription": {"title": "Company Description", "text": "<p>We are Acme.</p>"},
					  "jobDescription": {"title": "Job Description", "text": "<p>Build games.</p>"},
					  "qualifications": {"title": "Qualifications", "text": "<ul><li><p>Java</p></li><li><p>Unity</p></li></ul>"},
					  "additionalInformation": {"title": "Additional Information", "text": ""}}}}""");
			case "/api/v1/widget/accounts/acme" -> send(exchange, 200, """
					{"name": "Acme", "description": "<p>We are Acme.</p>", "jobs": [
					  {"title": "Backend Developer", "shortcode": "AB12", "employment_type": "Contract", "telecommuting": false,
					   "url": "https://apply.workable.com/j/AB12", "published_on": "2026-10-09", "created_at": "2026-10-01",
					   "locations": [{"country": "Greece", "countryCode": "GR", "city": "Athens", "region": "Attica", "hidden": false}],
					   "description": "<p>We build learning.</p>"},
					  {"title": "Backend Developer", "shortcode": "AB12", "employment_type": "Contract", "telecommuting": false,
					   "url": "https://apply.workable.com/j/AB12", "published_on": "2026-10-09",
					   "locations": [{"country": "United Kingdom", "countryCode": "GB", "city": "London", "hidden": false}],
					   "description": "<p>We build learning.</p>"},
					  {"title": "ML Engineer - EMEA Remote", "shortcode": "CD34", "employment_type": "Full-time",
					   "telecommuting": true, "shortlink": "https://apply.workable.com/j/CD34", "created_at": "2026-05-29",
					   "locations": [{"country": "France", "countryCode": "FR", "city": "Paris", "hidden": true}], "description": ""},
					  {"title": "No link", "shortcode": "EF56"}]}""");
			case "/recruitee/acme/api/offers/" -> send(exchange, 200, """
					{"offers": [
					  {"id": 2793226, "title": "Haskell Engineer", "status": "published",
					   "careers_url": "https://jobs.acme.com/o/haskell-engineer", "location": "Utrecht, Utrecht, Netherlands",
					   "country_code": "NL", "locations": [{"city": "Utrecht", "country": "Netherlands", "country_code": "NL"},
					     {"city": "Sofia", "country": "Bulgaria", "country_code": "BG"}],
					   "remote": false, "hybrid": true, "on_site": false, "employment_type_code": "fulltime_fixed_term",
					   "published_at": "2026-10-22 12:11:23 UTC", "created_at": "2026-10-22 06:17:49 UTC",
					   "salary": {"min": "4000", "max": "5700", "period": "month", "currency": "EUR"},
					   "description": "<p>We automate eCommerce.</p>", "requirements": "<ul><li><p>Haskell</p></li></ul>",
					   "company_name": "Acme"},
					  {"id": 2, "title": "Draft", "status": "draft", "careers_url": "https://jobs.acme.com/o/draft"}]}""");
			case "/personio/acme/xml" -> sendXml(exchange, query.contains("language=en") ? """
					<?xml version="1.0" encoding="UTF-8"?>
					<workzag-jobs>
					<position>
					    <id>2502124</id>
					    <subcompany>Acme GmbH</subcompany>
					    <office>Munich</office>
					    <additionalOffices><office>Berlin</office><office>Remote</office></additionalOffices>
					    <name>Senior Java Engineer (m/f/d)</name>
					    <jobDescriptions>
					        <jobDescription><name>What you&#039;ll do</name><value><![CDATA[<p>Build payments.</p>]]></value></jobDescription>
					        <jobDescription><name>What you need</name><value><![CDATA[<ul><li><p>Java</p></li></ul>]]></value></jobDescription>
					    </jobDescriptions>
					    <employmentType>permanent</employmentType>
					    <schedule>full-or-part-time</schedule>
					    <createdAt>2026-11-11T17:43:42+00:00</createdAt>
					    <salaryInformation><min>80000.00</min><max>100000.00</max><currencyCode>EUR</currencyCode><type>yearly</type></salaryInformation>
					</position>
					<position>
					    <id>2840027</id>
					    <office>Munich</office>
					    <name>Werkstudent:in Accounting (m/w/d)</name>
					    <jobDescriptions/>
					    <employmentType>working_student</employmentType>
					    <schedule>part-time</schedule>
					    <createdAt>2026-10-13T09:59:49+00:00</createdAt>
					</position>
					</workzag-jobs>""" : """
					<?xml version="1.0" encoding="UTF-8"?>
					<workzag-jobs>
					<position>
					    <id>2502124</id>
					    <office>Munich</office>
					    <name>Senior Java Engineer (m/w/d)</name>
					    <jobDescriptions>
					        <jobDescription><name>Was du tust</name><value><![CDATA[<p>Zahlungen bauen.</p>]]></value></jobDescription>
					    </jobDescriptions>
					    <employmentType>permanent</employmentType>
					</position>
					<position>
					    <id>2840027</id>
					    <office>Munich</office>
					    <name>Werkstudent:in Accounting (m/w/d)</name>
					    <jobDescriptions>
					        <jobDescription><name>Was dich erwartet</name><value><![CDATA[<p>Buchhaltung.</p>]]></value></jobDescription>
					    </jobDescriptions>
					    <employmentType>working_student</employmentType>
					    <schedule>part-time</schedule>
					</position>
					<position><id>1</id></position>
					</workzag-jobs>""");
			case "/personio/empty/xml" -> sendXml(exchange, """
					<?xml version="1.0" encoding="UTF-8"?>
					<workzag-jobs>
					</workzag-jobs>""");
			case "/personio/maintenance/xml" -> sendXml(exchange, "<!DOCTYPE html><html><body>Maintenance<br></body></html>");
			case "/personio/stalled/xml" -> {
				exchange.sendResponseHeaders(200, 0);
				OutputStream out = exchange.getResponseBody();
				out.write("<?xml version=\"1.0\"?><workzag-jobs><position><id>".getBytes(StandardCharsets.UTF_8));
				out.flush();
				awaitTheEnd();
				exchange.close();
			}
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

	/**
	 * @param more what the page has after its jobs, e.g. ", {...}"
	 */
	private static String smartRecruitersPage(int from, int count, String more) {
		return IntStream.range(from, from + count).mapToObj(id -> """
				{"id": "%d", "name": "Java Developer %d", "uuid": "u%d", "company": {"identifier": "Acme", "name": "Acme Inc"},
				 "releasedDate": "2026-10-28T15:14:43.941Z", "visibility": "PUBLIC",
				 "location": {"city": "Warsaw", "region": "Masovia", "country": "pl", "remote": %b, "hybrid": %b,
				   "fullLocation": "Warsaw, Masovia, Poland"},
				 "typeOfEmployment": {"id": "%s", "label": "%s"}}""".formatted(id, id, id, id >= 100, id < 100,
				id >= 100 ? "intern" : "permanent", id >= 100 ? "Intern" : "Full-time"))
			.collect(Collectors.joining(",", "{\"offset\": " + from + ", \"limit\": 100, \"totalFound\": 101, \"content\": [",
					more + "]}"));
	}

	private static void sendXml(HttpExchange exchange, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "text/xml");
		exchange.sendResponseHeaders(200, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
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
