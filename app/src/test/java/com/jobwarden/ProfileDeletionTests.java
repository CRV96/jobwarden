package com.jobwarden;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jobwarden.cv.dto.Story;
import com.jobwarden.cv.service.StoryService;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.exception.UserNotFoundException;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.JobOffer;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.OfferExtra;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.OfferExtraKind;
import com.jobwarden.jobs.enums.PayFrequency;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.OfferService;
import com.jobwarden.search.SearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Deleting a profile, across the modules: everything of it goes, in every table, and nothing of another profile does.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProfileDeletionTests {

	/** Each table, how many rows a profile has in it: by its user_id, or through its jobs' application_id. */
	private static final String TABLES_OF_A_PROFILE = """
			SELECT table_name, column_name FROM information_schema.columns
			WHERE table_schema = 'public' AND column_name IN ('user_id', 'application_id')
			ORDER BY table_name, column_name
			""";

	@Autowired
	private UserService users;

	@Autowired
	private ApplicationService applications;

	@Autowired
	private OfferService offers;

	@Autowired
	private DiscoveryService discovery;

	@Autowired
	private StoryService stories;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void noProfiles() {
		this.jdbc.sql("TRUNCATE users CASCADE").update();
	}

	@Test
	void deletesEverythingOfTheProfileAndNothingOfAnother() {
		User robert = this.users.create("Robert");
		User ana = this.users.create("Ana");
		fill(robert);
		fill(ana);
		Map<String, Long> anasRows = rows(ana.id());
		assertThat(rows(robert.id())).as("Robert's rows before").containsValue(1L);

		this.users.delete(robert.id());

		assertThat(this.users.findById(robert.id())).isEmpty();
		assertThat(rows(robert.id())).as("Robert's rows after").allSatisfy((table, count) -> assertThat(count).as(table).isZero());
		assertThat(this.jdbc.sql("SELECT count(*) FROM job_offer_extras").query(Long.class).single())
			.as("Ana's offer extras alone")
			.isEqualTo(1);
		assertThat(rows(ana.id())).isEqualTo(anasRows);
		assertThatExceptionOfType(UserNotFoundException.class).isThrownBy(() -> this.users.delete(robert.id()));
	}

	/** A job at Offer with its terms, a saved search, a request for the assistant, a story. */
	private void fill(User user) {
		long job = this.applications.create(NewApplication.builder()
			.userId(user.id())
			.title("Java Developer")
			.company("Acme")
			.link("https://example.com/jobs/" + user.id())
			.initialStatus(ApplicationStatus.OFFER)
			.initialStatusDate(LocalDate.now())
			.build());
		this.offers.save(user.id(), job, JobOffer.builder()
			.baseSalaryAmount(new BigDecimal("80000"))
			.baseSalaryCurrency("EUR")
			.extras(List.of(new OfferExtra(OfferExtraKind.MEAL_VOUCHERS, BigDecimal.TEN, "EUR", PayFrequency.WORKING_DAY, null)))
			.build());
		this.discovery.saveSearch(user.id(), "Java", SearchCriteria.builder().titles(List.of("Java Developer")).build());
		this.discovery.createRequest(user.id(), AiRequestKind.FIND_JOBS, AiRequirements.NONE);
		this.discovery.createRequest(user.id(), AiRequestKind.SCORE_JOB, job);
		this.stories.save(user.id(), Story.builder().title("Moved the payments to Kafka").skills(List.of("Kafka")).build());
	}

	/** Every table with a profile's rows, and how many of its rows it has. */
	private Map<String, Long> rows(long userId) {
		Map<String, Long> rows = new LinkedHashMap<>();
		List<Map<String, Object>> columns = this.jdbc.sql(TABLES_OF_A_PROFILE).query().listOfRows();
		for (Map<String, Object> column : columns) {
			String table = (String) column.get("table_name");
			String sql = "user_id".equals(column.get("column_name"))
					? "SELECT count(*) FROM " + table + " WHERE user_id = :userId"
					: "SELECT count(*) FROM " + table + " WHERE application_id IN (SELECT id FROM applications WHERE user_id = :userId)";
			rows.merge(table, this.jdbc.sql(sql).param("userId", userId).query(Long.class).single(), Math::max);
		}
		return rows;
	}

}
