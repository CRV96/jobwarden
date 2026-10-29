package com.jobwarden.web.view;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * What the AI Assistant page gives the profile to paste into Claude Code, which then does a request through JobWarden's
 * MCP server. Short: the request's step-by-step instructions come from the server (get_request). In English, like
 * the MCP server's tools. Without quotes or dollar signs, so it fits in a shell's double quotes as it is.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClaudePrompts {

	/** How often Claude Code looks for waiting requests, when left running the loop. */
	static final String LOOP_EVERY = "15m";

	/**
	 * @param text to paste into Claude Code
	 * @param command the same, as a terminal command that starts Claude Code with it
	 */
	public record Prompt(String text, String command) {
	}

	public static Prompt forRequest(long requestId, long profileId) {
		String text = "Do JobWarden request " + requestId + ": call get_request with request_id " + requestId
				+ " and profile_id " + profileId + ", follow its instructions, and end with finish_request.";
		return new Prompt(text, "claude \"" + text + "\"");
	}

	/**
	 * Claude Code's /loop runs the prompt again and again, so it picks up every request made on the page.
	 */
	public static String loop(long profileId) {
		return "/loop " + LOOP_EVERY + " Do the waiting JobWarden requests: call list_requests with profile_id " + profileId
				+ ", then do each one with get_request and finish_request.";
	}

}
