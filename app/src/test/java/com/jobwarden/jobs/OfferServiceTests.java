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
import com.jobwarden.jobs.dto.OfferExtra;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.OfferExtraKind;
import com.jobwarden.jobs.enums.PayFrequency;
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
			// Neither an amount with its unit, nor a note: nothing
			.extras(List.of(new OfferExtra(OfferExtraKind.BONUS, null, "EUR", PayFrequency.YEAR, " "),
					new OfferExtra(OfferExtraKind.BONUS, new BigDecimal("500"), " ", PayFrequency.YEAR, null)))
			.timeOff("25 days")
			.respondBy(LocalDate.of(2026, 11, 20))
			.build());

		assertThat(saved.baseSalary()).isEqualTo("90,000 EUR gross per year");
		assertThat(saved.extras()).isEmpty();
		assertThat(saved.updatedAt()).isNotNull();
		assertThat(this.offers.find(this.userId, job).respondBy()).isEqualTo(LocalDate.of(2026, 11, 20));

		this.offers.save(this.userId, job, JobOffer.builder()
			.extras(List.of(new OfferExtra(OfferExtraKind.EQUITY, null, null, PayFrequency.YEAR, " 0.05%, over 4 years ")))
			.build());
		JobOffer again = this.offers.find(this.userId, job);
		assertThat(again.extras())
			.containsExactly(new OfferExtra(OfferExtraKind.EQUITY, null, null, PayFrequency.YEAR, "0.05%, over 4 years"));
		assertThat(again.baseSalary()).isNull();
	}

	@Test
	void countsWhatTheOfferPaysAllInAYearAndWhatsPaidOnceOrPerEventBesideIt() {
		long job = create(this.userId, "Java Developer", ApplicationStatus.OFFER, null);

		JobOffer saved = this.offers.save(this.userId, job, JobOffer.builder()
			.baseSalaryAmount(new BigDecimal("5000"))
			.baseSalaryCurrency("EUR")
			.baseSalaryPeriod(SalaryPeriod.MONTH)
			.extras(List.of(new OfferExtra(OfferExtraKind.BONUS, BigDecimal.TEN, "%", PayFrequency.YEAR, "On targets"),
					new OfferExtra(OfferExtraKind.MEAL_VOUCHERS, BigDecimal.TEN, "eur", PayFrequency.WORKING_DAY, null),
					new OfferExtra(OfferExtraKind.BONUS, new BigDecimal("500"), "EUR", PayFrequency.QUARTER, null),
					new OfferExtra(OfferExtraKind.SIGNING_BONUS, new BigDecimal("5000"), "EUR", PayFrequency.ONCE, null),
					new OfferExtra(OfferExtraKind.OTHER, new BigDecimal("100"), "EUR", PayFrequency.EVENT, "From the state"),
					new OfferExtra(OfferExtraKind.EQUITY, null, null, PayFrequency.YEAR, "0.05%")))
			.build());

		// In the order typed in, the currency in capitals
		assertThat(saved.extras()).extracting(OfferExtra::kind)
			.containsExactly(OfferExtraKind.BONUS, OfferExtraKind.MEAL_VOUCHERS, OfferExtraKind.BONUS,
					OfferExtraKind.SIGNING_BONUS, OfferExtraKind.OTHER, OfferExtraKind.EQUITY);
		assertThat(saved.extras().get(1).unit()).isEqualTo("EUR");
		// 60,000 a year, and on top 10% of it, 220 working days of vouchers and four quarters: 6,000 + 2,200 + 2,000
		assertThat(this.offers.total(saved)).hasValueSatisfying(total -> {
			assertThat(total.currency()).isEqualTo("EUR");
			assertThat(total.base()).isEqualByComparingTo("60000");
			assertThat(total.extras()).isEqualByComparingTo("10200");
			assertThat(total.yearly()).isEqualByComparingTo("70200");
			assertThat(total.once()).isEqualByComparingTo("5000");
			assertThat(total.perEvent()).isEqualByComparingTo("100");
			// The shares say only what they are
			assertThat(total.uncounted()).isOne();
		});
		ComparedOffer compared = offer(this.offers.compare(this.userId, "EUR", SalaryPeriod.MONTH), job);
		assertThat(compared.total().per(SalaryPeriod.MONTH)).isEqualByComparingTo("5850");
		// Without a base salary, in the first extra's currency; a share of none isn't counted
		assertThat(this.offers.total(JobOffer.builder()
			.extras(List.of(new OfferExtra(OfferExtraKind.MEAL_VOUCHERS, new BigDecimal("40"), "RON", PayFrequency.WORKING_DAY, null),
					new OfferExtra(OfferExtraKind.BONUS, BigDecimal.TEN, "%", PayFrequency.YEAR, null)))
			.build())).hasValueSatisfying(total -> {
				assertThat(total.currency()).isEqualTo("RON");
				assertThat(total.base()).isNull();
				assertThat(total.yearly()).isEqualByComparingTo("8800");
				assertThat(total.uncounted()).isOne();
			});
		assertThat(this.offers.total(JobOffer.NONE)).isEmpty();
		// Words that say no period read as the comparison reads them: 5,000 EUR is a month
		assertThat(this.offers.total(JobOffer.builder().baseSalary("5,000 EUR").build()))
			.hasValueSatisfying(total -> assertThat(total.base()).isEqualByComparingTo("60000"));
	}

	@Test
	void keepsTheBaseSalaryAsAFigureInPlaceOfTheWordsAndComparesIt() {
		long job = create(this.userId, "Java Developer", ApplicationStatus.OFFER, "70,000 EUR per year");
		this.offers.save(this.userId, job, JobOffer.builder().baseSalary("70000 eur per year").build());

		JobOffer saved = this.offers.save(this.userId, job, JobOffer.builder()
			.baseSalaryAmount(new BigDecimal("8000"))
			.baseSalaryCurrency(" ron ")
			.build());

		// The figure, YEAR unless said, in place of the words from before
		assertThat(saved.baseSalaryAmount()).isEqualByComparingTo("8000");
		assertThat(saved.baseSalaryCurrency()).isEqualTo("RON");
		assertThat(saved.baseSalaryPeriod()).isEqualTo(SalaryPeriod.YEAR);
		assertThat(saved.baseSalary()).isNull();
		this.offers.save(this.userId, job, saved.toBuilder().baseSalaryPeriod(SalaryPeriod.MONTH).build());
		ComparedOffer compared = offer(this.offers.compare(this.userId, "RON", SalaryPeriod.YEAR), job);
		assertThat(compared.salaryText()).isEqualTo("8000 RON per month");
		assertThat(compared.salaryFromPosting()).isFalse();
		assertThat(compared.salary().midpoint()).isEqualByComparingTo("96000");
		// A currency without an amount says nothing
		JobOffer none = this.offers.save(this.userId, job, JobOffer.builder().baseSalaryCurrency("EUR").build());
		assertThat(none.baseSalaryCurrency()).isNull();
		assertThat(none.baseSalaryPeriod()).isNull();
	}

	@Test
	void comparesAFigureInACurrencyWithoutARateAsUnreadable() {
		long job = create(this.userId, "Java Developer", ApplicationStatus.OFFER, null);
		this.offers.save(this.userId, job, JobOffer.builder()
			.baseSalaryAmount(new BigDecimal("90000"))
			.baseSalaryCurrency("ZZZ")
			.build());

		ComparedOffer compared = offer(this.offers.compare(this.userId, "EUR", SalaryPeriod.YEAR), job);
		assertThat(compared.salaryUnreadable()).isTrue();
		assertThat(compared.total()).isNull();
	}

	@Test
	void keepsTheTermsToTheProfilesOwnJobs() {
		long job = create(this.userId, "Java Developer", ApplicationStatus.OFFER, null);

		assertThatExceptionOfType(ApplicationNotFoundException.class)
			.isThrownBy(() -> this.offers.save(this.otherUserId, job, JobOffer.builder().timeOff("25 days").build()));
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
		// In a currency no offer is in, nor a common one: still one to pick, so the page keeps it
		assertThat(this.offers.compare(this.userId, "SEK", SalaryPeriod.YEAR)).satisfies(inSek -> {
			assertThat(inSek.currency()).isEqualTo("SEK");
			assertThat(inSek.currencies()).contains("SEK", "EUR");
		});
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
		this.offers.save(this.userId, job, JobOffer.builder()
			.extras(List.of(new OfferExtra(OfferExtraKind.BONUS, BigDecimal.TEN, "%", PayFrequency.YEAR, null)))
			.build());
		this.offers.saveBrief(this.userId, job, NegotiationBrief.builder().position("Fine.").build());

		this.applications.delete(this.userId, job);

		assertThat(this.jdbc.sql("SELECT count(*) FROM job_offers").query(Long.class).single()).isZero();
		assertThat(this.jdbc.sql("SELECT count(*) FROM job_offer_extras").query(Long.class).single()).isZero();
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
