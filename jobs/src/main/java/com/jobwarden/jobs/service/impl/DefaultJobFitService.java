package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.FitFlag;
import com.jobwarden.jobs.dto.FitRequirement;
import com.jobwarden.jobs.dto.JobFit;
import com.jobwarden.jobs.entity.JobFitEntity;
import com.jobwarden.jobs.entity.JobFitRequirementEmbeddable;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.FitFlagKind;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.mapper.ApplicationMapper;
import com.jobwarden.jobs.repository.ApplicationRepository;
import com.jobwarden.jobs.repository.FitScore;
import com.jobwarden.jobs.repository.JobFitRepository;
import com.jobwarden.jobs.service.JobFitService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@CustomLog
@RequiredArgsConstructor
class DefaultJobFitService implements JobFitService {

	/** The jobs worth scoring: those still to apply to, and those waiting on an answer. */
	private static final EnumSet<ApplicationStatus> TO_SCORE = EnumSet.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED);

	private final JobFitRepository fits;

	private final ApplicationRepository applications;

	private final Clock clock;

	@Override
	public Optional<JobFit> find(long userId, long applicationId) {
		return this.fits.findByUserIdAndApplicationId(userId, applicationId).map(DefaultJobFitService::toFit);
	}

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 */
	@Override
	@Transactional
	public JobFit save(long userId, long applicationId, @Nullable String summary, List<FitRequirement> requirements) {
		BigDecimal score = JobFit.score(requirements);
		if (this.applications.findByIdAndUserId(applicationId, userId).isEmpty()) {
			throw new ApplicationNotFoundException(applicationId);
		}
		JobFitEntity fit = this.fits.findByUserIdAndApplicationId(userId, applicationId)
			.orElseGet(() -> new JobFitEntity(userId, applicationId));
		fit.replace(score, summary, requirements.stream()
			.map(requirement -> new JobFitRequirementEmbeddable(requirement.text(), requirement.importance(),
					requirement.match(), requirement.note()))
			.toList());
		JobFitEntity saved = this.fits.save(fit);
		log.info("Scored application {} of profile {}: {}", applicationId, userId, score);
		return toFit(saved);
	}

	@Override
	@Transactional
	public void delete(long userId, long applicationId) {
		this.fits.findByUserIdAndApplicationId(userId, applicationId).ifPresent(fit -> {
			this.fits.delete(fit);
			log.info("Deleted the fit of application {} of profile {}", applicationId, userId);
		});
	}

	@Override
	public Map<Long, BigDecimal> findScores(long userId, Collection<Long> applicationIds) {
		if (applicationIds.isEmpty()) {
			return Map.of();
		}
		return this.fits.findScores(userId, applicationIds)
			.stream()
			.collect(Collectors.toMap(FitScore::applicationId, FitScore::score));
	}

	@Override
	public List<ApplicationSummary> findUnscored(long userId, int limit) {
		return this.applications.findUnscored(userId, TO_SCORE, PageRequest.of(0, limit));
	}

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 */
	@Override
	public List<FitFlag> findFlags(long userId, long applicationId, @Nullable Boolean needsSponsorship) {
		ApplicationDetails job = this.applications.findByIdAndUserId(applicationId, userId)
			.map(ApplicationMapper::toApplicationDetails)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
		List<FitFlag> flags = new ArrayList<>();
		if (Boolean.TRUE.equals(needsSponsorship) && job.description() != null) {
			SponsorshipCheck.rulesOut(job.description())
				.ifPresent(evidence -> flags.add(new FitFlag(FitFlagKind.NO_SPONSORSHIP, evidence, null, null)));
		}
		if (job.company() != null) {
			this.applications.findSameJob(userId, applicationId, job.title(), job.company())
				.stream()
				.findFirst()
				.ifPresent(other -> flags.add(new FitFlag(FitFlagKind.REPOSTED, null, null, other)));
		}
		this.applications.findDatePosted(userId, applicationId)
			.flatMap(DefaultJobFitService::day)
			.filter(posted -> posted.isBefore(LocalDate.now(this.clock).minusDays(OPEN_LONG_AFTER_DAYS)))
			.ifPresent(posted -> flags.add(new FitFlag(FitFlagKind.OPEN_LONG, null, posted, null)));
		return flags;
	}

	/** The day of a date the job data gives, with or without a time after it. */
	private static Optional<LocalDate> day(String date) {
		try {
			return Optional.of(LocalDate.parse(date.strip().substring(0, Math.min(10, date.strip().length()))));
		} catch (DateTimeParseException notADate) {
			return Optional.empty();
		}
	}

	private static JobFit toFit(JobFitEntity fit) {
		return new JobFit(fit.getScore(), fit.getSummary(), fit.getRequirements()
			.stream()
			.map(requirement -> new FitRequirement(requirement.getText(), requirement.getImportance(),
					requirement.getMatch(), requirement.getNote()))
			.toList(), fit.getUpdatedAt());
	}

}
