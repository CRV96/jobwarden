/**
 * Apply: Claude applies to jobs for the profile. The answers application forms ask for (work permit, notice period,
 * salary expectation, the profile's own questions and answers...), and the jobs queued for Claude, each with how it
 * went: applied, filled in for the profile to send, waiting for an answer, skipped. Claude does it in the profile's
 * browser, through the MCP server; a job applied to moves to Applied, by Claude (the jobs module). Owns the
 * {@code application_answers} and {@code apply_items} tables.
 * <p>
 * Public API: the {@code dto}, {@code enums}, {@code exception} and {@code service} packages, marked with
 * {@code @NamedInterface}. {@code entity}, {@code repository} and {@code service.impl} are internal to the module.
 */
package com.jobwarden.apply;
