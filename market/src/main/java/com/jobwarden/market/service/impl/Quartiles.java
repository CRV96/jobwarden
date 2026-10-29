package com.jobwarden.market.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * The quartiles of some amounts: a quarter of them are below the lower one, half below the median, three quarters
 * below the upper one. Between two amounts it interpolates, like the salaries page does with the profile's own.
 */
record Quartiles(BigDecimal lower, BigDecimal median, BigDecimal upper) {

	/**
	 * @param amounts at least one; sorted here, in place
	 */
	static Quartiles of(double[] amounts) {
		Arrays.sort(amounts);
		return new Quartiles(at(amounts, 0.25), at(amounts, 0.5), at(amounts, 0.75));
	}

	private static BigDecimal at(double[] sorted, double fraction) {
		double position = fraction * (sorted.length - 1);
		int below = (int) Math.floor(position);
		int above = (int) Math.ceil(position);
		double value = sorted[below] + (sorted[above] - sorted[below]) * (position - below);
		return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP);
	}

}
