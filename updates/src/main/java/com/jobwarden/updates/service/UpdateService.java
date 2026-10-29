package com.jobwarden.updates.service;

import com.jobwarden.updates.dto.UpdateStatus;

/**
 * Whether there's a newer JobWarden than the one running.
 */
public interface UpdateService {

	/**
	 * Answers right away, with what it last heard from GitHub: when that's older than a few hours, it asks again in
	 * the background, so the next answer has it. The first one, right after the app starts, may not have it yet.
	 */
	UpdateStatus status();

}
