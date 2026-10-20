package com.jobwarden.web.view;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;

class DatesTests {

	private final Dates dates = new Dates(messages());

	@AfterEach
	void resetLocale() {
		LocaleContextHolder.resetLocaleContext();
	}

	@Test
	void showsRecentDaysRelativeToToday() {
		LocaleContextHolder.setLocale(Locale.ENGLISH);

		assertThat(this.dates.relativeDay(daysAgo(0))).isEqualTo("Today");
		assertThat(this.dates.relativeDay(daysAgo(1))).isEqualTo("Yesterday");
		assertThat(this.dates.relativeDay(daysAgo(6))).isEqualTo("6 days ago");
	}

	@Test
	void showsOlderDaysAsTheirDateInTheUsersLanguage() {
		LocaleContextHolder.setLocale(Locale.forLanguageTag("ro"));
		LocalDate weekAgo = LocalDate.now().minusDays(7);

		assertThat(this.dates.relativeDay(daysAgo(7))).isEqualTo(this.dates.date(daysAgo(7)))
			.startsWith(String.valueOf(weekAgo.getDayOfMonth()))
			.endsWith(String.valueOf(weekAgo.getYear()));
		assertThat(this.dates.relativeDay(daysAgo(2))).isEqualTo("acum 2 zile");
	}

	private static java.time.Instant daysAgo(int days) {
		return LocalDate.now().minusDays(days).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant();
	}

	private static ResourceBundleMessageSource messages() {
		ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
		messages.setBasename("i18n/web");
		messages.setDefaultEncoding("UTF-8");
		messages.setFallbackToSystemLocale(false);
		return messages;
	}

}
