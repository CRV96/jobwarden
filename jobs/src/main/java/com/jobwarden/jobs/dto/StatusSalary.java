package com.jobwarden.jobs.dto;

import java.math.BigDecimal;

import com.jobwarden.jobs.enums.ApplicationStatus;

/**
 * The median salary of the jobs with one status.
 *
 * @param count how many of the jobs with the status have a salary
 */
public record StatusSalary(ApplicationStatus status, BigDecimal median, int count) {
}
