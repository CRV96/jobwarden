package com.jobwarden.web.dto;

import java.util.Arrays;
import java.util.List;

import com.jobwarden.cv.dto.Story;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import static com.jobwarden.web.dto.FormValues.blankToNull;

/**
 * A story of the story bank, as the CV page's form sends it: a new one when its id is 0.
 *
 * @param skills what it shows, separated by commas or one per line
 */
public record StoryForm(long id,
		@Size(max = StoryForm.TITLE_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String title,
		@Size(max = StoryForm.PART_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String situation,
		@Size(max = StoryForm.PART_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String task,
		@Size(max = StoryForm.PART_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String action,
		@Size(max = StoryForm.PART_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String result,
		@Size(max = StoryForm.TITLE_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String skills) {

	/** Also the {@code maxlength} of the title and the skills in cv/stories.html. */
	public static final int TITLE_MAX_LENGTH = 200;

	/** Also the {@code maxlength} of each STAR part. */
	public static final int PART_MAX_LENGTH = 2000;

	/** A new story's empty form. */
	public static final StoryForm NEW = new StoryForm(0, null, null, null, null, null, null);

	public static StoryForm of(Story story) {
		return new StoryForm(story.id(), story.title(), story.situation(), story.task(), story.action(), story.result(),
				String.join(", ", story.skills()));
	}

	public Story toStory() {
		List<String> parsed = (this.skills == null) ? List.of()
				: Arrays.stream(this.skills.split("[,\\n]")).map(String::strip).filter(skill -> !skill.isEmpty()).toList();
		return Story.builder()
			.id(this.id)
			.title((this.title == null) ? "" : this.title)
			.situation(blankToNull(this.situation))
			.task(blankToNull(this.task))
			.action(blankToNull(this.action))
			.result(blankToNull(this.result))
			.skills(parsed)
			.build();
	}

}
