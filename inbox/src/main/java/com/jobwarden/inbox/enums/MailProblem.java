package com.jobwarden.inbox.enums;

/**
 * Why a mailbox couldn't be read, when JobWarden can say it in the profile's language rather than as the server did.
 */
public enum MailProblem {

	/** Nothing answers on this computer at the port: Proton Mail Bridge, say, isn't open. */
	LOCAL_SERVER_DOWN,

	/**
	 * The server on this computer shows another certificate than when it was connected: Proton Mail Bridge was reset
	 * or installed again, or something else answers at its port.
	 */
	CERTIFICATE_CHANGED

}
