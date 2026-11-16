package com.jobwarden.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketFormTests {

	@Test
	void takesTheDefaultsForFiltersLeftOutAndAllForEmptyOnes() {
		assertThat(new MarketForm(null, null, null).toQuery().role()).isEqualTo(MarketForm.DEFAULT_ROLE);
		assertThat(new MarketForm(" ", "", null).toQuery()).satisfies(query -> {
			assertThat(query.role()).isNull();
			assertThat(query.skill()).isNull();
		});
	}

	@Test
	void takesNoLongerARoleOrSkillThanTheMarketHas() {
		MarketForm form = new MarketForm("r".repeat(500), " " + "s".repeat(500), null);

		assertThat(form.selectedRole()).hasSize(MarketForm.MAX_FILTER_LENGTH);
		assertThat(form.selectedSkill()).hasSize(MarketForm.MAX_FILTER_LENGTH);
	}

}
