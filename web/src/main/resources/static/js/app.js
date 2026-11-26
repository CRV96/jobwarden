/*
 * What the pages do in the browser beyond HTMX, one module per feature (app/): the theme and the folding sidebar,
 * dialogs and the side panel, the list's selection, forms, fields of chips, the Discover page's cards, the calendar,
 * reminders, the board's drag and drop, cards that fold, toasts, the jobs' count as the extension saves more, the
 * setup guide, what changed after an update, the Search Builder's queries shown or hidden, and a job's description
 * edited with its bold, italics and lists. Elements ask for it with
 * data- attributes, and listeners on the document handle them, so it works the same for the parts HTMX swaps in.
 *
 * A module listens for the events only it handles. A click, a change or an input that several features might claim
 * goes through one chain here instead (Chain of Responsibility): each handler says whether it was its own, and the
 * first that was ends it, in this order.
 *
 * The theme and the sidebar are also read by theme.js, in layout.html's <head>, before the page paints.
 */
import { addFromButton, focusField, removeChip, tagTyped } from './app/tags.js';
import { addRow, autoSubmit, clearPrevious, countTyped, examplesTyped, fill, limitFileSize, removeRow, showStatusFields,
	toggleHidden } from './app/forms.js';
import { chooseTheme, dismiss, foldSidebar } from './app/layout.js';
import { clearSelection, selectAll, selectRow } from './app/selection.js';
import { closeDialog, closeOnBackdrop, markDrawerRow, openDialog } from './app/dialogs.js';
import { copy, share } from './app/sharing.js';
import { expand, unclamp } from './app/cards.js';
import { decideByClick } from './app/swipe.js';
import './app/calendar.js';
import './app/reminders.js';
import './app/pipeline.js';
import './app/toasts.js';
import './app/counts.js';
import './app/setup.js';
import './app/whats-new.js';
import './app/queries.js';
import './app/rich-text.js';

const CLICKS = [openDialog, closeDialog, clearPrevious, fill, closeOnBackdrop, foldSidebar, clearSelection, toggleHidden,
	expand, removeChip, addFromButton, focusField, decideByClick, share, unclamp, dismiss, copy, addRow, removeRow,
	markDrawerRow];

const CHANGES = [limitFileSize, chooseTheme, selectAll, selectRow, showStatusFields, autoSubmit];

const INPUTS = [countTyped, tagTyped, examplesTyped];

document.addEventListener('click', (event) => {
	if (event.target instanceof Element) {
		CLICKS.some((handle) => handle(event.target, event));
	}
});

document.addEventListener('change', (event) => {
	const target = event.target;
	if (target instanceof HTMLInputElement || target instanceof HTMLSelectElement) {
		CHANGES.some((handle) => handle(target, event));
	}
});

document.addEventListener('input', (event) => {
	if (event.target instanceof Element) {
		INPUTS.some((handle) => handle(event.target, event));
	}
});
