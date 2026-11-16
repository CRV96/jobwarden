/*
 * The Discover page's card (data-swipe-card): dragged far enough left it's discarded, right it's saved, like the
 * buttons with data-swipe="left" and "right"; the arrow keys and A (apply) do the same. While it's dragged, --discard
 * and --save (0 to 1) show what letting go would do. One pointer drags it at a time, and a drag the browser cancels
 * (a scroll, a gesture) decides nothing. When the server doesn't take a decision, the card comes back.
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
	const form = button.form;
	form.querySelector('input[name="decision"]').value = button.value;
	// HTMX swaps the next card in on success only: on a failure (the banner says so), this one comes back
	const answered = (event) => {
		if (event.detail.elt !== form) {
			return;
		}
		form.removeEventListener('htmx:afterRequest', answered);
		if (!event.detail.successful) {
			bringBack(card);
		}
	};
	form.addEventListener('htmx:afterRequest', answered);
	setTimeout(() => form.requestSubmit(), 180);
}

function bringBack(card) {
	card.classList.remove('is-leaving');
	card.style.transform = '';
	card.style.opacity = '';
	card.style.removeProperty('--save');
	card.style.removeProperty('--discard');
}

function dragTo(card, distance) {
	card.style.transform = `translateX(${distance}px) rotate(${distance / 18}deg)`;
	card.style.setProperty('--save', String(Math.min(1, Math.max(0, distance / SWIPE_DISTANCE))));
	card.style.setProperty('--discard', String(Math.min(1, Math.max(0, -distance / SWIPE_DISTANCE))));
}

document.addEventListener('pointerdown', (event) => {
	const target = event.target instanceof Element ? event.target : null;
	const card = target?.closest('[data-swipe-card]');
	// Links and buttons on the card stay clickable; a second pointer doesn't take over the drag
	if (!card || dragged || event.button !== 0 || target.closest('a, button')) {
		return;
	}
	dragged = { card, pointerId: event.pointerId, from: event.clientX, distance: 0 };
	card.setPointerCapture(event.pointerId);
	card.classList.add('is-dragging');
});

const isDragging = (event) => dragged !== null && event.pointerId === dragged.pointerId;

document.addEventListener('pointermove', (event) => {
	if (isDragging(event)) {
		dragged.distance = event.clientX - dragged.from;
		dragTo(dragged.card, dragged.distance);
	}
});

/** Ends the drag: let go far enough, it decides (decided); else, or cancelled, the card goes back to its place. */
function endDrag(event, decided) {
	if (!isDragging(event)) {
		return;
	}
	const { card, distance } = dragged;
	dragged = null;
	card.classList.remove('is-dragging');
	if (decided && Math.abs(distance) >= SWIPE_DISTANCE) {
		decide(document.querySelector(`[data-swipe="${distance > 0 ? 'right' : 'left'}"]`));
	} else {
		dragTo(card, 0);
		card.style.transform = '';
	}
}
document.addEventListener('pointerup', (event) => endDrag(event, true));
document.addEventListener('pointercancel', (event) => endDrag(event, false));

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

/**
 * data-swipe="left|right|up": a decision on the card by its button, which flies off first. Applying opens the posting
 * now, while the click still lets a page open a tab.
 */
export function decideByClick(target, event) {
	const decision = target.closest('[data-swipe]');
	if (!decision) {
		return false;
	}
	event.preventDefault();
	if (decision.dataset.openLink) {
		window.open(decision.dataset.openLink, '_blank', 'noopener');
	}
	decide(decision);
	return true;
}
