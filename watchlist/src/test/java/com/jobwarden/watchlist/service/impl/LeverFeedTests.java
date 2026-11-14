package com.jobwarden.watchlist.service.impl;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

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

	private String salary(String range) {
		Posting posting = this.feed.posting(JsonMapper.builder()
			.build()
			.readTree("{\"text\": \"Backend Developer\", \"hostedUrl\": \"https://jobs.lever.co/acme/1\", \"salaryRange\": "
					+ range + "}"));
		assertThat(posting).isNotNull();
		return posting.salary();
	}

}
