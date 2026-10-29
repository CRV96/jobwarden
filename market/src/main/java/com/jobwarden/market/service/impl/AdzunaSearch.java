package com.jobwarden.market.service.impl;

import java.util.Locale;
import java.util.Map;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * What to search job ads for, from a role as the survey names it and a skill. Ads match every word, so it stays short:
 * with a skill, {@code java developer}; without one, the role as job titles say it, {@code backend developer}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AdzunaSearch {

	private static final Map<String, String> TITLES = Map.ofEntries(
			Map.entry("Developer, full-stack", "full stack developer"),
			Map.entry("Developer, back-end", "backend developer"),
			Map.entry("Developer, front-end", "frontend developer"),
			Map.entry("Developer, mobile", "mobile developer"),
			Map.entry("Developer, desktop or enterprise applications", "software developer"),
			Map.entry("Developer, embedded applications or devices", "embedded developer"),
			Map.entry("Developer, game or graphics", "game developer"),
			Map.entry("Developer, QA or test", "qa engineer"),
			Map.entry("Developer, AI apps or physical AI", "ai developer"),
			Map.entry("Architect, software or solutions", "software architect"),
			Map.entry("Engineering manager", "engineering manager"),
			Map.entry("DevOps engineer or professional", "devops engineer"),
			Map.entry("Cloud infrastructure engineer", "cloud engineer"),
			Map.entry("AI/ML engineer", "machine learning engineer"),
			Map.entry("Data engineer", "data engineer"),
			Map.entry("Data scientist", "data scientist"),
			Map.entry("Database administrator or engineer", "database administrator"),
			Map.entry("Cybersecurity or InfoSec professional", "security engineer"),
			Map.entry("System administrator", "system administrator"));

	static String of(@Nullable String role, @Nullable String skill) {
		if (skill != null && !skill.isBlank()) {
			return skill.strip().toLowerCase(Locale.ROOT) + " developer";
		}
		if (role == null || role.isBlank()) {
			return "software developer";
		}
		return TITLES.getOrDefault(role.strip(), role.strip().toLowerCase(Locale.ROOT));
	}

}
