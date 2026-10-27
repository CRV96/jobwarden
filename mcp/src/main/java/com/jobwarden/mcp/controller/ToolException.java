package com.jobwarden.mcp.controller;

/**
 * A tool call that can't be done as asked, e.g. a missing argument: Claude reads the message and can try again.
 */
class ToolException extends RuntimeException {

	ToolException(String message) {
		super(message);
	}

}
