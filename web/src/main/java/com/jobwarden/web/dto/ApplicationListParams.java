package com.jobwarden.web.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.Sort;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * What the application list shows, from the query parameters of its address, e.g.
 * {@code /applications?q=java&status=APPLIED&sort=TITLE&dir=ASC&page=2}. A parameter left out, or one out of range,
 * means its default. The list's links (a status, a column to sort by, another page) are built from it, so each keeps
 * everything else.
 *
 * @param q only applications whose title or company contains this text
 * @param sort {@link ApplicationSort#ADDED} by default
 * @param dir newest first by default, otherwise A to Z
 * @param page from 1, as the list shows it
 * @param size one of {@link #SIZES}
 */
public record ApplicationListParams(
		@Nullable String q,
		@Nullable ApplicationStatus status,
		ApplicationSort sort,
		Sort.Direction dir,
		Integer page,
		Integer size) {

	/** The page sizes the list offers. */
	public static final List<Integer> SIZES = List.of(20, 50, 100);

	private static final int DEFAULT_SIZE = 20;

	/** How many page numbers the list shows around the current page, on each side, before it leaves a gap. */
	private static final int PAGES_AROUND = 2;

	private static final Set<Integer> ALLOWED_SIZES = Set.copyOf(SIZES);

	/**
	 * Spring binds the query parameters through this constructor, with {@code null} for each one that's missing, so
	 * it puts the defaults in: none of {@code sort}, {@code dir}, {@code page} and {@code size} is {@code null} once
	 * it's done. (They're objects, not {@code int}s, for that: Spring won't bind a missing value to an {@code int}.)
	 */
	public ApplicationListParams {
		q = FormValues.blankToNull(q);
		if (sort == null) {
			sort = ApplicationSort.ADDED;
		}
		if (dir == null) {
			dir = defaultDirection(sort);
		}
		if (page == null || page < 1) {
			page = 1;
		}
		if (size == null || !ALLOWED_SIZES.contains(size)) {
			size = DEFAULT_SIZE;
		}
	}

	/**
	 * The list as it first opens: everything, newest first.
	 */
	public static ApplicationListParams defaults() {
		return new ApplicationListParams(null, null, ApplicationSort.ADDED, Sort.Direction.DESC, 1, DEFAULT_SIZE);
	}

	public ApplicationQuery toQuery() {
		return ApplicationQuery.builder()
			.search(this.q)
			.status(this.status)
			.sort(this.sort)
			.direction(this.dir)
			.page(this.page - 1)
			.size(this.size)
			.build();
	}

	/**
	 * The list's address, with only the parameters that aren't their default.
	 */
	public String url() {
		UriComponentsBuilder url = UriComponentsBuilder.fromPath(ControllerConstants.APPLICATIONS);
		Map<String, Object> values = new HashMap<>();
		if (this.q != null) {
			// As a variable, which encode() encodes strictly: in the text itself, a + would come back as a space
			url.queryParam("q", "{q}");
			values.put("q", this.q);
		}
		if (this.status != null) {
			url.queryParam("status", this.status);
		}
		if (this.sort != ApplicationSort.ADDED || this.dir != defaultDirection(this.sort)) {
			url.queryParam("sort", this.sort).queryParam("dir", this.dir);
		}
		if (this.page > 1) {
			url.queryParam("page", this.page);
		}
		if (this.size.intValue() != DEFAULT_SIZE) {
			url.queryParam("size", this.size);
		}
		return url.encode().buildAndExpand(values).toUriString();
	}

	/**
	 * Only applications with this status, or all of them for {@code null}; from the first page.
	 */
	public ApplicationListParams withStatus(@Nullable ApplicationStatus status) {
		return new ApplicationListParams(this.q, status, this.sort, this.dir, 1, this.size);
	}

	/**
	 * Sorted by this column; if the list already is, the other way round. From the first page.
	 */
	public ApplicationListParams sortedBy(ApplicationSort sort) {
		Sort.Direction direction = (sort != this.sort) ? defaultDirection(sort)
				: this.dir.isAscending() ? Sort.Direction.DESC : Sort.Direction.ASC;
		return new ApplicationListParams(this.q, this.status, sort, direction, 1, this.size);
	}

	public boolean isSortedBy(ApplicationSort sort) {
		return this.sort == sort;
	}

	/**
	 * Whether the list shows what the user asked for, not everything: a search or a status filter.
	 */
	public boolean isFiltered() {
		return this.q != null || this.status != null;
	}

	/**
	 * {@link #SIZES}, for the template, which can't read a static field.
	 */
	public List<Integer> sizes() {
		return SIZES;
	}

	public ApplicationListParams withPage(int page) {
		return new ApplicationListParams(this.q, this.status, this.sort, this.dir, page, this.size);
	}

	/**
	 * The page numbers to link to, out of the given total: the first and the last, and those around this page.
	 * {@code null} stands for a gap, where the numbers in between are left out.
	 */
	public List<@Nullable Integer> pageNumbers(int totalPages) {
		List<@Nullable Integer> numbers = new ArrayList<>();
		for (int number = 1; number <= totalPages; number++) {
			boolean shown = number == 1 || number == totalPages || Math.abs(number - this.page) <= PAGES_AROUND;
			if (shown) {
				numbers.add(number);
			} else if (numbers.getLast() != null) {
				numbers.add(null);
			}
		}
		return numbers;
	}

	/**
	 * The date added goes newest first; text columns and statuses start from the top.
	 */
	private static Sort.Direction defaultDirection(ApplicationSort sort) {
		return (sort == ApplicationSort.ADDED) ? Sort.Direction.DESC : Sort.Direction.ASC;
	}

}
