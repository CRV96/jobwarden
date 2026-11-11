package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.jobwarden.inbox.dto.EmailUpdate;
import com.jobwarden.inbox.enums.UpdateDecider;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.jobs.enums.ApplicationStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.oneOf;

/**
 * Sorting the updates JobWarden found in the profile's emails, waiting on the Inbox page: what each email says and
 * which job it matched, if one, then applying it to the right job's timeline, or dismissing it when it isn't news. The
 * page shows what the assistant did, and the profile can bring back what it dismissed.
 */
@Component
@RequiredArgsConstructor
class InboxTools implements McpToolSet {

	private static final int UPDATES_MAX = 50;

	/** Enough of an email to tell the company and the role; the rest is signatures and legal text. */
	private static final int TEXT_MAX_LENGTH = 1500;

	/** What an email can say about an application. */
	private static final List<ApplicationStatus> EMAIL_STATUSES = List.of(ApplicationStatus.APPLIED,
			ApplicationStatus.INTERVIEWING, ApplicationStatus.OFFER, ApplicationStatus.REJECTED);

	private final Profiles profiles;

	private final InboxService inbox;

	@Override
	public List<McpTool> tools() {
		return List.of(listEmailUpdates(), applyEmailUpdate(), dismissEmailUpdate());
	}

	private McpTool listEmailUpdates() {
		return new McpTool("list_email_updates", "List the email updates",
				"Emails waiting on the Inbox page that look like news about a job, newest first: JobWarden's guess "
						+ "(status), the words behind it, the email's start, and the matched job if any.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"limit", integer("Default 20, max " + UPDATES_MAX))),
				true, false, call -> {
					long userId = this.profiles.of(call);
					return Map.of("updates", this.inbox.findPending(userId)
						.stream()
						.limit(call.limit("limit", 20, UPDATES_MAX))
						.map(InboxTools::row)
						.toList());
				});
	}

	private McpTool applyEmailUpdate() {
		return new McpTool("apply_email_update", "Apply an email update",
				"Adds the email's news to a job's timeline, dated when it came, which moves the job to the status.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"update_id", integer(),
						"job_id", integer("The job it's about"),
						"status", oneOf(EMAIL_STATUSES.toArray(ApplicationStatus[]::new), "APPLIED: received; INTERVIEWING: "
								+ "an interview or call offered; OFFER; REJECTED")),
						"update_id", "job_id", "status"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					long updateId = call.requiredNumber("update_id");
					long jobId = call.requiredNumber("job_id");
					ApplicationStatus status = call.constant("status", ApplicationStatus.class);
					if (status == null || !EMAIL_STATUSES.contains(status)) {
						throw new ToolException("status must be one of " + EMAIL_STATUSES);
					}
					this.inbox.apply(userId, updateId, jobId, status, null, UpdateDecider.CLAUDE);
					return Map.of("update_id", updateId, "job_id", jobId, "status", status);
				});
	}

	private McpTool dismissEmailUpdate() {
		return new McpTool("dismiss_email_update", "Dismiss an email update",
				"Drops an update that isn't news about an application: an alert, a newsletter, an ad, or news the "
						+ "timeline has. The profile can bring it back.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(), "update_id", integer()), "update_id"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					long updateId = call.requiredNumber("update_id");
					this.inbox.dismiss(userId, updateId, UpdateDecider.CLAUDE);
					return Map.of("update_id", updateId, "dismissed", true);
				});
	}

	private static UpdateRow row(EmailUpdate update) {
		return new UpdateRow(update.id(), update.receivedAt(), update.sender(), update.subject(), update.status(),
				update.excerpt(), shortened(update.text()),
				(update.applicationId() != null) ? new JobRef(update.applicationId(), update.jobTitle(), update.company()) : null);
	}

	private static @Nullable String shortened(@Nullable String text) {
		return (text == null || text.length() <= TEXT_MAX_LENGTH) ? text : text.substring(0, TEXT_MAX_LENGTH - 1) + "…";
	}

	/**
	 * @param status what JobWarden thinks the email says
	 * @param job the job it matched; none when it matched none
	 */
	record UpdateRow(long id, Instant receivedAt, @Nullable String sender, @Nullable String subject,
			ApplicationStatus status, @Nullable String excerpt, @Nullable String text, @Nullable JobRef job) {
	}

	record JobRef(long id, @Nullable String title, @Nullable String company) {
	}

}
