package com.jobwarden.apply.service;

import java.util.Collection;
import java.util.List;

import com.jobwarden.apply.dto.ApplicationAnswers;
import com.jobwarden.apply.dto.ApplyItem;
import com.jobwarden.apply.enums.ApplyMode;
import com.jobwarden.apply.enums.ApplyStatus;
import org.jspecify.annotations.Nullable;

/**
 * Every method takes the owning user's id, so one profile can never read or change another's answers or queue.
 */
public interface AutoApplyService {

	ApplicationAnswers findAnswers(long userId);

	/**
	 * Replaces the answers. Blank text is no answer, and custom answers without a question or an answer are left out.
	 */
	ApplicationAnswers saveAnswers(long userId, ApplicationAnswers answers);

	/**
	 * Keeps an answer to a question a form asked, among the profile's own answers, for the next forms: in place of
	 * the one it had to the same question, whatever its case and spacing.
	 *
	 * @throws IllegalArgumentException if the question or the answer is blank
	 */
	ApplicationAnswers learn(long userId, String question, String answer);

	/**
	 * Queues the profile's jobs for Claude, each once: a job already waiting, without a posting to apply on, or no
	 * longer Saved is left out.
	 *
	 * @return the jobs queued
	 */
	List<ApplyItem> queue(long userId, Collection<Long> applicationIds, ApplyMode mode);

	/**
	 * @return the jobs queued, the latest first
	 */
	List<ApplyItem> findItems(long userId, int limit);

	/**
	 * @return the jobs Claude still has to do, the oldest first
	 */
	List<ApplyItem> findOpenItems(long userId);

	/**
	 * Claude picked the job up: it's {@link ApplyStatus#APPLYING} from now on.
	 *
	 * @throws com.jobwarden.apply.exception.ApplyItemNotFoundException if the profile has no such item
	 * @throws com.jobwarden.common.exception.BusinessRuleException if it's no longer waiting
	 */
	ApplyItem start(long userId, long itemId);

	/**
	 * What Claude did. {@link ApplyStatus#APPLIED} moves a Saved job to Applied, by Claude, with the note on its
	 * timeline; a job the profile moved on meanwhile stays where it is. A filled-in form (REVIEW) can still be
	 * reported applied, once the profile sent it.
	 *
	 * @param result one of {@link ApplyStatus#RESULTS}
	 * @param question what the form asked that the answers don't say, for {@link ApplyStatus#NEEDS_ANSWER}
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the result isn't one, or the job is done
	 */
	ApplyItem report(long userId, long itemId, ApplyStatus result, @Nullable String site, @Nullable String note,
			@Nullable String question);

	/**
	 * The profile sent a form Claude filled in: like {@link #report} with {@link ApplyStatus#APPLIED}.
	 */
	ApplyItem markSent(long userId, long itemId);

	/**
	 * Takes the job back, unless it was applied to.
	 */
	ApplyItem cancel(long userId, long itemId);

	/**
	 * Answers the question a form asked for the job, among the profile's own answers, for the next forms too, and
	 * queues the job again.
	 *
	 * @throws com.jobwarden.common.exception.BusinessRuleException unless the job is waiting for an answer
	 */
	ApplyItem answer(long userId, long itemId, String answer);

	/**
	 * Queues the job again, e.g. once the question it needed is answered.
	 *
	 * @throws com.jobwarden.common.exception.BusinessRuleException unless Claude stopped short of applying
	 */
	ApplyItem retry(long userId, long itemId);

}
