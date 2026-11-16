/**
 * Copies the text to the clipboard. Where the page may not write to it (an embedded browser, an older one, or a page
 * served over plain http from another computer, which browsers give no Clipboard API), it copies a selection of the
 * text instead, the older way: execCommand is deprecated, but nothing else copies there.
 */
export async function copyText(text) {
	try {
		await navigator.clipboard.writeText(text);
	} catch {
		const area = document.createElement('textarea');
		area.value = text;
		area.readOnly = true;
		area.className = 'fixed top-0 left-0 opacity-0';
		document.body.append(area);
		area.select();
		const copied = document.execCommand('copy'); // NOSONAR: deprecated, and the only way left (above)
		area.remove();
		if (!copied) {
			throw new Error('The browser did not copy the text');
		}
	}
}
