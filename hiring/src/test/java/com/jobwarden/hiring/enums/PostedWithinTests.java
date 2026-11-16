package com.jobwarden.hiring.enums;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostedWithinTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 3, 31);

	@Test
	void countsBackFromToday() {
		assertThat(PostedWithin.DAY.since(TODAY)).isEqualTo(LocalDate.of(2026, 3, 30));
		assertThat(PostedWithin.WEEK.since(TODAY)).isEqualTo(LocalDate.of(2026, 3, 24));
		// A month back from the 31st is the last day February has
		assertThat(PostedWithin.MONTH.since(TODAY)).isEqualTo(LocalDate.of(2026, 2, 28));
	}

	@Test
	void anyTimeHasNoStart() {
		assertThat(PostedWithin.ANY.since(TODAY)).isNull();
	}

}
