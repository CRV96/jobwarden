package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EcbExchangeRatesTests {

	/** The start of the bank's daily file. */
	private static final String DAILY = """
			<?xml version="1.0" encoding="UTF-8"?>
			<gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01" xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref">
				<gesmes:subject>Reference rates</gesmes:subject>
				<Cube>
					<Cube time='2026-10-23'>
						<Cube currency='USD' rate='1.1612'/>
						<Cube currency='JPY' rate='171.85'/>
						<Cube currency='RON' rate='5.0789'/>
					</Cube>
				</Cube>
			</gesmes:Envelope>
			""";

	@Test
	void readsTheDayAndTheRates() {
		RateTable rates = EcbExchangeRates.parse(DAILY);

		assertThat(rates.date()).isEqualTo(LocalDate.of(2026, 10, 23));
		assertThat(rates.perEuro()).containsEntry("USD", new BigDecimal("1.1612"))
			.containsEntry("RON", new BigDecimal("5.0789"))
			.containsKey("EUR");
	}

	@Test
	void refusesAnAnswerWithoutRates() {
		assertThatIllegalArgumentException().isThrownBy(() -> EcbExchangeRates.parse("<html>Maintenance</html>"));
	}

	@Test
	void convertsThroughTheEuro() {
		RateTable rates = EcbExchangeRates.parse(DAILY);

		// 5078.90 RON is 1000 EUR, which is 1161.20 USD
		assertThat(rates.convert(new BigDecimal("5078.90"), "RON", "USD")).isEqualByComparingTo("1161.20");
	}

}
