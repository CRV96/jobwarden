package com.jobwarden.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WordsTests {

	@Test
	void comparesWordsWithoutCaseAccentsOrPunctuation() {
		assertThat(Words.of("Full-Stack Developer (m/w/d), Zürich")).isEqualTo(" full stack developer m w d zurich ");
		assertThat(Words.of("Łódź, Kraków; Straße, Ålesund")).isEqualTo(" lodz krakow strasse alesund ");
		assertThat(Words.of(null)).isEqualTo(" ");
		assertThat(Words.of("  ")).isEqualTo(" ");
	}

	@Test
	void findsWholeWordsOnly() {
		String title = Words.of("Senior Java Engineer, Payments");

		assertThat(Words.has(title, "java")).isTrue();
		assertThat(Words.has(title, "JAVA engineer")).isTrue();
		assertThat(Words.has(title, "engineer java")).isFalse();
		assertThat(Words.has(title, "Jav")).isFalse();
		assertThat(Words.has(Words.of("JavaScript Developer"), "Java")).isFalse();
		assertThat(Words.has(title, " - ")).isFalse();
	}

}
