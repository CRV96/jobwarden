package com.jobwarden.discover.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.jobwarden.search.EmploymentType;
import com.jobwarden.search.Recency;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.search.WorkType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A search's filters, each list a text array: the column of its own makes each one plain to read in the database.
 */
@Entity
@Table(name = "saved_searches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedSearchEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private String name;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> titles = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> locations = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> skills = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> sites = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> workTypes = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> employmentTypes = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> excludes = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	private Recency recency;

	@CreationTimestamp
	private Instant createdAt;

	public SavedSearchEntity(long userId, String name, SearchCriteria criteria) {
		this.userId = userId;
		change(name, criteria);
	}

	public void change(String name, SearchCriteria criteria) {
		this.name = name;
		this.titles = new ArrayList<>(criteria.titles());
		this.locations = new ArrayList<>(criteria.locations());
		this.skills = new ArrayList<>(criteria.skills());
		this.sites = new ArrayList<>(criteria.sites());
		this.workTypes = names(criteria.workTypes());
		this.employmentTypes = names(criteria.employmentTypes());
		this.excludes = new ArrayList<>(criteria.excludes());
		this.recency = criteria.recency();
	}

	public SearchCriteria toCriteria() {
		return SearchCriteria.builder()
			.titles(this.titles)
			.locations(this.locations)
			.skills(this.skills)
			.sites(this.sites)
			.workTypes(constants(this.workTypes, WorkType.class))
			.employmentTypes(constants(this.employmentTypes, EmploymentType.class))
			.excludes(this.excludes)
			.recency(this.recency)
			.build();
	}

	private static List<String> names(Collection<? extends Enum<?>> values) {
		return new ArrayList<>(values.stream().map(Enum::name).toList());
	}

	private static <E extends Enum<E>> Set<E> constants(List<String> names, Class<E> type) {
		return Set.copyOf(names.stream().map(name -> Enum.valueOf(type, name)).toList());
	}

}
