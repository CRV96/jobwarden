package com.jobwarden.jobs.dto;

import java.util.List;

/**
 * What importing a CSV file of jobs did.
 *
 * @param imported how many rows became applications
 * @param skipped how many rows the profile already had: the same link, or without a link, the same title at the same
 * company
 * @param problems the rows that weren't imported, and why, in the file's order
 */
public record CsvImport(int imported, int skipped, List<CsvImportProblem> problems) {

	public CsvImport {
		problems = List.copyOf(problems);
	}

}
