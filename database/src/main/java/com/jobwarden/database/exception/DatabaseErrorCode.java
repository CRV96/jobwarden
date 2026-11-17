package com.jobwarden.database.exception;

import com.jobwarden.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DatabaseErrorCode implements ErrorCode {

	/** The current password typed isn't the one JobWarden connects with. */
	PASSWORD_WRONG("DATABASE-001"),

	PASSWORD_TOO_SHORT("DATABASE-002"),

	PASSWORD_TOO_LONG("DATABASE-003"),

	/** The new password is the current one. */
	PASSWORD_UNCHANGED("DATABASE-004"),

	/** The connections don't come from a pool JobWarden can give a new password to: not HikariCP, not PostgreSQL. */
	PASSWORD_NOT_CHANGEABLE("DATABASE-005"),

	/** The database refused the change: nothing changed. */
	PASSWORD_CHANGE_FAILED("DATABASE-006"),

	/** The file that keeps the password for the next start couldn't be written: nothing changed. */
	PASSWORD_NOT_KEPT("DATABASE-007");

	private final String code;

}
