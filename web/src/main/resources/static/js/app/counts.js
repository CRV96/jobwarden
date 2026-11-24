/*
 * The jobs follow what the browser extension saves from another tab, without reloading the page. While a profile is
 * selected, every page asks the server how many jobs there are and when they last changed (body's data-job-changes):
 * every few seconds while it's in front, and at once when it comes back in front, as after saving a posting in another
 * tab. When they changed, the sidebar's count (data-job-count) and the Jobs page's subtitle (data-jobs-subtitle) say
 * so, and the list, the board or the calendar loads again, as they do for applications-changed.
 */
const LOOK_EVERY = 10_000;

/** The jobs as last seen; null until the first answer, which only sets it. */
let seen = null;

/** Set while this module fires applications-changed itself, which it doesn't need to hear. */
let firing = false;

async function lookForChanges(quietly = false) {
	const url = document.body.dataset.jobChanges;
	if (!url || document.hidden) {
		return;
	}
	let changes;
	try {
		const response = await fetch(url, { headers: { Accept: 'application/json' } });
		if (response.status !== 200) {
			return;
		}
		changes = await response.json();
	} catch {
		// The server is away, e.g. restarting: the next look will tell
		return;
	}
	const changed = seen !== null && changes.version !== seen;
	seen = changes.version;
	if (!changed) {
		return;
	}
	for (const count of document.querySelectorAll('[data-job-count]')) {
		count.textContent = String(changes.count);
	}
	for (const subtitle of document.querySelectorAll('[data-jobs-subtitle]')) {
		subtitle.textContent = changes.subtitle;
	}
	if (!quietly) {
		firing = true;
		htmx.trigger(document.body, 'applications-changed');
		firing = false;
	}
}

document.addEventListener('DOMContentLoaded', () => {
	void lookForChanges();
	setInterval(lookForChanges, LOOK_EVERY);
});
document.addEventListener('visibilitychange', () => {
	if (!document.hidden) {
		void lookForChanges();
	}
});
// A job changed on this page (the side panel, the board): what's shown reloads already; only the counts follow
document.body.addEventListener('applications-changed', () => {
	if (!firing) {
		void lookForChanges(true);
	}
});
