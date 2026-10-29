package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jobwarden.discover.dto.AddedFoundJobs;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.dto.NewFoundJob;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.dto.SearchCount;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.search.EmploymentType;
import com.jobwarden.search.GeneratedQueries;
import com.jobwarden.search.GeneratedQuery;
import com.jobwarden.search.QueryGenerator;
import com.jobwarden.search.Recency;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.search.SearchSuggestions;
import com.jobwarden.search.WorkType;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.date;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.list;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.oneOf;
import static com.jobwarden.mcp.controller.Schemas.string;
import static com.jobwarden.mcp.controller.Schemas.strings;

/**
 * Finding jobs for the AI Assistant page: the searches the profile saved, with queries ready to run, and the jobs found
 * for them, which wait there to be saved, applied to or discarded.
 */
@Component
@RequiredArgsConstructor
class DiscoverTools implements McpToolSet {

	private static final int MAX_JOBS_AT_ONCE = 50;

	private final Profiles profiles;

	private final DiscoveryService discovery;

	private final QueryGenerator queries;

	@Override
	public List<McpTool> tools() {
		return List.of(listSavedSearches(), saveSearch(), addFoundJobs(), listFoundJobs());
	}

	private McpTool listSavedSearches() {
		return new McpTool("list_saved_searches", "List saved searches",
				"The searches the profile keeps for finding jobs, each with its filters, ready-made Boolean queries for "
						+ "Google (limited to the job boards companies use), LinkedIn, Reddit, X and remote job boards, with the "
						+ "address that runs each, and how many found jobs still wait for a decision.",
				object(properties(Profiles.ARGUMENT, Profiles.schema())), true, false, call -> {
					long userId = this.profiles.of(call);
					Map<Long, Long> waiting = this.discovery.countToReviewBySearch(userId)
						.stream()
						.filter(count -> count.searchId() != null)
						.collect(Collectors.toMap(SearchCount::searchId, SearchCount::count));
					return Map.of("searches", this.discovery.findSearches(userId)
						.stream()
						.map(search -> view(search, waiting.getOrDefault(search.id(), 0L)))
						.toList());
				});
	}

	private McpTool saveSearch() {
		return new McpTool("save_search", "Save a search",
				"Keeps filters under a name, for finding jobs now and later; saving under a name the profile has replaces "
						+ "its filters. Returns the search with its queries.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"name", string("What the search is, e.g. Remote Java in Europe"),
						"titles", strings("Job titles; a job matches any of them"),
						"locations", strings("Locations or regions, e.g. Europe, EMEA, Germany"),
						"skills", strings("Skills or keywords; a job matches any of them"),
						"sites", strings("Domains Google searches; the job boards Lever, Greenhouse and Ashby if left out"),
						"work_types", list(oneOf(WorkType.values(), "A work type"), "Remote, hybrid or on-site"),
						"employment_types", list(oneOf(EmploymentType.values(), "An employment type"), "Kinds of contract"),
						"excludes", strings("Words a job must not have, e.g. junior"),
						"recency", oneOf(Recency.values(), "How recent the postings must be; any time if left out")),
						"name"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					List<String> sites = call.arguments().has("sites") ? call.strings("sites") : SearchSuggestions.DEFAULT_SITES;
					SearchCriteria criteria = SearchCriteria.builder()
						.titles(call.strings("titles"))
						.locations(call.strings("locations"))
						.skills(call.strings("skills"))
						.sites(sites)
						.workTypes(Set.copyOf(call.constants("work_types", WorkType.class)))
						.employmentTypes(Set.copyOf(call.constants("employment_types", EmploymentType.class)))
						.excludes(call.strings("excludes"))
						.recency(call.constant("recency", Recency.class))
						.build();
					SavedSearch search = this.discovery.saveSearch(userId, call.requiredString("name"), criteria);
					return view(search, this.discovery.countToReview(userId, search.id()));
				});
	}

	private McpTool addFoundJobs() {
		ObjectNode job = object(properties(
				"title", string("The job's title, as the posting has it"),
				"link", string("The posting's address: the job's own page, not a search"),
				"company", string("The company"),
				"location", string("Where, e.g. Remote, Europe"),
				"salary", string("The salary, as the posting gives it, e.g. 60,000–80,000 EUR per year"),
				"description", string("What the job is, in a few sentences"),
				"note", string("Why it fits the search, in a sentence: the profile reads it when deciding"),
				"posted_on", date("When the posting went up, as YYYY-MM-DD, if it says")),
				"title", "link");
		return new McpTool("add_found_jobs", "Add found jobs",
				"Adds jobs you found to the profile's AI Assistant page, where they wait to be saved, applied to or "
						+ "discarded. Only real postings that are still open, each with its own link. Links the profile already "
						+ "has, as found jobs (also discarded ones) or as applications, are left out and listed as skipped. "
						+ "At most " + MAX_JOBS_AT_ONCE + " at once.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"search_id", integer("The saved search they were found for, from list_saved_searches"),
						"request_id", integer("The request from the AI Assistant page they were found for, from get_request"),
						"search_label", string("Without a saved search: what they were searched for, e.g. Remote Kotlin jobs"),
						"jobs", list(job, "The jobs found")),
						"jobs"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					List<JsonNode> items = call.objects("jobs");
					if (items.size() > MAX_JOBS_AT_ONCE) {
						throw new ToolException("At most " + MAX_JOBS_AT_ONCE + " jobs at once: add the rest in another call");
					}
					Long searchId = call.number("search_id");
					Long requestId = call.number("request_id");
					String label = call.string("search_label");
					List<NewFoundJob> found = items.stream().map(call::item).map(item -> NewFoundJob.builder()
						.title(item.requiredString("title"))
						.link(item.requiredString("link"))
						.company(item.string("company"))
						.location(item.string("location"))
						.salary(item.string("salary"))
						.description(item.string("description"))
						.note(item.string("note"))
						.postedOn(item.date("posted_on"))
						.searchId(searchId)
						.searchLabel(label)
						.requestId(requestId)
						.build()).toList();
					AddedFoundJobs added = this.discovery.addFoundJobs(userId, found);
					return Map.of("added", added.added().stream().map(DiscoverTools::row).toList(), "skipped",
							added.skipped(), "waiting_for_a_decision", this.discovery.countToReview(userId, null));
				});
	}

	private McpTool listFoundJobs() {
		return new McpTool("list_found_jobs", "List found jobs",
				"The jobs found for the profile and what became of them: PENDING (waiting on the AI Assistant page), SAVED or "
						+ "APPLIED (now applications) or DISCARDED. Use it to learn what the profile likes before searching again.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"decision", oneOf(Decision.values(), "Only the jobs with this decision; all of them if left out"),
						"limit", integer("How many at most, up to 200; 50 if left out"))),
				true, false, call -> {
					long userId = this.profiles.of(call);
					return Map.of("jobs", this.discovery
						.findFoundJobs(userId, call.constant("decision", Decision.class), call.limit("limit", 50, 200))
						.stream()
						.map(DiscoverTools::row)
						.toList());
				});
	}

	private SearchView view(SavedSearch search, long waiting) {
		GeneratedQueries generated = this.queries.generate(search.criteria());
		Map<String, QueryView> queries = generated.all()
			.stream()
			.filter(query -> !query.query().isEmpty())
			.collect(Collectors.toMap(query -> query.platform().name(), DiscoverTools::view, (a, b) -> a,
					LinkedHashMap::new));
		return new SearchView(search.id(), search.name(), criteria(search.criteria()), queries, generated.googleTruncated(),
				waiting);
	}

	private static QueryView view(GeneratedQuery query) {
		return new QueryView(query.query(), query.searchUrl());
	}

	private static ObjectNode criteria(SearchCriteria criteria) {
		ObjectNode node = JsonNodeFactory.instance.objectNode();
		criteria.titles().forEach(node.putArray("titles")::add);
		criteria.locations().forEach(node.putArray("locations")::add);
		criteria.skills().forEach(node.putArray("skills")::add);
		criteria.sites().forEach(node.putArray("sites")::add);
		ArrayNode workTypes = node.putArray("work_types");
		criteria.workTypes().forEach(type -> workTypes.add(type.name()));
		ArrayNode employmentTypes = node.putArray("employment_types");
		criteria.employmentTypes().forEach(type -> employmentTypes.add(type.name()));
		criteria.excludes().forEach(node.putArray("excludes")::add);
		return node.put("recency", criteria.recency().name());
	}

	private static FoundRow row(FoundJob job) {
		return new FoundRow(job.id(), job.title(), job.company(), job.location(), job.link(), job.decision(),
				job.searchName(), job.foundAt(), job.decidedAt(), job.applicationId(), job.postedOn());
	}

	/**
	 * @param googleTruncated whether the Google query had to leave out terms to stay within Google's 32 words
	 * @param waitingForADecision how many of the jobs found for it still wait on the AI Assistant page
	 */
	record SearchView(long id, String name, ObjectNode criteria, Map<String, QueryView> queries,
			boolean googleTruncated, long waitingForADecision) {
	}

	record QueryView(String query, @Nullable String url) {
	}

	record FoundRow(long id, String title, @Nullable String company, @Nullable String location, String link,
			Decision decision, @Nullable String search, Instant foundAt, @Nullable Instant decidedAt,
			@Nullable Long applicationId, @Nullable LocalDate postedOn) {
	}

}
