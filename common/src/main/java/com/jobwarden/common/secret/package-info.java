/**
 * Encrypting what the server must keep but never as typed: mailbox passwords, GitHub tokens. Part of the module's
 * public API.
 */
@NamedInterface("secret")
@NullMarked
package com.jobwarden.common.secret;

import org.jspecify.annotations.NullMarked;

import org.springframework.modulith.NamedInterface;
