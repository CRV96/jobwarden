/*
 * What forms do beyond HTML: fields shown for some statuses only, rows added and removed, characters counted, files
 * checked for size, examples offered, and fields filled in from a pick (a mail provider, a chip), or emptied (a time).
 */

/**
 * Shows how many characters the field has, of the most it takes: "212/300", in red past it. It counts each letter
 * once, an emoji too, as LinkedIn does; data-count-max says the most when maxlength, which counts an emoji as two,
 * leaves room for more.
 */
function countCharacters(field) {
	const counter = document.getElementById(field.dataset.count);
	if (counter) {
		const length = [...field.value].length;
		const max = Number(field.dataset.countMax) || field.maxLength;
		counter.textContent = `${length}/${max}`;
		counter.classList.toggle('text-danger', max > 0 && length > max);
	}
}

/**
 * Numbers the fields of a list's rows from 0, in their order: experience[0].role, experience[1].role... The server
 * binds them by number, so a removed row mustn't leave a gap.
 */
function numberRows(rows) {
	[...rows.querySelectorAll('[data-row]')].forEach((row, index) => {
		for (const field of row.querySelectorAll('[name]')) {
			field.name = field.name.replace(/\[(\d+|i)\]/, `[${index}]`);
		}
	});
}

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
 * data-clear-previous: empties the field right before the button, e.g. a time: one emptied only in part is invalid,
 * and the browser wouldn't send the form.
 */
export function clearPrevious(target) {
	const clearer = target.closest('[data-clear-previous]');
	if (!clearer) {
		return false;
	}
	const field = clearer.previousElementSibling;
	if (field instanceof HTMLInputElement) {
		field.value = '';
		field.dispatchEvent(new Event('input', { bubbles: true }));
	}
	return true;
}

/**
 * data-fill="<selector>" with data-value: puts the value in that field of the form, e.g. a job's employment type picked
 * from its chips, and the cursor after it.
 */
export function fill(target) {
	const chip = target.closest('[data-fill]');
	if (!chip) {
		return false;
	}
	const field = chip.closest('form')?.querySelector(chip.dataset.fill);
	if (field instanceof HTMLInputElement) {
		field.value = chip.dataset.value ?? '';
		field.dispatchEvent(new Event('input', { bubbles: true }));
		field.focus();
	}
	return true;
}

/**
 * data-toggle-hidden="<id>": shows or hides that element, e.g. the rejection reason form. With data-focus="<selector>"
 * it only shows it, and puts the cursor in that field of it, e.g. the job's location.
 */
export function toggleHidden(target) {
	const toggler = target.closest('[data-toggle-hidden]');
	if (!toggler) {
		return false;
	}
	const element = document.getElementById(toggler.dataset.toggleHidden);
	if (element) {
		element.hidden = toggler.dataset.focus ? false : !element.hidden;
		toggler.setAttribute('aria-expanded', String(!element.hidden));
		if (toggler.dataset.focus) {
			element.querySelector(toggler.dataset.focus)?.focus();
		}
	}
	return true;
}

/** data-add-row="<list>": adds a blank row to that list of the CV page, from its <template data-row-template>. */
export function addRow(target) {
	const rowAdder = target.closest('[data-add-row]');
	if (!rowAdder) {
		return false;
	}
	const rows = document.querySelector(`[data-rows="${rowAdder.dataset.addRow}"]`);
	const template = document.querySelector(`template[data-row-template="${rowAdder.dataset.addRow}"]`);
	if (rows && template) {
		rows.append(template.content.cloneNode(true));
		numberRows(rows);
		rows.lastElementChild?.querySelector('input, textarea')?.focus();
	}
	return true;
}

/**
 * data-remove-row: removes the row it's in, and says the form changed, so one that saves as it changes (the offer's
 * terms) saves without it.
 */
export function removeRow(target) {
	const rowRemover = target.closest('[data-remove-row]');
	if (!rowRemover) {
		return false;
	}
	const rows = rowRemover.closest('[data-rows]');
	rowRemover.closest('[data-row]')?.remove();
	if (rows) {
		numberRows(rows);
		rows.dispatchEvent(new Event('change', { bubbles: true }));
	}
	return true;
}

/** data-max-bytes: a file field that refuses a file larger than that before it's sent, with its data-too-large message. */
export function limitFileSize(target) {
	if (!target.matches('input[type="file"][data-max-bytes]')) {
		return false;
	}
	const file = target.files?.[0];
	target.setCustomValidity(file && file.size > Number(target.dataset.maxBytes) ? target.dataset.tooLarge ?? '' : '');
	target.reportValidity();
	return true;
}

/** data-status-select: the status picked shows the fields that are for it (data-for-statuses). */
export function showStatusFields(target) {
	if (!target.matches('[data-status-select]')) {
		return false;
	}
	showFieldsFor(target);
	return true;
}

/** data-auto-submit: each change sends the form, e.g. the offers page's filters. */
export function autoSubmit(target) {
	if (!target.matches('[data-auto-submit]')) {
		return false;
	}
	target.form?.requestSubmit();
	return true;
}

/** data-count="<id>": the element with that id counts the field's characters, as LinkedIn does, against its maxlength. */
export function countTyped(target) {
	if (!(target instanceof HTMLTextAreaElement && target.dataset.count)) {
		return false;
	}
	countCharacters(target);
	return true;
}

/*
 * data-examples="<list id>": a field that offers examples while it's empty. A click on one (data-example) puts it in
 * the field and sends the form; it's picked on mousedown, before the field loses the focus and hides the list. A field
 * of chips (tags.js) has its own suggestions instead.
 */
const offersExamples = (target) => target instanceof HTMLInputElement && target.dataset.examples
	&& !target.matches('.tag-text');

export function examplesTyped(target) {
	if (!offersExamples(target)) {
		return false;
	}
	document.getElementById(target.dataset.examples).hidden = target.value !== '';
	return true;
}

document.addEventListener('focusin', (event) => {
	const input = event.target;
	if (offersExamples(input)) {
		const examples = document.getElementById(input.dataset.examples);
		examples.hidden = input.value !== '';
		input.setAttribute('aria-expanded', String(!examples.hidden));
	}
});

document.addEventListener('focusout', (event) => {
	const input = event.target;
	if (offersExamples(input)) {
		document.getElementById(input.dataset.examples).hidden = true;
		input.setAttribute('aria-expanded', 'false');
	}
});

document.addEventListener('mousedown', (event) => {
	const target = event.target instanceof Element ? event.target : null;
	const example = target?.closest('[data-example]');
	if (example && !target.closest('[data-tag-field] [role="option"]')) {
		event.preventDefault();
		const input = document.querySelector(`[data-examples="${example.closest('[role="listbox"]').id}"]`);
		input.value = example.dataset.example;
		example.closest('[role="listbox"]').hidden = true;
		input.form.requestSubmit();
	}
});

// The CV page's preview: the CV built from the fields, or the uploaded file, one at a time. Each tab shows its frame
// and its buttons (data-preview-for) and hides the other's.
document.addEventListener('change', (event) => {
	const tab = event.target;
	if (!(tab instanceof HTMLInputElement) || !tab.matches('[data-preview-tab]')) {
		return;
	}
	tab.closest('aside')?.querySelectorAll('[data-preview-for]').forEach((element) => {
		element.hidden = element.dataset.previewFor !== tab.value;
	});
});

// The inbox's mailbox form: picking a provider fills its IMAP server and port in (data-host, data-port); Other
// leaves the server to type. Proton Mail Bridge, on this computer (data-local), has its own password and hint.
document.addEventListener('change', (event) => {
	const provider = event.target;
	if (!(provider instanceof HTMLSelectElement) || !provider.matches('[data-mail-provider]')) {
		return;
	}
	const picked = provider.selectedOptions[0]?.dataset ?? {};
	const field = provider.form?.querySelector('[data-mail-host]');
	if (field) {
		field.value = picked.host ?? '';
		if (!picked.host) {
			field.focus();
		}
	}
	const port = provider.form?.querySelector('[data-mail-port]');
	if (port && picked.port) {
		port.value = picked.port;
	}
	const local = picked.local === 'true';
	provider.form?.querySelectorAll('[data-mail-local]').forEach((element) => {
		element.hidden = (element.dataset.mailLocal === 'true') !== local;
	});
});
