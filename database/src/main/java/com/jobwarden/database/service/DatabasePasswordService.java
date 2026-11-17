package com.jobwarden.database.service;

import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.database.dto.DatabaseLogin;

/**
 * The password JobWarden connects to its database with: the server's, the same for every profile.
 */
public interface DatabasePasswordService {

	/** The fewest characters a new password may have. */
	int PASSWORD_MIN_LENGTH = 12;

	/** The most characters a new password may have. */
	int PASSWORD_MAX_LENGTH = 128;

	/**
	 * @return how JobWarden connects; empty when its connections don't come from a pool it can change the password of
	 */
	Optional<DatabaseLogin> login();

	/**
	 * Gives the database role JobWarden logs in as a new password, given the current one: the database keeps only its
	 * hash, the connection pool uses it from now on, and a file keeps it, encrypted, for the next start, where it takes
	 * the place of the configured one. When anything refuses it, nothing changes.
	 *
	 * @throws BusinessRuleException when the current password is wrong, the new one too short, too long or the same,
	 * or the database or the file refused it (DatabaseErrorCode)
	 */
	void changePassword(String current, String password);

}
