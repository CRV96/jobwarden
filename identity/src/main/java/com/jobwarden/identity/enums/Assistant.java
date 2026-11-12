package com.jobwarden.identity.enums;

/**
 * The AI assistant a profile works with. Both do the same requests through JobWarden's MCP server; the pages write
 * what to paste, and the terminal commands to run, for the one the profile chose.
 */
public enum Assistant {

	/** Anthropic's Claude Code, the claude command; with Claude in Chrome, the only one that applies for the profile. */
	CLAUDE_CODE("Claude Code", "claude", "Claude"),

	/** OpenAI's Codex CLI, the codex command; and its desktop app. */
	CODEX("Codex", "codex", "Codex");

	/** Every profile's until it chooses: the one JobWarden was first made for. */
	public static final Assistant DEFAULT = CLAUDE_CODE;

	private final String product;

	private final String command;

	private final String app;

	Assistant(String product, String command, String app) {
		this.product = product;
		this.command = command;
		this.app = app;
	}

	/**
	 * @return the product's own name, e.g. Claude Code, which the pages show as it is in every language
	 */
	public String product() {
		return this.product;
	}

	/**
	 * @return the desktop app it runs in, e.g. Claude: Claude Code is a tab of Claude's desktop app
	 */
	public String app() {
		return this.app;
	}

	/**
	 * @return the command that starts it in a terminal
	 */
	public String command() {
		return this.command;
	}

	/**
	 * @return whether it can apply to jobs for the profile: that takes a browser it drives in the profile's own Chrome,
	 * which only Claude Code has, with Claude in Chrome
	 */
	public boolean appliesInBrowser() {
		return this == CLAUDE_CODE;
	}

}
