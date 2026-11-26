/*
 * The cards and texts of a job's page and side panel: texts cut short that open in full, cards that fold to their
 * header, links that jump to a card, and the AI Assistant's cards, which ask again as they change.
 */
import { preferences } from './preferences.js';

// A job's AI card asked for something, took it back or deleted it: the overview under the job's facts asks again
const ASSISTANT_CARD = /\/applications\/\d+\/(fit|research|cv|letter|outreach|prep|negotiation)(\/|$)/;
document.addEventListener('htmx:afterRequest', (event) => {
	const config = event.detail.requestConfig;
	if (event.detail.successful && config && config.verb !== 'get' && ASSISTANT_CARD.test(config.path ?? '')) {
		htmx.trigger(document.body, 'assistant-cards-changed');
	}
});

// A card that asks again every few seconds (a request waiting for the assistant) waits while a popover in it is open,
// e.g. the one saying to connect the assistant first: swapping the card would close it under the reader
document.addEventListener('htmx:beforeRequest', (event) => {
	if (event.detail.elt instanceof Element && event.detail.elt.querySelector(':popover-open')) {
		event.preventDefault();
	}
});

// A "Show more" button (data-expand) only shows when the text before it is cut off
const hideNeedlessExpanders = () => {
	for (const expander of document.querySelectorAll('[data-expand]')) {
		const text = expander.previousElementSibling;
		if (text.classList.contains(expander.dataset.clamp ?? 'line-clamp-12')) {
			expander.hidden = text.scrollHeight <= text.clientHeight;
		}
	}
};
document.addEventListener('DOMContentLoaded', hideNeedlessExpanders);
document.addEventListener('htmx:afterSettle', hideNeedlessExpanders);

/** data-expand: shows all of the clamped text before it (data-clamp, line-clamp-12 by default), or clamps it again. */
export function expand(target) {
	const expander = target.closest('[data-expand]');
	if (!expander) {
		return false;
	}
	const text = expander.previousElementSibling;
	const expanded = !text.classList.toggle(expander.dataset.clamp ?? 'line-clamp-12');
	expander.textContent = expanded ? expander.dataset.less : expander.dataset.more;
	return true;
}

/** data-unclamp: shows the whole of the text next to it (data-clamped), cut after a few lines. */
export function unclamp(target) {
	const unclamper = target.closest('[data-unclamp]');
	if (!unclamper) {
		return false;
	}
	unclamper.parentElement.querySelector('[data-clamped]')?.classList.remove('line-clamp-[8]');
	unclamper.hidden = true;
	return true;
}

/*
 * The cards of a job's page and side panel (data-collapse="<kind>") fold to their header, and unfold, with the arrow
 * added to it here. Each kind stays as it was left, for every job: an interview prep folded once stays folded. Again
 * after HTMX swaps a card in, e.g. while Claude is on it.
 */
const foldedKinds = () => new Set((preferences.get('folded') ?? '').split(',').filter(Boolean));

function showFolded() {
	const folded = foldedKinds();
	for (const card of document.querySelectorAll('[data-collapse]')) {
		const header = card.firstElementChild;
		if (!header) {
			continue;
		}
		let toggle = header.querySelector(':scope > [data-collapse-toggle]');
		if (!toggle) {
			toggle = document.createElement('button');
			toggle.type = 'button';
			toggle.className = 'collapse-toggle btn btn-ghost ml-auto size-6 flex-none p-0 text-ink/50';
			toggle.dataset.collapseToggle = '';
			toggle.innerHTML = '<i class="ph ph-caret-down text-sm"></i>';
			header.append(toggle);
		}
		const isFolded = folded.has(card.dataset.collapse);
		card.toggleAttribute('data-collapsed', isFolded);
		toggle.setAttribute('aria-expanded', String(!isFolded));
		toggle.title = isFolded ? document.body.dataset.unfold : document.body.dataset.fold;
		toggle.setAttribute('aria-label', toggle.title);
	}
}
document.addEventListener('DOMContentLoaded', showFolded);
document.addEventListener('htmx:afterSettle', showFolded);

document.addEventListener('click', (event) => {
	const toggle = event.target instanceof Element ? event.target.closest('[data-collapse-toggle]') : null;
	if (!toggle) {
		return;
	}
	const kind = toggle.closest('[data-collapse]').dataset.collapse;
	const folded = foldedKinds();
	if (!folded.delete(kind)) {
		folded.add(kind);
	}
	preferences.set('folded', [...folded].join(','));
	showFolded();
});

/*
 * The AI Assistant's overview under a job's facts links to its cards (data-jump="<card's id>"): a click scrolls to the
 * card, in the page or the side panel, and unfolds it when it's folded.
 */
document.addEventListener('click', (event) => {
	const link = event.target instanceof Element ? event.target.closest('[data-jump]') : null;
	const card = link ? document.getElementById(link.dataset.jump) : null;
	if (!card) {
		return;
	}
	event.preventDefault();
	const folded = card.closest('[data-collapsed]');
	folded?.querySelector('[data-collapse-toggle]')?.click();
	card.scrollIntoView({ behavior: 'smooth', block: 'start' });
});
