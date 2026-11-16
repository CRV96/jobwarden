package com.jobwarden.jobs.service.impl;

import com.jobwarden.jobs.enums.CsvProblem;
import com.jobwarden.jobs.service.impl.JobsCsvFormat.InvalidRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class JobsCsvFormatTests {

	/** A link as a sheet holds it, and as it's saved: a site's own, however its path is written, or one to complete. */
	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			https://jobs.lever.co/acme/1                     | https://jobs.lever.co/acme/1
			HTTP://Acme.example/Jobs                         | HTTP://Acme.example/Jobs
			https://acme.example/jobs?q=java developer^2     | https://acme.example/jobs?q=java developer^2
			https://localhost:8080/jobs/1                    | https://localhost:8080/jobs/1
			linkedin.com/jobs/view/1                         | https://linkedin.com/jobs/view/1
			""")
	void keepsAWebAddressWithItsHost(String cell, String saved) throws InvalidRow {
		assertThat(JobsCsvFormat.link(cell)).isEqualTo(saved);
	}

	/** The scheme alone, an address without a host or with a space in it, and what isn't a web address at all. */
	@ParameterizedTest
	@ValueSource(strings = { "https://", "http://?x", "https:///jobs/1", "https://acme example/1",
			"mailto:jobs@acme.example", "Acme careers page", "acme" })
	void refusesWhatIsNoWebAddress(String cell) {
		assertThatExceptionOfType(InvalidRow.class).isThrownBy(() -> JobsCsvFormat.link(cell))
			.satisfies(row -> assertThat(row.problem).isEqualTo(CsvProblem.BAD_LINK));
	}

	@Test
	void anEmptyCellHasNoLink() throws InvalidRow {
		assertThat(JobsCsvFormat.link(null)).isNull();
	}

}
