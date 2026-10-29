package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.SalaryEntry;
import com.jobwarden.jobs.dto.SalaryOverview;
import com.jobwarden.jobs.dto.StatusSalary;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.SalaryService;
import com.jobwarden.jobs.service.impl.SalaryParser.ParsedSalary;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

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
	private static final String DEFAULT_CURRENCY = "EUR";

	/** A year in euros under which a salary that doesn't say its period is per hour, per day, per month: */
	private static final BigDecimal HOURLY_BELOW = BigDecimal.valueOf(150);

	private static final BigDecimal DAILY_BELOW = BigDecimal.valueOf(1_000);

	private static final BigDecimal MONTHLY_BELOW = BigDecimal.valueOf(15_000);

	private final ApplicationService applications;

	private final ExchangeRates exchangeRates;

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
			Optional<ParsedSalary> salary = parser.parse(application.salary());
			if (salary.isPresent()) {
				read.add(new ReadSalary(application, salary.get()));
			} else {
				unreadable.add(application);
			}
		}

		List<ParsedSalary> parsed = read.stream().map(ReadSalary::salary).toList();
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

	/**
	 * The salary in the currency and per the period shown. A salary that doesn't say its period gets the one its
	 * amount suggests: 15,000 RON is a month, not a year.
	 */
	private static SalaryEntry entry(ApplicationSummary application, ParsedSalary salary, RateTable rates,
			String currency, SalaryPeriod shownPer) {
		SalaryPeriod period = (salary.period() != null) ? salary.period() : guessedPeriod(salary, rates);
		Function<BigDecimal, BigDecimal> converted = amount -> rates.convert(amount, salary.currency(), currency)
			.multiply(BigDecimal.valueOf(period.perYear()))
			.divide(BigDecimal.valueOf(shownPer.perYear()), MathContext.DECIMAL64)
			.setScale(2, RoundingMode.HALF_UP);
		return new SalaryEntry(application, converted.apply(salary.low()), converted.apply(salary.high()),
				salary.currency(), salary.period() == null, salary.basis());
	}

	private static SalaryPeriod guessedPeriod(ParsedSalary salary, RateTable rates) {
		BigDecimal inEuros = rates.convert(salary.low().add(salary.high()).divide(BigDecimal.TWO), salary.currency(), "EUR");
		if (inEuros.compareTo(HOURLY_BELOW) < 0) {
			return SalaryPeriod.HOUR;
		}
		if (inEuros.compareTo(DAILY_BELOW) < 0) {
			return SalaryPeriod.DAY;
		}
		return (inEuros.compareTo(MONTHLY_BELOW) < 0) ? SalaryPeriod.MONTH : SalaryPeriod.YEAR;
	}

	private static List<String> currencies(Iterable<ParsedSalary> salaries, RateTable rates) {
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
					percentile(status.getValue().stream().sorted().toList(), 0.5), status.getValue().size()))
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

	private record ReadSalary(ApplicationSummary application, ParsedSalary salary) {
	}

}
