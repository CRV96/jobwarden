package com.jobwarden.jobs.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import com.jobwarden.jobs.enums.FitVerdict;
import com.jobwarden.jobs.enums.RequirementImportance;
import com.jobwarden.jobs.enums.RequirementMatch;
import org.jspecify.annotations.Nullable;

/**
 * How well the profile fits a job: its score, from 1 to 5, and the posting's requirements it comes from.
 *
 * @param summary why, in a sentence or two: what makes it a fit, and what doesn't
 */
public record JobFit(BigDecimal score, @Nullable String summary, List<FitRequirement> requirements,
		@Nullable Instant updatedAt) {

	/** A missing must-have keeps the score at this, at best: never a strong fit. */
	public static final BigDecimal MISSING_MUST_CAP = BigDecimal.valueOf(3);

	public JobFit {
		requirements = List.copyOf(requirements);
	}

	public FitVerdict verdict() {
		return FitVerdict.of(this.score);
	}

	/**
	 * The score of these requirements, the same way every time, so that scores compare across jobs: 1 plus 4 times how
	 * much of the requirements the CV meets, a must-have weighing three times a nice-to-have and a partial match half.
	 * A missing must-have caps it at 3.0.
	 *
	 * @throws IllegalArgumentException with no requirements: nothing to score
	 */
	public static BigDecimal score(List<FitRequirement> requirements) {
		if (requirements.isEmpty()) {
			throw new IllegalArgumentException("A fit needs the posting's requirements");
		}
		double weights = 0;
		double met = 0;
		boolean mustMissing = false;
		for (FitRequirement requirement : requirements) {
			weights += requirement.importance().weight();
			met += requirement.importance().weight() * requirement.match().value();
			mustMissing |= requirement.importance() == RequirementImportance.MUST
					&& requirement.match() == RequirementMatch.MISSING;
		}
		BigDecimal score = BigDecimal.valueOf(1 + 4 * met / weights).setScale(1, RoundingMode.HALF_UP);
		return mustMissing ? score.min(MISSING_MUST_CAP.setScale(1)) : score;
	}

}
