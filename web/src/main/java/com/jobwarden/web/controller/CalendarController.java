package com.jobwarden.web.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;

import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.enums.CalendarView;
import com.jobwarden.web.view.CalendarDays;
import com.jobwarden.web.view.CalendarMonth;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The applications' timelines on a calendar, a month, a week or a day at a time: every status they got, the interviews,
 * the deadlines and the days to follow up. Next to it, what's coming up: the interviews, deadlines and days to follow
 * up from today on, and any other event dated after today. A click opens the application in the side panel.
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

	private final ApplicationService applications;

	/**
	 * @param view {@code month}, {@code week} or {@code day}; the month without one
	 * @param date a day of the month, week or day to show, as {@code 2026-10-27}; today without one, or with one that
	 * isn't a date
	 * @param month the month to show, as {@code 2026-10}, from the addresses before there were weeks and days
	 */
	@GetMapping
	String calendar(@RequestParam(required = false) @Nullable String view,
			@RequestParam(required = false) @Nullable String date, @RequestParam(required = false) @Nullable String month,
			Model model) {
		long userId = requireProfile(model).id();
		LocalDate today = LocalDate.now();
		CalendarView shownView = CalendarView.parse(view).orElse(CalendarView.MONTH);
		LocalDate shown = parseDate(date, parseMonth(month, today));

		switch (shownView) {
			case MONTH -> {
				YearMonth shownMonth = YearMonth.from(shown);
				model.addAttribute(ModelAttributes.CALENDAR_MONTH, CalendarMonth.of(shownMonth, FIRST_DAY_OF_WEEK, today,
						this.applications.findCalendar(userId, CalendarMonth.firstShown(shownMonth, FIRST_DAY_OF_WEEK),
								CalendarMonth.lastShown(shownMonth, FIRST_DAY_OF_WEEK))));
			}
			case WEEK -> addDays(model, userId, shown.with(TemporalAdjusters.previousOrSame(FIRST_DAY_OF_WEEK)), 7, today);
			case DAY -> addDays(model, userId, shown, 1, today);
		}
		model.addAttribute(ModelAttributes.CALENDAR_VIEW, shownView);
		model.addAttribute(ModelAttributes.CALENDAR_VIEWS, CalendarView.values());
		model.addAttribute(ModelAttributes.CALENDAR_DATE, shown);
		model.addAttribute(ModelAttributes.UPCOMING, this.applications
			.findCalendar(userId, today, today.plusDays(UPCOMING_DAYS))
			.stream()
			.filter(entry -> entry.kind() != CalendarEntryKind.EVENT || entry.isInterview() || entry.date().isAfter(today))
			.limit(UPCOMING_MAX)
			.toList());
		model.addAttribute(ModelAttributes.TODAY, today);
		addImages(model, userId);
		return Views.CALENDAR;
	}

	private void addDays(Model model, long userId, LocalDate first, int count, LocalDate today) {
		model.addAttribute(ModelAttributes.CALENDAR_DAYS, CalendarDays.of(first, count, today, LocalTime.now(),
				this.applications.findCalendar(userId, first, first.plusDays(count - 1L))));
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
