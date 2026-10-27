/**
 * Common: code every module shares, currently error codes, base exceptions, the application logger and the rule for
 * which addresses the server may fetch from.
 * Owns no tables, has no Spring beans and depends on no other module.
 * <p>
 * Public API: the {@code exception}, {@code logging} and {@code net} packages, marked with {@code @NamedInterface}.
 * Business logic never goes here; it belongs in a domain module.
 */
package com.jobwarden.common;
