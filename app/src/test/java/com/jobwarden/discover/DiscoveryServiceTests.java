package com.jobwarden.discover;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.dto.AddedFoundJobs;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.dto.NewFoundJob;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.dto.SearchCount;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.exception.FoundJobNotFoundException;
import com.jobwarden.discover.exception.SavedSearchNotFoundException;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.search.Recency;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.search.WorkType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * With the modules discover uses, jobs and search; the profiles are inserted with SQL, as identity isn't one of them.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class DiscoveryServiceTests {

	private static final SearchCriteria REMOTE_JAVA = SearchCriteria.builder()
		.titles(List.of("Java Developer"))
		.locations(List.of("Europe"))
		.workTypes(Set.of(WorkType.REMOTE))
		.excludes(List.of("junior"))
		.recency(Recency.WEEK)
		.build();

	@Autowired
	private DiscoveryService discovery;

	@Autowired
	private ApplicationService applications;

	@Autowired
	private JdbcClient jdbc;

	private long userId;

	private long otherUserId;

	@BeforeEach
	void createProfiles() {
		this.jdbc.sql("TRUNCATE users CASCADE").update();
		this.userId = insertProfile("Robert");
		this.otherUserId = insertProfile("Someone else");
	}

	@Test
	void keepsASearchsFilters() {
		SavedSearch saved = this.discovery.saveSearch(this.userId, " Remote Java ", REMOTE_JAVA);

		assertThat(saved.name()).isEqualTo("Remote Java");
		assertThat(this.discovery.findSearch(this.userId, saved.id()).criteria()).isEqualTo(REMOTE_JAVA);
		assertThat(this.discovery.findSearches(this.otherUserId)).isEmpty();
	}

	@Test
	void savingUnderTheSameNameReplacesTheFilters() {
		SavedSearch first = this.discovery.saveSearch(this.userId, "Remote Java", REMOTE_JAVA);
		SearchCriteria kotlin = REMOTE_JAVA.toBuilder().titles(List.of("Kotlin Developer")).build();

		SavedSearch second = this.discovery.saveSearch(this.userId, "remote java", kotlin);

		assertThat(second.id()).isEqualTo(first.id());
		assertThat(this.discovery.findSearches(this.userId)).singleElement()
			.satisfies(search -> assertThat(search.criteria().titles()).containsExactly("Kotlin Developer"));
	}

	@Test
	void needsANameForASearch() {
		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.discovery.saveSearch(this.userId, "  ", REMOTE_JAVA));
	}

	@Test
	void addsEachLinkOnceAndNeverOneThatIsAnApplication() {
		long search = this.discovery.saveSearch(this.userId, "Remote Java", REMOTE_JAVA).id();
		this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title("Already tracked")
			.link("https://jobs.lever.co/acme/3")
			.initialStatus(ApplicationStatus.APPLIED)
			.initialStatusDate(LocalDate.now())
			.build());

		AddedFoundJobs added = this.discovery.addFoundJobs(this.userId, List.of(
				job("Java Developer", "https://jobs.lever.co/acme/1#apply", search),
				job("Java Developer again", "https://jobs.lever.co/acme/1", search),
				job("Tracked", "https://jobs.lever.co/acme/3", search), job("Not a link", "javascript:alert(1)", null)));

		assertThat(added.added()).singleElement().satisfies(found -> {
			// Without the fragment, which never names another posting
			assertThat(found.link()).isEqualTo("https://jobs.lever.co/acme/1");
			assertThat(found.source()).isEqualTo("jobs.lever.co");
			assertThat(found.searchName()).isEqualTo("Remote Java");
			assertThat(found.decision()).isEqualTo(Decision.PENDING);
		});
		assertThat(added.skipped()).extracting(AddedFoundJobs.SkippedLink::reason)
			.containsExactly("already found", "already an application", "not a web address");
		// Found again later: still left out
		assertThat(this.discovery.addFoundJobs(this.userId, List.of(job("Again", "https://jobs.lever.co/acme/1", null)))
			.added()).isEmpty();
	}

	@Test
	void reviewsTheJobFoundFirstFirst() {
		long search = this.discovery.saveSearch(this.userId, "Remote Java", REMOTE_JAVA).id();
		this.discovery.addFoundJobs(this.userId, List.of(job("First", "https://example.com/1", search),
				job("Second", "https://example.com/2", null)));

		assertThat(this.discovery.nextToReview(this.userId, null)).get().extracting(FoundJob::title).isEqualTo("First");
		assertThat(this.discovery.countToReview(this.userId, null)).isEqualTo(2);
		assertThat(this.discovery.countToReview(this.userId, search)).isEqualTo(1);
		assertThat(this.discovery.countToReviewBySearch(this.userId)).containsExactlyInAnyOrder(
				new SearchCount(search, 1), new SearchCount(null, 1));
		assertThat(this.discovery.nextToReview(this.otherUserId, null)).isEmpty();
	}

	@Test
	void savingOrApplyingMakesTheJobAnApplication() {
		List<FoundJob> found = this.discovery.addFoundJobs(this.userId, List.of(
				job("Saved", "https://example.com/1", null), job("Applied", "https://example.com/2", null))).added();

		FoundJob saved = this.discovery.decide(this.userId, found.get(0).id(), Decision.SAVED);
		FoundJob applied = this.discovery.decide(this.userId, found.get(1).id(), Decision.APPLIED);

		ApplicationDetails savedApplication = this.applications.findById(this.userId, saved.applicationId()).orElseThrow();
		assertThat(savedApplication.status()).isEqualTo(ApplicationStatus.SAVED);
		assertThat(savedApplication.company()).isEqualTo("Acme");
		assertThat(savedApplication.link()).isEqualTo("https://example.com/1");
		assertThat(this.applications.findById(this.userId, applied.applicationId()).orElseThrow().status())
			.isEqualTo(ApplicationStatus.APPLIED);
		assertThat(applied.decidedAt()).isNotNull();
		assertThat(this.discovery.countToReview(this.userId, null)).isZero();
	}

	@Test
	void bringsBackADiscardedJob() {
		long id = this.discovery.addFoundJobs(this.userId, List.of(job("Maybe", "https://example.com/1", null)))
			.added()
			.getFirst()
			.id();

		this.discovery.decide(this.userId, id, Decision.DISCARDED);
		assertThat(this.discovery.findFoundJobs(this.userId, Decision.DISCARDED, 10)).extracting(FoundJob::id).containsExactly(id);
		assertThat(this.discovery.findDecided(this.userId, 10)).extracting(FoundJob::id).containsExactly(id);
		assertThat(this.discovery.nextToReview(this.userId, null)).isEmpty();

		this.discovery.decide(this.userId, id, Decision.PENDING);
		assertThat(this.discovery.nextToReview(this.userId, null)).get().satisfies(job -> {
			assertThat(job.id()).isEqualTo(id);
			assertThat(job.decidedAt()).isNull();
		});
	}

	@Test
	void anApplicationStaysOne() {
		long id = this.discovery.addFoundJobs(this.userId, List.of(job("Saved", "https://example.com/1", null)))
			.added()
			.getFirst()
			.id();
		this.discovery.decide(this.userId, id, Decision.SAVED);

		assertThat(this.discovery.decide(this.userId, id, Decision.DISCARDED).decision()).isEqualTo(Decision.SAVED);
	}

	@Test
	void keepsTheJobsWhenTheirSearchGoes() {
		long search = this.discovery.saveSearch(this.userId, "Remote Java", REMOTE_JAVA).id();
		this.discovery.addFoundJobs(this.userId, List.of(job("Java", "https://example.com/1", search)));

		this.discovery.deleteSearch(this.userId, search);

		assertThat(this.discovery.nextToReview(this.userId, null)).get().satisfies(job -> {
			assertThat(job.searchId()).isNull();
			assertThat(job.searchName()).isNull();
		});
	}

	@Test
	void neverShowsAnotherProfilesJobsOrSearches() {
		long search = this.discovery.saveSearch(this.userId, "Remote Java", REMOTE_JAVA).id();
		long id = this.discovery.addFoundJobs(this.userId, List.of(job("Java", "https://example.com/1", null)))
			.added()
			.getFirst()
			.id();

		assertThatExceptionOfType(FoundJobNotFoundException.class)
			.isThrownBy(() -> this.discovery.decide(this.otherUserId, id, Decision.SAVED));
		assertThatExceptionOfType(SavedSearchNotFoundException.class)
			.isThrownBy(() -> this.discovery.addFoundJobs(this.otherUserId,
					List.of(job("Java", "https://example.com/2", search))));
	}

	private static NewFoundJob job(String title, String link, @Nullable Long searchId) {
		return NewFoundJob.builder()
			.title(title)
			.company("Acme")
			.location("Remote, Europe")
			.link(link)
			.note("Java and Kafka, fully remote")
			.searchId(searchId)
			.build();
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id").param(name).query(Long.class).single();
	}

}
