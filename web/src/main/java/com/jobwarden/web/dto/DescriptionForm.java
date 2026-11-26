package com.jobwarden.web.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A job's description, edited on its page: its text with its bold, italics and lists as Markdown, as the editor writes
 * it (rich-text.js).
 */
public record DescriptionForm(
		@Size(max = DescriptionForm.MAX_LENGTH, message = "{validation.description.size}") @Nullable String description) {

	/** A posting's text runs to a few thousand characters: this is room for any. */
	public static final int MAX_LENGTH = 100_000;

	/**
	 * @return the description, its lines ending as on any computer, or {@code null} when it was emptied
	 */
	public @Nullable String toDescription() {
		return (this.description == null) ? null : FormValues.blankToNull(this.description.replace("\r\n", "\n"));
	}

}
