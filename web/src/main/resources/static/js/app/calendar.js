import { fill } from './text.js';

/**
 * The calendar's month, on a screen it fills: each day shows the entries its cell has room for, and a link to the day
 * with how many more there are (data-more, its data-message has {0}). Again whenever the cells change size: the window,
 * the sidebar folding, the side panel.
 */
function fitCalendarDays() {
	for (const day of document.querySelectorAll('[data-day-entries]')) {
		const entries = [...day.querySelectorAll('.calendar-entry')];
		const more = day.querySelector('[data-more]');
		entries.forEach((entry) => {
			entry.hidden = false;
		});
		more.hidden = true;
		let hiddenCount = 0;
		while (day.scrollHeight > day.clientHeight + 1 && hiddenCount < entries.length) {
			hiddenCount++;
			entries[entries.length - hiddenCount].hidden = true;
			more.hidden = false;
			more.textContent = fill(more.dataset.message, hiddenCount);
		}
	}
}

const calendarResizes = new ResizeObserver(() => requestAnimationFrame(fitCalendarDays));

/**
 * The calendar's week and day: the hours start at the one its data-scroll-to says (a minute of the day), and the month
 * fits its days to its cells.
 */
function showCalendar() {
	for (const grid of document.querySelectorAll('[data-calendar-fit]')) {
		calendarResizes.observe(grid);
	}
	for (const hours of document.querySelectorAll('[data-time-scroll]:not([data-scrolled])')) {
		const hour = parseFloat(getComputedStyle(hours).getPropertyValue('--hour')) || 52;
		hours.scrollTop = (Number(hours.dataset.scrollTo) / 60) * hour;
		hours.dataset.scrolled = '';
	}
}
document.addEventListener('DOMContentLoaded', showCalendar);
document.addEventListener('htmx:afterSettle', showCalendar);
