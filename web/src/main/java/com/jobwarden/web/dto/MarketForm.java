package com.jobwarden.web.dto;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.market.dto.MarketQuery;
import org.jspecify.annotations.Nullable;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * The market salaries section of the salaries page, as its address has it. A role or skill left out of the address is
 * the default; an empty one is all of them.
 *
 * @param country ISO 3166 two-letter codes, in the order to show them; the defaults when there's none
 */
public record MarketForm(@Nullable String role, @Nullable String skill, @Nullable List<String> country) {

	public static final String DEFAULT_ROLE = "Developer, back-end";

	public static final String DEFAULT_SKILL = "Java";

	/** Romania and the countries a remote job there is often with, and the two biggest markets to compare. */
	public static final List<String> DEFAULT_COUNTRIES = List.of("RO", "PL", "DE", "NL", "ES", "GB", "US");

	/** The most countries the section shows: the market module asks for no more. */
	public static final int MAX_COUNTRIES = 12;

	private static final Set<String> COUNTRY_CODES = Set.of(Locale.getISOCountries());

	/**
	 * @return the role picked, empty for all
	 */
	public String selectedRole() {
		return (this.role != null) ? this.role.strip() : DEFAULT_ROLE;
	}

	/**
	 * @return the skill picked, empty for any
	 */
	public String selectedSkill() {
		return (this.skill != null) ? this.skill.strip() : DEFAULT_SKILL;
	}

	/**
	 * @return the countries picked that are countries, each once, at most {@link #MAX_COUNTRIES}; the defaults when
	 * none is
	 */
	public List<String> countries() {
		List<String> picked = (this.country == null) ? List.of() : this.country.stream()
			.map(code -> code.strip().toUpperCase(Locale.ROOT))
			.filter(COUNTRY_CODES::contains)
			.distinct()
			.limit(MAX_COUNTRIES)
			.toList();
		return picked.isEmpty() ? DEFAULT_COUNTRIES : picked;
	}

	public MarketQuery toQuery() {
		return new MarketQuery(emptyToNull(selectedRole()), emptyToNull(selectedSkill()), countries());
	}

	/**
	 * The address of the section with these choices, in the currency and per the period of the page.
	 *
	 * @param withoutCountry a country to leave out, or {@code null}
	 */
	public String url(String path, String currency, SalaryPeriod period, @Nullable String withoutCountry) {
		UriComponentsBuilder address = UriComponentsBuilder.fromPath(path)
			.queryParam("currency", currency)
			.queryParam("period", period)
			.queryParam("role", selectedRole())
			.queryParam("skill", selectedSkill());
		countries().stream().filter(code -> !code.equals(withoutCountry)).forEach(code -> address.queryParam("country", code));
		return address.encode().build().toUriString();
	}

	private static @Nullable String emptyToNull(String text) {
		return text.isEmpty() ? null : text;
	}

}
