package com.jobwarden.web.view;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChartScaleTests {

	@Test
	void stepsInRoundNumbersUpToTheLargestValue() {
		assertThat(ChartScale.upTo(new BigDecimal("143000")).ticks()).extracting(BigDecimal::intValue)
			.containsExactly(0, 50_000, 100_000, 150_000);
		// Steps are 1, 2, 2.5 or 5 times a power of ten: 40,000 isn't one
		assertThat(ChartScale.upTo(new BigDecimal("160000")).ticks()).extracting(BigDecimal::intValue)
			.containsExactly(0, 50_000, 100_000, 150_000, 200_000);
		assertThat(ChartScale.upTo(new BigDecimal("90000")).ticks()).extracting(BigDecimal::intValue)
			.containsExactly(0, 25_000, 50_000, 75_000, 100_000);
		assertThat(ChartScale.upTo(new BigDecimal("47.5")).ticks()).extracting(BigDecimal::doubleValue)
			.containsExactly(0.0, 20.0, 40.0, 60.0);
	}

	@Test
	void placesValuesAlongTheAxis() {
		ChartScale scale = ChartScale.upTo(new BigDecimal("150000"));

		assertThat(scale.percent(new BigDecimal("37500"))).isEqualTo(25.0);
		assertThat(scale.percent(new BigDecimal("200000"))).isEqualTo(100.0);
		assertThat(scale.steps()).isEqualTo(3);
	}

	@Test
	void hasAnAxisWithoutValuesToo() {
		assertThat(ChartScale.upTo(BigDecimal.ZERO).ticks()).extracting(BigDecimal::intValue).containsExactly(0, 1);
	}

}
