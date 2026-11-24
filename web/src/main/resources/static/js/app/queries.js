/*
 * The Search Builder's queries (search/search.html): their text shown or not, as the switch above them says, which this
 * browser keeps; hidden unless it says so. The switch's holder (data-queries) stays as the cards are swapped, so the
 * choice holds while the filters change.
 */
import { preferences } from './preferences.js';

const SHOWN = 'search.queriesShown';

function show(shown) {
	document.querySelectorAll('[data-queries]').forEach((holder) => {
		holder.dataset.queries = shown ? 'shown' : 'hidden';
	});
}

document.addEventListener('DOMContentLoaded', () => {
	const toggle = document.querySelector('[data-show-queries]');
	if (toggle instanceof HTMLInputElement) {
		toggle.checked = preferences.get(SHOWN) === 'true';
		show(toggle.checked);
	}
});

document.addEventListener('change', (event) => {
	if (event.target instanceof HTMLInputElement && event.target.matches('[data-show-queries]')) {
		preferences.set(SHOWN, String(event.target.checked));
		show(event.target.checked);
	}
});
