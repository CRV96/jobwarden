package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.SalaryEntry;
import com.jobwarden.jobs.dto.SalaryOverview;
import com.jobwarden.jobs.dto.SalaryRange;
import com.jobwarden.jobs.dto.StatusSalary;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.SalaryService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

/**
 * Converts each salary to a year in euros to compare them, then to the currency and period asked for.
 */
@Service
@RequiredArgsConstructor
class DefaultSalaryService implements SalaryService {

	/** The currencies offered whatever the salaries are in. */
	private static final List<String> COMMON_CURRENCIES = List.of("EUR", "USD", "GBP");

	/** The currency shown unless another is asked for. */
	static final String DEFAULT_CURRENCY = "EUR";

	/** A year in euros under which a salary that doesn't say its period is per hour, per day, per month: */
	private static final BigDecimal HOURLY_BELOW = BigDecimal.valueOf(150);

	private static final BigDecimal DAILY_BELOW = BigDecimal.valueOf(1_000);

	private static final BigDecimal MONTHLY_BELOW = BigDecimal.valueOf(15_000);

	private final ApplicationService applications;

	private final ExchangeRates exchangeRates;

	private final MessageSource messages;

	@Override
	public SalaryOverview overview(long userId, @Nullable String currency, SalaryPeriod period) {
		RateTable rates = this.exchangeRates.current();
		SalaryParser parser = new SalaryParser(rates.currencies());

		List<ReadSalary> read = new ArrayList<>();
		List<ApplicationSummary> unreadable = new ArrayList<>();
		int withoutSalary = 0;
		for (ApplicationSummary application : this.applications.findAll(userId, null)) {
			if (application.salary() == null || application.salary().isBlank()) {
				withoutSalary++;
				continue;
			}
			Optional<SalaryRange> salary = parser.parse(application.salary());
			if (salary.isPresent()) {
				read.add(new ReadSalary(application, salary.get()));
			} else {
				unreadable.add(application);
			}
		}

		List<SalaryRange> parsed = read.stream().map(ReadSalary::salary).toList();
		String shownIn = (currency != null && rates.currencies().contains(currency)) ? currency : DEFAULT_CURRENCY;
		List<SalaryEntry> salaries = read.stream()
			.map(salary -> entry(salary.application(), salary.salary(), rates, shownIn, period))
			.sorted(Comparator.comparing(SalaryEntry::midpoint).reversed())
			.toList();
		List<BigDecimal> midpoints = salaries.stream().map(SalaryEntry::midpoint).sorted().toList();

		return SalaryOverview.builder()
			.currency(shownIn)
			.period(period)
			.salaries(salaries)
			.unreadable(unreadable)
			.withoutSalary(withoutSalary)
			.median(percentile(midpoints, 0.5))
			.lowerQuartile(percentile(midpoints, 0.25))
			.upperQuartile(percentile(midpoints, 0.75))
			.byStatus(byStatus(salaries))
			.currencies(currencies(parsed, rates))
			.ratesDate(rates.date())
			.build();
	}

	@Override
	public Optional<SalaryRange> read(@Nullable String salary) {
		return new SalaryParser(this.exchangeRates.current().currencies()).parse(salary);
	}

	@Override
	public List<String> currencies() {
		return this.exchangeRates.current().currencies().stream().sorted().toList();
	}

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 */
	@Override
	public void change(long userId, long applicationId, @Nullable SalaryRange range, Locale locale) {
		Optional<SalaryRange> current = read(this.applications.findById(userId, applicationId)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId))
			.salary());
		boolean same = (range == null) ? current.isEmpty() : range.sameAs(current.orElse(null));
		if (!same) {
			this.applications.updateSalary(userId, applicationId, (range != null) ? text(range, locale) : null);
		}
	}

	/**
	 * The range as postings write it, with the language's digits and words: "60,000–80,000 EUR gross per year",
	 * "6.000 RON net pe lună". {@link SalaryParser} reads the words for the period and the tax in every language
	 * there are messages in (i18n/jobs-salaries).
	 */
	String text(SalaryRange range, Locale locale) {
		NumberFormat amounts = NumberFormat.getNumberInstance(locale);
		amounts.setMaximumFractionDigits(2);
		StringJoiner text = new StringJoiner(" ");
		text.add(range.isSingleAmount() ? amounts.format(range.low())
				: amounts.format(range.low()) + "–" + amounts.format(range.high()));
		text.add(range.currency());
		if (range.basis() != null) {
			text.add(this.messages.getMessage("salaryText.basis." + range.basis(), null, locale));
		}
		if (range.period() != null) {
			text.add(this.messages.getMessage("salaryText.period." + range.period(), null, locale));
		}
		return text.toString();
	}

	/**
	 * The salary in the currency and per the period shown. A salary that doesn't say its period gets the one its
	 * amount suggests: 15,000 RON is a month, not a year.
	 */
	static SalaryEntry entry(ApplicationSummary application, SalaryRange salary, RateTable rates,
			String currency, SalaryPeriod shownPer) {
		SalaryPeriod period = (salary.period() != null) ? salary.period() : guessedPeriod(salary, rates);
		Function<BigDecimal, BigDecimal> converted = amount -> rates.convert(amount, salary.currency(), currency)
			.multiply(BigDecimal.valueOf(period.perYear()))
			.divide(BigDecimal.valueOf(shownPer.perYear()), MathContext.DECIMAL64)
			.setScale(2, RoundingMode.HALF_UP);
		return new SalaryEntry(application, converted.apply(salary.low()), converted.apply(salary.high()),
				salary.currency(), salary.period() == null, salary.basis());
	}

	static SalaryPeriod guessedPeriod(SalaryRange salary, RateTable rates) {
		BigDecimal inEuros = rates.convert(salary.low().add(salary.high()).divide(BigDecimal.TWO), salary.currency(), "EUR");
		if (inEuros.compareTo(HOURLY_BELOW) < 0) {
			return SalaryPeriod.HOUR;
		}
		if (inEuros.compareTo(DAILY_BELOW) < 0) {
			return SalaryPeriod.DAY;
		}
		return (inEuros.compareTo(MONTHLY_BELOW) < 0) ? SalaryPeriod.MONTH : SalaryPeriod.YEAR;
	}

	static List<String> currencies(Iterable<SalaryRange> salaries, RateTable rates) {
		TreeSet<String> currencies = new TreeSet<>(COMMON_CURRENCIES);
		salaries.forEach(salary -> currencies.add(salary.currency()));
		currencies.retainAll(rates.currencies());
		return List.copyOf(currencies);
	}

	private static List<StatusSalary> byStatus(List<SalaryEntry> salaries) {
		Map<ApplicationStatus, List<BigDecimal>> midpoints = salaries.stream()
			.collect(Collectors.groupingBy(entry -> entry.application().status(), () -> new EnumMap<>(ApplicationStatus.class),
					Collectors.mapping(SalaryEntry::midpoint, Collectors.toList())));
		return midpoints.entrySet()
			.stream()
			.map(status -> new StatusSalary(status.getKey(),
					Objects.requireNonNull(percentile(status.getValue().stream().sorted().toList(), 0.5),
							"a status is grouped by its salaries, so it has one"),
					status.getValue().size()))
			.toList();
	}

	/**
	 * Between the two nearest values when it falls between them, as spreadsheets do.
	 *
	 * @param sorted in ascending order
	 * @return {@code null} without values
	 */
	private static @Nullable BigDecimal percentile(List<BigDecimal> sorted, double fraction) {
		if (sorted.isEmpty()) {
			return null;
		}
		double position = fraction * (sorted.size() - 1);
		int below = (int) Math.floor(position);
		int above = (int) Math.ceil(position);
		BigDecimal share = BigDecimal.valueOf(position - below);
		BigDecimal low = sorted.get(below);
		return low.add(sorted.get(above).subtract(low).multiply(share)).setScale(2, RoundingMode.HALF_UP);
	}

	private record ReadSalary(ApplicationSummary application, SalaryRange salary) {
	}

}
