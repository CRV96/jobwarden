package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.jobwarden.cv.dto.CoverLetter;
import com.jobwarden.cv.dto.OutreachDraft;
import com.jobwarden.cv.service.CoverLetterService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.string;

/**
 * What the profile sends about a job besides its CV: the cover letter, which the job's page shows and downloads as a
 * PDF, and messages to the people who hire for it, a LinkedIn note and an email, which the profile sends itself.
 * JobWarden sends nothing, and neither does Claude.
 */
@Component
class LetterTools implements McpToolSet {

	private final Profiles profiles;

	private final CoverLetterService letters;

	private final ApplicationService applications;

	/** Where the web module serves a job's cover letter as a PDF for Claude, e.g. /claude/cover-letter?profile=1&job=12 */
	private final String letterPath;

	LetterTools(Profiles profiles, CoverLetterService letters, ApplicationService applications,
			@Value("${jobwarden.claude.cover-letter-path:/claude/cover-letter}") String letterPath) {
		this.profiles = profiles;
		this.letters = letters;
		this.applications = applications;
		this.letterPath = letterPath;
	}

	@Override
	public List<McpTool> tools() {
		return List.of(getCoverLetter(), saveCoverLetter(), saveOutreachDraft());
	}

	private McpTool getCoverLetter() {
		return new McpTool("get_cover_letter", "Get a job's cover letter",
				"The cover letter kept for one of the profile's jobs, as the profile last left it: its text, to paste into "
						+ "a form's field, and the address of its PDF, to download and upload. has_letter is false when "
						+ "the job has none; then base_letter, when there is one, is the person's own letter filled in for "
						+ "the job, to start one from.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer("The job, from get_request, list_jobs or list_apply_queue")),
						"job_id"),
				true, false, call -> {
					long userId = this.profiles.of(call);
					ApplicationDetails job = requireJob(userId, call.requiredNumber("job_id"));
					long jobId = job.id();
					return this.letters.findLetter(userId, jobId)
						.map(letter -> new LetterView(jobId, true, letter.body(), pdfUrl(userId, jobId), letter.updatedAt(),
								null))
						.orElseGet(() -> new LetterView(jobId, false, null, null, null, this.letters.findBaseLetter(userId)
							.map(base -> base.fillIn(job.company(), job.title()))
							.orElse(null)));
				});
	}

	private McpTool saveCoverLetter() {
		return new McpTool("save_cover_letter", "Save a cover letter",
				"Keeps the cover letter you wrote for one of the profile's jobs, replacing the one it had. The job's page "
						+ "shows it, for the profile to change, and downloads it as a PDF with the CV page's name and "
						+ "contact details at the top: leave them out of the letter. Only what the CV supports.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer("The job it's for, from get_request"),
						"body", string("The letter, from the greeting (Dear Ana Pop, or Dear hiring team,) to the sign-off "
								+ "(Kind regards,), paragraphs apart by a blank line; without the name under it, at most "
								+ CoverLetter.BODY_MAX_LENGTH + " characters"),
						"notes", string("What you highlighted, and what to check before sending it, in a few lines")),
						"job_id", "body"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					ApplicationDetails job = requireJob(userId, call.requiredNumber("job_id"));
					CoverLetter saved = this.letters.saveLetter(userId, job.id(), call.requiredString("body"),
							call.string("notes"));
					return Map.of("job_id", job.id(), "paragraphs", saved.paragraphs().size(), "pdf",
							pdfUrl(userId, job.id()), "page", "/applications/" + job.id());
				});
	}

	private McpTool saveOutreachDraft() {
		return new McpTool("save_outreach_draft", "Save messages to someone hiring",
				"Keeps what you wrote to one person about one of the profile's jobs: a LinkedIn connection request's note, "
						+ "at most " + OutreachDraft.LINKEDIN_NOTE_MAX_LENGTH + " characters, and an email. They replace "
						+ "what was written to the same person for the job. The job's page shows them for the profile to "
						+ "change and send itself: never send them.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer("The job it's about, from get_request"),
						"name", string("Whom it's to"),
						"role", string("What they do, e.g. Technical Recruiter at Acme"),
						"profile_link", string("Their profile, e.g. on LinkedIn"),
						"email", string("Their email address, when known"),
						"linkedin_note", string("The note, at most " + OutreachDraft.LINKEDIN_NOTE_MAX_LENGTH
								+ " characters, spaces included"),
						"email_subject", string("The email's subject"),
						"email_body", string("The email, from the greeting to the sign-off with the person's name")),
						"job_id", "name"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					ApplicationDetails job = requireJob(userId, call.requiredNumber("job_id"));
					OutreachDraft saved = this.letters.saveDraft(userId, OutreachDraft.builder()
						.applicationId(job.id())
						.recipientName(call.requiredString("name"))
						.recipientRole(call.string("role"))
						.recipientLink(call.string("profile_link"))
						.recipientEmail(call.string("email"))
						.linkedinNote(call.string("linkedin_note"))
						.emailSubject(call.string("email_subject"))
						.emailBody(call.string("email_body"))
						.build());
					return Map.of("job_id", job.id(), "draft_id", saved.id(), "note_characters",
							saved.linkedinNoteLength(), "page", "/applications/" + job.id());
				});
	}

	private ApplicationDetails requireJob(long userId, long jobId) {
		return this.applications.findById(userId, jobId).orElseThrow(() -> new ApplicationNotFoundException(jobId));
	}

	private String pdfUrl(long userId, long jobId) {
		return ServletUriComponentsBuilder.fromCurrentContextPath()
			.path(this.letterPath)
			.queryParam("profile", userId)
			.queryParam("job", jobId)
			.toUriString();
	}

	/**
	 * @param hasLetter whether the job has one
	 * @param pdfUrl the letter as a PDF, to download and upload
	 * @param baseLetter without a letter, the profile's base letter filled in for the job, to start one from
	 */
	record LetterView(long jobId, boolean hasLetter, @Nullable String body, @Nullable String pdfUrl,
			@Nullable Instant updatedAt, @Nullable String baseLetter) {
	}

}
