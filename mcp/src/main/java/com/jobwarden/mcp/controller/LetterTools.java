package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.jobwarden.cv.dto.CoverLetter;
import com.jobwarden.cv.dto.OutreachDraft;
import com.jobwarden.cv.service.CoverLetterService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
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
 * JobWarden sends nothing, and neither does the assistant.
 */
@Component
class LetterTools implements McpToolSet {

	private final Profiles profiles;

	private final CoverLetterService letters;

	private final ApplicationService applications;

	private final DiscoveryService discovery;

	/** Where the web module serves a job's cover letter as a PDF for the assistant, e.g. /claude/cover-letter?profile=1&job=12 */
	private final String letterPath;

	LetterTools(Profiles profiles, CoverLetterService letters, ApplicationService applications, DiscoveryService discovery,
			@Value("${jobwarden.claude.cover-letter-path:/claude/cover-letter}") String letterPath) {
		this.profiles = profiles;
		this.letters = letters;
		this.applications = applications;
		this.discovery = discovery;
		this.letterPath = letterPath;
	}

	@Override
	public List<McpTool> tools() {
		return List.of(getCoverLetter(), saveCoverLetter(), saveOutreachDraft());
	}

	private McpTool getCoverLetter() {
		return new McpTool("get_cover_letter", "Get a job's cover letter",
				"A job's cover letter: its text, and its PDF's URL to upload. Without one (has_letter false), "
						+ "base_letter is the person's own letter filled in for the job, if any.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(), "job_id", integer()), "job_id"),
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
				"Saves a job's cover letter, replacing its previous one. Its PDF puts the CV page's name and contact "
						+ "details on top: leave them out. Only what the CV supports.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer(),
						"body", string("From the greeting (Dear Ana Pop, or Dear hiring team,) to the sign-off (Kind "
								+ "regards,) without the name; a blank line between paragraphs; max "
								+ CoverLetter.BODY_MAX_LENGTH + " characters"),
						"notes", string("What you highlighted and what to check, in a few lines")),
						"job_id", "body"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					ApplicationDetails job = requireJob(userId, call.requiredNumber("job_id"));
					Optional<AiRequest> asked = latest(userId, AiRequestKind.WRITE_COVER_LETTER, job.id());
					if (isTakenBack(asked) && this.letters.findLetter(userId, job.id()).isPresent()) {
						throw new ToolException("The person took the request for this letter back: the letter the job has "
								+ "stays as it is. Don't save it again.");
					}
					CoverLetter saved = this.letters.saveLetter(userId, job.id(), call.requiredString("body"),
							call.string("notes"), askedAt(asked));
					return Map.of("job_id", job.id(), "paragraphs", saved.paragraphs().size(), "pdf",
							pdfUrl(userId, job.id()), "page", "/applications/" + job.id());
				});
	}

	private McpTool saveOutreachDraft() {
		return new McpTool("save_outreach_draft", "Save messages to someone hiring",
				"Saves messages to one person about a job: a LinkedIn connection note and an email, replacing earlier "
						+ "ones to them. The profile sends them itself: never send them.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer(),
						"name", string("The recipient"),
						"role", string("e.g. Technical Recruiter at Acme"),
						"profile_link", string(),
						"email", string(),
						"linkedin_note", string("Max " + OutreachDraft.LINKEDIN_NOTE_MAX_LENGTH + " characters with spaces"),
						"email_subject", string(),
						"email_body", string("Greeting to sign-off with the person's name")),
						"job_id", "name"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					ApplicationDetails job = requireJob(userId, call.requiredNumber("job_id"));
					// Taken back, or by changing a message by hand: the messages stay as the profile left them
					Optional<AiRequest> asked = latest(userId, AiRequestKind.WRITE_OUTREACH, job.id());
					if (isTakenBack(asked)) {
						throw new ToolException("The person took the request for these messages back: they stay as they "
								+ "are. Don't save any more.");
					}
					OutreachDraft saved = this.letters.saveDraft(userId, OutreachDraft.builder()
						.applicationId(job.id())
						.recipientName(call.requiredString("name"))
						.recipientRole(call.string("role"))
						.recipientLink(call.string("profile_link"))
						.recipientEmail(call.string("email"))
						.linkedinNote(call.string("linkedin_note"))
						.emailSubject(call.string("email_subject"))
						.emailBody(call.string("email_body"))
						.build(), askedAt(asked));
					return Map.of("job_id", job.id(), "draft_id", saved.id(), "note_characters",
							saved.linkedinNoteLength(), "page", "/applications/" + job.id());
				});
	}

	/** The latest request of that kind about the job, open or not. */
	private Optional<AiRequest> latest(long userId, AiRequestKind kind, long jobId) {
		return this.discovery.findLatestRequest(userId, kind, jobId);
	}

	/**
	 * Whether the profile took the request back: cancelled it, or changed by hand what the assistant was writing,
	 * which takes it back. The assistant's text would replace what the profile kept. A letter Auto-apply writes, with no request, is never
	 * refused.
	 */
	private static boolean isTakenBack(Optional<AiRequest> asked) {
		return asked.filter(request -> request.status() == AiRequestStatus.CANCELLED).isPresent();
	}

	/**
	 * When the profile asked the assistant for what it's saving, while the request is open: what the profile changed after
	 * that stays (CoverLetterService). {@code null} without one, e.g. for Auto-apply.
	 */
	private static @Nullable Instant askedAt(Optional<AiRequest> asked) {
		return asked.filter(request -> request.status().isOpen()).map(AiRequest::createdAt).orElse(null);
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
