/**
 * What the pages remember in this browser: the theme, the sidebar folded, the notices hidden, the cards folded. Where
 * storage is off or full (a private window) a choice lasts until the page is left.
 */

/** The choices storage refused, kept for this page only, as text like storage keeps them. */
const unsaved = new Map();

export const preferences = {
	get(key) {
		if (unsaved.has(key)) {
			return unsaved.get(key);
		}
		try {
			return localStorage.getItem(`jobwarden.${key}`);
		} catch {
			return null;
		}
	},
	set(key, value) {
		try {
			localStorage.setItem(`jobwarden.${key}`, value);
			unsaved.delete(key);
		} catch {
			unsaved.set(key, String(value));
		}
	},
};
