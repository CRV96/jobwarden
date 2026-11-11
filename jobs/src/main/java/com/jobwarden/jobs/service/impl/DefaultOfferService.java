package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.ComparedOffer;
import com.jobwarden.jobs.dto.JobOffer;
import com.jobwarden.jobs.dto.NegotiationBrief;
import com.jobwarden.jobs.dto.OfferComparison;
import com.jobwarden.jobs.dto.SalaryEntry;
import com.jobwarden.jobs.dto.SalaryRange;
import com.jobwarden.jobs.entity.JobOfferEntity;
import com.jobwarden.jobs.entity.NegotiationBriefEntity;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.repository.ApplicationRepository;
import com.jobwarden.jobs.repository.JobOfferRepository;
import com.jobwarden.jobs.repository.NegotiationBriefRepository;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.JobFitService;
import com.jobwarden.jobs.service.OfferService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The offers' salaries are read and converted like the salaries page's ({@link DefaultSalaryService}), with the same
 * exchange rates, so the two pages agree.
 */
@Service
@Transactional(readOnly = true)
@CustomLog
@RequiredArgsConstructor
class DefaultOfferService implements OfferService {

	private final JobOfferRepository offers;

	private final NegotiationBriefRepository briefs;

	private final ApplicationRepository applicationRows;

	private final ApplicationService applications;

	private final JobFitService fits;

	private final ExchangeRates exchangeRates;

	@Override
	public JobOffer find(long userId, long applicationId) {
		return this.offers.findByUserIdAndApplicationId(userId, applicationId)
			.map(DefaultOfferService::toOffer)
			.orElse(JobOffer.NONE);
	}

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 */
	@Override
	@Transactional
	public JobOffer save(long userId, long applicationId, JobOffer offer) {
		requireApplication(userId, applicationId);
		JobOfferEntity entity = this.offers.findByUserIdAndApplicationId(userId, applicationId)
			.orElseGet(() -> new JobOfferEntity(userId, applicationId));
		entity.replace(tidied(offer));
		JobOffer saved = toOffer(this.offers.saveAndFlush(entity));
		log.info("Saved the offer terms of application {} of profile {}", applicationId, userId);
		return saved;
	}

	@Override
	public OfferComparison compare(long userId, @Nullable String currency, SalaryPeriod period) {
		RateTable rates = this.exchangeRates.current();
		SalaryParser parser = new SalaryParser(rates.currencies());
		String shownIn = (currency != null && rates.currencies().contains(currency)) ? currency
				: DefaultSalaryService.DEFAULT_CURRENCY;

		List<ApplicationSummary> jobs = this.applications.findAll(userId, ApplicationStatus.OFFER)
			.stream()
			.sorted(Comparator.comparing(ApplicationSummary::lastModified).reversed())
			.toList();
		List<Long> ids = jobs.stream().map(ApplicationSummary::id).toList();
		Map<Long, JobOffer> terms = ids.isEmpty() ? Map.of() : this.offers.findByUserIdAndApplicationIdIn(userId, ids)
			.stream()
			.collect(Collectors.toMap(JobOfferEntity::getApplicationId, DefaultOfferService::toOffer));
		Map<Long, BigDecimal> scores = ids.isEmpty() ? Map.of() : this.fits.findScores(userId, ids);
		Set<Long> withBrief = ids.isEmpty() ? Set.of()
				: new HashSet<>(this.briefs.findApplicationIdsWithBrief(userId, ids));

		List<ComparedOffer> compared = new ArrayList<>();
		List<SalaryRange> read = new ArrayList<>();
		for (ApplicationSummary job : jobs) {
			JobOffer offer = terms.getOrDefault(job.id(), JobOffer.NONE);
			String text = (offer.baseSalary() != null) ? offer.baseSalary() : blankToNull(job.salary());
			Optional<SalaryRange> range = parser.parse(text);
			range.ifPresent(read::add);
			SalaryEntry salary = range.map(found -> DefaultSalaryService.entry(job, found, rates, shownIn, period))
				.orElse(null);
			compared.add(new ComparedOffer(job, offer, text, salary, offer.baseSalary() == null && text != null,
					scores.get(job.id()), withBrief.contains(job.id())));
		}
		return OfferComparison.builder()
			.currency(shownIn)
			.period(period)
			.offers(compared)
			.currencies(DefaultSalaryService.currencies(read, rates))
			.ratesDate(rates.date())
			.build();
	}

	@Override
	public Optional<NegotiationBrief> findBrief(long userId, long applicationId) {
		return this.briefs.findByUserIdAndApplicationId(userId, applicationId).map(DefaultOfferService::toBrief);
	}

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 */
	@Override
	@Transactional
	public NegotiationBrief saveBrief(long userId, long applicationId, NegotiationBrief brief) {
		requireApplication(userId, applicationId);
		NegotiationBriefEntity entity = this.briefs.findByUserIdAndApplicationId(userId, applicationId)
			.orElseGet(() -> new NegotiationBriefEntity(userId, applicationId));
		entity.replace(brief);
		NegotiationBrief saved = toBrief(this.briefs.save(entity));
		log.info("Saved the negotiation brief of application {} of profile {}", applicationId, userId);
		return saved;
	}

	@Override
	@Transactional
	public void deleteBrief(long userId, long applicationId) {
		this.briefs.findByUserIdAndApplicationId(userId, applicationId).ifPresent(brief -> {
			this.briefs.delete(brief);
			log.info("Deleted the negotiation brief of application {} of profile {}", applicationId, userId);
		});
	}

	private void requireApplication(long userId, long applicationId) {
		if (this.applicationRows.findFollowUpDatesById(userId, List.of(applicationId)).isEmpty()) {
			throw new ApplicationNotFoundException(applicationId);
		}
	}

	/** Blank text is no value, and the rest is stripped. */
	private static JobOffer tidied(JobOffer offer) {
		return offer.toBuilder()
			.baseSalary(blankToNull(offer.baseSalary()))
			.bonus(blankToNull(offer.bonus()))
			.equity(blankToNull(offer.equity()))
			.signingBonus(blankToNull(offer.signingBonus()))
			.timeOff(blankToNull(offer.timeOff()))
			.benefits(blankToNull(offer.benefits()))
			.notes(blankToNull(offer.notes()))
			.updatedAt(null)
			.build();
	}

	private static @Nullable String blankToNull(@Nullable String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

	private static JobOffer toOffer(JobOfferEntity offer) {
		return JobOffer.builder()
			.baseSalary(offer.getBaseSalary())
			.bonus(offer.getBonus())
			.equity(offer.getEquity())
			.signingBonus(offer.getSigningBonus())
			.timeOff(offer.getTimeOff())
			.benefits(offer.getBenefits())
			.startDate(offer.getStartDate())
			.respondBy(offer.getRespondBy())
			.notes(offer.getNotes())
			.updatedAt(offer.getUpdatedAt())
			.build();
	}

	private static NegotiationBrief toBrief(NegotiationBriefEntity brief) {
		return NegotiationBrief.builder()
			.position(brief.getPosition())
			.target(brief.getTarget())
			.walkAway(brief.getWalkAway())
			.leverage(brief.getLeverage())
			.asks(brief.getAsks())
			.script(brief.getScript())
			.email(brief.getEmail())
			.watchOut(brief.getWatchOut())
			.sources(brief.getSources())
			.updatedAt(brief.getUpdatedAt())
			.build();
	}

}
