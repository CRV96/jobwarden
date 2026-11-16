/*
 * The pipeline's cards (pipeline/board.html) move to another column by dragging: dropped there, the card goes to the
 * top of that column at once, and its timeline gets that status, as the status buttons of its side panel give it. The
 * answer's applications-changed then reloads the board, with the counts; when the server doesn't take it (the banner
 * says so), the card goes back where it was. A click still opens the card.
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
	const before = { column: card.parentElement, next: card.nextElementSibling, status: card.dataset.status };
	column.prepend(card);
	card.dataset.status = column.dataset.dropStatus;
	sendStatus(card, before);
});

/** Gives the card's application its new status; on a failure, the card goes back to its column and place. */
function sendStatus(card, before) {
	const url = card.dataset.eventsUrl;
	const answered = (event) => {
		if (event.detail.requestConfig?.path !== url) {
			return;
		}
		document.removeEventListener('htmx:afterRequest', answered);
		if (!event.detail.successful) {
			before.column.insertBefore(card, (before.next?.parentElement === before.column) ? before.next : null);
			card.dataset.status = before.status;
		}
	};
	document.addEventListener('htmx:afterRequest', answered);
	htmx.ajax('POST', url, { swap: 'none', values: { stageCategory: card.dataset.status, view: 'DRAWER' } });
}
