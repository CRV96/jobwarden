package com.jobwarden.web.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.dto.JobStatistics;
import com.jobwarden.jobs.dto.JobStatistics.ReplyRate;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.FitVerdict;
import com.jobwarden.jobs.service.ActivityService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.StatisticsService;
import com.jobwarden.web.constants.ControllerConstants;
import com.jobwarden.web.constants.ModelAttributes;
import com.jobwarden.web.constants.Views;
import com.jobwarden.web.dto.SearchFilters;
import com.jobwarden.web.view.JobImages;
import lombok.RequiredArgsConstructor;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import static com.jobwarden.web.controller.Pages.redirectTo;
import static com.jobwarden.web.controller.Pages.requireProfile;

/**
 * The homepage: how the job hunt goes lately and what waits for the profile. The applications sent and the jobs saved
 * today, this week and this month, and day by day over the last 30 days; the jobs by status; the latest entries on
 * their timelines; what to do: jobs to review, email updates, requests open for the assistant, follow-ups due, offers,
 * and the next days' interviews and deadlines; and the searches saved in the Search Builder, to run again or hand to the
 * assistant. Each links to where it's done. Below, the statistics: how often the applications got an answer, and an
 * interview, by site and by who applied; whether the jobs Claude scored high led to interviews more often than the
 * others (the calibration); what the fits and the tailored CVs found missing most often; and why the profile was turned
 * down. Worked out from the data, without AI.
 */
@PageController
@RequiredArgsConstructor
class HomeController {

	/** How many days ahead "Coming up" looks. */
	static final int COMING_UP_DAYS = 7;

	/** How many of them it lists. */
	static final int COMING_UP = 6;

	/** How many saved searches it lists, the latest first. */
	static final int SEARCHES = 5;

	/** How many applications a group needs before the calibration compares it: fewer say little. */
	static final int CALIBRATION_MIN_SENT = 3;

	/** How many points apart the interview rates must be to say the scores tell something. */
	static final int CALIBRATION_GAP = 10;

	/** How many of the keywords the tailored CVs couldn't claim the page shows, the most frequent. */
	static final int MISSING_KEYWORDS = 15;

	private final ActivityService activity;

	private final ApplicationService applications;

	private final StatisticsService statistics;

	private final DiscoveryService discovery;

	private final CvService cvs;

	private final Clock clock;

	private final JobImages jobImages;

	@GetMapping(ControllerConstants.HOMEPAGE)
	String home(Model model) {
		User profile = requireProfile(model);
		long userId = profile.id();
		LocalDate today = LocalDate.now(this.clock);
		model.addAttribute(ModelAttributes.TODAY, today);
		model.addAttribute(ModelAttributes.ACTIVITY, this.activity.find(userId, today, this.clock.getZone()));
		JobStatistics found = this.statistics.find(userId);
		model.addAttribute(ModelAttributes.STATISTICS, found);
		model.addAttribute(ModelAttributes.CALIBRATION, calibration(found.byFit()));
		model.addAttribute(ModelAttributes.MISSING_KEYWORDS, missingKeywords(this.cvs.findAllTailored(userId)));
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
		this.jobImages.addImages(model, userId);
		return Views.HOME;
	}

	/**
	 * The Statistics page's address, from before the statistics were on the homepage: a bookmark of it opens them there.
	 */
	@GetMapping(ControllerConstants.STATISTICS)
	String statistics() {
		return redirectTo(ControllerConstants.HOMEPAGE + "#statistics");
	}

	/**
	 * Whether the strong fits got to an interview more often than the partial and weak ones: WORKS when clearly more,
	 * BACKWARDS when clearly less, FLAT when about as often, FEW while either has too few applications to tell.
	 */
	static String calibration(List<ReplyRate> byFit) {
		Optional<ReplyRate> strong = byFit.stream().filter(rate -> FitVerdict.STRONG.name().equals(rate.key())).findFirst();
		List<ReplyRate> others = byFit.stream()
			.filter(rate -> rate.key() != null && !FitVerdict.STRONG.name().equals(rate.key()))
			.toList();
		int othersSent = others.stream().mapToInt(ReplyRate::sent).sum();
		if (strong.isEmpty() || strong.get().sent() < CALIBRATION_MIN_SENT || othersSent < CALIBRATION_MIN_SENT) {
			return "FEW";
		}
		int othersInterviewed = others.stream().mapToInt(ReplyRate::interviewed).sum();
		int gap = strong.get().interviewPercent() - (int) Math.round(100.0 * othersInterviewed / othersSent);
		if (gap >= CALIBRATION_GAP) {
			return "WORKS";
		}
		return (gap <= -CALIBRATION_GAP) ? "BACKWARDS" : "FLAT";
	}

	/**
	 * The keywords the tailored CVs couldn't honestly claim, counted across them however they're capitalised, the most
	 * frequent first: shown as first written.
	 */
	static Map<String, Integer> missingKeywords(List<TailoredCv> tailored) {
		Map<String, String> wording = new LinkedHashMap<>();
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (TailoredCv cv : tailored) {
			cv.missingKeywords().stream().map(String::strip).filter(keyword -> !keyword.isEmpty()).distinct().forEach(keyword -> {
				String key = keyword.toLowerCase(Locale.ROOT);
				wording.putIfAbsent(key, keyword);
				counts.merge(key, 1, Integer::sum);
			});
		}
		return counts.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
			.limit(MISSING_KEYWORDS)
			.collect(LinkedHashMap::new, (map, count) -> map.put(wording.get(count.getKey()), count.getValue()), Map::putAll);
	}

}
