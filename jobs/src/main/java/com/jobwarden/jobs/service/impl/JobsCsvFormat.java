package com.jobwarden.jobs.service.impl;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.AppliedBy;
import com.jobwarden.jobs.enums.CsvProblem;
import com.jobwarden.jobs.enums.RejectionReason;
import org.jspecify.annotations.Nullable;

/**
 * What the jobs' CSV file holds: its columns, under the headers they're known by, and how each cell's text reads as a
 * value, a date in the ways people write one, a status by its name or its English words. DefaultApplicationCsvService
 * imports and exports with it.
 */
final class JobsCsvFormat {

	private JobsCsvFormat() {
	}

	/** 2026-10-28, with a time or not: 2026-10-28 14:30, 2026-10-28 20:31:05, 2026-10-28T14:30:00Z */
	private static final Pattern YEAR_FIRST = Pattern
		.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[T ](\\d{1,2}):(\\d{2})(?::(\\d{2}))?.*)?");

	/** 28.10.2026, 28/10/2026 or 28-10-2026, with a time or not: the day first, as in Europe */
	private static final Pattern DAY_FIRST = Pattern
		.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})(?: (\\d{1,2}):(\\d{2})(?::(\\d{2}))?.*)?");

	/** Statuses under the names other trackers and spreadsheets give them */
	private static final Map<String, ApplicationStatus> STATUS_NAMES = Map.ofEntries(
			Map.entry("WISHLIST", ApplicationStatus.SAVED), Map.entry("BOOKMARKED", ApplicationStatus.SAVED),
			Map.entry("INTERESTED", ApplicationStatus.SAVED), Map.entry("INTERVIEW", ApplicationStatus.INTERVIEWING),
			Map.entry("INTERVIEWED", ApplicationStatus.INTERVIEWING), Map.entry("OFFERED", ApplicationStatus.OFFER),
			Map.entry("OFFER_RECEIVED", ApplicationStatus.OFFER), Map.entry("GHOSTED", ApplicationStatus.NO_RESPONSE),
			Map.entry("CLOSED", ApplicationStatus.POSITION_CLOSED), Map.entry("DECLINED", ApplicationStatus.WITHDRAWN),
			Map.entry("REOPENED", ApplicationStatus.POSITION_REOPENED));

	static ApplicationStatus status(@Nullable String value, @Nullable LocalDate appliedOn) throws InvalidRow {
		if (value == null) {
			return (appliedOn != null) ? ApplicationStatus.APPLIED : ApplicationStatus.SAVED;
		}
		String name = constantName(value);
		if (STATUS_NAMES.containsKey(name)) {
			return STATUS_NAMES.get(name);
		}
		return Arrays.stream(ApplicationStatus.values())
			.filter(status -> status.name().equals(name))
			.findFirst()
			.orElseThrow(() -> new InvalidRow(CsvProblem.UNKNOWN_STATUS, value));
	}

	/**
	 * YOU or CLAUDE; anything else isn't known.
	 */
	static @Nullable AppliedBy appliedBy(@Nullable String value) {
		String name = (value != null) ? constantName(value) : "";
		return Arrays.stream(AppliedBy.values()).filter(by -> by.name().equals(name)).findFirst().orElse(null);
	}

	static @Nullable RejectionReason rejectionReason(@Nullable String value) throws InvalidRow {
		if (value == null) {
			return null;
		}
		String name = constantName(value);
		return Arrays.stream(RejectionReason.values())
			.filter(reason -> reason.name().equals(name))
			.findFirst()
			.orElseThrow(() -> new InvalidRow(CsvProblem.UNKNOWN_REJECTION_REASON, value));
	}

	static @Nullable LocalDate date(@Nullable String value) throws InvalidRow {
		LocalDateTime dateTime = dateTime(value);
		return (dateTime != null) ? dateTime.toLocalDate() : null;
	}

	/**
	 * @return the date, at midnight when the value has no time
	 */
	static @Nullable LocalDateTime dateTime(@Nullable String value) throws InvalidRow {
		if (value == null) {
			return null;
		}
		Matcher yearFirst = YEAR_FIRST.matcher(value);
		Matcher dayFirst = DAY_FIRST.matcher(value);
		try {
			if (yearFirst.matches()) {
				return at(Integer.parseInt(yearFirst.group(1)), Integer.parseInt(yearFirst.group(2)),
						Integer.parseInt(yearFirst.group(3)), yearFirst.group(4), yearFirst.group(5), yearFirst.group(6));
			}
			if (dayFirst.matches()) {
				return at(Integer.parseInt(dayFirst.group(3)), Integer.parseInt(dayFirst.group(2)),
						Integer.parseInt(dayFirst.group(1)), dayFirst.group(4), dayFirst.group(5), dayFirst.group(6));
			}
		} catch (DateTimeException _) {
			// e.g. 2026-02-30: reported below, like text that isn't a date
		}
		throw new InvalidRow(CsvProblem.BAD_DATE, value);
	}

	static LocalDateTime at(int year, int month, int day, @Nullable String hour, @Nullable String minute,
			@Nullable String second) {
		LocalDate date = LocalDate.of(year, month, day);
		if (hour == null) {
			return date.atStartOfDay();
		}
		int seconds = (second != null) ? Integer.parseInt(second) : 0;
		return date.atTime(Integer.parseInt(hour), Integer.parseInt(minute), seconds);
	}

	/**
	 * A web address; one without its scheme, e.g. linkedin.com/jobs/view/1, gets https://.
	 */
	static @Nullable String link(@Nullable String value) throws InvalidRow {
		if (value == null) {
			return null;
		}
		String lower = value.toLowerCase(Locale.ROOT);
		if (lower.startsWith("https://") || lower.startsWith("http://")) {
			return value;
		}
		if (value.contains(".") && value.chars().noneMatch(Character::isWhitespace) && !value.contains(":")) {
			return "https://" + value;
		}
		throw new InvalidRow(CsvProblem.BAD_LINK, value);
	}

	/**
	 * "No response", "no-response" and "NO_RESPONSE" all become NO_RESPONSE.
	 */
	static String constantName(String value) {
		return value.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("(^_)|(_$)", "");
	}

	/**
	 * "Job title", "job-title" and "JOB_TITLE" all become job_title.
	 */
	static String headerName(String value) {
		return constantName(value).toLowerCase(Locale.ROOT);
	}

	/**
	 * Which value of a row each column is, by the header's names. The first column with a name wins.
	 */
	static Map<Column, Integer> columnsOf(List<String> header) {
		Map<Column, Integer> columns = new EnumMap<>(Column.class);
		for (int i = 0; i < header.size(); i++) {
			String name = headerName(header.get(i));
			for (Column column : Column.values()) {
				if (column.imported && column.names.contains(name)) {
					columns.putIfAbsent(column, i);
				}
			}
		}
		return columns;
	}

	/**
	 * The columns, in the order an export writes them, under the names an import knows them by. The first name is
	 * the header an export writes.
	 */
	enum Column {

		TITLE("title", "job_title", "position", "role", "job", "job_name"),
		COMPANY("company", "company_name", "employer", "organization", "organisation"),
		LOCATION("location", "city", "place"),
		STATUS("status", "stage", "state"),
		STATUS_DATE("status_date", "status_since", "date", "updated"),
		APPLIED_ON("applied_on", "applied", "date_applied", "applied_date", "application_date"),
		APPLIED_BY("applied_by"),
		SALARY("salary", "pay", "compensation"),
		EMPLOYMENT_TYPE("employment_type", "job_type", "type", "contract"),
		LINK("link", "url", "job_url", "job_link", "posting", "posting_url"),
		SOURCE(false, "source"),
		REJECTION_REASON("rejection_reason"),
		CONTACT_NAME("contact_name", "contact", "recruiter"),
		CONTACT_ROLE("contact_role"),
		CONTACT_EMAIL("contact_email", "email"),
		CONTACT_PHONE("contact_phone", "phone"),
		CONTACT_LINK("contact_link", "linkedin"),
		REFERRED_BY("referred_by", "referral"),
		DEADLINE("deadline", "closing_date"),
		FOLLOW_UP_ON("follow_up_on", "follow_up", "next_follow_up"),
		NOTES("notes", "note", "comments"),
		DESCRIPTION("description", "job_description"),
		ADDED_ON(false, "added_on"),
		LAST_MODIFIED(false, "last_modified");

		final String header;

		final Set<String> names;

		/** Whether an import reads it: JobWarden sets the others itself */
		final boolean imported;

		Column(String... names) {
			this(true, names);
		}

		Column(boolean imported, String... names) {
			this.header = names[0];
			this.names = Set.of(names);
			this.imported = imported;
		}

	}

	/**
	 * Why a row can't be imported. Checked, so that every row's reading is sure to report it.
	 */
	static final class InvalidRow extends Exception {

		final CsvProblem problem;

		final @Nullable String value;

		InvalidRow(CsvProblem problem, @Nullable String value) {
			super(problem + ": " + value, null, false, false);
			this.problem = problem;
			this.value = value;
		}

	}

}
