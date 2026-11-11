package com.jobwarden.cv.dto;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * A story of the profile's story bank: something it did, told the STAR way, to tell in interviews again and again.
 * Build it with {@link #builder()}.
 *
 * @param id {@code 0} for one not kept yet
 * @param title what it's about, in a few words, e.g. "Cutting checkout's latency in half"
 * @param situation where and when: the team, the product, what was wrong
 * @param task what the person had to do
 * @param action what the person did, themselves
 * @param result what came of it, with the numbers
 * @param skills what it shows, e.g. leadership, Kafka, a conflict handled: to find the story for a question
 */
@Builder(toBuilder = true)
public record Story(long id, String title, @Nullable String situation, @Nullable String task, @Nullable String action,
		@Nullable String result, List<String> skills, @Nullable Instant updatedAt) {

	/** The most stories a bank keeps: enough for any interview, few enough to read through. */
	public static final int MAX_STORIES = 40;

	/** The longest title: a few words. Also the title field's {@code maxlength} in cv/stories.html. */
	public static final int TITLE_MAX_LENGTH = 200;

	/** The longest skill kept: a skill is a word or two. */
	public static final int SKILL_MAX_LENGTH = 60;

	/** The most skills a story keeps. */
	public static final int SKILLS_MAX = 10;

	public Story {
		skills = (skills == null) ? List.of() : List.copyOf(skills);
	}

	/**
	 * @return whether it tells every part: the situation, the task, the action and the result
	 */
	public boolean isComplete() {
		return this.situation != null && this.task != null && this.action != null && this.result != null;
	}

	/**
	 * @return whether the profile has something to fill in: a part missing, or a question Claude left in brackets where
	 * the CV didn't say, e.g. "[How many users did it reach?]"
	 */
	public boolean needsFinishing() {
		return !isComplete() || Stream.of(this.situation, this.task, this.action, this.result)
			.anyMatch(part -> part != null && part.contains("["));
	}

}
