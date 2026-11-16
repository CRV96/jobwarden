package com.jobwarden.search;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Text as words, to tell whether a posting says a term without minding case, accents or punctuation: "Zürich" is
 * "zurich", "Kraków" is "krakow", "Full-Stack Developer (m/w/d)" is "full stack developer m w d".
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Words {

	private static final Pattern MARKS = Pattern.compile("\\p{M}+");

	private static final Pattern NOT_A_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

	/**
	 * @return the words, in lower case and without accents, each between single spaces, the first and last too: so
	 * {@link #has} finds whole words only. Empty text is a single space.
	 */
	public static String of(@Nullable String text) {
		if (text == null || text.isBlank()) {
			return " ";
		}
		String lower = text.toLowerCase(Locale.ROOT)
			// The letters that are no letter plus an accent, so normalizing leaves them as they are
			.replace("ß", "ss")
			.replace("ł", "l")
			.replace("ø", "o")
			.replace("æ", "ae")
			.replace("œ", "oe")
			.replace("đ", "d")
			.replace("ı", "i");
		String plain = MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
		return " " + NOT_A_WORD.matcher(plain).replaceAll(" ").strip() + " ";
	}

	/**
	 * @param words text as {@link #of} gives it
	 * @param phrase a word or a few, as typed: "Java", "remote EU"
	 * @return whether the text has the phrase as whole words, in that order; never for a phrase with no words
	 */
	public static boolean has(String words, String phrase) {
		String wanted = of(phrase);
		return !wanted.isBlank() && words.contains(wanted);
	}

}
