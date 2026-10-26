/*
 * What the pages do in the browser beyond HTMX: the theme and the folding sidebar, dialogs and menus, the list's
 * checkboxes and the side panel. Elements ask for it with data- attributes, and one listener per event on the
 * document handles them all, so it works the same for the parts HTMX swaps in.
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

	// data-expand: shows all of the clamped text before it (line-clamp-12), or clamps it again
	const expander = target.closest('[data-expand]');
	if (expander) {
		const text = expander.previousElementSibling;
		const expanded = !text.classList.toggle('line-clamp-12');
		expander.textContent = expanded ? expander.dataset.less : expander.dataset.more;
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
