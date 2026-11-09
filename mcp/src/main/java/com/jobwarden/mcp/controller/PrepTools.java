package com.jobwarden.mcp.controller;

import java.util.List;
import java.util.Map;

import com.jobwarden.jobs.dto.InterviewBrief;
import com.jobwarden.jobs.service.InterviewBriefService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.string;
import static com.jobwarden.mcp.controller.Schemas.strings;

/**
 * Preparing the profile for an interview: the brief Claude writes for a job, which the job's page shows.
 */
@Component
@RequiredArgsConstructor
class PrepTools implements McpToolSet {

	/** The most a text field keeps, and the most items a list does: a brief is read the evening before. */
	private static final int TEXT_MAX_LENGTH = 6000;

	private static final int ITEMS_MAX = 20;

	private final Profiles profiles;

	private final InterviewBriefService briefs;

	@Override
	public List<McpTool> tools() {
		return List.of(saveInterviewBrief());
	}

	private McpTool saveInterviewBrief() {
		return new McpTool("save_interview_brief", "Save an interview brief",
				"Keeps the brief you wrote to prepare the profile for a job's interview, replacing the one the job had. The "
						+ "job's page shows it.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer("The job, from get_request"),
						"company", string("Who they are: what they make and for whom, how big, where, how they're doing, "
								+ "what's new; a paragraph or two"),
						"role", string("What the job is about, and what they'll look for in the interview"),
						"questions", strings("The questions likely asked, technical and about the person"),
						"stories", strings("For what they'll look for, a story from the CV each, in a line or two, with the "
								+ "result"),
						"ask_them", strings("Good questions to ask them"),
						"watch_out", string("What to look into before saying yes; left out when there's nothing"),
						"sources", strings("The addresses you read")),
						"job_id", "company"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					long jobId = call.requiredNumber("job_id");
					InterviewBrief saved = this.briefs.save(userId, jobId, InterviewBrief.builder()
						.company(shortened(call.requiredString("company")))
						.role(shortened(call.string("role")))
						.questions(items(call.strings("questions")))
						.stories(items(call.strings("stories")))
						.askThem(items(call.strings("ask_them")))
						.watchOut(shortened(call.string("watch_out")))
						.sources(items(call.strings("sources")))
						.build());
					return Map.of("job_id", jobId, "questions", saved.questions().size(), "stories", saved.stories().size(),
							"page", "/applications/" + jobId);
				});
	}

	private static List<String> items(List<String> items) {
		return items.stream().limit(ITEMS_MAX).map(PrepTools::shortened).toList();
	}

	private static @Nullable String shortened(@Nullable String text) {
		return (text == null || text.length() <= TEXT_MAX_LENGTH) ? text : text.substring(0, TEXT_MAX_LENGTH - 1) + "…";
	}

}
