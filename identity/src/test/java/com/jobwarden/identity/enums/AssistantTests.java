package com.jobwarden.identity.enums;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantTests {

	@Test
	void onlyClaudeCodeAppliesInTheBrowser() {
		assertThat(Arrays.stream(Assistant.values()).filter(Assistant::appliesInBrowser)).containsExactly(Assistant.CLAUDE_CODE);
	}

	@Test
	void eachHasItsOwnNameCommandAndApp() {
		assertThat(Assistant.values()).allSatisfy(assistant -> {
			assertThat(assistant.product()).isNotBlank();
			assertThat(assistant.command()).isNotBlank().doesNotContain(" ");
			assertThat(assistant.app()).isNotBlank();
		});
		assertThat(Arrays.stream(Assistant.values()).map(Assistant::command).distinct()).hasSize(Assistant.values().length);
	}

}
