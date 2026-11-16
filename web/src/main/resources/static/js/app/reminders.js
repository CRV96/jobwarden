/*
 * Reminders (calendar/reminders.html). While a profile is selected, every page asks for the reminders that are due
 * (body's data-reminders) about once a minute, and as it comes back in front. The server gives each one once, to
 * whichever tab, or the browser extension, asks first. A page in front shows it as a card in its corner; a page
 * behind others as the system's notification, when the browser allows them. A page behind others that may not show
 * notifications doesn't ask: it leaves the reminder to the extension, or to the page once it's in front again.
 */
const REMINDERS_EVERY = 60_000;

const mayNotify = () => 'Notification' in window && Notification.permission === 'granted';

async function showDueReminders() {
	const url = document.body.dataset.reminders;
	if (!url || (document.hidden && !mayNotify())) {
		return;
	}
	let due;
	try {
		const response = await fetch(url, { method: 'POST', headers: { Accept: 'application/json' } });
		if (!response.ok) {
			return;
		}
		due = await response.json();
	} catch {
		// The server is away, e.g. restarting: the reminders wait for the next time
		return;
	}
	for (const reminder of due) {
		if (document.hidden && mayNotify()) {
			const icon = document.querySelector('link[rel="icon"][type="image/png"]')?.href;
			const notification = new Notification(reminder.title, { body: reminder.body, icon, tag: `jobwarden-reminder-${reminder.id}` });
			notification.addEventListener('click', () => {
				window.focus();
				location.href = reminder.url;
				notification.close();
			});
		} else {
			showReminderCard(reminder);
		}
	}
}

function showReminderCard(reminder) {
	const template = document.getElementById('reminder-card');
	const card = template.content.firstElementChild.cloneNode(true);
	card.querySelector('[data-title]').textContent = reminder.title;
	card.querySelector('[data-body]').textContent = reminder.body;
	card.querySelector('[data-open]').href = reminder.url;
	document.getElementById('reminder-cards').append(card);
}

/** The reminders' sections say so when the browser blocks the notifications: then only the cards show them. */
function showNotificationsOff() {
	const off = 'Notification' in window && Notification.permission === 'denied';
	for (const hint of document.querySelectorAll('[data-notifications-off]')) {
		hint.hidden = !off;
	}
}

// showDueReminders keeps its own errors: the calls only start it (void)
document.addEventListener('DOMContentLoaded', () => {
	void showDueReminders();
	setInterval(showDueReminders, REMINDERS_EVERY);
	showNotificationsOff();
});
document.addEventListener('htmx:afterSettle', showNotificationsOff);
document.addEventListener('visibilitychange', () => {
	if (!document.hidden) {
		void showDueReminders();
	}
});

// Setting a reminder is when the browser asks whether JobWarden may show notifications: on the click, as it wants
document.addEventListener('change', (event) => {
	const box = event.target;
	if (box instanceof HTMLInputElement && box.checked && box.closest('[data-reminder-picker]')
			&& 'Notification' in window && Notification.permission === 'default') {
		Notification.requestPermission().then(showNotificationsOff, showNotificationsOff);
	}
});

// data-close-card: closes the reminder's card it's in
document.addEventListener('click', (event) => {
	event.target.closest('[data-close-card]')?.closest('[data-reminder-card]')?.remove();
});
