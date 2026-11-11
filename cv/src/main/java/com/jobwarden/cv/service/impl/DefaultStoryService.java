package com.jobwarden.cv.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.Story;
import com.jobwarden.cv.entity.StoryEntity;
import com.jobwarden.cv.exception.CvErrorCode;
import com.jobwarden.cv.exception.StoryNotFoundException;
import com.jobwarden.cv.repository.StoryRepository;
import com.jobwarden.cv.service.StoryService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@CustomLog
@RequiredArgsConstructor
class DefaultStoryService implements StoryService {

	private final StoryRepository stories;

	@Override
	public List<Story> findAll(long userId) {
		return this.stories.findByUserIdOrderByIdAsc(userId).stream().map(DefaultStoryService::toStory).toList();
	}

	@Override
	public List<Story> findAll(long userId, Collection<Long> storyIds) {
		if (storyIds.isEmpty()) {
			return List.of();
		}
		Map<Long, Story> found = this.stories.findByUserIdAndIdIn(userId, storyIds)
			.stream()
			.collect(Collectors.toMap(StoryEntity::getId, DefaultStoryService::toStory));
		return storyIds.stream().distinct().map(found::get).filter(Objects::nonNull).toList();
	}

	/**
	 * @throws StoryNotFoundException if the user has no such story
	 */
	@Override
	public Story find(long userId, long storyId) {
		return this.stories.findByIdAndUserId(storyId, userId)
			.map(DefaultStoryService::toStory)
			.orElseThrow(() -> new StoryNotFoundException(storyId));
	}

	@Override
	@Transactional
	public Story save(long userId, Story story) {
		Story tidied = tidied(story);
		StoryEntity entity;
		if (story.id() == 0) {
			if (this.stories.countByUserId(userId) >= Story.MAX_STORIES) {
				throw new BusinessRuleException(CvErrorCode.TOO_MANY_STORIES, "The story bank is full",
						String.valueOf(Story.MAX_STORIES));
			}
			entity = new StoryEntity(userId);
		} else {
			entity = this.stories.findByIdAndUserId(story.id(), userId)
				.orElseThrow(() -> new StoryNotFoundException(story.id()));
		}
		entity.change(tidied);
		Story saved = toStory(this.stories.saveAndFlush(entity));
		log.info("Saved story {} of profile {}", saved.id(), userId);
		return saved;
	}

	@Override
	@Transactional
	public List<Story> addDrafted(long userId, List<Story> drafted) {
		List<StoryEntity> kept = this.stories.findByUserIdOrderByIdAsc(userId);
		Set<String> titles = new HashSet<>(kept.stream().map(story -> key(story.getTitle())).toList());
		int room = Story.MAX_STORIES - kept.size();
		List<Story> added = new ArrayList<>();
		for (Story story : drafted) {
			if (added.size() >= room) {
				break;
			}
			String title = text(story.title());
			if (title == null || !titles.add(key(title))) {
				continue;
			}
			StoryEntity entity = new StoryEntity(userId);
			entity.change(tidied(story));
			added.add(toStory(this.stories.save(entity)));
		}
		log.info("Added {} of the {} stories Claude drafted to the story bank of profile {}", added.size(), drafted.size(),
				userId);
		return added;
	}

	@Override
	@Transactional
	public void delete(long userId, long storyId) {
		if (this.stories.deleteOwned(userId, storyId) > 0) {
			log.info("Deleted story {} of profile {}", storyId, userId);
		}
	}

	/**
	 * @throws BusinessRuleException without a title
	 */
	private static Story tidied(Story story) {
		String title = text(story.title());
		if (title == null) {
			throw new BusinessRuleException(CvErrorCode.STORY_WITHOUT_TITLE, "The story has no title");
		}
		return story.toBuilder()
			.title(title)
			.situation(text(story.situation()))
			.task(text(story.task()))
			.action(text(story.action()))
			.result(text(story.result()))
			.skills(story.skills()
				.stream()
				.filter(Objects::nonNull)
				.map(String::strip)
				.filter(skill -> !skill.isEmpty())
				.distinct()
				.toList())
			.build();
	}

	private static String key(String title) {
		return title.strip().toLowerCase(Locale.ROOT);
	}

	private static @Nullable String text(@Nullable String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

	private static Story toStory(StoryEntity story) {
		return Story.builder()
			.id(story.getId())
			.title(story.getTitle())
			.situation(story.getSituation())
			.task(story.getTask())
			.action(story.getAction())
			.result(story.getResult())
			.skills(story.getSkills())
			.updatedAt(story.getUpdatedAt())
			.build();
	}

}
