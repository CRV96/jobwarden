package com.jobwarden.updates.dto;

import java.net.URI;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * A version of JobWarden released on GitHub.
 *
 * @param version its number, without the tag's v, e.g. 0.2.0
 * @param name what the release is called, e.g. JobWarden 0.2.0
 * @param url its page on GitHub: what changed, and the files to download
 * @param notes what it says changed: the HTML GitHub makes of its Markdown, cleaned down to text formatting (headings,
 * lists, quotes, code, tables and links, which open in a new tab), so a page can show it as it is; {@code null} when
 * it says nothing
 */
public record Release(String version, @Nullable String name, URI url, @Nullable Instant publishedAt,
		@Nullable String notes) {

	/**
	 * A release that says nothing of what changed.
	 */
	public Release(String version, @Nullable String name, URI url, @Nullable Instant publishedAt) {
		this(version, name, url, publishedAt, null);
	}

	/**
	 * @param version e.g. the version running
	 * @return whether this release is a later version; false when {@code version} isn't one, e.g. dev
	 */
	public boolean isNewerThan(String version) {
		return Version.isNewer(this.version, version);
	}

}
