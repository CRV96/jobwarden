package com.jobwarden.mcp.controller;

import java.util.List;
import java.util.Map;

import com.jobwarden.cv.dto.Story;
import com.jobwarden.cv.service.StoryService;
import com.jobwarden.jobs.dto.InterviewBrief;
import com.jobwarden.jobs.service.InterviewBriefService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;
import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.list;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.string;
import static com.jobwarden.mcp.controller.Schemas.strings;

/**
 * Preparing the profile for an interview: the brief the assistant writes for a job, which the job's page shows, with the
 * stories of the profile's story bank that answer what they'll look for.
 */
@Component
@RequiredArgsConstructor
class PrepTools implements McpToolSet {

	/** The most a text field keeps, and the most items a list does: a brief is read the evening before. */
	private static final int TEXT_MAX_LENGTH = 6000;

	private static final int ITEMS_MAX = 20;

	private final Profiles profiles;

	private final InterviewBriefService briefs;

	private final StoryService stories;

	@Override
	public List<McpTool> tools() {
		return List.of(saveInterviewBrief());
	}

	private McpTool saveInterviewBrief() {
		return new McpTool("save_interview_brief", "Save an interview brief",
				"Saves a job's interview brief, replacing its previous one.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer(),
						"company", string("What they make and for whom, size, place, how they're doing, what's new; 1-2 "
								+ "paragraphs"),
						"role", string("What the job is about and what they'll look for"),
						"questions", strings("Likely questions, technical and personal"),
						"stories", strings("A story per thing they'll look for, 1-2 lines with the result"),
						"story_ids", list(integer(), "The story bank's that fit, best first"),
						"ask_them", strings(),
						"watch_out", string("What to check before saying yes; omit if nothing"),
						"sources", strings("URLs read")),
						"job_id", "company"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					long jobId = call.requiredNumber("job_id");
					List<Long> storyIds = this.stories.findAll(userId, call.numbers("story_ids", ITEMS_MAX))
						.stream()
						.map(Story::id)
						.limit(ITEMS_MAX)
						.toList();
					InterviewBrief saved = this.briefs.save(userId, jobId, InterviewBrief.builder()
						.company(shortened(call.requiredString("company")))
						.role(shortened(call.string("role")))
						.questions(items(call.strings("questions")))
						.stories(items(call.strings("stories")))
						.storyIds(storyIds)
						.askThem(items(call.strings("ask_them")))
						.watchOut(shortened(call.string("watch_out")))
						.sources(items(call.strings("sources")))
						.build());
					return Map.of("job_id", jobId, "questions", saved.questions().size(), "stories", saved.stories().size(),
							"bank_stories", saved.storyIds().size(), "page", "/applications/" + jobId);
				});
	}

	private static List<String> items(List<String> items) {
		return items.stream().limit(ITEMS_MAX).map(PrepTools::shortened).toList();
	}

	@Contract("null -> null; !null -> !null")
	private static @Nullable String shortened(@Nullable String text) {
		return Texts.shortened(text, TEXT_MAX_LENGTH);
	}

}
