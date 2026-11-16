package com.jobwarden.mcp.controller;

import java.util.List;

import com.jobwarden.watchlist.dto.WatchResult;
import com.jobwarden.watchlist.enums.Ats;
import com.jobwarden.watchlist.service.WatchlistService;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.string;

/**
 * The watchlist: companies whose job boards on Greenhouse, Lever and Ashby JobWarden reads itself every few hours,
 * with no tokens, adding the postings that match the saved searches to the AI Assistant page. Claude adds the boards
 * it comes across, so it needn't search them again.
 */
@Component
@RequiredArgsConstructor
class WatchlistTools implements McpToolSet {

	private final Profiles profiles;

	private final WatchlistService watchlist;

	@Override
	public List<McpTool> tools() {
		return List.of(watchCompany());
	}

	private McpTool watchCompany() {
		return new McpTool("watch_company", "Watch a company's job board",
				"Watches a company's job board on Greenhouse, Lever or Ashby: JobWarden then reads it every few hours with "
						+ "no tokens and adds the postings that match the saved searches to the AI Assistant page. Call it "
						+ "when you find such a company whose jobs fit.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"link", string("Its board, or one of its jobs, e.g. https://jobs.lever.co/acme"),
						"name", string("Default the board's")),
						"link"),
				false, true, call -> {
					long userId = this.profiles.of(call);
					WatchResult watched = this.watchlist.watch(userId, call.requiredString("link"), call.string("name"));
					return new Watched(watched.company().id(), watched.company().name(), watched.company().ats(),
							watched.company().boardUrl(), watched.added(), watched.newJobs());
				});
	}

	/**
	 * @param added whether it's new on the watchlist; false when it was watched already
	 * @param newJobs how many of its postings matched a saved search, and went to the AI Assistant page
	 */
	record Watched(long companyId, String name, Ats ats, String boardUrl, boolean added, int newJobs) {
	}

}
