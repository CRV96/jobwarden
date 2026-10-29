package com.jobwarden.updates.dto;

import java.net.URI;

import org.jspecify.annotations.Nullable;

/**
 * The version running, and the latest one released.
 *
 * @param version the version running, e.g. 0.1.0; {@code dev} when the build didn't set it
 * @param latest the latest release, or {@code null} when there's none yet, GitHub can't be reached, or checking is
 * turned off
 * @param releases the page of every release
 */
public record UpdateStatus(String version, @Nullable Release latest, URI releases) {

	/**
	 * @return whether the latest release is newer than the version running
	 */
	public boolean updateAvailable() {
		return this.latest != null && Version.isNewer(this.latest.version(), this.version);
	}

}
