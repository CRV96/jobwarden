/*
 * A job's description, edited with its bold, italics and lists (applications/fragments :: description): the editor
 * (data-rich-editor) is a box to type in, whose buttons (data-rich-command) and Ctrl/⌘+B or I format the text. As it's
 * typed, it's written to the form's hidden field (data-rich-markdown) as Markdown, which the server keeps and shows
 * (RichText): **bold**, *italics*, a line starting with "- " or "1. " for a point of a list, a blank line between
 * paragraphs. What's pasted keeps that much of its formatting, and no more. Cancel puts back what it started with.
 */

/** Elements that are a block of their own, as a paragraph: a line apart from what's before and after. */
const BLOCKS = new Set(['P', 'DIV', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'BLOCKQUOTE', 'PRE', 'SECTION', 'ARTICLE',
	'HEADER', 'FOOTER', 'TABLE', 'TR', 'DL', 'DT', 'DD', 'FIGURE']);

/** Elements whose content is never text of the posting. */
const DROPPED = new Set(['SCRIPT', 'STYLE', 'TEMPLATE', 'NOSCRIPT', 'IFRAME', 'OBJECT', 'SVG', 'IMG', 'VIDEO', 'AUDIO',
	'CANVAS', 'BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'HEAD', 'TITLE', 'META', 'LINK']);

/** Bold or italics around the text, its spaces left outside, as Markdown needs: "** a **" isn't bold. */
function wrap(text, mark) {
	const [, before, inner, after] = /^(\s*)([\s\S]*?)(\s*)$/.exec(text);
	return inner ? `${before}${mark}${inner}${mark}${after}` : text;
}

/** A point of a list is one line. */
function oneLine(text) {
	return text.replace(/\s*\n\s*/g, ' ').trim();
}

function isBold(element) {
	return element.tagName === 'B' || element.tagName === 'STRONG' || Number(element.style?.fontWeight) >= 600
		|| element.style?.fontWeight === 'bold';
}

function isItalic(element) {
	return element.tagName === 'I' || element.tagName === 'EM' || element.style?.fontStyle === 'italic';
}

function markdownOf(node) {
	return [...node.childNodes].map(partOf).join('');
}

function partOf(node) {
	if (node.nodeType === Node.TEXT_NODE) {
		// \s takes in the no-break spaces pages put between words too
		return node.textContent.replace(/\s+/g, ' ');
	}
	if (node.nodeType !== Node.ELEMENT_NODE || DROPPED.has(node.tagName)) {
		return '';
	}
	const tag = node.tagName;
	if (tag === 'BR') {
		return '\n';
	}
	if (tag === 'UL' || tag === 'OL') {
		const points = [...node.children].filter((child) => child.tagName === 'LI');
		return `\n${points.map((point, index) => `${tag === 'OL' ? `${index + 1}.` : '-'} ${oneLine(markdownOf(point))}`)
			.join('\n')}\n`;
	}
	if (tag === 'LI') {
		return `\n- ${oneLine(markdownOf(node))}\n`;
	}
	if (BLOCKS.has(tag)) {
		return `\n\n${markdownOf(node).trim()}\n\n`;
	}
	let text = markdownOf(node);
	if (tag === 'A') {
		// A web address shows as itself, and becomes a link again; other links keep where they lead after their words
		const href = node.getAttribute('href') ?? '';
		text = (/^https?:\/\//i.test(href) && !text.includes(href)) ? `${text} (${href})` : text;
	}
	if (isBold(node)) {
		text = wrap(text, '**');
	}
	if (isItalic(node)) {
		text = wrap(text, '*');
	}
	return text;
}

/** The editor's text as Markdown: no spaces at the lines' ends, at most one blank line in a row. */
function toMarkdown(editor) {
	return markdownOf(editor)
		.split('\n')
		.map((line) => line.trim())
		.join('\n')
		.replace(/\n{3,}/g, '\n\n')
		.trim();
}

function escaped(text) {
	const span = document.createElement('span');
	span.textContent = text;
	return span.innerHTML;
}

/** Pasted plain text: its paragraphs, its lines in them. The server makes lists of its "- " and "•" lines. */
function plainHtml(text) {
	return text.replace(/\r\n?/g, '\n')
		.split(/\n\s*\n/)
		.filter((paragraph) => paragraph.trim())
		.map((paragraph) => `<p>${paragraph.split('\n').map(escaped).join('<br>')}</p>`)
		.join('');
}

/**
 * Pasted HTML, e.g. from a posting's page: its paragraphs, lists, bold and italics, as elements of its own with no
 * attribute. The page it came from is parsed apart (DOMParser), where nothing of it runs, and nothing else is kept.
 */
function cleanHtml(html) {
	const source = new DOMParser().parseFromString(html, 'text/html').body;
	const target = document.createElement('div');
	const copy = (from, into) => {
		for (const node of from.childNodes) {
			if (node.nodeType === Node.TEXT_NODE) {
				into.append(node.textContent);
				continue;
			}
			if (node.nodeType !== Node.ELEMENT_NODE || DROPPED.has(node.tagName)) {
				continue;
			}
			const tag = node.tagName;
			let element = null;
			if (tag === 'BR') {
				into.append(document.createElement('br'));
				continue;
			} else if (tag === 'UL' || tag === 'OL' || tag === 'LI') {
				element = document.createElement(tag);
			} else if (BLOCKS.has(tag)) {
				element = document.createElement('p');
			}
			let inner = element ?? into;
			if (isBold(node)) {
				const bold = document.createElement('strong');
				(element ?? into).append(bold);
				inner = bold;
			}
			if (isItalic(node)) {
				const italic = document.createElement('em');
				inner.append(italic);
				inner = italic;
			}
			copy(node, inner);
			if (element) {
				into.append(element);
			}
		}
	};
	copy(source, target);
	return target.innerHTML;
}

function sync(editor) {
	const field = editor.closest('form')?.querySelector('[data-rich-markdown]');
	if (field) {
		field.value = toMarkdown(editor);
	}
}

document.addEventListener('input', (event) => {
	const editor = event.target instanceof Element ? event.target.closest('[data-rich-editor]') : null;
	if (editor) {
		sync(editor);
	}
});

// Before HTMX reads the form, in case the browser formatted without an input event
document.addEventListener('submit', (event) => {
	const editor = event.target instanceof HTMLFormElement ? event.target.querySelector('[data-rich-editor]') : null;
	if (editor) {
		sync(editor);
	}
}, true);

// Enter starts a paragraph, as in a word processor, and Shift+Enter a line in it
document.addEventListener('focusin', (event) => {
	if (event.target instanceof Element && event.target.matches('[data-rich-editor]')) {
		document.execCommand('defaultParagraphSeparator', false, 'p');
	}
});

// The buttons keep the selection where it is in the editor, which a click would take away
document.addEventListener('mousedown', (event) => {
	if (event.target instanceof Element && event.target.closest('[data-rich-command]')) {
		event.preventDefault();
	}
});

document.addEventListener('click', (event) => {
	const button = event.target instanceof Element ? event.target.closest('[data-rich-command]') : null;
	const editor = button?.closest('form')?.querySelector('[data-rich-editor]');
	if (!editor) {
		return;
	}
	if (!editor.contains(document.getSelection()?.anchorNode ?? null)) {
		editor.focus();
	}
	// execCommand is what every browser formats a box to type in with, and fires the input event that syncs it
	document.execCommand(button.dataset.richCommand);
	sync(editor);
});

document.addEventListener('paste', (event) => {
	const editor = event.target instanceof Element ? event.target.closest('[data-rich-editor]') : null;
	if (!editor || !event.clipboardData) {
		return;
	}
	event.preventDefault();
	const html = event.clipboardData.getData('text/html');
	document.execCommand('insertHTML', false, html ? cleanHtml(html) : plainHtml(event.clipboardData.getData('text/plain')));
	sync(editor);
});

// Cancel: the text the editor started with, which the form's reset puts back in the hidden field too
document.addEventListener('reset', (event) => {
	const form = event.target;
	const editor = form instanceof HTMLFormElement ? form.querySelector('[data-rich-editor]') : null;
	const original = form?.querySelector?.('[data-rich-original]');
	if (editor && original) {
		editor.innerHTML = original.innerHTML;
	}
});
