package com.jobwarden.database.service.impl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The password changed in Settings, given to the connection pool as the server starts.
 */
@Configuration(proxyBeanMethods = false)
class DatabaseConfiguration {

	/**
	 * Static, as a post-processor is made before the other beans, the data source among them: its file is read from
	 * the settings themselves.
	 */
	@Bean
	static SavedPasswordApplier savedPasswordApplier(Environment environment) {
		return new SavedPasswordApplier(SavedPassword.of(environment));
	}

}
