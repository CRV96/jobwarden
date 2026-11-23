package com.jobwarden;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A database the 49 migrations from before the squash built: its tables are those V1 to V9 build, and its history
 * names the 49. Each test builds the schema with V1 to V9, then gives it the history it's about; the migrations after
 * the 9, V10 on, come after.
 */
class SquashedMigrationsTests {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	private static SimpleDriverDataSource dataSource;

	private JdbcClient jdbc;

	@BeforeAll
	static void startPostgres() {
		POSTGRES.start();
		dataSource = new SimpleDriverDataSource(new org.postgresql.Driver(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
				POSTGRES.getPassword());
	}

	@AfterAll
	static void stopPostgres() {
		POSTGRES.stop();
	}

	@BeforeEach
	void aSchemaBuiltByTheNine() {
		this.jdbc = JdbcClient.create(dataSource);
		this.jdbc.sql("DROP SCHEMA public CASCADE").update();
		this.jdbc.sql("CREATE SCHEMA public").update();
		theNine().migrate();
		this.jdbc.sql("INSERT INTO users (name) VALUES ('Robert')").update();
	}

	@Test
	void replacesTheFortyNinesHistoryWithABaselineAndTouchesNothingElse() {
		historyOfTheOld(49);

		assertThat(SquashedMigrations.convert(flyway())).isTrue();

		assertThat(history()).containsExactly("9 BASELINE Migrations 1 to 49, squashed into 1 to 9");
		// Flyway runs the migrations after the 9 only, and finds nothing wrong; the profile is still there
		assertThat(flyway().migrate().migrations)
			.allSatisfy(migration -> assertThat(Integer.parseInt(migration.version)).isGreaterThan(9));
		assertThat(flyway().validateWithResult().validationSuccessful).isTrue();
		assertThat(this.jdbc.sql("SELECT name FROM users").query(String.class).list()).containsExactly("Robert");
		// And the next start leaves it alone, with those after the 9 in its history
		assertThat(SquashedMigrations.convert(flyway())).isFalse();
		assertThat(history().getFirst()).isEqualTo("9 BASELINE Migrations 1 to 49, squashed into 1 to 9");
	}

	@Test
	void leavesTheNinesHistoryAsItIs() {
		List<String> nine = history();

		assertThat(SquashedMigrations.convert(flyway())).isFalse();

		assertThat(history()).hasSize(9).isEqualTo(nine);
	}

	@Test
	void leavesTheMigrationsAfterTheNineToFlyway() {
		flyway().migrate();
		List<String> all = history();

		assertThat(SquashedMigrations.convert(flyway())).isFalse();

		assertThat(all).hasSizeGreaterThan(9);
		assertThat(history()).isEqualTo(all);
	}

	@Test
	void leavesANewDatabaseToFlyway() {
		this.jdbc.sql("DROP SCHEMA public CASCADE").update();
		this.jdbc.sql("CREATE SCHEMA public").update();

		assertThat(SquashedMigrations.convert(flyway())).isFalse();
	}

	@Test
	void leavesAHistoryUnderAnotherNameToFlyway() {
		historyOfTheOld(49);
		Flyway elsewhere = Flyway.configure().dataSource(dataSource).table("other_history").load();

		assertThat(SquashedMigrations.convert(elsewhere)).isFalse();

		assertThat(history()).hasSize(49);
	}

	@Test
	void refusesADatabaseTheOldOnesBuiltOnlyInPart() {
		historyOfTheOld(30);
		Flyway flyway = flyway();

		assertThatThrownBy(() -> SquashedMigrations.convert(flyway)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("not up to V49");
		assertThat(history()).hasSize(30);
	}

	/** All of JobWarden's migrations */
	private static Flyway flyway() {
		return migrations().load();
	}

	/** JobWarden's migrations up to the last of the 9 */
	private static Flyway theNine() {
		return migrations().target(SquashedMigrations.SQUASHED_VERSION).load();
	}

	private static FluentConfiguration migrations() {
		return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
	}

	/** The history the old migrations wrote, up to the version given; the last one's description as it was */
	private void historyOfTheOld(int last) {
		this.jdbc.sql("DELETE FROM flyway_schema_history").update();
		for (int version = 1; version <= last; version++) {
			this.jdbc.sql("""
					INSERT INTO flyway_schema_history
					    (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
					VALUES (?, ?, ?, 'SQL', ?, ?, 'jobwarden', 1, TRUE)""")
				.params(version, String.valueOf(version),
						(version == 49) ? SquashedMigrations.LAST_OLD_DESCRIPTION : "old migration " + version,
						"V" + version + "__old.sql", version * 7919)
				.update();
		}
	}

	private List<String> history() {
		return this.jdbc.sql("SELECT version || ' ' || type || ' ' || description FROM flyway_schema_history "
				+ "ORDER BY installed_rank").query(String.class).list();
	}

}
