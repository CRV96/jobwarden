package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jobwarden.jobs.enums.PayBasis;
import com.jobwarden.jobs.enums.SalaryPeriod;
import org.jspecify.annotations.Nullable;

/**
 * Reads a salary as postings write it, in the languages the extension saves them in: "80,000–160,000 USD per year",
 * "$120K - $150K a year", "15 000–20 000 zł brutto / mies.", "5.000 EUR pe lună". The first two amounts make the
 * range; a currency code or symbol says the currency; words such as "hour", "Monat" or "lună" the period, when there
 * are any.
 */
final class SalaryParser {

	/**
	 * An amount: digits grouped by thousands (80,000 80.000 80 000 80'000) or not, decimals of one or two digits, and
	 * k or M for thousands or millions. Not one followed by a percent sign or an ordinal (13th salary).
	 */
	private static final Pattern AMOUNT = Pattern.compile("(?<![\\d.,])(\\d{1,3}(?:[ \\u00a0\\u202f.,'’]\\d{3})+|\\d+)"
			+ "(?:[.,](\\d{1,2}))?(?!\\d)(?:\\s?([kKmM])(?!\\p{L}))?(?!\\s?%|st\\b|nd\\b|rd\\b|th\\b)");

	private static final Pattern WORD_CODE = Pattern.compile("(?<!\\p{L})(\\p{L}{3})(?!\\p{L})");

	/** Codes that are everyday words too in lower case, like TRY: only in capitals. These are also safe in lower case. */
	private static final Set<String> LOWER_CASE_CODES = Set.of("eur", "usd", "gbp", "chf", "pln", "ron", "huf", "czk");

	/** Symbols, longest first, so US$ isn't read as $. */
	private static final List<Map.Entry<String, String>> SYMBOLS = List.of(Map.entry("us$", "USD"),
			Map.entry("ca$", "CAD"), Map.entry("au$", "AUD"), Map.entry("nz$", "NZD"), Map.entry("hk$", "HKD"),
			Map.entry("mx$", "MXN"), Map.entry("c$", "CAD"), Map.entry("a$", "AUD"), Map.entry("s$", "SGD"),
			Map.entry("r$", "BRL"), Map.entry("$", "USD"), Map.entry("€", "EUR"), Map.entry("£", "GBP"),
			Map.entry("₹", "INR"), Map.entry("¥", "JPY"), Map.entry("₺", "TRY"), Map.entry("₪", "ILS"),
			Map.entry("₩", "KRW"));

	/** Names of currencies, as words of their own. */
	private static final Map<String, String> CURRENCY_WORDS = Map.ofEntries(Map.entry("lei", "RON"),
			Map.entry("zł", "PLN"), Map.entry("zl", "PLN"), Map.entry("kč", "CZK"), Map.entry("ft", "HUF"),
			Map.entry("euro", "EUR"), Map.entry("euros", "EUR"), Map.entry("dollars", "USD"), Map.entry("pounds", "GBP"));

	/** The words for each period, in the order they're looked for: "$50 an hour" is per hour, not per year. */
	private static final Map<SalaryPeriod, Pattern> PERIODS = Map.of(
			SalaryPeriod.HOUR, words("hours?", "hourly", "hrs?", "stunden?", "std", "stündlich", "heures?", "horas?",
					"godz\\.?", "godzin[ęay]?", "godzinowo", "oră", "ora", "/\\s*h"),
			SalaryPeriod.DAY, words("days?", "daily", "tag", "täglich", "tagessatz", "jours?", "journée", "día", "dia",
					"diarios?", "dzień", "dziennie", "zi", "/\\s*d"),
			SalaryPeriod.WEEK, words("weeks?", "weekly", "woche", "wöchentlich", "semaines?", "semana", "semanal",
					"tydzień", "tygodniowo", "săptămână", "saptamana"),
			SalaryPeriod.MONTH, words("months?", "monthly", "monat", "monatlich", "mois", "mensuel", "mes", "mês",
					"mensual", "mensal", "mies\\.?", "miesiąc", "miesięcznie", "miesiecznie", "lună", "luna", "lunar",
					"/\\s*mo", "/\\s*m"),
			SalaryPeriod.YEAR, words("years?", "yearly", "yr", "annum", "annual", "annually", "p\\.\\s?a\\.", "jahr",
					"jährlich", "année", "annuel", "año", "anual", "ano", "rok", "rocznie", "(?:par|pe|per|/)\\s*an",
					"/\\s*y"));

	private static final List<SalaryPeriod> PERIOD_ORDER = List.of(SalaryPeriod.HOUR, SalaryPeriod.DAY,
			SalaryPeriod.WEEK, SalaryPeriod.MONTH, SalaryPeriod.YEAR);

	private static final Pattern NET = words("net", "netto", "neto", "na rękę", "na reke");

	private static final Pattern GROSS = words("gross", "brutto", "brut", "bruto");

	private final Set<String> currencies;

	/**
	 * @param currencies the ISO 4217 codes there are exchange rates for: only those are read as currencies
	 */
	SalaryParser(Set<String> currencies) {
		this.currencies = Set.copyOf(currencies);
	}

	/**
	 * @return the salary, or nothing without an amount or a currency that's known
	 */
	Optional<ParsedSalary> parse(@Nullable String text) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		List<BigDecimal> amounts = amounts(text);
		Optional<String> currency = currency(text);
		if (amounts.isEmpty() || currency.isEmpty()) {
			return Optional.empty();
		}
		BigDecimal first = amounts.getFirst();
		BigDecimal second = (amounts.size() > 1) ? amounts.get(1) : first;
		String lowerCase = text.toLowerCase(Locale.ROOT);
		return Optional.of(new ParsedSalary(first.min(second), first.max(second), currency.get(), period(lowerCase),
				basis(lowerCase)));
	}

	/**
	 * The first two amounts. In "60–80k" the k is for both: the first takes the second's multiplier when, as written,
	 * it's the smaller number. In "6000–8k" it isn't: 6000 is already the amount.
	 */
	private static List<BigDecimal> amounts(String text) {
		List<BigDecimal> amounts = new ArrayList<>();
		List<BigDecimal> multipliers = new ArrayList<>();
		Matcher matcher = AMOUNT.matcher(text);
		while (matcher.find() && amounts.size() < 2) {
			String whole = matcher.group(1).replaceAll("[ \\u00a0\\u202f.,'’]", "");
			BigDecimal amount = new BigDecimal((matcher.group(2) == null) ? whole : whole + "." + matcher.group(2));
			if (amount.signum() > 0) {
				amounts.add(amount);
				multipliers.add(multiplier(matcher.group(3)));
			}
		}
		if (amounts.size() == 2 && multipliers.getFirst().equals(BigDecimal.ONE)
				&& amounts.getFirst().compareTo(amounts.getLast()) <= 0) {
			multipliers.set(0, multipliers.getLast());
		}
		for (int i = 0; i < amounts.size(); i++) {
			amounts.set(i, amounts.get(i).multiply(multipliers.get(i)));
		}
		return amounts;
	}

	private static BigDecimal multiplier(@Nullable String suffix) {
		if (suffix == null) {
			return BigDecimal.ONE;
		}
		return suffix.equalsIgnoreCase("k") ? BigDecimal.valueOf(1_000) : BigDecimal.valueOf(1_000_000);
	}

	private Optional<String> currency(String text) {
		Matcher word = WORD_CODE.matcher(text);
		while (word.find()) {
			String candidate = word.group(1);
			boolean capitals = candidate.equals(candidate.toUpperCase(Locale.ROOT));
			String code = candidate.toUpperCase(Locale.ROOT);
			if (this.currencies.contains(code) && (capitals || LOWER_CASE_CODES.contains(candidate))) {
				return Optional.of(code);
			}
		}
		String lowerCase = text.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, String> symbol : SYMBOLS) {
			if (lowerCase.contains(symbol.getKey()) && this.currencies.contains(symbol.getValue())) {
				return Optional.of(symbol.getValue());
			}
		}
		return CURRENCY_WORDS.entrySet()
			.stream()
			.filter(name -> words(name.getKey()).matcher(lowerCase).find())
			.map(Map.Entry::getValue)
			.filter(this.currencies::contains)
			.findFirst();
	}

	private static @Nullable SalaryPeriod period(String lowerCase) {
		return PERIOD_ORDER.stream().filter(period -> PERIODS.get(period).matcher(lowerCase).find()).findFirst().orElse(null);
	}

	private static @Nullable PayBasis basis(String lowerCase) {
		if (NET.matcher(lowerCase).find()) {
			return PayBasis.NET;
		}
		return GROSS.matcher(lowerCase).find() ? PayBasis.GROSS : null;
	}

	/** Any of the words (regular expressions), as words of their own in lower-case text. */
	private static Pattern words(String... words) {
		return Pattern.compile("(?<!\\p{L})(?:" + String.join("|", words) + ")(?!\\p{L})");
	}

	/**
	 * @param period {@code null} when the text doesn't say
	 * @param basis {@code null} when the text doesn't say
	 */
	record ParsedSalary(BigDecimal low, BigDecimal high, String currency, @Nullable SalaryPeriod period,
			@Nullable PayBasis basis) {
	}

}
