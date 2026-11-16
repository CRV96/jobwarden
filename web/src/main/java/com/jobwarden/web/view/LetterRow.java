package com.jobwarden.web.view;

import com.jobwarden.cv.dto.CoverLetter;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationStatus;
import org.jspecify.annotations.Nullable;

/**
 * One job on the Cover letters page: the job, its letter if it has one, Claude writing one, and how many people its
 * messages are to.
 *
 * @param letter the job's cover letter, or {@code null}
 * @param request the request for Claude to write it while it's open, or {@code null}
 * @param messages how many people the job's outreach drafts are to
 */
public record LetterRow(long jobId, String title, @Nullable String company, ApplicationStatus status,
		@Nullable CoverLetter letter, @Nullable AiRequest request, int messages) {

	public static LetterRow of(ApplicationSummary job, @Nullable CoverLetter letter, @Nullable AiRequest request,
			int messages) {
		return new LetterRow(job.id(), job.title(), job.company(), job.status(), letter, request, messages);
	}

	public static LetterRow of(ApplicationDetails job, @Nullable CoverLetter letter, @Nullable AiRequest request,
			int messages) {
		return new LetterRow(job.id(), job.title(), job.company(), job.status(), letter, request, messages);
	}

	public boolean hasLetter() {
		return this.letter != null;
	}

	/**
	 * @return whether Claude has a letter for it to write, or is writing it
	 */
	public boolean isWriting() {
		return this.request != null && this.request.status().isOpen();
	}

	/**
	 * @return whether it can be ticked, to ask Claude for its letter with others: it has none, and Claude isn't on it
	 */
	public boolean canAsk() {
		return !hasLetter() && !isWriting();
	}

}
