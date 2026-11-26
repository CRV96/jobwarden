package com.jobwarden.jobs.repository;

import java.time.Instant;

/**
 * When a job's logo last changed, selected straight into a record: the version its address carries, so a browser that
 * kept the old one asks for the new one.
 */
public record LogoVersion(long applicationId, Instant updatedAt) {
}
