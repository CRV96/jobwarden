package com.jobwarden.web.view;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jobwarden.jobs.dto.ApplicationDetails;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * What sharing a job sends, as the templates use it: {@code ${@sharing.text(jobApplication)}}. It shares the posting,
 * not JobWarden's page: the app runs on your computer, where nobody else can open it.
 */
@Component("sharing")
@RequiredArgsConstructor
public class Sharing {

	private final MessageSource messages;

	/**
	 * Everything the share menu needs, at once: Thymeleaf only lets th:with call beans, not the attributes that use
	 * the result.
	 */
	public Share of(ApplicationDetails job) {
		return new Share(title(job), summary(job), text(job), link(job), email(job), whatsApp(job), linkedIn(job), x(job));
	}

	/**
	 * @return "Backend engineer at Acme", or the title alone without a company
	 */
	public String title(ApplicationDetails job) {
		return (job.company() == null) ? job.title()
				: this.messages.getMessage("share.title", new Object[] { job.title(), job.company() },
						LocaleContextHolder.getLocale());
	}

	/**
	 * The title, then the location and the salary, then the posting's link, each line only when there's something to
	 * say.
	 */
	public String text(ApplicationDetails job) {
		return joined("\n", summary(job), link(job));
	}

	/**
	 * Like {@link #text}, without the link: for a share sheet, which takes the link on its own.
	 */
	public String summary(ApplicationDetails job) {
		return joined("\n", title(job), joined(" · ", job.location(), job.salary()));
	}

	/**
	 * @return the posting's address, only when it's on the web: a link captured from a page could be anything
	 */
	public @Nullable String link(ApplicationDetails job) {
		String link = job.link();
		return (link != null && (link.startsWith("https://") || link.startsWith("http://"))) ? link : null;
	}

	public String email(ApplicationDetails job) {
		return "mailto:?subject=" + encoded(title(job)) + "&body=" + encoded(text(job));
	}

	public String whatsApp(ApplicationDetails job) {
		return "https://wa.me/?text=" + encoded(text(job));
	}

	/**
	 * @return LinkedIn's share page for the posting; {@code null} without a link, as LinkedIn shares only addresses
	 */
	public @Nullable String linkedIn(ApplicationDetails job) {
		String link = link(job);
		return (link == null) ? null : "https://www.linkedin.com/sharing/share-offsite/?url=" + encoded(link);
	}

	public String x(ApplicationDetails job) {
		String link = link(job);
		return "https://x.com/intent/tweet?text=" + encoded(title(job)) + ((link == null) ? "" : "&url=" + encoded(link));
	}

	/** The parts that have something to say, joined. */
	private static String joined(String separator, @Nullable String... parts) {
		return Stream.of(parts).filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(separator));
	}

	/** Spaces as %20, which mail apps read; + would stay a plus. */
	private static String encoded(String text) {
		return UriUtils.encode(text, StandardCharsets.UTF_8);
	}

	/**
	 * What sharing a job sends: see the methods of the same names.
	 */
	public record Share(String title, String summary, String text, @Nullable String link, String email,
			String whatsApp, @Nullable String linkedIn, String x) {
	}

}
