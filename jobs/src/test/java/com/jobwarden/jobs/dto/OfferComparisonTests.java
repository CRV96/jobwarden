package com.jobwarden.jobs.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.PayBasis;
import com.jobwarden.jobs.enums.SalaryPeriod;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfferComparisonTests {

	@Test
	void marksTheOfferThatPaysTheMostOnTheSameBasis() {
		assertThat(comparison(offer(1, 80_000, PayBasis.GROSS), offer(2, 90_000, PayBasis.GROSS)).best())
			.hasValueSatisfying(best -> assertThat(best.application().id()).isEqualTo(2));
		// None saying is the same basis for all
		assertThat(comparison(offer(1, 80_000, null), offer(2, 90_000, null)).best())
			.hasValueSatisfying(best -> assertThat(best.application().id()).isEqualTo(2));
	}

	@Test
	void marksNoneWhenAGrossSalaryWouldMeetANetOne() {
		assertThat(comparison(offer(1, 80_000, PayBasis.GROSS), offer(2, 60_000, PayBasis.NET)).best()).isEmpty();
		assertThat(comparison(offer(1, 80_000, PayBasis.GROSS), offer(2, 90_000, null)).best()).isEmpty();
		assertThat(comparison(offer(1, 80_000, PayBasis.GROSS)).best()).isEmpty();
	}

	private static OfferComparison comparison(ComparedOffer... offers) {
		return OfferComparison.builder()
			.currency("EUR")
			.period(SalaryPeriod.YEAR)
			.offers(List.of(offers))
			.currencies(List.of("EUR"))
			.build();
	}

	private static ComparedOffer offer(long id, int salary, @Nullable PayBasis basis) {
		ApplicationSummary job = ApplicationSummary.builder()
			.id(id)
			.title("Backend engineer")
			.status(ApplicationStatus.OFFER)
			.capturedAt(Instant.now())
			.build();
		SalaryEntry entry = new SalaryEntry(job, BigDecimal.valueOf(salary), BigDecimal.valueOf(salary), "EUR", false, basis);
		return new ComparedOffer(job, JobOffer.NONE, null, entry, false, null, null, false);
	}

}
