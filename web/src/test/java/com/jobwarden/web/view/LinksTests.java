package com.jobwarden.web.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinksTests {

	private final Links links = new Links();

	@Test
	void makesTheWebAddressesLinksAndEscapesTheRest() {
		assertThat(this.links.html("Apply at https://jobs.lever.co/acme/7. Or <b>email</b> us & say hi"))
			.isEqualTo("Apply at <a class=\"link break-all\" href=\"https://jobs.lever.co/acme/7\" target=\"_blank\" "
					+ "rel=\"noopener noreferrer nofollow\">https://jobs.lever.co/acme/7</a>. Or &lt;b&gt;email&lt;/b&gt; us "
					+ "&amp; say hi");
	}

	@Test
	void keepsTheAddressesQueryAndABracketItOpens() {
		assertThat(this.links.html("(see https://en.wikipedia.org/wiki/Java_(programming_language))"))
			.contains("href=\"https://en.wikipedia.org/wiki/Java_(programming_language)\"")
			.endsWith("</a>)");
		assertThat(this.links.html("https://acme.com/jobs?team=a&id=7")).contains("href=\"https://acme.com/jobs?team=a&amp;id=7\"");
	}

	@Test
	void neverLinksAnythingButWebAddresses() {
		assertThat(this.links.html("javascript:alert(1) and ftp://files")).doesNotContain("<a");
		assertThat(this.links.html("https://evil.com/\"onmouseover=\"x")).doesNotContain("\"onmouseover");
		assertThat(this.links.html(null)).isEmpty();
	}

}
