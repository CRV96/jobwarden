package com.jobwarden.jobs;

import java.time.LocalDate;
import java.util.List;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.jobs.dto.CompanyResearch;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.CompanyResearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The jobs module starts here without identity, so the profiles that own the jobs are inserted with SQL.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class CompanyResearchServiceTests {

	@Autowired
	private CompanyResearchService research;

	@Autowired
	private ApplicationService applications;

	@Autowired
	private JdbcClient jdbc;

	private long userId;

	private long otherUserId;

	private long job;

	@BeforeEach
	void createAJob() {
		this.jdbc.sql("TRUNCATE users CASCADE").update();
		this.userId = insertProfile("Robert");
		this.otherUserId = insertProfile("Someone else");
		this.job = this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title("Senior Java Engineer")
			.company("Tires Easy")
			.initialStatus(ApplicationStatus.SAVED)
			.initialStatusDate(LocalDate.now())
			.build());
	}

	@Test
	void keepsTheLatestResearchOfAJob() {
		this.research.save(this.userId, this.job, CompanyResearch.builder()
			.company("Tires Easy sells tyres online.")
			.ratings(List.of("Glassdoor 3.9/5 (24 reviews)"))
			.pros(List.of("Friendly team"))
			.interviews("Two rounds.")
			.watchOut("Few reviews.")
			.sources(List.of("https://www.glassdoor.com/Reviews/Tires-Easy"))
			.build());

		assertThat(this.research.find(this.userId, this.job)).hasValueSatisfying(found -> {
			assertThat(found.company()).isEqualTo("Tires Easy sells tyres online.");
			assertThat(found.ratings()).containsExactly("Glassdoor 3.9/5 (24 reviews)");
			assertThat(found.pros()).containsExactly("Friendly team");
			assertThat(found.interviews()).isEqualTo("Two rounds.");
			assertThat(found.watchOut()).isEqualTo("Few reviews.");
			assertThat(found.updatedAt()).isNotNull();
		});

		// Done again: the job keeps one, the latest, with nothing left of the first
		this.research.save(this.userId, this.job, CompanyResearch.builder()
			.company("Tires Easy, an online tyre shop in the US.")
			.salaries(List.of("Senior Java Developer: 40–45 EUR an hour"))
			.build());

		assertThat(this.research.find(this.userId, this.job)).hasValueSatisfying(found -> {
			assertThat(found.company()).isEqualTo("Tires Easy, an online tyre shop in the US.");
			assertThat(found.salaries()).containsExactly("Senior Java Developer: 40–45 EUR an hour");
			assertThat(found.ratings()).isEmpty();
			assertThat(found.interviews()).isNull();
			assertThat(found.watchOut()).isNull();
		});
		assertThat(this.jdbc.sql("SELECT count(*) FROM company_research").query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void keepsResearchForTheProfilesOwnJobsAlone() {
		CompanyResearch found = CompanyResearch.builder().company("Tires Easy.").build();

		assertThatExceptionOfType(ApplicationNotFoundException.class)
			.isThrownBy(() -> this.research.save(this.otherUserId, this.job, found));
		assertThat(this.research.find(this.otherUserId, this.job)).isEmpty();

		this.research.save(this.userId, this.job, found);
		this.research.delete(this.otherUserId, this.job);
		assertThat(this.research.find(this.userId, this.job)).isPresent();
		this.research.delete(this.userId, this.job);
		assertThat(this.research.find(this.userId, this.job)).isEmpty();
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id").param(name).query(Long.class).single();
	}

}
