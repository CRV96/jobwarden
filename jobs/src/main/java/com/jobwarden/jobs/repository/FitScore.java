package com.jobwarden.jobs.repository;

import java.math.BigDecimal;

/**
 * A job's fit score alone, selected straight into a record.
 */
public record FitScore(long applicationId, BigDecimal score) {
}
