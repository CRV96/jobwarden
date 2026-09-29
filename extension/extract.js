// Injected into the job posting page by popup.js, which receives what this file ends with: the result of the one
// function call below (Chrome waits for its promise). Nothing is declared outside it, so the popup can inject it
// again into the same page. It runs in an isolated world: it sees the page's DOM, not its JavaScript.
//
// Each field comes from the first of these that has it:
//   1. Text the user selected on the page, as the description: their way to pick it where nothing else finds it.
//   2. The posting's schema.org JobPosting, which job sites add for Google's job search, as JSON-LD
//      (<script type="application/ld+json">) or microdata (itemprop attributes). It has every field, and the whole
//      object is sent along as rawPayload.
//   3. On LinkedIn, which gives signed-in members no JobPosting: the job's public page, then what's on screen.
//   4. The page itself: Open Graph tags, a block marked as the job description, the heading, the page title.
// The user can correct the title, company, location and description in the popup before saving. The link isn't read
// here: the popup takes it from the tab's address (links.js).
(async () => {
  /** Elements that start a new paragraph (blank line) or a new line when a description is turned into text. */
  const PARAGRAPH = new Set(['P', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'UL', 'OL', 'TABLE', 'BLOCKQUOTE', 'PRE', 'HR']);
  const LINE = new Set(['DIV', 'LI', 'TR', 'DT', 'DD', 'SECTION', 'ARTICLE', 'HEADER', 'FOOTER']);

  /** Shorter selections are more likely a word left selected by accident than a description. */
  const MIN_SELECTION_LENGTH = 30;

  /**
   * Where LinkedIn shows each field: first its public job page, then the signed-in pages (a job, or the job shown
   * beside the search results). LinkedIn changes the signed-in pages' classes now and then; the public page's have
   * stayed the same for years.
   */
  const LINKEDIN_SELECTORS = {
    title: ['.top-card-layout__title', '.job-details-jobs-unified-top-card__job-title',
      '.jobs-unified-top-card__job-title'],
    company: ['.topcard__org-name-link', '.job-details-jobs-unified-top-card__company-name',
      '.jobs-unified-top-card__company-name'],
    location: ['.topcard__flavor--bullet:not(.num-applicants__caption)',
      '.job-details-jobs-unified-top-card__primary-description-container .tvm__text',
      '.job-details-jobs-unified-top-card__tertiary-description-container .tvm__text',
      '.jobs-unified-top-card__bullet'],
    description: ['.show-more-less-html__markup', '#job-details', '.jobs-description__content',
      '.jobs-description-content__text'],
  };

  /** The "Employment type" heading of the public page's criteria list, in the extension's languages. */
  const LINKEDIN_EMPLOYMENT_TYPE = /employment|angaj|emploi|beschäftigung|zatrudnien|empleo|emprego/i;

  /** How long to wait for LinkedIn's public page before going on with what's on screen. */
  const LINKEDIN_TIMEOUT_MILLIS = 5000;

  const url = new URL(window.location.href);
  const posting = findJobPosting() ?? findMicrodataPosting();
  const job = firstOfEach(
    { description: selectedText() },
    posting && fromJobPosting(posting),
    await fromLinkedIn(url),
    fromPage(),
  );
  return job;

  /** Each field from the first source that has it; sources can be null. */
  function firstOfEach(...sources) {
    const job = {
      title: null, company: null, description: null, location: null, salary: null, employmentType: null,
      rawPayload: null,
    };
    for (const source of sources.filter(Boolean)) {
      for (const field of Object.keys(job)) {
        job[field] ??= source[field] ?? null;
      }
    }
    return job;
  }

  function fromJobPosting(posting) {
    return {
      title: plainText(posting.title) ?? plainText(posting.name),
      company: organizationName(posting.hiringOrganization),
      description: htmlToText(posting.description),
      location: locationOf(posting),
      salary: salaryOf(posting.baseSalary ?? posting.estimatedSalary),
      employmentType: employmentTypeOf(posting.employmentType),
      rawPayload: posting,
    };
  }

  /** What any page has. Not og:site_name as the company: on job boards, it's the board ("LinkedIn"). */
  function fromPage() {
    return {
      title: metaTag('og:title') ?? textOf(document.querySelector('h1')) ?? pageTitle(),
      description: descriptionBlock() ?? metaTag('og:description') ?? metaTag('description'),
    };
  }

  function selectedText() {
    const text = window.getSelection()?.toString().trim() ?? '';
    return text.length >= MIN_SELECTION_LENGTH ? text.replace(/\n{3,}/g, '\n\n') : null;
  }

  /** A block the site marked as the description: <div class="job-description">, itemprop="description"... */
  function descriptionBlock() {
    const blocks = document.querySelectorAll(['[itemprop="description"]', '[id*="job-description" i]',
      '[class*="job-description" i]', '[id*="jobdescription" i]', '[class*="jobdescription" i]',
      '[id*="job_description" i]', '[class*="job_description" i]'].join(', '));
    for (const block of blocks) {
      const text = htmlToText(block.innerHTML);
      // Not a label or a link that happens to have the class
      if (text && text.length >= 100) {
        return text;
      }
    }
    return null;
  }

  // LinkedIn

  async function fromLinkedIn(url) {
    if (!isLinkedIn(url)) {
      return null;
    }
    const id = linkedInJobId(url);
    const publicPage = id ? await linkedInPublicPage(id) : null;
    return firstOfEach(publicPage && readLinkedIn(publicPage), readLinkedIn(document), linkedInPageTitle());
  }

  /**
   * The job's page as visitors who aren't signed in see it: the same request LinkedIn's own public job search makes.
   * Null if it doesn't answer in time or answers with an error, e.g. when asked too often.
   */
  async function linkedInPublicPage(id) {
    try {
      const response = await fetch(`${url.origin}/jobs-guest/jobs/api/jobPosting/${id}`, {
        signal: AbortSignal.timeout(LINKEDIN_TIMEOUT_MILLIS),
      });
      return response.ok ? parseHtml(await response.text()) : null;
    } catch {
      return null;
    }
  }

  /** @param {ParentNode} root the public page, or the document */
  function readLinkedIn(root) {
    const first = (selectors, read) => {
      for (const selector of selectors) {
        const value = read(root.querySelector(selector));
        if (value) {
          return value;
        }
      }
      return null;
    };
    return {
      title: first(LINKEDIN_SELECTORS.title, textOf),
      company: first(LINKEDIN_SELECTORS.company, textOf),
      location: first(LINKEDIN_SELECTORS.location, textOf),
      description: first(LINKEDIN_SELECTORS.description, (element) => element && htmlToText(element.innerHTML)),
      employmentType: linkedInEmploymentType(root),
    };
  }

  function linkedInEmploymentType(root) {
    for (const item of root.querySelectorAll('.description__job-criteria-item')) {
      const heading = item.querySelector('.description__job-criteria-subheader')?.textContent ?? '';
      if (LINKEDIN_EMPLOYMENT_TYPE.test(heading)) {
        return employmentTypeOf(textOf(item.querySelector('.description__job-criteria-text')));
      }
    }
    return null;
  }

  /** A signed-in job page is titled "Senior Java Developer | Deloitte | LinkedIn". */
  function linkedInPageTitle() {
    const parts = pageTitle().split(' | ');
    return (parts.length === 3 && parts[2] === 'LinkedIn') ? { title: parts[0], company: parts[1] } : null;
  }

  function isLinkedIn(url) {
    return /(^|\.)linkedin\.com$/.test(url.hostname);
  }

  /**
   * From a job page (/jobs/view/4012345678/, maybe with the title in the path) or a search showing one. As in
   * links.js, which a script injected into a page can't import.
   */
  function linkedInJobId(url) {
    return url.searchParams.get('currentJobId') ?? url.pathname.match(/\/jobs\/view\/(?:[^/]*-)?(\d+)/)?.[1] ?? null;
  }

  // Microdata

  function findMicrodataPosting() {
    const scope = [...document.querySelectorAll('[itemscope][itemtype]')]
      .find((element) => /(^|[\s/])JobPosting(\s|$)/.test(element.getAttribute('itemtype')));
    return scope ? microdataItem(scope) : null;
  }

  /** The item's properties as an object, so it reads like a JSON-LD JobPosting. */
  function microdataItem(scope) {
    const item = { '@type': scope.getAttribute('itemtype').trim().split('/').pop() };
    for (const element of scope.querySelectorAll('[itemprop]')) {
      // A property of a nested item belongs to that item
      if (element.parentElement.closest('[itemscope]') !== scope) {
        continue;
      }
      const value = element.hasAttribute('itemscope') ? microdataItem(element) : microdataValue(element);
      for (const name of element.getAttribute('itemprop').trim().split(/\s+/)) {
        item[name] = (name in item) ? [item[name], value].flat() : value;
      }
    }
    return item;
  }

  function microdataValue(element) {
    if (element.hasAttribute('content')) {
      return element.getAttribute('content');
    }
    if (element.tagName === 'A' || element.tagName === 'LINK') {
      return element.href;
    }
    if (element.tagName === 'TIME') {
      return element.dateTime || element.textContent;
    }
    // HTML, so a description keeps its paragraphs; the short fields are turned into plain text
    return element.innerHTML;
  }

  // JSON-LD

  function findJobPosting() {
    for (const script of document.querySelectorAll('script[type="application/ld+json"]')) {
      const posting = findIn(parseJson(script.textContent));
      if (posting) {
        return posting;
      }
    }
    return null;
  }

  function parseJson(text) {
    try {
      return JSON.parse(text);
    } catch {
      // Some sites put raw line breaks inside strings, which JSON doesn't allow
    }
    try {
      return JSON.parse(text.replace(/[\u0000-\u001f]+/g, ' '));
    } catch {
      return null;
    }
  }

  /**
   * The posting can be the block itself, sit in an array or an "@graph", or hang off another object
   * (a WebPage's "mainEntity"), so this searches everything, a few levels deep.
   */
  function findIn(node, depth = 0) {
    if (node === null || typeof node !== 'object' || depth > 5) {
      return null;
    }
    if (!Array.isArray(node) && isJobPosting(node)) {
      return node;
    }
    for (const value of Object.values(node)) {
      const found = findIn(value, depth + 1);
      if (found) {
        return found;
      }
    }
    return null;
  }

  /** "JobPosting", ["JobPosting"], "schema:JobPosting" or "https://schema.org/JobPosting" */
  function isJobPosting(node) {
    return [node['@type']].flat().some((type) => typeof type === 'string' && /(^|[/:])JobPosting$/.test(type));
  }

  function organizationName(organization) {
    const first = [organization].flat()[0];
    if (first === null || typeof first !== 'object') {
      return plainText(first);
    }
    return plainText(first.name) ?? plainText(first.legalName);
  }

  /** "Bucharest, Romania", several places separated by "; ", and "Remote" first for a remote job. */
  function locationOf(posting) {
    const places = [posting.jobLocation].flat().map(placeName).filter(Boolean);
    const remote = [posting.jobLocationType].flat().some((type) => /telecommute/i.test(String(type)));
    if (remote) {
      // Where a remote employee may live, e.g. "Remote (Romania)"
      const regions = [posting.applicantLocationRequirements].flat().map(placeName).filter(Boolean);
      const label = chrome.i18n.getMessage('remote') || 'Remote';
      places.unshift(regions.length > 0 ? `${label} (${regions.join(', ')})` : label);
    }
    return places.length > 0 ? [...new Set(places)].join('; ') : null;
  }

  /** A Place, its PostalAddress, a Country or plain text. */
  function placeName(place) {
    if (place === null || typeof place !== 'object') {
      return plainText(place);
    }
    const address = place.address ?? place;
    if (typeof address !== 'object') {
      return plainText(address);
    }
    const parts = [address.addressLocality, address.addressRegion, placeName(address.addressCountry)]
      .map(plainText)
      .filter(Boolean);
    return parts.length > 0 ? [...new Set(parts)].join(', ') : plainText(place.name);
  }

  /** A MonetaryAmount such as { currency: "EUR", value: { minValue: 5000, maxValue: 7000, unitText: "MONTH" } }. */
  function salaryOf(salary) {
    if (salary === null || typeof salary !== 'object') {
      return plainText(salary);
    }
    const amount = (salary.value !== null && typeof salary.value === 'object') ? salary.value : salary;
    const low = amount.minValue ?? amount.value;
    const high = amount.maxValue;
    if (low == null && high == null) {
      return null;
    }
    const range = (low != null && high != null && Number(low) !== Number(high))
      ? `${formatNumber(low)}–${formatNumber(high)}`
      : formatNumber(low ?? high);
    const unit = plainText(amount.unitText ?? salary.unitText);
    const per = unit ? (translated('salaryPer', unit) ?? unit.toLowerCase()) : null;
    return [range, plainText(salary.currency ?? amount.currency), per].filter(Boolean).join(' ');
  }

  /** In the browser's language: 5,000 in English, 5.000 in German. */
  function formatNumber(value) {
    const number = Number(value);
    return Number.isFinite(number) ? new Intl.NumberFormat(chrome.i18n.getUILanguage()).format(number) : String(value);
  }

  /** "FULL_TIME" becomes "Full-time" in the browser's language; anything else is kept as the site wrote it. */
  function employmentTypeOf(employmentType) {
    const types = [employmentType].flat()
      .map(plainText)
      .filter(Boolean)
      .map((type) => translated('employmentType', type) ?? type);
    return types.length > 0 ? [...new Set(types)].join(', ') : null;
  }

  /**
   * The message for one of schema.org's values, e.g. employmentType_FULL_TIME for "FULL_TIME" or "full time".
   * Null for a value it has no message for.
   */
  function translated(prefix, value) {
    const name = value.toUpperCase().replace(/[\s-]+/g, '_');
    return /^[A-Z_]+$/.test(name) ? (chrome.i18n.getMessage(`${prefix}_${name}`) || null) : null;
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
      .replace(/\n{3,}/g, '\n\n')
      .trim() || null;
  }

  /** Parsed, not rendered: scripts don't run and images don't load. Null if the page's policy forbids it. */
  function parseHtml(html) {
    try {
      return new DOMParser().parseFromString(html, 'text/html').body;
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
})();
