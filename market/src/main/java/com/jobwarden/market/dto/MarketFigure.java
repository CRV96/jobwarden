package com.jobwarden.market.dto;

import java.math.BigDecimal;

import com.jobwarden.market.enums.MarketSource;
import com.jobwarden.market.enums.MarketStatistic;
import org.jspecify.annotations.Nullable;

/**
 * What a source says a position pays in a country, in the currency and per the period asked for.
 *
 * @param year the year the figures are for
 * @param lowerQuartile a quarter earn less; {@code null} when the source has only the middle figure
 * @param middle the median or the mean ({@link #statistic()})
 * @param upperQuartile a quarter earn more; {@code null} when the source has only the middle figure
 * @param sampleSize how many answers or job ads it's from; {@code null} when the source doesn't say
 */
public record MarketFigure(MarketSource source, int year, @Nullable BigDecimal lowerQuartile, BigDecimal middle,
		@Nullable BigDecimal upperQuartile, @Nullable Integer sampleSize) {

	public MarketStatistic statistic() {
		return this.source.statistic();
	}

	/**
	 * @return whether it has the range of the middle half of the salaries, not only the middle figure
	 */
	public boolean hasRange() {
		return this.lowerQuartile != null && this.upperQuartile != null;
	}

	/**
	 * @return the highest figure it has
	 */
	public BigDecimal highest() {
		return (this.upperQuartile != null) ? this.upperQuartile.max(this.middle) : this.middle;
	}

}
