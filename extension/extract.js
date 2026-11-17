// Injected into the job posting page by popup.js, after its parts in extract/ (EXTRACT_SCRIPTS,
// injected.js): the browser hands back what the last file ends with, the result of the one function call below, and
// waits for its promise. Nothing is declared outside it, and the parts only register what they give on
// globalThis.jobWarden, so the popup can inject them again into the same page. It runs in an isolated world: it sees the
// page's DOM, not its JavaScript.
//
// Each field comes from the first of these that has it:
//   1. Text the user selected on the page, as the description: their way to pick it where nothing else finds it.
//   2. The posting's schema.org JobPosting, which job sites add for Google's job search, as JSON-LD
//      (<script type="application/ld+json">) or microdata (itemprop attributes). It has every field, and the whole
//      object is sent along as rawPayload (extract/posting.js).
//   3. For a job a site shows beside its search results (Indeed, Glassdoor): the job's own page, fetched, with its
//      JobPosting. On LinkedIn, which gives signed-in members none: the job's public page (extract/linkedin.js).
//   4. What the site shows on screen, for the sites that leave fields out of the JobPosting or have none (SITES, in
//      extract/sites.js).
//   5. The page itself: Open Graph tags, a block marked as the job description, the heading, the page title.
// The company's logo comes from the JobPosting or a site's own readers, never from just any image on the page; the
// server downloads it (logoUrl).
// The user can correct the title, company, location and description in the popup before saving. The popup takes the
// link from the tab's address (links.js), unless the page shows a job that isn't its own: then this says which (link).
(async () => {
  const {
    fetchPage, firstOfEach, htmlToText, metaTag, pageTitle, textOf, webAddress,
  } = globalThis.jobWarden.extract.common;
  const { findJobPosting, findMicrodataPosting, fromJobPosting } = globalThis.jobWarden.extract.posting;
  const { SITES, readSite } = globalThis.jobWarden.extract.sites;
  const { fromLinkedIn } = globalThis.jobWarden.extract.linkedin;

  /** Shorter selections are more likely a word left selected by accident than a description. */
  const MIN_SELECTION_LENGTH = 30;

  /** A tag some sites put in front of a job's title: "[Hiring] ", "[Remote Job] ". */
  const TITLE_TAG = /^\s*\[[\p{L} ]{2,20}\]\s*/u;

  const url = new URL(window.location.href);
  const site = SITES.find((candidate) => candidate.host.test(url.hostname)) ?? null;
  const jobId = site?.jobId?.(url) ?? null;
  const posting = findJobPosting(document) ?? findMicrodataPosting(document);
  // A job shown beside search results: its own page has the JobPosting this page lacks
  const jobLink = (posting === null && jobId !== null && /^[\w-]+$/.test(jobId)) ? site.jobPage(url, jobId) : null;
  const jobPage = jobLink ? await fetchPage(jobLink) : null;
  const jobPagePosting = jobPage && (findJobPosting(jobPage) ?? findMicrodataPosting(jobPage));
  const job = firstOfEach(
    { description: selectedText() },
    posting && fromJobPosting(posting),
    jobPagePosting && fromJobPosting(jobPagePosting),
    jobPage && readSite(site, jobPage, jobId),
    await fromLinkedIn(url),
    site && readSite(site, document, jobId),
    fromPage(),
  );
  return { ...job, title: tidyTitle(job.title, job.company), link: webAddress(jobLink) };

  /**
   * What any page has. Not og:site_name as the company: on job boards, it's the board ("LinkedIn"). Many job pages
   * are titled "<job> at <company>", which gives the company too, as long as the heading says the same job ("at" can
   * be part of a job's title).
   */
  function fromPage() {
    const heading = textOf(document.querySelector('h1'));
    const titled = fromPageTitle();
    // The heading is cleaner when the page's title only wraps it, e.g. in "[Remote Job] <job> at <company> | <site>"
    const headingIsTheJob = Boolean(heading && titled.title?.toLowerCase().includes(heading.toLowerCase()));
    return {
      title: headingIsTheJob ? heading : (titled.title ?? heading ?? pageTitle()),
      company: (headingIsTheJob || !heading) ? titled.company : null,
      description: descriptionBlock() ?? metaTag('og:description') ?? metaTag('description'),
    };
  }

  /** og:title or the page title, without a tag in front or the site's name after, split at "at" or "@". */
  function fromPageTitle() {
    let title = (metaTag('og:title') ?? pageTitle()).replace(TITLE_TAG, '');
    const suffix = title.match(/\s+[|–—-]\s+([^|–—-]+)$/);
    if (suffix && isSiteName(suffix[1])) {
      title = title.slice(0, suffix.index);
    }
    const parts = title.match(/^(.+?)\s+(?:at|@)\s+(.+)$/);
    return parts ? { title: parts[1].trim(), company: parts[2].trim() } : { title: title.trim() || null, company: null };
  }

  /** Whether a title's last part names the site: "Working Nomads" on workingnomads.com, "bunq Careers" on bunq.com. */
  function isSiteName(text) {
    const squash = (value) => value.toLowerCase().replace(/[^\p{L}\p{N}]/gu, '');
    const name = squash(text);
    const siteName = squash(metaTag('og:site_name') ?? '');
    const domainName = window.location.hostname.split('.').slice(-2, -1)[0] ?? '';
    return (siteName !== '' && name.includes(siteName)) || (domainName.length > 2 && name.includes(domainName));
  }

  /**
   * Without what some sites wrap a job's title in, even in their JobPosting: a tag in front ("[Hiring] "), the company
   * after ("@Askable", " at Askable").
   */
  function tidyTitle(title, company) {
    if (!title) {
      return title;
    }
    let tidy = title.replace(TITLE_TAG, '');
    if (company) {
      const escaped = company.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      tidy = tidy.replace(new RegExp(`\\s*(?:@|\\bat\\b|[|–—-])\\s*${escaped}\\s*$`, 'iu'), '');
    }
    return tidy.trim() || title;
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
})();
