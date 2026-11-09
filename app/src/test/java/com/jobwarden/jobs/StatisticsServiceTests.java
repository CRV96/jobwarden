package com.jobwarden.jobs;

import java.time.LocalDate;
import java.util.List;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.jobs.dto.FitRequirement;
import com.jobwarden.jobs.dto.JobStatistics;
import com.jobwarden.jobs.dto.JobStatistics.ReasonCount;
import com.jobwarden.jobs.dto.JobStatistics.ReplyRate;
import com.jobwarden.jobs.dto.JobStatistics.SkillGap;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.AppliedBy;
import com.jobwarden.jobs.enums.RejectionReason;
import com.jobwarden.jobs.enums.RequirementImportance;
import com.jobwarden.jobs.enums.RequirementMatch;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.JobFitService;
import com.jobwarden.jobs.service.StatisticsService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class StatisticsServiceTests {

	private static final LocalDate APPLIED_ON = LocalDate.of(2026, 10, 1);

	@Autowired
	private StatisticsService statistics;

	@Autowired
	private ApplicationService applications;

	@Autowired
	private JobFitService fits;

	@Autowired
	private JdbcClient jdbc;

	private long userId;

	@BeforeEach
	void createProfile() {
		this.jdbc.sql("TRUNCATE users CASCADE").update();
		this.userId = this.jdbc.sql("INSERT INTO users (name) VALUES ('Robert') RETURNING id").query(Long.class).single();
	}

	@Test
	void countsHowOftenTheApplicationsGotAnAnswerAndAnInterview() {
		// Rejected after 4 days: a strong fit, 1 + 4 x (3 + 0 + 1) / 5 = 4.2, Kubernetes only a plus it missed
		long rejected = applied("https://www.linkedin.com/jobs/view/1", AppliedBy.YOU);
		event(rejected, 4, ApplicationStatus.REJECTED, RejectionReason.POSITION_FILLED);
		score(rejected, RequirementMatch.MET, "Kubernetes", RequirementImportance.NICE);
		// An interview after 10 days, then an offer: a weak fit, 1 + 4 x (1.5 + 0 + 1) / 7 = 2.4
		long offered = applied("https://www.linkedin.com/jobs/view/2", AppliedBy.CLAUDE);
		event(offered, 10, ApplicationStatus.INTERVIEWING, null);
		event(offered, 20, ApplicationStatus.OFFER, null);
		score(offered, RequirementMatch.PARTIAL, "kubernetes.", RequirementImportance.MUST);
		// No answer: neither a no-response nor a "rejection" for no response is one
		long silent = applied("https://jobs.example.com/3", AppliedBy.EXTENSION);
		event(silent, 30, ApplicationStatus.NO_RESPONSE, null);
		long ghosted = applied(null, null);
		event(ghosted, 30, ApplicationStatus.REJECTED, RejectionReason.NO_RESPONSE);
		// Saved, not applied to
		this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title("Saved")
			.initialStatus(ApplicationStatus.SAVED)
			.initialStatusDate(APPLIED_ON)
			.build());

		JobStatistics found = this.statistics.find(this.userId);

		assertThat(found).satisfies(stats -> {
			assertThat(stats.jobs()).isEqualTo(5);
			assertThat(stats.sent()).isEqualTo(4);
			assertThat(stats.replied()).isEqualTo(2);
			assertThat(stats.interviewed()).isEqualTo(1);
			assertThat(stats.offered()).isEqualTo(1);
			assertThat(stats.replyPercent()).isEqualTo(50);
			// 4 and 10 days
			assertThat(stats.medianDaysToReply()).isEqualTo(7);
		});
		String linkedIn = source(rejected);
		assertThat(found.bySite()).extracting(ReplyRate::key, ReplyRate::sent, ReplyRate::replied, ReplyRate::interviewed)
			.startsWith(tuple(linkedIn, 2, 2, 1))
			.contains(tuple(source(silent), 1, 0, 0), tuple(null, 1, 0, 0));
		// Applied by no one in particular: the profile
		assertThat(found.byMethod()).extracting(ReplyRate::key, ReplyRate::sent)
			.containsExactlyInAnyOrder(tuple("YOU", 2), tuple("CLAUDE", 1), tuple("EXTENSION", 1));
		// The strong fit first, then the weak one, then those with no score
		assertThat(found.byFit()).extracting(ReplyRate::key, ReplyRate::sent, ReplyRate::interviewed)
			.containsExactly(tuple("STRONG", 1, 0), tuple("WEAK", 1, 1), tuple(null, 2, 0));
		assertThat(found.rejectionReasons()).extracting(ReasonCount::reason, ReasonCount::count)
			.containsExactlyInAnyOrder(tuple("POSITION_FILLED", 1), tuple("NO_RESPONSE", 1));
		// Missing in both, written two ways, a must-have in one: one gap; the partly met Java after it
		assertThat(found.skillGaps()).extracting(SkillGap::requirement, SkillGap::missing, SkillGap::partly, SkillGap::mustHave)
			.containsExactly(tuple("Kubernetes", 2, 0, true), tuple("5+ years of Java", 0, 1, true));
	}

	@Test
	void countsNothingForAProfileWithNoJobs() {
		assertThat(this.statistics.find(this.userId)).satisfies(stats -> {
			assertThat(stats.sent()).isZero();
			assertThat(stats.replyPercent()).isZero();
			assertThat(stats.medianDaysToReply()).isNull();
			assertThat(stats.bySite()).isEmpty();
			assertThat(stats.skillGaps()).isEmpty();
		});
	}

	private long applied(@Nullable String link, @Nullable AppliedBy by) {
		return this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title("Backend Engineer")
			.company("Acme")
			.link(link)
			.initialStatus(ApplicationStatus.APPLIED)
			.initialStatusDate(APPLIED_ON)
			.appliedBy(by)
			.build());
	}

	private void event(long job, int daysAfter, ApplicationStatus status, @Nullable RejectionReason reason) {
		this.applications.addEvent(this.userId, job, NewTimelineEvent.builder()
			.eventDate(APPLIED_ON.plusDays(daysAfter))
			.stageCategory(status)
			.rejectionReason(reason)
			.build());
	}

	/** Java as matched, Kubernetes missing as written, English met. */
	private void score(long job, RequirementMatch java, String kubernetes, RequirementImportance kubernetesImportance) {
		this.fits.save(this.userId, job, null, List.of(
				new FitRequirement("5+ years of Java", RequirementImportance.MUST, java, null),
				new FitRequirement(kubernetes, kubernetesImportance, RequirementMatch.MISSING, null),
				new FitRequirement("English", RequirementImportance.NICE, RequirementMatch.MET, null)));
	}

	private @Nullable String source(long job) {
		return this.applications.findById(this.userId, job).orElseThrow().source();
	}

}
