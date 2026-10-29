package com.jobwarden.web.view;

import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Countries by their ISO 3166 code, named in the user's language: {@code ${@countries.name('RO')}}.
 */
@Component("countries")
public class CountryNames {

	public String name(String code) {
		return Locale.of("", code).getDisplayCountry(LocaleContextHolder.getLocale());
	}

	/**
	 * @return every country, in the alphabetical order of the user's language
	 */
	public List<Country> all() {
		Locale locale = LocaleContextHolder.getLocale();
		Collator collator = Collator.getInstance(locale);
		return Arrays.stream(Locale.getISOCountries())
			.map(code -> new Country(code, Locale.of("", code).getDisplayCountry(locale)))
			.sorted(Comparator.comparing(Country::name, collator))
			.toList();
	}

	public record Country(String code, String name) {
	}

}
