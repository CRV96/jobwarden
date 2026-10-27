package com.jobwarden.web.view;

import java.math.BigDecimal;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.context.i18n.LocaleContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTests {

	private final Money money = new Money();

	@AfterEach
	void resetLanguage() {
		LocaleContextHolder.resetLocaleContext();
	}

	@Test
	void shortensAmountsForCharts() {
		LocaleContextHolder.setLocale(Locale.ENGLISH);

		assertThat(this.money.compact(new BigDecimal("85432"), "EUR")).isEqualTo("€85K");
		assertThat(this.money.compact(new BigDecimal("4500"), "EUR")).isEqualTo("€4.5K");
		assertThat(this.money.compact(new BigDecimal("37.50"), "USD")).isEqualTo("$37.5");
		assertThat(this.money.compact(new BigDecimal("1250000"), "GBP")).isEqualTo("£1.3M");
	}

	@Test
	void putsTheSymbolWhereTheLanguageDoes() {
		LocaleContextHolder.setLocale(Locale.ENGLISH);
		assertThat(this.money.compact(new BigDecimal("15000"), "RON")).isEqualTo("RON 15K");

		LocaleContextHolder.setLocale(Locale.GERMAN);
		assertThat(this.money.compact(new BigDecimal("150"), "EUR")).isEqualTo("150 €");
	}

	@Test
	void showsARangeOrOneAmount() {
		LocaleContextHolder.setLocale(Locale.ENGLISH);

		assertThat(this.money.range(new BigDecimal("40000"), new BigDecimal("80000"), "EUR"))
			.isEqualTo("€40K – €80K");
		assertThat(this.money.range(new BigDecimal("40000"), new BigDecimal("40000"), "EUR")).isEqualTo("€40K");
	}

}
