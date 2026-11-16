package com.jobwarden.jobs.service.impl;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Whether a posting rules out sponsoring a visa, or asks for the right to work already, in the app's languages: the
 * sentence that says so.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SponsorshipCheck {

	private static final Pattern RULES_OUT = Pattern.compile(String.join("|",
			"no (?:visa )?sponsorship", "(?:unable|not able) to (?:offer|provide|support) (?:visa )?sponsorship",
			"(?:do|does|will|can|are) not (?:able to )?(?:offer |provide )?(?:visa )?sponsor", "cannot sponsor",
			"without (?:the need for |requiring )?(?:visa )?sponsorship", "sponsorship (?:is )?not (?:available|offered|provided)",
			"must (?:already )?(?:be (?:legally )?authori[sz]ed|have (?:the )?(?:legal )?right) to work",
			"(?:existing|valid|current) (?:right|authori[sz]ation|permit) to work",
			"keine (?:visa-?)?(?:sponsoring|sponsorship|unterstutzung beim visum)",
			"(?:gultige )?arbeitserlaubnis (?:ist )?(?:erforderlich|voraussetzung|notwendig)",
			"pas de (?:sponsoring|parrainage)", "sans (?:sponsoring|parrainage)", "autorisation de travail (?:valide|requise|obligatoire)",
			"no (?:ofrecemos|se ofrece|podemos ofrecer) patrocinio", "sin patrocinio",
			"permiso de trabajo (?:valido|vigente|requerido|obligatorio)",
			"nao (?:oferecemos|patrocinamos|e possivel patrocinar)", "sem patrocinio",
			"autorizacao de trabalho (?:valida|necessaria|obrigatoria)", "nu (?:oferim|putem oferi) sponsorizare",
			"fara sponsorizare", "drept(?:ul)? de munca (?:valabil|valid)",
			"nie (?:zapewniamy|oferujemy) (?:sponsorowania|sponsoringu)", "pozwolenie na prace (?:jest )?wymagane",
			"prawo do pracy w"));

	private static final int EVIDENCE_MAX_LENGTH = 240;

	/**
	 * @return the sentence that rules it out; empty when the posting doesn't, or doesn't say
	 */
	static Optional<String> rulesOut(String description) {
		String text = Normalizer.normalize(description, Normalizer.Form.NFC);
		Matcher found = RULES_OUT.matcher(fold(text));
		return found.find() ? Optional.of(sentence(text, found.start())) : Optional.empty();
	}

	/** Lower case, without accents, the same length as the text: the place found in one is the place in the other. */
	private static String fold(String text) {
		StringBuilder folded = new StringBuilder(text.length());
		for (char letter : text.toCharArray()) {
			String plain = Normalizer.normalize(String.valueOf(letter), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
			folded.append(plain.isEmpty() ? letter : Character.toLowerCase(plain.charAt(0)));
		}
		return folded.toString().replace('ł', 'l').replace('ș', 's').replace('ț', 't').toLowerCase(Locale.ROOT);
	}

	private static String sentence(String text, int at) {
		int start = at;
		while (start > 0 && at - start < EVIDENCE_MAX_LENGTH / 2 && ".!?\n".indexOf(text.charAt(start - 1)) < 0) {
			start--;
		}
		int end = at;
		while (end < text.length() && end - start < EVIDENCE_MAX_LENGTH && ".!?\n".indexOf(text.charAt(end)) < 0) {
			end++;
		}
		if (end < text.length() && text.charAt(end) != '\n') {
			end++;
		}
		return text.substring(start, end).replaceAll("\\s+", " ").strip();
	}

}
