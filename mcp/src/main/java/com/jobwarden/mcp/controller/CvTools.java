package com.jobwarden.mcp.controller;

import java.util.List;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvCertification;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvEducation;
import com.jobwarden.cv.dto.CvExperience;
import com.jobwarden.cv.dto.CvLanguage;
import com.jobwarden.cv.dto.CvProject;
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
import static com.jobwarden.mcp.controller.Schemas.bool;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.list;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.string;
import static com.jobwarden.mcp.controller.Schemas.strings;

/**
 * The profile's CV, for the assistant to match jobs against, and to rewrite for one job.
 */
@Component
@RequiredArgsConstructor
class CvTools implements McpToolSet {

	/** The most of the file's text it returns: a CV is far shorter, and the assistant's context is shared. */
	static final int MAX_TEXT_LENGTH = 30_000;

	/**
	 * The most of the file's text a request hands out with its other data (a posting of up to 12,000 characters, the
	 * fields), less when they leave less room: get_cv with full=true gives up to MAX_TEXT_LENGTH of it.
	 */
	static final int REQUEST_TEXT_LENGTH = 15_000;

	/** Where a file's text is when a CV is handed out without it, or with less of it (up to 30,000 characters). */
	static final String FILE_TEXT_WITH = "get_cv with full=true";

	/** The most of it a brief CV returns: the first pages say who the person is. */
	static final int BRIEF_TEXT_LENGTH = 8_000;

	private final Profiles profiles;

	private final CvService cvs;

	private final ApplicationService applications;

	@Override
	public List<McpTool> tools() {
		return List.of(getCv(), saveTailoredCv());
	}

	private McpTool getCv() {
		return new McpTool("get_cv", "Get the CV",
				"The profile's CV: the CV page's fields, or without them the uploaded file's text; has_cv false if "
						+ "neither. Brief by default: jobs without their points.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"full", bool("Every job's points, and the file's text too"))),
				true, false, call -> {
					boolean full = call.arguments().path("full").asBoolean(false);
					return view(this.profiles.of(call), full ? Depth.FULL : Depth.BRIEF);
				});
	}

	/**
	 * How much of the CV: the jobs alone, enough to know who the person is; every point, to write from what they did;
	 * or every point and the file's text too, to rewrite the CV.
	 */
	enum Depth {

		BRIEF,

		/** Every job's points, and the file's text when the fields have none: they'd say the same twice. */
		POINTS,

		/** Every job's points, and the file's text as well as the fields. */
		FULL

	}

	/**
	 * The CV as get_cv gives it, which get_request also hands out with the requests that need it.
	 */
	CvView view(long userId, Depth depth) {
		return view(userId, depth, MAX_TEXT_LENGTH);
	}

	/**
	 * The same for get_request, which hands it out with the request's other data: the file's text no longer than
	 * {@link #REQUEST_TEXT_LENGTH}.
	 */
	CvView viewForRequest(long userId, Depth depth) {
		return view(userId, depth, REQUEST_TEXT_LENGTH);
	}

	/**
	 * The same, with no more of the file's text than given: for a request whose other data leaves less room.
	 */
	CvView viewForRequest(long userId, Depth depth, int fileTextMax) {
		return view(userId, depth, Math.min(fileTextMax, REQUEST_TEXT_LENGTH));
	}

	private CvView view(long userId, Depth depth, int fileTextMax) {
		Cv cv = this.cvs.find(userId);
		CvDetails details = cv.details();
		boolean filledIn = details.isFilledIn() || !details.skills().isEmpty() || details.summary() != null;
		boolean points = depth != Depth.BRIEF;
		// The file's text says again what the fields say: only without them, unless asked
		boolean fileText = !filledIn || depth == Depth.FULL
				|| (depth == Depth.POINTS && details.experience().stream().allMatch(job -> job.description() == null));
		// Left out, or cut short, the file says where its text is, for the evidence only it has
		String text = fileText ? fileText(userId, points, fileTextMax) : null;
		boolean cut = text != null && points && text.length() >= fileTextMax;
		FileView file = (cv.file() == null) ? null
				: new FileView(cv.file().fileName(), text, (!fileText || cut) ? FILE_TEXT_WITH : null);
		return new CvView(filledIn || file != null, filledIn ? details(details, points) : null, file);
	}

	/**
	 * The CV rewritten for a job, which its page then shows, previews and downloads as a PDF. The contact details left
	 * out are the CV page's.
	 */
	private McpTool saveTailoredCv() {
		ObjectNode job = object(properties(
				"role", string("As on the CV"),
				"company", string(),
				"location", string("e.g. Remote"),
				"start", string("As on the CV, e.g. Mar 2021"),
				"end", string("e.g. Present"),
				"description", string("Points, one per line, each starting with a verb")),
				"role");
		ObjectNode school = object(properties(
				"degree", string("Degree or course"),
				"school", string(),
				"location", string(),
				"start", string(),
				"end", string(),
				"description", string("Other points, one per line")),
				"degree");
		ObjectNode project = object(properties(
				"name", string(),
				"link", string("e.g. its repository"),
				"technologies", string("In a few words"),
				"description", string("What it does and what the person built, 1-3 sentences")),
				"name");
		ObjectNode cv = object("full_name and the contact fields only when the CV page has none", properties(
				"full_name", string(),
				"headline", string("The posting's title, if true of the person"),
				"summary", string("3-4 lines on why the person fits"),
				"skills", strings("The posting's keywords the person has first, in its words, then other relevant ones"),
				"experience", list(job, "The CV's jobs and dates, newest first"),
				"projects", list(project, "Those that fit the job first"),
				"education", list(school),
				"languages", list(object(properties("language", string(), "level", string("e.g. C1, Native")))),
				"certifications", list(object(properties("name", string(), "issuer", string(), "year", string()))),
				"location", string(),
				"email", string(),
				"phone", string(),
				"linkedin", string()));
		return new McpTool("save_tailored_cv", "Save a CV tailored to a job",
				"Saves the CV rewritten for one job, replacing the job's previous one; its page shows it and its PDF. "
						+ "Only what the person did, in the posting's words; contact details come from the CV page.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer(),
						"cv", cv,
						"matched_keywords", strings("The posting's keywords the CV now names"),
						"missing_keywords", strings("Those it asks for that the person can't honestly claim"),
						"notes", string("What you changed and what to check, in a few lines")),
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
	 * The fields the assistant wrote, with the CV page's contact details: the name and how to reach the person aren't
	 * the assistant's to change.
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
			.projects(cv.items("projects").stream().map(ProjectTools::project).toList())
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

	/** The file's text: all of it with the points (up to max), else its start */
	private @Nullable String fileText(long userId, boolean full, int max) {
		int length = full ? max : BRIEF_TEXT_LENGTH;
		return this.cvs.fileText(userId).map(text -> shortened(text, length)).orElse(null);
	}

	private static @Nullable String firstOf(@Nullable String saved, @Nullable String written) {
		return (saved != null) ? saved : written;
	}

	/**
	 * @param full with every job's points; else only the jobs' roles, companies, places and dates
	 */
	private static DetailsView details(CvDetails cv, boolean full) {
		List<CvExperience> experience = full ? cv.experience() : cv.experience()
			.stream()
			.map(job -> new CvExperience(job.role(), job.company(), job.location(), job.start(), job.end(), null))
			.toList();
		return new DetailsView(cv.fullName(), cv.headline(), cv.location(), cv.summary(), cv.skills(), experience,
				cv.projects(), cv.education(), cv.languages(), cv.certifications());
	}

	private static String shortened(String text, int maxLength) {
		return (text.length() <= maxLength) ? text : text.substring(0, maxLength) + "…";
	}

	/**
	 * @param hasCv whether there's anything to read: the fields or a file
	 */
	record CvView(boolean hasCv, @Nullable DetailsView fields, @Nullable FileView file) {
	}

	/** The fields, without the contact details: the assistant needs none of them to find jobs. */
	record DetailsView(@Nullable String fullName, @Nullable String headline, @Nullable String location,
			@Nullable String summary, List<String> skills, List<CvExperience> experience, List<CvProject> projects,
			List<CvEducation> education, List<CvLanguage> languages, List<CvCertification> certifications) {
	}

	/**
	 * @param text what could be read out of it; none for a scanned PDF
	 */
	/**
	 * @param text its text, or none when the fields say it already
	 * @param textWith then, how to get it: for evidence only the file has
	 */
	record FileView(String name, @Nullable String text, @Nullable String textWith) {
	}

	/**
	 * @param jobs how many jobs the CV lists
	 * @param page where the profile sees it
	 */
	record TailoredView(long jobId, String title, @Nullable String company, int jobs, List<String> matchedKeywords,
			List<String> missingKeywords, String page) {
	}

}
