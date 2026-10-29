package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jobwarden.jobs.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DefaultExchangeRateService implements ExchangeRateService {

	private final ExchangeRates exchangeRates;

	@Override
	public boolean knows(String currency) {
		return this.exchangeRates.current().currencies().contains(currency);
	}

	@Override
	public BigDecimal convert(BigDecimal amount, String from, String to) {
		RateTable rates = this.exchangeRates.current();
		if (!rates.currencies().contains(from) || !rates.currencies().contains(to)) {
			throw new IllegalArgumentException("No exchange rate between " + from + " and " + to);
		}
		return rates.convert(amount, from, to);
	}

	@Override
	public @Nullable LocalDate ratesDate() {
		return this.exchangeRates.current().date();
	}

}
