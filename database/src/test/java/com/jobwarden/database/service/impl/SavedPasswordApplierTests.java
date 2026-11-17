package com.jobwarden.database.service.impl;

import java.io.IOException;
import java.nio.file.Path;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Without a database to ask; with one, the app module's DatabasePasswordTests.
 */
class SavedPasswordApplierTests {

	private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

	private static final String OTHER_KEY = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";

	@TempDir
	private Path folder;

	@Test
	void leavesThePoolAloneWhenNoPasswordWasChanged() {
		try (HikariDataSource pool = pool()) {
			apply(saved(KEY), pool);

			assertThat(pool.getPassword()).isEqualTo("jobwarden");
		}
	}

	@Test
	void givesTheSavedPasswordWhenTheDatabaseCantBeReachedYet() throws IOException {
		saved(KEY).save("a long new password");

		try (HikariDataSource pool = pool()) {
			apply(saved(KEY), pool);

			assertThat(pool.getPassword()).isEqualTo("a long new password");
		}
	}

	@Test
	void keepsTheConfiguredPasswordWhenTheSavedOneCantBeRead() throws IOException {
		saved(KEY).save("a long new password");

		try (HikariDataSource pool = pool()) {
			apply(saved(OTHER_KEY), pool);

			assertThat(pool.getPassword()).isEqualTo("jobwarden");
		}
	}

	@Test
	void caresOnlyForConnectionPools() {
		Object other = new Object();

		assertThat(new SavedPasswordApplier(saved(KEY)).postProcessAfterInitialization(other, "other")).isSameAs(other);
	}

	private SavedPassword saved(String key) {
		return new SavedPassword(this.folder.resolve("database.password"), key, this.folder.resolve("inbox.key"));
	}

	/** Nothing listens on port 1: the database can't be asked */
	private static HikariDataSource pool() {
		HikariDataSource pool = new HikariDataSource();
		pool.setJdbcUrl("jdbc:postgresql://127.0.0.1:1/jobwarden?connectTimeout=2");
		pool.setUsername("jobwarden");
		pool.setPassword("jobwarden");
		return pool;
	}

	private static void apply(SavedPassword saved, HikariDataSource pool) {
		assertThat(new SavedPasswordApplier(saved).postProcessAfterInitialization(pool, "dataSource")).isSameAs(pool);
	}

}
