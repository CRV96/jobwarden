package com.jobwarden.web.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.jobs.dto.ComparedOffer;
import com.jobwarden.market.dto.MarketCountry;
import com.jobwarden.market.dto.MarketFigure;
import com.jobwarden.market.dto.MarketSalaries;
import com.jobwarden.search.Regions;
import org.jspecify.annotations.Nullable;

/**
 * What the market pays the role in an offer's country, beside the offer on the offers page: the first source that has
 * the country (the Stack Overflow survey, then Adzuna's job ads, then Eurostat), and how far the offer is from its
 * middle figure.
 *
 * @param country the ISO 3166 code of the country the job's location names
 * @param figure the source's figure, in the offers page's currency and period
 * @param percentFromMiddle how much more (or less, below zero) the offer pays than the figure's middle, in whole percent;
 * {@code null} without a salary to compare
 */
public record OfferMarket(String country, MarketFigure figure, @Nullable Integer percentFromMiddle) {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	/**
	 * @return the country the location names, when it names exactly one: a job in two countries, or only "Remote",
	 * has none to compare with
	 */
	public static Optional<String> countryOf(@Nullable String location) {
		Set<String> countries = Regions.countriesIn(location);
		return (countries.size() == 1) ? Optional.of(countries.iterator().next()) : Optional.empty();
	}

	/**
	 * @return the market beside the offer, when the market has its country
	 */
	public static Optional<OfferMarket> of(ComparedOffer offer, MarketSalaries market) {
		return countryOf(offer.application().location()).flatMap(code -> market.countries()
			.stream()
			.filter(country -> country.country().equals(code))
			.findFirst()
			.map(MarketCountry::figures)
			.filter(figures -> !figures.isEmpty())
			.map(figures -> new OfferMarket(code, figures.getFirst(), percent(offer, figures.getFirst()))));
	}

	/**
	 * @return where the offer stands: below the middle half, in it, or above it; {@code null} without a salary, or
	 * when the source has only its middle figure
	 */
	public @Nullable String band(@Nullable BigDecimal salary) {
		if (salary == null || !this.figure.hasRange()) {
			return null;
		}
		if (salary.compareTo(this.figure.lowerQuartile()) < 0) {
			return "BELOW";
		}
		return (salary.compareTo(this.figure.upperQuartile()) > 0) ? "ABOVE" : "MIDDLE";
	}

	private static @Nullable Integer percent(ComparedOffer offer, MarketFigure figure) {
		if (offer.salary() == null || figure.middle().signum() <= 0) {
			return null;
		}
		return offer.salary()
			.midpoint()
			.subtract(figure.middle())
			.multiply(HUNDRED)
			.divide(figure.middle(), 0, RoundingMode.HALF_UP)
			.intValue();
	}

}
