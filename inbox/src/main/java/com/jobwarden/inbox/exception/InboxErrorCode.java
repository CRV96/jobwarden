package com.jobwarden.inbox.exception;

import com.jobwarden.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InboxErrorCode implements ErrorCode {

	EMAIL_UPDATE_NOT_FOUND("INBOX-001"),

	MAILBOX_NOT_REACHED("INBOX-002"),
	NO_MAILBOX("INBOX-003"),

	/** Nothing answers on this computer at the port: Proton Mail Bridge, say, isn't open. */
	LOCAL_SERVER_DOWN("INBOX-004");

	private final String code;

}
