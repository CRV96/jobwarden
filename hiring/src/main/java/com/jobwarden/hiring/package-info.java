/**
 * Hiring: who's hiring. Recruiters and hiring managers, and their posts saying they're hiring, which Claude finds on
 * LinkedIn, X and Reddit through the MCP server; Claude finds recruiters too, and the contacts saved on the profile's
 * jobs (the jobs module) are some. The profile follows the recruiters whose posts it wants first, adds them by hand,
 * removes the others, hides the posts it doesn't want, and saves the others as jobs. Owns the {@code recruiters} and
 * {@code hiring_posts} tables.
 * <p>
 * Public API: the {@code dto}, {@code enums}, {@code exception} and {@code service} packages, marked with
 * {@code @NamedInterface}. {@code entity}, {@code repository} and {@code service.impl} are internal to the module.
 */
package com.jobwarden.hiring;
