package com.jobwarden.apply.service.impl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jobwarden.apply.dto.ApplicationAnswers;
import com.jobwarden.apply.dto.ApplyItem;
import com.jobwarden.apply.dto.CustomAnswer;
import com.jobwarden.apply.entity.ApplicationAnswersEntity;
import com.jobwarden.apply.entity.ApplyItemEntity;
import com.jobwarden.apply.enums.ApplyMode;
import com.jobwarden.apply.enums.ApplyStatus;
import com.jobwarden.apply.exception.ApplyErrorCode;
import com.jobwarden.apply.exception.ApplyItemNotFoundException;
import com.jobwarden.apply.repository.ApplicationAnswersRepository;
import com.jobwarden.apply.repository.ApplyItemRepository;
import com.jobwarden.apply.service.AutoApplyService;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.enums.AppliedBy;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@CustomLog
@RequiredArgsConstructor
class DefaultAutoApplyService implements AutoApplyService {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final ApplicationAnswersRepository answers;

	private final ApplyItemRepository items;

	private final ApplicationService applications;

	@Override
	@Transactional(readOnly = true)
	public ApplicationAnswers findAnswers(long userId) {
		return this.answers.findById(userId).map(DefaultAutoApplyService::toAnswers).orElse(ApplicationAnswers.NONE);
	}

	@Override
	@Transactional
	public ApplicationAnswers saveAnswers(long userId, ApplicationAnswers changed) {
		ApplicationAnswersEntity entity = this.answers.findById(userId)
			.orElseGet(() -> new ApplicationAnswersEntity(userId));
		List<CustomAnswer> custom = changed.custom()
			.stream()
			.filter(answer -> text(answer.question()) != null && text(answer.answer()) != null)
			.map(answer -> new CustomAnswer(answer.question().strip(), answer.answer().strip()))
			.toList();
		entity.replace(text(changed.workAuthorization()), changed.needsSponsorship(), text(changed.noticePeriod()),
				text(changed.earliestStart()), text(changed.salaryExpectation()), changed.willingToRelocate(),
				text(changed.workPreference()), changed.yearsOfExperience(), changed.coverLetter(),
				changed.declineVoluntary(), JSON.writeValueAsString(custom));
		ApplicationAnswers saved = toAnswers(this.answers.saveAndFlush(entity));
		log.info("Saved the application answers of profile {}", userId);
		return saved;
	}

	@Override
	@Transactional
	public ApplicationAnswers learn(long userId, String question, String answer) {
		String asked = text(question);
		String given = text(answer);
		if (asked == null || given == null) {
			throw new IllegalArgumentException("An answer to learn needs a question and an answer");
		}
		ApplicationAnswers answers = findAnswers(userId);
		List<CustomAnswer> custom = new ArrayList<>(answers.custom());
		custom.removeIf(known -> sameQuestion(known.question(), asked));
		custom.add(new CustomAnswer(asked.replaceAll("\\s+", " "), given));
		return saveAnswers(userId, answers.toBuilder().custom(custom).build());
	}

	@Override
	@Transactional
	public List<ApplyItem> queue(long userId, Collection<Long> applicationIds, ApplyMode mode) {
		List<ApplyItemEntity> queued = new ArrayList<>();
		for (long applicationId : Set.copyOf(applicationIds)) {
			ApplicationDetails job = this.applications.findById(userId, applicationId).orElse(null);
			if (job == null || job.status() != ApplicationStatus.SAVED || !isWebAddress(job.link())
					|| this.items.existsByUserIdAndApplicationIdAndStatusIn(userId, applicationId, ApplyStatus.WAITING)) {
				continue;
			}
			queued.add(this.items.save(new ApplyItemEntity(userId, applicationId, mode)));
		}
		log.info("Queued {} jobs of profile {} for Claude to apply to ({})", queued.size(), userId, mode);
		return toApplyItems(userId, queued);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ApplyItem> findItems(long userId, int limit) {
		return toApplyItems(userId, this.items.findByUserIdOrderByCreatedAtDescIdDesc(userId, Limit.of(limit)));
	}

	@Override
	@Transactional(readOnly = true)
	public List<ApplyItem> findOpenItems(long userId) {
		return toApplyItems(userId, this.items.findByUserIdAndStatusInOrderByCreatedAtAscIdAsc(userId,
				EnumSet.of(ApplyStatus.QUEUED, ApplyStatus.APPLYING)));
	}

	/**
	 * @throws ApplyItemNotFoundException if the profile has no such item
	 * @throws BusinessRuleException if it's no longer waiting
	 */
	@Override
	@Transactional
	public ApplyItem start(long userId, long itemId) {
		ApplyItemEntity item = getOwnedItem(userId, itemId);
		if (!item.getStatus().isOpen()) {
			throw finished(item);
		}
		item.start();
		return toApplyItems(userId, List.of(item)).getFirst();
	}

	/**
	 * @throws ApplyItemNotFoundException if the profile has no such item
	 * @throws BusinessRuleException if the result isn't one, or the job is done
	 */
	@Override
	@Transactional
	public ApplyItem report(long userId, long itemId, ApplyStatus result, @Nullable String site, @Nullable String note,
			@Nullable String question) {
		if (!ApplyStatus.RESULTS.contains(result)) {
			throw new BusinessRuleException(ApplyErrorCode.NOT_A_RESULT, result + " isn't a result",
					String.valueOf(result));
		}
		ApplyItemEntity item = getOwnedItem(userId, itemId);
		boolean sentAfterReview = item.getStatus() == ApplyStatus.FILLED && result == ApplyStatus.APPLIED;
		if (!item.getStatus().isOpen() && !sentAfterReview) {
			throw finished(item);
		}
		item.finish(result, text(site), text(note), (result == ApplyStatus.NEEDS_ANSWER) ? text(question) : null);
		if (result == ApplyStatus.APPLIED) {
			moveToApplied(userId, item);
		}
		log.info("Claude {} application {} of profile {} (item {})", result, item.getApplicationId(), userId, itemId);
		return toApplyItems(userId, List.of(item)).getFirst();
	}

	@Override
	@Transactional
	public ApplyItem markSent(long userId, long itemId) {
		ApplyItemEntity item = getOwnedItem(userId, itemId);
		if (item.getStatus() != ApplyStatus.FILLED) {
			throw finished(item);
		}
		item.finish(ApplyStatus.APPLIED, null, null, null);
		moveToApplied(userId, item);
		log.info("Profile {} sent the form Claude filled in for application {}", userId, item.getApplicationId());
		return toApplyItems(userId, List.of(item)).getFirst();
	}

	@Override
	@Transactional
	public ApplyItem cancel(long userId, long itemId) {
		ApplyItemEntity item = getOwnedItem(userId, itemId);
		if (item.getStatus() == ApplyStatus.APPLIED) {
			throw finished(item);
		}
		item.finish(ApplyStatus.CANCELLED, null, null, null);
		return toApplyItems(userId, List.of(item)).getFirst();
	}

	@Override
	@Transactional
	public ApplyItem answer(long userId, long itemId, String answer) {
		ApplyItemEntity item = getOwnedItem(userId, itemId);
		if (item.getStatus() != ApplyStatus.NEEDS_ANSWER || item.getQuestion() == null || text(answer) == null) {
			throw finished(item);
		}
		learn(userId, item.getQuestion(), answer);
		item.requeue();
		return toApplyItems(userId, List.of(item)).getFirst();
	}

	@Override
	@Transactional
	public ApplyItem retry(long userId, long itemId) {
		ApplyItemEntity item = getOwnedItem(userId, itemId);
		if (!item.getStatus().canRetry()) {
			throw finished(item);
		}
		item.requeue();
		return toApplyItems(userId, List.of(item)).getFirst();
	}

	/**
	 * A Saved job moves to Applied, by Claude, now, with what Claude said on its timeline. One the profile moved on
	 * meanwhile, e.g. applied to by hand, stays as it is.
	 */
	private void moveToApplied(long userId, ApplyItemEntity item) {
		ApplicationDetails job = this.applications.findById(userId, item.getApplicationId()).orElse(null);
		if (job == null || job.status() != ApplicationStatus.SAVED) {
			return;
		}
		String note = (item.getSite() != null && item.getNote() != null) ? item.getSite() + ": " + item.getNote()
				: (item.getSite() != null) ? item.getSite() : item.getNote();
		this.applications.addEvent(userId, item.getApplicationId(), NewTimelineEvent.builder()
			.eventDate(LocalDate.now())
			.eventTime(LocalTime.now().truncatedTo(ChronoUnit.MINUTES))
			.stageCategory(ApplicationStatus.APPLIED)
			.note(note)
			.appliedBy(AppliedBy.CLAUDE)
			.build());
	}

	private ApplyItemEntity getOwnedItem(long userId, long itemId) {
		return this.items.findByIdAndUserId(itemId, userId).orElseThrow(() -> new ApplyItemNotFoundException(itemId));
	}

	private static BusinessRuleException finished(ApplyItemEntity item) {
		return new BusinessRuleException(ApplyErrorCode.APPLY_ITEM_FINISHED,
				"Apply item " + item.getId() + " is " + item.getStatus(), String.valueOf(item.getId()));
	}

	/**
	 * With each job's title, company and link, read once for all of them.
	 */
	private List<ApplyItem> toApplyItems(long userId, List<ApplyItemEntity> entities) {
		if (entities.isEmpty()) {
			return List.of();
		}
		Map<Long, ApplicationSummary> jobs = this.applications.findAll(userId, null)
			.stream()
			.collect(Collectors.toMap(ApplicationSummary::id, Function.identity()));
		return entities.stream().filter(item -> jobs.containsKey(item.getApplicationId())).map(item -> {
			ApplicationSummary job = jobs.get(item.getApplicationId());
			return ApplyItem.builder()
				.id(item.getId())
				.applicationId(item.getApplicationId())
				.title(job.title())
				.company(job.company())
				.link(job.link())
				.mode(item.getMode())
				.status(item.getStatus())
				.site(item.getSite())
				.note(item.getNote())
				.question(item.getQuestion())
				.createdAt(item.getCreatedAt())
				.finishedAt(item.getFinishedAt())
				.build();
		}).toList();
	}

	private static ApplicationAnswers toAnswers(ApplicationAnswersEntity entity) {
		return ApplicationAnswers.builder()
			.workAuthorization(entity.getWorkAuthorization())
			.needsSponsorship(entity.getNeedsSponsorship())
			.noticePeriod(entity.getNoticePeriod())
			.earliestStart(entity.getEarliestStart())
			.salaryExpectation(entity.getSalaryExpectation())
			.willingToRelocate(entity.getWillingToRelocate())
			.workPreference(entity.getWorkPreference())
			.yearsOfExperience(entity.getYearsOfExperience())
			.coverLetter(entity.getCoverLetter())
			.declineVoluntary(entity.isDeclineVoluntary())
			.custom(JSON.readValue(entity.getCustom(), new TypeReference<List<CustomAnswer>>() {
			}))
			.updatedAt(entity.getUpdatedAt())
			.build();
	}

	private static boolean isWebAddress(@Nullable String link) {
		return link != null && (link.startsWith("https://") || link.startsWith("http://"));
	}

	/** Forms ask the same question with other spacing or case now and then. */
	private static boolean sameQuestion(String one, String other) {
		return normalized(one).equals(normalized(other));
	}

	private static String normalized(String question) {
		return question.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private static @Nullable String text(@Nullable String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

}
