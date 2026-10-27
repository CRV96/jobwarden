package com.jobwarden.search;

/**
 * The kind of contract, by the word job postings use for it.
 */
public enum EmploymentType {

	FULL_TIME("full-time"), CONTRACT("contract"), FREELANCE("freelance"), INTERNSHIP("internship");

	private final String term;

	EmploymentType(String term) {
		this.term = term;
	}

	/**
	 * @return the word the queries search for
	 */
	public String term() {
		return this.term;
	}

}
