package com.jobwarden.watchlist.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlTextTests {

	@Test
	void keepsTheParagraphsAndListsOfAPostingsHtml() {
		assertThat(HtmlText.of("<div><p>We build <b>payments</b>.</p><ul><li>Java</li><li>Kafka<br>and Postgres</li></ul>"
				+ "<script>alert(1)</script></div>"))
			.isEqualTo("We build payments.\n\n• Java\n• Kafka\nand Postgres");
		assertThat(HtmlText.of("  ")).isNull();
		assertThat(HtmlText.of("<p> </p>")).isNull();
	}

	@Test
	void keepsAParagraphThatStartsAListItemOnTheBulletsLine() {
		// As Recruitee and Personio write their lists
		assertThat(HtmlText.of("<p>You bring:</p><ul><li><p>Java</p></li><li><p>Kafka</p><p>or Pulsar</p></li></ul>"))
			.isEqualTo("You bring:\n\n• Java\n• Kafka\n\nor Pulsar");
	}

}
