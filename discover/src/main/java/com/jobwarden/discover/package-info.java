/**
 * Discover: searches a profile keeps for finding jobs (the Boolean search's filters, under a name), and the jobs found
 * for them, by Claude over MCP, waiting on the Discover page to be saved, applied to or discarded. Saving or applying
 * makes one an application in the jobs module. Owns the {@code saved_searches} and {@code found_jobs} tables.
 * <p>
 * Public API: the {@code dto}, {@code enums}, {@code service} and {@code exception} packages, marked with
 * {@code @NamedInterface}. {@code entity}, {@code repository} and {@code service.impl} are internal to the module.
 */
package com.jobwarden.discover;
