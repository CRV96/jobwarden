package com.jobwarden.jobs;

import java.time.LocalDate;
import java.util.List;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.FitRequirement;
import com.jobwarden.jobs.dto.JobFit;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.FitFlagKind;
import com.jobwarden.jobs.enums.RequirementImportance;
import com.jobwarden.jobs.enums.RequirementMatch;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.JobFitService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class JobFitServiceTests {

	private static final List<FitRequirement> STRONG = List.of(
			new FitRequirement("5+ years of Java", RequirementImportance.MUST, RequirementMatch.MET, "8 years"),
			new FitRequirement("Kafka", RequirementImportance.MUST, RequirementMatch.PARTIAL, "RabbitMQ"),
			new FitRequirement("Kubernetes", RequirementImportance.NICE, RequirementMatch.MISSING, null));

	@Autowired
	private JobFitService fits;

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
	void keepsTheRequirementsInTheirOrderAndTheScoreTheyComeTo() {
		long job = create(this.userId, "Backend Engineer", "Acme", null);

		this.fits.save(this.userId, job, "Strong on Java.", STRONG);

		assertThat(this.fits.find(this.userId, job)).hasValueSatisfying(fit -> {
			// 1 + 4 x (3 + 1.5 + 0) / 7
			assertThat(fit.score()).isEqualByComparingTo("3.6");
			assertThat(fit.summary()).isEqualTo("Strong on Java.");
			assertThat(fit.requirements()).extracting(FitRequirement::text, FitRequirement::match)
				.containsExactly(tuple("5+ years of Java", RequirementMatch.MET), tuple("Kafka", RequirementMatch.PARTIAL),
						tuple("Kubernetes", RequirementMatch.MISSING));
			assertThat(fit.updatedAt()).isNotNull();
		});
		// Scored again: the latest is kept
		this.fits.save(this.userId, job, null, STRONG.subList(0, 1));
		assertThat(this.fits.find(this.userId, job).map(JobFit::score)).hasValueSatisfying(score ->
				assertThat(score).isEqualByComparingTo("5.0"));
		assertThat(this.fits.find(this.otherUserId, job)).isEmpty();
		assertThatExceptionOfType(ApplicationNotFoundException.class)
			.isThrownBy(() -> this.fits.save(this.otherUserId, job, null, STRONG));
	}

	@Test
	void listsTheJobsToScoreAndSortsTheListByScore() {
		long weak = create(this.userId, "Data Engineer", "Globex", null);
		long strong = create(this.userId, "Backend Engineer", "Acme", null);
		long unscored = create(this.userId, "Platform Engineer", "Initech", null);
		this.fits.save(this.userId, weak, null, List.of(new FitRequirement("Spark", RequirementImportance.MUST,
				RequirementMatch.MISSING, null)));
		this.fits.save(this.userId, strong, null, STRONG.subList(0, 1));

		assertThat(this.fits.findUnscored(this.userId, 10)).extracting(ApplicationSummary::id).containsExactly(unscored);
		assertThat(this.fits.findScores(this.userId, List.of(weak, strong, unscored))).containsOnlyKeys(weak, strong);
		assertThat(this.applications.find(this.userId, ApplicationQuery.builder()
			.sort(ApplicationSort.FIT)
			.direction(Sort.Direction.DESC)
			.page(0)
			.size(10)
			.build()).getContent()).extracting(ApplicationSummary::id).containsExactly(strong, weak, unscored);
	}

	@Test
	void flagsAPostingThatRulesOutTheSponsorshipTheProfileNeeds() {
		long job = create(this.userId, "Backend Engineer", "Acme", "We love Java. Unfortunately we are not able to "
				+ "offer visa sponsorship for this role. Apply now!");

		assertThat(this.fits.findFlags(this.userId, job, true)).singleElement().satisfies(flag -> {
			assertThat(flag.kind()).isEqualTo(FitFlagKind.NO_SPONSORSHIP);
			assertThat(flag.evidence()).isEqualTo("Unfortunately we are not able to offer visa sponsorship for this role.");
		});
		// Not to a profile that doesn't need it, or didn't say
		assertThat(this.fits.findFlags(this.userId, job, false)).isEmpty();
		assertThat(this.fits.findFlags(this.userId, job, null)).isEmpty();
	}

	@Test
	void flagsAJobPostedAgainAndOneUpForLong() {
		long first = create(this.userId, "Backend Engineer", "Acme", null);
		long again = create(this.userId, "backend engineer", "ACME", null);
		this.jdbc.sql("UPDATE applications SET raw_payload = CAST(? AS JSONB) WHERE id = ?")
			.params("{\"@type\": \"JobPosting\", \"datePosted\": \"" + LocalDate.now().minusDays(90) + "T10:00:00Z\"}", again)
			.update();

		assertThat(this.fits.findFlags(this.userId, again, null)).satisfiesExactlyInAnyOrder(
				flag -> {
					assertThat(flag.kind()).isEqualTo(FitFlagKind.REPOSTED);
					assertThat(flag.otherApplicationId()).isEqualTo(first);
				},
				flag -> {
					assertThat(flag.kind()).isEqualTo(FitFlagKind.OPEN_LONG);
					assertThat(flag.since()).isEqualTo(LocalDate.now().minusDays(90));
				});
	}

	private long create(long userId, String title, String company, @Nullable String description) {
		return this.applications.create(NewApplication.builder()
			.userId(userId)
			.title(title)
			.company(company)
			.description(description)
			.link("https://example.com/jobs/" + System.nanoTime())
			.initialStatus(ApplicationStatus.SAVED)
			.initialStatusDate(LocalDate.now())
			.build());
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id")
			.param(name)
			.query(Long.class)
			.single();
	}

}
