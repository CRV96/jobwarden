package com.jobwarden.web.view;

import java.util.stream.Collectors;

import com.jobwarden.web.view.QuerySyntax.Kind;
import com.jobwarden.web.view.QuerySyntax.Token;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class QuerySyntaxTests {

	private final QuerySyntax syntax = new QuerySyntax();

	@Test
	void tellsEachPartOfAQueryByWhatItDoes() {
		assertThat(this.syntax.tokens("(site:jobs.lever.co OR site:boards.greenhouse.io) \"Java Developer\" Spring -junior -\"no remote\""))
			.filteredOn(token -> token.kind() != Kind.SPACE)
			.extracting(Token::text, Token::kind)
			.containsExactly(tuple("(", Kind.PAREN), tuple("site:jobs.lever.co", Kind.FIELD), tuple("OR", Kind.OPERATOR),
					tuple("site:boards.greenhouse.io", Kind.FIELD), tuple(")", Kind.PAREN),
					tuple("\"Java Developer\"", Kind.PHRASE), tuple("Spring", Kind.WORD), tuple("-junior", Kind.EXCLUDE),
					tuple("-\"no remote\"", Kind.EXCLUDE));
	}

	@Test
	void keepsTheWholeQuery() {
		String query = "\"we're hiring\" (java OR kotlin) min_faves:5 -filter:replies \"unclosed";

		assertThat(this.syntax.tokens(query).stream().map(Token::text).collect(Collectors.joining())).isEqualTo(query);
	}

}
