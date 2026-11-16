package com.jobwarden.updates.service;

import com.jobwarden.updates.dto.UpdateStatus;

/**
 * Whether there's a newer JobWarden than the one running.
 */
public interface UpdateService {

	/**
	 * Answers right away, with what it last heard from GitHub, which it asks as the app starts and then daily. The
	 * first answer, right after the app starts, may not have it yet.
	 */
	UpdateStatus status();

}
