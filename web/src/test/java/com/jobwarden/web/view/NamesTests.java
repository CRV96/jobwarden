package com.jobwarden.web.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamesTests {

	private final Names names = new Names();

	@Test
	void takesTheFirstLettersOfTheFirstTwoWords() {
		assertThat(this.names.initials("Robert Vasile Ciapa")).isEqualTo("RV");
		assertThat(this.names.initials("  robert  ")).isEqualTo("R");
		assertThat(this.names.initials("Ștefan ălexandru")).isEqualTo("ȘĂ");
	}

}
