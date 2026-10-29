package com.jobwarden.market.service.impl;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SurveyCsvTests {

	@Test
	void readsQuotedFieldsWithCommasQuotesAndLineBreaks() throws IOException {
		SurveyCsv csv = new SurveyCsv(new StringReader("""
				ResponseId,Country,Comment\r
				1,"Iran, Islamic Republic of...","He said ""hi""
				and left"\r
				2,Romania,
				"""));

		assertThat(csv.read()).containsExactly("ResponseId", "Country", "Comment");
		assertThat(csv.read()).containsExactly("1", "Iran, Islamic Republic of...", "He said \"hi\"\nand left");
		assertThat(csv.read()).containsExactly("2", "Romania", "");
		assertThat(csv.read()).isNull();
	}

	@Test
	void readsAFieldLongerThanMostParsersAllow() throws IOException {
		String longAnswer = "x".repeat(200_000);
		SurveyCsv csv = new SurveyCsv(new StringReader("a,b\n\"" + longAnswer + "\",2\n"));

		csv.read();
		List<String> answer = csv.read();
		assertThat(answer).hasSize(2);
		assertThat(answer.getFirst()).hasSize(200_000);
	}

}
