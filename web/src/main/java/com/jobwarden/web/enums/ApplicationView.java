package com.jobwarden.web.enums;

import com.jobwarden.web.constants.AppConstants.Views;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Where an application's parts render. The forms in them send it along (as {@code view}), so the answer to a form
 * refreshes the part it came from.
 */
@Getter
@RequiredArgsConstructor
public enum ApplicationView {

	/** The application's own page. */
	PAGE(Views.APPLICATION_DETAIL_FRAGMENT),

	/** The side panel the list and the pipeline open over themselves. */
	DRAWER(Views.APPLICATION_DRAWER_FRAGMENT);

	/** The template fragment that holds the parts. */
	private final String fragment;

}
