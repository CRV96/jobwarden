package com.jobwarden.web.dto;

import org.jspecify.annotations.Nullable;

/**
 * What a field of chips on the search page suggests, as the page shows it.
 *
 * @param value what the chip holds
 * @param label what the suggestion says, often the value
 * @param detail more about it, in smaller text: a job board's domain
 * @param flag the flag icon's country code, shown instead of the field's icon
 */
public record TagSuggestion(String value, String label, @Nullable String detail, @Nullable String flag) {

	public static TagSuggestion of(String value) {
		return new TagSuggestion(value, value, null, null);
	}

}
