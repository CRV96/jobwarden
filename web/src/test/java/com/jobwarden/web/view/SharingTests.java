package com.jobwarden.web.view;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.web.view.Sharing.Share;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.context.support.StaticMessageSource;

import static org.assertj.core.api.Assertions.assertThat;

class SharingTests {

	private final Sharing sharing = new Sharing(messages());

	@Test
	void sharesThePostingWithItsDetails() {
		Share share = this.sharing.of(job("https://jobs.lever.co/acme/42", "Acme"));

		assertThat(share.title()).isEqualTo("Backend engineer at Acme");
		assertThat(share.text()).isEqualTo("Backend engineer at Acme\nRemote · €5,000 a month\nhttps://jobs.lever.co/acme/42");
		assertThat(share.summary()).doesNotContain("https://");
		// Spaces as %20, which mail apps read
		assertThat(share.email()).startsWith("mailto:?subject=Backend%20engineer%20at%20Acme&body=Backend%20engineer")
			.contains("%0ARemote%20%C2%B7%20%E2%82%AC5%2C000");
		assertThat(share.linkedIn())
			.isEqualTo("https://www.linkedin.com/sharing/share-offsite/?url=https%3A%2F%2Fjobs.lever.co%2Facme%2F42");
		assertThat(share.x()).endsWith("&url=https%3A%2F%2Fjobs.lever.co%2Facme%2F42");
	}

	@Test
	void sharesAJobWithoutALinkOrACompanyToo() {
		Share share = this.sharing.of(job(null, null));

		assertThat(share.title()).isEqualTo("Backend engineer");
		assertThat(share.link()).isNull();
		assertThat(share.linkedIn()).isNull();
		assertThat(share.x()).doesNotContain("&url=");
	}

	@Test
	void sharesOnlyLinksOnTheWeb() {
		assertThat(this.sharing.of(job("javascript:alert(1)", "Acme")).link()).isNull();
	}

	private static ApplicationDetails job(@Nullable String link, @Nullable String company) {
		return ApplicationDetails.builder()
			.id(7)
			.title("Backend engineer")
			.company(company)
			.link(link)
			.location("Remote")
			.salary("€5,000 a month")
			.status(ApplicationStatus.APPLIED)
			.capturedAt(Instant.parse("2026-10-20T10:00:00Z"))
			.timeline(List.of())
			.build();
	}

	private static StaticMessageSource messages() {
		StaticMessageSource messages = new StaticMessageSource();
		messages.addMessage("share.title", Locale.getDefault(), "{0} at {1}");
		return messages;
	}

}
