package com.jobwarden.web.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.service.ActivityService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.StatisticsService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.SearchFilters;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The homepage: how the job hunt goes lately and what waits for the profile. The applications sent and the jobs saved
 * today, this week and this month, and day by day over the last 30 days; the jobs by status; the latest entries on
 * their timelines; what to do: jobs to review, email updates, requests open for the assistant, follow-ups due, offers,
 * and the next days' interviews and deadlines; and the searches saved in the Search Builder, to run again or hand to the
 * assistant. Each links to where it's done. Worked out from the data, without AI.
 */
@Controller
@RequiredArgsConstructor
class HomeController extends BaseController {

	/** How many days ahead "Coming up" looks. */
	static final int COMING_UP_DAYS = 7;

	/** How many of them it lists. */
	static final int COMING_UP = 6;

	/** How many saved searches it lists, the latest first. */
	static final int SEARCHES = 5;

	private final ActivityService activity;

	private final ApplicationService applications;

	private final StatisticsService statistics;

	private final DiscoveryService discovery;

	private final Clock clock;

	@GetMapping(ControllerConstants.HOMEPAGE)
	String home(Model model) {
		User profile = requireProfile(model);
		long userId = profile.id();
		LocalDate today = LocalDate.now(this.clock);
		model.addAttribute(ModelAttributes.TODAY, today);
		model.addAttribute(ModelAttributes.ACTIVITY, this.activity.find(userId, today, this.clock.getZone()));
		model.addAttribute(ModelAttributes.STATISTICS, this.statistics.find(userId));
		// The jobs by status, as the pipeline orders them, those with none left out
		Map<ApplicationStatus, Long> counts = this.applications.countByStatus(userId, null, false);
		Map<ApplicationStatus, Long> byStatus = new LinkedHashMap<>();
		for (ApplicationStatus status : ApplicationStatus.values()) {
			long count = counts.getOrDefault(status, 0L);
			if (count > 0) {
				byStatus.put(status, count);
			}
		}
		model.addAttribute(ModelAttributes.JOBS_BY_STATUS, byStatus);
		model.addAttribute(ModelAttributes.MOST_IN_A_STATUS, byStatus.values().stream().mapToLong(Long::longValue).max().orElse(1));
		model.addAttribute(ModelAttributes.OFFER_COUNT, byStatus.getOrDefault(ApplicationStatus.OFFER, 0L));
		// What's ahead: interviews, the days applications close, follow-ups; not the entries that only record a status
		List<CalendarEntry> comingUp = this.applications.findCalendar(userId, today, today.plusDays(COMING_UP_DAYS))
			.stream()
			.filter(entry -> entry.kind() != CalendarEntryKind.EVENT || entry.isInterview())
			.sorted(Comparator.comparing(CalendarEntry::date)
				.thenComparing(CalendarEntry::time, Comparator.nullsLast(Comparator.naturalOrder())))
			.limit(COMING_UP)
			.toList();
		model.addAttribute(ModelAttributes.COMING_UP, comingUp);
		// Every request still open: Auto-apply's apart, as it's done on its own page, at the browser
		List<AiRequest> open = this.discovery.findOpenRequests(userId);
		model.addAttribute(ModelAttributes.OPEN_REQUESTS,
				open.stream().filter(request -> request.kind() != AiRequestKind.AUTO_APPLY).count());
		model.addAttribute(ModelAttributes.OPEN_APPLY_REQUESTS,
				open.stream().filter(request -> request.kind() == AiRequestKind.AUTO_APPLY).count());
		// The searches saved in the Search Builder, the latest first, each with the address that opens it there again
		List<SavedSearch> searches = this.discovery.findSearches(userId)
			.stream()
			.sorted(Comparator.comparing(SavedSearch::createdAt).reversed())
			.limit(SEARCHES)
			.toList();
		model.addAttribute(ModelAttributes.SAVED_SEARCHES, searches);
		model.addAttribute(ModelAttributes.SEARCH_URLS,
				searches.stream().collect(Collectors.toMap(SavedSearch::id, search -> SearchFilters.url(search.criteria()))));
		addImages(model, userId);
		return Views.HOME;
	}

}
