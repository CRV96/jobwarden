/*
 * Toasts: what a page, or a part of it HTMX swapped in, has to say in passing, e.g. that the mailbox is connected. The
 * server writes it as a hidden [data-toast] (layout.html's toast fragment); it shows here as a card in the corner, from
 * the #toast-card template, in the same place as the reminders' cards, and goes after a few seconds, or when closed.
 */
const TOAST_FOR = 6_000;

function showToasts() {
	const template = document.getElementById('toast-card');
	const cards = document.getElementById('reminder-cards');
	if (!template || !cards) {
		return;
	}
	for (const toast of document.querySelectorAll('[data-toast]')) {
		const text = toast.textContent.trim();
		toast.remove();
		if (!text) {
			continue;
		}
		const card = template.content.firstElementChild.cloneNode(true);
		card.querySelector('[data-body]').textContent = text;
		cards.append(card);
		setTimeout(() => card.remove(), TOAST_FOR);
	}
}

document.addEventListener('DOMContentLoaded', showToasts);
document.addEventListener('htmx:afterSettle', showToasts);
