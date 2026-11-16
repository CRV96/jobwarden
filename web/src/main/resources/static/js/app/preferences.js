/**
 * What the pages remember in this browser: the theme, the sidebar folded, the notices hidden, the cards folded. Where
 * storage is off (a private window) a choice lasts until the page is left.
 */
export const preferences = {
	get(key) {
		try {
			return localStorage.getItem(`jobwarden.${key}`);
		} catch {
			return null;
		}
	},
	set(key, value) {
		try {
			localStorage.setItem(`jobwarden.${key}`, value);
		} catch {
			// Storage is off, e.g. in a private window: the choice lasts until the page is left
		}
	},
};
