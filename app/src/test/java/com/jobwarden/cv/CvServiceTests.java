package com.jobwarden.cv;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvCertification;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvEducation;
import com.jobwarden.cv.dto.CvExperience;
import com.jobwarden.cv.dto.CvFile;
import com.jobwarden.cv.dto.CvLanguage;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.exception.CvErrorCode;
import com.jobwarden.cv.exception.CvFileNotFoundException;
import com.jobwarden.cv.service.CvService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cv module starts here without identity, so the profiles that own the CVs are inserted with SQL.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class CvServiceTests {

	@Autowired
	private CvService cvs;

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
	void hasNothingBeforeItsFilledIn() {
		Cv cv = this.cvs.find(this.userId);

		assertThat(cv.file()).isNull();
		assertThat(cv.details()).isEqualTo(CvDetails.NONE);
	}

	@Test
	void keepsTheFieldsTidiedWithoutTheEmptyRows() {
		this.cvs.saveDetails(this.userId, CvDetails.builder()
			.fullName(" Robert Ciapă ")
			.headline("Senior Java Developer")
			.email("")
			.skills(List.of("Java", " Kafka ", "", "Java"))
			.experience(List.of(new CvExperience("Java Developer", "Acme", null, "2021", "Present", "Payments\nKafka"),
					new CvExperience(" ", "Nobody", null, null, null, null)))
			.education(List.of(new CvEducation("BSc Computer Science", "University of Bucharest", null, "2014", "2018", null)))
			.languages(List.of(new CvLanguage("Romanian", "Native"), new CvLanguage("", "C1")))
			.certifications(List.of(new CvCertification("OCP Java 17", "Oracle", "2023")))
			.build());

		CvDetails saved = this.cvs.find(this.userId).details();
		assertThat(saved.fullName()).isEqualTo("Robert Ciapă");
		assertThat(saved.email()).isNull();
		assertThat(saved.skills()).containsExactly("Java", "Kafka");
		assertThat(saved.experience()).containsExactly(new CvExperience("Java Developer", "Acme", null, "2021", "Present",
				"Payments\nKafka"));
		assertThat(saved.education()).extracting(CvEducation::school).containsExactly("University of Bucharest");
		assertThat(saved.languages()).containsExactly(new CvLanguage("Romanian", "Native"));
		assertThat(saved.certifications()).extracting(CvCertification::issuer).containsExactly("Oracle");
		assertThat(saved.updatedAt()).isNotNull();
		assertThat(this.cvs.find(this.otherUserId).details()).isEqualTo(CvDetails.NONE);

		// Saving again replaces the lists
		this.cvs.saveDetails(this.userId, CvDetails.builder().fullName("Robert").build());
		assertThat(this.cvs.find(this.userId).details().experience()).isEmpty();
	}

	@Test
	void keepsTheFileWithItsTextAndReplacesIt() {
		CvFile uploaded = this.cvs.uploadFile(this.userId, "C:\\Users\\robert\\cv.txt", "text/plain",
				"Senior Java Developer\nKafka".getBytes(StandardCharsets.UTF_8));

		assertThat(uploaded.fileName()).isEqualTo("cv.txt");
		assertThat(uploaded.textLength()).isEqualTo(27);
		assertThat(this.cvs.find(this.userId).file()).satisfies(file -> {
			assertThat(file.fileName()).isEqualTo("cv.txt");
			assertThat(file.contentType()).isEqualTo("text/plain");
			assertThat(file.size()).isEqualTo(27);
			assertThat(file.textLength()).isEqualTo(27);
		});
		assertThat(this.cvs.fileText(this.userId)).contains("Senior Java Developer\nKafka");
		assertThat(this.cvs.downloadFile(this.userId).content()).asString(StandardCharsets.UTF_8).startsWith("Senior");

		this.cvs.uploadFile(this.userId, "cv.md", null, "# Robert".getBytes(StandardCharsets.UTF_8));
		assertThat(this.cvs.fileText(this.userId)).contains("# Robert");
		assertThat(this.cvs.fileText(this.otherUserId)).isEmpty();

		this.cvs.deleteFile(this.userId);
		assertThat(this.cvs.find(this.userId).file()).isNull();
		assertThatExceptionOfType(CvFileNotFoundException.class).isThrownBy(() -> this.cvs.downloadFile(this.userId));
	}

	@Test
	void takesOnlyCvFiles() {
		assertThatThrownBy(() -> this.cvs.uploadFile(this.userId, "photo.png", "image/png", new byte[] { 1 }))
			.isInstanceOfSatisfying(BusinessRuleException.class, exception -> {
				assertThat(exception.getErrorCode()).isEqualTo(CvErrorCode.FILE_TYPE_NOT_SUPPORTED);
				assertThat(exception.getArguments()).containsExactly(".png");
			});
		assertThatThrownBy(() -> this.cvs.uploadFile(this.userId, "cv.pdf", "application/pdf",
				"not a pdf".getBytes(StandardCharsets.UTF_8)))
			.isInstanceOfSatisfying(BusinessRuleException.class,
					exception -> assertThat(exception.getErrorCode()).isEqualTo(CvErrorCode.FILE_NOT_WHAT_IT_SAYS));
		assertThatThrownBy(() -> this.cvs.uploadFile(this.userId, "cv.pdf", "application/pdf", new byte[0]))
			.isInstanceOfSatisfying(BusinessRuleException.class,
					exception -> assertThat(exception.getErrorCode()).isEqualTo(CvErrorCode.FILE_EMPTY));
		byte[] tooLarge = new byte[CvService.MAX_FILE_MEGABYTES * 1024 * 1024 + 1];
		assertThatThrownBy(() -> this.cvs.uploadFile(this.userId, "cv.txt", "text/plain", tooLarge))
			.isInstanceOfSatisfying(BusinessRuleException.class,
					exception -> assertThat(exception.getErrorCode()).isEqualTo(CvErrorCode.FILE_TOO_LARGE));
		assertThat(this.cvs.find(this.userId).file()).isNull();
	}

	@Test
	void keepsACvTailoredToEachJobTidiedAndReplacesItWhenTailoredAgain() {
		long job = insertJob(this.userId, "Backend engineer");
		long otherJob = insertJob(this.userId, "Data engineer");
		CvDetails tailored = CvDetails.builder()
			.fullName("Robert Ciapă")
			.headline(" Backend Engineer ")
			.skills(List.of("Spring Boot", "Kafka", " Kafka", ""))
			.experience(List.of(new CvExperience("Java Developer", "Acme", null, "2021", "Present", "Built payments\nRan Kafka")))
			.build();

		this.cvs.saveTailored(this.userId, job, tailored, List.of("Spring Boot", " Kafka ", "Kafka"), List.of("Kubernetes"),
				"Put Kafka first.");
		this.cvs.saveTailored(this.userId, otherJob, CvDetails.builder().headline("Data Engineer").build(), List.of(),
				List.of(), " ");

		TailoredCv saved = this.cvs.findTailored(this.userId, job).orElseThrow();
		assertThat(saved.details().headline()).isEqualTo("Backend Engineer");
		assertThat(saved.details().skills()).containsExactly("Spring Boot", "Kafka");
		assertThat(saved.details().experience()).singleElement()
			.extracting(CvExperience::description)
			.isEqualTo("Built payments\nRan Kafka");
		assertThat(saved.matchedKeywords()).containsExactly("Spring Boot", "Kafka");
		assertThat(saved.missingKeywords()).containsExactly("Kubernetes");
		assertThat(saved.notes()).isEqualTo("Put Kafka first.");
		assertThat(this.cvs.findAllTailored(this.userId)).extracting(TailoredCv::applicationId).containsExactly(otherJob, job);
		assertThat(this.cvs.findTailored(this.userId, otherJob).orElseThrow().notes()).isNull();

		this.cvs.saveTailored(this.userId, job, CvDetails.builder().headline("Senior Backend Engineer").build(), List.of(),
				List.of(), null);
		assertThat(this.cvs.findTailored(this.userId, job).orElseThrow().details().headline())
			.isEqualTo("Senior Backend Engineer");
		assertThat(this.cvs.findAllTailored(this.userId)).hasSize(2);
	}

	@Test
	void keepsATailoredCvToItsProfileAndDeletesIt() {
		long job = insertJob(this.userId, "Backend engineer");
		this.cvs.saveTailored(this.userId, job, CvDetails.builder().headline("Backend Engineer").build(), List.of(),
				List.of(), null);

		assertThat(this.cvs.findTailored(this.otherUserId, job)).isEmpty();
		this.cvs.deleteTailored(this.otherUserId, job);
		assertThat(this.cvs.findTailored(this.userId, job)).isPresent();

		this.cvs.deleteTailored(this.userId, job);
		assertThat(this.cvs.findTailored(this.userId, job)).isEmpty();
	}

	@Test
	void forgetsTheTailoredCvOfADeletedJob() {
		long job = insertJob(this.userId, "Backend engineer");
		this.cvs.saveTailored(this.userId, job, CvDetails.builder().headline("Backend Engineer").build(), List.of(),
				List.of(), null);

		this.jdbc.sql("DELETE FROM applications WHERE id = ?").param(job).update();

		assertThat(this.cvs.findAllTailored(this.userId)).isEmpty();
	}

	/**
	 * The jobs module isn't started here, so the jobs a CV is tailored to are inserted with SQL too.
	 */
	private long insertJob(long userId, String title) {
		return this.jdbc.sql("""
				INSERT INTO applications (user_id, title, current_status, captured_at) VALUES (?, ?, 'SAVED', now())
				RETURNING id""").param(userId).param(title).query(Long.class).single();
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id").param(name).query(Long.class).single();
	}

}
