package com.jobwarden.web.view;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * What the AI Assistant page gives the profile to paste into Claude Code, which then does a request through JobWarden's
 * MCP server, and the terminal loop that does every request on its own. Short: the request's step-by-step
 * instructions come from the server (get_request). In English, like the MCP server's tools. The prompts have no
 * quotes or dollar signs, so they fit in a shell's double quotes as they are.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClaudePrompts {

	/** How often the loop asks JobWarden whether a request waits: asking costs nothing, only doing one does. */
	static final int LOOP_EVERY_SECONDS = 300;

	/**
	 * What Claude Code may do in the loop without asking, as it runs unattended: JobWarden's tools (the MCP server named
	 * jobwarden, as the README adds it), and reading the web.
	 */
	static final String LOOP_TOOLS = "mcp__jobwarden WebSearch WebFetch";

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
	 * A terminal loop that picks up every request made on the page: it asks JobWarden every few minutes whether one
	 * waits, and only then starts Claude Code, afresh each time. Waiting costs no tokens, and no request reads through
	 * the ones before it, as it would in one long Claude Code session. Auto-apply isn't among them: it needs the
	 * profile at the browser.
	 *
	 * @param waitingUrl where JobWarden says how many requests wait, e.g. http://localhost:8080/claude/requests
	 */
	public static String loop(long profileId, String waitingUrl) {
		String prompt = "Do the waiting JobWarden requests but the AUTO_APPLY ones: call list_requests with profile_id "
				+ profileId + ", then do each one with get_request and finish_request.";
		return "while true; do n=$(curl -sf '" + waitingUrl + "?profile=" + profileId + "'); [ \"${n:-0}\" -gt 0 ] && claude -p \""
				+ prompt + "\" --allowedTools '" + LOOP_TOOLS + "'; sleep " + LOOP_EVERY_SECONDS + "; done";
	}

}
