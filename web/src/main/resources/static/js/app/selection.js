/*
 * The list's selection: the boxes that tick rows, the bar that counts them, and the dialog that deletes them.
 */
import { capitalize, fill } from './text.js';

/**
 * The list's selection: the select-all box, the bar that counts the ticked rows, and the texts of the dialog that
 * deletes them. The bar and the dialog carry their texts in data- attributes, in the page's language.
 */
export function updateSelection() {
	const boxes = [...document.querySelectorAll('[data-select-row]')];
	const count = boxes.filter((box) => box.checked).length;

	const all = document.querySelector('[data-select-all]');
	if (all) {
		all.checked = boxes.length > 0 && count === boxes.length;
		all.indeterminate = count > 0 && count < boxes.length;
	}

	const bar = document.getElementById('selection-bar');
	if (bar) {
		bar.hidden = count === 0;
		bar.querySelector('[data-selection-count]').textContent = fill(bar.dataset.label, count);
	}

	const dialog = document.getElementById('delete-selected');
	if (dialog) {
		const form = count === 1 ? 'one' : 'many';
		dialog.querySelector('[data-title]').textContent = fill(dialog.dataset[`title${capitalize(form)}`], count);
		dialog.querySelector('[data-body]').textContent = dialog.dataset[`body${capitalize(form)}`];
	}
}

/** data-clear-selection: unticks every row of the list. */
export function clearSelection(target) {
	if (!target.closest('[data-clear-selection]')) {
		return false;
	}
	for (const box of document.querySelectorAll('[data-select-row]')) {
		box.checked = false;
	}
	updateSelection();
	return true;
}

/** data-select-all: ticks every row, or none. */
export function selectAll(target) {
	if (!target.matches('[data-select-all]')) {
		return false;
	}
	for (const box of document.querySelectorAll('[data-select-row]')) {
		box.checked = target.checked;
	}
	updateSelection();
	return true;
}

/** data-select-row: one row ticked or unticked. */
export function selectRow(target) {
	if (!target.matches('[data-select-row]')) {
		return false;
	}
	updateSelection();
	return true;
}

document.addEventListener('htmx:afterSettle', () => updateSelection());
