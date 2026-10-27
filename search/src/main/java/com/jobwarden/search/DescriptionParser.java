package com.jobwarden.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

/**
 * Reads a description of the job someone wants, such as "senior backend engineer, remote Europe, contract", into
 * {@link SearchCriteria}: the words it knows, in English. Job titles are a role word (developer, engineer...) with
 * what may come before it (backend, data, Java...); locations, work types and contracts come from word lists, and
 * "not", "without" or "no" before a word excludes it. The rest of the text is ignored, and the criteria have no
 * sites: the page keeps its own.
 */
@Service
public class DescriptionParser {

	private static final Pattern EXCLUSION = Pattern.compile("\\b(?:not|without|exclude|excluding|except|no)\\s+([a-z][a-z+#.-]*)");

	private static final Map<String, String> LOCATIONS = locations();

	private static final Map<String, WorkType> WORK_TYPES = Map.of("remote", WorkType.REMOTE, "hybrid", WorkType.HYBRID,
			"onsite", WorkType.ONSITE, "on-site", WorkType.ONSITE, "on site", WorkType.ONSITE, "in office",
			WorkType.ONSITE, "in-office", WorkType.ONSITE);

	private static final Map<String, EmploymentType> EMPLOYMENT_TYPES = Map.ofEntries(
			Map.entry("full-time", EmploymentType.FULL_TIME), Map.entry("full time", EmploymentType.FULL_TIME),
			Map.entry("fulltime", EmploymentType.FULL_TIME), Map.entry("permanent", EmploymentType.FULL_TIME),
			Map.entry("contract", EmploymentType.CONTRACT), Map.entry("contractor", EmploymentType.CONTRACT),
			Map.entry("contract-to-hire", EmploymentType.CONTRACT), Map.entry("freelance", EmploymentType.FREELANCE),
			Map.entry("freelancer", EmploymentType.FREELANCE), Map.entry("freelancing", EmploymentType.FREELANCE),
			Map.entry("intern", EmploymentType.INTERNSHIP), Map.entry("internship", EmploymentType.INTERNSHIP));

	/** Kept as keywords: a title with them would find too little. */
	private static final List<String> SENIORITIES = List.of("principal", "senior", "junior", "entry-level", "entry level",
			"mid-level", "mid level", "lead", "staff");

	/** What may come before a role word in a title, longest first, so "full stack" wins over "stack". */
	private static final List<String> SPECIALTIES = List.of("machine learning", "site reliability", "full stack",
			"full-stack", "fullstack", "back end", "back-end", "backend", "front end", "front-end", "frontend",
			"test automation", "qa automation", "embedded software", "software", "data", "product", "project",
			"engineering", "ux", "ui", "mobile", "ios", "android", "cloud", "platform", "security", "solutions", "devops",
			"ml", "qa", "test", "web", "java", "python", "javascript", "typescript", "react", "node.js", "php", "ruby",
			"golang", "go", ".net", "c#", "c++", "kotlin", "scala", "salesforce", "sap");

	private static final List<String> ROLES = List.of("developer", "engineer", "designer", "manager", "architect",
			"analyst", "programmer", "scientist", "administrator", "tester");

	/** Roles that are titles on their own. */
	private static final List<String> STANDALONE_ROLES = List.of("devops", "sre", "recruiter", "consultant", "qa",
			"scrum master", "tech lead");

	/** Skills whose names are everyday words too: only their unambiguous names count. */
	private static final Map<String, String> SKILL_ALIASES = inOrder("golang", "Go", "k8s", "Kubernetes", "postgres",
			"PostgreSQL", "nodejs", "Node.js", "node", "Node.js", "vue.js", "Vue", "reactjs", "React");

	private static final Set<String> AMBIGUOUS_SKILLS = Set.of("go", "spring", "swift", "rust", "spark");

	/** How words are written in titles, where it isn't with a capital first letter. */
	private static final Map<String, String> SPELLINGS = Map.ofEntries(Map.entry("ios", "iOS"), Map.entry("ux", "UX"),
			Map.entry("ui", "UI"), Map.entry("qa", "QA"), Map.entry("ml", "ML"), Map.entry("sre", "SRE"),
			Map.entry("devops", "DevOps"), Map.entry(".net", ".NET"), Map.entry("c#", "C#"), Map.entry("c++", "C++"),
			Map.entry("php", "PHP"), Map.entry("sap", "SAP"), Map.entry("javascript", "JavaScript"),
			Map.entry("typescript", "TypeScript"), Map.entry("node.js", "Node.js"), Map.entry("golang", "Go"));

	public SearchCriteria parse(String description) {
		String text = " " + description.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + " ";

		Set<String> excluded = new HashSet<>();
		List<String> excludes = new ArrayList<>();
		Matcher exclusion = EXCLUSION.matcher(text);
		while (exclusion.find()) {
			excluded.add(exclusion.group(1));
			excludes.add(exclusion.group(1));
		}

		String rest = text;
		List<String> titles = new ArrayList<>();
		for (String specialty : SPECIALTIES) {
			for (String role : ROLES) {
				String title = specialty + " " + role;
				if (mentions(rest, title) && !excluded.contains(role)) {
					titles.add(spelled(title));
					rest = rest.replace(title, " ");
				}
			}
		}
		for (String role : Stream.concat(ROLES.stream(), STANDALONE_ROLES.stream()).toList()) {
			if (mentions(rest, role) && !excluded.contains(role)) {
				titles.add(spelled(role));
				rest = rest.replace(role, " ");
			}
		}

		// The skills a title names are in the title already: "Java developer" isn't also Java
		List<String> skills = new ArrayList<>(found(text, excluded, seniorities()).values());
		skills.addAll(found(rest, excluded, skills()).values());

		return SearchCriteria.builder()
			.titles(titles)
			.locations(List.copyOf(found(text, excluded, LOCATIONS).values()))
			.skills(skills)
			.workTypes(Set.copyOf(found(text, excluded, WORK_TYPES).values()))
			.employmentTypes(Set.copyOf(found(text, excluded, EMPLOYMENT_TYPES).values()))
			.excludes(excludes)
			.build();
	}

	/** What the words the text mentions stand for, unless they're excluded. */
	private static <T> Map<String, T> found(String text, Set<String> excluded, Map<String, T> words) {
		Map<String, T> found = new LinkedHashMap<>();
		words.forEach((word, meaning) -> {
			if (mentions(text, word) && !excluded.contains(word)) {
				found.put(word, meaning);
			}
		});
		return found;
	}

	/** Whether the text has the phrase as words of their own: "go" isn't in "google". */
	private static boolean mentions(String text, String phrase) {
		return Pattern.compile("(?:^|[^a-z])" + Pattern.quote(phrase) + "(?:[^a-z]|$)").matcher(text).find();
	}

	private static String spelled(String phrase) {
		return Stream.of(phrase.split(" "))
			.map(word -> SPELLINGS.getOrDefault(word, Character.toUpperCase(word.charAt(0)) + word.substring(1)))
			.collect(Collectors.joining(" "));
	}

	/** The regions the page suggests, by name and by the other names people use for them. */
	private static Map<String, String> locations() {
		Map<String, String> locations = new LinkedHashMap<>();
		SearchSuggestions.REGIONS.stream()
			.map(Region::name)
			.filter(name -> !name.equals("Remote"))
			.forEach(name -> locations.put(name.toLowerCase(Locale.ROOT), name));
		locations.putAll(inOrder("eu", "Europe", "european union", "Europe", "usa", "US", "united states", "US",
				"united kingdom", "UK", "britain", "UK", "anywhere", "Worldwide", "global", "Worldwide"));
		return locations;
	}

	/** Words and what each stands for, in this order. */
	private static Map<String, String> inOrder(String... wordsAndMeanings) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < wordsAndMeanings.length; i += 2) {
			map.put(wordsAndMeanings[i], wordsAndMeanings[i + 1]);
		}
		return map;
	}

	private static Map<String, String> seniorities() {
		Map<String, String> seniorities = new LinkedHashMap<>();
		SENIORITIES.forEach(seniority -> seniorities.put(seniority, seniority));
		return seniorities;
	}

	private static Map<String, String> skills() {
		Map<String, String> skills = new LinkedHashMap<>();
		SearchSuggestions.SKILLS.stream()
			.filter(skill -> !AMBIGUOUS_SKILLS.contains(skill.toLowerCase(Locale.ROOT)))
			.forEach(skill -> skills.put(skill.toLowerCase(Locale.ROOT), skill));
		skills.putAll(SKILL_ALIASES);
		return skills;
	}

}
