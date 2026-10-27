package com.jobwarden.search;

/**
 * Where the work is done, by the word job postings use for it.
 */
public enum WorkType {

	REMOTE("remote"), HYBRID("hybrid"), ONSITE("onsite");

	private final String term;

	WorkType(String term) {
		this.term = term;
	}

	/**
	 * @return the word the queries search for
	 */
	public String term() {
		return this.term;
	}

}
