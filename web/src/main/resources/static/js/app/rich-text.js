/*
 * A job's description, edited with its bold, italics and lists (applications/fragments :: description): the editor
 * (data-rich-editor) is a box to type in, whose buttons (data-rich-command) and Ctrl/⌘+B or I format the text. As it's
 * typed, it's written to the form's hidden field (data-rich-markdown) as Markdown, which the server keeps and shows
 * (RichText): **bold**, *italics*, a line starting with "- " or "1. " for a point of a list, a blank line between
 * paragraphs. What's pasted or dropped keeps that much of its formatting, and no more (tidy). Cancel puts back what it
 * started with.
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

/** Bold by its tag or its style; not a b that says it's normal, as Google Docs wraps everything it copies in */
function isBold(element) {
	const weight = element.style?.fontWeight ?? '';
	if (weight === 'normal' || (Number(weight) > 0 && Number(weight) < 600)) {
		return false;
	}
	return element.tagName === 'B' || element.tagName === 'STRONG' || Number(weight) >= 600 || weight === 'bold';
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

/** Where the caret is, as how many characters of the editor's text come before it; null outside the editor */
function caretOffset(editor) {
	const selection = document.getSelection();
	if (!selection?.rangeCount || !editor.contains(selection.focusNode)) {
		return null;
	}
	const before = document.createRange();
	before.selectNodeContents(editor);
	before.setEnd(selection.focusNode, selection.focusOffset);
	return before.toString().length;
}

/** Puts the caret back that many characters into the editor's text, or at its end */
function placeCaret(editor, offset) {
	const walker = document.createTreeWalker(editor, NodeFilter.SHOW_TEXT);
	let left = offset;
	for (let text = walker.nextNode(); text; text = walker.nextNode()) {
		if (left <= text.length) {
			document.getSelection().collapse(text, left);
			return;
		}
		left -= text.length;
	}
	document.getSelection().collapse(editor, editor.childNodes.length);
}

/** The element in place of another, with its children: the same text nodes, moved */
function replaced(node, tag) {
	const element = document.createElement(tag);
	element.append(...node.childNodes);
	node.replaceWith(element);
	return element;
}

/**
 * What a paste or a drop brought, the editor's own few tags alone, with no attribute: paragraphs, line breaks, lists,
 * bold and italics. A block becomes a paragraph, a bold or italic span the tag that says so, anything else gives way
 * to its text, and what's never text (images, scripts, forms) goes. The browser pasted it itself, as HTML it parsed:
 * nothing here reads HTML from the clipboard.
 */
function tidy(parent) {
	for (const node of [...parent.childNodes]) {
		if (node.nodeType === Node.TEXT_NODE) {
			continue;
		}
		if (node.nodeType !== Node.ELEMENT_NODE || DROPPED.has(node.tagName)) {
			node.remove();
			continue;
		}
		tidy(node);
		const tag = node.tagName;
		const bold = isBold(node);
		const italic = isItalic(node);
		let kept;
		if (['BR', 'P', 'UL', 'OL', 'LI'].includes(tag)) {
			kept = node;
		} else if (BLOCKS.has(tag)) {
			kept = replaced(node, 'p');
		} else if (bold || italic) {
			// b, strong, i, em, or a span whose style says so
			const wanted = bold ? 'STRONG' : 'EM';
			kept = (tag === wanted) ? node : replaced(node, wanted.toLowerCase());
			if (bold && italic) {
				const both = document.createElement('em');
				both.append(...kept.childNodes);
				kept.append(both);
			}
		} else {
			// A plain span, a link, a font: its text alone
			node.replaceWith(...node.childNodes);
			continue;
		}
		for (const name of kept.getAttributeNames()) {
			kept.removeAttribute(name);
		}
	}
}

function sync(editor) {
	const field = editor.closest('form')?.querySelector('[data-rich-markdown]');
	if (field) {
		field.value = toMarkdown(editor);
	}
}

document.addEventListener('input', (event) => {
	const editor = event.target instanceof Element ? event.target.closest('[data-rich-editor]') : null;
	if (!editor) {
		return;
	}
	// What a paste or a drop brought keeps the editor's formatting alone, the caret where it was
	if (event.inputType === 'insertFromPaste' || event.inputType === 'insertFromDrop') {
		const caret = caretOffset(editor);
		tidy(editor);
		if (caret !== null) {
			placeCaret(editor, caret);
		}
	}
	sync(editor);
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

// Cancel: the text the editor started with, which the form's reset puts back in the hidden field too
document.addEventListener('reset', (event) => {
	const form = event.target;
	const editor = form instanceof HTMLFormElement ? form.querySelector('[data-rich-editor]') : null;
	const original = form?.querySelector?.('[data-rich-original]');
	if (editor && original) {
		editor.replaceChildren(original.content.cloneNode(true));
	}
});
