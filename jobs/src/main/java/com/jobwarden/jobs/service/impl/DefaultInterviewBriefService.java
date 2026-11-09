package com.jobwarden.jobs.service.impl;

import java.util.List;
import java.util.Optional;

import com.jobwarden.jobs.dto.InterviewBrief;
import com.jobwarden.jobs.entity.InterviewBriefEntity;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.repository.ApplicationRepository;
import com.jobwarden.jobs.repository.InterviewBriefRepository;
import com.jobwarden.jobs.service.InterviewBriefService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@CustomLog
@RequiredArgsConstructor
class DefaultInterviewBriefService implements InterviewBriefService {

	private final InterviewBriefRepository briefs;

	private final ApplicationRepository applications;

	@Override
	public Optional<InterviewBrief> find(long userId, long applicationId) {
		return this.briefs.findByUserIdAndApplicationId(userId, applicationId).map(DefaultInterviewBriefService::toBrief);
	}

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 */
	@Override
	@Transactional
	public InterviewBrief save(long userId, long applicationId, InterviewBrief brief) {
		if (this.applications.findFollowUpDatesById(userId, List.of(applicationId)).isEmpty()) {
			throw new ApplicationNotFoundException(applicationId);
		}
		InterviewBriefEntity entity = this.briefs.findByUserIdAndApplicationId(userId, applicationId)
			.orElseGet(() -> new InterviewBriefEntity(userId, applicationId));
		entity.replace(brief);
		InterviewBriefEntity saved = this.briefs.save(entity);
		log.info("Saved the interview brief of application {} of profile {}", applicationId, userId);
		return toBrief(saved);
	}

	@Override
	@Transactional
	public void delete(long userId, long applicationId) {
		this.briefs.findByUserIdAndApplicationId(userId, applicationId).ifPresent(brief -> {
			this.briefs.delete(brief);
			log.info("Deleted the interview brief of application {} of profile {}", applicationId, userId);
		});
	}

	private static InterviewBrief toBrief(InterviewBriefEntity brief) {
		return InterviewBrief.builder()
			.company(brief.getCompany())
			.role(brief.getRole())
			.questions(brief.getQuestions())
			.stories(brief.getStories())
			.askThem(brief.getAskThem())
			.watchOut(brief.getWatchOut())
			.sources(brief.getSources())
			.updatedAt(brief.getUpdatedAt())
			.build();
	}

}
