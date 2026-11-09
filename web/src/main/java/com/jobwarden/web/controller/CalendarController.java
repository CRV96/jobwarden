package com.jobwarden.web.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

import com.jobwarden.jobs.dto.CalendarEntry;

import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.CalendarFeedService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.enums.CalendarEntryType;
import com.jobwarden.web.enums.CalendarView;
import com.jobwarden.web.view.CalendarDays;
import com.jobwarden.web.view.CalendarMonth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The applications' timelines on a calendar, a month, a week or a day at a time: every status they got, the interviews,
 * the deadlines and the days to follow up. Next to it, what's coming up: the interviews, deadlines and days to follow
 * up from today on, and any other event dated after today. A click opens the application in the side panel. Its legend
 * picks what it shows: interviews, other status changes, deadlines, days to follow up; the browser session remembers
 * the choice.
 */
@Controller
@RequestMapping(ControllerConstants.CALENDAR)
@RequiredArgsConstructor
class CalendarController extends BaseController {

	/** How far ahead the upcoming list looks, and how much it shows at most: it scrolls. */
	private static final int UPCOMING_DAYS = 30;

	private static final int UPCOMING_MAX = 50;

	/**
	 * Weeks start on Monday (ISO 8601), as in every country whose language the app has. The page's locale has only a
	 * language, and English alone would mean the United States' Sunday.
	 */
	private static final DayOfWeek FIRST_DAY_OF_WEEK = DayOfWeek.MONDAY;

	/** The types the legend picked last in this browser session, a set of {@link CalendarEntryType}. */
	static final String SHOWN_TYPES = CalendarController.class.getName() + ".shownTypes";

	/** The parameter that names a type to show, once for each. */
	private static final String SHOW = "show";

	private final ApplicationService applications;

	private final CalendarFeedService feeds;

	/**
	 * @param view {@code month}, {@code week} or {@code day}; the month without one
	 * @param date a day of the month, week or day to show, as {@code 2026-10-27}; today without one, or with one that
	 * isn't a date
	 * @param month the month to show, as {@code 2026-10}, from the addresses before there were weeks and days
	 * @param request its {@code show} parameters are the types to show, as the legend's form sends them: with an empty
	 * one, so that unticking every type still says so. Read as they are, since Spring would make a lone empty one no
	 * parameter at all. Without any, the ones picked last in the session, and everything the first time.
	 */
	@GetMapping
	String calendar(@RequestParam(required = false) @Nullable String view,
			@RequestParam(required = false) @Nullable String date, @RequestParam(required = false) @Nullable String month,
			HttpServletRequest request, HttpSession session, Model model) {
		long userId = requireProfile(model).id();
		LocalDate today = LocalDate.now();
		CalendarView shownView = CalendarView.parse(view).orElse(CalendarView.MONTH);
		LocalDate shown = parseDate(date, parseMonth(month, today));
		String[] show = request.getParameterValues(SHOW);
		Set<CalendarEntryType> types = shownTypes((show != null) ? List.of(show) : null, session);

		switch (shownView) {
			case MONTH -> {
				YearMonth shownMonth = YearMonth.from(shown);
				model.addAttribute(ModelAttributes.CALENDAR_MONTH, CalendarMonth.of(shownMonth, FIRST_DAY_OF_WEEK, today,
						entries(userId, CalendarMonth.firstShown(shownMonth, FIRST_DAY_OF_WEEK),
								CalendarMonth.lastShown(shownMonth, FIRST_DAY_OF_WEEK), types)));
			}
			case WEEK -> addDays(model, userId, shown.with(TemporalAdjusters.previousOrSame(FIRST_DAY_OF_WEEK)), 7, today,
					types);
			case DAY -> addDays(model, userId, shown, 1, today, types);
		}
		model.addAttribute(ModelAttributes.CALENDAR_VIEW, shownView);
		model.addAttribute(ModelAttributes.CALENDAR_VIEWS, CalendarView.values());
		model.addAttribute(ModelAttributes.CALENDAR_DATE, shown);
		model.addAttribute(ModelAttributes.CALENDAR_TYPES, CalendarEntryType.values());
		model.addAttribute(ModelAttributes.CALENDAR_SHOWN_TYPES, types);
		model.addAttribute(ModelAttributes.UPCOMING, entries(userId, today, today.plusDays(UPCOMING_DAYS), types)
			.stream()
			.filter(entry -> entry.kind() != CalendarEntryKind.EVENT || entry.isInterview() || entry.date().isAfter(today))
			.limit(UPCOMING_MAX)
			.toList());
		model.addAttribute(ModelAttributes.TODAY, today);
		addImages(model, userId);
		addFeed(model, this.feeds.token(userId));
		return Views.CALENDAR;
	}

	/**
	 * A new address for the calendar feed, when the old one got where it shouldn't: the old one stops working. HTMX
	 * swaps the Subscribe menu's content with the new one.
	 */
	@PostMapping("/feed/renew")
	String renewFeed(Model model) {
		addFeed(model, this.feeds.renewToken(requireProfile(model).id()));
		return Views.CALENDAR_FEED_FRAGMENT;
	}

	/**
	 * The feed's address, and the same as webcal:, which opens the system's calendar app to subscribe to it.
	 */
	private static void addFeed(Model model, String token) {
		String url = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path(ControllerConstants.CALENDAR + "/feed.ics")
			.queryParam("token", token)
			.toUriString();
		model.addAttribute(ModelAttributes.CALENDAR_FEED_URL, url);
		model.addAttribute(ModelAttributes.CALENDAR_WEBCAL_URL, url.replaceFirst("^https?:", "webcal:"));
	}

	private void addDays(Model model, long userId, LocalDate first, int count, LocalDate today,
			Set<CalendarEntryType> types) {
		model.addAttribute(ModelAttributes.CALENDAR_DAYS, CalendarDays.of(first, count, today, LocalTime.now(),
				entries(userId, first, first.plusDays(count - 1L), types)));
	}

	private List<CalendarEntry> entries(long userId, LocalDate from, LocalDate to, Set<CalendarEntryType> types) {
		List<CalendarEntry> entries = this.applications.findCalendar(userId, from, to);
		return types.containsAll(CalendarEntryType.ALL) ? entries
				: entries.stream().filter(entry -> types.contains(CalendarEntryType.of(entry))).toList();
	}

	/**
	 * The types the legend picked now, which the session then keeps; else those it kept.
	 */
	@SuppressWarnings("unchecked")
	private static Set<CalendarEntryType> shownTypes(@Nullable List<String> show, HttpSession session) {
		if (show != null) {
			Set<CalendarEntryType> picked = CalendarEntryType.parse(show);
			session.setAttribute(SHOWN_TYPES, picked);
			return picked;
		}
		return (session.getAttribute(SHOWN_TYPES) instanceof Set<?> kept) ? (Set<CalendarEntryType>) kept
				: CalendarEntryType.ALL;
	}

	private static LocalDate parseDate(@Nullable String date, LocalDate otherwise) {
		try {
			return (date == null || date.isBlank()) ? otherwise : LocalDate.parse(date.strip());
		} catch (DateTimeParseException notADate) {
			return otherwise;
		}
	}

	private static LocalDate parseMonth(@Nullable String month, LocalDate today) {
		try {
			return (month == null || month.isBlank()) ? today : YearMonth.parse(month.strip()).atDay(1);
		} catch (DateTimeParseException notAMonth) {
			return today;
		}
	}

}
