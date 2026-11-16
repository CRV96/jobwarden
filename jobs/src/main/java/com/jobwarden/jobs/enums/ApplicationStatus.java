package com.jobwarden.jobs.enums;

/**
 * Top-level stage of an application, used for filtering and the dashboard. The company-specific
 * sub-stage ("Technical interview", "Onsite round 2") is free text on each timeline event.
 * <p>
 * In the order the lists sort and the pipeline shows them: the way an application goes, then the ways it ends.
 */
public enum ApplicationStatus {

	SAVED, APPLIED, INTERVIEWING, OFFER,

	/** The company opened the position again after closing it: the application is in play again, and may close again. */
	POSITION_REOPENED,

	REJECTED,

	/** Never heard back. */
	NO_RESPONSE,

	/** The company closed the position, e.g. filled or cancelled it, without a decision on this application. */
	POSITION_CLOSED,

	WITHDRAWN;

	/**
	 * Whether this status goes through stages of the company's own, which events can name, e.g. "Technical interview"
	 * or "Offer call". The others are a single step: there's nothing to name.
	 */
	public boolean hasStages() {
		return this == INTERVIEWING || this == OFFER;
	}

	/**
	 * Whether an event with this status can follow one with the same: an interview after an interview, another round of
	 * offers. The others say something once: a job closed is closed until it's reopened, then it can close again.
	 */
	public boolean canRepeat() {
		return hasStages();
	}

	/**
	 * The step most likely added next to a job with this status: the same again when it can repeat (another interview,
	 * another round of offers), else an interview.
	 */
	public ApplicationStatus likelyNext() {
		return canRepeat() ? this : INTERVIEWING;
	}

	/**
	 * Whether the application ended with this status: turned down, never answered, closed, or withdrawn.
	 */
	public boolean hasEnded() {
		return this == REJECTED || this == NO_RESPONSE || this == POSITION_CLOSED || this == WITHDRAWN;
	}

	/**
	 * Whether events with this status say why ({@link RejectionReason}): only a rejection does.
	 */
	public boolean hasRejectionReason() {
		return this == REJECTED;
	}

	/**
	 * Whether events with this status can say how and where the interview takes place ({@link InterviewFormat}, a
	 * location or a meeting link, a duration): only an interview can.
	 */
	public boolean hasInterviewDetails() {
		return this == INTERVIEWING;
	}

}
