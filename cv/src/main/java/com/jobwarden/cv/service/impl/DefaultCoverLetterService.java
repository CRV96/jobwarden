package com.jobwarden.cv.service.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.CoverLetter;
import com.jobwarden.cv.dto.OutreachDraft;
import com.jobwarden.cv.entity.CoverLetterEntity;
import com.jobwarden.cv.entity.OutreachDraftEntity;
import com.jobwarden.cv.exception.CvErrorCode;
import com.jobwarden.cv.exception.OutreachDraftNotFoundException;
import com.jobwarden.cv.repository.CoverLetterRepository;
import com.jobwarden.cv.repository.OutreachDraftRepository;
import com.jobwarden.cv.service.CoverLetterService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@CustomLog
@RequiredArgsConstructor
class DefaultCoverLetterService implements CoverLetterService {

	private final CoverLetterRepository letters;

	private final OutreachDraftRepository drafts;

	@Override
	public Optional<CoverLetter> findLetter(long userId, long applicationId) {
		return this.letters.findByUserIdAndApplicationId(userId, applicationId).map(DefaultCoverLetterService::toLetter);
	}

	@Override
	@Transactional
	public CoverLetter saveLetter(long userId, long applicationId, String body, @Nullable String notes) {
		CoverLetterEntity entity = this.letters.findByUserIdAndApplicationId(userId, applicationId)
			.orElseGet(() -> new CoverLetterEntity(userId, applicationId));
		entity.replace(checkedBody(body), text(notes));
		CoverLetter saved = toLetter(this.letters.saveAndFlush(entity));
		log.info("Saved the cover letter Claude wrote for application {} of profile {}", applicationId, userId);
		return saved;
	}

	@Override
	@Transactional
	public CoverLetter changeLetter(long userId, long applicationId, String body) {
		String checked = checkedBody(body);
		CoverLetterEntity entity = this.letters.findByUserIdAndApplicationId(userId, applicationId).orElseGet(() -> {
			CoverLetterEntity written = new CoverLetterEntity(userId, applicationId);
			written.replace(checked, null);
			return written;
		});
		entity.changeBody(checked);
		CoverLetter saved = toLetter(this.letters.saveAndFlush(entity));
		log.info("Saved the cover letter of application {} of profile {}", applicationId, userId);
		return saved;
	}

	@Override
	@Transactional
	public void deleteLetter(long userId, long applicationId) {
		if (this.letters.deleteOwned(userId, applicationId) > 0) {
			log.info("Deleted the cover letter of application {} of profile {}", applicationId, userId);
		}
	}

	@Override
	public Set<Long> findJobsWithLetter(long userId, Collection<Long> applicationIds) {
		return applicationIds.isEmpty() ? Set.of()
				: new HashSet<>(this.letters.findApplicationIdsWithLetter(userId, applicationIds));
	}

	@Override
	public List<OutreachDraft> findDrafts(long userId, long applicationId) {
		return this.drafts.findByUserIdAndApplicationIdOrderByIdAsc(userId, applicationId)
			.stream()
			.map(DefaultCoverLetterService::toDraft)
			.toList();
	}

	@Override
	@Transactional
	public OutreachDraft saveDraft(long userId, OutreachDraft draft) {
		OutreachDraft tidied = tidied(draft);
		if (tidied.recipientName() == null || tidied.recipientName().isBlank()) {
			throw new BusinessRuleException(CvErrorCode.OUTREACH_WITHOUT_RECIPIENT, "The draft says whom to no one");
		}
		checkNote(tidied.linkedinNote());
		OutreachDraftEntity entity = this.drafts.findByUserIdAndApplicationIdOrderByIdAsc(userId, draft.applicationId())
			.stream()
			.filter(kept -> samePerson(kept, tidied))
			.findFirst()
			.orElseGet(() -> new OutreachDraftEntity(userId, draft.applicationId()));
		entity.replace(tidied);
		OutreachDraft saved = toDraft(this.drafts.saveAndFlush(entity));
		log.info("Saved messages to someone about application {} of profile {}", draft.applicationId(), userId);
		return saved;
	}

	@Override
	@Transactional
	public OutreachDraft changeDraft(long userId, long draftId, @Nullable String linkedinNote,
			@Nullable String emailSubject, @Nullable String emailBody) {
		OutreachDraftEntity entity = this.drafts.findByIdAndUserId(draftId, userId)
			.orElseThrow(() -> new OutreachDraftNotFoundException(draftId));
		String note = text(linkedinNote);
		checkNote(note);
		entity.changeMessages(note, text(emailSubject), text(emailBody));
		log.info("Saved the messages of draft {} of profile {}", draftId, userId);
		return toDraft(this.drafts.saveAndFlush(entity));
	}

	@Override
	@Transactional
	public void deleteDraft(long userId, long draftId) {
		if (this.drafts.deleteOwned(userId, draftId) > 0) {
			log.info("Deleted the outreach draft {} of profile {}", draftId, userId);
		}
	}

	/** The same profile link, or without links, the same name. */
	private static boolean samePerson(OutreachDraftEntity kept, OutreachDraft draft) {
		if (kept.getRecipientLink() != null && draft.recipientLink() != null) {
			return kept.getRecipientLink().equalsIgnoreCase(draft.recipientLink());
		}
		return kept.getRecipientName().toLowerCase(Locale.ROOT).equals(draft.recipientName().toLowerCase(Locale.ROOT));
	}

	private static String checkedBody(String body) {
		String letter = text(body);
		if (letter == null) {
			throw new BusinessRuleException(CvErrorCode.COVER_LETTER_EMPTY, "The cover letter is empty");
		}
		if (letter.length() > CoverLetter.BODY_MAX_LENGTH) {
			throw new BusinessRuleException(CvErrorCode.COVER_LETTER_TOO_LONG,
					"The cover letter has " + letter.length() + " characters", String.valueOf(CoverLetter.BODY_MAX_LENGTH));
		}
		return letter;
	}

	private static void checkNote(@Nullable String note) {
		int length = (note == null) ? 0 : note.codePointCount(0, note.length());
		if (length > OutreachDraft.LINKEDIN_NOTE_MAX_LENGTH) {
			throw new BusinessRuleException(CvErrorCode.LINKEDIN_NOTE_TOO_LONG, "The LinkedIn note has " + length
					+ " characters", String.valueOf(OutreachDraft.LINKEDIN_NOTE_MAX_LENGTH), String.valueOf(length));
		}
	}

	private static OutreachDraft tidied(OutreachDraft draft) {
		return draft.toBuilder()
			.recipientName(text(draft.recipientName()))
			.recipientRole(text(draft.recipientRole()))
			.recipientLink(text(draft.recipientLink()))
			.recipientEmail(text(draft.recipientEmail()))
			.linkedinNote(text(draft.linkedinNote()))
			.emailSubject(text(draft.emailSubject()))
			.emailBody(text(draft.emailBody()))
			.build();
	}

	private static @Nullable String text(@Nullable String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

	private static CoverLetter toLetter(CoverLetterEntity letter) {
		return new CoverLetter(letter.getApplicationId(), letter.getBody(), letter.getNotes(), letter.getUpdatedAt());
	}

	private static OutreachDraft toDraft(OutreachDraftEntity draft) {
		return OutreachDraft.builder()
			.id(draft.getId())
			.applicationId(draft.getApplicationId())
			.recipientName(draft.getRecipientName())
			.recipientRole(draft.getRecipientRole())
			.recipientLink(draft.getRecipientLink())
			.recipientEmail(draft.getRecipientEmail())
			.linkedinNote(draft.getLinkedinNote())
			.emailSubject(draft.getEmailSubject())
			.emailBody(draft.getEmailBody())
			.updatedAt(draft.getUpdatedAt())
			.build();
	}

}
