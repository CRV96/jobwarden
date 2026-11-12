package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jobwarden.discover.dto.AddedFoundJobs;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.dto.NewFoundJob;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.hiring.service.HiringService;
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

	private static final int MAX_LINKS_AT_ONCE = 100;

	private final Profiles profiles;

	private final DiscoveryService discovery;

	private final QueryGenerator queries;

	private final HiringService hiring;

	@Override
	public List<McpTool> tools() {
		return List.of(listSavedSearches(), saveSearch(), addFoundJobs(), listFoundJobs(), filterLinks());
	}

	private McpTool listSavedSearches() {
		return new McpTool("list_saved_searches", "List saved searches",
				"The profile's saved job searches: filters, and ready-made queries for Google (on companies' job boards), "
						+ "LinkedIn, Reddit, X and remote job boards.",
				object(properties(Profiles.ARGUMENT, Profiles.schema())), true, false,
				call -> Map.of("searches", searches(this.profiles.of(call))));
	}

	/** The saved searches as list_saved_searches gives them, which get_request also hands out to run them. */
	List<SearchView> searches(long userId) {
		return this.discovery.findSearches(userId).stream().map(this::view).toList();
	}

	private McpTool saveSearch() {
		return new McpTool("save_search", "Save a search",
				"Saves filters under a name, replacing a search of that name, and returns its queries.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"name", string("e.g. Remote Java in Europe"),
						"titles", strings("Any of these"),
						"locations", strings("e.g. Europe, Germany"),
						"skills", strings("Any of these"),
						"sites", strings("Domains for Google; default Lever, Greenhouse and Ashby"),
						"work_types", list(oneOf(WorkType.values())),
						"employment_types", list(oneOf(EmploymentType.values())),
						"excludes", strings("e.g. junior"),
						"recency", oneOf(Recency.values(), "Default ANY")),
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
					return view(this.discovery.saveSearch(userId, call.requiredString("name"), criteria));
				});
	}

	private McpTool addFoundJobs() {
		ObjectNode job = object(properties(
				"title", string("As posted"),
				"link", string("The posting's own page, not a search"),
				"company", string(),
				"location", string("e.g. Remote, Europe"),
				"salary", string("As posted"),
				"description", string("A few sentences"),
				"note", string("Why it fits, one sentence: the profile reads it"),
				"posted_on", date()),
				"title", "link");
		return new McpTool("add_found_jobs", "Add found jobs",
				"Adds jobs you found to the AI Assistant page, for the profile to save or discard. Only real, open postings, "
						+ "each with its own link; links the profile has (discarded ones too) are skipped. Max "
						+ MAX_JOBS_AT_ONCE + ".",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"search_id", integer("The saved search they're for"),
						"request_id", integer("The request they're for"),
						"search_label", string("Without either: what you searched for, e.g. Remote Kotlin jobs"),
						"jobs", list(job)),
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
					return Map.of("added", added.added().size(), "skipped", added.skipped(), "waiting_for_a_decision",
							this.discovery.countToReview(userId, null));
				});
	}

	private McpTool listFoundJobs() {
		return new McpTool("list_found_jobs", "List found jobs",
				"Jobs found for the profile and its decision: PENDING (on the AI Assistant page), SAVED, APPLIED or "
						+ "DISCARDED.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"decision", oneOf(Decision.values()),
						"limit", integer("Default 50, max 200"))),
				true, false, call -> {
					long userId = this.profiles.of(call);
					return Map.of("jobs", this.discovery
						.findFoundJobs(userId, call.constant("decision", Decision.class), call.limit("limit", 50, 200))
						.stream()
						.map(DiscoverTools::row)
						.toList());
				});
	}

	/**
	 * Which search results to open: the profile's jobs, found jobs, hiring posts and recruiters are left out, so the
	 * assistant opens only what may be new. Opening a page costs far more than asking.
	 */
	private McpTool filterLinks() {
		return new McpTool("filter_links", "Filter links",
				"Which links are new to the profile: not its jobs, found jobs, hiring posts or recruiters, hidden or "
						+ "removed ones too. Call it on search results before opening any. Max " + MAX_LINKS_AT_ONCE + ".",
				object(properties(Profiles.ARGUMENT, Profiles.schema(), "links", strings()), "links"),
				true, false, call -> {
					long userId = this.profiles.of(call);
					List<String> links = call.strings("links").stream().distinct().toList();
					if (links.size() > MAX_LINKS_AT_ONCE) {
						throw new ToolException("At most " + MAX_LINKS_AT_ONCE + " links at once: filter the rest in another call");
					}
					Set<String> known = new HashSet<>(this.discovery.findKnownLinks(userId, links));
					known.addAll(this.hiring.findKnownLinks(userId, links));
					List<String> fresh = links.stream().filter(link -> !known.contains(link)).toList();
					return Map.of("new_links", fresh, "known", links.size() - fresh.size());
				});
	}

	/** A search with each platform's query: to run with a web search, which needs no address. */
	private SearchView view(SavedSearch search) {
		GeneratedQueries generated = this.queries.generate(search.criteria());
		Map<String, String> queries = generated.all()
			.stream()
			.filter(query -> !query.query().isEmpty())
			.collect(Collectors.toMap(query -> query.platform().name(), GeneratedQuery::query, (a, b) -> a,
					LinkedHashMap::new));
		return new SearchView(search.id(), search.name(), criteria(search.criteria()), queries);
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
	 * @param queries each platform's query, by platform
	 */
	record SearchView(long id, String name, ObjectNode criteria, Map<String, String> queries) {
	}

	record FoundRow(long id, String title, @Nullable String company, @Nullable String location, String link,
			Decision decision, @Nullable String search, Instant foundAt, @Nullable Instant decidedAt,
			@Nullable Long applicationId, @Nullable LocalDate postedOn) {
	}

}
