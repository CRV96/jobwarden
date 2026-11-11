package com.jobwarden.cv;

import java.util.List;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.CoverLetter;
import com.jobwarden.cv.dto.OutreachDraft;
import com.jobwarden.cv.dto.Story;
import com.jobwarden.cv.exception.CvErrorCode;
import com.jobwarden.cv.exception.OutreachDraftNotFoundException;
import com.jobwarden.cv.exception.StoryNotFoundException;
import com.jobwarden.cv.service.CoverLetterService;
import com.jobwarden.cv.service.StoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The cv module starts here without identity and jobs, so the profiles and their jobs are inserted with SQL, like in
 * {@link CvServiceTests}.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class CoverLetterAndStoryServiceTests {

	@Autowired
	private CoverLetterService letters;

	@Autowired
	private StoryService stories;

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
	void keepsTheLetterClaudeWroteAndTheProfilesChangesToIt() {
		long job = insertJob(this.userId, "Backend engineer");

		CoverLetter written = this.letters.saveLetter(this.userId, job,
				" Dear hiring team,\n\nI build payment systems.\n \nKind regards, ", "Led with Kafka.");
		assertThat(written.body()).isEqualTo("Dear hiring team,\n\nI build payment systems.\n \nKind regards,");
		assertThat(written.paragraphs()).containsExactly("Dear hiring team,", "I build payment systems.", "Kind regards,");

		CoverLetter changed = this.letters.changeLetter(this.userId, job, "Dear Ana,\n\nKind regards,");
		assertThat(changed.body()).isEqualTo("Dear Ana,\n\nKind regards,");
		// Claude's notes stay
		assertThat(changed.notes()).isEqualTo("Led with Kafka.");
		assertThat(this.letters.findJobsWithLetter(this.userId, List.of(job, job + 1))).containsExactly(job);
		assertThat(this.letters.findLetter(this.otherUserId, job)).isEmpty();
	}

	@Test
	void writesALetterWithoutClaudeAndRefusesAnEmptyOrTooLongOne() {
		long job = insertJob(this.userId, "Backend engineer");

		assertThat(this.letters.changeLetter(this.userId, job, "Dear Ana,").notes()).isNull();
		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(() -> this.letters.changeLetter(this.userId, job, " "))
			.satisfies(refused -> assertThat(refused.getErrorCode()).isEqualTo(CvErrorCode.COVER_LETTER_EMPTY));
		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.letters.saveLetter(this.userId, job, "x".repeat(CoverLetter.BODY_MAX_LENGTH + 1), null))
			.satisfies(refused -> assertThat(refused.getErrorCode()).isEqualTo(CvErrorCode.COVER_LETTER_TOO_LONG));

		this.letters.deleteLetter(this.otherUserId, job);
		assertThat(this.letters.findLetter(this.userId, job)).isPresent();
		this.letters.deleteLetter(this.userId, job);
		assertThat(this.letters.findLetter(this.userId, job)).isEmpty();
	}

	@Test
	void keepsOneDraftPerPersonAndAJobsNotesWithinLinkedInsLimit() {
		long job = insertJob(this.userId, "Backend engineer");
		OutreachDraft toAna = OutreachDraft.builder()
			.applicationId(job)
			.recipientName("Ana Pop")
			.recipientLink("https://www.linkedin.com/in/anapop")
			.linkedinNote("Hi Ana!")
			.emailBody(" ")
			.build();

		OutreachDraft first = this.letters.saveDraft(this.userId, toAna);
		this.letters.saveDraft(this.userId, toAna.toBuilder().recipientName("Ana Pop-Ionescu").linkedinNote("Hello Ana!").build());
		this.letters.saveDraft(this.userId, OutreachDraft.builder().applicationId(job).recipientName("Hiring team").build());

		List<OutreachDraft> drafts = this.letters.findDrafts(this.userId, job);
		assertThat(drafts).extracting(OutreachDraft::recipientName).containsExactly("Ana Pop-Ionescu", "Hiring team");
		assertThat(drafts.getFirst().id()).isEqualTo(first.id());
		assertThat(drafts.getFirst().linkedinNote()).isEqualTo("Hello Ana!");
		assertThat(drafts.getFirst().emailBody()).isNull();

		// 300 letters are fine, however many bytes they take; 301 aren't
		this.letters.changeDraft(this.userId, first.id(), "ă".repeat(300), "Subject", "Body");
		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.letters.changeDraft(this.userId, first.id(), "x".repeat(301), null, null))
			.satisfies(refused -> assertThat(refused.getErrorCode()).isEqualTo(CvErrorCode.LINKEDIN_NOTE_TOO_LONG));
		assertThatExceptionOfType(OutreachDraftNotFoundException.class)
			.isThrownBy(() -> this.letters.changeDraft(this.otherUserId, first.id(), "Hi", null, null));
		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(() -> this.letters.saveDraft(this.userId,
				OutreachDraft.builder().applicationId(job).recipientName(" ").build()));

		this.letters.deleteDraft(this.userId, first.id());
		assertThat(this.letters.findDrafts(this.userId, job)).hasSize(1);
	}

	@Test
	void keepsTheStoriesTidiedAndInTheirOrder() {
		Story saved = this.stories.save(this.userId, Story.builder()
			.title(" The Kafka migration ")
			.situation("Orders were lost at peaks.")
			.result(" ")
			.skills(List.of("Kafka", " Kafka ", "", "leadership"))
			.build());
		Story second = this.stories.save(this.userId, Story.builder().title("Halving checkout latency").build());

		assertThat(saved.title()).isEqualTo("The Kafka migration");
		assertThat(saved.result()).isNull();
		assertThat(saved.skills()).containsExactly("Kafka", "leadership");
		assertThat(this.stories.findAll(this.userId)).extracting(Story::id).containsExactly(saved.id(), second.id());
		// In the order a brief names them, the ones gone or another profile's left out
		assertThat(this.stories.findAll(this.userId, List.of(second.id(), 999_999L, saved.id()))).extracting(Story::id)
			.containsExactly(second.id(), saved.id());
		assertThat(this.stories.findAll(this.otherUserId, List.of(saved.id()))).isEmpty();

		Story changed = this.stories.save(this.userId, saved.toBuilder().result("No order lost since.").build());
		assertThat(changed.id()).isEqualTo(saved.id());
		assertThat(this.stories.find(this.userId, saved.id()).result()).isEqualTo("No order lost since.");
		assertThatExceptionOfType(StoryNotFoundException.class)
			.isThrownBy(() -> this.stories.save(this.otherUserId, saved.toBuilder().title("Mine").build()));
		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.stories.save(this.userId, Story.builder().title(" ").build()))
			.satisfies(refused -> assertThat(refused.getErrorCode()).isEqualTo(CvErrorCode.STORY_WITHOUT_TITLE));

		this.stories.delete(this.userId, second.id());
		assertThat(this.stories.findAll(this.userId)).hasSize(1);
	}

	@Test
	void addsTheStoriesClaudeDraftedButThoseTheBankHasAndThoseOverItsSize() {
		this.stories.save(this.userId, Story.builder().title("The Kafka migration").build());

		List<Story> added = this.stories.addDrafted(this.userId, List.of(
				Story.builder().title("the kafka migration ").action("Moved the queue.").build(),
				Story.builder().title("Halving checkout latency").result("[By how much?]").build(),
				Story.builder().title("Halving checkout latency").build()));

		assertThat(added).extracting(Story::title).containsExactly("Halving checkout latency");
		assertThat(added.getFirst().needsFinishing()).isTrue();
		assertThat(this.stories.findAll(this.userId)).hasSize(2);

		for (int i = 2; i < Story.MAX_STORIES; i++) {
			this.stories.save(this.userId, Story.builder().title("Story " + i).build());
		}
		assertThat(this.stories.addDrafted(this.userId, List.of(Story.builder().title("One more").build()))).isEmpty();
		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.stories.save(this.userId, Story.builder().title("One more").build()))
			.satisfies(refused -> assertThat(refused.getErrorCode()).isEqualTo(CvErrorCode.TOO_MANY_STORIES));
	}

	private long insertJob(long userId, String title) {
		return this.jdbc.sql("""
				INSERT INTO applications (user_id, title, current_status, captured_at) VALUES (?, ?, 'SAVED', now())
				RETURNING id""").param(userId).param(title).query(Long.class).single();
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id").param(name).query(Long.class).single();
	}

}
