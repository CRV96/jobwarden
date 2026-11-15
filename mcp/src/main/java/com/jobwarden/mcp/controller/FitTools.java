package com.jobwarden.mcp.controller;

import java.util.List;
import java.util.Map;

import com.jobwarden.jobs.dto.FitRequirement;
import com.jobwarden.jobs.dto.JobFit;
import com.jobwarden.jobs.enums.RequirementImportance;
import com.jobwarden.jobs.enums.RequirementMatch;
import com.jobwarden.jobs.service.JobFitService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.list;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.oneOf;
import static com.jobwarden.mcp.controller.Schemas.string;
import static com.jobwarden.mcp.controller.Texts.shortened;

/**
 * Scoring how well the profile fits its jobs: the assistant reads each posting's requirements against the CV, and JobWarden
 * works out the score from them, the same way for every job, so scores compare.
 */
@Component
@RequiredArgsConstructor
class FitTools implements McpToolSet {

	static final int REQUIREMENTS_MIN = 3;

	static final int REQUIREMENTS_MAX = 15;

	private static final int UNSCORED_MAX = 25;

	private static final int TEXT_MAX_LENGTH = 300;

	private static final int SUMMARY_MAX_LENGTH = 1000;

	private final Profiles profiles;

	private final JobFitService fits;

	@Override
	public List<McpTool> tools() {
		return List.of(listJobsToScore(), saveJobFit());
	}

	private McpTool listJobsToScore() {
		return new McpTool("list_jobs_to_score", "List the jobs to score",
				"Unscored jobs still to apply to or awaiting an answer, newest first.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"limit", integer("Default 10, max " + UNSCORED_MAX))),
				true, false,
				call -> Map.of("jobs", unscored(this.profiles.of(call), call.limit("limit", 10, UNSCORED_MAX))));
	}

	/** The jobs to score, as list_jobs_to_score gives them, which get_request also hands out to score them. */
	List<JobRef> unscored(long userId, int limit) {
		return this.fits.findUnscored(userId, Math.min(limit, UNSCORED_MAX))
			.stream()
			.map(job -> new JobRef(job.id(), job.title(), job.company(), job.link()))
			.toList();
	}

	private McpTool saveJobFit() {
		ObjectNode requirement = object(properties(
				"text", string("In the posting's words, short, e.g. 5+ years of Java"),
				"importance", oneOf(RequirementImportance.values(), "MUST: required; NICE: a plus"),
				"match", oneOf(RequirementMatch.values(), "MET: the CV shows it; PARTIAL: something near it; MISSING: "
						+ "nothing supports it"),
				"note", string("Why, in a few words")),
				"text", "importance", "match");
		return new McpTool("save_job_fit", "Save a job's fit",
				"Saves how the profile fits a job: " + REQUIREMENTS_MIN + "-" + REQUIREMENTS_MAX + " of the posting's "
						+ "requirements, each MUST or NICE, and MET, PARTIAL or MISSING by the CV. Returns the 1-5 score "
						+ "JobWarden works out; a missing MUST keeps it at 3 at most.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer(),
						"requirements", list(requirement, "Must-haves first"),
						"summary", string("What fits and what doesn't, 1-2 sentences")),
						"job_id", "requirements"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					long jobId = call.requiredNumber("job_id");
					List<FitRequirement> requirements = call.items("requirements")
						.stream()
						.map(item -> new FitRequirement(shortened(item.requiredString("text"), TEXT_MAX_LENGTH),
								required(item, "importance", RequirementImportance.class),
								required(item, "match", RequirementMatch.class), shortened(item.string("note"), TEXT_MAX_LENGTH)))
						.toList();
					if (requirements.size() < REQUIREMENTS_MIN || requirements.size() > REQUIREMENTS_MAX) {
						throw new ToolException("requirements must have from " + REQUIREMENTS_MIN + " to " + REQUIREMENTS_MAX
								+ " items");
					}
					JobFit fit = this.fits.save(userId, jobId, shortened(call.string("summary"), SUMMARY_MAX_LENGTH),
							requirements);
					return Map.of("job_id", jobId, "score", fit.score(), "verdict", fit.verdict());
				});
	}

	private static <E extends Enum<E>> E required(ToolCall item, String name, Class<E> type) {
		E value = item.constant(name, type);
		if (value == null) {
			throw new ToolException("Each requirement needs its " + name);
		}
		return value;
	}

	record JobRef(long id, String title, @Nullable String company, @Nullable String link) {
	}

}
