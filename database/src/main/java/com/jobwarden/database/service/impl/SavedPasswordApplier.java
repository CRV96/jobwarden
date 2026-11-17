package com.jobwarden.database.service.impl;

import java.io.UncheckedIOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;

import com.zaxxer.hikari.HikariDataSource;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * As the server starts, gives the connection pool the password changed in Settings in place of the configured one,
 * however the data source was configured: {@code spring.datasource.password}, the Docker Compose database's, a test's
 * container. The database is asked first whether it takes it. When it refuses it (a backup from before the change was
 * restored, say) or the file can't be read (the key changed), the configured one stays, and the log says so. When the
 * database can't be reached yet, the saved one is given: it's the last one the database was given.
 */
@CustomLog
@RequiredArgsConstructor
class SavedPasswordApplier implements BeanPostProcessor {

	/** SQLSTATE class 28, invalid authorization: a wrong password, or a role that may not log in */
	private static final String LOGIN_REFUSED = "28";

	private final SavedPassword saved;

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof HikariDataSource pool) {
			apply(pool);
		}
		return bean;
	}

	/**
	 * Before the pool opens its first connection: Spring Boot's pool opens none until it's asked for one.
	 */
	void apply(HikariDataSource pool) {
		Optional<String> kept;
		try {
			kept = this.saved.read();
		} catch (IllegalStateException | UncheckedIOException unreadable) {
			log.warn("Couldn't read the database password changed in Settings, in {} ({}): connecting with the configured one",
					this.saved.file(), unreadable.getMessage());
			return;
		}
		if (kept.isEmpty() || kept.get().equals(pool.getPassword())) {
			return;
		}
		if (refused(pool, kept.get())) {
			log.warn("The database refused the password changed in Settings, kept in {}: connecting with the configured one",
					this.saved.file());
			return;
		}
		pool.setPassword(kept.get());
		log.info("Connecting to the database with the password changed in Settings");
	}

	/**
	 * @return whether the database refuses the login with this password; not when it can't be told, e.g. it isn't up yet
	 */
	private static boolean refused(HikariDataSource pool, String password) {
		String url = pool.getJdbcUrl();
		if (url == null) {
			return false;
		}
		Properties login = new Properties();
		login.putAll(pool.getDataSourceProperties());
		String user = pool.getUsername();
		if (user != null) {
			login.setProperty("user", user);
		}
		login.setProperty("password", password);
		try (var _ = DriverManager.getConnection(url, login)) {
			return false;
		} catch (SQLException failed) {
			String state = failed.getSQLState();
			return state != null && state.startsWith(LOGIN_REFUSED);
		}
	}

}
