package com.jobwarden.jobs.enums;

/**
 * How well the CV meets a posting's requirement.
 */
public enum RequirementMatch {

	/** The CV shows it. */
	MET(1.0),

	/** Something near it: a related tool, fewer years, a smaller scale. */
	PARTIAL(0.5),

	/** Nothing in the CV supports it. */
	MISSING(0.0);

	private final double value;

	RequirementMatch(double value) {
		this.value = value;
	}

	public double value() {
		return this.value;
	}

}
