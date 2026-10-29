package com.jobwarden.jobs;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.CsvImport;
import com.jobwarden.jobs.dto.CsvImportProblem;
import com.jobwarden.jobs.dto.FollowUp;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.enums.AppliedBy;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CsvProblem;
import com.jobwarden.jobs.enums.RejectionReason;
import com.jobwarden.jobs.exception.JobsErrorCode;
import com.jobwarden.jobs.service.ApplicationCsvService;
import com.jobwarden.jobs.service.ApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Like {@link ApplicationServiceTests}, the profiles are inserted with SQL.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class ApplicationCsvServiceTests {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private ApplicationCsvService csv;

	@Autowired
	private ApplicationService applications;

	@Autowired
	private JdbcClient jdbc;

	private long userId;

	private long otherUserId;

	@BeforeEach
	void createProfiles() {
		this.jdbc.sql("TRUNCATE users CASCADE").update();
		this.userId = insertProfile("Robert");
		this.otherUserId = insertProfile("Someone else");
	}

	@Test
	void exportsEveryJobWithItsTimelineDatesAndFollowUp() throws IOException {
		long id = this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title("Backend engineer")
			.company("Acme, Inc.")
			.link("https://jobs.lever.co/acme/1")
			.description("We build \"things\".\nRemote.")
			.initialStatus(ApplicationStatus.APPLIED)
			.initialStatusDate(LocalDate.of(2026, 10, 1))
			.build());
		this.applications.addEvent(this.userId, id, NewTimelineEvent.builder()
			.eventDate(LocalDate.of(2026, 10, 20))
			.eventTime(LocalTime.of(14, 30))
			.stageCategory(ApplicationStatus.REJECTED)
			.rejectionReason(RejectionReason.POSITION_FILLED)
			.build());
		this.applications.updateFollowUp(this.userId, id, FollowUp.builder().contactName("Ana").build());
		this.applications.updateNotes(this.userId, id, "=1+1");
		create(this.otherUserId, "Not theirs");

		String exported = export(this.userId);

		assertThat(exported).startsWith("﻿title,company,location,status,status_date,applied_on,");
		assertThat(exported.lines()).hasSize(3);
		assertThat(exported).contains("Backend engineer,\"Acme, Inc.\",,REJECTED,2026-10-20 14:30,2026-10-01,")
			.contains("https://jobs.lever.co/acme/1,jobs.lever.co,POSITION_FILLED,Ana,")
			// A formula a posting could slip into a spreadsheet only shows as text
			.contains(",'=1+1,")
			.contains("\"We build \"\"things\"\".\nRemote.\"")
			.doesNotContain("Not theirs");
	}

	@Test
	void importsWhatItExportedIntoAnotherProfile() throws IOException {
		long id = this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title("Backend engineer")
			.company("Acme")
			.location("Cluj-Napoca, Romania")
			.link("https://jobs.lever.co/acme/1")
			.initialStatus(ApplicationStatus.APPLIED)
			.initialStatusDate(LocalDate.of(2026, 10, 1))
			.appliedBy(AppliedBy.CLAUDE)
			.build());
		// A time to the second, as the extension records it
		this.applications.addEvent(this.userId, id, NewTimelineEvent.builder()
			.eventDate(LocalDate.of(2026, 10, 8))
			.eventTime(LocalTime.of(20, 31, 5))
			.stageCategory(ApplicationStatus.INTERVIEWING)
			.build());
		this.applications.updateFollowUp(this.userId, id,
				FollowUp.builder().contactEmail("ana@acme.example").followUpOn(LocalDate.of(2026, 11, 2)).build());
		this.applications.updateNotes(this.userId, id, "- Ask about the team\n=SUM(A1)");

		CsvImport imported = this.csv.importCsv(this.otherUserId, new StringReader(export(this.userId)));

		assertThat(imported).isEqualTo(new CsvImport(1, 0, List.of()));
		ApplicationSummary summary = this.applications.findAll(this.otherUserId, null).getFirst();
		ApplicationDetails copy = this.applications.findById(this.otherUserId, summary.id()).orElseThrow();
		assertThat(copy.title()).isEqualTo("Backend engineer");
		assertThat(copy.location()).isEqualTo("Cluj-Napoca, Romania");
		assertThat(copy.source()).isEqualTo("jobs.lever.co");
		assertThat(copy.status()).isEqualTo(ApplicationStatus.INTERVIEWING);
		assertThat(copy.timeline()).extracting("eventDate", "eventTime", "stageCategory")
			.containsExactly(tuple(LocalDate.of(2026, 10, 1), null, ApplicationStatus.APPLIED),
					tuple(LocalDate.of(2026, 10, 8), LocalTime.of(20, 31, 5), ApplicationStatus.INTERVIEWING));
		assertThat(copy.followUp().contactEmail()).isEqualTo("ana@acme.example");
		assertThat(copy.followUp().followUpOn()).isEqualTo(LocalDate.of(2026, 11, 2));
		assertThat(copy.notes()).isEqualTo("- Ask about the team\n=SUM(A1)");
		assertThat(copy.appliedBy()).isEqualTo(AppliedBy.CLAUDE);
	}

	@Test
	void readsASpreadsheetsColumnsSemicolonsAndUsualStatusNames() throws IOException {
		String sheet = """
				Job Title;Company Name;URL;Status;Date applied;Notes
				Data engineer;Globex;linkedin.com/jobs/view/42;Wishlist;;Looks good
				QA engineer;Initech;;Interview;01.10.2026;
				""";

		CsvImport imported = this.csv.importCsv(this.userId, new StringReader(sheet));

		assertThat(imported.imported()).isEqualTo(2);
		assertThat(this.applications.findAll(this.userId, null))
			.extracting(ApplicationSummary::title, ApplicationSummary::link, ApplicationSummary::status)
			.containsExactlyInAnyOrder(tuple("Data engineer", "https://linkedin.com/jobs/view/42", ApplicationStatus.SAVED),
					tuple("QA engineer", null, ApplicationStatus.INTERVIEWING));
		long qa = this.applications.findAll(this.userId, ApplicationStatus.INTERVIEWING).getFirst().id();
		assertThat(this.applications.findById(this.userId, qa).orElseThrow().timeline())
			.extracting("eventDate", "stageCategory")
			.containsExactly(tuple(LocalDate.of(2026, 10, 1), ApplicationStatus.APPLIED),
					tuple(TODAY, ApplicationStatus.INTERVIEWING));
	}

	@Test
	void skipsTheJobsTheProfileHasAndReportsTheRowsItCantRead() throws IOException {
		create(this.userId, "Backend engineer");
		String file = """
				title,company,link,status,status_date
				Backend engineer,Acme,,,
				Frontend engineer,Acme,https://acme.example/2,Pending,
				,Acme,https://acme.example/3,,
				Designer,Acme,https://acme.example/4,,2026-02-30
				Designer,Acme,mailto:jobs@acme.example,,
				Writer,Acme,https://acme.example/5,,
				Writer again,Acme,https://acme.example/5,,
				""";

		CsvImport imported = this.csv.importCsv(this.userId, new StringReader(file));

		assertThat(imported.imported()).isEqualTo(1);
		assertThat(imported.skipped()).isEqualTo(2);
		assertThat(imported.problems()).containsExactly(
				new CsvImportProblem(3, CsvProblem.UNKNOWN_STATUS, "Pending"),
				new CsvImportProblem(4, CsvProblem.NO_TITLE, null),
				new CsvImportProblem(5, CsvProblem.BAD_DATE, "2026-02-30"),
				new CsvImportProblem(6, CsvProblem.BAD_LINK, "mailto:jobs@acme.example"));
		assertThat(this.applications.count(this.userId)).isEqualTo(2);
	}

	@Test
	void refusesAFileWithoutATitleColumn() {
		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.csv.importCsv(this.userId, new StringReader("name,company\nBackend engineer,Acme\n")))
			.satisfies(refused -> assertThat(refused.getErrorCode()).isEqualTo(JobsErrorCode.CSV_WITHOUT_TITLE_COLUMN));
	}

	private String export(long userId) throws IOException {
		StringWriter out = new StringWriter();
		this.csv.export(userId, out);
		return out.toString();
	}

	private void create(long userId, String title) {
		this.applications.create(NewApplication.builder()
			.userId(userId)
			.title(title)
			.company("Acme")
			.initialStatus(ApplicationStatus.SAVED)
			.initialStatusDate(TODAY)
			.build());
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id")
			.param(name)
			.query(Long.class)
			.single();
	}

}
