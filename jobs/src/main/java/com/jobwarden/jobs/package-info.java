/**
 * Jobs: the core domain. Job postings the user bookmarked or applied to, their status,
 * and the event timeline of each application; the offers they made, compared side by side, and the briefs to
 * negotiate them. Owns the {@code applications} and {@code application_events} tables, {@code job_offers} and
 * {@code negotiation_briefs} among others.
 * <p>
 * Public API: the {@code dto}, {@code enums} and {@code service} packages, marked with {@code @NamedInterface}.
 * {@code entity}, {@code repository} and {@code service.impl} are internal to the module.
 */
@NullMarked
package com.jobwarden.jobs;

import org.jspecify.annotations.NullMarked;
