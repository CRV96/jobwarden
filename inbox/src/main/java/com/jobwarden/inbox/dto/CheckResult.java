package com.jobwarden.inbox.dto;

/**
 * What reading the mailbox did.
 *
 * @param read how many emails it read: the new ones since the last time
 * @param added how many of them became updates
 */
public record CheckResult(int read, int added) {
}
