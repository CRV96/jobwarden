package com.jobwarden.database.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.database.dto.DatabaseLogin;
import com.jobwarden.database.exception.DatabaseErrorCode;
import com.jobwarden.database.service.DatabasePasswordService;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Against PostgreSQL itself: the role the tests log in as gets a new password, and gets its own back after each test,
 * so the database stays as the other tests expect it.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class DatabasePasswordTests {

	private static final String NEW_PASSWORD = "a long new password";

	private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

	/** jobwarden.database.password-file, as config/application.properties sets it for the tests */
	private static final Path PASSWORD_FILE = Path.of("target/test-database.password");

	@Autowired
	private DatabasePasswordService database;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private PostgreSQLContainer postgres;

	@BeforeEach
	@AfterEach
	void givesTheRoleItsOwnPasswordBack() throws IOException, SQLException {
		this.jdbc.sql("ALTER ROLE " + this.postgres.getUsername() + " PASSWORD '" + this.postgres.getPassword() + "'").update();
		this.dataSource.unwrap(HikariDataSource.class).setPassword(this.postgres.getPassword());
		Files.deleteIfExists(PASSWORD_FILE);
	}

	@Test
	void saysHowItConnects() {
		DatabaseLogin login = this.database.login().orElseThrow();

		assertThat(login.user()).isEqualTo(this.postgres.getUsername());
		assertThat(login.address()).endsWith("/" + this.postgres.getDatabaseName()).doesNotContain("jdbc:", "?");
		assertThat(login.changedHere()).isFalse();
		assertThat(login.defaultPassword()).isFalse();
		assertThat(login.keptIn()).isEqualTo(PASSWORD_FILE);
	}

	@Test
	void changesThePasswordOfTheDatabaseThePoolAndTheNextStart() throws SQLException, IOException {
		this.database.changePassword(this.postgres.getPassword(), NEW_PASSWORD);

		// The database takes the new one only, and keeps its hash, never the password
		assertThat(logsIn(NEW_PASSWORD)).isTrue();
		assertThat(logsIn(this.postgres.getPassword())).isFalse();
		assertThat(this.jdbc.sql("SELECT rolpassword FROM pg_authid WHERE rolname = session_user").query(String.class).single())
			.startsWith("SCRAM-SHA-256$")
			.doesNotContain(NEW_PASSWORD);
		// The pool's connections were replaced by new ones, which log in with it
		assertThat(this.jdbc.sql("SELECT 1").query(Integer.class).single()).isEqualTo(1);
		// And the next start finds it
		assertThat(Files.readString(PASSWORD_FILE)).doesNotContain(NEW_PASSWORD);
		assertThat(this.database.login().orElseThrow().changedHere()).isTrue();
		assertRefused(() -> this.database.changePassword(NEW_PASSWORD, NEW_PASSWORD), DatabaseErrorCode.PASSWORD_UNCHANGED);
	}

	@Test
	void changesNothingWithoutTheCurrentPasswordOrAGoodNewOne() throws SQLException {
		assertRefused(() -> this.database.changePassword("not the password", NEW_PASSWORD), DatabaseErrorCode.PASSWORD_WRONG);
		assertRefused(() -> this.database.changePassword(this.postgres.getPassword(), "eleven char"),
				DatabaseErrorCode.PASSWORD_TOO_SHORT);
		assertRefused(() -> this.database.changePassword(this.postgres.getPassword(), " ".repeat(20)),
				DatabaseErrorCode.PASSWORD_TOO_SHORT);
		assertRefused(() -> this.database.changePassword(this.postgres.getPassword(), "x".repeat(129)),
				DatabaseErrorCode.PASSWORD_TOO_LONG);

		assertThat(logsIn(this.postgres.getPassword())).isTrue();
		assertThat(PASSWORD_FILE).doesNotExist();
	}

	@Test
	void startsWithThePasswordChangedInSettingsWhenTheDatabaseTakesIt(@TempDir Path folder) throws IOException {
		SavedPassword saved = new SavedPassword(folder.resolve("database.password"), KEY, folder.resolve("inbox.key"));
		saved.save(this.postgres.getPassword());

		try (HikariDataSource pool = pool("the configured one, from before")) {
			new SavedPasswordApplier(saved).postProcessAfterInitialization(pool, "dataSource");

			assertThat(pool.getPassword()).isEqualTo(this.postgres.getPassword());
		}
	}

	@Test
	void startsWithTheConfiguredPasswordWhenTheDatabaseRefusesTheSavedOne(@TempDir Path folder) throws IOException {
		SavedPassword saved = new SavedPassword(folder.resolve("database.password"), KEY, folder.resolve("inbox.key"));
		saved.save("a password from before the backup was restored");

		try (HikariDataSource pool = pool(this.postgres.getPassword())) {
			new SavedPasswordApplier(saved).postProcessAfterInitialization(pool, "dataSource");

			assertThat(pool.getPassword()).isEqualTo(this.postgres.getPassword());
		}
	}

	/** A pool configured as Spring Boot makes it, not started yet */
	private HikariDataSource pool(String password) {
		HikariDataSource pool = new HikariDataSource();
		pool.setJdbcUrl(this.postgres.getJdbcUrl());
		pool.setUsername(this.postgres.getUsername());
		pool.setPassword(password);
		return pool;
	}

	private boolean logsIn(String password) throws SQLException {
		try (Connection _ = DriverManager.getConnection(this.postgres.getJdbcUrl(), this.postgres.getUsername(), password)) {
			return true;
		} catch (SQLException refused) {
			if ("28P01".equals(refused.getSQLState())) {
				return false;
			}
			throw refused;
		}
	}

	private static void assertRefused(Runnable change, DatabaseErrorCode code) {
		assertThatThrownBy(change::run).isInstanceOfSatisfying(BusinessRuleException.class,
				refused -> assertThat(refused.getErrorCode()).isEqualTo(code));
	}

}
