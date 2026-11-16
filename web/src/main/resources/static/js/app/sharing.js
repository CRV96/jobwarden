/*
 * Sharing a job and copying texts: the browser's share sheet where it has one, the clipboard everywhere.
 */
import { copyText } from './clipboard.js';

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

/** data-native-share: the browser's own share sheet, with what the data-share-* attributes say. */
export function share(target) {
	const sharer = target.closest('[data-native-share]');
	if (!sharer) {
		return false;
	}
	const { shareTitle: title, shareText: text, shareUrl: url } = sharer.dataset;
	// Cancelling the sheet rejects: nothing to do
	navigator.share({ title, text, ...(url && { url }) }).catch(() => {});
	return true;
}

/**
 * data-copy-text: copies the text, and says so for a moment. data-copy-from="<ids>": copies what the fields with those
 * ids hold now, as they're being changed, a blank line between them.
 */
export function copy(target) {
	const copier = target.closest('[data-copy-text], [data-copy-from]');
	if (!copier) {
		return false;
	}
	const text = copier.dataset.copyFrom
		? copier.dataset.copyFrom.split(' ')
			.map((id) => document.getElementById(id)?.value ?? '')
			.filter((value) => value.trim() !== '')
			.join('\n\n')
		: copier.dataset.copyText;
	copyText(text).then(() => {
		copier.querySelector('[data-copy-idle]').hidden = true;
		copier.querySelector('[data-copy-done]').hidden = false;
		setTimeout(() => {
			copier.querySelector('[data-copy-idle]').hidden = false;
			copier.querySelector('[data-copy-done]').hidden = true;
		}, 1500);
	}).catch(() => {
		// Nothing copied: the button stays as it was
	});
	return true;
}
