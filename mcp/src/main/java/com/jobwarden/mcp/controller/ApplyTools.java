package com.jobwarden.mcp.controller;

import java.util.List;
import java.util.Map;

import com.jobwarden.apply.dto.ApplicationAnswers;
import com.jobwarden.apply.dto.ApplyItem;
import com.jobwarden.apply.dto.CustomAnswer;
import com.jobwarden.apply.enums.ApplyMode;
import com.jobwarden.apply.enums.ApplyStatus;
import com.jobwarden.apply.enums.CoverLetter;
import com.jobwarden.apply.service.AutoApplyService;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.service.CvService;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.oneOf;
import static com.jobwarden.mcp.controller.Schemas.string;

/**
 * Auto-apply: the answers application forms ask for, and the jobs the profile queued for Claude to apply to, in its
 * browser. Each job's CV is a file to download from the server, to upload to the form.
 */
@Component
class ApplyTools implements McpToolSet {

	private final Profiles profiles;

	private final AutoApplyService autoApply;

	private final CvService cvs;

	/** Where the web module serves a job's CV for Claude, e.g. /claude/cv?profile=1&job=12 */
	private final String cvPath;

	ApplyTools(Profiles profiles, AutoApplyService autoApply, CvService cvs,
			@Value("${jobwarden.claude.cv-path:/claude/cv}") String cvPath) {
		this.profiles = profiles;
		this.autoApply = autoApply;
		this.cvs = cvs;
		this.cvPath = cvPath;
	}

	@Override
	public List<McpTool> tools() {
		return List.of(getApplicationAnswers(), listApplyQueue(), startApply(), reportApplyResult());
	}

	private McpTool getApplicationAnswers() {
		return new McpTool("get_application_answers", "Get the application answers",
				"What to fill application forms in with: the person's contact details from the CV page, and their answers "
						+ "to what forms ask beyond the CV (work permit, sponsorship, notice period, start date, salary "
						+ "expectation, relocation, work preference, years of experience, a cover letter or not, the "
						+ "voluntary questions, and their own questions and answers). Use them as they are; never make an "
						+ "answer up.",
				object(properties(Profiles.ARGUMENT, Profiles.schema())), true, false, call -> {
					long userId = this.profiles.of(call);
					CvDetails cv = this.cvs.find(userId).details();
					ApplicationAnswers answers = this.autoApply.findAnswers(userId);
					return new AnswersView(new ContactView(cv.fullName(), cv.email(), cv.phone(), cv.location(),
							cv.linkedin(), cv.github(), cv.website()), answers.workAuthorization(),
							answers.needsSponsorship(), answers.noticePeriod(), answers.earliestStart(),
							answers.salaryExpectation(), answers.willingToRelocate(), answers.workPreference(),
							answers.yearsOfExperience(), answers.coverLetter(), answers.declineVoluntary(), answers.custom());
				});
	}

	private McpTool listApplyQueue() {
		return new McpTool("list_apply_queue", "List the apply queue",
				"The jobs the profile queued on JobWarden's Auto-apply page for you to apply to and not done yet, the oldest "
						+ "first: each with its posting's link, its mode (REVIEW: fill the form in and leave it to send; "
						+ "SUBMIT: send it once every answer is known), whether a CV is tailored to it, and the address of "
						+ "the CV to download and upload.",
				object(properties(Profiles.ARGUMENT, Profiles.schema())), true, false, call -> {
					long userId = this.profiles.of(call);
					return Map.of("jobs", this.autoApply.findOpenItems(userId).stream().map(item -> row(userId, item)).toList());
				});
	}

	private McpTool startApply() {
		return new McpTool("start_apply", "Start applying",
				"Marks a queued job as being applied to, which the Auto-apply page shows. Call it before opening the form.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"item_id", integer("The queued job, from list_apply_queue")), "item_id"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					return row(userId, this.autoApply.start(userId, call.requiredNumber("item_id")));
				});
	}

	private McpTool reportApplyResult() {
		return new McpTool("report_apply_result", "Report an application",
				"Says how applying to a queued job went. APPLIED (sent, and you saw it confirmed) moves the job to Applied, "
						+ "by Claude, with the note on its timeline; FILLED: filled in and left for the profile to check and "
						+ "send (REVIEW), or APPLIED later once they say they sent it; NEEDS_ANSWER: the form asks something "
						+ "the answers don't say, which goes in question; SKIPPED: not applied to, and why; FAILED: tried "
						+ "and couldn't.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"item_id", integer("The queued job"),
						"result", oneOf(ApplyStatus.RESULTS.toArray(ApplyStatus[]::new), "How it went"),
						"site", string("The form it was on, e.g. Greenhouse, Lever, Workday, the company's own"),
						"note", string("What you did, in a sentence or two: e.g. the CV uploaded, the questions answered, "
								+ "the cover letter written; or why it was skipped or failed"),
						"question", string("For NEEDS_ANSWER: the form's question, word for word")),
						"item_id", "result"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					ApplyStatus result = call.constant("result", ApplyStatus.class);
					return row(userId, this.autoApply.report(userId, call.requiredNumber("item_id"), result,
							call.string("site"), call.string("note"), call.string("question")));
				});
	}

	private QueueRow row(long userId, ApplyItem item) {
		String cvUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path(this.cvPath)
			.queryParam("profile", userId)
			.queryParam("job", item.applicationId())
			.toUriString();
		return new QueueRow(item.id(), item.applicationId(), item.title(), item.company(), item.link(), item.mode(),
				item.status(), this.cvs.findTailored(userId, item.applicationId()).isPresent(), cvUrl, item.question());
	}

	/**
	 * @param contact from the CV page
	 * @param needsSponsorship null when the person didn't say
	 */
	record AnswersView(ContactView contact, @Nullable String workAuthorization, @Nullable Boolean needsSponsorship,
			@Nullable String noticePeriod, @Nullable String earliestStart, @Nullable String salaryExpectation,
			@Nullable Boolean willingToRelocate, @Nullable String workPreference, @Nullable Integer yearsOfExperience,
			CoverLetter coverLetter, boolean declineVoluntary, List<CustomAnswer> custom) {
	}

	record ContactView(@Nullable String fullName, @Nullable String email, @Nullable String phone,
			@Nullable String location, @Nullable String linkedin, @Nullable String github, @Nullable String website) {
	}

	/**
	 * @param itemId what start_apply and report_apply_result take
	 * @param hasTailoredCv whether a CV is tailored to the job: the CV to download is that one
	 * @param cvUrl the CV as a file, to download and upload: the tailored one, else the one built from the CV page, else
	 * the file uploaded there
	 * @param question what the form asked last time, when it needed an answer
	 */
	record QueueRow(long itemId, long jobId, String title, @Nullable String company, @Nullable String link,
			ApplyMode mode, ApplyStatus status, boolean hasTailoredCv, String cvUrl, @Nullable String question) {
	}

}
