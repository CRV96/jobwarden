package com.jobwarden.jobs;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.jobs.dto.ComparedOffer;
import com.jobwarden.jobs.dto.JobOffer;
import com.jobwarden.jobs.dto.NegotiationBrief;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.OfferComparison;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.OfferService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class OfferServiceTests {

	@Autowired
	private OfferService offers;

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
	void keepsTheTermsTidiedAndReplacesThemWhenTypedAgain() {
		long job = create(this.userId, "Java Developer", ApplicationStatus.OFFER, null);
		assertThat(this.offers.find(this.userId, job)).isEqualTo(JobOffer.NONE);

		JobOffer saved = this.offers.save(this.userId, job, JobOffer.builder()
			.baseSalary(" 90,000 EUR gross per year ")
			.bonus(" ")
			.timeOff("25 days")
			.respondBy(LocalDate.of(2026, 11, 20))
			.build());

		assertThat(saved.baseSalary()).isEqualTo("90,000 EUR gross per year");
		assertThat(saved.bonus()).isNull();
		assertThat(saved.updatedAt()).isNotNull();
		assertThat(this.offers.find(this.userId, job).respondBy()).isEqualTo(LocalDate.of(2026, 11, 20));

		this.offers.save(this.userId, job, JobOffer.builder().equity("0.05%").build());
		JobOffer again = this.offers.find(this.userId, job);
		assertThat(again.equity()).isEqualTo("0.05%");
		assertThat(again.baseSalary()).isNull();
	}

	@Test
	void keepsTheTermsToTheProfilesOwnJobs() {
		long job = create(this.userId, "Java Developer", ApplicationStatus.OFFER, null);

		assertThatExceptionOfType(ApplicationNotFoundException.class)
			.isThrownBy(() -> this.offers.save(this.otherUserId, job, JobOffer.builder().bonus("10%").build()));
		assertThat(this.offers.find(this.otherUserId, job)).isEqualTo(JobOffer.NONE);
	}

	@Test
	void comparesTheOffersSalariesFromTheTermsOrElseThePostings() {
		long typedIn = create(this.userId, "Java Developer", ApplicationStatus.OFFER, "70,000 EUR per year");
		this.offers.save(this.userId, typedIn, JobOffer.builder().baseSalary("90,000 EUR per year").build());
		long fromPosting = create(this.userId, "Backend Engineer", ApplicationStatus.OFFER, "6,000 EUR per month");
		long unreadable = create(this.userId, "Platform Engineer", ApplicationStatus.OFFER, "Competitive");
		// Not an offer, and not the profile's
		create(this.userId, "Data Engineer", ApplicationStatus.APPLIED, "100,000 EUR per year");
		create(this.otherUserId, "Java Developer", ApplicationStatus.OFFER, "120,000 EUR per year");
		this.offers.saveBrief(this.userId, fromPosting, NegotiationBrief.builder().position("Fine.").build());

		OfferComparison comparison = this.offers.compare(this.userId, "EUR", SalaryPeriod.YEAR);

		assertThat(comparison.offers()).extracting(offer -> offer.application().id())
			.containsExactlyInAnyOrder(typedIn, fromPosting, unreadable);
		ComparedOffer typed = offer(comparison, typedIn);
		assertThat(typed.salaryText()).isEqualTo("90,000 EUR per year");
		assertThat(typed.salaryFromPosting()).isFalse();
		assertThat(typed.salary().midpoint()).isEqualByComparingTo("90000");
		ComparedOffer posting = offer(comparison, fromPosting);
		assertThat(posting.salaryFromPosting()).isTrue();
		assertThat(posting.salary().midpoint()).isEqualByComparingTo(new BigDecimal("72000"));
		assertThat(posting.hasBrief()).isTrue();
		assertThat(offer(comparison, unreadable).salaryUnreadable()).isTrue();
		assertThat(comparison.best()).hasValueSatisfying(best -> assertThat(best.application().id()).isEqualTo(typedIn));
	}

	@Test
	void keepsANegotiationBriefPerJobAndDeletesIt() {
		long job = create(this.userId, "Java Developer", ApplicationStatus.OFFER, null);

		this.offers.saveBrief(this.userId, job, NegotiationBrief.builder()
			.position("8% under the median.")
			.target("95,000 EUR")
			.leverage(List.of("Median 92,000 EUR"))
			.sources(List.of("Stack Overflow survey 2025"))
			.build());
		this.offers.saveBrief(this.userId, job, NegotiationBrief.builder().position("On the median.").build());

		assertThat(this.offers.findBrief(this.userId, job)).hasValueSatisfying(brief -> {
			assertThat(brief.position()).isEqualTo("On the median.");
			assertThat(brief.leverage()).isEmpty();
			assertThat(brief.updatedAt()).isNotNull();
		});
		assertThat(this.offers.findBrief(this.otherUserId, job)).isEmpty();
		assertThatExceptionOfType(ApplicationNotFoundException.class).isThrownBy(
				() -> this.offers.saveBrief(this.otherUserId, job, NegotiationBrief.builder().position("Mine.").build()));

		this.offers.deleteBrief(this.otherUserId, job);
		assertThat(this.offers.findBrief(this.userId, job)).isPresent();
		this.offers.deleteBrief(this.userId, job);
		assertThat(this.offers.findBrief(this.userId, job)).isEmpty();
	}

	@Test
	void forgetsTheOfferOfADeletedJob() {
		long job = create(this.userId, "Java Developer", ApplicationStatus.OFFER, null);
		this.offers.save(this.userId, job, JobOffer.builder().bonus("10%").build());
		this.offers.saveBrief(this.userId, job, NegotiationBrief.builder().position("Fine.").build());

		this.applications.delete(this.userId, job);

		assertThat(this.jdbc.sql("SELECT count(*) FROM job_offers").query(Long.class).single()).isZero();
		assertThat(this.jdbc.sql("SELECT count(*) FROM negotiation_briefs").query(Long.class).single()).isZero();
	}

	private static ComparedOffer offer(OfferComparison comparison, long jobId) {
		return comparison.offers().stream().filter(offer -> offer.application().id() == jobId).findFirst().orElseThrow();
	}

	private long create(long userId, String title, ApplicationStatus status, @Nullable String salary) {
		return this.applications.create(NewApplication.builder()
			.userId(userId)
			.title(title)
			.company("Acme")
			.salary(salary)
			.link("https://example.com/jobs/" + System.nanoTime())
			.initialStatus(status)
			.initialStatusDate(LocalDate.now())
			.build());
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id").param(name).query(Long.class).single();
	}

}
