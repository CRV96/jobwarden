package com.jobwarden.jobs.service.impl;

/**
 * Where the rates that convert salaries come from.
 */
interface ExchangeRates {

	RateTable current();

}
