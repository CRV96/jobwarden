package com.jobwarden.jobs.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * What the AI Assistant found out on the web about a job's company: who they are, what the people who work there say,
 * what they pay, and what's new with them. Build it with {@link #builder()}.
 *
 * @param company who they are: what they make, for whom, how big, where, who owns them, how they're doing
 * @param ratings the employer's ratings, each with its site and how many reviews, e.g. Glassdoor 3.9/5 (210 reviews)
 * @param pros what the people who work there praise, most often said first
 * @param cons what they complain about, most often said first
 * @param salaries what the company pays this role, or roles near it, each with its source
 * @param benefits the perks, the work model, the hours and the holidays, as reported
 * @param news what's new: growth, funding, layoffs, acquisitions, products, with when
 * @param interviews how their hiring goes, as candidates tell it: the rounds, how long, what's asked
 * @param watchOut what to look into before saying yes, e.g. layoffs, many bad reviews, late pay
 * @param sources where it was read: review sites, the news, the company's pages
 */
@Builder
public record CompanyResearch(String company, List<String> ratings, List<String> pros, List<String> cons,
		List<String> salaries, List<String> benefits, List<String> news, @Nullable String interviews,
		@Nullable String watchOut, List<String> sources, @Nullable Instant updatedAt) {

	/**
	 * A builder can't make sure every required field is set, so a missing one fails here, right away. The lists left
	 * out are empty.
	 */
	public CompanyResearch {
		Objects.requireNonNull(company, "company");
		ratings = (ratings == null) ? List.of() : List.copyOf(ratings);
		pros = (pros == null) ? List.of() : List.copyOf(pros);
		cons = (cons == null) ? List.of() : List.copyOf(cons);
		salaries = (salaries == null) ? List.of() : List.copyOf(salaries);
		benefits = (benefits == null) ? List.of() : List.copyOf(benefits);
		news = (news == null) ? List.of() : List.copyOf(news);
		sources = (sources == null) ? List.of() : List.copyOf(sources);
	}

}
