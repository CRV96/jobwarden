package com.jobwarden.database.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.database.exception.DatabaseErrorCode;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.PGConnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * In what order a change goes, and what's put back when the database refuses it; against PostgreSQL itself, the app
 * module's DatabasePasswordTests.
 */
class DefaultDatabasePasswordServiceTests {

	private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

	private static final String CURRENT = "the current password";

	private static final String NEW_PASSWORD = "a long new password";

	@TempDir
	private Path folder;

	private final HikariDataSource pool = mock();

	private final Connection connection = mock();

	private final PGConnection postgres = mock();

	private final HikariPoolMXBean connections = mock();

	private SavedPassword saved;

	private DefaultDatabasePasswordService database;

	@BeforeEach
	void aPoolLoggedInAsJobWarden() throws SQLException {
		this.saved = new SavedPassword(this.folder.resolve("database.password"), KEY, this.folder.resolve("inbox.key"));
		this.database = new DefaultDatabasePasswordService(this.pool, this.saved);
		given(this.pool.isWrapperFor(HikariDataSource.class)).willReturn(true);
		given(this.pool.unwrap(HikariDataSource.class)).willReturn(this.pool);
		given(this.pool.getPassword()).willReturn(CURRENT);
		given(this.pool.getUsername()).willReturn("jobwarden");
		given(this.pool.getJdbcUrl()).willReturn("jdbc:postgresql://localhost:5432/jobwarden?ssl=false");
		given(this.pool.getConnection()).willReturn(this.connection);
		given(this.pool.getHikariPoolMXBean()).willReturn(this.connections);
		Statement statement = mock();
		ResultSet sessionUser = mock();
		given(this.connection.createStatement()).willReturn(statement);
		given(statement.executeQuery("SELECT session_user")).willReturn(sessionUser);
		given(sessionUser.next()).willReturn(true);
		given(sessionUser.getString(1)).willReturn("jobwarden");
		given(this.connection.unwrap(PGConnection.class)).willReturn(this.postgres);
		given(this.connection.getAutoCommit()).willReturn(true);
	}

	@Test
	void keepsTheNewPasswordThenGivesItToTheDatabaseThenToThePool() throws SQLException {
		this.database.changePassword(CURRENT, NEW_PASSWORD);

		var order = inOrder(this.postgres, this.pool, this.connections);
		order.verify(this.postgres).alterUserPassword(eq("jobwarden"), any(char[].class), isNull());
		order.verify(this.pool).setPassword(NEW_PASSWORD);
		order.verify(this.connections).softEvictConnections();
		assertThat(this.saved.read()).contains(NEW_PASSWORD);
	}

	@Test
	void putsBackThePasswordKeptBeforeWhenTheDatabaseRefusesTheNewOne() throws SQLException, IOException {
		this.saved.save(CURRENT);
		willThrow(new SQLException("permission denied", "42501")).given(this.postgres)
			.alterUserPassword(anyString(), any(char[].class), isNull());

		assertRefused(() -> this.database.changePassword(CURRENT, NEW_PASSWORD), DatabaseErrorCode.PASSWORD_CHANGE_FAILED);

		assertThat(this.saved.read()).contains(CURRENT);
		then(this.pool).should(never()).setPassword(anyString());
	}

	@Test
	void deletesTheFileItMadeWhenTheDatabaseRefusesTheNewPassword() throws SQLException {
		willThrow(new SQLException("permission denied", "42501")).given(this.postgres)
			.alterUserPassword(anyString(), any(char[].class), isNull());

		assertRefused(() -> this.database.changePassword(CURRENT, NEW_PASSWORD), DatabaseErrorCode.PASSWORD_CHANGE_FAILED);

		assertThat(this.saved.file()).doesNotExist();
	}

	@Test
	void changesNothingWhenTheNewPasswordCantBeKept() throws SQLException, IOException {
		// Where its folder should be, a file
		Path file = Files.writeString(this.folder.resolve("not-a-folder"), "");
		SavedPassword unwritable = new SavedPassword(file.resolve("database.password"), KEY, this.folder.resolve("inbox.key"));
		DefaultDatabasePasswordService keepingNothing = new DefaultDatabasePasswordService(this.pool, unwritable);

		assertRefused(() -> keepingNothing.changePassword(CURRENT, NEW_PASSWORD), DatabaseErrorCode.PASSWORD_NOT_KEPT);

		then(this.postgres).should(never()).alterUserPassword(anyString(), any(char[].class), any());
		then(this.pool).should(never()).setPassword(anyString());
	}

	@Test
	void changesNothingWithoutTheCurrentPassword() throws SQLException {
		assertRefused(() -> this.database.changePassword("the current passwort", NEW_PASSWORD),
				DatabaseErrorCode.PASSWORD_WRONG);
		assertRefused(() -> this.database.changePassword("", NEW_PASSWORD), DatabaseErrorCode.PASSWORD_WRONG);

		then(this.pool).should(never()).getConnection();
		assertThat(this.saved.file()).doesNotExist();
	}

	@Test
	void saysHowItConnectsAndWhetherThePasswordWasChangedHere() {
		var login = this.database.login().orElseThrow();
		assertThat(login.user()).isEqualTo("jobwarden");
		assertThat(login.address()).isEqualTo("localhost:5432/jobwarden");
		assertThat(login.changedHere()).isFalse();
		assertThat(login.defaultPassword()).isFalse();
		assertThat(login.keptIn()).isEqualTo(this.saved.file());

		this.database.changePassword(CURRENT, NEW_PASSWORD);
		given(this.pool.getPassword()).willReturn(NEW_PASSWORD);

		assertThat(this.database.login().orElseThrow().changedHere()).isTrue();
	}

	@Test
	void tellsTheDefaultPassword() {
		given(this.pool.getPassword()).willReturn("jobwarden");

		assertThat(this.database.login().orElseThrow().defaultPassword()).isTrue();
	}

	@Test
	void cantChangeThePasswordOfConnectionsThatDontComeFromHikari() {
		DefaultDatabasePasswordService withoutHikari = new DefaultDatabasePasswordService(mock(DataSource.class), this.saved);

		assertThat(withoutHikari.login()).isEmpty();
		assertRefused(() -> withoutHikari.changePassword(CURRENT, NEW_PASSWORD), DatabaseErrorCode.PASSWORD_NOT_CHANGEABLE);
	}

	@Test
	void showsWhereTheDatabaseIsWithoutWhatMayHoldASecret() {
		assertThat(DefaultDatabasePasswordService.address("jdbc:postgresql://localhost:5432/jobwarden"))
			.isEqualTo("localhost:5432/jobwarden");
		assertThat(DefaultDatabasePasswordService.address("jdbc:postgresql://db:5432/jobwarden?user=jw&password=secret"))
			.isEqualTo("db:5432/jobwarden");
		assertThat(DefaultDatabasePasswordService.address("jdbc:postgresql://jw:secret@db/jobwarden")).isEqualTo("db/jobwarden");
		assertThat(DefaultDatabasePasswordService.address("jdbc:postgresql://jw:s/e@cret@db/jobwarden"))
			.isEqualTo("db/jobwarden");
		assertThat(DefaultDatabasePasswordService.address("jdbc:postgresql://a:5432,b:5433/jobwarden"))
			.isEqualTo("a:5432,b:5433/jobwarden");
		assertThat(DefaultDatabasePasswordService.address(null)).isEmpty();
	}

	private static void assertRefused(Runnable change, DatabaseErrorCode code) {
		assertThatThrownBy(change::run).isInstanceOfSatisfying(BusinessRuleException.class,
				refused -> assertThat(refused.getErrorCode()).isEqualTo(code));
	}

}
