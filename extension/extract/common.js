// Part of reading a job posting (extract.js, which says how): what the readers share. A page's text as the web app
// shows it, the page's own title and tags, fetching another page, and putting a job together from what each reader
// found. Injected before extract.js, like the other parts, each registering what it gives on globalThis.jobWarden:
// a classic script, as injected scripts are, so injecting it again into the same page only registers it anew.
globalThis.jobWarden ??= {};
globalThis.jobWarden.extract = {};
globalThis.jobWarden.extract.common = (() => {
  /** Elements that start a new paragraph (blank line) or a new line when a description is turned into text. */
  const PARAGRAPH = new Set(['P', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'UL', 'OL', 'TABLE', 'BLOCKQUOTE', 'PRE', 'HR']);
  const LINE = new Set(['DIV', 'LI', 'TR', 'DT', 'DD', 'SECTION', 'ARTICLE', 'HEADER', 'FOOTER']);

  /** How long to wait for a job's own page (LinkedIn's public one, Indeed's...) before going on with what's on screen. */
  const JOB_PAGE_TIMEOUT_MILLIS = 5000;

  /** Each field from the first source that has it; sources can be null. */
  function firstOfEach(...sources) {
    const job = {
      title: null, company: null, description: null, location: null, salary: null, employmentType: null,
      logoUrl: null, rawPayload: null,
    };
    for (const source of sources.filter(Boolean)) {
      for (const field of Object.keys(job)) {
        job[field] ??= source[field] ?? null;
      }
    }
    return job;
  }

  /**
   * A page of the same site, such as the job's own page: the request goes out as the page's own would, with the
   * user's cookies, so it gets what the user would. Null if it doesn't answer in time or answers with an error.
   */
  async function fetchPage(address) {
    try {
      const response = await fetch(address, { signal: AbortSignal.timeout(JOB_PAGE_TIMEOUT_MILLIS) });
      return response.ok ? parseDocument(await response.text()) : null;
    } catch {
      return null;
    }
  }

  function parseJson(text) {
    try {
      return JSON.parse(text);
    } catch {
      // Some sites put raw line breaks inside strings, which JSON doesn't allow
    }
    try {
      // eslint-disable-next-line no-control-regex -- the control characters are what it takes out
      return JSON.parse(text.replace(/[\u0000-\u001f]+/g, ' '));
    } catch {
      return null;
    }
  }

  /** The value as an absolute http or https address, relative to the page; null for anything else. */
  function webAddress(value) {
    if (typeof value !== 'string') {
      return null;
    }
    const address = URL.parse(value.trim(), window.location.href);
    return (address?.protocol === 'https:' || address?.protocol === 'http:') ? address.href : null;
  }

  // Text

  /** A short text on one line: entities decoded, tags and extra whitespace removed. */
  function plainText(value) {
    if (typeof value === 'number') {
      return String(value);
    }
    const text = htmlToText(value);
    return text ? text.replace(/\s+/g, ' ') : null;
  }

  /**
   * Descriptions are HTML. The web app shows them as plain text that keeps line breaks, so paragraphs become
   * blank lines and list items "• " lines.
   */
  function htmlToText(value) {
    if (typeof value !== 'string' || value.trim() === '') {
      return null;
    }
    let body = parseHtml(value);
    if (body === null) {
      return value.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim() || null;
    }
    // Escaped twice ("&lt;p&gt;"): the first pass leaves the tags as text
    if (/<\/?[a-z][^>]*>/i.test(body.textContent)) {
      body = parseHtml(body.textContent) ?? body;
    }

    const parts = [];
    let breaks = 0;
    const breakLine = (count) => {
      breaks = Math.max(breaks, count);
    };
    const write = (text) => {
      if (parts.length > 0 && breaks > 0) {
        parts.push('\n'.repeat(breaks));
        text = text.trimStart();
      }
      breaks = 0;
      parts.push(text);
    };
    const walk = (node) => {
      if (node.nodeType === Node.TEXT_NODE) {
        const text = node.textContent.replace(/\s+/g, ' ');
        if (text.trim() !== '') {
          write(text);
        } else if (breaks === 0 && parts.length > 0) {
          parts.push(' ');
        }
        return;
      }
      if (node.nodeType !== Node.ELEMENT_NODE || node.tagName === 'SCRIPT' || node.tagName === 'STYLE') {
        return;
      }
      if (node.tagName === 'BR') {
        breaks = Math.min(breaks + 1, 2);
        return;
      }
      const lines = PARAGRAPH.has(node.tagName) ? 2 : LINE.has(node.tagName) ? 1 : 0;
      breakLine(lines);
      if (node.tagName === 'LI') {
        write('• ');
      }
      node.childNodes.forEach(walk);
      breakLine(lines);
    };
    walk(body);

    return parts.join('')
      .replace(/[ \t]*\n[ \t]*/g, '\n')
      .replace(/ {2,}/g, ' ')
      // A value some sites' templates print where they had none, alone or before the text
      .replace(/^(null|undefined)$/gm, '')
      .replace(/^\s*(null|undefined)\s+/, '')
      .replace(/\n{3,}/g, '\n\n')
      .trim() || null;
  }

  /** The texts of several blocks, such as a description's sections, a blank line apart. */
  function joinedText(elements) {
    return [...elements].map((element) => htmlToText(element.innerHTML)).filter(Boolean).join('\n\n') || null;
  }

  /** The text directly in an element, without its children's: a company name without its "About us" link. */
  function ownText(element) {
    if (!element) {
      return null;
    }
    const own = [...element.childNodes].filter((node) => node.nodeType === Node.TEXT_NODE)
      .map((node) => node.textContent).join(' ');
    return plainText(own) ?? textOf(element);
  }

  /** Parsed, not rendered: scripts don't run and images don't load. Null if the page's policy forbids it. */
  function parseHtml(html) {
    return parseDocument(html)?.body ?? null;
  }

  /** A whole page, head included: that's where many sites put their JobPosting. */
  function parseDocument(html) {
    try {
      return new DOMParser().parseFromString(html, 'text/html');
    } catch {
      return null;
    }
  }

  /** An element's text on one line, e.g. a heading. */
  function textOf(element) {
    return element?.textContent.replace(/\s+/g, ' ').trim() || null;
  }

  function metaTag(name) {
    const tag = document.querySelector(`meta[property="${name}"], meta[name="${name}"]`);
    return plainText(tag?.content);
  }

  /** Without the unread count some sites put in front, such as "(3) ". */
  function pageTitle() {
    return plainText(document.title.replace(/^\(\d+\+?\)\s*/, '')) ?? window.location.hostname;
  }

  return {
    firstOfEach, fetchPage, parseJson, webAddress, plainText, htmlToText, joinedText, ownText, textOf, metaTag,
    pageTitle,
  };
})();
