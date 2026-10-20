package com.jobwarden.web.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The languages the pages are translated into. Each has message files named after its tag, e.g. web_ro.properties.
 */
@Getter
@RequiredArgsConstructor
public enum Language {

	ENGLISH("en", "English", "gb"),
	ROMANIAN("ro", "Română", "ro"),
	FRENCH("fr", "Français", "fr"),
	GERMAN("de", "Deutsch", "de"),
	POLISH("pl", "Polski", "pl"),
	SPANISH("es", "Español", "es"),
	PORTUGUESE("pt", "Português", "pt");

	/** Also for any language the app doesn't have. */
	public static final Language DEFAULT = ENGLISH;

	private final String tag;

	/** The language's name in itself, as the language picker shows it. */
	private final String nativeName;

	/** The country whose flag the language picker shows next to it, as its flag-icons class: {@code fi-<flag>}. */
	private final String flag;

	public Locale getLocale() {
		return Locale.forLanguageTag(this.tag);
	}

	/**
	 * @param tag a language tag such as {@code ro} or {@code pt-BR}; only its language part counts
	 */
	public static Optional<Language> fromTag(@Nullable String tag) {
		if (tag == null) {
			return Optional.empty();
		}
		String language = Locale.forLanguageTag(tag).getLanguage();
		return Arrays.stream(values()).filter(candidate -> candidate.tag.equals(language)).findFirst();
	}

	/**
	 * The browser's most preferred language that the app has, or {@link #DEFAULT}.
	 *
	 * @param acceptLanguage the request's {@code Accept-Language} header, e.g. {@code ro-RO,ro;q=0.9,en;q=0.8}
	 */
	public static Language bestMatch(@Nullable String acceptLanguage) {
		if (acceptLanguage == null || acceptLanguage.isBlank()) {
			return DEFAULT;
		}
		try {
			// Sorted by the browser's preference
			for (Locale.LanguageRange range : Locale.LanguageRange.parse(acceptLanguage)) {
				Optional<Language> language = fromTag(range.getRange());
				if (language.isPresent()) {
					return language.get();
				}
			}
		} catch (IllegalArgumentException malformedHeader) {
			// A header the JDK can't parse: same as none at all
		}
		return DEFAULT;
	}

}
