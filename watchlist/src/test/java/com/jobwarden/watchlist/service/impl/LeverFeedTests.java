package com.jobwarden.watchlist.service.impl;

import java.time.Duration;

import com.jobwarden.watchlist.enums.Ats;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class LeverFeedTests {

	private final LeverFeed feed = new LeverFeed();

	@Test
	void keepsTheCentsOfAnHourlyRange() {
		assertThat(salary("{\"currency\": \"USD\", \"interval\": \"per-hour-wage\", \"min\": 23.75, \"max\": 28.5}"))
			.isEqualTo("USD 23.75–28.50 per hour wage");
	}

	@Test
	void writesWholeAmountsWithoutCentsWhateverTheServersLanguage() {
		assertThat(salary("{\"currency\": \"EUR\", \"interval\": \"per-year-salary\", \"min\": 60000, \"max\": 80000}"))
			.isEqualTo("EUR 60,000–80,000 per year");
		assertThat(salary("{\"currency\": \"EUR\", \"interval\": \"per-year-salary\", \"min\": 70000.00, \"max\": 70000}"))
			.isEqualTo("EUR 70,000 per year");
	}

	@Test
	void readsAnAmountWrittenAsTextAndLeavesOutARangeWithout() {
		assertThat(salary("{\"currency\": \"GBP\", \"interval\": \"per-month-salary\", \"min\": \"4000\"}"))
			.isEqualTo("GBP 4,000 per month");
		assertThat(salary("{\"currency\": \"GBP\", \"min\": 0, \"max\": \"not an amount\"}")).isNull();
	}

	@Test
	void leavesOutAnAmountNoSalaryCouldBeQuicklyHoweverItsWritten() {
		// Written out, or rounded to cents, each would be a number of a billion digits
		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
			assertThat(salary("{\"currency\": \"USD\", \"min\": \"1E+1000000000\"}")).isNull();
			// Its digits before the point, counted in an int, would wrap below the limit
			assertThat(salary("{\"currency\": \"USD\", \"min\": \"1E+2147483647\"}")).isNull();
			assertThat(salary("{\"currency\": \"USD\", \"min\": \"1E-1000000000\"}")).isNull();
			assertThat(salary("{\"currency\": \"USD\", \"min\": 1e400}")).isNull();
			assertThat(salary("{\"currency\": \"USD\", \"min\": \"9999999999999\"}")).isNull();
		});
		assertThat(salary("{\"currency\": \"USD\", \"min\": \"999999999999\"}")).isEqualTo("USD 999,999,999,999");
	}

	private @Nullable String salary(String range) {
		Posting posting = this.feed.posting(JsonMapper.builder()
			.build()
			.readTree("{\"text\": \"Backend Developer\", \"hostedUrl\": \"https://jobs.lever.co/acme/1\", \"salaryRange\": "
					+ range + "}"), new Board(Ats.LEVER, "acme"));
		assertThat(posting).isNotNull();
		return posting.salary();
	}

}
