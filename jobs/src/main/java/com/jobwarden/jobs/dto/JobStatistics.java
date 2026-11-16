package com.jobwarden.jobs.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * How the profile's applications went, for the statistics page: worked out from the timelines, the fits and where the
 * jobs came from, without AI.
 *
 * @param jobs how many jobs the profile has
 * @param sent how many it applied to: their timeline has Applied, or what only comes after it
 * @param replied how many of those got an answer: an interview, an offer, or a rejection that wasn't for no response
 * @param interviewed how many got to an interview, or straight to an offer
 * @param medianDaysToReply from applying to the first answer, the median; {@code null} when no answer has both dates
 * @param bySite by the site each job came from, those applied to most first; a {@code null} key for the jobs added by
 * hand
 * @param byMethod by who applied: an {@link com.jobwarden.jobs.enums.AppliedBy}'s name
 * @param byFit by the fit's verdict: a {@link com.jobwarden.jobs.enums.FitVerdict}'s name, and {@code null} for the jobs
 * with no score
 * @param rejectionReasons how many rejections gave each reason, the most first
 * @param skillGaps the requirements the fits found missing or partly met most often, the most first
 */
public record JobStatistics(int jobs, int sent, int replied, int interviewed, int offered,
		@Nullable Integer medianDaysToReply, List<ReplyRate> bySite, List<ReplyRate> byMethod, List<ReplyRate> byFit,
		List<ReasonCount> rejectionReasons, List<SkillGap> skillGaps) {

	/**
	 * How the applications of one kind went.
	 *
	 * @param key what they have in common: a site, who applied, a verdict; {@code null} for none
	 * @param sent how many were sent
	 */
	public record ReplyRate(@Nullable String key, int sent, int replied, int interviewed) {

		/** @return the share that got an answer, in percent, rounded */
		public int replyPercent() {
			return percent(this.replied, this.sent);
		}

		/** @return the share that got to an interview, in percent, rounded */
		public int interviewPercent() {
			return percent(this.interviewed, this.sent);
		}

	}

	public record ReasonCount(String reason, int count) {
	}

	/**
	 * A requirement the CV didn't meet in the postings scored.
	 *
	 * @param requirement as one posting worded it
	 * @param missing in how many postings the CV had nothing for it
	 * @param partly in how many it had something near it
	 * @param mustHave whether a posting required it
	 */
	public record SkillGap(String requirement, int missing, int partly, boolean mustHave) {
	}

	/** @return the share of them that got an answer, in percent */
	public int replyPercent() {
		return percent(this.replied, this.sent);
	}

	/** @return the share of them that got to an interview, in percent */
	public int interviewPercent() {
		return percent(this.interviewed, this.sent);
	}

	static int percent(int part, int whole) {
		return (whole == 0) ? 0 : (int) Math.round(100.0 * part / whole);
	}

}
