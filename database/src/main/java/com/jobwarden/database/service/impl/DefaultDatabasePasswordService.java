package com.jobwarden.database.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.database.dto.DatabaseLogin;
import com.jobwarden.database.exception.DatabaseErrorCode;
import com.jobwarden.database.service.DatabasePasswordService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.postgresql.PGConnection;

import org.springframework.stereotype.Service;

/**
 * Changes the password in this order, so a failure never leaves the database with a password the next start doesn't
 * know: the file first (put back as it was when the database refuses), then the database, then the pool. The database
 * gets the password's SCRAM hash, made here (PGConnection.alterUserPassword): the password itself never reaches it, nor
 * its log. The pool's open connections stay as they are, logged in already; the pool replaces them with new ones, which
 * log in with the new password.
 */
@Service
@CustomLog
@RequiredArgsConstructor
class DefaultDatabasePasswordService implements DatabasePasswordService {

	/** The password JobWarden's Compose files give the database unless told another: anyone can read it there */
	static final String DEFAULT_PASSWORD = "jobwarden";

	private final DataSource dataSource;

	private final SavedPassword saved;

	/** One change at a time: two at once could leave the file with one password and the database with the other */
	private final Lock changing = new ReentrantLock();

	@Override
	public Optional<DatabaseLogin> login() {
		return pool().map(pool -> new DatabaseLogin(Objects.requireNonNullElse(pool.getUsername(), ""),
				address(pool.getJdbcUrl()), changedHere(pool.getPassword()), DEFAULT_PASSWORD.equals(pool.getPassword()),
				this.saved.file()));
	}

	@Override
	public void changePassword(String current, String password) {
		HikariDataSource pool = pool().orElseThrow(() -> new BusinessRuleException(
				DatabaseErrorCode.PASSWORD_NOT_CHANGEABLE, "The connections don't come from a HikariCP pool"));
		this.changing.lock();
		try {
			String inUse = Objects.requireNonNullElse(pool.getPassword(), "");
			if (!sameText(current, inUse)) {
				throw new BusinessRuleException(DatabaseErrorCode.PASSWORD_WRONG, "Not the database's current password");
			}
			checkNew(password, inUse);
			String before = keptBefore();
			keep(password);
			String role;
			try (Connection connection = pool.getConnection()) {
				role = sessionUser(connection);
				connection.unwrap(PGConnection.class).alterUserPassword(role, password.toCharArray(), null);
				if (!connection.getAutoCommit()) {
					connection.commit();
				}
			} catch (SQLException refused) {
				putBack(before);
				log.error(DatabaseErrorCode.PASSWORD_CHANGE_FAILED, "The database refused the new password", refused);
				throw new BusinessRuleException(DatabaseErrorCode.PASSWORD_CHANGE_FAILED, "The database refused it",
						Objects.requireNonNullElse(refused.getMessage(), refused.getClass().getSimpleName()));
			}
			pool.setPassword(password);
			HikariPoolMXBean connections = pool.getHikariPoolMXBean();
			if (connections != null) {
				connections.softEvictConnections();
			}
			log.info("Changed the password of the database role {}; kept for the next start in {}", role,
					this.saved.file());
		} finally {
			this.changing.unlock();
		}
	}

	/**
	 * @return the pool the connections come from; empty when they don't come from HikariCP, whose password can change
	 * while it runs
	 */
	private Optional<HikariDataSource> pool() {
		try {
			return this.dataSource.isWrapperFor(HikariDataSource.class)
					? Optional.of(this.dataSource.unwrap(HikariDataSource.class)) : Optional.empty();
		} catch (SQLException notAWrapper) {
			return Optional.empty();
		}
	}

	/**
	 * Where the database is, without what may hold a secret: jdbc:postgresql://localhost:5432/jobwarden?password=...
	 * is localhost:5432/jobwarden.
	 */
	static String address(@Nullable String url) {
		if (url == null) {
			return "";
		}
		String address = url;
		int scheme = address.indexOf("://");
		if (scheme >= 0) {
			address = address.substring(scheme + 3);
		}
		int parameters = address.indexOf('?');
		if (parameters >= 0) {
			address = address.substring(0, parameters);
		}
		// A user and password before the host, which some drivers take: up to the last @, as the password may hold a /
		int login = address.lastIndexOf('@');
		return (login >= 0) ? address.substring(login + 1) : address;
	}

	private boolean changedHere(@Nullable String inUse) {
		try {
			return inUse != null && this.saved.read().filter(inUse::equals).isPresent();
		} catch (IllegalStateException | UncheckedIOException unreadable) {
			return false;
		}
	}

	/** Compared in a time that doesn't tell how much of it was right */
	private static boolean sameText(String typed, String password) {
		return MessageDigest.isEqual(typed.getBytes(StandardCharsets.UTF_8), password.getBytes(StandardCharsets.UTF_8));
	}

	private static void checkNew(String password, String inUse) {
		int length = password.codePointCount(0, password.length());
		if (length < PASSWORD_MIN_LENGTH || password.isBlank()) {
			throw new BusinessRuleException(DatabaseErrorCode.PASSWORD_TOO_SHORT, "New database password too short",
					String.valueOf(PASSWORD_MIN_LENGTH));
		}
		if (length > PASSWORD_MAX_LENGTH) {
			throw new BusinessRuleException(DatabaseErrorCode.PASSWORD_TOO_LONG, "New database password too long",
					String.valueOf(PASSWORD_MAX_LENGTH));
		}
		if (sameText(password, inUse)) {
			throw new BusinessRuleException(DatabaseErrorCode.PASSWORD_UNCHANGED, "New database password is the current one");
		}
	}

	/** What the file kept before, to put back if the database refuses the new one; null when none, or unreadable */
	private @Nullable String keptBefore() {
		try {
			return this.saved.read().orElse(null);
		} catch (IllegalStateException | UncheckedIOException unreadable) {
			return null;
		}
	}

	private void keep(String password) {
		try {
			this.saved.save(password);
		} catch (IOException | IllegalStateException | UncheckedIOException failed) {
			log.error(DatabaseErrorCode.PASSWORD_NOT_KEPT, "Couldn't keep the new database password in {}",
					this.saved.file(), failed);
			throw new BusinessRuleException(DatabaseErrorCode.PASSWORD_NOT_KEPT, "Couldn't write the password file",
					this.saved.file());
		}
	}

	private void putBack(@Nullable String before) {
		try {
			if (before != null) {
				this.saved.save(before);
			} else {
				this.saved.delete();
			}
		} catch (IOException | IllegalStateException | UncheckedIOException failed) {
			log.error(DatabaseErrorCode.PASSWORD_CHANGE_FAILED,
					"Couldn't put back {} after the database refused the new password: delete it before the next start",
					this.saved.file(), failed);
		}
	}

	/** The role the pool logs in as, which the database names whether or not the pool was told it */
	private static String sessionUser(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT session_user")) {
			if (!result.next()) {
				throw new SQLException("The database named no session user");
			}
			return result.getString(1);
		}
	}

}
