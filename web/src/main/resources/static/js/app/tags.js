import { fill } from './text.js';

/*
 * The search page's fields of chips (data-tag-field="<name>"). Each chip holds a hidden input of that name, so the form
 * sends one value per chip; the list under the field suggests what to add, filtered by what's typed. Enter adds the
 * highlighted suggestion, or else the text; in a field with data-split-commas, so does a comma. Backspace in the empty
 * field removes the last chip. Each change fires the form's change event: HTMX then asks for the queries again.
 */
const MAX_SUGGESTIONS = 8;

const tagValues = (field) => [...field.querySelectorAll('.tag-chip input')].map((input) => input.value);

const hasTag = (field, value) => tagValues(field).some((tag) => tag.toLowerCase() === value.toLowerCase());

/**
 * Adds a chip, unless the field has it already, spelled like the suggestion it matches, if any: "spring" as Spring. The
 * caller tells the form (tagsChanged).
 */
function addTag(field, value) {
	const typed = value.trim();
	const suggested = [...field.querySelectorAll('[role="option"]')]
		.find((option) => option.dataset.value.toLowerCase() === typed.toLowerCase());
	const text = suggested?.dataset.value ?? typed;
	if (!text || hasTag(field, text)) {
		return;
	}
	const chip = field.querySelector('template[data-chip-template]').content.firstElementChild.cloneNode(true);
	chip.querySelector('[data-chip-label]').textContent = text;
	chip.querySelector('input').value = text;
	const remove = chip.querySelector('[data-remove-tag]');
	remove.title = fill(field.dataset.removeLabel, text);
	remove.setAttribute('aria-label', remove.title);
	field.querySelector('.tag-text').before(chip);
}

function removeTag(chip) {
	const field = chip.closest('[data-tag-field]');
	chip.remove();
	field.querySelector('.tag-text').focus();
	tagsChanged(field);
}

/**
 * After chips were added or removed: the buttons that add one show only while it's missing, and the form hears of it.
 * The suggestions close, so they don't cover the next field; typing opens them again.
 */
function tagsChanged(field) {
	for (const button of document.querySelectorAll(`[data-add-to="${field.dataset.tagField}"]`)) {
		button.hidden = hasTag(field, button.dataset.value);
	}
	showSuggestions(field, false);
	field.dispatchEvent(new Event('change', { bubbles: true }));
}

/**
 * Shows the suggestions the field doesn't have yet that match what's typed, at most MAX_SUGGESTIONS, or hides the list.
 * While something is typed, the suggestion that's exactly it, or else the first, is highlighted.
 */
function showSuggestions(field, open = true) {
	const input = field.querySelector('.tag-text');
	const list = field.querySelector('.tag-options');
	const typed = input.value.trim().toLowerCase();
	const shown = [];
	for (const option of list.querySelectorAll('[role="option"]')) {
		const matches = open && shown.length < MAX_SUGGESTIONS && !hasTag(field, option.dataset.value)
			&& (!typed || `${option.textContent} ${option.dataset.value}`.toLowerCase().includes(typed));
		option.hidden = !matches;
		if (matches) {
			shown.push(option);
		}
	}
	list.hidden = shown.length === 0;
	input.setAttribute('aria-expanded', String(shown.length > 0));
	const exact = shown.find((option) => option.dataset.value.toLowerCase() === typed);
	highlight(field, typed ? (exact ?? shown[0]) : null);
}

function highlight(field, option) {
	for (const highlighted of field.querySelectorAll('[role="option"][aria-selected="true"]')) {
		highlighted.setAttribute('aria-selected', 'false');
	}
	const input = field.querySelector('.tag-text');
	if (option) {
		option.setAttribute('aria-selected', 'true');
		option.scrollIntoView({ block: 'nearest' });
		input.setAttribute('aria-activedescendant', option.id);
	} else {
		input.removeAttribute('aria-activedescendant');
	}
}

/** Moves the highlight to the next (1) or previous (-1) suggestion shown. */
function moveHighlight(field, step) {
	if (field.querySelector('.tag-options').hidden) {
		showSuggestions(field);
	}
	const shown = [...field.querySelectorAll('[role="option"]:not([hidden])')];
	const current = shown.findIndex((option) => option.getAttribute('aria-selected') === 'true');
	const next = shown[Math.min(Math.max(current + step, 0), shown.length - 1)];
	highlight(field, next ?? null);
}

/** Adds the highlighted suggestion, or else what's typed: in a field that splits at commas, each part of it. */
function addTyped(field) {
	const input = field.querySelector('.tag-text');
	const highlighted = field.querySelector('[role="option"][aria-selected="true"]:not([hidden])');
	const values = highlighted ? [highlighted.dataset.value]
		: field.dataset.splitCommas ? input.value.split(',') : [input.value];
	values.forEach((value) => addTag(field, value));
	input.value = '';
	tagsChanged(field);
}

document.addEventListener('keydown', (event) => {
	const input = event.target;
	if (!(input instanceof HTMLInputElement) || !input.matches('.tag-text')) {
		return;
	}
	const field = input.closest('[data-tag-field]');
	if (event.key === 'Enter') {
		// Not the form's submit: the text becomes a chip
		event.preventDefault();
		if (input.value.trim() || field.querySelector('[role="option"][aria-selected="true"]:not([hidden])')) {
			addTyped(field);
		}
	} else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
		event.preventDefault();
		moveHighlight(field, event.key === 'ArrowDown' ? 1 : -1);
	} else if (event.key === 'Escape') {
		showSuggestions(field, false);
	} else if (event.key === 'Backspace' && input.value === '') {
		const chips = field.querySelectorAll('.tag-chip');
		if (chips.length > 0) {
			removeTag(chips[chips.length - 1]);
		}
	}
});

/** data-remove-tag: removes its chip from a field of chips. */
export function removeChip(target) {
	const removeButton = target.closest('[data-remove-tag]');
	if (!removeButton) {
		return false;
	}
	removeTag(removeButton.closest('.tag-chip'));
	return true;
}

/** data-add-to="<field>" data-value="...": adds that chip to the field, e.g. a job board to the sites. */
export function addFromButton(target) {
	const adder = target.closest('[data-add-to]');
	if (!adder) {
		return false;
	}
	const field = document.querySelector(`[data-tag-field="${adder.dataset.addTo}"]`);
	if (field) {
		addTag(field, adder.dataset.value);
		tagsChanged(field);
	}
	return true;
}

/** A click on a field of chips is for typing another: it shows the suggestions. */
export function focusField(target) {
	const tagField = target.closest('[data-tag-field]');
	if (!tagField || target.closest('button, li')) {
		return false;
	}
	tagField.querySelector('.tag-text').focus();
	showSuggestions(tagField);
	return true;
}

/** Typing in a field of chips: a comma, typed or pasted, ends each chip before it; the suggestions follow the text. */
export function tagTyped(target) {
	if (!(target instanceof HTMLInputElement) || !target.matches('.tag-text')) {
		return false;
	}
	const field = target.closest('[data-tag-field]');
	if (field.dataset.splitCommas && target.value.includes(',')) {
		const parts = target.value.split(',');
		const rest = parts.pop();
		parts.forEach((part) => addTag(field, part));
		target.value = rest.trimStart();
		tagsChanged(field);
	}
	showSuggestions(field);
	return true;
}

document.addEventListener('focusin', (event) => {
	const input = event.target;
	if (input instanceof HTMLInputElement && input.matches('.tag-text')) {
		showSuggestions(input.closest('[data-tag-field]'));
	}
});

document.addEventListener('focusout', (event) => {
	const input = event.target;
	if (input instanceof HTMLInputElement && input.matches('.tag-text')) {
		showSuggestions(input.closest('[data-tag-field]'), false);
	}
});

// A suggestion is picked on mousedown, before the field loses the focus and hides the list
document.addEventListener('mousedown', (event) => {
	const target = event.target instanceof Element ? event.target : null;
	const option = target?.closest('[data-tag-field] [role="option"]');
	if (option) {
		event.preventDefault();
		const field = option.closest('[data-tag-field]');
		addTag(field, option.dataset.value);
		field.querySelector('.tag-text').value = '';
		tagsChanged(field);
	}
});
