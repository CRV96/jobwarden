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

/**
 * Scoring how well the profile fits its jobs: Claude reads each posting's requirements against the CV, and JobWarden
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
				"The profile's jobs still to apply to, or waiting on an answer, that have no score yet, the newest first.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"limit", integer("How many at most, up to " + UNSCORED_MAX + "; 10 if left out"))),
				true, false, call -> {
					long userId = this.profiles.of(call);
					return Map.of("jobs", this.fits.findUnscored(userId, call.limit("limit", 10, UNSCORED_MAX))
						.stream()
						.map(job -> new JobRef(job.id(), job.title(), job.company(), job.link()))
						.toList());
				});
	}

	private McpTool saveJobFit() {
		ObjectNode requirement = object(properties(
				"text", string("The requirement, in the posting's words, short, e.g. 5+ years of Java"),
				"importance", oneOf(RequirementImportance.values(), "MUST: required, must, you have; NICE: a plus, nice to "
						+ "have, bonus, preferred"),
				"match", oneOf(RequirementMatch.values(), "MET: the CV shows it; PARTIAL: something near it (a related "
						+ "tool, fewer years); MISSING: nothing in the CV supports it"),
				"note", string("What in the CV meets it, or what's missing, in a few words")),
				"text", "importance", "match");
		return new McpTool("save_job_fit", "Save a job's fit",
				"Keeps how well the profile fits one of its jobs: the posting's requirements, each a must-have or a plus, "
						+ "met, partly met or missing by the CV. JobWarden works out the score from them, from 1 to 5, and "
						+ "returns it; a missing must-have keeps it at 3 at most. From " + REQUIREMENTS_MIN + " to "
						+ REQUIREMENTS_MAX + " requirements: the ones that matter, the place, the level and the work type "
						+ "among them when the posting sets them.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer("The job"),
						"requirements", list(requirement, "The posting's requirements, the must-haves first"),
						"summary", string("Why, in a sentence or two: what makes it a fit, and what doesn't")),
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

	private static @Nullable String shortened(@Nullable String text, int maxLength) {
		return (text == null || text.length() <= maxLength) ? text : text.substring(0, maxLength - 1) + "…";
	}

	record JobRef(long id, String title, @Nullable String company, @Nullable String link) {
	}

}
