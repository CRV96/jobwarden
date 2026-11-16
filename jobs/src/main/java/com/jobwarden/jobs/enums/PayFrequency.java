package com.jobwarden.jobs.enums;

/**
 * How often an offer's extra is paid. To count a year of it, a working day is one of 220 (the weekdays, less public
 * holidays and time off: meal vouchers are given for the days worked); what's paid once, or each time something
 * happens, isn't counted in a year, but beside it.
 */
public enum PayFrequency {

	WORKING_DAY(220), MONTH(12), QUARTER(4), YEAR(1), ONCE(0), EVENT(0);

	private final int perYear;

	PayFrequency(int perYear) {
		this.perYear = perYear;
	}

	/**
	 * @return how many times a year it's paid; 0 for once, or per event
	 */
	public int perYear() {
		return this.perYear;
	}

	/**
	 * @return whether it's paid again and again, so a year of it can be counted
	 */
	public boolean isRecurring() {
		return this.perYear > 0;
	}

}
