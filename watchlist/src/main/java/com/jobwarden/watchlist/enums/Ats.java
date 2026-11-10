package com.jobwarden.watchlist.enums;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * A job-board system companies post their jobs on, whose boards anyone can read as JSON, with no key. Lever keeps
 * the boards of companies in Europe apart, on servers of their own.
 */
public enum Ats {

	GREENHOUSE("Greenhouse", "https://job-boards.greenhouse.io/"), LEVER("Lever", "https://jobs.lever.co/"),
	LEVER_EU("Lever", "https://jobs.eu.lever.co/"), ASHBY("Ashby", "https://jobs.ashbyhq.com/");

	private final String label;

	private final String boardsUrl;

	Ats(String label, String boardsUrl) {
		this.label = label;
		this.boardsUrl = boardsUrl;
	}

	/**
	 * @return its name, as companies' careers pages say it
	 */
	public String label() {
		return this.label;
	}

	/**
	 * @param board the board's name, e.g. acme
	 * @return where people see the board, e.g. https://jobs.lever.co/acme
	 */
	public String boardUrl(String board) {
		return this.boardsUrl + URLEncoder.encode(board, StandardCharsets.UTF_8).replace("+", "%20");
	}

}
