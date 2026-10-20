package com.jobwarden.jobs.repository;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.entity.ApplicationEntity;
import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.enums.ApplicationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

/**
 * Built with the Criteria API, because the filters and the order change with each request: a fixed JPQL query would
 * need a variant for each combination.
 */
class ApplicationQueriesImpl implements ApplicationQueries {

	private static final char LIKE_ESCAPE = '\\';

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public Page<ApplicationSummary> findSummaries(long userId, ApplicationQuery query) {
		CriteriaBuilder builder = this.entityManager.getCriteriaBuilder();

		CriteriaQuery<ApplicationSummary> rows = builder.createQuery(ApplicationSummary.class);
		Root<ApplicationEntity> application = rows.from(ApplicationEntity.class);
		rows.select(builder.construct(ApplicationSummary.class, application.get("id"), application.get("title"),
				application.get("company"), application.get("location"), application.get("salary"),
				application.get("source"), application.get("currentStatus"), application.get("capturedAt")))
			.where(matching(builder, application, userId, query.search(), query.status()))
			.orderBy(order(builder, application, query));

		Pageable pageable = PageRequest.of(query.page(), query.size());
		List<ApplicationSummary> content = this.entityManager.createQuery(rows)
			.setFirstResult((int) pageable.getOffset())
			.setMaxResults(pageable.getPageSize())
			.getResultList();
		// Counts only when the page doesn't already tell the total, e.g. a first page that isn't full
		return PageableExecutionUtils.getPage(content, pageable, () -> count(userId, query));
	}

	@Override
	public Map<ApplicationStatus, Long> countByStatus(long userId, @Nullable String search) {
		CriteriaBuilder builder = this.entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = builder.createTupleQuery();
		Root<ApplicationEntity> application = query.from(ApplicationEntity.class);
		Path<ApplicationStatus> status = application.get("currentStatus");
		query.multiselect(status, builder.count(application))
			.where(matching(builder, application, userId, search, null))
			.groupBy(status);

		Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
		for (Tuple row : this.entityManager.createQuery(query).getResultList()) {
			counts.put(row.get(0, ApplicationStatus.class), row.get(1, Long.class));
		}
		return counts;
	}

	private long count(long userId, ApplicationQuery query) {
		CriteriaBuilder builder = this.entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> count = builder.createQuery(Long.class);
		Root<ApplicationEntity> application = count.from(ApplicationEntity.class);
		count.select(builder.count(application))
			.where(matching(builder, application, userId, query.search(), query.status()));
		return this.entityManager.createQuery(count).getSingleResult();
	}

	private static Predicate[] matching(CriteriaBuilder builder, Root<ApplicationEntity> application, long userId,
			@Nullable String search, @Nullable ApplicationStatus status) {
		List<Predicate> conditions = new ArrayList<>();
		conditions.add(builder.equal(application.get("userId"), userId));
		if (status != null) {
			conditions.add(builder.equal(application.get("currentStatus"), status));
		}
		if (search != null && !search.isBlank()) {
			// Lowered by the database on both sides, so both follow the same rules
			Expression<String> pattern = builder.lower(builder.literal("%" + escapeLike(search.strip()) + "%"));
			conditions.add(builder.or(builder.like(builder.lower(application.get("title")), pattern, LIKE_ESCAPE),
					builder.like(builder.lower(application.get("company")), pattern, LIKE_ESCAPE)));
		}
		return conditions.toArray(Predicate[]::new);
	}

	private static List<Order> order(CriteriaBuilder builder, Root<ApplicationEntity> application,
			ApplicationQuery query) {
		Expression<?> value = switch (query.sort()) {
			case ADDED -> application.get("capturedAt");
			case TITLE -> builder.lower(application.get("title"));
			case LOCATION -> builder.lower(application.get("location"));
			case SOURCE -> builder.lower(application.get("source"));
			case STATUS -> statusOrder(builder, application.get("currentStatus"));
		};
		Order byValue = query.direction().isAscending() ? builder.asc(value, Nulls.LAST)
				: builder.desc(value, Nulls.LAST);
		// Then newest first, and by id, so a page always holds the same rows
		return List.of(byValue, builder.desc(application.get("capturedAt")), builder.desc(application.get("id")));
	}

	/**
	 * The status's position in {@link ApplicationStatus}, for {@link ApplicationSort#STATUS}. The column holds the
	 * status's name, which would sort alphabetically.
	 */
	private static Expression<Integer> statusOrder(CriteriaBuilder builder, Path<ApplicationStatus> status) {
		CriteriaBuilder.SimpleCase<ApplicationStatus, Integer> position = builder.selectCase(status);
		for (ApplicationStatus value : ApplicationStatus.values()) {
			position = position.when(value, value.ordinal());
		}
		return position.otherwise(ApplicationStatus.values().length);
	}

	/**
	 * So that {@code %} and {@code _} in the search match themselves, not any text.
	 */
	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
