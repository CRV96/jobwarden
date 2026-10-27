package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.util.Set;

import com.jobwarden.jobs.enums.PayBasis;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.impl.SalaryParser.ParsedSalary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryParserTests {

	private final SalaryParser parser = new SalaryParser(RateTable.APPROXIMATE.currencies());

	/** What the extension saves from a JobPosting, in each language it speaks, and what sites show. */
	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			80,000–160,000 USD per year            | 80000  | 160000 | USD | YEAR
			5.000–7.000 EUR pe lună                 | 5000   | 7000   | EUR | MONTH
			5 000–7 000 PLN miesięcznie             | 5000   | 7000   | PLN | MONTH
			60.000–80.000 EUR pro Jahr              | 60000  | 80000  | EUR | YEAR
			45 000 EUR par an                       | 45000  | 45000  | EUR | YEAR
			$120K - $150K a year                    | 120000 | 150000 | USD | YEAR
			$50 - $75 an hour                       | 50     | 75     | USD | HOUR
			£45,000 - £55,000 per annum             | 45000  | 55000  | GBP | YEAR
			15 000–20 000 zł brutto / mies.         | 15000  | 20000  | PLN | MONTH
			CHF 120'000                             | 120000 | 120000 | CHF |
			€60–80k                                 | 60000  | 80000  | EUR |
			37,5 €/h                                | 37.5   | 37.5   | EUR | HOUR
			500 EUR per day                         | 500    | 500    | EUR | DAY
			100.000,00 - 120.000,00 €               | 100000 | 120000 | EUR |
			Up to $150,000                          | 150000 | 150000 | USD |
			6000 - 8k RON net                       | 6000   | 8000   | RON |
			8000 lei pe lună                        | 8000   | 8000   | RON | MONTH
			""")
	void readsTheAmountsCurrencyAndPeriod(String text, BigDecimal low, BigDecimal high, String currency,
			SalaryPeriod period) {
		ParsedSalary salary = this.parser.parse(text).orElseThrow();

		assertThat(salary.low()).isEqualByComparingTo(low);
		assertThat(salary.high()).isEqualByComparingTo(high);
		assertThat(salary.currency()).isEqualTo(currency);
		assertThat(salary.period()).isEqualTo(period);
	}

	@Test
	void readsWhetherItIsBeforeOrAfterTax() {
		assertThat(this.parser.parse("8000 RON net pe lună").orElseThrow().basis()).isEqualTo(PayBasis.NET);
		assertThat(this.parser.parse("10 000 zł brutto").orElseThrow().basis()).isEqualTo(PayBasis.GROSS);
		assertThat(this.parser.parse("5000 EUR").orElseThrow().basis()).isNull();
	}

	@Test
	void leavesOutPercentagesAndOrdinals() {
		ParsedSalary salary = this.parser.parse("5000 EUR + 10% bonus, 13th salary").orElseThrow();

		assertThat(salary.low()).isEqualByComparingTo("5000");
		assertThat(salary.high()).isEqualByComparingTo("5000");
	}

	@Test
	void ordersAReversedRange() {
		ParsedSalary salary = this.parser.parse("7000 - 5000 EUR").orElseThrow();

		assertThat(salary.low()).isEqualByComparingTo("5000");
		assertThat(salary.high()).isEqualByComparingTo("7000");
	}

	@Test
	void readsCurrencyCodesInCapitalsOnlyWhenTheyAreAlsoWords() {
		// TRY is the Turkish lira; "try" is a word
		assertThat(this.parser.parse("try 5000")).isEmpty();
		assertThat(this.parser.parse("5000 TRY")).isPresent();
		assertThat(this.parser.parse("5000 eur")).isPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Competitive", "Negotiable, depending on experience", "15000", "  " })
	void readsNothingWithoutAnAmountAndACurrency(String text) {
		assertThat(this.parser.parse(text)).isEmpty();
	}

	@Test
	void readsNothingInACurrencyWithoutARate() {
		SalaryParser euroOnly = new SalaryParser(Set.of("EUR"));

		assertThat(euroOnly.parse("5000 USD")).isEmpty();
		assertThat(euroOnly.parse("5000 EUR")).isPresent();
	}

}
