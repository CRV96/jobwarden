package com.jobwarden.web.view;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jobwarden.common.exception.JobWardenException;
import lombok.RequiredArgsConstructor;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

/**
 * The texts a page shows about what went wrong, in the user's language: a module's exception, a form's errors.
 */
@Component
@RequiredArgsConstructor
public class FormTexts {

	private final MessageSource messages;

	/**
	 * What users see for one of the modules' exceptions: its code's message, in their language.
	 */
	public String message(JobWardenException exception) {
		String detail = exception.getMessage();
		String inEnglish = (detail != null) ? detail : exception.getErrorCode().getCode();
		String translated = this.messages.getMessage(exception.getErrorCode().getMessageKey(), exception.getArguments(),
				inEnglish, LocaleContextHolder.getLocale());
		return (translated != null) ? translated : inEnglish;
	}

	/**
	 * A message of the pages, in the user's language.
	 */
	public String text(String code) {
		String translated = this.messages.getMessage(code, null, code, LocaleContextHolder.getLocale());
		return (translated != null) ? translated : code;
	}

	/**
	 * The first error of each field, by field name, in the user's language, to show next to the form's fields.
	 */
	public Map<String, String> fieldErrors(BindingResult result) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : result.getFieldErrors()) {
			// The validation message, or for a value that couldn't even be converted (e.g. text as a date) the
			// typeMismatch message
			errors.putIfAbsent(error.getField(), this.messages.getMessage(error, LocaleContextHolder.getLocale()));
		}
		return errors;
	}

	/**
	 * All the form's errors as one text, for forms that show a single error line.
	 */
	public String errorMessage(BindingResult result) {
		return String.join(" ", fieldErrors(result).values());
	}

}
