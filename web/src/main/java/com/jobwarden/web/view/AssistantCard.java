package com.jobwarden.web.view;

/**
 * One of a job's AI Assistant cards, in the overview under its facts: what it's called, where it is on the page, and
 * whether the assistant made it yet.
 *
 * @param title the message key of its title, e.g. tailoredCv.title
 * @param anchor the id of the card on the page, to jump to, e.g. tailored-cv
 */
public record AssistantCard(String title, String anchor, State state) {

	/**
	 * Whether there's something yet: none, the assistant on it (asked, or working), or made.
	 */
	public enum State {

		NONE, WORKING, DONE

	}

}
