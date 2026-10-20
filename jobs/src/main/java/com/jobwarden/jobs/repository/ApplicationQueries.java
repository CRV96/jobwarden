package com.jobwarden.jobs.repository;

import java.util.Map;

import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationStatus;
import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.Page;

/**
 * The application list's queries, whose filters and order depend on what the user picked. Spring Data adds them to
 * {@link ApplicationRepository} from {@link ApplicationQueriesImpl}.
 */
public interface ApplicationQueries {

	/**
	 * One page of the list's rows. Selects only the columns the list shows: the description and the raw payload can
	 * be large, and loading them for every row would be wasted work.
	 */
	Page<ApplicationSummary> findSummaries(long userId, ApplicationQuery query);

	/**
	 * How many of the applications matching the search have each status. A status no application has is left out.
	 *
	 * @param search as in {@link ApplicationQuery#search()}
	 */
	Map<ApplicationStatus, Long> countByStatus(long userId, @Nullable String search);

}
