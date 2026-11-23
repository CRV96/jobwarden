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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.xml.stream.XMLStreamException;

import com.jobwarden.common.net.HttpClients;
import com.jobwarden.watchlist.enums.Ats;
import jakarta.annotation.PreDestroy;
import lombok.CustomLog;
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
 * The public feeds of the boards, which anyone can read, with no key, the ones the systems make for companies' careers
 * sites: Greenhouse's Job Board API, Lever's Postings API (and its servers for Europe), Ashby's Job Postings API,
 * SmartRecruiters' Posting API, Workable's widget, Recruitee's Careers Site API and Personio's XML feed. Each answers
 * with every job on the board at once, but SmartRecruiters, a page at a time, and Personio, a language at a time; each
 * answer is read as it streams in, a job at a time, up to {@link #MAX_BYTES}. Greenhouse's and SmartRecruiters' lists
 * leave the descriptions out: those of the jobs that matched are read one by one ({@link #description}). What differs
 * from one ATS to the next, its addresses and its JSON (or XML), is its {@link BoardFeed}; this class reads them all
 * the same way.
 * <p>
 * The servers are these, whatever the link pasted: the board's name is only ever a part of the path, or, for
 * Recruitee's and Personio's boards, the one label before their domain.
 */
@Component
@CustomLog
class BoardFeeds {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	/** The biggest boards take a while: Ashby's, with every description, can be over 10 MB. */
	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	/** How long reading a board's answer may take in all: the biggest take a few seconds. */
	static final Duration READ_DEADLINE = Duration.ofMinutes(2);

	/** More than any board's feed: OpenAI's on Ashby, with every description, is about 14 MB. */
	static final long MAX_BYTES = 40L * 1024 * 1024;

	/**
	 * The most pages of a board read at once: 10,000 jobs on SmartRecruiters, twice the biggest board there (Bosch's,
	 * about 5,200). A board past them is read up to there.
	 */
	static final int MAX_PAGES = 100;

	private static final String JSON = "application/json";

	private static final String XML = "application/xml, text/xml";

	private static final BoardFeed GREENHOUSE = new GreenhouseFeed();

	private static final BoardFeed LEVER = new LeverFeed();

	private static final BoardFeed ASHBY = new AshbyFeed();

	private static final BoardFeed SMARTRECRUITERS = new SmartRecruitersFeed();

	private static final BoardFeed WORKABLE = new WorkableFeed();

	private static final BoardFeed RECRUITEE = new RecruiteeFeed();

	private static final BoardFeed PERSONIO = new PersonioFeed();

	private final HttpClient http = HttpClients.followingRedirects(CONNECT_TIMEOUT);

	private final JsonMapper json = JsonMapper.builder().build();

	private final String userAgent;

	private final WatchlistProperties properties;

	private final Duration readDeadline;

	/** Closes the answers that take longer than readDeadline (readWithinDeadline). */
	private final ScheduledExecutorService deadlines = Executors
		.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("board-read-deadline").factory());

	/**
	 * @param version the version the build put in the app's properties, which the requests say they come from
	 */
	@Autowired
	BoardFeeds(@Value("${jobwarden.version:dev}") String version, WatchlistProperties properties) {
		this(version, properties, READ_DEADLINE);
	}

	/**
	 * @param readDeadline how long reading an answer may take in all: the tests' is short
	 */
	BoardFeeds(String version, WatchlistProperties properties, Duration readDeadline) {
		this.readDeadline = readDeadline;
		this.userAgent = HttpClients.userAgent(version);
		this.properties = properties;
	}

	/**
	 * @return every job on the board, listed or not as its feed lists them, once each, however often its feed lists it;
	 * those with no title or link left out
	 * @throws BoardFailure when the board doesn't exist, or can't be read
	 */
	List<Posting> postings(Board board) {
		BoardFeed feed = feed(board.ats());
		URI server = server(board);
		String name = encoded(board.name());
		Map<String, Posting> byLink = new LinkedHashMap<>();
		int read = 0;
		URI page = feed.jobs(server, name);
		for (int pages = 1; page != null; pages++) {
			if (pages > MAX_PAGES) {
				log.info("Read the first {} pages of {}, {} jobs: the rest are left out", MAX_PAGES, board, read);
				break;
			}
			AtomicInteger onPage = new AtomicInteger();
			read(page, feed, job -> {
				onPage.incrementAndGet();
				Posting posting = feed.posting(job, board);
				if (posting != null) {
					byLink.merge(posting.link(), posting, Posting::mergedWith);
				}
			});
			read += onPage.get();
			page = feed.next(server, name, pages, read, onPage.get());
		}
		return List.copyOf(byLink.values());
	}

	/**
	 * A job's description, for the boards whose list leaves it out (Greenhouse's, SmartRecruiters').
	 *
	 * @return its text; {@code null} when the list has it, the board has none for it, or it can't be read
	 */
	@Nullable String description(Board board, String postingId) {
		BoardFeed feed = feed(board.ats());
		try {
			URI address = feed.description(server(board), encoded(board.name()), encoded(postingId));
			return (address != null) ? feed.descriptionOf(send(address, JSON, this.json::readTree)) : null;
		} catch (BoardFailure | JacksonException failed) {
			log.debug("Couldn't read the description of job {} on {}: {}", postingId, board, failed.getMessage());
			return null;
		}
	}

	/**
	 * The jobs of one answer, JSON or XML as the feed says.
	 */
	private void read(URI address, BoardFeed feed, Consumer<JsonNode> job) {
		String field = feed.jobsField();
		if (feed.xml()) {
			String element = Objects.requireNonNull(field, "An XML feed names its jobs' element");
			send(address, XML, body -> {
				readXml(address, body, element, job);
				return null;
			});
			return;
		}
		send(address, JSON, body -> {
			try (JsonParser parser = this.json.createParser(body)) {
				readAnswer(parser, field, job);
				return null;
			} catch (JacksonIOException failed) {
				throw new BoardFailure(address.getHost() + " couldn't be read: " + cause(failed));
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

	/**
	 * @throws BoardFailure when the answer isn't XML, or couldn't be read
	 */
	private static void readXml(URI address, InputStream body, String element, Consumer<JsonNode> job) {
		try {
			FeedXml.readJobs(body, element, job);
		} catch (XMLStreamException failed) {
			IOException reading = ioCause(failed);
			if (reading != null) {
				throw new BoardFailure(address.getHost() + " couldn't be read: " + reading.getMessage());
			}
			throw new BoardFailure("The answer isn't a board's jobs: " + failed.getMessage());
		}
	}

	/** The parser passes on the reading's own failure, the cap's or the connection's, as its cause. */
	private static @Nullable IOException ioCause(XMLStreamException failed) {
		@Nullable Throwable cause = failed.getNestedException();
		if (cause == null) {
			cause = failed.getCause();
		}
		while (cause != null && !(cause instanceof IOException)) {
			cause = cause.getCause();
		}
		return (cause instanceof IOException reading) ? reading : null;
	}

	/**
	 * @param accept the kind of answer, JSON or XML
	 */
	private <T extends @Nullable Object> T send(URI address, String accept, Reader<T> reader) {
		HttpRequest request = HttpRequest.newBuilder(address)
			.timeout(TIMEOUT)
			.header("Accept", accept)
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
			throw new BoardFailure(address.getHost() + " couldn't be read: " + cause(failed));
		}
	}

	/**
	 * The request's timeout ends once the headers are in: a board that then stops sending would hold the reading
	 * forever, and every reading after it. So the body is closed at the deadline, which ends a read waiting on it with
	 * an IOException.
	 */
	private <T extends @Nullable Object> T readWithinDeadline(InputStream body, Reader<T> reader) throws IOException {
		ScheduledFuture<?> deadline = this.deadlines.schedule(() -> {
			try {
				body.close();
			} catch (IOException _) {
				// Closing it is all that's needed: the read stops either way
			}
		}, this.readDeadline.toMillis(), TimeUnit.MILLISECONDS);
		try (InputStream capped = new Capped(body)) {
			return reader.read(capped);
		} finally {
			deadline.cancel(false);
		}
	}

	/** What the parser passed on: the reading's own failure, as it said it. */
	private static @Nullable String cause(JacksonIOException failed) {
		Throwable cause = failed.getCause();
		return (cause != null) ? cause.getMessage() : failed.getOriginalMessage();
	}

	@PreDestroy
	void stop() {
		this.deadlines.shutdownNow();
	}

	/** Each ATS's feed: a new {@link Ats} doesn't compile until it has one. */
	static BoardFeed feed(Ats ats) {
		return switch (ats) {
			case GREENHOUSE -> GREENHOUSE;
			case LEVER, LEVER_EU -> LEVER;
			case ASHBY -> ASHBY;
			case SMARTRECRUITERS -> SMARTRECRUITERS;
			case WORKABLE -> WORKABLE;
			case RECRUITEE -> RECRUITEE;
			case PERSONIO -> PERSONIO;
		};
	}

	/**
	 * @throws BoardFailure for a board that is a host of its own whose name couldn't be one
	 */
	private URI server(Board board) {
		return switch (board.ats()) {
			case GREENHOUSE -> this.properties.greenhouseApi();
			case LEVER -> this.properties.leverApi();
			case LEVER_EU -> this.properties.leverEuApi();
			case ASHBY -> this.properties.ashbyApi();
			case SMARTRECRUITERS -> this.properties.smartRecruitersApi();
			case WORKABLE -> this.properties.workableApi();
			case RECRUITEE -> ownHost(this.properties.recruiteeApi(), board);
			case PERSONIO -> ownHost(this.properties.personioApi(), board);
		};
	}

	/**
	 * The address of a board that is a host of its own: its name is one label of a domain name, as {@link Board#of}
	 * reads them. Any other, from a row changed by hand say, could make it another host: there's no such board.
	 *
	 * @param template the address, {board} where the name goes
	 */
	private static URI ownHost(String template, Board board) {
		if (!Board.isHostLabel(board.name())) {
			throw new BoardFailure("No board can be named " + board.name(), true);
		}
		return URI.create(template.replace(WatchlistProperties.BOARD, board.name()));
	}

	/**
	 * @return the part, encoded for an address: a space is %20
	 */
	static String encoded(String part) {
		return URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20");
	}

	@FunctionalInterface
	private interface Reader<T extends @Nullable Object> {

		T read(InputStream body) throws IOException;

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
