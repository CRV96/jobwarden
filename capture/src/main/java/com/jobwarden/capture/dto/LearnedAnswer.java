package com.jobwarden.capture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An answer the user gave during an Easy Apply run to a question the profile's answers didn't cover: kept for the
 * next forms.
 *
 * @param question the form's question, word for word
 * @param answer what the user filled in: for a choice, the option's text
 */
public record LearnedAnswer(@NotBlank @Size(max = LearnedAnswer.QUESTION_MAX_LENGTH) String question,
		@NotBlank @Size(max = LearnedAnswer.ANSWER_MAX_LENGTH) String answer) {

	/** As long as the Auto-apply page takes them. */
	static final int QUESTION_MAX_LENGTH = 300;

	static final int ANSWER_MAX_LENGTH = 2000;

}
