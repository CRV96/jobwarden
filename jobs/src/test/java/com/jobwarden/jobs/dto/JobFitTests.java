package com.jobwarden.jobs.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jobwarden.jobs.enums.FitVerdict;
import com.jobwarden.jobs.enums.RequirementImportance;
import com.jobwarden.jobs.enums.RequirementMatch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JobFitTests {

	@Test
	void scoresFromOneToFiveByWhatTheCvMeets() {
		assertThat(JobFit.score(List.of(must(RequirementMatch.MET), nice(RequirementMatch.MET)))).isEqualByComparingTo("5.0");
		// A must-have weighs three times a plus, a partial match half: 1 + 4 x (3 + 0.5) / 4
		assertThat(JobFit.score(List.of(must(RequirementMatch.MET), nice(RequirementMatch.PARTIAL))))
			.isEqualByComparingTo("4.5");
		assertThat(JobFit.score(List.of(nice(RequirementMatch.MISSING), nice(RequirementMatch.MISSING))))
			.isEqualByComparingTo("1.0");
	}

	@Test
	void keepsAJobMissingAMustHaveFromAStrongFit() {
		// 1 + 4 x 9 / 12 would be 4.0
		BigDecimal score = JobFit.score(List.of(must(RequirementMatch.MET), must(RequirementMatch.MET),
				must(RequirementMatch.MET), must(RequirementMatch.MISSING)));

		assertThat(score).isEqualByComparingTo("3.0");
		assertThat(FitVerdict.of(score)).isEqualTo(FitVerdict.PARTIAL);
	}

	@Test
	void needsRequirementsToScore() {
		assertThatIllegalArgumentException().isThrownBy(() -> JobFit.score(List.of()));
	}

	@Test
	void saysWhatAScoreMeans() {
		assertThat(FitVerdict.of(new BigDecimal("4.0"))).isEqualTo(FitVerdict.STRONG);
		assertThat(FitVerdict.of(new BigDecimal("3.9"))).isEqualTo(FitVerdict.PARTIAL);
		assertThat(FitVerdict.of(new BigDecimal("2.9"))).isEqualTo(FitVerdict.WEAK);
	}

	private static FitRequirement must(RequirementMatch match) {
		return new FitRequirement("Java", RequirementImportance.MUST, match, null);
	}

	private static FitRequirement nice(RequirementMatch match) {
		return new FitRequirement("Kafka", RequirementImportance.NICE, match, null);
	}

}
