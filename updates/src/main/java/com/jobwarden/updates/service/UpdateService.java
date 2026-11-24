package com.jobwarden.updates.service;

import java.util.List;
import java.util.Optional;

import com.jobwarden.updates.dto.Release;
import com.jobwarden.updates.dto.UpdateStatus;
import org.jspecify.annotations.Nullable;

/**
 * Whether there's a newer JobWarden than the one running, and what each release changed. Each answers right away, with
 * what it last heard from GitHub, which it asks as the app starts and then daily: the first answer, right after the
 * app starts, may not have it yet.
 */
public interface UpdateService {

	/** The most releases {@link #whatsNew} tells of at once. */
	int WHATS_NEW_MOST = 5;

	UpdateStatus status();

	/**
	 * @return the releases GitHub listed, the newest version first; empty before it answers, when it can't be reached,
	 * or when checking is turned off
	 */
	List<Release> releases();

	/**
	 * @return the release of the version running, as GitHub lists it; empty for a build of its own (e.g. dev), or
	 * before GitHub answers
	 */
	Optional<Release> running();

	/**
	 * What changed up to the version running, since the one seen before it.
	 *
	 * @param seen the version seen before, e.g. 1.0.0; {@code null}, or text that isn't a version, when none was
	 * @return the releases after {@code seen}, up to the one running, the newest first, at most
	 * {@value #WHATS_NEW_MOST}; without a version seen, the release running alone. Empty when the version running isn't
	 * a release GitHub lists, or {@code seen} isn't older than it.
	 */
	List<Release> whatsNew(@Nullable String seen);

}
