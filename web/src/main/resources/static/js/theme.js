/*
 * Runs in <head>, before the page paints, so it never flashes in the other theme: the theme picked in the settings,
 * otherwise the system's, and the sidebar folded as it was left. app.js keeps both up to date.
 */
'use strict';

(() => {
	const read = (key) => {
		try {
			return localStorage.getItem(`jobwarden.${key}`);
		} catch {
			return null;
		}
	};
	const root = document.documentElement;
	root.dataset.theme = read('theme') ?? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
	if (read('sidebar') === 'folded') {
		root.dataset.sidebar = 'folded';
	}
})();
