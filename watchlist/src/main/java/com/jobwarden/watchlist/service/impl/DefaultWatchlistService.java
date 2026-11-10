package com.jobwarden.watchlist.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.dto.AddedFoundJobs;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.dto.NewFoundJob;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.watchlist.dto.BoardLink;
import com.jobwarden.watchlist.dto.CompanySuggestion;
import com.jobwarden.watchlist.dto.WatchResult;
import com.jobwarden.watchlist.dto.WatchedCompany;
import com.jobwarden.watchlist.dto.WatchlistCheck;
import com.jobwarden.watchlist.entity.WatchedCompanyEntity;
import com.jobwarden.watchlist.entity.WatchedCompanyFindEntity;
import com.jobwarden.watchlist.exception.WatchedCompanyNotFoundException;
import com.jobwarden.watchlist.exception.WatchlistErrorCode;
import com.jobwarden.watchlist.repository.WatchedCompanyFindRepository;
import com.jobwarden.watchlist.repository.WatchedCompanyRepository;
import com.jobwarden.watchlist.service.WatchlistService;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reading a board talks to its system, which may take a few seconds: that happens outside any transaction, a few
 * boards at a time, and each board once for every profile watching it. Then each profile's saved searches pick the
 * postings that fit them ({@link PostingMatcher}); those whose links the profile has, found or as a job, are left out,
 * as they are for Claude; and the newest of the others go to the AI Assistant page. One reading at a time: the
 * schedule's, a profile's Check now, or a company just added.
 */
@Service
@CustomLog
class DefaultWatchlistService implements WatchlistService {

	/** How many new postings of a board one reading adds for a profile, the newest: a first reading may match many. */
	static final int MAX_NEW_PER_COMPANY = 25;

	/** How many boards are read at the same time. */
	static final int AT_ONCE = 4;

	/** How much of a description a found job keeps: what the job is and asks for come first. */
	static final int DESCRIPTION_MAX_LENGTH = 4000;

	/** How many of the profile's latest found jobs the suggestions look at, with all its jobs. */
	private static final int FOUND_JOBS_TO_SUGGEST_FROM = 500;

	private static final int ERROR_MAX_LENGTH = 500;

	private static final int NAME_MAX_LENGTH = 200;

	private static final Duration WEEK = Duration.ofDays(7);

	private final WatchedCompanyRepository companies;

	private final WatchedCompanyFindRepository finds;

	private final DiscoveryService discovery;

	private final ApplicationService applications;

	private final BoardFeeds feeds;

	private final TransactionTemplate transactions;

	private final Duration every;

	private final ReentrantLock reading = new ReentrantLock();

	/**
	 * @param every how often each board is read on its own
	 */
	DefaultWatchlistService(WatchedCompanyRepository companies, WatchedCompanyFindRepository finds,
			DiscoveryService discovery, ApplicationService applications, BoardFeeds feeds,
			PlatformTransactionManager transactionManager, @Value("${jobwarden.watchlist.every:PT6H}") Duration every) {
		this.companies = companies;
		this.finds = finds;
		this.discovery = discovery;
		this.applications = applications;
		this.feeds = feeds;
		this.transactions = new TransactionTemplate(transactionManager);
		this.every = every;
	}

	@Override
	@Transactional(readOnly = true)
	public List<WatchedCompany> findCompanies(long userId) {
		return toCompanies(this.companies.findByUserIdOrderByNameAscIdAsc(userId));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<BoardLink> findBoard(long userId, String link) {
		return Board.of(link).map(board -> {
			WatchedCompany watched = this.companies.findByUserIdAndAtsAndBoard(userId, board.ats(), board.name())
				.map(company -> toCompanies(List.of(company)).getFirst())
				.orElse(null);
			return new BoardLink(board.ats(), board.name(), board.url(),
					(watched != null) ? watched.name() : board.companyName(), watched);
		});
	}

	/**
	 * @throws BusinessRuleException if the link isn't on a board, the board doesn't exist, or it can't be read
	 */
	@Override
	public WatchResult watch(long userId, String link, @Nullable String name) {
		Board board = Board.of(link)
			.orElseThrow(() -> new BusinessRuleException(WatchlistErrorCode.NOT_A_BOARD_LINK,
					"Not a link to a Greenhouse, Lever or Ashby board: " + link));
		Optional<WatchedCompanyEntity> existing = this.companies.findByUserIdAndAtsAndBoard(userId, board.ats(),
				board.name());
		if (existing.isPresent()) {
			return new WatchResult(toCompanies(List.of(existing.get())).getFirst(), false, 0);
		}
		List<Posting> postings;
		try {
			postings = this.feeds.postings(board);
		} catch (BoardFailure failed) {
			log.debug("Board {} not watched for profile {}: {}", board, userId, failed.getMessage());
			if (failed.notFound()) {
				throw new BusinessRuleException(WatchlistErrorCode.BOARD_NOT_FOUND, "No board at " + board.url(),
						board.url());
			}
			throw new BusinessRuleException(WatchlistErrorCode.BOARD_NOT_READ, "Couldn't read " + board.url(),
					board.url(), shortened(failed.getMessage(), ERROR_MAX_LENGTH));
		}
		String given = (name != null && !name.isBlank()) ? name.strip() : null;
		String companyName = shortened((given != null) ? given
				: postings.stream().map(Posting::company).filter(Objects::nonNull).findFirst().orElse(board.companyName()),
				NAME_MAX_LENGTH);
		WatchedCompanyEntity company;
		try {
			company = Objects.requireNonNull(this.transactions.execute(status -> this.companies
				.save(new WatchedCompanyEntity(userId, companyName, board.ats(), board.name()))));
		} catch (DataIntegrityViolationException watchedMeanwhile) {
			// Added a moment ago, in another tab say
			return this.companies.findByUserIdAndAtsAndBoard(userId, board.ats(), board.name())
				.map(watched -> new WatchResult(toCompanies(List.of(watched)).getFirst(), false, 0))
				.orElseThrow(() -> watchedMeanwhile);
		}
		log.info("Profile {} watches {} ({}, {} jobs on its board)", userId, companyName, board, postings.size());
		this.reading.lock();
		int newJobs;
		try {
			newJobs = readBoard(board, postings, List.of(company), new ConcurrentHashMap<>()).newJobs();
		} finally {
			this.reading.unlock();
		}
		return new WatchResult(toCompanies(List.of(this.companies.findById(company.getId()).orElse(company))).getFirst(),
				true, newJobs);
	}

	/**
	 * @throws WatchedCompanyNotFoundException if the profile doesn't watch it
	 */
	@Override
	@Transactional
	public void unwatch(long userId, long companyId) {
		WatchedCompanyEntity company = this.companies.findByIdAndUserId(companyId, userId)
			.orElseThrow(() -> new WatchedCompanyNotFoundException(companyId));
		this.companies.delete(company);
		log.info("Profile {} stopped watching {} ({})", userId, company.getName(), companyId);
	}

	@Override
	public WatchlistCheck check(long userId) {
		this.reading.lock();
		try {
			WatchlistCheck checked = read(this.companies.findByUserIdOrderByNameAscIdAsc(userId));
			log.info("Checked the watchlist of profile {}: {}", userId, checked);
			return checked;
		} finally {
			this.reading.unlock();
		}
	}

	/**
	 * Reads the boards whose time has come, each with every profile watching it: those of a company never read, or
	 * read {@code jobwarden.watchlist.every} ago.
	 */
	WatchlistCheck checkDue() {
		this.reading.lock();
		try {
			Instant now = Instant.now();
			Map<Board, List<WatchedCompanyEntity>> byBoard = byBoard(this.companies.findAllByOrderByAtsAscBoardAscIdAsc());
			List<WatchedCompanyEntity> due = byBoard.values()
				.stream()
				.filter(watching -> watching.stream().anyMatch(company -> company.isDue(this.every, now)))
				.flatMap(List::stream)
				.toList();
			if (due.isEmpty()) {
				return WatchlistCheck.NONE;
			}
			WatchlistCheck checked = read(due);
			log.info("Read the watched boards that were due: {}", checked);
			return checked;
		} finally {
			this.reading.unlock();
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<CompanySuggestion> suggest(long userId, int limit) {
		Set<Board> watched = this.companies.findByUserIdOrderByNameAscIdAsc(userId)
			.stream()
			.map(company -> new Board(company.getAts(), company.getBoard()))
			.collect(Collectors.toSet());
		Map<Board, Map<String, Integer>> names = new LinkedHashMap<>();
		Map<Board, Integer> jobs = new HashMap<>();
		List<ApplicationSummary> tracked = this.applications.findAll(userId, null);
		List<FoundJob> found = this.discovery.findFoundJobs(userId, null, FOUND_JOBS_TO_SUGGEST_FROM);
		tracked.forEach(job -> tally(job.link(), job.company(), watched, names, jobs));
		found.forEach(job -> tally(job.link(), job.company(), watched, names, jobs));
		return jobs.entrySet()
			.stream()
			.map(board -> new CompanySuggestion(nameOf(board.getKey(), names.get(board.getKey())), board.getKey().ats(),
					board.getKey().name(), board.getKey().url(), board.getValue()))
			.sorted(Comparator.comparingInt(CompanySuggestion::jobs).reversed().thenComparing(CompanySuggestion::name))
			.limit(limit)
			.toList();
	}

	private static void tally(@Nullable String link, @Nullable String company, Set<Board> watched,
			Map<Board, Map<String, Integer>> names, Map<Board, Integer> jobs) {
		Board.of(link).filter(board -> !watched.contains(board)).ifPresent(board -> {
			jobs.merge(board, 1, Integer::sum);
			Map<String, Integer> called = names.computeIfAbsent(board, any -> new HashMap<>());
			if (company != null && !company.isBlank()) {
				called.merge(company.strip(), 1, Integer::sum);
			}
		});
	}

	/** What the profile's jobs call the company the most, else the board's name. */
	private static String nameOf(Board board, @Nullable Map<String, Integer> called) {
		return (called == null) ? board.companyName()
				: called.entrySet()
					.stream()
					.max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
					.map(Map.Entry::getKey)
					.orElse(board.companyName());
	}

	/**
	 * Reads each board once, {@link #AT_ONCE} at a time, for all the companies on it.
	 */
	private WatchlistCheck read(List<WatchedCompanyEntity> watched) {
		Map<Board, List<WatchedCompanyEntity>> byBoard = byBoard(watched);
		Map<Long, List<SavedSearch>> searches = new ConcurrentHashMap<>();
		Semaphore slots = new Semaphore(AT_ONCE);
		WatchlistCheck total = WatchlistCheck.NONE;
		try (ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<WatchlistCheck>> boards = new ArrayList<>();
			byBoard.forEach((board, watching) -> boards.add(readers.submit(() -> {
				slots.acquire();
				try {
					return readBoard(board, watching, searches);
				} finally {
					slots.release();
				}
			})));
			for (Future<WatchlistCheck> board : boards) {
				total = total.plus(resultOf(board));
			}
		} catch (InterruptedException _) {
			Thread.currentThread().interrupt();
		}
		return total;
	}

	/**
	 * @return what reading the board did; nothing when it failed in a way readBoard didn't expect, which is logged
	 */
	private static WatchlistCheck resultOf(Future<WatchlistCheck> board) throws InterruptedException {
		try {
			return board.get();
		} catch (ExecutionException failed) {
			log.warn("Couldn't read a watched board: {}", failed.getCause().toString());
			return WatchlistCheck.NONE;
		}
	}

	private WatchlistCheck readBoard(Board board, List<WatchedCompanyEntity> watching,
			Map<Long, List<SavedSearch>> searches) {
		List<Posting> postings;
		try {
			postings = this.feeds.postings(board);
		} catch (RuntimeException failed) {
			// BoardFailure, or a board's answer JobWarden didn't expect
			String reason = shortened(failed.getMessage(), ERROR_MAX_LENGTH);
			Instant now = Instant.now();
			this.transactions.executeWithoutResult(status -> watching
				.forEach(company -> this.companies.findById(company.getId()).ifPresent(still -> still.failed(reason, now))));
			log.warn("Couldn't read the watched board {}: {}", board, reason);
			return new WatchlistCheck(watching.size(), watching.size(), 0);
		}
		return readBoard(board, postings, watching, searches);
	}

	/**
	 * For each profile watching the board, adds the postings that fit its saved searches.
	 */
	private WatchlistCheck readBoard(Board board, List<Posting> postings, List<WatchedCompanyEntity> watching,
			Map<Long, List<SavedSearch>> searches) {
		Map<String, Optional<String>> descriptions = new HashMap<>();
		int newJobs = 0;
		int failed = 0;
		for (WatchedCompanyEntity company : watching) {
			Instant now = Instant.now();
			try {
				List<SavedSearch> mine = searches.computeIfAbsent(company.getUserId(), this.discovery::findSearches);
				// Reading descriptions talks to the board: outside the transaction that saves what it found
				List<NewFoundJob> jobs = matches(board, postings, company, mine, descriptions, now);
				newJobs += Objects.requireNonNull(this.transactions.execute(status -> {
					int added = add(board, company, jobs);
					this.companies.findById(company.getId()).ifPresent(still -> still.checked(now));
					return added;
				}));
			} catch (RuntimeException failure) {
				// A saved search deleted meanwhile, say: the next reading tries again
				failed++;
				String reason = shortened(failure.getMessage(), ERROR_MAX_LENGTH);
				this.transactions.executeWithoutResult(status -> this.companies.findById(company.getId())
					.ifPresent(still -> still.failed(reason, now)));
				log.warn("Couldn't add the jobs of {} for profile {}: {}", board, company.getUserId(), failure.toString());
			}
		}
		return new WatchlistCheck(watching.size(), failed, newJobs);
	}

	/**
	 * The board's postings that fit one of the profile's saved searches, but those whose links it has: the newest,
	 * {@link #MAX_NEW_PER_COMPANY} at most.
	 *
	 * @param descriptions those read already, by posting: Greenhouse's list leaves them out
	 */
	private List<NewFoundJob> matches(Board board, List<Posting> postings, WatchedCompanyEntity company,
			List<SavedSearch> searches, Map<String, Optional<String>> descriptions, Instant now) {
		if (searches.isEmpty()) {
			return List.of();
		}
		List<Posting> matched = postings.stream()
			.filter(posting -> PostingMatcher.firstMatch(posting, searches, now).isPresent())
			.toList();
		if (matched.isEmpty()) {
			return List.of();
		}
		Set<String> known = this.discovery.findKnownLinks(company.getUserId(), matched.stream().map(Posting::link).toList());
		List<Posting> fresh = matched.stream()
			.filter(posting -> !known.contains(posting.link()))
			.sorted(Comparator.comparing(Posting::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
			.toList();
		List<NewFoundJob> jobs = new ArrayList<>();
		for (Posting posting : fresh) {
			if (jobs.size() >= MAX_NEW_PER_COMPANY) {
				break;
			}
			Posting complete = withDescription(board, posting, descriptions);
			// With its description read, the search's skills can be looked for
			PostingMatcher.firstMatch(complete, searches, now)
				.ifPresent(search -> jobs.add(toFoundJob(complete, company, search)));
		}
		return jobs;
	}

	/**
	 * @return how many went to the AI Assistant page: a link found meanwhile is left out
	 */
	private int add(Board board, WatchedCompanyEntity company, List<NewFoundJob> jobs) {
		if (jobs.isEmpty()) {
			return 0;
		}
		AddedFoundJobs added = this.discovery.addFoundJobs(company.getUserId(), jobs);
		for (FoundJob job : added.added()) {
			this.finds.save(new WatchedCompanyFindEntity(company.getId(), job.id()));
		}
		log.info("Added {} jobs from {} for profile {}", added.added().size(), board, company.getUserId());
		return added.added().size();
	}

	private Posting withDescription(Board board, Posting posting, Map<String, Optional<String>> descriptions) {
		if (posting.description() != null || posting.id().isEmpty()) {
			return posting;
		}
		Optional<String> description = descriptions.computeIfAbsent(posting.id(),
				id -> Optional.ofNullable(this.feeds.description(board, id)));
		return description.map(text -> posting.toBuilder().description(text).build()).orElse(posting);
	}

	private static NewFoundJob toFoundJob(Posting posting, WatchedCompanyEntity company, SavedSearch search) {
		String description = posting.description();
		return NewFoundJob.builder()
			.title(posting.title())
			.company(company.getName())
			.location(posting.location())
			.salary(posting.salary())
			.link(posting.link())
			.description((description == null || description.length() <= DESCRIPTION_MAX_LENGTH) ? description
					: description.substring(0, DESCRIPTION_MAX_LENGTH - 1) + "…")
			.note("From " + company.getName() + "'s job board on " + company.getAts().label() + ", which you watch.")
			.postedOn((posting.publishedAt() != null) ? LocalDate.ofInstant(posting.publishedAt(), ZoneId.systemDefault())
					: null)
			.searchId(search.id())
			.build();
	}

	private static Map<Board, List<WatchedCompanyEntity>> byBoard(List<WatchedCompanyEntity> watched) {
		return watched.stream()
			.collect(Collectors.groupingBy(company -> new Board(company.getAts(), company.getBoard()), LinkedHashMap::new,
					Collectors.toList()));
	}

	/** With how many jobs each one's board gave this week, counted once for all of them. */
	private List<WatchedCompany> toCompanies(List<WatchedCompanyEntity> entities) {
		Map<Long, Long> thisWeek = new HashMap<>();
		if (!entities.isEmpty()) {
			for (Object[] count : this.finds.countSince(entities.stream().map(WatchedCompanyEntity::getId).toList(),
					Instant.now().minus(WEEK))) {
				thisWeek.put((Long) count[0], (Long) count[1]);
			}
		}
		return entities.stream()
			.map(company -> WatchedCompany.builder()
				.id(company.getId())
				.name(company.getName())
				.ats(company.getAts())
				.board(company.getBoard())
				.boardUrl(company.getAts().boardUrl(company.getBoard()))
				.addedAt(company.getAddedAt())
				.lastCheckedAt(company.getLastCheckedAt())
				.lastError(company.getLastError())
				.newThisWeek(thisWeek.getOrDefault(company.getId(), 0L))
				.build())
			.toList();
	}

	private static String shortened(@Nullable String text, int maxLength) {
		String said = Objects.requireNonNullElse(text, "");
		return (said.length() <= maxLength) ? said : said.substring(0, maxLength - 1) + "…";
	}

}
