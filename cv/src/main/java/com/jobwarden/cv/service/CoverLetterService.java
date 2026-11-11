package com.jobwarden.cv.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.cv.dto.BaseLetter;
import com.jobwarden.cv.dto.CoverLetter;
import com.jobwarden.cv.dto.OutreachDraft;
import org.jspecify.annotations.Nullable;

/**
 * What the profile sends about a job, besides its CV: a cover letter, and messages to the people who hire for it (a
 * LinkedIn note, an email). Claude writes them; the profile changes them and sends them itself: JobWarden never sends
 * anything. Every method takes the owning user's id, like {@link CvService}; a job's id is one the caller made sure is
 * the profile's.
 */
public interface CoverLetterService {

	Optional<CoverLetter> findLetter(long userId, long applicationId);

	/**
	 * Keeps the letter Claude wrote, replacing the one the job had, unless the profile changed that one after asking
	 * Claude: then it stays. Checked and written at once, so a change the profile saves meanwhile is never lost.
	 *
	 * @param askedAt when the profile asked Claude for the letter; {@code null} when it didn't, e.g. Auto-apply writing
	 * one for a job without
	 * @throws com.jobwarden.common.exception.BusinessRuleException if it's empty or longer than
	 * {@link CoverLetter#BODY_MAX_LENGTH}, or the letter changed after {@code askedAt}
	 */
	CoverLetter saveLetter(long userId, long applicationId, String body, @Nullable String notes, @Nullable Instant askedAt);

	/**
	 * Keeps the letter as the profile changed it, or wrote it without Claude: Claude's notes stay.
	 *
	 * @throws com.jobwarden.common.exception.BusinessRuleException if it's empty or too long
	 */
	CoverLetter changeLetter(long userId, long applicationId, String body);

	void deleteLetter(long userId, long applicationId);

	/**
	 * @return which of the jobs have a cover letter
	 */
	Set<Long> findJobsWithLetter(long userId, Collection<Long> applicationIds);

	/**
	 * @return the cover letters of those of the jobs that have one, by the job's id
	 */
	Map<Long, CoverLetter> findLetters(long userId, Collection<Long> applicationIds);

	/**
	 * @return the profile's base letter, that each job's letter starts from, if it wrote one
	 */
	Optional<BaseLetter> findBaseLetter(long userId);

	/**
	 * Keeps the profile's base letter, replacing the one it had.
	 *
	 * @throws com.jobwarden.common.exception.BusinessRuleException if it's empty or longer than
	 * {@link CoverLetter#BODY_MAX_LENGTH}
	 */
	BaseLetter saveBaseLetter(long userId, String body);

	void deleteBaseLetter(long userId);

	/**
	 * @return the messages about the job, to each person, in the order they were first written
	 */
	List<OutreachDraft> findDrafts(long userId, long applicationId);

	/**
	 * @return how many people the messages about each of the jobs are to, for those that have any, by the job's id
	 */
	Map<Long, Integer> countDrafts(long userId, Collection<Long> applicationIds);

	/**
	 * Keeps the messages Claude wrote to someone about a job: they replace those to the same person (the same profile
	 * link, or else the same name), unless the profile changed those after asking Claude, or are added. Blank text is
	 * no value. Checked and written at once, like {@link #saveLetter}.
	 *
	 * @param askedAt when the profile asked Claude for them, or {@code null}
	 * @throws com.jobwarden.common.exception.BusinessRuleException if it says to no one, the LinkedIn note is longer
	 * than {@link OutreachDraft#LINKEDIN_NOTE_MAX_LENGTH}, or the messages to the person changed after {@code askedAt}
	 */
	OutreachDraft saveDraft(long userId, OutreachDraft draft, @Nullable Instant askedAt);

	/**
	 * Keeps the messages as the profile changed them.
	 *
	 * @throws com.jobwarden.cv.exception.OutreachDraftNotFoundException if the profile has no such draft
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the LinkedIn note is too long
	 */
	OutreachDraft changeDraft(long userId, long draftId, @Nullable String linkedinNote, @Nullable String emailSubject,
			@Nullable String emailBody);

	void deleteDraft(long userId, long draftId);

}
