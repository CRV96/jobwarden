package com.jobwarden;

import com.jobwarden.common.logging.LogContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.MDC;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.support.ScheduledTaskObservationContext;

/**
 * Scheduling, for what the modules do now and then: checking the mailboxes and the watched job boards, deleting the
 * reminders that are over. Spring observes each run of a {@code @Scheduled} method: the {@code tasks.scheduled.execution}
 * metric times it, with its outcome, and {@link #logContextForScheduledRuns} gives it an id of its own in the logs, like
 * a request's ({@link LogContext}).
 * <p>
 * The observation, rather than a decorator on the scheduler: with virtual threads, the scheduler runs fixed-delay tasks
 * on its own thread, where a decorator never sees them.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration {

	@Bean
	ObservationHandler<ScheduledTaskObservationContext> logContextForScheduledRuns() {
		return new ObservationHandler<>() {

			@Override
			public void onStart(ScheduledTaskObservationContext context) {
				MDC.put(LogContext.ID, LogContext.newId());
			}

			@Override
			public void onStop(ScheduledTaskObservationContext context) {
				MDC.remove(LogContext.ID);
			}

			@Override
			public boolean supportsContext(Observation.Context context) {
				return context instanceof ScheduledTaskObservationContext;
			}

		};
	}

}
