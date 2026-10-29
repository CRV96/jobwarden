package com.jobwarden.jobs.service.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.CsvImport;
import com.jobwarden.jobs.dto.CsvImportProblem;
import com.jobwarden.jobs.dto.FollowUp;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.entity.ApplicationEntity;
import com.jobwarden.jobs.entity.TimelineEventEntity;
import com.jobwarden.jobs.enums.AppliedBy;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CsvProblem;
import com.jobwarden.jobs.enums.RejectionReason;
import com.jobwarden.jobs.exception.JobsErrorCode;
import com.jobwarden.jobs.repository.ApplicationRepository;
import com.jobwarden.jobs.service.ApplicationCsvService;
import com.jobwarden.jobs.service.ApplicationService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@CustomLog
@RequiredArgsConstructor
class DefaultApplicationCsvService implements ApplicationCsvService {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	/** For a time the extension recorded to the second, so an import keeps it as it was */
	private static final DateTimeFormatter TIME_WITH_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
			Map.entry("CLOSED", ApplicationStatus.POSITION_CLOSED), Map.entry("DECLINED", ApplicationStatus.WITHDRAWN));

	private final ApplicationRepository applications;

	private final ApplicationService applicationService;

	@Override
	public void export(long userId, Writer out) throws IOException {
		List<ApplicationEntity> all = this.applications.findByUserIdOrderByCapturedAtAscIdAsc(userId);
		out.write(Csv.BYTE_ORDER_MARK);
		Csv.write(out, Arrays.stream(Column.values()).map(column -> column.header).toList());
		for (ApplicationEntity application : all) {
			Csv.write(out, Arrays.stream(Column.values()).map(column -> valueOf(application, column)).toList());
		}
		log.info("Exported {} applications of profile {}", all.size(), userId);
	}

	@Override
	@Transactional
	public CsvImport importCsv(long userId, Reader in) throws IOException {
		StringWriter text = new StringWriter();
		in.transferTo(text);
		List<List<String>> records = Csv.parse(text.toString());
		Map<Column, Integer> columns = records.isEmpty() ? Map.of() : columnsOf(records.getFirst());
		if (!columns.containsKey(Column.TITLE)) {
			throw new BusinessRuleException(JobsErrorCode.CSV_WITHOUT_TITLE_COLUMN, "The CSV file has no title column");
		}

		Known known = known(userId);
		int imported = 0;
		int skipped = 0;
		List<CsvImportProblem> problems = new ArrayList<>();
		for (int i = 1; i < records.size(); i++) {
			// The header is row 1, as in a spreadsheet
			int row = i + 1;
			if (i > MAX_ROWS) {
				problems.add(new CsvImportProblem(row, CsvProblem.TOO_MANY_ROWS, String.valueOf(MAX_ROWS)));
				break;
			}
			ImportedJob job;
			try {
				job = read(userId, new Row(records.get(i), columns));
			} catch (InvalidRow invalid) {
				problems.add(new CsvImportProblem(row, invalid.problem, invalid.value));
				continue;
			}
			if (!known.add(job.application())) {
				skipped++;
				continue;
			}
			save(job);
			imported++;
		}
		log.info("Imported {} applications for profile {} from CSV, skipped {} already there, {} rows with problems",
				imported, userId, skipped, problems.size());
		return new CsvImport(imported, skipped, problems);
	}

	private void save(ImportedJob job) {
		NewApplication application = job.application();
		long userId = application.userId();
		long id = this.applicationService.create(application);
		if (job.laterStatus() != null) {
			this.applicationService.addEvent(userId, id, job.laterStatus());
		}
		if (!job.followUp().equals(FollowUp.NONE)) {
			this.applicationService.updateFollowUp(userId, id, job.followUp());
		}
		if (job.notes() != null) {
			this.applicationService.updateNotes(userId, id, job.notes());
		}
	}

	/**
	 * The row as an application to create. Its timeline starts at Applied when it was applied to before its status,
	 * e.g. applied on the 1st, rejected on the 20th: then the status is a second event.
	 */
	private static ImportedJob read(long userId, Row row) throws InvalidRow {
		String title = row.get(Column.TITLE);
		if (title == null) {
			throw new InvalidRow(CsvProblem.NO_TITLE, null);
		}
		LocalDate appliedOn = date(row.get(Column.APPLIED_ON));
		ApplicationStatus status = status(row.get(Column.STATUS), appliedOn);
		LocalDateTime statusAt = dateTime(row.get(Column.STATUS_DATE));
		LocalDate statusDate = (statusAt != null) ? statusAt.toLocalDate()
				: (status == ApplicationStatus.APPLIED && appliedOn != null) ? appliedOn : LocalDate.now();
		LocalTime statusTime = (statusAt != null && !statusAt.toLocalTime().equals(LocalTime.MIDNIGHT))
				? statusAt.toLocalTime() : null;
		RejectionReason rejectionReason = status.hasRejectionReason() ? rejectionReason(row.get(Column.REJECTION_REASON))
				: null;

		boolean appliedBefore = appliedOn != null && status != ApplicationStatus.SAVED
				&& status != ApplicationStatus.APPLIED && !appliedOn.isAfter(statusDate);
		NewApplication application = NewApplication.builder()
			.userId(userId)
			.title(title)
			.company(row.get(Column.COMPANY))
			.location(row.get(Column.LOCATION))
			.link(link(row.get(Column.LINK)))
			.salary(row.get(Column.SALARY))
			.employmentType(row.get(Column.EMPLOYMENT_TYPE))
			.description(row.get(Column.DESCRIPTION))
			.appliedBy(appliedBy(row.get(Column.APPLIED_BY)))
			.initialStatus(appliedBefore ? ApplicationStatus.APPLIED : status)
			.initialStatusDate(appliedBefore ? appliedOn : statusDate)
			.initialStatusTime(appliedBefore ? null : statusTime)
			.initialRejectionReason(appliedBefore ? null : rejectionReason)
			.build();
		NewTimelineEvent laterStatus = appliedBefore ? NewTimelineEvent.builder()
			.eventDate(statusDate)
			.eventTime(statusTime)
			.stageCategory(status)
			.rejectionReason(rejectionReason)
			.build() : null;
		FollowUp followUp = FollowUp.builder()
			.contactName(row.get(Column.CONTACT_NAME))
			.contactRole(row.get(Column.CONTACT_ROLE))
			.contactEmail(row.get(Column.CONTACT_EMAIL))
			.contactPhone(row.get(Column.CONTACT_PHONE))
			.contactLink(link(row.get(Column.CONTACT_LINK)))
			.referredBy(row.get(Column.REFERRED_BY))
			.deadline(date(row.get(Column.DEADLINE)))
			.followUpOn(date(row.get(Column.FOLLOW_UP_ON)))
			.build();
		return new ImportedJob(application, laterStatus, followUp, row.get(Column.NOTES));
	}

	private static ApplicationStatus status(@Nullable String value, @Nullable LocalDate appliedOn) throws InvalidRow {
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
	private static @Nullable AppliedBy appliedBy(@Nullable String value) {
		String name = (value != null) ? constantName(value) : "";
		return Arrays.stream(AppliedBy.values()).filter(by -> by.name().equals(name)).findFirst().orElse(null);
	}

	private static @Nullable RejectionReason rejectionReason(@Nullable String value) throws InvalidRow {
		if (value == null) {
			return null;
		}
		String name = constantName(value);
		return Arrays.stream(RejectionReason.values())
			.filter(reason -> reason.name().equals(name))
			.findFirst()
			.orElseThrow(() -> new InvalidRow(CsvProblem.UNKNOWN_REJECTION_REASON, value));
	}

	private static @Nullable LocalDate date(@Nullable String value) throws InvalidRow {
		LocalDateTime dateTime = dateTime(value);
		return (dateTime != null) ? dateTime.toLocalDate() : null;
	}

	/**
	 * @return the date, at midnight when the value has no time
	 */
	private static @Nullable LocalDateTime dateTime(@Nullable String value) throws InvalidRow {
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
		} catch (DateTimeException notADate) {
			// e.g. 2026-02-30: reported below, like text that isn't a date
		}
		throw new InvalidRow(CsvProblem.BAD_DATE, value);
	}

	private static LocalDateTime at(int year, int month, int day, @Nullable String hour, @Nullable String minute,
			@Nullable String second) {
		LocalTime time = (hour != null) ? LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute),
				(second != null) ? Integer.parseInt(second) : 0) : LocalTime.MIDNIGHT;
		return LocalDate.of(year, month, day).atTime(time);
	}

	/**
	 * A web address; one without its scheme, e.g. linkedin.com/jobs/view/1, gets https://.
	 */
	private static @Nullable String link(@Nullable String value) throws InvalidRow {
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
	private static String constantName(String value) {
		return value.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
	}

	/**
	 * "Job title", "job-title" and "JOB_TITLE" all become job_title.
	 */
	private static String headerName(String value) {
		return constantName(value).toLowerCase(Locale.ROOT);
	}

	/**
	 * Which value of a row each column is, by the header's names. The first column with a name wins.
	 */
	private static Map<Column, Integer> columnsOf(List<String> header) {
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

	private static @Nullable String valueOf(ApplicationEntity application, Column column) {
		List<TimelineEventEntity> events = application.getEvents();
		TimelineEventEntity latest = events.isEmpty() ? null : events.getLast();
		return switch (column) {
			case TITLE -> application.getTitle();
			case COMPANY -> application.getCompany();
			case LOCATION -> application.getLocation();
			case STATUS -> application.getCurrentStatus().name();
			case STATUS_DATE -> (latest == null) ? null : latest.getEventDate()
					+ ((latest.getEventTime() != null) ? " " + time(latest.getEventTime()) : "");
			case APPLIED_BY -> (application.getAppliedBy() != null) ? application.getAppliedBy().name() : null;
			case APPLIED_ON -> events.stream()
				.filter(event -> event.getStageCategory() == ApplicationStatus.APPLIED)
				.findFirst()
				.map(event -> event.getEventDate().toString())
				.orElse(null);
			case SALARY -> application.getSalary();
			case EMPLOYMENT_TYPE -> application.getEmploymentType();
			case LINK -> application.getLink();
			case SOURCE -> application.getSource();
			case REJECTION_REASON -> (latest != null && latest.getRejectionReason() != null)
					? latest.getRejectionReason().name() : null;
			case CONTACT_NAME -> application.getContactName();
			case CONTACT_ROLE -> application.getContactRole();
			case CONTACT_EMAIL -> application.getContactEmail();
			case CONTACT_PHONE -> application.getContactPhone();
			case CONTACT_LINK -> application.getContactLink();
			case REFERRED_BY -> application.getReferredBy();
			case DEADLINE -> Objects.toString(application.getDeadline(), null);
			case FOLLOW_UP_ON -> Objects.toString(application.getFollowUpOn(), null);
			case NOTES -> application.getNotes();
			case DESCRIPTION -> application.getDescription();
			case ADDED_ON -> LocalDate.ofInstant(application.getCapturedAt(), ZoneId.systemDefault()).toString();
			case LAST_MODIFIED -> DATE_TIME.format(LocalDateTime.ofInstant(
					Objects.requireNonNullElse(application.getUpdatedAt(), application.getCapturedAt()),
					ZoneId.systemDefault()));
		};
	}

	private static String time(LocalTime time) {
		return ((time.getSecond() == 0) ? TIME : TIME_WITH_SECONDS).format(time);
	}

	/**
	 * What the profile already has: the links, and the title and company of the jobs without one.
	 */
	private Known known(long userId) {
		Known known = new Known(new HashSet<>(), new HashSet<>());
		this.applicationService.findAll(userId, null).forEach(known::add);
		return known;
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
	 * A data row, read by column. A value is stripped, and blank is {@code null}.
	 */
	private record Row(List<String> values, Map<Column, Integer> columns) {

		@Nullable String get(Column column) {
			Integer index = this.columns.get(column);
			if (index == null || index >= this.values.size()) {
				return null;
			}
			String value = Csv.unprotect(this.values.get(index).strip());
			return value.isEmpty() ? null : value;
		}

	}

	/**
	 * @param laterStatus the row's status, after Applied, when it was applied to before it
	 */
	private record ImportedJob(NewApplication application, @Nullable NewTimelineEvent laterStatus, FollowUp followUp,
			@Nullable String notes) {
	}

	private record Known(Set<String> links, Set<String> titlesAtCompanies) {

		void add(ApplicationSummary summary) {
			add(summary.link(), summary.title(), summary.company());
		}

		/**
		 * @return whether it's new; it's known from now on
		 */
		boolean add(NewApplication application) {
			return add(application.link(), application.title(), application.company());
		}

		private boolean add(@Nullable String link, String title, @Nullable String company) {
			if (link != null) {
				return this.links.add(link);
			}
			return this.titlesAtCompanies.add(title.strip().toLowerCase(Locale.ROOT) + "\n"
					+ Objects.requireNonNullElse(company, "").strip().toLowerCase(Locale.ROOT));
		}

	}

	/**
	 * Why a row can't be imported. Checked, so that every row's reading is sure to report it.
	 */
	private static final class InvalidRow extends Exception {

		private final CsvProblem problem;

		private final @Nullable String value;

		InvalidRow(CsvProblem problem, @Nullable String value) {
			super(problem + ": " + value, null, false, false);
			this.problem = problem;
			this.value = value;
		}

	}

}
