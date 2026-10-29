package com.jobwarden.web.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * A Boolean query in parts, so the search page colours each by what it does: {@code ${@querySyntax.tokens(query)}}.
 */
@Component("querySyntax")
public class QuerySyntax {

	/** In order: a phrase, an excluded term or phrase, a field (site:, min_faves:...), an operator, a parenthesis. */
	private static final Pattern TOKEN = Pattern.compile(
			"(?<phrase>\"[^\"]*\")|(?<exclude>-(?:\"[^\"]*\"|[^\\s()]+))|(?<field>\\b[a-z_]+:[^\\s()]+)"
					+ "|(?<operator>\\b(?:OR|AND|NOT)\\b)|(?<paren>[()])|(?<space>\\s+)|(?<word>[^\\s()\"]+)");

	public enum Kind {

		PHRASE, EXCLUDE, FIELD, OPERATOR, PAREN, SPACE, WORD

	}

	public record Token(String text, Kind kind) {
	}

	/**
	 * @return the query's parts, in order: together, the whole query
	 */
	public List<Token> tokens(String query) {
		List<Token> tokens = new ArrayList<>();
		Matcher matcher = TOKEN.matcher(query);
		int end = 0;
		while (matcher.find()) {
			if (matcher.start() > end) {
				tokens.add(new Token(query.substring(end, matcher.start()), Kind.WORD));
			}
			tokens.add(new Token(matcher.group(), kind(matcher)));
			end = matcher.end();
		}
		if (end < query.length()) {
			tokens.add(new Token(query.substring(end), Kind.WORD));
		}
		return tokens;
	}

	private static Kind kind(Matcher matcher) {
		for (Kind kind : Kind.values()) {
			if (matcher.group(kind.name().toLowerCase(Locale.ROOT)) != null) {
				return kind;
			}
		}
		return Kind.WORD;
	}

}
