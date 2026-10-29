package com.jobwarden.market.enums;

/**
 * Where a market salary comes from.
 */
public enum MarketSource {

	/**
	 * The Stack Overflow Developer Survey: what developers say they earn in a year, salary, bonuses and perks before
	 * taxes, in US dollars. Yearly; ODbL 1.0, so it's credited wherever it shows.
	 */
	STACK_OVERFLOW(MarketStatistic.MEDIAN),

	/** Adzuna: the salaries that current job ads in a country give, for a search. Credited wherever it shows. */
	ADZUNA(MarketStatistic.MEDIAN),

	/**
	 * Eurostat's Structure of Earnings Survey: mean gross annual earnings of professionals in information and
	 * communication, every four years.
	 */
	EUROSTAT(MarketStatistic.MEAN);

	private final MarketStatistic statistic;

	MarketSource(MarketStatistic statistic) {
		this.statistic = statistic;
	}

	/**
	 * @return what the source's middle figure is
	 */
	public MarketStatistic statistic() {
		return this.statistic;
	}

}
