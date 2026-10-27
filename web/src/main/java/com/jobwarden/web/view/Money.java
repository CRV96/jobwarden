package com.jobwarden.web.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Amounts of money as the templates show them, in the user's language: {@code ${@money.compact(amount, 'EUR')}}.
 */
@Component("money")
public class Money {

	private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000);

	private static final BigDecimal TEN_THOUSAND = BigDecimal.valueOf(10_000);

	private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

	/**
	 * Short, for charts and figures: €85K, 4.5K RON, €37.5 (an hourly rate keeps its decimal).
	 */
	public String compact(BigDecimal amount, String currency) {
		Locale locale = LocaleContextHolder.getLocale();
		// The compact format rounds what it doesn't shorten, and drops a BigDecimal's fraction digits
		NumberFormat number = (amount.abs().compareTo(THOUSAND) < 0) ? NumberFormat.getNumberInstance(locale)
				: NumberFormat.getCompactNumberInstance(locale, NumberFormat.Style.SHORT);
		boolean round = amount.abs().compareTo(TEN_THOUSAND) >= 0 && amount.abs().compareTo(MILLION) < 0;
		number.setMaximumFractionDigits(round ? 0 : 1);
		number.setRoundingMode(RoundingMode.HALF_UP);
		return withCurrency(number.format(amount.doubleValue()), currency, locale);
	}

	/**
	 * A range, or one amount when both ends are the same: €40K – €80K, with thin spaces around the dash.
	 */
	public String range(BigDecimal low, BigDecimal high, String currency) {
		return (low.compareTo(high) == 0) ? compact(low, currency)
				: compact(low, currency) + "\u2009–\u2009" + compact(high, currency);
	}

	/**
	 * The currency's symbol where the language puts it: before the number in English (€85K), after it in German
	 * (85.000 €). A symbol of letters keeps a space from the number (RON 15K).
	 */
	private static String withCurrency(String number, String code, Locale locale) {
		Currency currency = Currency.getInstance(code);
		String symbol = currency.getSymbol(locale);
		NumberFormat sample = NumberFormat.getCurrencyInstance(locale);
		sample.setCurrency(currency);
		String one = sample.format(1);
		int symbolAt = one.indexOf(symbol);
		boolean before = symbolAt >= 0 && symbolAt < one.indexOf('1');
		if (before) {
			return Character.isLetter(symbol.codePointBefore(symbol.length())) ? symbol + " " + number : symbol + number;
		}
		return number + " " + symbol;
	}

}
