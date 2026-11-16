/*
 * The page around the content: the theme, the sidebar, the notices that can be hidden, the menus, and the banner that
 * says a request failed.
 */
import { preferences } from './preferences.js';

document.addEventListener('DOMContentLoaded', () => {
	// The settings page: the theme option that's in use
	for (const choice of document.querySelectorAll('[data-theme-choice]')) {
		choice.checked = choice.value === document.documentElement.dataset.theme;
	}
	// data-dismissible="<key>": a notice the user hid stays hidden, e.g. the one for a version, until there's another
	for (const notice of document.querySelectorAll('[data-dismissible]')) {
		notice.hidden = preferences.get(`hidden.${notice.dataset.dismissible}`) === 'true';
	}
});

/** data-theme-choice: the theme picked in the settings, for every page from now on. */
export function chooseTheme(target) {
	if (!target.matches('[data-theme-choice]')) {
		return false;
	}
	document.documentElement.dataset.theme = target.value;
	preferences.set('theme', target.value);
	return true;
}

/** data-fold-sidebar: folds the sidebar to its icons, or unfolds it. */
export function foldSidebar(target) {
	if (!target.closest('[data-fold-sidebar]')) {
		return false;
	}
	const root = document.documentElement;
	const folded = root.dataset.sidebar !== 'folded';
	if (folded) {
		root.dataset.sidebar = 'folded';
	} else {
		delete root.dataset.sidebar;
	}
	preferences.set('sidebar', folded ? 'folded' : 'open');
	return true;
}

/** data-dismiss: hides the data-dismissible notice it's in, for good. */
export function dismiss(target) {
	const dismisser = target.closest('[data-dismiss]');
	if (!dismisser) {
		return false;
	}
	const notice = dismisser.closest('[data-dismissible]');
	preferences.set(`hidden.${notice.dataset.dismissible}`, 'true');
	notice.hidden = true;
	return true;
}

// A menu with data-anchor="<button id>" opens under its button, lined up with its right edge. Before it shows, so it
// never flashes elsewhere; its width comes from the stylesheet.
document.addEventListener('beforetoggle', (event) => {
	const menu = event.target;
	if (!(menu instanceof HTMLElement) || !menu.dataset.anchor || event.newState !== 'open') {
		return;
	}
	const anchor = document.getElementById(menu.dataset.anchor).getBoundingClientRect();
	const width = Number.parseFloat(getComputedStyle(menu).width);
	menu.style.top = `${anchor.bottom + 6}px`;
	menu.style.left = `${Math.max(8, anchor.right - width)}px`;
}, true);

// HTMX ignores error responses, so a failed click would otherwise do nothing, e.g. when the application was deleted in
// another tab or the server is down. layout.html has the banner this shows instead.
for (const failure of ['htmx:responseError', 'htmx:sendError']) {
	document.addEventListener(failure, () => {
		const banner = document.getElementById('request-error');
		if (banner) {
			banner.hidden = false;
		}
	});
}
