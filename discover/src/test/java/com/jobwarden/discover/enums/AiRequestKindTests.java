package com.jobwarden.discover.enums;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRequestKindTests {

	@Test
	void whatFindsJobsHasRequirements() {
		assertThat(Arrays.stream(AiRequestKind.values()).filter(AiRequestKind::findsJobs))
			.isNotEmpty()
			.allMatch(AiRequestKind::hasRequirements);
		assertThat(AiRequestKind.FIND_RECRUITERS.hasRequirements()).isTrue();
		assertThat(AiRequestKind.FIND_HIRING_POSTS.hasRequirements()).isTrue();
	}

	@Test
	void aRequestIsAboutAJobOrFindsJobsNeverBoth() {
		assertThat(AiRequestKind.values())
			.noneMatch(kind -> kind.isAboutAJob() && kind.findsJobs());
		assertThat(AiRequestKind.TAILOR_CV.isAboutAJob()).isTrue();
		assertThat(AiRequestKind.WRITE_COVER_LETTER.isAboutAJob()).isTrue();
		assertThat(AiRequestKind.FIND_JOBS.isAboutAJob()).isFalse();
	}

}
