package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.ComparedOffer;
import com.jobwarden.jobs.dto.JobOffer;
import com.jobwarden.jobs.dto.NegotiationBrief;
import com.jobwarden.jobs.dto.OfferComparison;
import com.jobwarden.jobs.dto.OfferExtra;
import com.jobwarden.jobs.dto.OfferTotal;
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
	public Optional<OfferTotal> total(JobOffer offer) {
		RateTable rates = this.exchangeRates.current();
		Optional<SalaryRange> base = offer.baseSalaryRange()
			.or(() -> new SalaryParser(rates.currencies()).parse(offer.baseSalary()));
		// In the base salary's currency; without one, the first extra's
		Optional<String> currency = base.map(SalaryRange::currency)
			.or(() -> offer.extras()
				.stream()
				.filter(extra -> extra.amount() != null && extra.unit() != null && !extra.isShareOfBase())
				.map(OfferExtra::unit)
				.findFirst());
		return currency.flatMap(shownIn -> OfferTotals.total(offer.extras(),
				base.map(range -> range.low()
					.add(range.high())
					.divide(BigDecimal.TWO)
					.multiply(BigDecimal.valueOf(Objects.requireNonNullElse(range.period(), SalaryPeriod.YEAR).perYear())))
					.orElse(null),
				shownIn, rates));
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
			String offered = offer.baseSalaryText();
			String text = (offered != null) ? offered : blankToNull(job.salary());
			// The figure picked as it is; words, the offer's from before or the posting's, as they read
			Optional<SalaryRange> range = offer.baseSalaryRange().or(() -> parser.parse(text));
			range.ifPresent(read::add);
			SalaryEntry salary = range.map(found -> DefaultSalaryService.entry(job, found, rates, shownIn, period))
				.orElse(null);
			// All in, a year of it, in the comparison's currency
			BigDecimal baseYearly = (salary != null) ? salary.midpoint().multiply(BigDecimal.valueOf(period.perYear())) : null;
			OfferTotal total = OfferTotals.total(offer.extras(), baseYearly, shownIn, rates).orElse(null);
			compared.add(new ComparedOffer(job, offer, text, salary, offered == null && text != null, total,
					scores.get(job.id()), withBrief.contains(job.id())));
		}
		return OfferComparison.builder()
			.currency(shownIn)
			.period(period)
			.offers(compared)
			.currencies(currencies(read, rates, shownIn))
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

	/**
	 * Blank text is no value, and the rest is stripped. A base salary is a figure with its currency, or none: a
	 * currency or a period without an amount says nothing, and once there's a figure, the words from before go. An
	 * extra's amount comes with its unit, and an extra with neither an amount nor a note goes.
	 */
	private static JobOffer tidied(JobOffer offer) {
		boolean figure = offer.baseSalaryAmount() != null && blankToNull(offer.baseSalaryCurrency()) != null;
		return offer.toBuilder()
			.baseSalaryAmount(figure ? offer.baseSalaryAmount() : null)
			.baseSalaryCurrency(figure ? Objects.requireNonNull(blankToNull(offer.baseSalaryCurrency())).toUpperCase(Locale.ROOT)
					: null)
			.baseSalaryPeriod(figure ? Objects.requireNonNullElse(offer.baseSalaryPeriod(), SalaryPeriod.YEAR) : null)
			.baseSalary(figure ? null : blankToNull(offer.baseSalary()))
			.extras(offer.extras().stream().map(DefaultOfferService::tidied).flatMap(Optional::stream).toList())
			.timeOff(blankToNull(offer.timeOff()))
			.benefits(blankToNull(offer.benefits()))
			.notes(blankToNull(offer.notes()))
			.updatedAt(null)
			.build();
	}

	private static Optional<OfferExtra> tidied(OfferExtra extra) {
		String unit = blankToNull(extra.unit());
		boolean amount = extra.amount() != null && extra.amount().signum() > 0 && unit != null;
		String note = blankToNull(extra.note());
		if (!amount && note == null) {
			return Optional.empty();
		}
		return Optional.of(new OfferExtra(extra.kind(), amount ? extra.amount() : null,
				amount ? Objects.requireNonNull(unit).toUpperCase(Locale.ROOT) : null, extra.frequency(), note));
	}

	private static @Nullable String blankToNull(@Nullable String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

	private static JobOffer toOffer(JobOfferEntity offer) {
		return JobOffer.builder()
			.baseSalaryAmount(offer.getBaseSalaryAmount())
			.baseSalaryCurrency(offer.getBaseSalaryCurrency())
			.baseSalaryPeriod(offer.getBaseSalaryPeriod())
			.baseSalary(offer.getBaseSalary())
			.extras(offer.extras())
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

	/**
	 * The currencies to pick from: the common ones and the offers', and the one shown, so picking something else on the
	 * page never changes it unasked.
	 */
	private static List<String> currencies(List<SalaryRange> salaries, RateTable rates, String shownIn) {
		TreeSet<String> currencies = new TreeSet<>(DefaultSalaryService.currencies(salaries, rates));
		currencies.add(shownIn);
		return List.copyOf(currencies);
	}

}
