package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.JobStatistics;
import com.jobwarden.jobs.dto.JobStatistics.ReasonCount;
import com.jobwarden.jobs.dto.JobStatistics.ReplyRate;
import com.jobwarden.jobs.dto.JobStatistics.SkillGap;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.StatisticsService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(StatisticsController.class)
class StatisticsControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final JobStatistics STATISTICS = new JobStatistics(57, 40, 16, 5, 1, 9,
			List.of(new ReplyRate("linkedin.com", 30, 12, 4), new ReplyRate(null, 10, 4, 1)),
			List.of(new ReplyRate("YOU", 25, 10, 3), new ReplyRate("CLAUDE", 15, 6, 2)),
			List.of(new ReplyRate("STRONG", 10, 6, 4), new ReplyRate("PARTIAL", 8, 3, 1), new ReplyRate(null, 22, 7, 0)),
			List.of(new ReasonCount("POSITION_FILLED", 6), new ReasonCount("LACK_OF_EXPERIENCE", 2)),
			List.of(new SkillGap("Kubernetes", 7, 2, true), new SkillGap("Go", 1, 0, false)));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private StatisticsService statistics;

	@MockitoBean
	private CvService cvs;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@BeforeEach
	void selectRobert() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
	}

	@Test
	void showsHowTheApplicationsWent() {
		given(this.statistics.find(1)).willReturn(STATISTICS);
		given(this.cvs.findAllTailored(1)).willReturn(List.of(tailored("Kubernetes", "AWS"), tailored("kubernetes")));

		assertThat(this.mvc.get().uri("/statistics")).hasStatusOk()
			.hasViewName("statistics/statistics")
			.bodyText()
			.contains("Statistics", ">40<", "of 57 jobs saved", ">40%<", "16 answered", ">13%<", "9 days",
					// By site, the jobs added by hand among them
					"linkedin.com", "Added by hand", "12 of 30 answered, 4 got to an interview", "width: 40%",
					"You", "AI Assistant", "Strong fit", "Not scored",
					// 4 of 10 strong fits against 1 of 8 others
					"Yes: the jobs scored 4 or more got to an interview clearly more often.",
					"Kubernetes", "Missing in 7, partly in 2", "×2", "AWS", "Position filled", "Lack of experience");
	}

	@Test
	void saysThereIsNothingToCountYet() {
		given(this.statistics.find(1)).willReturn(new JobStatistics(3, 0, 0, 0, 0, null, List.of(), List.of(), List.of(),
				List.of(), List.of()));

		assertThat(this.mvc.get().uri("/statistics")).hasStatusOk()
			.bodyText()
			.contains("Nothing to count yet")
			.doesNotContain("Answers by site");
	}

	@Test
	void tellsWhetherTheScoresLedToInterviews() {
		assertThat(StatisticsController.calibration(List.of(new ReplyRate("STRONG", 10, 5, 5), new ReplyRate("WEAK", 10, 2, 1))))
			.isEqualTo("WORKS");
		assertThat(StatisticsController.calibration(List.of(new ReplyRate("STRONG", 10, 5, 1), new ReplyRate("PARTIAL", 5, 2, 3))))
			.isEqualTo("BACKWARDS");
		assertThat(StatisticsController.calibration(List.of(new ReplyRate("STRONG", 10, 5, 2), new ReplyRate("WEAK", 10, 2, 2))))
			.isEqualTo("FLAT");
		// Too few, and no score isn't a verdict
		assertThat(StatisticsController.calibration(List.of(new ReplyRate("STRONG", 2, 2, 2), new ReplyRate(null, 30, 5, 1))))
			.isEqualTo("FEW");
	}

	private static TailoredCv tailored(String... missing) {
		return new TailoredCv(7, CvDetails.NONE, List.of(), List.of(missing), null, Instant.now());
	}

}
