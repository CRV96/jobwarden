package com.jobwarden.capture.enums;

import com.jobwarden.jobs.enums.AppliedBy;
import com.jobwarden.jobs.enums.ApplicationStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The button the user clicked in the extension popup, or what the extension's Easy Apply run did.
 */
@Getter
@RequiredArgsConstructor
public enum CaptureAction {

	/** Already applied, or applying now: track it as applied. */
	APPLY(ApplicationStatus.APPLIED, null),

	/** Keep it to revisit or apply to later. */
	BOOKMARK(ApplicationStatus.SAVED, null),

	/** The Easy Apply run sent it on LinkedIn: track it as applied, by the extension. */
	EASY_APPLY(ApplicationStatus.APPLIED, AppliedBy.EXTENSION);

	private final ApplicationStatus initialStatus;

	/** Who sent the application, when it's known */
	private final @Nullable AppliedBy appliedBy;

}
