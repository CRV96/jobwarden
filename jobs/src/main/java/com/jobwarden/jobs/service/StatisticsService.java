package com.jobwarden.jobs.service;

import com.jobwarden.jobs.dto.JobStatistics;

/**
 * How the profile's applications went: how often they got an answer, and an interview, by site, by who applied and by
 * the fit's score; why they were turned down; and what the fits found missing most often. From the timelines alone.
 */
public interface StatisticsService {

	/** How many of the requirements the fits found missing it gives, the most frequent. */
	int SKILL_GAPS = 12;

	/** How many of the sites it gives, those applied to most. */
	int SITES = 12;

	JobStatistics find(long userId);

}
