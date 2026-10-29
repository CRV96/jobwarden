package com.jobwarden.jobs.dto;

import com.jobwarden.jobs.enums.CsvProblem;
import org.jspecify.annotations.Nullable;

/**
 * A row of a CSV file that wasn't imported.
 *
 * @param row the row's number in the file, the header being row 1: a spreadsheet's row number
 * @param value what the row had that couldn't be read, e.g. the status, or {@code null} when it's missing
 */
public record CsvImportProblem(int row, CsvProblem problem, @Nullable String value) {
}
