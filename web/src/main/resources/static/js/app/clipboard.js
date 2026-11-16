/**
 * Copies the text to the clipboard. Where the page may not write to it (an embedded browser, an older one), it copies
 * a selection of the text instead, the older way.
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
		const copied = document.execCommand('copy');
		area.remove();
		if (!copied) {
			throw new Error('The browser did not copy the text');
		}
	}
}
