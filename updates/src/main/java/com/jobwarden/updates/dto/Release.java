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
 */
public record Release(String version, @Nullable String name, URI url, @Nullable Instant publishedAt) {
}
