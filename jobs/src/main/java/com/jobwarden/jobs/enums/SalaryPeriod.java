package com.jobwarden.jobs.enums;

/**
 * What a salary pays for. To compare salaries, each counts as a year of them: 40 hours a week, 5 days a week, 52 weeks.
 */
public enum SalaryPeriod {

	HOUR(2080), DAY(260), WEEK(52), MONTH(12), YEAR(1);

	private final int perYear;

	SalaryPeriod(int perYear) {
		this.perYear = perYear;
	}

	/**
	 * @return how many of these periods make a year
	 */
	public int perYear() {
		return this.perYear;
	}

}
