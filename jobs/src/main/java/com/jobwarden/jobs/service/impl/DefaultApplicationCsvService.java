package com.jobwarden.jobs.service.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.CsvImport;
import com.jobwarden.jobs.dto.CsvImportProblem;
import com.jobwarden.jobs.dto.FollowUp;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.entity.ApplicationEntity;
import com.jobwarden.jobs.entity.TimelineEventEntity;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CsvProblem;
import com.jobwarden.jobs.enums.RejectionReason;
import com.jobwarden.jobs.exception.JobsErrorCode;
import com.jobwarden.jobs.repository.ApplicationRepository;
import com.jobwarden.jobs.service.ApplicationCsvService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.impl.JobsCsvFormat.Column;
import com.jobwarden.jobs.service.impl.JobsCsvFormat.InvalidRow;
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

	private final ApplicationRepository applications;

	private final ApplicationService applicationService;

	private final Clock clock;

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
		Map<Column, Integer> columns = records.isEmpty() ? Map.of() : JobsCsvFormat.columnsOf(records.getFirst());
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
		NewTimelineEvent laterStatus = job.laterStatus();
		if (laterStatus != null) {
			this.applicationService.addEvent(userId, id, laterStatus);
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
	private ImportedJob read(long userId, Row row) throws InvalidRow {
		String title = row.get(Column.TITLE);
		if (title == null) {
			throw new InvalidRow(CsvProblem.NO_TITLE, null);
		}
		LocalDate appliedOn = JobsCsvFormat.date(row.get(Column.APPLIED_ON));
		ApplicationStatus status = JobsCsvFormat.status(row.get(Column.STATUS), appliedOn);
		LocalDateTime statusAt = JobsCsvFormat.dateTime(row.get(Column.STATUS_DATE));
		LocalDate statusDate = statusDate(statusAt, status, appliedOn);
		LocalTime statusTime = (statusAt != null && !statusAt.toLocalTime().equals(LocalTime.MIDNIGHT))
				? statusAt.toLocalTime() : null;
		RejectionReason rejectionReason = status.hasRejectionReason() ? JobsCsvFormat.rejectionReason(row.get(Column.REJECTION_REASON))
				: null;

		// The day it was applied to, when that came before a later status: the job starts as applied, then moves on
		LocalDate appliedFirst = (appliedOn != null && status != ApplicationStatus.SAVED
				&& status != ApplicationStatus.APPLIED && !appliedOn.isAfter(statusDate)) ? appliedOn : null;
		boolean appliedBefore = appliedFirst != null;
		NewApplication application = NewApplication.builder()
			.userId(userId)
			.title(title)
			.company(row.get(Column.COMPANY))
			.location(row.get(Column.LOCATION))
			.link(JobsCsvFormat.link(row.get(Column.LINK)))
			.salary(row.get(Column.SALARY))
			.employmentType(row.get(Column.EMPLOYMENT_TYPE))
			.description(row.get(Column.DESCRIPTION))
			.appliedBy(JobsCsvFormat.appliedBy(row.get(Column.APPLIED_BY)))
			.initialStatus(appliedBefore ? ApplicationStatus.APPLIED : status)
			.initialStatusDate((appliedFirst != null) ? appliedFirst : statusDate)
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
			.contactLink(JobsCsvFormat.link(row.get(Column.CONTACT_LINK)))
			.referredBy(row.get(Column.REFERRED_BY))
			.deadline(JobsCsvFormat.date(row.get(Column.DEADLINE)))
			.followUpOn(JobsCsvFormat.date(row.get(Column.FOLLOW_UP_ON)))
			.build();
		return new ImportedJob(application, laterStatus, followUp, row.get(Column.NOTES));
	}

	/**
	 * The day of the row's status: its date when it has one; an application's, the day it was applied to; else today.
	 */
	private LocalDate statusDate(@Nullable LocalDateTime statusAt, ApplicationStatus status,
			@Nullable LocalDate appliedOn) {
		if (statusAt != null) {
			return statusAt.toLocalDate();
		}
		if (status == ApplicationStatus.APPLIED && appliedOn != null) {
			return appliedOn;
		}
		return LocalDate.now(this.clock);
	}

	private @Nullable String valueOf(ApplicationEntity application, Column column) {
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
			case ADDED_ON -> LocalDate.ofInstant(application.getCapturedAt(), this.clock.getZone()).toString();
			case LAST_MODIFIED -> DATE_TIME.format(LocalDateTime.ofInstant(
					Objects.requireNonNullElse(application.getUpdatedAt(), application.getCapturedAt()),
					this.clock.getZone()));
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

}
