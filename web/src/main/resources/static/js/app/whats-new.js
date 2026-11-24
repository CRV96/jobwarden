/*
 * The popup of what changed (updates/updates.html: whats-new), once for each profile in this browser, after the server
 * runs a release the profile hasn't seen yet (data-app-version). It tells of the releases since the version the
 * browser remembers the profile saw; with none remembered, of the release running alone, unless the profile was made
 * after that release came out (data-profile-after-release): then there's no update to tell of, and the version is
 * only remembered. It's remembered once the popup opens, so it never opens twice for the same version; when the
 * server has nothing to show (204: GitHub not asked yet), the next page asks again.
 */
import { preferences } from './preferences.js';

const seenKey = () => `whatsNew.seen.${document.body.dataset.profileKey}`;

/**
 * @returns whether the popup is to open on this page: the setup guide waits for the next one (setup.js)
 */
export function whatsNewDue() {
	const { whatsNew, appVersion, profileKey } = document.body.dataset;
	return Boolean(whatsNew && appVersion && profileKey) && preferences.get(seenKey()) !== appVersion;
}

async function openWhatsNew() {
	const { whatsNew: url, appVersion: version, profileAfterRelease } = document.body.dataset;
	const seen = preferences.get(seenKey());
	if (seen === null && profileAfterRelease === 'true') {
		preferences.set(seenKey(), version);
		return;
	}
	const holder = document.getElementById('whats-new-holder');
	if (!holder) {
		return;
	}
	await htmx.ajax('GET', url, { target: holder, swap: 'innerHTML', values: seen ? { since: seen } : {} });
	const dialog = holder.querySelector('dialog');
	if (dialog) {
		dialog.showModal();
		preferences.set(seenKey(), version);
	}
}

document.addEventListener('DOMContentLoaded', () => {
	if (whatsNewDue()) {
		openWhatsNew().catch(() => {
			// The server is away: the popup waits for the next page
		});
	}
});
