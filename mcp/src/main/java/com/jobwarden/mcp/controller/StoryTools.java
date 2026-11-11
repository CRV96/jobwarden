package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.jobwarden.cv.dto.Story;
import com.jobwarden.cv.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.list;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.string;
import static com.jobwarden.mcp.controller.Schemas.strings;

/**
 * The profile's story bank, kept with its CV: what it did, told the STAR way (situation, task, action, result), for
 * the interview briefs to draw on; and the stories Claude drafts for it from the CV.
 */
@Component
@RequiredArgsConstructor
class StoryTools implements McpToolSet {

	/** The most a part of a story keeps: a story is told in a minute or two. */
	private static final int PART_MAX_LENGTH = 2000;

	private static final int SKILLS_MAX = 10;

	/** The most stories one request drafts: the CV's best, for the profile to finish. */
	static final int DRAFT_MAX = 8;

	private final Profiles profiles;

	private final StoryService stories;

	@Override
	public List<McpTool> tools() {
		return List.of(listStories(), saveStories());
	}

	private McpTool listStories() {
		return new McpTool("list_stories", "List the story bank",
				"The profile's own stories, kept with its CV, to tell in interviews: each with its id, its title, what it "
						+ "shows (skills), and the STAR parts: situation, task, action, result. The profile wrote or checked "
						+ "them: prefer them to stories made up from the CV.",
				object(properties(Profiles.ARGUMENT, Profiles.schema())), true, false, call -> {
					long userId = this.profiles.of(call);
					return Map.of("stories", this.stories.findAll(userId).stream().map(StoryTools::view).toList());
				});
	}

	private McpTool saveStories() {
		ObjectNode story = object(properties(
				"title", string("What it's about, in a few words, e.g. Cutting checkout latency in half"),
				"situation", string("Where and when: the team, the product, what was wrong"),
				"task", string("What the person had to do"),
				"action", string("What the person did, themselves, step by step"),
				"result", string("What came of it, with the CV's numbers; [add the number] where the CV has none"),
				"skills", strings("What it shows, e.g. Kafka, leadership, a conflict handled")),
				"title");
		return new McpTool("save_stories", "Add stories to the story bank",
				"Adds stories you drafted from the CV to the profile's story bank, which the CV page shows for it to check "
						+ "and finish. Those whose title the bank has are left out; the profile's own stay as they are. At "
						+ "most " + DRAFT_MAX + ".",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"stories", list(story, "The stories, the strongest first")),
						"stories"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					List<Story> drafted = call.items("stories")
						.stream()
						.limit(DRAFT_MAX)
						.map(item -> Story.builder()
							.title(shortened(item.requiredString("title")))
							.situation(shortened(item.string("situation")))
							.task(shortened(item.string("task")))
							.action(shortened(item.string("action")))
							.result(shortened(item.string("result")))
							.skills(item.strings("skills").stream().limit(SKILLS_MAX).toList())
							.build())
						.toList();
					List<Story> added = this.stories.addDrafted(userId, drafted);
					return Map.of("added", added.stream().map(Story::title).toList(), "left_out",
							drafted.size() - added.size(), "page", "/cv");
				});
	}

	private static StoryView view(Story story) {
		return new StoryView(story.id(), story.title(), story.skills(), story.situation(), story.task(), story.action(),
				story.result(), story.updatedAt());
	}

	private static @Nullable String shortened(@Nullable String text) {
		return (text == null || text.length() <= PART_MAX_LENGTH) ? text : text.substring(0, PART_MAX_LENGTH - 1) + "…";
	}

	record StoryView(long id, String title, List<String> skills, @Nullable String situation, @Nullable String task,
			@Nullable String action, @Nullable String result, @Nullable Instant updatedAt) {
	}

}
