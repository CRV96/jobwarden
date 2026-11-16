/*
 * Dialogs, and the side panel (#drawer) that shows an application over the list or the pipeline.
 */

/** data-open-dialog="<id>": opens that dialog. */
export function openDialog(target) {
	const opener = target.closest('[data-open-dialog]');
	if (!opener) {
		return false;
	}
	document.getElementById(opener.dataset.openDialog)?.showModal();
	return true;
}

/** data-close-dialog: closes the dialog it's in. */
export function closeDialog(target) {
	const closer = target.closest('[data-close-dialog]');
	if (!closer) {
		return false;
	}
	closer.closest('dialog')?.close();
	return true;
}

/** A click on a dialog's backdrop lands on the dialog itself, outside its box: it closes the dialog. */
export function closeOnBackdrop(target, event) {
	if (!(target instanceof HTMLDialogElement && target.open)) {
		return false;
	}
	const box = target.getBoundingClientRect();
	const outside = event.clientX < box.left || event.clientX > box.right || event.clientY < box.top
		|| event.clientY > box.bottom;
	if (outside) {
		target.close();
	}
	return true;
}

/** A row of the list, or a card of the pipeline, marks itself while its application is in the side panel. */
export function markDrawerRow(target) {
	const row = target.closest('[data-drawer-row]');
	if (!row) {
		return false;
	}
	for (const open of document.querySelectorAll('[data-drawer-row].is-open')) {
		open.classList.remove('is-open');
	}
	row.classList.add('is-open');
	return true;
}

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

document.addEventListener('close', (event) => {
	if (event.target.id === 'drawer') {
		for (const open of document.querySelectorAll('[data-drawer-row].is-open')) {
			open.classList.remove('is-open');
		}
	}
}, true);

// data-close-dialog-on-success: a form in a dialog that closes it once HTMX has sent it, e.g. saving a search
document.addEventListener('htmx:afterRequest', (event) => {
	const form = event.detail.elt;
	if (event.detail.successful && form instanceof HTMLFormElement && form.matches('[data-close-dialog-on-success]')) {
		form.closest('dialog')?.close();
		form.reset();
	}
});

/*
 * data-row-click: a click on the row itself, not on a link, a box or a label in it, is the row's: it fires row-click,
 * which the row's HTMX request waits for (hx-trigger="row-click"). A filter in hx-trigger ("click[...]") would say the
 * same, but HTMX runs it as code, which the Content Security Policy refuses: the filter would let every click through.
 */
document.addEventListener('click', (event) => {
	const target = event.target instanceof Element ? event.target : null;
	const row = target?.closest('[data-row-click]');
	if (row && !target.closest('a, input, label')) {
		htmx.trigger(row, 'row-click');
	}
});
