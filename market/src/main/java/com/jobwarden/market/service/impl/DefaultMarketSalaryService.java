package com.jobwarden.market.service.impl;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.ExchangeRateService;
import com.jobwarden.market.dto.MarketCountry;
import com.jobwarden.market.dto.MarketFigure;
import com.jobwarden.market.dto.MarketQuery;
import com.jobwarden.market.dto.MarketSalaries;
import com.jobwarden.market.dto.SurveyStatus;
import com.jobwarden.market.entity.MarketSalaryEntity;
import com.jobwarden.market.enums.MarketSource;
import com.jobwarden.market.repository.MarketSalaryRepository;
import com.jobwarden.market.service.MarketSalaryService;
import com.jobwarden.market.service.impl.AdzunaSalaries.AdsFigure;
import com.jobwarden.market.service.impl.EurostatSalaries.EarningsTable;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@CustomLog
@RequiredArgsConstructor
class DefaultMarketSalaryService implements MarketSalaryService {

	/** What the role and skill fields offer before the survey is imported. */
	static final List<String> COMMON_ROLES = List.of("Developer, full-stack", "Developer, back-end",
			"Developer, front-end", "Developer, mobile", "Architect, software or solutions", "Engineering manager",
			"DevOps engineer or professional", "Data engineer", "AI/ML engineer", "Data scientist");

	static final List<String> COMMON_SKILLS = List.of("JavaScript", "SQL", "Python", "TypeScript", "Java", "C#",
			"Go", "Kotlin", "PHP", "C++", "Rust");

	/** The most countries one query asks for: each may cost a request to Adzuna. */
	static final int MAX_COUNTRIES = 12;

	private final MarketSalaryRepository salaries;

	private final MarketSalaryStore store;

	private final StackOverflowSurvey survey;

	private final AdzunaSalaries adzuna;

	private final EurostatSalaries eurostat;

	private final ExchangeRateService exchangeRates;

	private final AtomicBoolean importing = new AtomicBoolean();

	private volatile @Nullable String importError;

	@Override
	@Transactional(readOnly = true)
	public List<String> roles() {
		List<String> roles = this.salaries.findRoles(MarketSource.STACK_OVERFLOW).stream().map(MarketSalaryEntity::getRole).toList();
		return roles.isEmpty() ? COMMON_ROLES : roles;
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> skills() {
		List<String> skills = this.salaries.findSkills(MarketSource.STACK_OVERFLOW).stream().map(MarketSalaryEntity::getSkill).toList();
		return skills.isEmpty() ? COMMON_SKILLS : skills;
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> surveyCountries() {
		return this.salaries.findCountries(MarketSource.STACK_OVERFLOW).stream().map(MarketSalaryEntity::getCountry).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public SurveyStatus surveyStatus() {
		Optional<MarketSalaryEntity> latest = this.salaries.findFirstBySourceOrderByImportedAtDesc(MarketSource.STACK_OVERFLOW);
		return new SurveyStatus(latest.map(MarketSalaryEntity::getSurveyYear).orElse(null),
				this.salaries.countBySource(MarketSource.STACK_OVERFLOW), latest.map(MarketSalaryEntity::getImportedAt).orElse(null),
				this.importing.get(), this.importError);
	}

	/**
	 * In a virtual thread: the download takes about half a minute, reading the answers a few seconds more.
	 */
	@Override
	public boolean importSurvey() {
		if (!this.importing.compareAndSet(false, true)) {
			return false;
		}
		this.importError = null;
		log.info("Importing the {} Stack Overflow survey", this.survey.year());
		Thread.ofVirtual().name("survey-import").start(() -> {
			try {
				this.store.replaceSurvey(this.survey.year(), this.survey.download());
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				this.importError = ex.toString();
			} catch (Exception ex) {
				log.warn("Couldn't import the Stack Overflow survey: {}", ex.toString());
				this.importError = (ex.getMessage() != null) ? ex.getMessage() : ex.toString();
			} finally {
				this.importing.set(false);
			}
		});
		return true;
	}

	/**
	 * Not in one transaction: it asks Adzuna and Eurostat too, and a connection would wait for them.
	 */
	@Override
	public MarketSalaries find(MarketQuery query, String currency, SalaryPeriod period) {
		String role = (query.role() != null) ? query.role() : "";
		String skill = (query.skill() != null) ? query.skill() : "";
		List<String> countries = query.countries()
			.stream()
			.map(country -> country.toUpperCase(Locale.ROOT))
			.distinct()
			.limit(MAX_COUNTRIES)
			.toList();
		String search = AdzunaSearch.of(query.role(), query.skill());

		List<String> withWorld = new ArrayList<>(countries);
		withWorld.add("");
		Map<String, MarketSalaryEntity> fromSurvey = this.salaries
			.findBySourceAndRoleAndSkillAndCountryIn(MarketSource.STACK_OVERFLOW, role, skill, withWorld)
			.stream()
			.collect(Collectors.toMap(MarketSalaryEntity::getCountry, figure -> figure));
		Map<String, AdsFigure> fromAds = ads(countries, search);
		Optional<EarningsTable> fromEurostat = this.eurostat.current();

		Converter converter = new Converter(currency, period);
		List<MarketCountry> rows = new ArrayList<>();
		for (String country : withWorld) {
			List<MarketFigure> figures = new ArrayList<>();
			MarketSalaryEntity answers = fromSurvey.get(country);
			if (answers != null) {
				figures.add(new MarketFigure(MarketSource.STACK_OVERFLOW, answers.getSurveyYear(),
						converter.convert(answers.getLowerQuartile(), answers.getCurrency()),
						converter.convert(answers.getMedian(), answers.getCurrency()),
						converter.convert(answers.getUpperQuartile(), answers.getCurrency()), answers.getSampleSize()));
			}
			AdsFigure ads = fromAds.get(country);
			if (ads != null && this.exchangeRates.knows(ads.currency())) {
				figures.add(new MarketFigure(MarketSource.ADZUNA, LocalDate.now().getYear(),
						converter.convert(ads.lowerQuartile(), ads.currency()), converter.convert(ads.median(), ads.currency()),
						converter.convert(ads.upperQuartile(), ads.currency()), ads.ads()));
			}
			BigDecimal earnings = fromEurostat.map(table -> table.byCountry().get(country)).orElse(null);
			if (earnings != null) {
				figures.add(new MarketFigure(MarketSource.EUROSTAT, fromEurostat.get().year(), null,
						converter.convert(earnings, EurostatSalaries.CURRENCY), null, null));
			}
			if (!country.isEmpty() || !figures.isEmpty()) {
				rows.add(new MarketCountry(country, figures));
			}
		}
		log.debug("Market salaries of {} with {} for {} countries", role, skill, countries.size());
		return MarketSalaries.builder()
			.role(query.role())
			.skill(query.skill())
			.currency(currency)
			.period(period)
			.countries(rows)
			.survey(surveyStatus())
			.adzunaSearch(search)
			.adzunaConfigured(this.adzuna.configured())
			.eurostatYear(fromEurostat.map(EarningsTable::year).orElse(null))
			.euAverage(fromEurostat.map(EarningsTable::euAverage)
				.map(average -> converter.convert(average, EurostatSalaries.CURRENCY))
				.orElse(null))
			.build();
	}

	/** Asks Adzuna for the countries it covers, all at once: each is a request when it isn't kept yet. */
	private Map<String, AdsFigure> ads(List<String> countries, String search) {
		Map<String, AdsFigure> figures = new LinkedHashMap<>();
		if (!this.adzuna.configured()) {
			return figures;
		}
		Map<String, Future<Optional<AdsFigure>>> asked = new LinkedHashMap<>();
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (String country : countries) {
				if (this.adzuna.covers(country)) {
					asked.put(country, executor.submit(() -> this.adzuna.find(country, search)));
				}
			}
			for (Map.Entry<String, Future<Optional<AdsFigure>>> answer : asked.entrySet()) {
				answer.getValue().get().ifPresent(figure -> figures.put(answer.getKey(), figure));
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException ex) {
			throw new IllegalStateException("Asking Adzuna failed", ex.getCause());
		}
		return figures;
	}

	/** A year in a source's currency, to the currency and period shown. */
	private final class Converter {

		private final String currency;

		private final SalaryPeriod period;

		Converter(String currency, SalaryPeriod period) {
			this.currency = currency;
			this.period = period;
		}

		BigDecimal convert(BigDecimal perYear, String from) {
			return DefaultMarketSalaryService.this.exchangeRates.convert(perYear, from, this.currency)
				.divide(BigDecimal.valueOf(this.period.perYear()), MathContext.DECIMAL64)
				.setScale(2, RoundingMode.HALF_UP);
		}

	}

}
