package com.jobwarden.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.jobwarden.jobs.dto.JobOffer;
import com.jobwarden.jobs.dto.OfferExtra;
import com.jobwarden.jobs.enums.OfferExtraKind;
import com.jobwarden.jobs.enums.PayFrequency;
import com.jobwarden.jobs.enums.SalaryPeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.format.annotation.DateTimeFormat;

import static com.jobwarden.web.dto.FormValues.blankToNull;

/**
 * The terms of a job's offer, saved as they're changed on its page. Every field is optional; the base salary is a
 * figure, with its currency and what it pays for, picked as the job's salary is.
 *
 * @param baseSalaryCurrency the ISO 4217 code, needed with an amount
 * @param baseSalaryPeriod what the base salary pays for; YEAR if left out
 * @param extras what it pays on top, a row each, numbered by app.js as they're added and removed
 */
public record OfferForm(
		@DecimalMin(value = "0.01", message = "{validation.details.salary.amount}")
		@Digits(integer = 12, fraction = 2, message = "{validation.details.salary.amount}") @Nullable BigDecimal baseSalaryAmount,
		@Nullable String baseSalaryCurrency,
		@Nullable SalaryPeriod baseSalaryPeriod,
		@Valid @Nullable List<@Nullable ExtraRow> extras,
		@Size(max = OfferForm.TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String timeOff,
		@Size(max = OfferForm.LONG_TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String benefits,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate startDate,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate respondBy,
		@Size(max = OfferForm.LONG_TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String notes) {

	/** Also the {@code maxlength} of the one-line fields in applications/offer.html. */
	public static final int TEXT_MAX_LENGTH = 200;

	/** Also the {@code maxlength} of the benefits and the notes. */
	public static final int LONG_TEXT_MAX_LENGTH = 2000;

	/**
	 * @return whether an amount comes with its currency
	 */
	@AssertTrue(message = "{validation.details.salary.currency}")
	public boolean isBaseSalaryCurrencyGiven() {
		return this.baseSalaryAmount == null || blankToNull(this.baseSalaryCurrency) != null;
	}

	/**
	 * What the offer pays on top: a bonus, meal vouchers, shares, a signing bonus, other money.
	 *
	 * @param amount what one payment is, in the unit; or left out, for the note to say
	 * @param unit an ISO 4217 code, or % for a percentage of the base salary
	 * @param frequency how often it's paid; yearly if left out
	 */
	public record ExtraRow(@Nullable OfferExtraKind kind,
			@DecimalMin(value = "0.01", message = "{validation.details.salary.amount}")
			@Digits(integer = 12, fraction = 2, message = "{validation.details.salary.amount}") @Nullable BigDecimal amount,
			@Nullable String unit, @Nullable PayFrequency frequency,
			@Size(max = OfferForm.TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String note) {

		/**
		 * @return whether an amount comes with its currency, or a percentage
		 */
		@AssertTrue(message = "{validation.details.salary.currency}")
		public boolean isUnitGiven() {
			return this.amount == null || blankToNull(this.unit) != null;
		}

		OfferExtra toExtra() {
			return new OfferExtra(Objects.requireNonNullElse(this.kind, OfferExtraKind.OTHER), this.amount,
					blankToNull(this.unit), Objects.requireNonNullElse(this.frequency, PayFrequency.YEAR),
					blankToNull(this.note));
		}

	}

	public JobOffer toOffer() {
		return JobOffer.builder()
			.baseSalaryAmount(this.baseSalaryAmount)
			.baseSalaryCurrency(blankToNull(this.baseSalaryCurrency))
			.baseSalaryPeriod(this.baseSalaryPeriod)
			.extras((this.extras == null) ? List.of()
					: this.extras.stream().filter(Objects::nonNull).map(ExtraRow::toExtra).toList())
			.timeOff(blankToNull(this.timeOff))
			.benefits(blankToNull(this.benefits))
			.startDate(this.startDate)
			.respondBy(this.respondBy)
			.notes(blankToNull(this.notes))
			.build();
	}

}
