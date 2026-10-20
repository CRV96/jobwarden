package com.jobwarden.web.dto;

import org.jspecify.annotations.Nullable;

/**
 * The notes field of an application, saved as it's typed.
 */
public record NotesForm(@Nullable String notes) {

	/**
	 * @return the notes, or {@code null} when the field was emptied
	 */
	public @Nullable String toNotes() {
		return FormValues.blankToNull(this.notes);
	}

}
