package com.jobwarden.jobs.service.impl;

import java.time.Clock;
import java.util.Optional;

import com.jobwarden.jobs.dto.CompanyResearch;
import com.jobwarden.jobs.entity.CompanyResearchEntity;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.repository.ApplicationRepository;
import com.jobwarden.jobs.repository.CompanyResearchRepository;
import com.jobwarden.jobs.service.CompanyResearchService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@CustomLog
@RequiredArgsConstructor
class DefaultCompanyResearchService implements CompanyResearchService {

	private final CompanyResearchRepository research;

	private final ApplicationRepository applications;

	private final Clock clock;

	@Override
	public Optional<CompanyResearch> find(long userId, long applicationId) {
		return this.research.findByUserIdAndApplicationId(userId, applicationId)
			.map(DefaultCompanyResearchService::toResearch);
	}

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 */
	@Override
	@Transactional
	public CompanyResearch save(long userId, long applicationId, CompanyResearch found) {
		if (!this.applications.existsByIdAndUserId(applicationId, userId)) {
			throw new ApplicationNotFoundException(applicationId);
		}
		CompanyResearchEntity entity = this.research.findByUserIdAndApplicationId(userId, applicationId)
			.orElseGet(() -> new CompanyResearchEntity(userId, applicationId));
		entity.replace(found, this.clock.instant());
		CompanyResearchEntity saved = this.research.save(entity);
		log.info("Saved the company research of application {} of profile {}", applicationId, userId);
		return toResearch(saved);
	}

	@Override
	@Transactional
	public void delete(long userId, long applicationId) {
		this.research.findByUserIdAndApplicationId(userId, applicationId).ifPresent(found -> {
			this.research.delete(found);
			log.info("Deleted the company research of application {} of profile {}", applicationId, userId);
		});
	}

	private static CompanyResearch toResearch(CompanyResearchEntity found) {
		return CompanyResearch.builder()
			.company(found.getCompany())
			.ratings(found.getRatings())
			.pros(found.getPros())
			.cons(found.getCons())
			.salaries(found.getSalaries())
			.benefits(found.getBenefits())
			.news(found.getNews())
			.interviews(found.getInterviews())
			.watchOut(found.getWatchOut())
			.sources(found.getSources())
			.updatedAt(found.getUpdatedAt())
			.build();
	}

}
