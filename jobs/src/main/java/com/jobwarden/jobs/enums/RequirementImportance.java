package com.jobwarden.jobs.enums;

/**
 * How much a posting's requirement weighs in the job's fit score.
 */
public enum RequirementImportance {

	/** Required: the posting says it must be there. Missing one caps the score. */
	MUST(3),

	/** A plus: nice to have, a bonus, preferred. */
	NICE(1);

	private final int weight;

	RequirementImportance(int weight) {
		this.weight = weight;
	}

	public int weight() {
		return this.weight;
	}

}
