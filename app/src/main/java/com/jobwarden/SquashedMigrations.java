package com.jobwarden;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.jobwarden.common.logging.JobWardenLogger;
import org.flywaydb.core.Flyway;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Before the first release, the schema's 49 migrations became 9, one per module (V1 to V9), which build the very same
 * schema. A database the 49 built has them in its history, which Flyway would refuse. As the server starts, such a
 * database gets its history replaced by a baseline at V9, in one transaction: no table and no row of JobWarden's is
 * touched, as its schema is already the one V1 to V9 build. A new database, or one converted already, is left as it is.
 * <p>
 * Delete this class once every database the 49 built has started once with it.
 */
@Configuration(proxyBeanMethods = false)
class SquashedMigrations {

	private static final JobWardenLogger log = JobWardenLogger.of(SquashedMigrations.class);

	/** The last of the 49, by its version and description, which a database they built has in its history */
	static final String LAST_OLD_VERSION = "49";

	static final String LAST_OLD_DESCRIPTION = "identity add profile passwords";

	/** The last of the 9, which the baseline stands for */
	static final String SQUASHED_VERSION = "9";

	static final String BASELINE_DESCRIPTION = "Migrations 1 to 49, squashed into 1 to 9";

	/** Flyway's history table, as JobWarden leaves it: spring.flyway.table isn't set */
	static final String HISTORY = "flyway_schema_history";

	@Bean
	FlywayMigrationStrategy migrateOnceSquashed() {
		return flyway -> {
			convert(flyway);
			flyway.migrate();
		};
	}

	/**
	 * @return whether the database's history was the 49's, and is now the baseline
	 * @throws IllegalStateException when the 49 built the database only in part: it must reach the 49th first
	 */
	static boolean convert(Flyway flyway) {
		if (!HISTORY.equals(flyway.getConfiguration().getTable())) {
			return false;
		}
		try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
			if (!historyExists(connection)) {
				return false;
			}
			List<String> versions = appliedVersions(connection);
			boolean allOld = versions.contains(LAST_OLD_VERSION) && lastOldApplied(connection);
			if (!allOld) {
				if (versions.stream().anyMatch(version -> Integer.parseInt(version) > Integer.parseInt(SQUASHED_VERSION))) {
					throw new IllegalStateException("The database was migrated by JobWarden's migrations from before they "
							+ "were squashed, but not up to V" + LAST_OLD_VERSION + ": start the version before them once, "
							+ "then this one");
				}
				return false;
			}
			replaceHistory(connection);
			log.info("Replaced the database's migration history, of the migrations 1 to {}, with a baseline at V{}: "
					+ "its tables are as they were", LAST_OLD_VERSION, SQUASHED_VERSION);
			return true;
		} catch (SQLException failed) {
			throw new IllegalStateException("Couldn't read or replace the database's migration history", failed);
		}
	}

	private static boolean historyExists(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
			statement.setString(1, HISTORY);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() && result.getBoolean(1);
			}
		}
	}

	/** The versions applied, numbers only: a baseline at V9 is one of them */
	private static List<String> appliedVersions(Connection connection) throws SQLException {
		List<String> versions = new ArrayList<>();
		try (Statement statement = connection.createStatement();
				ResultSet result = statement
					.executeQuery("SELECT version FROM flyway_schema_history WHERE version ~ '^[0-9]+$' AND success")) {
			while (result.next()) {
				versions.add(result.getString(1));
			}
		}
		return versions;
	}

	private static boolean lastOldApplied(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT count(*) FROM flyway_schema_history WHERE version = ? AND description = ? AND success")) {
			statement.setString(1, LAST_OLD_VERSION);
			statement.setString(2, LAST_OLD_DESCRIPTION);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() && result.getLong(1) == 1;
			}
		}
	}

	/** In one transaction: the old history goes only with the baseline in its place, as Flyway's own would be */
	private static void replaceHistory(Connection connection) throws SQLException {
		boolean autoCommit = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try (Statement delete = connection.createStatement();
				PreparedStatement baseline = connection.prepareStatement("INSERT INTO flyway_schema_history "
						+ "(installed_rank, version, description, type, script, checksum, installed_by, execution_time, "
						+ "success) VALUES (1, ?, ?, 'BASELINE', ?, NULL, current_user, 0, TRUE)")) {
			delete.executeUpdate("DELETE FROM flyway_schema_history");
			baseline.setString(1, SQUASHED_VERSION);
			baseline.setString(2, BASELINE_DESCRIPTION);
			baseline.setString(3, BASELINE_DESCRIPTION);
			baseline.executeUpdate();
			connection.commit();
		} catch (SQLException failed) {
			connection.rollback();
			throw failed;
		} finally {
			connection.setAutoCommit(autoCommit);
		}
	}

}
