package com.jobwarden.market.service.impl;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Countries as the sources name them, to ISO 3166 two-letter codes.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Countries {

	/** The survey's names that aren't Java's English names for the countries. */
	private static final Map<String, String> SURVEY_NAMES = Map.ofEntries(
			Map.entry("United States of America", "US"),
			Map.entry("United Kingdom of Great Britain and Northern Ireland", "GB"),
			Map.entry("Czech Republic", "CZ"),
			Map.entry("Russian Federation", "RU"),
			Map.entry("Republic of Moldova", "MD"),
			Map.entry("Iran, Islamic Republic of...", "IR"),
			Map.entry("Turkey", "TR"),
			Map.entry("Republic of North Macedonia", "MK"),
			Map.entry("Viet Nam", "VN"),
			Map.entry("Hong Kong (S.A.R.)", "HK"),
			Map.entry("Venezuela, Bolivarian Republic of...", "VE"),
			Map.entry("Republic of Korea", "KR"),
			Map.entry("South Korea", "KR"),
			Map.entry("North Korea", "KP"),
			Map.entry("Côte d'Ivoire", "CI"),
			Map.entry("Palestine", "PS"),
			Map.entry("Syrian Arab Republic", "SY"),
			Map.entry("Kosovo", "XK"),
			Map.entry("Lao People's Democratic Republic", "LA"),
			Map.entry("United Republic of Tanzania", "TZ"),
			Map.entry("Democratic Republic of the Congo", "CD"),
			Map.entry("Congo, Republic of the...", "CG"),
			Map.entry("Libyan Arab Jamahiriya", "LY"),
			Map.entry("The former Yugoslav Republic of Macedonia", "MK"),
			Map.entry("Micronesia, Federated States of...", "FM"));

	private static final Map<String, String> ENGLISH_NAMES = new HashMap<>();

	static {
		for (String code : Locale.getISOCountries()) {
			ENGLISH_NAMES.put(Locale.of("", code).getDisplayCountry(Locale.ENGLISH), code);
		}
	}

	/**
	 * @return the country's code, or {@code null} for a name that isn't a known country
	 */
	static @Nullable String ofSurveyName(String name) {
		String code = SURVEY_NAMES.get(name);
		return (code != null) ? code : ENGLISH_NAMES.get(name);
	}

	/**
	 * Eurostat writes Greece {@code EL}, the European Union's own code, where ISO has {@code GR}.
	 *
	 * @return the country's code, or {@code null} for a group of countries such as {@code EU27_2020}
	 */
	static @Nullable String ofEurostatCode(String code) {
		if ("EL".equals(code)) {
			return "GR";
		}
		return (code.length() == 2) ? code : null;
	}

}
