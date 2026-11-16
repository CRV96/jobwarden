package com.jobwarden.jobs.enums;

import java.math.BigDecimal;

/**
 * What a fit score says: apply, think twice, or let it go. "Apply better to fewer": the jobs worth an application
 * are the strong fits.
 */
public enum FitVerdict {

	/** 4.0 and up: every must-have met, or nearly. */
	STRONG,

	/** 3.0 to 3.9: something important is partly there, or missing. */
	PARTIAL,

	/** Under 3.0. */
	WEAK;

	public static FitVerdict of(BigDecimal score) {
		if (score.compareTo(BigDecimal.valueOf(4)) >= 0) {
			return STRONG;
		}
		return (score.compareTo(BigDecimal.valueOf(3)) >= 0) ? PARTIAL : WEAK;
	}

}
