package com.jobwarden.jobs.service;

import java.time.LocalDate;
import java.time.ZoneId;

import com.jobwarden.jobs.dto.JobActivity;

/**
 * What the profile did lately, for the homepage. Takes the owning user's id, like {@link ApplicationService}.
 */
public interface ActivityService {

	/**
	 * @param today the day it is where the profile is
	 * @param zone where that is: when a job was saved, it's that day there
	 */
	JobActivity find(long userId, LocalDate today, ZoneId zone);

}
