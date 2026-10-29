package com.jobwarden.capture.dto;

import java.net.URI;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Which JobWarden the server runs, and the latest one released: the extension compares both with its own version,
 * and says when there's a newer one to get, or when it's older than the server.
 *
 * @param version the server's version, e.g. 0.1.0; {@code dev} when the build didn't set it
 * @param latest the latest release on GitHub, or {@code null} when there's none, or GitHub can't be reached
 * @param releases the page of every release
 */
public record ServerVersion(String version, @Nullable LatestRelease latest, URI releases) {

	/**
	 * @param url the release's page: what changed, and the files to download
	 */
	public record LatestRelease(String version, @Nullable String name, URI url, @Nullable Instant publishedAt) {
	}

}
