/*
 * The pipeline's cards (pipeline/board.html) move to another column by dragging: dropped there, the card goes to the
 * top of that column at once, and its timeline gets that status, as the status buttons of its side panel give it. The
 * answer's applications-changed, sent only once the status is saved, then reloads the board, with the counts. When it
 * isn't saved, the card goes back where it was: on a failure the banner says so; on a refusal its side panel opens.
 * Rejected asks why, so a card dropped there stays put and its side panel opens with the question. A card moves once
 * at a time: it can't be dragged again until its move is answered. A click still opens the card.
 */
let draggedCard = null;

const dropColumn = (event) => event.target instanceof Element ? event.target.closest('[data-drop-status]') : null;

const clearDropTargets = () => {
	for (const column of document.querySelectorAll('.is-drop-target')) {
		column.classList.remove('is-drop-target');
	}
};

document.addEventListener('dragstart', (event) => {
	const card = event.target instanceof Element ? event.target.closest('[data-drag-card]') : null;
	if (!card) {
		return;
	}
	draggedCard = card;
	event.dataTransfer.effectAllowed = 'move';
	event.dataTransfer.setData('text/plain', card.dataset.dragCard);
	card.classList.add('is-dragged');
});

document.addEventListener('dragend', () => {
	draggedCard?.classList.remove('is-dragged');
	draggedCard = null;
	clearDropTargets();
});

// Only another column takes the card: dropping it back where it was changes nothing
document.addEventListener('dragover', (event) => {
	const column = dropColumn(event);
	if (!draggedCard || !column || column.dataset.dropStatus === draggedCard.dataset.status) {
		return;
	}
	event.preventDefault();
	event.dataTransfer.dropEffect = 'move';
	if (!column.classList.contains('is-drop-target')) {
		clearDropTargets();
		column.classList.add('is-drop-target');
	}
});

document.addEventListener('dragleave', (event) => {
	const column = dropColumn(event);
	if (column && !column.contains(event.relatedTarget)) {
		column.classList.remove('is-drop-target');
	}
});

document.addEventListener('drop', (event) => {
	const column = dropColumn(event);
	if (!draggedCard || !column || column.dataset.dropStatus === draggedCard.dataset.status) {
		return;
	}
	event.preventDefault();
	const card = draggedCard;
	clearDropTargets();
	if (column.dataset.dropStatus === 'REJECTED') {
		askWhyRejected(card);
		return;
	}
	const before = { column: card.parentElement, next: card.nextElementSibling, status: card.dataset.status };
	column.prepend(card);
	card.dataset.status = column.dataset.dropStatus;
	sendStatus(card, before);
});

/**
 * Gives the card's application its new status. Saved, the answer says applications-changed; else the card goes back
 * to its column and place, and on a refusal (an answer without it) its side panel opens.
 */
function sendStatus(card, before) {
	const url = card.dataset.eventsUrl;
	card.draggable = false;
	const answered = (event) => {
		if (event.detail.requestConfig?.path !== url) {
			return;
		}
		document.removeEventListener('htmx:afterRequest', answered);
		card.draggable = true;
		const saved = event.detail.successful
			&& (event.detail.xhr.getResponseHeader('HX-Trigger') ?? '').includes('applications-changed');
		if (!saved) {
			before.column.insertBefore(card, (before.next?.parentElement === before.column) ? before.next : null);
			card.dataset.status = before.status;
			if (event.detail.successful) {
				card.click();
			}
		}
	};
	document.addEventListener('htmx:afterRequest', answered);
	htmx.ajax('POST', url, { swap: 'none', values: { stageCategory: card.dataset.status, view: 'DRAWER' } });
}

/** Opens the card's side panel with its "Rejected…" question shown, the way its button there shows it. */
function askWhyRejected(card) {
	const opened = (event) => {
		if (event.detail.target.id !== 'drawer-content') {
			return;
		}
		document.removeEventListener('htmx:afterSwap', opened);
		const id = `reject-${card.dataset.dragCard}`;
		event.detail.target.querySelector(`[data-toggle-hidden="${id}"]`)?.click();
		document.getElementById(id)?.querySelector('select')?.focus();
	};
	document.addEventListener('htmx:afterSwap', opened);
	card.click();
}
