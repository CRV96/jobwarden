package com.jobwarden.watchlist.service.impl;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jobwarden.search.EmploymentType;
import com.jobwarden.search.Regions;
import com.jobwarden.search.WorkType;
import com.jobwarden.search.Words;
import com.jobwarden.watchlist.enums.Ats;
import jakarta.annotation.PreDestroy;
import lombok.CustomLog;
import org.jsoup.parser.Parser;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The public feeds of the boards, which anyone can read, with no key: Greenhouse's Job Board API, Lever's Postings
 * API (and its servers for Europe) and Ashby's Job Postings API. Each answers with every job on the board at once, read
 * as it streams in, a job at a time, up to {@link #MAX_BYTES}. Greenhouse's list leaves the descriptions out: those
 * of the jobs that matched are read one by one ({@link #description}).
 * <p>
 * The servers are these four, whatever the link pasted: the board's name is only ever a part of the path.
 */
@Component
@CustomLog
class BoardFeeds {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	/** The biggest boards take a while: Ashby's, with every description, can be over 10 MB. */
	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	/** How long reading a board's answer may take in all: the biggest take a few seconds. */
	static final Duration READ_DEADLINE = Duration.ofMinutes(2);

	private static final String LOCATION = "location";

	/** More than any board's feed: OpenAI's on Ashby, with every description, is about 14 MB. */
	static final long MAX_BYTES = 40L * 1024 * 1024;

	/** How much of a description the matching reads: enough for the requirements, which come after the intro. */
	static final int DESCRIPTION_MAX_LENGTH = 15_000;

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(CONNECT_TIMEOUT)
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private final JsonMapper json = JsonMapper.builder().build();

	private final String userAgent;

	private final Map<Ats, URI> servers = new EnumMap<>(Ats.class);

	private final Duration readDeadline;

	/** Closes the answers that take longer than readDeadline (readWithinDeadline). */
	private final ScheduledExecutorService deadlines = Executors
		.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("board-read-deadline").factory());

	/**
	 * @param version the version the build put in the app's properties, which the requests say they come from
	 */
	@Autowired
	BoardFeeds(@Value("${jobwarden.version:dev}") String version,
			@Value("${jobwarden.watchlist.greenhouse-api:https://boards-api.greenhouse.io}") URI greenhouse,
			@Value("${jobwarden.watchlist.lever-api:https://api.lever.co}") URI lever,
			@Value("${jobwarden.watchlist.lever-eu-api:https://api.eu.lever.co}") URI leverEu,
			@Value("${jobwarden.watchlist.ashby-api:https://api.ashbyhq.com}") URI ashby) {
		this(version, greenhouse, lever, leverEu, ashby, READ_DEADLINE);
	}

	/**
	 * @param readDeadline how long reading an answer may take in all: the tests' is short
	 */
	BoardFeeds(String version, URI greenhouse, URI lever, URI leverEu, URI ashby, Duration readDeadline) {
		this.readDeadline = readDeadline;
		this.userAgent = "JobWarden/" + version + " (+https://github.com/CRV96/jobwarden)";
		this.servers.put(Ats.GREENHOUSE, greenhouse);
		this.servers.put(Ats.LEVER, lever);
		this.servers.put(Ats.LEVER_EU, leverEu);
		this.servers.put(Ats.ASHBY, ashby);
	}

	/**
	 * @return every job on the board, listed or not as its feed lists them; those with no title or link left out
	 * @throws BoardFailure when the board doesn't exist, or can't be read
	 */
	List<Posting> postings(Board board) {
		String name = encoded(board.name());
		URI server = this.servers.get(board.ats());
		List<Posting> postings = new ArrayList<>();
		Consumer<JsonNode> keep = job -> {
			Posting posting = switch (board.ats()) {
				case GREENHOUSE -> greenhouse(job);
				case LEVER, LEVER_EU -> lever(job);
				case ASHBY -> ashby(job);
			};
			if (posting != null) {
				postings.add(posting);
			}
		};
		switch (board.ats()) {
			case GREENHOUSE -> read(URI.create(server + "/v1/boards/" + name + "/jobs"), "jobs", keep);
			case LEVER, LEVER_EU -> read(URI.create(server + "/v0/postings/" + name + "?mode=json"), null, keep);
			case ASHBY -> read(URI.create(server + "/posting-api/job-board/" + name + "?includeCompensation=true"), "jobs",
					keep);
		}
		return postings;
	}

	/**
	 * A job's description, for the boards whose list leaves it out: Greenhouse.
	 *
	 * @return its text; {@code null} when the board has none for it, or it can't be read
	 */
	@Nullable String description(Board board, String postingId) {
		if (board.ats() != Ats.GREENHOUSE) {
			return null;
		}
		URI address = URI.create(this.servers.get(Ats.GREENHOUSE) + "/v1/boards/" + encoded(board.name()) + "/jobs/"
				+ encoded(postingId));
		try {
			JsonNode job = send(address, body -> this.json.readTree(body));
			// The HTML comes escaped: &lt;p&gt;
			return shortened(HtmlText.of(Parser.unescapeEntities(job.path("content").asString(""), false)));
		} catch (BoardFailure | JacksonException failed) {
			log.debug("Couldn't read the description of job {} on {}: {}", postingId, board, failed.getMessage());
			return null;
		}
	}

	/**
	 * @param field the property holding the jobs; {@code null} when the answer is the list itself
	 */
	private void read(URI address, @Nullable String field, Consumer<JsonNode> job) {
		send(address, body -> {
			try (JsonParser parser = this.json.createParser(body)) {
				readAnswer(parser, field, job);
				return null;
			} catch (JacksonIOException failed) {
				throw new BoardFailure(address.getHost() + " couldn't be read: " + failed.getCause().getMessage());
			} catch (JacksonException notJson) {
				throw new BoardFailure("The answer isn't a board's jobs: " + notJson.getOriginalMessage());
			}
		});
	}

	/**
	 * The jobs of the answer: the list it is, or the one under the property.
	 *
	 * @throws BoardFailure when the answer is something else
	 */
	private static void readAnswer(JsonParser parser, @Nullable String field, Consumer<JsonNode> job) {
		JsonToken first = parser.nextToken();
		if (field == null && first == JsonToken.START_ARRAY) {
			readJobs(parser, job);
			return;
		}
		if (field == null || first != JsonToken.START_OBJECT) {
			throw new BoardFailure("The answer isn't a board's jobs");
		}
		while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
			String property = parser.currentName();
			if (parser.nextToken() == JsonToken.START_ARRAY && field.equals(property)) {
				readJobs(parser, job);
			} else {
				parser.skipChildren();
			}
		}
	}

	/** One job at a time, so a board of thousands never sits in memory as a whole tree. */
	private static void readJobs(JsonParser parser, Consumer<JsonNode> job) {
		while (parser.nextToken() == JsonToken.START_OBJECT) {
			job.accept(parser.readValueAsTree());
		}
	}

	private <T> T send(URI address, Reader<T> reader) {
		HttpRequest request = HttpRequest.newBuilder(address)
			.timeout(TIMEOUT)
			.header("Accept", "application/json")
			.header("User-Agent", this.userAgent)
			.GET()
			.build();
		try {
			HttpResponse<InputStream> response = this.http.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = response.body()) {
				int status = response.statusCode();
				if (status == 404) {
					throw new BoardFailure(address.getHost() + " has no such board", true);
				}
				if (status != 200) {
					throw new BoardFailure(address.getHost() + " answered " + status);
				}
				if (response.headers().firstValueAsLong("Content-Length").orElse(0) > MAX_BYTES) {
					throw new BoardFailure("The board is bigger than " + MAX_BYTES / (1024 * 1024) + " MB");
				}
				return readWithinDeadline(body, reader);
			}
		} catch (IOException failed) {
			throw new BoardFailure(address.getHost() + " couldn't be reached: " + failed.getMessage());
		} catch (InterruptedException _) {
			Thread.currentThread().interrupt();
			throw new BoardFailure("Interrupted while reading " + address.getHost());
		} catch (JacksonIOException failed) {
			// Capped's IOException, or the connection's, as the parser passes it on
			throw new BoardFailure(address.getHost() + " couldn't be read: " + failed.getCause().getMessage());
		}
	}

	/**
	 * The request's timeout ends once the headers are in: a board that then stops sending would hold the reading
	 * forever, and every reading after it. So the body is closed at the deadline, which ends a read waiting on it with
	 * an IOException.
	 */
	private <T> T readWithinDeadline(InputStream body, Reader<T> reader) throws IOException {
		ScheduledFuture<?> deadline = this.deadlines.schedule(() -> {
			try {
				body.close();
			} catch (IOException _) {
				// Closing it is all that's needed: the read stops either way
			}
		}, this.readDeadline.toMillis(), TimeUnit.MILLISECONDS);
		try {
			return reader.read(new Capped(body));
		} finally {
			deadline.cancel(false);
		}
	}

	@PreDestroy
	void stop() {
		this.deadlines.shutdownNow();
	}

	private static @Nullable Posting greenhouse(JsonNode job) {
		String title = text(job, "title");
		String link = text(job, "absolute_url");
		if (title == null || link == null) {
			return null;
		}
		Instant published = instant(text(job, "first_published"));
		return Posting.builder()
			.id(job.path("id").asString(""))
			.title(title)
			.location(text(job.path(LOCATION), "name"))
			.link(link)
			.publishedAt((published != null) ? published : instant(text(job, "updated_at")))
			.company(text(job, "company_name"))
			.build();
	}

	private static @Nullable Posting lever(JsonNode job) {
		String title = text(job, "text");
		String link = text(job, "hostedUrl");
		if (title == null || link == null) {
			return null;
		}
		JsonNode categories = job.path("categories");
		List<String> places = strings(categories.path("allLocations"));
		String location = places.isEmpty() ? text(categories, LOCATION) : String.join("; ", places);
		String country = text(job, "country");
		Employment employment = Employment.of(text(categories, "commitment"));
		long createdAt = job.path("createdAt").asLong(0);
		return Posting.builder()
			.id(job.path("id").asString(""))
			.title(title)
			.location(location)
			.countries((country != null && country.length() == 2) ? Set.of(country.toUpperCase(Locale.ROOT)) : Set.of())
			.workType(workType(text(job, "workplaceType")))
			.employmentStated(employment.stated())
			.employmentType(employment.type())
			.link(link)
			.publishedAt((createdAt > 0) ? Instant.ofEpochMilli(createdAt) : null)
			.salary(leverSalary(job.path("salaryRange")))
			.description(leverDescription(job))
			.build();
	}

	private static @Nullable Posting ashby(JsonNode job) {
		String title = text(job, "title");
		String link = text(job, "jobUrl");
		if (title == null || link == null || !job.path("isListed").asBoolean(true)) {
			return null;
		}
		List<JsonNode> secondary = job.path("secondaryLocations").values().stream().toList();
		String location = Stream.concat(Stream.of(text(job, LOCATION)), secondary.stream().map(place -> text(place, LOCATION)))
			.filter(place -> place != null)
			.distinct()
			.collect(Collectors.joining("; "));
		Set<String> countries = new LinkedHashSet<>();
		Stream.concat(Stream.of(job), secondary.stream())
			.map(place -> text(place.path("address").path("postalAddress"), "addressCountry"))
			.filter(country -> country != null)
			.forEach(country -> countries.addAll(Regions.countriesOf(country)));
		Employment employment = Employment.of(text(job, "employmentType"));
		JsonNode compensation = job.path("compensation");
		String salary = text(compensation, "scrapeableCompensationSalarySummary");
		return Posting.builder()
			.id(job.path("id").asString(""))
			.title(title)
			.location(location.isEmpty() ? null : location)
			.countries(countries)
			.workType(job.path("isRemote").asBoolean(false) ? WorkType.REMOTE : workType(text(job, "workplaceType")))
			.employmentStated(employment.stated())
			.employmentType(employment.type())
			.link(link)
			.publishedAt(instant(text(job, "publishedAt")))
			.salary((salary != null) ? salary : text(compensation, "compensationTierSummary"))
			.description(shortened(text(job, "descriptionPlain")))
			.build();
	}

	/** Its plain intro, its lists (what you'll do, what you need...) and what it adds after. */
	private static @Nullable String leverDescription(JsonNode job) {
		StringBuilder text = new StringBuilder();
		String intro = text(job, "descriptionPlain");
		text.append((intro != null) ? intro : "");
		for (JsonNode list : job.path("lists").values()) {
			String heading = text(list, "text");
			String items = HtmlText.of(text(list, "content"));
			if (items != null) {
				text.append("\n\n").append((heading != null) ? heading + "\n" : "").append(items);
			}
		}
		String after = text(job, "additionalPlain");
		text.append((after != null) ? "\n\n" + after : "");
		return shortened(text.toString().strip());
	}

	/** {"currency": "EUR", "interval": "per-year-salary", "min": 60000, "max": 80000}: EUR 60,000–80,000 per year. */
	private static @Nullable String leverSalary(JsonNode range) {
		long min = range.path("min").asLong(0);
		long max = range.path("max").asLong(0);
		if (min <= 0 && max <= 0) {
			return null;
		}
		String currency = range.path("currency").asString("");
		String interval = range.path("interval").asString("").replace("-salary", "").replace('-', ' ');
		String amount = (min > 0 && max > 0 && min != max) ? "%,d–%,d".formatted(min, max)
				: "%,d".formatted(Math.max(min, max));
		return (currency + " " + amount + " " + interval).strip();
	}

	/** Lever's remote, hybrid, onsite (on-site); Ashby's Remote, Hybrid, OnSite. */
	private static @Nullable WorkType workType(@Nullable String type) {
		return switch (Words.of(type).replace(" ", "")) {
			case "remote" -> WorkType.REMOTE;
			case "hybrid" -> WorkType.HYBRID;
			case "onsite" -> WorkType.ONSITE;
			default -> null;
		};
	}

	private static @Nullable String shortened(@Nullable String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		return (text.length() <= DESCRIPTION_MAX_LENGTH) ? text : text.substring(0, DESCRIPTION_MAX_LENGTH - 1) + "…";
	}

	private static @Nullable String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return (value.isString() && !value.asString().isBlank()) ? value.asString().strip() : null;
	}

	private static List<String> strings(JsonNode array) {
		return array.values().stream().filter(JsonNode::isString).map(JsonNode::asString).filter(s -> !s.isBlank()).toList();
	}

	private static @Nullable Instant instant(@Nullable String text) {
		if (text == null) {
			return null;
		}
		try {
			return OffsetDateTime.parse(text).toInstant();
		} catch (DateTimeParseException _) {
			// Without its offset: as UTC
			try {
				return Instant.parse(text);
			} catch (DateTimeParseException _) {
				return null;
			}
		}
	}

	private static String encoded(String part) {
		return URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20");
	}

	@FunctionalInterface
	private interface Reader<T> {

		T read(InputStream body) throws IOException;

	}

	/**
	 * The kind of contract a feed says, in its words: Lever's commitment (Full-time, Permanent, Contract, Intern...),
	 * Ashby's employment type (FullTime, PartTime, Intern, Contract, Temporary).
	 *
	 * @param stated whether it says one, even one there's no {@link EmploymentType} for, such as part-time
	 */
	record Employment(boolean stated, @Nullable EmploymentType type) {

		private static final Set<String> INTERNSHIP_WORDS = Set.of("intern", "interns", "internship", "trainee",
				"apprentice", "apprenticeship", "praktikum", "werkstudent");

		static Employment of(@Nullable String said) {
			String words = Words.of(said);
			if (words.isBlank()) {
				return new Employment(false, null);
			}
			Set<String> each = Arrays.stream(words.strip().split(" ")).collect(Collectors.toSet());
			String joined = words.replace(" ", "");
			if (each.stream().anyMatch(INTERNSHIP_WORDS::contains)) {
				return new Employment(true, EmploymentType.INTERNSHIP);
			}
			if (joined.contains("parttime")) {
				return new Employment(true, null);
			}
			if (each.contains("freelance") || each.contains("freelancer")) {
				return new Employment(true, EmploymentType.FREELANCE);
			}
			if (each.contains("contract") || each.contains("contractor") || each.contains("temporary")
					|| joined.contains("fixedterm") || joined.contains("shortterm")) {
				return new Employment(true, EmploymentType.CONTRACT);
			}
			if (joined.contains("fulltime") || each.contains("permanent") || each.contains("regular")) {
				return new Employment(true, EmploymentType.FULL_TIME);
			}
			// Anything else, a team's name say, isn't about the contract
			return new Employment(false, null);
		}

	}

	/**
	 * Stops reading a board's answer past {@link #MAX_BYTES}.
	 */
	private static final class Capped extends FilterInputStream {

		private long left = MAX_BYTES;

		Capped(InputStream in) {
			super(in);
		}

		@Override
		public int read() throws IOException {
			int read = super.read();
			if (read >= 0 && --this.left < 0) {
				throw tooBig();
			}
			return read;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int read = super.read(buffer, offset, length);
			if (read > 0) {
				this.left -= read;
				if (this.left < 0) {
					throw tooBig();
				}
			}
			return read;
		}

		private static IOException tooBig() {
			return new IOException("the board is bigger than " + MAX_BYTES / (1024 * 1024) + " MB");
		}

	}

}
