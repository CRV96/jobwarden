package com.jobwarden.jobs.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * What Claude wrote to negotiate an offer, from the market salaries JobWarden has, the profile's other offers and what
 * it read about the company's pay. Build it with {@link #builder()}.
 *
 * @param position where the offer stands: against the market for the role and the country, and against the other
 * offers
 * @param target what to ask for, with the figure and why
 * @param walkAway the least worth accepting
 * @param leverage what makes the ask fair: the market's figures, the other offers, what the profile brings
 * @param asks what else to ask for when the salary can't move: a signing bonus, time off, remote days, a title...
 * @param script what to say on the call, in a few lines
 * @param email an email asking for it, ready to adapt and send
 * @param watchOut what to be careful about, e.g. an offer that expires, a company that cuts offers that negotiate
 * @param sources where the figures come from: JobWarden's market sources, the pages read
 */
@Builder
public record NegotiationBrief(String position, @Nullable String target, @Nullable String walkAway,
		List<String> leverage, List<String> asks, @Nullable String script, @Nullable String email,
		@Nullable String watchOut, List<String> sources, @Nullable Instant updatedAt) {

	/**
	 * A builder can't make sure every required field is set, so a missing one fails here, right away. The lists left
	 * out are empty.
	 */
	public NegotiationBrief {
		Objects.requireNonNull(position, "position");
		leverage = (leverage == null) ? List.of() : List.copyOf(leverage);
		asks = (asks == null) ? List.of() : List.copyOf(asks);
		sources = (sources == null) ? List.of() : List.copyOf(sources);
	}

}
