package com.jobwarden.jobs.enums;

/**
 * Top-level stage of an application, used for filtering and the dashboard. The company-specific
 * sub-stage ("Technical interview", "Onsite round 2") is free text on each timeline event.
 * <p>
 * In the order the lists sort and the pipeline shows them: the way an application goes, then the ways it ends.
 */
public enum ApplicationStatus {

	SAVED, APPLIED, INTERVIEWING, OFFER, REJECTED,

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
