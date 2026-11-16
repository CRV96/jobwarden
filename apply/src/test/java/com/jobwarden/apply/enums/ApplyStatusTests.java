package com.jobwarden.apply.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyStatusTests {

	@Test
	void aJobStillQueuedOrBeingAppliedToIsOpenAndCantBeRetried() {
		assertThat(ApplyStatus.values()).allSatisfy(status -> assertThat(status.isOpen() && status.canRetry()).isFalse());
		assertThat(ApplyStatus.QUEUED.isOpen()).isTrue();
		assertThat(ApplyStatus.APPLYING.isOpen()).isTrue();
	}

	@Test
	void whatDidntGetThroughCanBeRetriedWhatDidCant() {
		assertThat(ApplyStatus.FAILED.canRetry()).isTrue();
		assertThat(ApplyStatus.NEEDS_ANSWER.canRetry()).isTrue();
		assertThat(ApplyStatus.APPLIED.canRetry()).isFalse();
		assertThat(ApplyStatus.FILLED.canRetry()).isFalse();
	}

	@Test
	void eachResultTheAssistantReportsEndsTheApplying() {
		assertThat(ApplyStatus.RESULTS.stream().filter(ApplyStatus::isOpen).toList()).isEmpty();
	}

}
