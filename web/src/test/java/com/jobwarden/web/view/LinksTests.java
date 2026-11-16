package com.jobwarden.web.view;

import com.jobwarden.web.view.Links.Part;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinksTests {

	private final Links links = new Links();

	@Test
	void partsTheWebAddressesFromTheRest() {
		assertThat(this.links.parts("Apply at https://jobs.lever.co/acme/7. Or <b>email</b> us & say hi")).containsExactly(
				new Part("Apply at ", false), new Part("https://jobs.lever.co/acme/7", true),
				new Part(". Or <b>email</b> us & say hi", false));
		assertThat(this.links.parts("https://acme.com")).containsExactly(new Part("https://acme.com", true));
	}

	@Test
	void keepsTheAddressesQueryAndABracketItOpens() {
		assertThat(this.links.parts("(see https://en.wikipedia.org/wiki/Java_(programming_language))")).containsExactly(
				new Part("(see ", false), new Part("https://en.wikipedia.org/wiki/Java_(programming_language)", true),
				new Part(")", false));
		assertThat(this.links.parts("https://acme.com/jobs?team=a&id=7"))
			.containsExactly(new Part("https://acme.com/jobs?team=a&id=7", true));
	}

	@Test
	void neverLinksAnythingButWebAddresses() {
		assertThat(this.links.parts("javascript:alert(1) and ftp://files")).noneMatch(Part::link);
		assertThat(this.links.parts("https://evil.com/\"onmouseover=\"x")).filteredOn(Part::link)
			.containsExactly(new Part("https://evil.com/", true));
		assertThat(this.links.parts(null)).isEmpty();
	}

}
