package com.jobwarden.web.dto;

import java.util.List;

import com.jobwarden.jobs.dto.ReminderTarget;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.ReminderLead;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * The reminders of one calendar entry, as its chips send them on every change: the ones ticked, the others off.
 *
 * @param job the entry's job
 * @param event the entry's event on the timeline, for an event
 * @param leads the reminders ticked; none when every one is off
 */
public record ReminderForm(@NotNull Long job, @NotNull CalendarEntryKind kind, @Nullable Long event,
		@Nullable List<ReminderLead> leads) {

	public ReminderTarget toTarget() {
		return new ReminderTarget(this.job, this.kind, (this.kind == CalendarEntryKind.EVENT) ? this.event : null);
	}

	public List<ReminderLead> toLeads() {
		return (this.leads == null) ? List.of() : this.leads;
	}

}
