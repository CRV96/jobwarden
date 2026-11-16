/**
 * Identity: local profiles in the self-hosted version, real user accounts in the hosted version later.
 * Owns the {@code users} table.
 * <p>
 * Public API: the {@code dto} and {@code service} packages, marked with {@code @NamedInterface}.
 * {@code entity}, {@code repository} and {@code service.impl} are internal to the module.
 */
@NullMarked
package com.jobwarden.identity;

import org.jspecify.annotations.NullMarked;
