package com.jobwarden.web.dto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.jobwarden.search.EmploymentType;
import com.jobwarden.search.Platform;
import com.jobwarden.search.Recency;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.search.SearchSuggestions;
import com.jobwarden.search.WorkType;
import org.jspecify.annotations.Nullable;

import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriUtils;

/**
 * The search page's filters, as its address has them: a parameter for each chip ({@code titles=...&titles=...}), the
 * excluded terms as one text separated by commas, and the platform whose query the page shows.
 * <p>
 * Read from the parameters rather than bound to a form: Spring splits a lone value at its commas, and a location such
 * as "Cluj-Napoca, Romania" must stay one. Values that aren't one of an enum's constants are ignored.
 */
public record SearchFilters(SearchCriteria criteria, Platform platform) {

	public static SearchFilters from(MultiValueMap<String, String> parameters) {
		// The page always sends the platform. Without one, it's opened fresh: with the default sites
		boolean fresh = !parameters.containsKey("platform") && !parameters.containsKey("sites");
		SearchCriteria criteria = SearchCriteria.builder()
			.titles(values(parameters, "titles"))
			.locations(values(parameters, "locations"))
			.skills(values(parameters, "skills"))
			.sites(fresh ? SearchSuggestions.DEFAULT_SITES : values(parameters, "sites"))
			.workTypes(constants(parameters, "workTypes", WorkType.class))
			.employmentTypes(constants(parameters, "employmentTypes", EmploymentType.class))
			.excludes(Arrays.asList(Objects.requireNonNullElse(parameters.getFirst("excludes"), "").split(",")))
			.recency(constant(parameters.getFirst("recency"), Recency.class).orElse(Recency.ANY))
			.build();
		return new SearchFilters(criteria,
				constant(parameters.getFirst("platform"), Platform.class).orElse(Platform.GOOGLE));
	}

	/**
	 * The search page's address for the filters, as its form makes it: a saved search opens there, to run or change.
	 */
	public static String url(SearchCriteria criteria) {
		StringJoiner query = new StringJoiner("&", "/search?", "");
		criteria.titles().forEach(title -> query.add(parameter("titles", title)));
		criteria.locations().forEach(location -> query.add(parameter("locations", location)));
		criteria.skills().forEach(skill -> query.add(parameter("skills", skill)));
		criteria.sites().forEach(site -> query.add(parameter("sites", site)));
		criteria.workTypes().forEach(type -> query.add(parameter("workTypes", type.name())));
		criteria.employmentTypes().forEach(type -> query.add(parameter("employmentTypes", type.name())));
		query.add(parameter("excludes", String.join(", ", criteria.excludes())));
		query.add(parameter("recency", criteria.recency().name()));
		return query.add(parameter("platform", Platform.GOOGLE.name())).toString();
	}

	/** Encoded whole, a plus too: in a query, + is a space. */
	private static String parameter(String name, String value) {
		return name + "=" + UriUtils.encode(value, StandardCharsets.UTF_8);
	}

	private static List<String> values(MultiValueMap<String, String> parameters, String name) {
		return Objects.requireNonNullElse(parameters.get(name), List.of());
	}

	private static <E extends Enum<E>> Set<E> constants(MultiValueMap<String, String> parameters, String name,
			Class<E> type) {
		return values(parameters, name).stream()
			.map(value -> constant(value, type))
			.flatMap(Optional::stream)
			.collect(Collectors.toSet());
	}

	private static <E extends Enum<E>> Optional<E> constant(@Nullable String value, Class<E> type) {
		return Arrays.stream(type.getEnumConstants()).filter(constant -> constant.name().equals(value)).findFirst();
	}

}
