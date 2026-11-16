package com.jobwarden.web.dto;

import com.jobwarden.inbox.enums.InboxAutomation;
import com.jobwarden.web.enums.ReadingInterval;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Settings' inbox section: how often the mailbox is read on its own, and what becomes of the updates found. Both are
 * picked from what the page offers, so a missing one is a broken request, not a typing error.
 */
public record InboxSettingsForm(@NotNull @Nullable ReadingInterval readEvery, @NotNull @Nullable InboxAutomation automation) {
}
