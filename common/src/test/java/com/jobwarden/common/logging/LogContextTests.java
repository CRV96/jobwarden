package com.jobwarden.common.logging;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class LogContextTests {

	@Test
	void anIdIsSixteenHexadecimalDigits() {
		assertThat(LogContext.newId()).matches("[0-9a-f]{16}").isNotEqualTo(LogContext.newId());
	}

	@Test
	void aTaskRunsWithAnIdOfItsOwnThatsGoneAfterwards() {
		AtomicReference<String> during = new AtomicReference<>();

		LogContext.withNewId(() -> during.set(MDC.get(LogContext.ID))).run();

		assertThat(during.get()).matches("[0-9a-f]{16}");
		assertThat(MDC.get(LogContext.ID)).isNull();
	}

}
