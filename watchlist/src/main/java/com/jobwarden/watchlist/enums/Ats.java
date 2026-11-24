package com.jobwarden.watchlist.enums;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * A job-board system companies post their jobs on, whose boards anyone can read, with no key: as JSON, or Personio's
 * as XML. Lever keeps the boards of companies in Europe apart, on servers of their own. Recruitee and Personio give
 * each company an address of its own, the board's name before their domain.
 */
public enum Ats {

	GREENHOUSE("Greenhouse", "https://job-boards.greenhouse.io/{board}"),
	LEVER("Lever", "https://jobs.lever.co/{board}"),
	LEVER_EU("Lever", "https://jobs.eu.lever.co/{board}"),
	ASHBY("Ashby", "https://jobs.ashbyhq.com/{board}"),
	SMARTRECRUITERS("SmartRecruiters", "https://careers.smartrecruiters.com/{board}"),
	WORKABLE("Workable", "https://apply.workable.com/{board}"),
	RECRUITEE("Recruitee", "https://{board}.recruitee.com"),
	PERSONIO("Personio", "https://{board}.jobs.personio.de");

	private static final String BOARD = "{board}";

	private final String label;

	private final String boardUrl;

	Ats(String label, String boardUrl) {
		this.label = label;
		this.boardUrl = boardUrl;
	}

	/**
	 * @return its name, as companies' careers pages say it
	 */
	public String label() {
		return this.label;
	}

	/**
	 * @param board the board's name, e.g. acme
	 * @return where people see the board, e.g. https://jobs.lever.co/acme or https://acme.recruitee.com
	 */
	public String boardUrl(String board) {
		return this.boardUrl.replace(BOARD, URLEncoder.encode(board, StandardCharsets.UTF_8).replace("+", "%20"));
	}

}
