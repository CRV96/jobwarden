/*
 * What the pages do in the browser beyond HTMX: the theme and the folding sidebar, dialogs and menus, the list's
 * checkboxes and the side panel, and the search page's fields of chips. Elements ask for it with data- attributes, and
 * one listener per event on the document handles them all, so it works the same for the parts HTMX swaps in.
 *
 * The theme and the sidebar are also read by the script in layout.html's <head>, before the page paints.
 */
'use strict';

const preferences = {
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

/** A message with {0} in it, e.g. "{0} selected", filled in. */
const fill = (message, value) => message.replace('{0}', value);

document.addEventListener('DOMContentLoaded', () => {
	// The settings page: the theme option that's in use
	for (const choice of document.querySelectorAll('[data-theme-choice]')) {
		choice.checked = choice.value === document.documentElement.dataset.theme;
	}
});

document.addEventListener('click', (event) => {
	const target = event.target instanceof Element ? event.target : null;
	if (!target) {
		return;
	}

	// data-open-dialog="<id>": opens that dialog
	const opener = target.closest('[data-open-dialog]');
	if (opener) {
		document.getElementById(opener.dataset.openDialog)?.showModal();
		return;
	}

	// data-close-dialog: closes the dialog it's in
	const closer = target.closest('[data-close-dialog]');
	if (closer) {
		closer.closest('dialog')?.close();
		return;
	}

	// A click on a dialog's backdrop lands on the dialog itself, outside its box: it closes the dialog
	if (target instanceof HTMLDialogElement && target.open) {
		const box = target.getBoundingClientRect();
		const outside = event.clientX < box.left || event.clientX > box.right || event.clientY < box.top
			|| event.clientY > box.bottom;
		if (outside) {
			target.close();
		}
		return;
	}

	// data-fold-sidebar: folds the sidebar to its icons, or unfolds it
	if (target.closest('[data-fold-sidebar]')) {
		const root = document.documentElement;
		const folded = root.dataset.sidebar !== 'folded';
		if (folded) {
			root.dataset.sidebar = 'folded';
		} else {
			delete root.dataset.sidebar;
		}
		preferences.set('sidebar', folded ? 'folded' : 'open');
		return;
	}

	// data-clear-selection: unticks every row of the list
	if (target.closest('[data-clear-selection]')) {
		for (const box of document.querySelectorAll('[data-select-row]')) {
			box.checked = false;
		}
		updateSelection();
		return;
	}

	// data-toggle-hidden="<id>": shows or hides that element, e.g. the rejection reason form
	const toggler = target.closest('[data-toggle-hidden]');
	if (toggler) {
		const element = document.getElementById(toggler.dataset.toggleHidden);
		if (element) {
			element.hidden = !element.hidden;
			toggler.setAttribute('aria-expanded', String(!element.hidden));
		}
		return;
	}

	// data-expand: shows all of the clamped text before it (data-clamp, line-clamp-12 by default), or clamps it again
	const expander = target.closest('[data-expand]');
	if (expander) {
		const text = expander.previousElementSibling;
		const expanded = !text.classList.toggle(expander.dataset.clamp ?? 'line-clamp-12');
		expander.textContent = expanded ? expander.dataset.less : expander.dataset.more;
		return;
	}

	// data-remove-tag: removes its chip from a field of chips
	const removeButton = target.closest('[data-remove-tag]');
	if (removeButton) {
		removeTag(removeButton.closest('.tag-chip'));
		return;
	}

	// data-add-to="<field>" data-value="...": adds that chip to the field, e.g. a job board to the sites
	const adder = target.closest('[data-add-to]');
	if (adder) {
		const field = document.querySelector(`[data-tag-field="${adder.dataset.addTo}"]`);
		if (field) {
			addTag(field, adder.dataset.value);
			tagsChanged(field);
		}
		return;
	}

	// A click on a field of chips is for typing another: it shows the suggestions
	const tagField = target.closest('[data-tag-field]');
	if (tagField && !target.closest('button, li')) {
		tagField.querySelector('.tag-text').focus();
		showSuggestions(tagField);
		return;
	}

	// data-swipe="left|right|up": a decision on the Discover page's card, which flies off first. Applying opens the
	// posting now, while the click still lets a page open a tab
	const decision = target.closest('[data-swipe]');
	if (decision) {
		event.preventDefault();
		if (decision.dataset.openLink) {
			window.open(decision.dataset.openLink, '_blank', 'noopener');
		}
		decide(decision);
		return;
	}

	// data-native-share: the browser's own share sheet, with what the data-share-* attributes say
	const sharer = target.closest('[data-native-share]');
	if (sharer) {
		const { shareTitle: title, shareText: text, shareUrl: url } = sharer.dataset;
		// Cancelling the sheet rejects: nothing to do
		navigator.share({ title, text, ...(url && { url }) }).catch(() => {});
		return;
	}

	// data-copy-text: copies the text, and says so for a moment
	const copier = target.closest('[data-copy-text]');
	if (copier) {
		copyText(copier.dataset.copyText).then(() => {
			copier.querySelector('[data-copy-idle]').hidden = true;
			copier.querySelector('[data-copy-done]').hidden = false;
			setTimeout(() => {
				copier.querySelector('[data-copy-idle]').hidden = false;
				copier.querySelector('[data-copy-done]').hidden = true;
			}, 1500);
		});
		return;
	}

	// A row of the list, or a card of the pipeline, marks itself while its application is in the side panel
	const row = target.closest('[data-drawer-row]');
	if (row) {
		for (const open of document.querySelectorAll('[data-drawer-row].is-open')) {
			open.classList.remove('is-open');
		}
		row.classList.add('is-open');
	}
});

/**
 * Copies the text to the clipboard. Where the page may not write to it (an embedded browser, an older one), it copies
 * a selection of the text instead, the older way.
 */
async function copyText(text) {
	try {
		await navigator.clipboard.writeText(text);
	} catch {
		const area = document.createElement('textarea');
		area.value = text;
		area.readOnly = true;
		area.className = 'fixed top-0 left-0 opacity-0';
		document.body.append(area);
		area.select();
		const copied = document.execCommand('copy');
		area.remove();
		if (!copied) {
			throw new Error('The browser did not copy the text');
		}
	}
}

document.addEventListener('change', (event) => {
	const target = event.target;
	if (!(target instanceof HTMLInputElement || target instanceof HTMLSelectElement)) {
		return;
	}

	if (target.matches('[data-theme-choice]')) {
		document.documentElement.dataset.theme = target.value;
		preferences.set('theme', target.value);
	} else if (target.matches('[data-select-all]')) {
		for (const box of document.querySelectorAll('[data-select-row]')) {
			box.checked = target.checked;
		}
		updateSelection();
	} else if (target.matches('[data-select-row]')) {
		updateSelection();
	} else if (target.matches('[data-status-select]')) {
		showFieldsFor(target);
	} else if (target.matches('[data-auto-submit]')) {
		target.form?.requestSubmit();
	}
});

/**
 * The fields of a status select's form that are for some statuses only, e.g. data-for-statuses="INTERVIEWING OFFER":
 * shown while one of those is picked. Hidden, their controls are disabled too, so they're neither sent nor required.
 */
function showFieldsFor(select) {
	for (const field of select.form.querySelectorAll('[data-for-statuses]')) {
		const shown = field.dataset.forStatuses.split(' ').includes(select.value);
		field.hidden = !shown;
		const controls = field.matches('input, select, textarea') ? [field] : field.querySelectorAll('input, select, textarea');
		for (const control of controls) {
			control.disabled = !shown;
		}
	}
}

/**
 * The list's selection: the select-all box, the bar that counts the ticked rows, and the texts of the dialog that
 * deletes them. The bar and the dialog carry their texts in data- attributes, in the page's language.
 */
function updateSelection() {
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

const capitalize = (word) => word.charAt(0).toUpperCase() + word.slice(1);

// A menu with data-anchor="<button id>" opens under its button, lined up with its right edge. Before it shows, so it
// never flashes elsewhere; its width comes from the stylesheet.
document.addEventListener('beforetoggle', (event) => {
	const menu = event.target;
	if (!(menu instanceof HTMLElement) || !menu.dataset.anchor || event.newState !== 'open') {
		return;
	}
	const anchor = document.getElementById(menu.dataset.anchor).getBoundingClientRect();
	const width = parseFloat(getComputedStyle(menu).width);
	menu.style.top = `${anchor.bottom + 6}px`;
	menu.style.left = `${Math.max(8, anchor.right - width)}px`;
}, true);

// The side panel opens once HTMX has loaded an application into it. When it closes, its row stops being marked.
// Reloaded rows come unticked, so the selection starts over.
document.addEventListener('htmx:afterSwap', (event) => {
	if (event.detail.target.id === 'drawer-content') {
		const drawer = document.getElementById('drawer');
		if (!drawer.open) {
			drawer.showModal();
		}
	}
});

document.addEventListener('htmx:afterSettle', () => updateSelection());

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

// The share menus offer the browser's share sheet where it has one, e.g. Safari and phones
const showShareSheets = () => {
	if (navigator.share) {
		for (const button of document.querySelectorAll('[data-native-share][hidden]')) {
			button.hidden = false;
		}
	}
};
document.addEventListener('DOMContentLoaded', showShareSheets);
document.addEventListener('htmx:afterSettle', showShareSheets);

document.addEventListener('close', (event) => {
	if (event.target.id === 'drawer') {
		for (const open of document.querySelectorAll('[data-drawer-row].is-open')) {
			open.classList.remove('is-open');
		}
	}
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

document.addEventListener('input', (event) => {
	const input = event.target;
	if (!(input instanceof HTMLInputElement)) {
		return;
	}
	if (input.matches('.tag-text')) {
		const field = input.closest('[data-tag-field]');
		// A comma, typed or pasted, ends each chip before it
		if (field.dataset.splitCommas && input.value.includes(',')) {
			const parts = input.value.split(',');
			const rest = parts.pop();
			parts.forEach((part) => addTag(field, part));
			input.value = rest.trimStart();
			tagsChanged(field);
		}
		showSuggestions(field);
	} else if (input.dataset.examples) {
		document.getElementById(input.dataset.examples).hidden = input.value !== '';
	}
});

document.addEventListener('focusin', (event) => {
	const input = event.target;
	if (!(input instanceof HTMLInputElement)) {
		return;
	}
	if (input.matches('.tag-text')) {
		showSuggestions(input.closest('[data-tag-field]'));
	} else if (input.dataset.examples) {
		// data-examples="<list id>": the examples, while the field is empty
		const examples = document.getElementById(input.dataset.examples);
		examples.hidden = input.value !== '';
		input.setAttribute('aria-expanded', String(!examples.hidden));
	}
});

document.addEventListener('focusout', (event) => {
	const input = event.target;
	if (!(input instanceof HTMLInputElement)) {
		return;
	}
	if (input.matches('.tag-text')) {
		showSuggestions(input.closest('[data-tag-field]'), false);
	} else if (input.dataset.examples) {
		document.getElementById(input.dataset.examples).hidden = true;
		input.setAttribute('aria-expanded', 'false');
	}
});

// A suggestion or an example is picked on mousedown, before the field loses the focus and hides the list
document.addEventListener('mousedown', (event) => {
	const target = event.target instanceof Element ? event.target : null;
	const option = target?.closest('[data-tag-field] [role="option"]');
	if (option) {
		event.preventDefault();
		const field = option.closest('[data-tag-field]');
		addTag(field, option.dataset.value);
		field.querySelector('.tag-text').value = '';
		tagsChanged(field);
		return;
	}
	const example = target?.closest('[data-example]');
	if (example) {
		event.preventDefault();
		const input = document.querySelector(`[data-examples="${example.closest('[role="listbox"]').id}"]`);
		input.value = example.dataset.example;
		example.closest('[role="listbox"]').hidden = true;
		input.form.requestSubmit();
	}
});

/*
 * The Discover page's card (data-swipe-card): dragged far enough left it's discarded, right it's saved, like the
 * buttons with data-swipe="left" and "right"; the arrow keys and A (apply) do the same. While it's dragged, --discard
 * and --save (0 to 1) show what letting go would do.
 */
const SWIPE_DISTANCE = 110;

let dragged = null;

/** Sends the button's decision once the card has flown off that way; HTMX then swaps in the next card. */
function decide(button) {
	const card = document.querySelector('[data-swipe-card]');
	if (!card || card.classList.contains('is-leaving')) {
		return;
	}
	card.classList.add('is-leaving');
	const way = { left: -1, right: 1 }[button.dataset.swipe] ?? 0;
	card.style.transform = way ? `translateX(${way * 130}%) rotate(${way * 16}deg)` : 'translateY(-40%) scale(0.9)';
	card.style.opacity = '0';
	button.form.querySelector('input[name="decision"]').value = button.value;
	setTimeout(() => button.form.requestSubmit(), 180);
}

function dragTo(card, distance) {
	card.style.transform = `translateX(${distance}px) rotate(${distance / 18}deg)`;
	card.style.setProperty('--save', String(Math.min(1, Math.max(0, distance / SWIPE_DISTANCE))));
	card.style.setProperty('--discard', String(Math.min(1, Math.max(0, -distance / SWIPE_DISTANCE))));
}

document.addEventListener('pointerdown', (event) => {
	const target = event.target instanceof Element ? event.target : null;
	const card = target?.closest('[data-swipe-card]');
	// Links and buttons on the card stay clickable
	if (!card || event.button !== 0 || target.closest('a, button')) {
		return;
	}
	dragged = { card, from: event.clientX, distance: 0 };
	card.setPointerCapture(event.pointerId);
	card.classList.add('is-dragging');
});

document.addEventListener('pointermove', (event) => {
	if (dragged) {
		dragged.distance = event.clientX - dragged.from;
		dragTo(dragged.card, dragged.distance);
	}
});

const letGo = () => {
	if (!dragged) {
		return;
	}
	const { card, distance } = dragged;
	dragged = null;
	card.classList.remove('is-dragging');
	if (Math.abs(distance) >= SWIPE_DISTANCE) {
		decide(document.querySelector(`[data-swipe="${distance > 0 ? 'right' : 'left'}"]`));
	} else {
		dragTo(card, 0);
		card.style.transform = '';
	}
};
document.addEventListener('pointerup', letGo);
document.addEventListener('pointercancel', letGo);

document.addEventListener('keydown', (event) => {
	const target = event.target;
	const typing = target instanceof HTMLElement && target.closest('input, textarea, select, [contenteditable]');
	if (typing || event.altKey || event.ctrlKey || event.metaKey || !document.querySelector('[data-swipe-card]')) {
		return;
	}
	const way = { ArrowLeft: 'left', ArrowRight: 'right', a: 'up', A: 'up' }[event.key];
	const button = way && document.querySelector(`[data-swipe="${way}"]`);
	if (button) {
		event.preventDefault();
		if (button.dataset.openLink) {
			window.open(button.dataset.openLink, '_blank', 'noopener');
		}
		decide(button);
	}
});

// data-close-dialog-on-success: a form in a dialog that closes it once HTMX has sent it, e.g. saving a search
document.addEventListener('htmx:afterRequest', (event) => {
	const form = event.detail.elt;
	if (event.detail.successful && form instanceof HTMLFormElement && form.matches('[data-close-dialog-on-success]')) {
		form.closest('dialog')?.close();
		form.reset();
	}
});
