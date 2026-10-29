package com.jobwarden.market.service;

import java.util.List;

import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.market.dto.MarketQuery;
import com.jobwarden.market.dto.MarketSalaries;
import com.jobwarden.market.dto.SurveyStatus;

/**
 * What positions pay across countries, from public sources. Not a profile's data: the same for everyone.
 */
public interface MarketSalaryService {

	/**
	 * @return the roles to pick from: the survey's, the most answered first, or common ones before it's imported
	 */
	List<String> roles();

	/**
	 * @return the programming languages to pick from, the most answered first, or common ones before it's imported
	 */
	List<String> skills();

	/**
	 * @return the countries the survey has enough answers for, as ISO 3166 two-letter codes, the most answers first
	 */
	List<String> surveyCountries();

	SurveyStatus surveyStatus();

	/**
	 * Downloads the Stack Overflow survey's answers and replaces the figures from them, in the background.
	 *
	 * @return whether it started; not when an import is already running
	 */
	boolean importSurvey();

	/**
	 * Asks the sources for each country: the survey's figures, the job ads' salaries (Adzuna, for the countries it
	 * covers, when the app has a key) and Eurostat's (for the countries of the European Economic Area).
	 *
	 * @param currency an ISO 4217 code with an exchange rate
	 * @param period what to show the salaries per; the sources' figures are a year
	 */
	MarketSalaries find(MarketQuery query, String currency, SalaryPeriod period);

}
