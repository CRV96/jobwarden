package com.jobwarden.jobs.entity;

import java.math.BigDecimal;

import com.jobwarden.jobs.dto.OfferExtra;
import com.jobwarden.jobs.enums.OfferExtraKind;
import com.jobwarden.jobs.enums.PayFrequency;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * One of an offer's extras, a row of job_offer_extras: see {@link OfferExtra}.
 */
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class OfferExtraValue {

	@Enumerated(EnumType.STRING)
	private OfferExtraKind kind;

	private @Nullable BigDecimal amount;

	private @Nullable String unit;

	@Enumerated(EnumType.STRING)
	private PayFrequency frequency;

	private @Nullable String note;

	OfferExtraValue(OfferExtra extra) {
		this.kind = extra.kind();
		this.amount = extra.amount();
		this.unit = extra.unit();
		this.frequency = extra.frequency();
		this.note = extra.note();
	}

	public OfferExtra toExtra() {
		return new OfferExtra(this.kind, this.amount, this.unit, this.frequency, this.note);
	}

}
