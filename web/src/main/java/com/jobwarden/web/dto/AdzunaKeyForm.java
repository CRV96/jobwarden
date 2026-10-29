package com.jobwarden.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * The Adzuna key entered in Settings: the app ID and the app key from developer.adzuna.com, letters and digits as
 * Adzuna shows them. Spaces around them, from a copy and paste, are dropped.
 */
public record AdzunaKeyForm(
		@NotBlank(message = "{validation.adzuna.appId.required}")
		@Size(max = AdzunaKeyForm.MAX_LENGTH, message = "{validation.adzuna.size}")
		@Pattern(regexp = AdzunaKeyForm.FORMAT, message = "{validation.adzuna.format}") @Nullable String appId,
		@NotBlank(message = "{validation.adzuna.appKey.required}")
		@Size(max = AdzunaKeyForm.MAX_LENGTH, message = "{validation.adzuna.size}")
		@Pattern(regexp = AdzunaKeyForm.FORMAT, message = "{validation.adzuna.format}") @Nullable String appKey) {

	/** Also the fields' {@code maxlength} in settings/settings.html, and the columns' length. */
	public static final int MAX_LENGTH = 100;

	static final String FORMAT = "\\s*[\\w-]*\\s*";

}
