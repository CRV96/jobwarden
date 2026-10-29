package com.jobwarden.market.dto;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The market salaries to look up.
 *
 * @param role a role as the Stack Overflow survey names it, e.g. {@code Developer, back-end}; {@code null} for all
 * @param skill a programming language, e.g. {@code Java}; {@code null} for any
 * @param countries ISO 3166 two-letter codes, in the order to show them
 */
public record MarketQuery(@Nullable String role, @Nullable String skill, List<String> countries) {

	public MarketQuery {
		countries = List.copyOf(Objects.requireNonNull(countries, "countries"));
	}

}
