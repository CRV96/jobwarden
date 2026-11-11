package com.jobwarden.jobs.dto;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.jobwarden.jobs.enums.SalaryPeriod;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * The profile's offers side by side, every salary in one currency and per one period.
 *
 * @param offers the jobs at Offer, the latest changed first
 * @param currencies the currencies the comparison can be in, in alphabetical order
 * @param ratesDate the day of the European Central Bank's exchange rates it used, or {@code null} when they couldn't
 * be loaded and approximate rates stood in
 */
@Builder
public record OfferComparison(String currency, SalaryPeriod period, List<ComparedOffer> offers, List<String> currencies,
		@Nullable LocalDate ratesDate) {

	public OfferComparison {
		offers = List.copyOf(offers);
		currencies = List.copyOf(currencies);
	}

	/**
	 * @return the offer that pays the most, by the middle of its range, when two or more have a salary to compare: all
	 * of them gross, all net, or none saying, as a gross salary and a net one aren't the same amount
	 */
	public Optional<ComparedOffer> best() {
		List<ComparedOffer> paid = this.offers.stream().filter(offer -> offer.salary() != null).toList();
		long bases = paid.stream().map(offer -> Optional.ofNullable(offer.salary().basis())).distinct().count();
		return (paid.size() < 2 || bases > 1) ? Optional.empty()
				: paid.stream().max(Comparator.comparing(offer -> offer.salary().midpoint()));
	}

	public boolean isEmpty() {
		return this.offers.isEmpty();
	}

}
