package com.jobwarden.web.dto;

import java.math.BigDecimal;
import java.util.Objects;

import com.jobwarden.jobs.dto.SalaryRange;
import com.jobwarden.jobs.enums.PayBasis;
import com.jobwarden.jobs.enums.SalaryPeriod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import org.jspecify.annotations.Nullable;

import static com.jobwarden.web.dto.FormValues.blankToNull;

/**
 * A job's facts, changed from its page: the posting's title, company, link and employment type, e.g. a company the
 * browser extension saved wrong, and the location and the salary. The salary comes as amounts: only {@code salaryFrom} for a single
 * amount, both for a range, either way round; neither for none.
 *
 * @param link the posting's address, http(s) only, as it's opened from the page; blank for none
 * @param currency the ISO 4217 code, needed with an amount
 * @param period what the salary pays for; {@code null} when the posting doesn't say
 * @param basis before tax or after; {@code null} when the posting doesn't say
 */
public record JobDetailsForm(
		@NotBlank(message = "{validation.application.title.required}")
		@Size(max = JobDetailsForm.TITLE_MAX_LENGTH, message = "{validation.details.title.size}") @Nullable String title,
		@Size(max = JobDetailsForm.COMPANY_MAX_LENGTH, message = "{validation.details.company.size}") @Nullable String company,
		@URL(message = "{validation.application.link.invalid}")
		@Pattern(regexp = "(?i)(https?://.*)?", message = "{validation.application.link.invalid}")
		@Size(max = JobDetailsForm.LINK_MAX_LENGTH, message = "{validation.details.link.size}") @Nullable String link,
		@Size(max = JobDetailsForm.EMPLOYMENT_TYPE_MAX_LENGTH, message = "{validation.details.employmentType.size}")
		@Nullable String employmentType,
		@Size(max = JobDetailsForm.LOCATION_MAX_LENGTH, message = "{validation.details.location.size}") @Nullable String location,
		@Positive(message = "{validation.details.salary.amount}")
		@Digits(integer = 12, fraction = 2, message = "{validation.details.salary.amount}") @Nullable BigDecimal salaryFrom,
		@Positive(message = "{validation.details.salary.amount}")
		@Digits(integer = 12, fraction = 2, message = "{validation.details.salary.amount}") @Nullable BigDecimal salaryTo,
		@Nullable String currency,
		@Nullable SalaryPeriod period,
		@Nullable PayBasis basis) {

	/** The {@code maxlength}s of the fields in applications/fragments.html too. */
	public static final int TITLE_MAX_LENGTH = 300;

	public static final int COMPANY_MAX_LENGTH = 200;

	public static final int LINK_MAX_LENGTH = 2000;

	public static final int EMPLOYMENT_TYPE_MAX_LENGTH = 100;

	public static final int LOCATION_MAX_LENGTH = 200;

	@AssertTrue(message = "{validation.details.salary.currency}")
	public boolean isCurrencyGivenWithTheAmounts() {
		return (this.salaryFrom == null && this.salaryTo == null) || blankToNull(this.currency) != null;
	}

	/**
	 * @return the title typed in; only once {@code @NotBlank} passed
	 */
	public String toTitle() {
		return Objects.requireNonNull(blankToNull(this.title), "a valid form has a title");
	}

	public @Nullable String toCompany() {
		return blankToNull(this.company);
	}

	public @Nullable String toLink() {
		return blankToNull(this.link);
	}

	public @Nullable String toEmploymentType() {
		return blankToNull(this.employmentType);
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
