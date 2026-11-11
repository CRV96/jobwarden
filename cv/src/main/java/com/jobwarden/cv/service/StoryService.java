package com.jobwarden.cv.service;

import java.util.Collection;
import java.util.List;

import com.jobwarden.cv.dto.Story;

/**
 * The profile's story bank: what it did, told the STAR way, kept with its CV for every interview. The profile writes
 * them on the CV page, or Claude drafts them from the CV; the interview briefs draw on them. Every method takes the
 * owning user's id, like {@link CvService}.
 */
public interface StoryService {

	/**
	 * @return the profile's stories, in the order they were written
	 */
	List<Story> findAll(long userId);

	/**
	 * @return those of the stories the profile has, in the order the ids are given; the others are left out, e.g.
	 * deleted since a brief named them
	 */
	List<Story> findAll(long userId, Collection<Long> storyIds);

	/**
	 * @throws com.jobwarden.cv.exception.StoryNotFoundException if the profile has no such story
	 */
	Story find(long userId, long storyId);

	/**
	 * Keeps a new story, when its id is {@code 0}, or the changes to one. Blank text is no value; the skills are
	 * stripped, and each kept once.
	 *
	 * @throws com.jobwarden.common.exception.BusinessRuleException if it has no title, or a new one would make more than
	 * {@link Story#MAX_STORIES}
	 * @throws com.jobwarden.cv.exception.StoryNotFoundException if it changes a story the profile hasn't
	 */
	Story save(long userId, Story story);

	/**
	 * Adds the stories Claude drafted, leaving out those whose title the bank has, and those over
	 * {@link Story#MAX_STORIES}. The profile's own stay as they are.
	 *
	 * @return the stories added
	 */
	List<Story> addDrafted(long userId, List<Story> stories);

	void delete(long userId, long storyId);

}
