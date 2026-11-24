/*
 * The setup guide (setup/guide.html): what to set up for each part of JobWarden to work. While a step is left, it opens
 * on its own on the first page a profile opens in a browser session, until the profile asks not to show it again,
 * which this browser keeps; the profile menu opens it whenever (data-open-setup-guide). It's loaded when it opens, so
 * it says what's done as of then. A page that shows what changed after an update (whats-new.js) leaves it for the next.
 */
import { preferences } from './preferences.js';
import { whatsNewDue } from './whats-new.js';

/** Kept for the browser session: the guide opened on its own once already. */
const OPENED_THIS_SESSION = 'jobwarden.setupGuideOpened';

const hiddenKey = () => `setupGuide.hidden.${document.body.dataset.profileKey}`;

function openedThisSession() {
	try {
		return sessionStorage.getItem(OPENED_THIS_SESSION) === document.body.dataset.profileKey;
	} catch {
		return false;
	}
}

function rememberOpened() {
	try {
		sessionStorage.setItem(OPENED_THIS_SESSION, document.body.dataset.profileKey);
	} catch {
		// Storage off: it may open again on the next page
	}
}

/**
 * @param asked whether the profile asked for it, from the menu: then it opens even when every step is done
 */
async function openGuide(asked) {
	const url = document.body.dataset.setupGuide;
	const holder = document.getElementById('setup-guide-holder');
	if (!url || !holder) {
		return;
	}
	await htmx.ajax('GET', url, { target: holder, swap: 'innerHTML' });
	const dialog = holder.querySelector('dialog');
	if (dialog && (asked || dialog.dataset.complete !== 'true')) {
		dialog.showModal();
		rememberOpened();
	}
}

document.addEventListener('DOMContentLoaded', () => {
	// Not over the popup of what changed after an update: then the guide waits for the next page
	if (document.body.dataset.setupGuide && preferences.get(hiddenKey()) !== 'true' && !openedThisSession()
		&& !whatsNewDue()) {
		openGuide(false).catch(() => {
			// The server is away: the guide waits for the next page
		});
	}
});

document.addEventListener('click', (event) => {
	const target = event.target instanceof Element ? event.target : null;
	if (target?.closest('[data-open-setup-guide]')) {
		document.getElementById('profile-menu')?.hidePopover?.();
		openGuide(true).catch(() => {
			// The server is away: nothing opens
		});
	} else if (target?.closest('[data-hide-setup-guide]')) {
		preferences.set(hiddenKey(), 'true');
		target.closest('dialog')?.close();
	}
});
