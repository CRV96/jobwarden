package com.jobwarden.jobs.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.jobwarden.jobs.enums.SalaryPeriod;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * The terms of an offer a job made the profile, as it typed them in, to compare offers side by side and to negotiate.
 * Every field is optional. Build it with {@link #builder()}.
 *
 * @param baseSalaryAmount the base salary, a figure picked with its currency and period on the job's page
 * @param baseSalaryCurrency its ISO 4217 code, with an amount
 * @param baseSalaryPeriod what it pays for, YEAR unless said otherwise
 * @param baseSalary the base salary in words, as terms saved before it was a figure, e.g. "90,000 EUR gross per year":
 * read like a posting's salary until the figure is saved; {@code null} with a figure, or for the posting's own
 * @param extras what it pays on top: a bonus, meal vouchers, shares, a signing bonus, other money, each with how often,
 * in the order typed in
 * @param timeOff e.g. "25 days"
 * @param benefits anything else: health insurance, a pension, a budget for learning...
 * @param startDate when the job would start
 * @param respondBy when the company wants an answer by
 * @param notes what the profile thinks of it: what it likes, what worries it
 * @param updatedAt when it was last changed; {@code null} for terms not kept yet
 */
@Builder(toBuilder = true)
public record JobOffer(@Nullable BigDecimal baseSalaryAmount, @Nullable String baseSalaryCurrency,
		@Nullable SalaryPeriod baseSalaryPeriod, @Nullable String baseSalary, List<OfferExtra> extras,
		@Nullable String timeOff, @Nullable String benefits, @Nullable LocalDate startDate, @Nullable LocalDate respondBy,
		@Nullable String notes, @Nullable Instant updatedAt) {

	/** Nothing known yet. */
	public static final JobOffer NONE = JobOffer.builder().build();

	public JobOffer {
		extras = (extras == null) ? List.of() : List.copyOf(extras);
	}

	/**
	 * @return how many days are left to answer, from the day given: 0 on the day, below 0 once it's past; {@code null}
	 * when the company set no day
	 */
	public @Nullable Long daysToRespond(LocalDate today) {
		return (this.respondBy == null) ? null : ChronoUnit.DAYS.between(today, this.respondBy);
	}

	/**
	 * @return whether nothing about the offer is known
	 */
	public boolean isEmpty() {
		return this.extras.isEmpty() && Stream.of(this.baseSalaryAmount, this.baseSalary, this.timeOff, this.benefits,
				this.startDate, this.respondBy, this.notes).allMatch(value -> value == null);
	}

	/**
	 * @return the base salary as a figure, when it was picked as one: the amount, both low and high
	 */
	public Optional<SalaryRange> baseSalaryRange() {
		if (this.baseSalaryAmount == null || this.baseSalaryCurrency == null) {
			return Optional.empty();
		}
		return Optional.of(new SalaryRange(this.baseSalaryAmount, this.baseSalaryAmount, this.baseSalaryCurrency,
				Objects.requireNonNullElse(this.baseSalaryPeriod, SalaryPeriod.YEAR), null));
	}

	/**
	 * @return the base salary in words, to show or read: the figure, e.g. 90000 EUR per year, else the words saved
	 * before it was one
	 */
	public @Nullable String baseSalaryText() {
		return baseSalaryRange().map(range -> range.low().stripTrailingZeros().toPlainString() + " " + range.currency()
				+ " per " + Objects.requireNonNull(range.period()).name().toLowerCase(Locale.ROOT)).orElse(this.baseSalary);
	}

}
