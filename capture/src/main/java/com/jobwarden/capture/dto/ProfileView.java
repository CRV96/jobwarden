package com.jobwarden.capture.dto;

import java.util.UUID;

/**
 * A profile, as the extension's settings find it by the id they're given.
 *
 * @param id the number the extension then sends with each capture
 * @param publicId the id the profile shows, to show back
 */
public record ProfileView(long id, UUID publicId, String name) {
}
