package com.jobwarden.market.service.impl;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.jobwarden.market.service.impl.StackOverflowSurvey.SurveyFigure;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

class StackOverflowSurveyTests {

	private static final String HEADER = "ResponseId,Employment,DevType,Country,LanguageHaveWorkedWith,ConvertedCompYearly\n";

	@Test
	void sumsUpTheAnswersPerCountryRoleAndLanguageAndForAllOfThem() throws IOException {
		// Five back-end developers in Romania who use Java, earning 40,000 to 80,000 dollars
		String answers = IntStream.range(0, 5)
			.mapToObj(i -> i + ",Employed,\"Developer, back-end\",Romania,Java;SQL," + (40_000 + i * 10_000))
			.collect(Collectors.joining("\n"));

		List<SurveyFigure> figures = StackOverflowSurvey.read(new StringReader(HEADER + answers + "\n"));

		assertThat(figures).filteredOn(figure -> figure.country().equals("RO") && figure.role().equals("Developer, back-end")
				&& figure.skill().equals("Java"))
			.singleElement()
			.satisfies(figure -> {
				assertThat(figure.sampleSize()).isEqualTo(5);
				assertThat(figure.lowerQuartile()).isEqualByComparingTo(new BigDecimal("50000"));
				assertThat(figure.median()).isEqualByComparingTo(new BigDecimal("60000"));
				assertThat(figure.upperQuartile()).isEqualByComparingTo(new BigDecimal("70000"));
			});
		// Every country, role and language together, and each on its own
		assertThat(figures).extracting(figure -> figure.country() + "|" + figure.role() + "|" + figure.skill())
			.contains("RO||", "||", "|Developer, back-end|", "||Java", "||SQL", "RO|Developer, back-end|SQL");
	}

	@Test
	void leavesOutWhatSaysNothingOrToolittle() throws IOException {
		String answers = String.join("\n",
				// Not in work, no salary, a salary out of range, or a group too small to show
				"1,Student,\"Developer, back-end\",Romania,Java,30000",
				"2,Employed,\"Developer, back-end\",Romania,Java,NA",
				"3,Employed,\"Developer, back-end\",Romania,Java,50000000",
				"4,Employed,\"Developer, back-end\",Romania,Java,50000",
				"5,\"Independent contractor, freelancer, or self-employed\",NA,Atlantis,NA,60000");

		List<SurveyFigure> figures = StackOverflowSurvey.read(new StringReader(HEADER + answers + "\n"));

		assertThat(figures).isEmpty();
	}

	@Test
	void namesTheCountriesTheWayIsoDoes() throws IOException {
		String answers = IntStream.range(0, 5)
			.mapToObj(i -> i + ",Employed,NA,United Kingdom of Great Britain and Northern Ireland,NA,90000")
			.collect(Collectors.joining("\n"));

		assertThat(StackOverflowSurvey.read(new StringReader(HEADER + answers)))
			.extracting(SurveyFigure::country)
			.containsExactlyInAnyOrder("GB", "");
	}

	@Test
	void needsTheColumnsItReads() {
		assertThatIOException().isThrownBy(() -> StackOverflowSurvey.read(new StringReader("ResponseId,Country\n1,Romania\n")))
			.withMessageContaining("DevType");
	}

}
