package com.jobwarden.web.controller;

import java.util.List;
import java.util.Map;

import com.jobwarden.search.DescriptionParser;
import com.jobwarden.search.EmploymentType;
import com.jobwarden.search.GeneratedQueries;
import com.jobwarden.search.Platform;
import com.jobwarden.search.QueryGenerator;
import com.jobwarden.search.Recency;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.search.SearchSuggestions;
import com.jobwarden.search.WorkType;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.SearchFilters;
import com.jobwarden.web.dto.TagSuggestion;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The Boolean search generator. It needs no profile: nothing is saved, the address holds the filters (see
 * {@link SearchFilters}), so a search can be bookmarked.
 */
@Controller
@RequestMapping(ControllerConstants.SEARCH)
@RequiredArgsConstructor
class SearchController extends BaseController {

	private static final Map<String, String> PLATFORM_ICONS = Map.of(Platform.GOOGLE.name(), "ph-google-logo",
			Platform.LINKEDIN.name(), "ph-linkedin-logo", Platform.REDDIT.name(), "ph-reddit-logo", Platform.X.name(),
			"ph-x-logo", Platform.REMOTE_BOARDS.name(), "ph-globe-hemisphere-west");

	private final QueryGenerator queries;

	private final DescriptionParser descriptions;

	/**
	 * The page, with the queries for the filters in its address. As the filters change, HTMX asks again for the
	 * queries only. With {@code describe}, the filters come from that description instead, keeping the sites, and
	 * HTMX swaps the whole form.
	 */
	@GetMapping
	String search(@RequestParam MultiValueMap<String, String> parameters, @RequestParam(required = false) @Nullable String describe,
			@RequestHeader(name = HtmxHeaders.REQUEST, required = false) @Nullable String htmx, Model model) {
		SearchFilters filters = SearchFilters.from(parameters);
		SearchCriteria criteria = filters.criteria();
		boolean described = describe != null && !describe.isBlank();
		if (described) {
			criteria = this.descriptions.parse(describe).toBuilder().sites(criteria.sites()).build();
		}
		GeneratedQueries generated = this.queries.generate(criteria);

		model.addAttribute(ModelAttributes.SEARCH_CRITERIA, criteria);
		model.addAttribute(ModelAttributes.QUERIES, generated);
		model.addAttribute(ModelAttributes.SELECTED_PLATFORM, filters.platform());
		model.addAttribute(ModelAttributes.PLATFORMS, Platform.values());
		model.addAttribute(ModelAttributes.PLATFORM_ICONS, PLATFORM_ICONS);
		model.addAttribute(ModelAttributes.GOOGLE_WORD_LIMIT, GeneratedQueries.GOOGLE_WORD_LIMIT);
		if (htmx != null && !described) {
			return Views.SEARCH_OUTPUT_FRAGMENT;
		}

		model.addAttribute(ModelAttributes.DESCRIPTION, described ? describe : null);
		model.addAttribute(ModelAttributes.EXAMPLES, SearchSuggestions.EXAMPLES);
		model.addAttribute(ModelAttributes.WORK_TYPES, WorkType.values());
		model.addAttribute(ModelAttributes.EMPLOYMENT_TYPES, EmploymentType.values());
		model.addAttribute(ModelAttributes.RECENCIES, Recency.values());
		model.addAttribute(ModelAttributes.TITLE_SUGGESTIONS, suggestions(SearchSuggestions.TITLES));
		model.addAttribute(ModelAttributes.SKILL_SUGGESTIONS, suggestions(SearchSuggestions.SKILLS));
		model.addAttribute(ModelAttributes.LOCATION_SUGGESTIONS, SearchSuggestions.REGIONS.stream()
			.map(region -> new TagSuggestion(region.name(), region.name(), null, region.countryCode()))
			.toList());
		model.addAttribute(ModelAttributes.SITE_SUGGESTIONS, SearchSuggestions.JOB_BOARDS.stream()
			.map(board -> new TagSuggestion(board.domain(), board.name(), board.domain(), null))
			.toList());
		model.addAttribute(ModelAttributes.JOB_BOARDS, SearchSuggestions.JOB_BOARDS);
		return Views.SEARCH;
	}

	private static List<TagSuggestion> suggestions(List<String> values) {
		return values.stream().map(TagSuggestion::of).toList();
	}

}
