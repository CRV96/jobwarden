package com.jobwarden.capture.dto;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import com.jobwarden.capture.enums.CaptureAction;
import com.jobwarden.jobs.dto.PngImage;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import org.jspecify.annotations.Nullable;

/**
 * What the extension sends: the fields it extracted from the page, plus the raw data they came from.
 *
 * @param includeTime whether the timeline records the time of the capture, not only its date; yes when left out
 * @param icon the site's icon, as the browser showed it on the posting's tab, drawn into a PNG: a
 * {@code data:image/png;base64,...} URL
 * @param logoUrl where the posting has the company's logo; the server downloads it
 */
public record CaptureRequest(
		@NotNull Long userId,
		@NotNull CaptureAction action,
		@NotBlank String title,
		@Nullable String company,
		@Nullable String description,
		@NotBlank @URL String link,
		@Nullable String location,
		@Nullable String salary,
		@Nullable String employmentType,
		@Nullable Map<String, Object> rawPayload,
		@Nullable Boolean includeTime,
		@Size(max = CaptureRequest.ICON_MAX_LENGTH) @Pattern(regexp = "data:image/png;base64,[A-Za-z0-9+/]+={0,2}")
		@Nullable String icon,
		@Size(max = CaptureRequest.LOGO_URL_MAX_LENGTH) @Pattern(regexp = "https?://.+") @Nullable String logoUrl) {

	private static final String ICON_PREFIX = "data:image/png;base64,";

	/** The prefix, then the base64 of an icon of PngImage.MAX_SIZE bytes: 4 characters for every 3 bytes. */
	static final int ICON_MAX_LENGTH = 22 + 4 * ((64 * 1024 + 2) / 3);

	/** Longer than any image address a page has: LinkedIn's, with their signatures, take about 250 characters. */
	static final int LOGO_URL_MAX_LENGTH = 2048;

	/**
	 * The icon holds a PNG, not only says it does. The other constraints report an icon that isn't a PNG data URL.
	 */
	@AssertTrue(message = "must be a PNG")
	public boolean isIconPng() {
		return this.icon == null || !this.icon.startsWith(ICON_PREFIX) || this.icon.length() > ICON_MAX_LENGTH
				|| decodedIcon().map(PngImage::isPng).orElse(true);
	}

	/**
	 * @return the icon, or {@code null} without one
	 */
	public @Nullable PngImage toSourceIcon() {
		return decodedIcon().map(PngImage::new).orElse(null);
	}

	private Optional<byte[]> decodedIcon() {
		if (this.icon == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(Base64.getDecoder().decode(this.icon.substring(ICON_PREFIX.length())));
		} catch (IllegalArgumentException notBase64) {
			// The pattern reports it
			return Optional.empty();
		}
	}

}
