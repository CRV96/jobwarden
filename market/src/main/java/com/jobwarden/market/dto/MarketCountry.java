package com.jobwarden.market.dto;

import java.util.List;
import java.util.Optional;

import com.jobwarden.market.enums.MarketSource;

/**
 * What the sources say a position pays in one country.
 *
 * @param country an ISO 3166 two-letter code; empty for every country together
 * @param figures one per source that has the country, in the order of {@link MarketSource}
 */
public record MarketCountry(String country, List<MarketFigure> figures) {

	public MarketCountry {
		figures = List.copyOf(figures);
	}

	public Optional<MarketFigure> from(MarketSource source) {
		return this.figures.stream().filter(figure -> figure.source() == source).findFirst();
	}

	public boolean isWorld() {
		return this.country.isEmpty();
	}

}
