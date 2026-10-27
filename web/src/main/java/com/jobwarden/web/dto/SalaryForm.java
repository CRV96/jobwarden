package com.jobwarden.web.dto;

import org.jspecify.annotations.Nullable;

/**
 * An application's salary, corrected on the salaries page.
 */
public record SalaryForm(@Nullable String salary) {

	/**
	 * @return the salary, or {@code null} when the field was emptied
	 */
	public @Nullable String toSalary() {
		return FormValues.blankToNull(this.salary);
	}

}
