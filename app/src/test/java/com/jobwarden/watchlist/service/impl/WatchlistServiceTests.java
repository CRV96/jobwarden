package com.jobwarden.watchlist.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.dto.NewFoundJob;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.watchlist.dto.WatchResult;
import com.jobwarden.watchlist.dto.WatchlistCheck;
import com.jobwarden.watchlist.enums.Ats;
import com.jobwarden.watchlist.exception.WatchedCompanyNotFoundException;
import com.jobwarden.watchlist.exception.WatchlistErrorCode;
import com.jobwarden.watchlist.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * With the modules the watchlist uses (discover, jobs, search), and no job board: what the boards' feeds give is the
 * test's own. In the module's internal package, to give it.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class WatchlistServiceTests {

	private static final Board ACME = new Board(Ats.LEVER, "acme");

	private static final Board GLOBEX = new Board(Ats.GREENHOUSE, "globex");

	private static final Instant YESTERDAY = Instant.now().minus(1, ChronoUnit.DAYS);

	@Autowired
	private WatchlistService watchlist;

	@Autowired
	private DefaultWatchlistService schedule;

	@Autowired
	private DiscoveryService discovery;

	@Autowired
	private ApplicationService applications;

	@Autowired
	private JdbcClient jdbc;

	@MockitoBean
	private BoardFeeds feeds;

	private long userId;

	private long otherUserId;

	private SavedSearch remoteJava;

	@BeforeEach
	void createProfiles() {
		this.jdbc.sql("TRUNCATE users CASCADE").update();
		this.userId = insertProfile("Robert");
		this.otherUserId = insertProfile("Someone else");
		this.remoteJava = this.discovery.saveSearch(this.userId, "Remote Java", SearchCriteria.builder()
			.titles(List.of("Java Developer"))
			.locations(List.of("Europe"))
			.excludes(List.of("junior"))
			.build());
		given(this.feeds.postings(ACME)).willReturn(List.of(
				posting("1", "Senior Java Engineer", "Remote, Germany"),
				posting("2", "Junior Java Developer", "Berlin"),
				posting("3", "Java Developer", "Austin, Texas"),
				posting("4", "Product Designer", "Berlin")));
	}

	@Test
	void watchesACompanyByAnyLinkToItsBoardAndAddsTheJobsThatMatchTheSavedSearches() {
		WatchResult watched = this.watchlist.watch(this.userId, "https://jobs.lever.co/acme/7f3a", null);

		assertThat(watched.added()).isTrue();
		assertThat(watched.newJobs()).isEqualTo(1);
		assertThat(watched.company().name()).isEqualTo("Acme");
		assertThat(watched.company().boardUrl()).isEqualTo("https://jobs.lever.co/acme");
		assertThat(watched.company().lastCheckedAt()).isNotNull();
		assertThat(watched.company().newThisWeek()).isEqualTo(1);
		assertThat(this.discovery.findFoundJobs(this.userId, null, 10)).singleElement().satisfies(job -> {
			assertThat(job.title()).isEqualTo("Senior Java Engineer");
			assertThat(job.company()).isEqualTo("Acme");
			assertThat(job.link()).isEqualTo("https://jobs.lever.co/acme/1");
			assertThat(job.searchId()).isEqualTo(this.remoteJava.id());
			assertThat(job.searchName()).isEqualTo("Remote Java");
			assertThat(job.requestId()).isNull();
			assertThat(job.note()).isEqualTo("From Acme's job board on Lever, which you watch.");
			assertThat(job.postedOn()).isEqualTo(LocalDate.ofInstant(YESTERDAY, ZoneId.systemDefault()));
		});
	}

	@Test
	void tellsTheBoardALinkIsOnAndWhetherTheProfileWatchesItWithoutReadingIt() {
		assertThat(this.watchlist.findBoard(this.userId, "https://www.linkedin.com/jobs/view/1")).isEmpty();
		assertThat(this.watchlist.findBoard(this.userId, "https://jobs.lever.co/acme/7f3a")).hasValueSatisfying(board -> {
			assertThat(board.name()).isEqualTo("Acme");
			assertThat(board.boardUrl()).isEqualTo("https://jobs.lever.co/acme");
			assertThat(board.company()).isNull();
		});

		this.watchlist.watch(this.userId, "https://jobs.lever.co/acme", "Acme Inc");

		assertThat(this.watchlist.findBoard(this.userId, "https://jobs.lever.co/acme/7f3a")).hasValueSatisfying(board -> {
			assertThat(board.name()).isEqualTo("Acme Inc");
			assertThat(board.company()).isNotNull();
		});
		assertThat(this.watchlist.findBoard(this.otherUserId, "https://jobs.lever.co/acme/7f3a"))
			.hasValueSatisfying(board -> assertThat(board.company()).isNull());
		then(this.feeds).should(times(1)).postings(ACME);
	}

	@Test
	void watchesACompanyOnce() {
		this.watchlist.watch(this.userId, "jobs.lever.co/acme", "Acme Corp");

		WatchResult again = this.watchlist.watch(this.userId, "https://jobs.lever.co/ACME/", "Other name");

		assertThat(again.added()).isFalse();
		assertThat(again.company().name()).isEqualTo("Acme Corp");
		assertThat(this.watchlist.findCompanies(this.userId)).hasSize(1);
		then(this.feeds).should(times(1)).postings(ACME);
	}

	@Test
	void saysWhenTheLinkIsntABoardOrTheBoardIsntThere() {
		given(this.feeds.postings(new Board(Ats.ASHBY, "nobody"))).willThrow(new BoardFailure("no such board", true));
		given(this.feeds.postings(new Board(Ats.ASHBY, "down"))).willThrow(new BoardFailure("api.ashbyhq.com answered 503"));

		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.watchlist.watch(this.userId, "https://www.linkedin.com/jobs/view/1", null))
			.satisfies(refused -> assertThat(refused.getErrorCode()).isEqualTo(WatchlistErrorCode.NOT_A_BOARD_LINK));
		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.watchlist.watch(this.userId, "https://jobs.ashbyhq.com/nobody", null))
			.satisfies(refused -> {
				assertThat(refused.getErrorCode()).isEqualTo(WatchlistErrorCode.BOARD_NOT_FOUND);
				assertThat(refused.getArguments()).containsExactly("https://jobs.ashbyhq.com/nobody");
			});
		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.watchlist.watch(this.userId, "https://jobs.ashbyhq.com/down", null))
			.satisfies(refused -> assertThat(refused.getErrorCode()).isEqualTo(WatchlistErrorCode.BOARD_NOT_READ));
		assertThat(this.watchlist.findCompanies(this.userId)).isEmpty();
	}

	@Test
	void addsOnlyThePostingsTheProfileDoesntHaveYet() {
		this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title("Java Developer")
			.company("Acme")
			.link("https://jobs.lever.co/acme/5")
			.initialStatus(ApplicationStatus.APPLIED)
			.initialStatusDate(LocalDate.now())
			.build());
		this.watchlist.watch(this.userId, "https://jobs.lever.co/acme", null);
		given(this.feeds.postings(ACME)).willReturn(List.of(posting("1", "Senior Java Engineer", "Remote, Germany"),
				posting("5", "Java Developer", "Remote, Poland"), posting("6", "Java Developer", "Kraków")));

		WatchlistCheck checked = this.watchlist.check(this.userId);

		assertThat(checked).isEqualTo(new WatchlistCheck(1, 0, 1));
		assertThat(this.discovery.findFoundJobs(this.userId, null, 10)).extracting(FoundJob::link)
			.containsExactlyInAnyOrder("https://jobs.lever.co/acme/1", "https://jobs.lever.co/acme/6");
		assertThat(this.watchlist.findCompanies(this.userId)).singleElement()
			.satisfies(company -> assertThat(company.newThisWeek()).isEqualTo(2));
	}

	@Test
	void readsGreenhouseDescriptionsForTheMatchesOnlyAndLooksForTheSkillsInThem() {
		this.discovery.saveSearch(this.userId, "Remote Java", SearchCriteria.builder()
			.titles(List.of("Java Developer"))
			.skills(List.of("Kafka"))
			.build());
		given(this.feeds.postings(GLOBEX)).willReturn(List.of(posting("10", "Java Engineer", "Remote"),
				posting("11", "Java Engineer", "Berlin"), posting("12", "Office Manager", "Berlin")));
		given(this.feeds.description(GLOBEX, "10")).willReturn("Java, Kafka and Postgres.");
		given(this.feeds.description(GLOBEX, "11")).willReturn("Java and Postgres.");

		WatchResult watched = this.watchlist.watch(this.userId, "https://boards.greenhouse.io/globex", null);

		assertThat(watched.newJobs()).isEqualTo(1);
		assertThat(this.discovery.findFoundJobs(this.userId, null, 10)).singleElement().satisfies(job -> {
			assertThat(job.link()).isEqualTo("https://jobs.lever.co/acme/10");
			assertThat(job.description()).isEqualTo("Java, Kafka and Postgres.");
		});
		then(this.feeds).should(never()).description(GLOBEX, "12");
	}

	@Test
	void readsEachBoardOnceForEveryProfileWatchingItWhenItsDue() {
		this.discovery.saveSearch(this.otherUserId, "Anything Java", SearchCriteria.builder()
			.titles(List.of("Java"))
			.build());
		this.watchlist.watch(this.userId, "https://jobs.lever.co/acme", null);
		this.watchlist.watch(this.otherUserId, "https://jobs.lever.co/acme", null);
		given(this.feeds.postings(ACME)).willReturn(List.of(posting("7", "Java Developer", "Remote, Spain")));
		// Read when they were added: not due for six hours
		assertThat(this.schedule.checkDue()).isEqualTo(WatchlistCheck.NONE);
		this.jdbc.sql("UPDATE watched_companies SET last_checked_at = now() - interval '7 hours' WHERE user_id = ?")
			.param(this.userId)
			.update();

		WatchlistCheck checked = this.schedule.checkDue();

		assertThat(checked).isEqualTo(new WatchlistCheck(2, 0, 2));
		then(this.feeds).should(times(3)).postings(ACME);
		assertThat(this.discovery.findFoundJobs(this.otherUserId, null, 10)).extracting(FoundJob::title)
			.contains("Java Developer", "Junior Java Developer");
	}

	@Test
	void keepsWhyABoardCouldntBeRead() {
		this.watchlist.watch(this.userId, "https://jobs.lever.co/acme", null);
		given(this.feeds.postings(ACME)).willThrow(new BoardFailure("api.lever.co answered 503"));

		assertThat(this.watchlist.check(this.userId)).isEqualTo(new WatchlistCheck(1, 1, 0));
		assertThat(this.watchlist.findCompanies(this.userId)).singleElement()
			.satisfies(company -> assertThat(company.lastError()).isEqualTo("api.lever.co answered 503"));
	}

	@Test
	void addsNothingForAProfileWithoutSavedSearches() {
		WatchResult watched = this.watchlist.watch(this.otherUserId, "https://jobs.lever.co/acme", null);

		assertThat(watched.newJobs()).isZero();
		assertThat(this.discovery.findFoundJobs(this.otherUserId, null, 10)).isEmpty();
	}

	@Test
	void stopsWatchingOnlyItsOwnCompanies() {
		long companyId = this.watchlist.watch(this.userId, "https://jobs.lever.co/acme", null).company().id();

		assertThatExceptionOfType(WatchedCompanyNotFoundException.class)
			.isThrownBy(() -> this.watchlist.unwatch(this.otherUserId, companyId));
		this.watchlist.unwatch(this.userId, companyId);

		assertThat(this.watchlist.findCompanies(this.userId)).isEmpty();
		// The jobs found on its board stay
		assertThat(this.discovery.findFoundJobs(this.userId, null, 10)).hasSize(1);
	}

	@Test
	void suggestsTheCompaniesWhoseBoardsTheProfilesJobsAreOn() {
		this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title("Platform Engineer")
			.company("Initech GmbH")
			.link("https://job-boards.greenhouse.io/initech/jobs/1")
			.initialStatus(ApplicationStatus.SAVED)
			.initialStatusDate(LocalDate.now())
			.build());
		this.discovery.addFoundJobs(this.userId, List.of(
				NewFoundJob.builder().title("SRE").company("Initech").link("https://boards.greenhouse.io/initech/jobs/2").build(),
				NewFoundJob.builder().title("SRE").company("Initech").link("https://boards.greenhouse.io/initech/jobs/3").build(),
				NewFoundJob.builder().title("Java Developer").link("https://jobs.lever.co/acme/9").build(),
				NewFoundJob.builder().title("Data Engineer").link("https://www.linkedin.com/jobs/view/4").build()));

		assertThat(this.watchlist.suggest(this.userId, 5)).satisfiesExactly(initech -> {
			assertThat(initech.name()).isEqualTo("Initech");
			assertThat(initech.ats()).isEqualTo(Ats.GREENHOUSE);
			assertThat(initech.link()).isEqualTo("https://job-boards.greenhouse.io/initech");
			assertThat(initech.jobs()).isEqualTo(3);
		}, acme -> assertThat(acme.name()).isEqualTo("Acme"));

		this.watchlist.watch(this.userId, "https://jobs.lever.co/acme", null);
		assertThat(this.watchlist.suggest(this.userId, 5)).extracting(suggestion -> suggestion.board())
			.containsExactly("initech");
	}

	private static Posting posting(String id, String title, String location) {
		return Posting.builder()
			.id(id)
			.title(title)
			.location(location)
			.countries(Set.of())
			.link("https://jobs.lever.co/acme/" + id)
			.publishedAt(YESTERDAY)
			.build();
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id").param(name).query(Long.class).single();
	}

}
