package com.jobwarden.mcp.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
 * the interview briefs to draw on; and the stories the assistant drafts for it from the CV.
 */
@Component
@RequiredArgsConstructor
class StoryTools implements McpToolSet {

	/** The most a part of a story keeps: a story is told in a minute or two. */
	private static final int PART_MAX_LENGTH = 2000;

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
				"The profile's story bank for interviews: id, title, skills and the STAR parts. The person wrote or checked "
						+ "them: prefer them to stories from the CV.",
				object(properties(Profiles.ARGUMENT, Profiles.schema())), true, false,
				call -> Map.of("stories", views(this.profiles.of(call))));
	}

	/** The story bank as list_stories gives it, which get_request also hands out to prepare an interview. */
	List<StoryView> views(long userId) {
		return this.stories.findAll(userId).stream().map(StoryTools::view).toList();
	}

	/**
	 * As many of the stories as fit in the length given, in the bank's order, for a request that hands them out with
	 * its other data; the others are for list_stories.
	 */
	static List<StoryView> within(List<StoryView> stories, int maxLength) {
		List<StoryView> fitting = new ArrayList<>();
		int length = 0;
		for (StoryView story : stories) {
			length += length(story);
			if (length > maxLength && !fitting.isEmpty()) {
				break;
			}
			fitting.add(story);
		}
		return fitting;
	}

	private static int length(StoryView story) {
		return story.title().length() + String.join(", ", story.skills()).length()
				+ Stream.of(story.situation(), story.task(), story.action(), story.result())
					.mapToInt(text -> (text == null) ? 0 : text.length())
					.sum();
	}

	/** The story bank's titles: enough not to write a story again. */
	List<String> titles(long userId) {
		return this.stories.findAll(userId).stream().map(Story::title).toList();
	}

	private McpTool saveStories() {
		ObjectNode story = object(properties(
				"title", string("A few words, e.g. Cutting checkout latency in half"),
				"situation", string("The team, the product, what was wrong"),
				"task", string("What the person had to do"),
				"action", string("What they did themselves, step by step"),
				"result", string("With the CV's numbers; [add the number] where it has none"),
				"skills", strings("What it shows, e.g. Kafka, leadership")),
				"title");
		return new McpTool("save_stories", "Add stories to the story bank",
				"Adds stories drafted from the CV to the story bank, for the person to check and finish; titles the bank "
						+ "has are skipped. Max " + DRAFT_MAX + ".",
				object(properties(Profiles.ARGUMENT, Profiles.schema(), "stories", list(story, "Strongest first")),
						"stories"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					List<Story> drafted = call.items("stories")
						.stream()
						.limit(DRAFT_MAX)
						.map(item -> Story.builder()
							.title(shortened(item.requiredString("title"), Story.TITLE_MAX_LENGTH))
							.situation(shortened(item.string("situation"), PART_MAX_LENGTH))
							.task(shortened(item.string("task"), PART_MAX_LENGTH))
							.action(shortened(item.string("action"), PART_MAX_LENGTH))
							.result(shortened(item.string("result"), PART_MAX_LENGTH))
							// As many as a story keeps, each no longer than a story's skills can be, before anything else
							.skills(item.strings("skills", Story.SKILLS_MAX)
								.stream()
								.map(skill -> (skill.length() > Story.SKILLS_TEXT_MAX_LENGTH)
										? skill.substring(0, Story.SKILLS_TEXT_MAX_LENGTH) : skill)
								.toList())
							.build())
						.toList();
					List<Story> added = this.stories.addDrafted(userId, drafted);
					return Map.of("added", added.stream().map(Story::title).toList(), "left_out",
							drafted.size() - added.size(), "page", "/cv");
				});
	}

	private static StoryView view(Story story) {
		return new StoryView(story.id(), story.title(), story.skills(), story.situation(), story.task(), story.action(),
				story.result());
	}

	private static @Nullable String shortened(@Nullable String text, int max) {
		return (text == null || text.length() <= max) ? text : text.substring(0, max - 1) + "…";
	}

	record StoryView(long id, String title, List<String> skills, @Nullable String situation, @Nullable String task,
			@Nullable String action, @Nullable String result) {
	}

}
