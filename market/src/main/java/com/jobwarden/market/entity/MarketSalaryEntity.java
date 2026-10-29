package com.jobwarden.market.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.jobwarden.market.enums.MarketSource;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * What a source says a role with a skill pays in a country, a year in the source's currency. An empty country, role
 * or skill is all of them.
 */
@Entity
@Table(name = "market_salaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketSalaryEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	private MarketSource source;

	private int surveyYear;

	private String country;

	private String role;

	private String skill;

	private int sampleSize;

	private BigDecimal lowerQuartile;

	private BigDecimal median;

	private BigDecimal upperQuartile;

	private String currency;

	@CreationTimestamp
	private Instant importedAt;

	public MarketSalaryEntity(MarketSource source, int surveyYear, String country, String role, String skill,
			int sampleSize, BigDecimal lowerQuartile, BigDecimal median, BigDecimal upperQuartile, String currency) {
		this.source = source;
		this.surveyYear = surveyYear;
		this.country = country;
		this.role = role;
		this.skill = skill;
		this.sampleSize = sampleSize;
		this.lowerQuartile = lowerQuartile;
		this.median = median;
		this.upperQuartile = upperQuartile;
		this.currency = currency;
	}

}
