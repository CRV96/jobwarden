package com.jobwarden.inbox.service.impl;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * How the mailboxes are read: {@code jobwarden.inbox.*}. How often ({@code tick}, {@code first-check}) is the
 * schedule's, in InboxChecks.
 *
 * @param timeoutMillis how long connecting, and each answer of the mail server, may take
 * @param tls whether the connection is encrypted; only the tests' own mail server goes without
 */
@ConfigurationProperties("jobwarden.inbox")
@Validated
record InboxProperties(@DefaultValue("20000") @Positive int timeoutMillis, @DefaultValue("true") boolean tls) {
}
