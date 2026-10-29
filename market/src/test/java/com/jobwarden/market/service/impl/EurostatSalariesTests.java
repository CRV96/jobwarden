package com.jobwarden.market.service.impl;

import java.math.BigDecimal;
import java.net.URI;

import com.jobwarden.market.service.impl.EurostatSalaries.EarningsTable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EurostatSalariesTests {

	/** The statistics API's JSON-stat, shortened: every dimension but the country and the year has one value. */
	private static final String EARNINGS = """
			{"version": "2.0", "class": "dataset",
			 "id": ["freq", "sex", "indic_se", "isco08", "sizeclas", "nace_r2", "unit", "geo", "time"],
			 "size": [1, 1, 1, 1, 1, 1, 1, 4, 1],
			 "value": {"0": 57522, "1": 40037.4, "2": 30635, "3": 78189},
			 "dimension": {
			   "geo": {"category": {"index": {"EU27_2020": 0, "RO": 1, "EL": 2, "DE": 3}}},
			   "time": {"category": {"index": {"2022": 0}}}}}""";

	@Test
	void readsEachCountrysMeanEarnings() {
		EarningsTable table = EurostatSalaries.parse(JsonMapper.builder().build().readTree(EARNINGS));

		assertThat(table.year()).isEqualTo(2022);
		assertThat(table.byCountry()).containsOnlyKeys("RO", "GR", "DE");
		assertThat(table.byCountry().get("RO")).isEqualByComparingTo(new BigDecimal("40037"));
		assertThat(table.euAverage()).isEqualByComparingTo(new BigDecimal("57522"));
	}

	@Test
	void needsATableByCountry() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> EurostatSalaries.parse(JsonMapper.builder().build().readTree("{\"id\": [\"time\"], \"size\": [1]}")));
	}

	@Test
	void hasNothingWhenEurostatCantBeReached() {
		assertThat(new EurostatSalaries(URI.create("http://127.0.0.1:1/eurostat")).current()).isEmpty();
	}

}
