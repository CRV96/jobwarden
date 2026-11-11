package com.jobwarden.jobs.service;

import java.util.Optional;

import com.jobwarden.jobs.dto.JobOffer;
import com.jobwarden.jobs.dto.NegotiationBrief;
import com.jobwarden.jobs.dto.OfferComparison;
import com.jobwarden.jobs.enums.SalaryPeriod;
import org.jspecify.annotations.Nullable;

/**
 * The offers the profile's jobs made it: their terms, typed in on each job's page, compared side by side on the offers
 * page; and the brief Claude writes to negotiate one. Every method takes the owning user's id, like
 * {@link ApplicationService}.
 */
public interface OfferService {

	/**
	 * @return the job's offer terms, or {@link JobOffer#NONE} when none were typed in
	 */
	JobOffer find(long userId, long applicationId);

	/**
	 * Keeps the terms, replacing what the job had. Blank text is no value.
	 *
	 * @throws com.jobwarden.jobs.exception.ApplicationNotFoundException if the profile has no such job
	 * @return the terms as kept, with when
	 */
	JobOffer save(long userId, long applicationId, JobOffer offer);

	/**
	 * The jobs at Offer, side by side: each with its terms, and its salary (the offer's base salary, or else the
	 * posting's) in the currency and per the period asked for, so they compare.
	 *
	 * @param currency an ISO 4217 code; {@code null}, or one there's no exchange rate for, for euros
	 */
	OfferComparison compare(long userId, @Nullable String currency, SalaryPeriod period);

	Optional<NegotiationBrief> findBrief(long userId, long applicationId);

	/**
	 * Keeps the brief, replacing the one the job had.
	 *
	 * @throws com.jobwarden.jobs.exception.ApplicationNotFoundException if the profile has no such job
	 * @return the brief as kept, with when
	 */
	NegotiationBrief saveBrief(long userId, long applicationId, NegotiationBrief brief);

	void deleteBrief(long userId, long applicationId);

}
