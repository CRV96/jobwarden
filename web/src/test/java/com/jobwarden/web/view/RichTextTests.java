package com.jobwarden.web.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RichTextTests {

	private final RichText richText = new RichText(new Links());

	@Test
	void showsBoldAndItalics() {
		assertThat(this.richText.html("**Requirements** for a *senior* engineer"))
			.isEqualTo("<p><strong>Requirements</strong> for a <em>senior</em> engineer</p>");
	}

	@Test
	void keepsStarsThatAreNoFormatting() {
		assertThat(this.richText.html("5*3*2 stars, a ** b ** c, and * alone"))
			.isEqualTo("<p>5*3*2 stars, a ** b ** c, and * alone</p>");
	}

	@Test
	void makesParagraphsOfLinesAndListsOfPoints() {
		assertThat(this.richText.html("""
				About
				We build eCommerce.

				Requirements
				\t•\t4–6+ years of **Java**
				- Spring Framework
				* Hibernate
				1. Apply
				2) Talk to us
				Thanks"""))
			.isEqualTo("<p>About<br>We build eCommerce.</p><p>Requirements</p>"
					+ "<ul><li>4–6+ years of <strong>Java</strong></li><li>Spring Framework</li><li>Hibernate</li></ul>"
					+ "<ol><li>Apply</li><li>Talk to us</li></ol><p>Thanks</p>");
	}

	@Test
	void linksTheWebAddresses() {
		assertThat(this.richText.html("Apply at **https://acme.com/jobs?team=a&id=7**."))
			.isEqualTo("<p>Apply at <strong><a class=\"link break-all\" href=\"https://acme.com/jobs?team=a&amp;id=7\" "
					+ "rel=\"noopener noreferrer nofollow\" target=\"_blank\">https://acme.com/jobs?team=a&amp;id=7</a>"
					+ "</strong>.</p>");
	}

	@Test
	void writesEverythingElseAsText() {
		assertThat(this.richText.html("<script>alert(1)</script> <b onclick=\"x\">hi</b> & <a href=\"javascript:x\">y</a>"))
			.isEqualTo("<p>&lt;script&gt;alert(1)&lt;/script&gt; &lt;b onclick=\"x\"&gt;hi&lt;/b&gt; &amp; "
					+ "&lt;a href=\"javascript:x\"&gt;y&lt;/a&gt;</p>")
			.doesNotContain("<script", "<b ", "<a href=\"javascript");
		assertThat(this.richText.html("javascript:alert(1) **x**")).isEqualTo("<p>javascript:alert(1) <strong>x</strong></p>");
	}

	@Test
	void showsNothingForNoText() {
		assertThat(this.richText.html(null)).isEmpty();
		assertThat(this.richText.html(" \n ")).isEmpty();
	}

}
