package com.jobwarden.web.dto;

import java.time.LocalDate;

import com.jobwarden.jobs.dto.JobOffer;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.format.annotation.DateTimeFormat;

import static com.jobwarden.web.dto.FormValues.blankToNull;

/**
 * The terms of a job's offer, saved as they're changed on its page. Every field is optional.
 */
public record OfferForm(
		@Size(max = OfferForm.TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String baseSalary,
		@Size(max = OfferForm.TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String bonus,
		@Size(max = OfferForm.TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String equity,
		@Size(max = OfferForm.TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String signingBonus,
		@Size(max = OfferForm.TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String timeOff,
		@Size(max = OfferForm.LONG_TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String benefits,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate startDate,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate respondBy,
		@Size(max = OfferForm.LONG_TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String notes) {

	/** Also the {@code maxlength} of the one-line fields in applications/offer.html. */
	public static final int TEXT_MAX_LENGTH = 200;

	/** Also the {@code maxlength} of the benefits and the notes. */
	public static final int LONG_TEXT_MAX_LENGTH = 2000;

	public JobOffer toOffer() {
		return JobOffer.builder()
			.baseSalary(blankToNull(this.baseSalary))
			.bonus(blankToNull(this.bonus))
			.equity(blankToNull(this.equity))
			.signingBonus(blankToNull(this.signingBonus))
			.timeOff(blankToNull(this.timeOff))
			.benefits(blankToNull(this.benefits))
			.startDate(this.startDate)
			.respondBy(this.respondBy)
			.notes(blankToNull(this.notes))
			.build();
	}

}
