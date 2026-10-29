package com.jobwarden.apply;

import java.time.LocalDate;
import java.util.List;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.apply.dto.ApplicationAnswers;
import com.jobwarden.apply.dto.ApplyItem;
import com.jobwarden.apply.dto.CustomAnswer;
import com.jobwarden.apply.enums.ApplyMode;
import com.jobwarden.apply.enums.ApplyStatus;
import com.jobwarden.apply.enums.CoverLetter;
import com.jobwarden.apply.exception.ApplyErrorCode;
import com.jobwarden.apply.exception.ApplyItemNotFoundException;
import com.jobwarden.apply.service.AutoApplyService;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.enums.AppliedBy;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The apply module starts here with the jobs module, whose jobs it applies to, and without identity, so the profiles
 * are inserted with SQL.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class AutoApplyServiceTests {

	@Autowired
	private AutoApplyService autoApply;

	@Autowired
	private ApplicationService applications;

	@Autowired
	private JdbcClient jdbc;

	private long userId;

	private long otherUserId;

	@BeforeEach
	void createProfiles() {
		this.jdbc.sql("TRUNCATE users CASCADE").update();
		this.userId = insertProfile("Robert");
		this.otherUserId = insertProfile("Someone else");
	}

	@Test
	void keepsTheAnswersTidied() {
		assertThat(this.autoApply.findAnswers(this.userId)).isEqualTo(ApplicationAnswers.NONE);

		this.autoApply.saveAnswers(this.userId, ApplicationAnswers.builder()
			.workAuthorization(" EU citizen ")
			.needsSponsorship(false)
			.noticePeriod("")
			.salaryExpectation("5,500 EUR gross a month")
			.yearsOfExperience(8)
			.coverLetter(CoverLetter.NONE)
			.declineVoluntary(true)
			.custom(List.of(new CustomAnswer(" Driving licence? ", " Yes, B "), new CustomAnswer("No answer", " ")))
			.build());

		ApplicationAnswers saved = this.autoApply.findAnswers(this.userId);
		assertThat(saved.workAuthorization()).isEqualTo("EU citizen");
		assertThat(saved.needsSponsorship()).isFalse();
		assertThat(saved.willingToRelocate()).isNull();
		assertThat(saved.noticePeriod()).isNull();
		assertThat(saved.yearsOfExperience()).isEqualTo(8);
		assertThat(saved.coverLetter()).isEqualTo(CoverLetter.NONE);
		assertThat(saved.custom()).containsExactly(new CustomAnswer("Driving licence?", "Yes, B"));
		assertThat(saved.updatedAt()).isNotNull();
		assertThat(this.autoApply.findAnswers(this.otherUserId)).isEqualTo(ApplicationAnswers.NONE);
	}

	@Test
	void queuesOnlySavedJobsWithAPostingOnce() {
		long saved = job("Backend engineer", "https://jobs.lever.co/acme/1", ApplicationStatus.SAVED);
		long withoutLink = job("Data engineer", null, ApplicationStatus.SAVED);
		long applied = job("QA engineer", "https://jobs.lever.co/acme/3", ApplicationStatus.APPLIED);
		long someoneElses = jobOf(this.otherUserId, "Designer", "https://jobs.lever.co/acme/4");

		List<ApplyItem> queued = this.autoApply.queue(this.userId, List.of(saved, withoutLink, applied, someoneElses),
				ApplyMode.SUBMIT);
		List<ApplyItem> again = this.autoApply.queue(this.userId, List.of(saved), ApplyMode.REVIEW);

		assertThat(queued).singleElement().satisfies(item -> {
			assertThat(item.applicationId()).isEqualTo(saved);
			assertThat(item.title()).isEqualTo("Backend engineer");
			assertThat(item.link()).isEqualTo("https://jobs.lever.co/acme/1");
			assertThat(item.mode()).isEqualTo(ApplyMode.SUBMIT);
			assertThat(item.status()).isEqualTo(ApplyStatus.QUEUED);
		});
		assertThat(again).isEmpty();
		assertThat(this.autoApply.findOpenItems(this.userId)).hasSize(1);
		assertThat(this.autoApply.findOpenItems(this.otherUserId)).isEmpty();
	}

	@Test
	void appliedMovesTheJobToAppliedByClaude() {
		long job = job("Backend engineer", "https://jobs.lever.co/acme/1", ApplicationStatus.SAVED);
		ApplyItem item = this.autoApply.queue(this.userId, List.of(job), ApplyMode.SUBMIT).getFirst();

		assertThat(this.autoApply.start(this.userId, item.id()).status()).isEqualTo(ApplyStatus.APPLYING);
		ApplyItem done = this.autoApply.report(this.userId, item.id(), ApplyStatus.APPLIED, "Lever",
				"CV uploaded, 6 questions answered.", "ignored");

		assertThat(done.status()).isEqualTo(ApplyStatus.APPLIED);
		assertThat(done.site()).isEqualTo("Lever");
		assertThat(done.question()).isNull();
		assertThat(done.finishedAt()).isNotNull();
		ApplicationDetails applied = this.applications.findById(this.userId, job).orElseThrow();
		assertThat(applied.status()).isEqualTo(ApplicationStatus.APPLIED);
		assertThat(applied.appliedBy()).isEqualTo(AppliedBy.CLAUDE);
		assertThat(applied.timeline().getLast().note()).isEqualTo("Lever: CV uploaded, 6 questions answered.");
		assertThat(this.autoApply.findOpenItems(this.userId)).isEmpty();

		// Done is done
		assertThatThrownBy(() -> this.autoApply.report(this.userId, item.id(), ApplyStatus.FAILED, null, "again", null))
			.isInstanceOfSatisfying(BusinessRuleException.class,
					refused -> assertThat(refused.getErrorCode()).isEqualTo(ApplyErrorCode.APPLY_ITEM_FINISHED));
		assertThatThrownBy(() -> this.autoApply.cancel(this.userId, item.id())).isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void aFormFilledInIsAppliedOnceTheProfileSendsIt() {
		long job = job("Backend engineer", "https://jobs.lever.co/acme/1", ApplicationStatus.SAVED);
		ApplyItem item = this.autoApply.queue(this.userId, List.of(job), ApplyMode.REVIEW).getFirst();

		this.autoApply.report(this.userId, item.id(), ApplyStatus.FILLED, "Greenhouse", "Ready in the second tab.", null);
		assertThat(this.applications.findById(this.userId, job).orElseThrow().status()).isEqualTo(ApplicationStatus.SAVED);
		// Not queued again while the profile has it to send
		assertThat(this.autoApply.queue(this.userId, List.of(job), ApplyMode.REVIEW)).isEmpty();

		assertThat(this.autoApply.markSent(this.userId, item.id()).status()).isEqualTo(ApplyStatus.APPLIED);
		assertThat(this.applications.findById(this.userId, job).orElseThrow())
			.extracting(ApplicationDetails::status, ApplicationDetails::appliedBy)
			.containsExactly(ApplicationStatus.APPLIED, AppliedBy.CLAUDE);
	}

	@Test
	void learnsAnAnswerInPlaceOfTheOneToTheSameQuestion() {
		this.autoApply.saveAnswers(this.userId, ApplicationAnswers.builder()
			.noticePeriod("1 month")
			.custom(List.of(new CustomAnswer("Are you willing to work weekends?", "No"),
					new CustomAnswer("Driving licence?", "Yes, B")))
			.build());

		ApplicationAnswers answers = this.autoApply.learn(this.userId, "  are you willing to   work weekends? ",
				" Now and then ");

		assertThat(answers.noticePeriod()).isEqualTo("1 month");
		assertThat(answers.custom()).containsExactly(new CustomAnswer("Driving licence?", "Yes, B"),
				new CustomAnswer("are you willing to work weekends?", "Now and then"));
		assertThat(this.autoApply.findAnswers(this.otherUserId)).isEqualTo(ApplicationAnswers.NONE);
		assertThatThrownBy(() -> this.autoApply.learn(this.userId, "Driving licence?", " "))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aQuestionAnsweredIsKeptAndTheJobQueuedAgain() {
		long job = job("Backend engineer", "https://jobs.lever.co/acme/1", ApplicationStatus.SAVED);
		ApplyItem item = this.autoApply.queue(this.userId, List.of(job), ApplyMode.SUBMIT).getFirst();
		this.autoApply.start(this.userId, item.id());

		ApplyItem waiting = this.autoApply.report(this.userId, item.id(), ApplyStatus.NEEDS_ANSWER, "Workday", null,
				"Are you willing to work weekends?");
		assertThat(waiting.question()).isEqualTo("Are you willing to work weekends?");
		assertThat(this.autoApply.findOpenItems(this.userId)).isEmpty();
		assertThat(this.autoApply.queue(this.userId, List.of(job), ApplyMode.SUBMIT)).isEmpty();

		ApplyItem requeued = this.autoApply.answer(this.userId, item.id(), "Now and then");

		assertThat(requeued.status()).isEqualTo(ApplyStatus.QUEUED);
		assertThat(requeued.question()).isNull();
		assertThat(this.autoApply.findAnswers(this.userId).custom())
			.containsExactly(new CustomAnswer("Are you willing to work weekends?", "Now and then"));
		assertThatThrownBy(() -> this.autoApply.answer(this.userId, item.id(), "Again")).isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void skippedOrCancelledJobsCanBeTriedAgain() {
		long job = job("Backend engineer", "https://www.linkedin.com/jobs/view/1", ApplicationStatus.SAVED);
		ApplyItem item = this.autoApply.queue(this.userId, List.of(job), ApplyMode.SUBMIT).getFirst();

		this.autoApply.report(this.userId, item.id(), ApplyStatus.SKIPPED, "LinkedIn", "Easy Apply only.", null);
		assertThat(this.autoApply.retry(this.userId, item.id()).status()).isEqualTo(ApplyStatus.QUEUED);
		assertThat(this.autoApply.cancel(this.userId, item.id()).status()).isEqualTo(ApplyStatus.CANCELLED);
		assertThat(this.autoApply.findItems(this.userId, 10))
			.extracting(ApplyItem::status, ApplyItem::site, ApplyItem::note)
			.containsExactly(tuple(ApplyStatus.CANCELLED, "LinkedIn", "Easy Apply only."));

		assertThatExceptionOfType(ApplyItemNotFoundException.class)
			.isThrownBy(() -> this.autoApply.retry(this.otherUserId, item.id()));
		assertThatThrownBy(() -> this.autoApply.report(this.userId, item.id(), ApplyStatus.QUEUED, null, null, null))
			.isInstanceOfSatisfying(BusinessRuleException.class,
					refused -> assertThat(refused.getErrorCode()).isEqualTo(ApplyErrorCode.NOT_A_RESULT));
	}

	@Test
	void leavesAJobTheProfileAppliedToMeanwhile() {
		long job = job("Backend engineer", "https://jobs.lever.co/acme/1", ApplicationStatus.SAVED);
		ApplyItem item = this.autoApply.queue(this.userId, List.of(job), ApplyMode.SUBMIT).getFirst();
		this.applications.addEvent(this.userId, job, NewTimelineEvent.builder()
			.eventDate(LocalDate.now())
			.stageCategory(ApplicationStatus.INTERVIEWING)
			.build());

		this.autoApply.report(this.userId, item.id(), ApplyStatus.APPLIED, "Lever", null, null);

		assertThat(this.applications.findById(this.userId, job).orElseThrow().status())
			.isEqualTo(ApplicationStatus.INTERVIEWING);
	}

	private long job(String title, @Nullable String link, ApplicationStatus status) {
		return this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title(title)
			.company("Acme")
			.link(link)
			.initialStatus(status)
			.initialStatusDate(LocalDate.now())
			.build());
	}

	private long jobOf(long userId, String title, String link) {
		return this.applications.create(NewApplication.builder()
			.userId(userId)
			.title(title)
			.link(link)
			.initialStatus(ApplicationStatus.SAVED)
			.initialStatusDate(LocalDate.now())
			.build());
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id").param(name).query(Long.class).single();
	}

}
