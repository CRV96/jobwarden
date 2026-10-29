package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.util.List;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvCertification;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvEducation;
import com.jobwarden.cv.dto.CvExperience;
import com.jobwarden.cv.dto.CvLanguage;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.list;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.string;
import static com.jobwarden.mcp.controller.Schemas.strings;

/**
 * The profile's CV, for Claude to match jobs against, and to rewrite for one job.
 */
@Component
@RequiredArgsConstructor
class CvTools implements McpToolSet {

	/** The most of the file's text it returns: a CV is far shorter, and Claude's context is shared. */
	static final int MAX_TEXT_LENGTH = 30_000;

	private final Profiles profiles;

	private final CvService cvs;

	private final ApplicationService applications;

	@Override
	public List<McpTool> tools() {
		return List.of(getCv(), saveTailoredCv());
	}

	private McpTool getCv() {
		return new McpTool("get_cv", "Get the CV",
				"The profile's CV, to find jobs that fit it: the fields filled in on the CV page (headline, summary, "
						+ "experience, education, skills, languages, certifications) and the text of the CV file uploaded. "
						+ "Either may be missing; has_cv is false when both are.",
				object(properties(Profiles.ARGUMENT, Profiles.schema())), true, false, call -> {
					long userId = this.profiles.of(call);
					Cv cv = this.cvs.find(userId);
					FileView file = (cv.file() == null) ? null
							: new FileView(cv.file().fileName(), cv.file().uploadedAt(),
									this.cvs.fileText(userId).map(CvTools::shortened).orElse(null));
					CvDetails details = cv.details();
					boolean filledIn = details.isFilledIn() || !details.skills().isEmpty() || details.summary() != null;
					return new CvView(filledIn || file != null, filledIn ? details(details) : null, file);
				});
	}

	/**
	 * The CV rewritten for a job, which its page then shows, previews and downloads as a PDF. The contact details left
	 * out are the CV page's.
	 */
	private McpTool saveTailoredCv() {
		ObjectNode job = object(properties(
				"role", string("The job title, as the CV had it"),
				"company", string("The employer"),
				"location", string("Where, e.g. Cluj-Napoca or Remote"),
				"start", string("When it started, as the CV had it, e.g. 2021 or Mar 2021"),
				"end", string("When it ended, e.g. Present"),
				"description", string("The points, one per line, each starting with a verb")),
				"role");
		ObjectNode school = object(properties(
				"degree", string("The degree or the course"),
				"school", string("The school"),
				"location", string("Where"),
				"start", string("When it started"),
				"end", string("When it ended"),
				"description", string("Anything else, a line per point")),
				"degree");
		ObjectNode cv = object(properties(
				"full_name", string("Only when the CV page has none: from the CV file"),
				"headline", string("What the person does, under the name: the posting's title when it's true of them"),
				"summary", string("Three or four lines on why the person fits the job"),
				"skills", strings("The posting's keywords the person has first, in its words, then the other relevant ones"),
				"experience", list(job, "The jobs, newest first: the same jobs and dates as the CV"),
				"education", list(school, "As the CV has it"),
				"languages", list(object(properties("language", string("e.g. English"),
						"level", string("e.g. C1, or Native"))), "As the CV has it"),
				"certifications", list(object(properties("name", string("The certification"),
						"issuer", string("Who gave it"), "year", string("When"))), "As the CV has it"),
				"location", string("Only when the CV page has none: where the person lives"),
				"email", string("Only when the CV page has none"),
				"phone", string("Only when the CV page has none"),
				"linkedin", string("Only when the CV page has none: the profile's link")));
		return new McpTool("save_tailored_cv", "Save a CV tailored to a job",
				"Keeps the CV you rewrote for one of the profile's jobs, replacing the one there was for it. The job's page "
						+ "shows it, with a preview and a PDF to download. Only what the person really did, in the posting's "
						+ "words; the contact details you leave out are the CV page's.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer("The job it's for, from get_request or list_jobs"),
						"cv", cv,
						"matched_keywords", strings("The posting's skills and keywords the CV now names"),
						"missing_keywords", strings("Those the posting asks for that the person can't honestly claim"),
						"notes", string("What you changed, and what to check before sending it, in a few lines")),
						"job_id", "cv"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					long jobId = call.requiredNumber("job_id");
					ApplicationDetails application = this.applications.findById(userId, jobId)
						.orElseThrow(() -> new ApplicationNotFoundException(jobId));
					CvDetails saved = this.cvs.find(userId).details();
					TailoredCv tailored = this.cvs.saveTailored(userId, jobId, tailored(call.requiredObject("cv"), saved),
							call.strings("matched_keywords"), call.strings("missing_keywords"), call.string("notes"));
					return new TailoredView(jobId, application.title(), application.company(),
							tailored.details().experience().size(), tailored.matchedKeywords(), tailored.missingKeywords(),
							"/applications/" + jobId);
				});
	}

	/**
	 * The fields Claude wrote, with the CV page's contact details: the name and how to reach the person aren't
	 * Claude's to change.
	 */
	private static CvDetails tailored(ToolCall cv, CvDetails saved) {
		return CvDetails.builder()
			.fullName(firstOf(saved.fullName(), cv.string("full_name")))
			.headline(cv.string("headline"))
			.email(firstOf(saved.email(), cv.string("email")))
			.phone(firstOf(saved.phone(), cv.string("phone")))
			.location(firstOf(saved.location(), cv.string("location")))
			.website(saved.website())
			.linkedin(firstOf(saved.linkedin(), cv.string("linkedin")))
			.github(saved.github())
			.summary(cv.string("summary"))
			.skills(cv.strings("skills"))
			.experience(cv.items("experience").stream().map(job -> new CvExperience(job.requiredString("role"),
					job.string("company"), job.string("location"), job.string("start"), job.string("end"),
					job.string("description"))).toList())
			.education(cv.items("education").stream().map(school -> new CvEducation(school.requiredString("degree"),
					school.string("school"), school.string("location"), school.string("start"), school.string("end"),
					school.string("description"))).toList())
			.languages(cv.items("languages").stream().map(language -> new CvLanguage(language.requiredString("language"),
					language.string("level"))).toList())
			.certifications(cv.items("certifications").stream().map(certification -> new CvCertification(
					certification.requiredString("name"), certification.string("issuer"), certification.string("year")))
				.toList())
			.build();
	}

	private static @Nullable String firstOf(@Nullable String saved, @Nullable String written) {
		return (saved != null) ? saved : written;
	}

	private static DetailsView details(CvDetails cv) {
		return new DetailsView(cv.fullName(), cv.headline(), cv.location(), cv.summary(), cv.skills(), cv.experience(),
				cv.education(), cv.languages(), cv.certifications(), cv.updatedAt());
	}

	private static String shortened(String text) {
		return (text.length() <= MAX_TEXT_LENGTH) ? text : text.substring(0, MAX_TEXT_LENGTH) + "…";
	}

	/**
	 * @param hasCv whether there's anything to read: the fields or a file
	 */
	record CvView(boolean hasCv, @Nullable DetailsView fields, @Nullable FileView file) {
	}

	/** The fields, without the contact details: Claude needs none of them to find jobs. */
	record DetailsView(@Nullable String fullName, @Nullable String headline, @Nullable String location,
			@Nullable String summary, List<String> skills, List<CvExperience> experience, List<CvEducation> education,
			List<CvLanguage> languages, List<CvCertification> certifications, @Nullable Instant updatedAt) {
	}

	/**
	 * @param text what could be read out of it; none for a scanned PDF
	 */
	record FileView(String name, Instant uploadedAt, @Nullable String text) {
	}

	/**
	 * @param jobs how many jobs the CV lists
	 * @param page where the profile sees it
	 */
	record TailoredView(long jobId, String title, @Nullable String company, int jobs, List<String> matchedKeywords,
			List<String> missingKeywords, String page) {
	}

}
