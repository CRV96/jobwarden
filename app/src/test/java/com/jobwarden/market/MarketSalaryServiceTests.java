package com.jobwarden.market;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.ExchangeRateService;
import com.jobwarden.market.dto.MarketCountry;
import com.jobwarden.market.dto.MarketQuery;
import com.jobwarden.market.dto.MarketSalaries;
import com.jobwarden.market.enums.MarketSource;
import com.jobwarden.market.service.MarketSalaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * The market module on its own, with a small survey file in the place of Stack Overflow's, no Adzuna key, and Eurostat
 * out of reach. The exchange rates come from the jobs module, here one to one.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class MarketSalaryServiceTests {

	@Autowired
	private MarketSalaryService market;

	@Autowired
	private JdbcClient jdbc;

	@MockitoBean
	private ExchangeRateService exchangeRates;

	@DynamicPropertySource
	static void sources(DynamicPropertyRegistry properties) {
		properties.add("jobwarden.market.stack-overflow.url", () -> {
			try {
				return new ClassPathResource("market/survey.csv").getURI().toString();
			} catch (IOException ex) {
				throw new IllegalStateException(ex);
			}
		});
		properties.add("jobwarden.market.eurostat.url", () -> "http://127.0.0.1:1/eurostat");
		properties.add("jobwarden.market.adzuna.app-id", () -> "");
		properties.add("jobwarden.market.adzuna.app-key", () -> "");
	}

	@BeforeEach
	void oneToOneRates() {
		this.jdbc.sql("DELETE FROM market_salaries").update();
		// AdzunaKeyServiceTests saves keys in the same database
		this.jdbc.sql("DELETE FROM market_adzuna_key").update();
		given(this.exchangeRates.knows(anyString())).willReturn(true);
		given(this.exchangeRates.convert(any(), anyString(), anyString())).willAnswer(call -> call.getArgument(0));
	}

	@Test
	void offersCommonRolesAndSkillsBeforeTheSurveyIsIn() {
		assertThat(this.market.surveyStatus().imported()).isFalse();
		assertThat(this.market.roles()).contains("Developer, back-end");
		assertThat(this.market.skills()).contains("Java");
		assertThat(this.market.surveyCountries()).isEmpty();
	}

	@Test
	void importsTheSurveyAndComparesTheCountries() throws InterruptedException {
		assertThat(this.market.importSurvey()).isTrue();
		awaitImport();

		assertThat(this.market.surveyStatus()).satisfies(status -> {
			assertThat(status.imported()).isTrue();
			assertThat(status.year()).isEqualTo(2025);
			assertThat(status.error()).isNull();
		});
		// The most answered first
		assertThat(this.market.roles()).containsExactly("Developer, back-end", "Developer, front-end");
		assertThat(this.market.skills()).startsWith("Java");
		assertThat(this.market.surveyCountries()).containsExactly("DE", "RO");

		MarketSalaries salaries = this.market.find(new MarketQuery("Developer, back-end", "Java", List.of("ro", "DE", "FR")),
				"USD", SalaryPeriod.MONTH);

		assertThat(salaries.countries()).extracting(MarketCountry::country).containsExactly("RO", "DE", "FR", "");
		assertThat(salaries.countries().getFirst().from(MarketSource.STACK_OVERFLOW)).hasValueSatisfying(figure -> {
			// 40,000 to 80,000 dollars a year: a median of 60,000, 5,000 a month
			assertThat(figure.middle()).isEqualByComparingTo(new BigDecimal("5000"));
			assertThat(figure.lowerQuartile()).isEqualByComparingTo(new BigDecimal("4166.67"));
			assertThat(figure.sampleSize()).isEqualTo(5);
			assertThat(figure.year()).isEqualTo(2025);
		});
		// No answers from France, no Adzuna key, and Eurostat out of reach
		assertThat(salaries.countries().get(2).figures()).isEmpty();
		assertThat(salaries.adzunaConfigured()).isFalse();
		assertThat(salaries.adzunaSearch()).isEqualTo("java developer");
		assertThat(salaries.eurostatYear()).isNull();
		// All countries together: the ten back-end Java developers
		assertThat(salaries.countries().getLast().from(MarketSource.STACK_OVERFLOW))
			.hasValueSatisfying(figure -> assertThat(figure.sampleSize()).isEqualTo(10));
	}

	@Test
	void importsOnceAtATime() throws InterruptedException {
		assertThat(this.market.importSurvey()).isTrue();
		assertThat(this.market.importSurvey()).isFalse();
		awaitImport();
		// Again, it replaces the figures instead of adding to them
		assertThat(this.market.importSurvey()).isTrue();
		awaitImport();
		assertThat(this.jdbc.sql("SELECT count(*) FROM market_salaries WHERE country = 'RO' AND role = '' AND skill = ''")
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	private void awaitImport() throws InterruptedException {
		Instant giveUp = Instant.now().plus(Duration.ofSeconds(20));
		while (this.market.surveyStatus().importing() && Instant.now().isBefore(giveUp)) {
			Thread.sleep(50);
		}
		assertThat(this.market.surveyStatus().importing()).isFalse();
	}

}
