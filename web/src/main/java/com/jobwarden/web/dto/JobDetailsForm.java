package com.jobwarden.web.dto;

import java.math.BigDecimal;

import com.jobwarden.jobs.dto.SalaryRange;
import com.jobwarden.jobs.enums.PayBasis;
import com.jobwarden.jobs.enums.SalaryPeriod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import static com.jobwarden.web.dto.FormValues.blankToNull;

/**
 * A job's location and salary, changed from its facts. The salary comes as amounts: only {@code salaryFrom} for a
 * single amount, both for a range, either way round; neither for none.
 *
 * @param currency the ISO 4217 code, needed with an amount
 * @param period what the salary pays for; {@code null} when the posting doesn't say
 * @param basis before tax or after; {@code null} when the posting doesn't say
 */
public record JobDetailsForm(
		@Size(max = JobDetailsForm.LOCATION_MAX_LENGTH, message = "{validation.details.location.size}") @Nullable String location,
		@Positive(message = "{validation.details.salary.amount}")
		@Digits(integer = 12, fraction = 2, message = "{validation.details.salary.amount}") @Nullable BigDecimal salaryFrom,
		@Positive(message = "{validation.details.salary.amount}")
		@Digits(integer = 12, fraction = 2, message = "{validation.details.salary.amount}") @Nullable BigDecimal salaryTo,
		@Nullable String currency,
		@Nullable SalaryPeriod period,
		@Nullable PayBasis basis) {

	/** Also the {@code maxlength} of the location field in applications/fragments.html. */
	public static final int LOCATION_MAX_LENGTH = 200;

	@AssertTrue(message = "{validation.details.salary.currency}")
	public boolean isCurrencyGivenWithTheAmounts() {
		return (this.salaryFrom == null && this.salaryTo == null) || blankToNull(this.currency) != null;
	}

	public @Nullable String toLocation() {
		return blankToNull(this.location);
	}

	/**
	 * @return the salary typed in, or {@code null} without amounts
	 */
	public @Nullable SalaryRange toSalaryRange() {
		BigDecimal low = (this.salaryFrom != null) ? this.salaryFrom : this.salaryTo;
		BigDecimal high = (this.salaryTo != null) ? this.salaryTo : this.salaryFrom;
		if (low == null || high == null || this.currency == null) {
			return null;
		}
		return new SalaryRange(low, high, this.currency.strip(), this.period, this.basis);
	}

}
