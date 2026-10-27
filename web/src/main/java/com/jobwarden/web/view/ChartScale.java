package com.jobwarden.web.view;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * The axis of a chart that starts at zero: round ticks at even steps, the last one at or above the largest value.
 * The templates place marks with {@link #percent}.
 *
 * @param ticks from zero to the end of the axis
 */
public record ChartScale(List<BigDecimal> ticks) {

	/** About how many steps the axis has. */
	private static final int STEPS = 4;

	public ChartScale {
		ticks = List.copyOf(ticks);
	}

	/**
	 * @param largest the largest value the chart shows; zero or less for an empty chart
	 */
	public static ChartScale upTo(BigDecimal largest) {
		BigDecimal step = (largest.signum() > 0) ? roundStep(largest.divide(BigDecimal.valueOf(STEPS), MathContext.DECIMAL64))
				: BigDecimal.ONE;
		List<BigDecimal> ticks = new ArrayList<>(List.of(BigDecimal.ZERO));
		while (ticks.getLast().compareTo(largest) < 0 || ticks.size() < 2) {
			ticks.add(ticks.getLast().add(step));
		}
		return new ChartScale(ticks);
	}

	public BigDecimal max() {
		return this.ticks.getLast();
	}

	/**
	 * @return how many steps the axis has, for the gridlines
	 */
	public int steps() {
		return this.ticks.size() - 1;
	}

	/**
	 * @return where the value is along the axis, from 0 to 100
	 */
	public double percent(BigDecimal value) {
		double percent = value.divide(max(), MathContext.DECIMAL64).doubleValue() * 100;
		return Math.clamp(percent, 0, 100);
	}

	/** 1, 2, 2.5 or 5 times a power of ten, the smallest at least as big as the raw step. */
	private static BigDecimal roundStep(BigDecimal raw) {
		BigDecimal power = BigDecimal.TEN.pow(raw.precision() - raw.scale() - 1, MathContext.DECIMAL64);
		for (String multiple : List.of("1", "2", "2.5", "5", "10")) {
			BigDecimal step = power.multiply(new BigDecimal(multiple));
			if (step.compareTo(raw) >= 0) {
				return step.stripTrailingZeros();
			}
		}
		throw new IllegalStateException("No step for " + raw);
	}

}
