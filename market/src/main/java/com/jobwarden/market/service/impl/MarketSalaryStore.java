package com.jobwarden.market.service.impl;

import java.util.List;

import com.jobwarden.market.entity.MarketSalaryEntity;
import com.jobwarden.market.enums.MarketSource;
import com.jobwarden.market.repository.MarketSalaryRepository;
import com.jobwarden.market.service.impl.StackOverflowSurvey.SurveyFigure;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces a source's figures in one transaction, so the page never shows half of an import.
 */
@Component
@CustomLog
@RequiredArgsConstructor
class MarketSalaryStore {

	private final MarketSalaryRepository salaries;

	@Transactional
	public void replaceSurvey(int year, List<SurveyFigure> figures) {
		int deleted = this.salaries.deleteBySource(MarketSource.STACK_OVERFLOW);
		this.salaries.saveAll(figures.stream()
			.map(figure -> new MarketSalaryEntity(MarketSource.STACK_OVERFLOW, year, figure.country(), figure.role(),
					figure.skill(), figure.sampleSize(), figure.lowerQuartile(), figure.median(), figure.upperQuartile(),
					StackOverflowSurvey.CURRENCY))
			.toList());
		log.info("Replaced {} figures of the {} Stack Overflow survey with {}", deleted, year, figures.size());
	}

}
