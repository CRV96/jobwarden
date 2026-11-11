package com.jobwarden.jobs.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * The terms of an offer a job made the profile, as it typed them in, to compare offers side by side and to negotiate.
 * Every field is optional. Build it with {@link #builder()}.
 *
 * @param baseSalary as offers and postings write it, e.g. "90,000 EUR gross per year": read like a posting's salary,
 * so it compares; {@code null} for the posting's own
 * @param bonus e.g. "10% of the base, yearly"
 * @param equity e.g. "0.05%, vesting over 4 years"
 * @param signingBonus paid once, on joining
 * @param timeOff e.g. "25 days"
 * @param benefits anything else: health insurance, a pension, a budget for learning...
 * @param startDate when the job would start
 * @param respondBy when the company wants an answer by
 * @param notes what the profile thinks of it: what it likes, what worries it
 * @param updatedAt when it was last changed; {@code null} for terms not kept yet
 */
@Builder(toBuilder = true)
public record JobOffer(@Nullable String baseSalary, @Nullable String bonus, @Nullable String equity,
		@Nullable String signingBonus, @Nullable String timeOff, @Nullable String benefits,
		@Nullable LocalDate startDate, @Nullable LocalDate respondBy, @Nullable String notes,
		@Nullable Instant updatedAt) {

	/** Nothing known yet. */
	public static final JobOffer NONE = JobOffer.builder().build();

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
		return Stream.of(this.baseSalary, this.bonus, this.equity, this.signingBonus, this.timeOff, this.benefits,
				this.startDate, this.respondBy, this.notes).allMatch(value -> value == null);
	}

}
