package com.jobwarden.jobs.entity;

import java.io.Serializable;

import jakarta.persistence.Embeddable;

/**
 * A source icon's id: one icon per profile and site.
 *
 * @param source the site, e.g. {@code linkedin.com}: an application's source
 */
@Embeddable
public record SourceIconKey(long userId, String source) implements Serializable {
}
