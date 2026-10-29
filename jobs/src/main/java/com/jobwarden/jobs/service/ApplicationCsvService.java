package com.jobwarden.jobs.service;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import com.jobwarden.jobs.dto.CsvImport;

/**
 * The profile's jobs as a CSV file, one row per job: exported, e.g. to keep a copy or open in a spreadsheet, and
 * imported back, or from a spreadsheet or another tracker.
 * <p>
 * The columns, by their header: title, company, location, status, status_date, applied_on, applied_by (YOU,
 * CLAUDE or EXTENSION), salary, employment_type, link, source, rejection_reason, contact_name, contact_role,
 * contact_email, contact_phone, contact_link, referred_by, deadline, follow_up_on, notes, description, added_on and
 * last_modified.
 * An import needs only title; it reads the columns by their header, in any order, under their usual names too (Job
 * title, Company name, URL, Date applied...), and leaves out source, added_on and last_modified, which JobWarden sets
 * itself.
 */
public interface ApplicationCsvService {

	/** How many rows an import reads at most. */
	int MAX_ROWS = 5000;

	/**
	 * Writes every application of the profile, the oldest first: UTF-8 with a byte order mark, so a spreadsheet
	 * reads its letters right, and a value that a spreadsheet would run as a formula starts with an apostrophe.
	 */
	void export(long userId, Writer out) throws IOException;

	/**
	 * Adds a job for each row. Its timeline starts with the row's status at its status_date (today if it has none);
	 * when the row was applied to before that, with Applied at applied_on, then the status. A row the profile already
	 * has is skipped, so a file can be imported twice. Reads commas, semicolons or tabs between the values, as
	 * spreadsheets save them.
	 *
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the file has no title column
	 */
	CsvImport importCsv(long userId, Reader in) throws IOException;

}
