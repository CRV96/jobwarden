package com.jobwarden.watchlist.dto;

import com.jobwarden.watchlist.enums.Ats;
import org.jspecify.annotations.Nullable;

/**
 * The job board a link is on, and the company the profile watches there, if it does: what the browser extension shows
 * on a job's page.
 *
 * @param board the board's name on its system, e.g. acme
 * @param boardUrl where people see the board, e.g. https://jobs.lever.co/acme
 * @param name the company's name: as the profile watches it, else as the board's name has it
 * @param company the company, when the profile watches it
 */
public record BoardLink(Ats ats, String board, String boardUrl, String name, @Nullable WatchedCompany company) {
}
