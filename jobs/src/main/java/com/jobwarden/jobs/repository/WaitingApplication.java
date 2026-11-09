package com.jobwarden.jobs.repository;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/**
 * An application waiting at its status: the day of its timeline's latest event, and when to follow up on it.
 */
public record WaitingApplication(long applicationId, LocalDate lastEvent, @Nullable LocalDate followUpOn) {
}
