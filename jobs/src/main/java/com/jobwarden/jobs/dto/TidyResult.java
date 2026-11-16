package com.jobwarden.jobs.dto;

import java.util.List;

/**
 * What tidying up the applications with no answer did.
 *
 * @param movedToNoResponse the applications moved to No response, the company never having answered
 * @param followUpsSet the newer ones that got a day to follow up
 * @param waiting how many applications were waiting at Applied, these included
 */
public record TidyResult(List<Long> movedToNoResponse, List<Long> followUpsSet, int waiting) {
}
