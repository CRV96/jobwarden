// Injected into the job posting page by popup.js, which receives what this file ends with: the result of the one
// function call below (Chrome waits for its promise). Nothing is declared outside it, so the popup can inject it
// again into the same page. It runs in an isolated world: it sees the page's DOM, not its JavaScript.
//
// Each field comes from the first of these that has it:
//   1. Text the user selected on the page, as the description: their way to pick it where nothing else finds it.
//   2. The posting's schema.org JobPosting, which job sites add for Google's job search, as JSON-LD
//      (<script type="application/ld+json">) or microdata (itemprop attributes). It has every field, and the whole
//      object is sent along as rawPayload.
//   3. For a job a site shows beside its search results (Indeed, Glassdoor): the job's own page, fetched, with its
//      JobPosting. On LinkedIn, which gives signed-in members none: the job's public page.
//   4. What the site shows on screen, for the sites that leave fields out of the JobPosting or have none (SITES).
//   5. The page itself: Open Graph tags, a block marked as the job description, the heading, the page title.
// The company's logo comes from the JobPosting or a site's own readers, never from just any image on the page; the
// server downloads it (logoUrl).
// The user can correct the title, company, location and description in the popup before saving. The popup takes the
// link from the tab's address (links.js), unless the page shows a job that isn't its own: then this says which (link).
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

  /** How long to wait for a job's own page (LinkedIn's public one, Indeed's...) before going on with what's on screen. */
  const JOB_PAGE_TIMEOUT_MILLIS = 5000;

  /** More places than this, and a location lists only the first, and a remote job only "Remote". */
  const MAX_PLACES = 3;

  /** Places that say no more than "remote": a remote job's location shows "Remote" once. */
  const ANYWHERE = /^(anywhere|worldwide|global|everywhere|remote)$/i;

  /** A tag some sites put in front of a job's title: "[Hiring] ", "[Remote Job] ". */
  const TITLE_TAG = /^\s*\[[\p{L} ]{2,20}\]\s*/u;

  /** Logos a JobPosting has that aren't the company's: the job board's own, for a company without one. */
  const IGNORED_LOGOS = [/^https?:\/\/[^/]*indeed\.com\/jsf\/images\//];

  /** The sections of a pracuj.pl offer that make up its description, in the order the page shows them. */
  const PRACUJ_SECTIONS = ['about-project', 'responsibilities', 'requirements', 'training-space', 'offered', 'benefits',
    'work-organization', 'about-us-description'];

  /**
   * Job sites the JobPosting doesn't cover, or doesn't cover well, and where each shows the fields it leaves out. A
   * reader is a CSS selector, or a function of the page (the document, or the job's page fetched) and the job's id,
   * for what a selector can't reach; the first one that finds something wins. A site that shows a job beside its search
   * results also says how to find that job's own page, which has the JobPosting: jobId and jobPage. LinkedIn has a
   * reader of its own, below.
   */
  const SITES = [
    {
      host: /(^|\.)indeed\.[a-z.]+$/,
      // A search names the job it shows beside the results (vjk); the job's page names its own (jk)
      jobId: (url) => url.searchParams.get('vjk') ?? url.searchParams.get('jk'),
      jobPage: (url, id) => `${url.origin}/viewjob?jk=${encodeURIComponent(id)}`,
      // The job's page, then what a search shows: the job's pane, and its card in the results
      title: ['[data-testid="vj-job-title"]', '[data-testid="jobsearch-JobInfoHeader-title"] > span:first-child',
        '[data-testid="jobsearch-JobInfoHeader-title"]'],
      company: ['[data-testid="inlineHeader-companyName"]',
        (root, id) => indeedCard(root, id)?.querySelector('[data-testid="company-name"]')],
      location: ['[data-testid="inlineHeader-companyLocation"]', '[data-testid="job-location"]',
        (root, id) => indeedCard(root, id)?.querySelector('[data-testid="text-location"]')],
      description: ['#jobDescriptionText'],
      logo: [(root, id) => indeedCard(root, id)?.querySelector('img[data-testid="brandingLogo-image"]')],
    },
    {
      host: /(^|\.)glassdoor\.[a-z.]+$/,
      // A search shows the job its address names (jl), or else the first one, whose heading has its id
      jobId: (url) => url.searchParams.get('jl')
        ?? document.querySelector('h1[id^="jd-job-title-"]')?.id.slice('jd-job-title-'.length) ?? null,
      jobPage: (url, id) => document.querySelector(`a[href*="/job-listing/"][href*="jl=${id}"]`)?.href ?? null,
      title: ['h1[id^="jd-job-title-"]'],
    },
    {
      // The job boards companies host their openings on: job-boards.greenhouse.io, and the older boards.greenhouse.io
      host: /(^|\.)greenhouse\.io$/,
      title: ['.job__title h1', '.app-title'],
      // The page title is "Job Application for <title> at <company>"
      company: [() => document.title.match(/^\s*Job Application for .+ at (.+?)\s*$/)?.[1] ?? null, '.company-name'],
      location: ['.job__location', '.location'],
      description: ['.job__description', '#content'],
      logo: ['.logo img', () => metaTag('og:image')],
    },
    {
      // Companies' career sites on Workday: the JobPosting has no logo, but the page's image is the company's
      host: /(^|\.)myworkdayjobs\.com$/,
      logo: [() => metaTag('og:image')],
    },
    {
      // Its JobPosting has neither the company nor the description
      host: /(^|\.)pracuj\.pl$/,
      company: [(root) => ownText(root.querySelector('[data-test="text-employerName"]'))],
      description: [(root) => joinedText(root.querySelectorAll(PRACUJ_SECTIONS
        .map((name) => `[data-test="section-${name}"]`).join(', ')))],
      logo: ['img[data-test="image-company-logo"]'],
    },
    {
      host: /(^|\.)infojobs\.net$/,
      company: [(root) => [...root.querySelectorAll('a[href*="/em-i"]')].find((link) => textOf(link))],
      // The page title is "Oferta de empleo: <title> en <place> - Bolsa trabajo InfoJobs"
      location: [() => document.title.match(/^.* en (.+?) - Bolsa (de )?trabajo InfoJobs\s*$/i)?.[1] ?? null],
      description: [(root) => joinedText(root.querySelectorAll('.ij-EnrichedTextArea-paragraph'))],
      logo: ['img[src*="multimedia-logos.infojobs.net"]'],
    },
    {
      // Remote jobs, without a JobPosting; each detail is a line with an icon. The list opens a job beside it, with
      // the same details, and names it in its address (job): the job's own page is /jobs/<that name>
      host: /(^|\.)workingnomads\.com$/,
      jobId: (url) => url.searchParams.get('job'),
      jobPage: (url, slug) => `${url.origin}/jobs/${slug}`,
      title: ['h1.jd-title'],
      company: ['.jd-company a', '.jd-company'],
      location: [(root) => workingNomadsLine(root, 'fa-map-marker')],
      employmentType: [(root) => workingNomadsLine(root, 'fa-clock-o')],
      description: ['.jd-description'],
    },
    {
      // Remote jobs, without a JobPosting: the page's data (Next.js) has the job
      host: /(^|\.)arc\.dev$/,
      title: [() => arcJob()?.title, 'h1'],
      company: [() => arcJob()?.companyName ?? arcJob()?.company?.name],
      location: [() => remoteLocation(arcJob()?.requiredCountries ?? [])],
      description: [() => arcJob()?.description],
      logo: [() => arcJob()?.company?.logoUrl],
    },
    {
      // No JobPosting and no heading: og:title is "<title> , <company>", the page title has the address
      host: /(^|\.)bestjobs\.eu$/,
      title: [() => bestJobsHeading().title, 'h2'],
      company: [() => bestJobsHeading().company, 'h3'],
      location: [() => bestJobsAddress()],
      logo: ['img[src*="imgcdn.bestjobs.eu"][src*="logo"]'],
    },
  ];

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

  function fromJobPosting(posting) {
    return {
      title: plainText(posting.title) ?? plainText(posting.name),
      company: organizationName(posting.hiringOrganization),
      description: htmlToText(posting.description),
      location: locationOf(posting),
      salary: salaryOf(posting.baseSalary ?? posting.estimatedSalary),
      employmentType: employmentTypeOf(posting.employmentType),
      logoUrl: usableLogo(logoOf(posting.hiringOrganization)),
      rawPayload: posting,
    };
  }

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

  // Sites

  /**
   * What the site's readers find on the page: the document, or the job's page fetched. A reader that's a function
   * gives an element, read like a selector's, or the value itself.
   */
  function readSite(site, root, jobId) {
    const first = (readers, readElement, readValue) => {
      for (const reader of readers ?? []) {
        const found = (typeof reader === 'function') ? reader(root, jobId) : root.querySelector(reader);
        const value = (found !== null && typeof found === 'object') ? readElement(found) : readValue(found);
        if (value) {
          return value;
        }
      }
      return null;
    };
    return {
      title: first(site.title, textOf, plainText),
      company: first(site.company, textOf, plainText),
      location: first(site.location, textOf, plainText),
      salary: first(site.salary, textOf, plainText),
      employmentType: first(site.employmentType, textOf, plainText),
      description: first(site.description, (element) => htmlToText(element.innerHTML), htmlToText),
      logoUrl: usableLogo(first(site.logo, imageAddress, webAddress)),
    };
  }

  /** On Working Nomads, the text of the detail line marked with this icon. */
  function workingNomadsLine(root, icon) {
    return root.querySelector(`.jd-meta-line .${icon}`)?.closest('.jd-meta-line')?.querySelector('span') ?? null;
  }

  /** On Arc, the job in the page's Next.js data. */
  function arcJob() {
    return nextData()?.props?.pageProps?.job ?? null;
  }

  /** What a Next.js page renders from, which has more than its HTML: the JSON in script#__NEXT_DATA__. */
  function nextData() {
    const script = document.querySelector('script#__NEXT_DATA__');
    return script ? parseJson(script.textContent) : null;
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

  /** On an Indeed search, the card of the job in the results: its title links with the job's id (data-jk). */
  function indeedCard(root, id) {
    let card = id ? root.querySelector(`[data-jk="${id}"]`) : null;
    for (let level = 0; card && level < 8 && !card.querySelector('[data-testid="company-name"]'); level++) {
      card = card.parentElement;
    }
    return card;
  }

  /** og:title on BestJobs: "<title> , <company>". */
  function bestJobsHeading() {
    const [title, ...company] = (metaTag('og:title') ?? '').split(' , ');
    return { title: title || null, company: company.join(' , ') || null };
  }

  /** The page title on BestJobs is "<title> - <address>- <company> - BestJobs.eu". */
  function bestJobsAddress() {
    const { title, company } = bestJobsHeading();
    const page = document.title.replace(/\s*-\s*BestJobs\.eu\s*$/i, '');
    const start = (title && page.startsWith(`${title} - `)) ? title.length + 3 : -1;
    const end = company ? page.lastIndexOf(`- ${company}`) : -1;
    return (start > 0 && end > start) ? plainText(page.slice(start, end)) : null;
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
  function linkedInPublicPage(id) {
    return fetchPage(`${url.origin}/jobs-guest/jobs/api/jobPosting/${id}`);
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
      logoUrl: linkedInLogo(root),
    };
  }

  /**
   * The public page's company logo, in the top card. LinkedIn loads it late, from data-delayed-url, and shows a grey
   * placeholder until then; a signed-in page has no such card.
   */
  function linkedInLogo(root) {
    const image = root.querySelector('[data-tracking-control-name="public_jobs_topcard_logo"] img');
    return image ? usableLogo(imageAddress(image)) : null;
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

  function findMicrodataPosting(root) {
    const scope = [...root.querySelectorAll('[itemscope][itemtype]')]
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

  function findJobPosting(root) {
    for (const script of root.querySelectorAll('script[type="application/ld+json"]')) {
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

  /** The organization's logo: an address, or an ImageObject with one. */
  function logoOf(organization) {
    const first = [organization].flat()[0];
    if (first === null || typeof first !== 'object') {
      return null;
    }
    const logo = [first.logo].flat()[0];
    return webAddress((logo !== null && typeof logo === 'object') ? (logo.url ?? logo.contentUrl) : logo);
  }

  /** An image's address; LinkedIn and others load the real one late, from data-delayed-url. */
  function imageAddress(element) {
    const image = (element.tagName === 'IMG') ? element : element.querySelector('img');
    return image ? webAddress(image.getAttribute('data-delayed-url') ?? image.getAttribute('src')) : null;
  }

  function usableLogo(address) {
    return (address && !IGNORED_LOGOS.some((ignored) => ignored.test(address))) ? address : null;
  }

  /** The value as an absolute http or https address, relative to the page; null for anything else. */
  function webAddress(value) {
    if (typeof value !== 'string') {
      return null;
    }
    const address = URL.parse(value.trim(), window.location.href);
    return (address?.protocol === 'https:' || address?.protocol === 'http:') ? address.href : null;
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
    let places = [posting.jobLocation].flat().map(placeName).filter(Boolean);
    const remote = [posting.jobLocationType].flat().some((type) => /telecommute/i.test(String(type)));
    if (remote) {
      // Where a remote employee may live, e.g. "Remote (Romania)"
      const regions = [posting.applicantLocationRequirements].flat().map(placeName).filter(Boolean);
      // Sites put "Anywhere" or "Remote" in every field they have
      places = places.filter((place) => !ANYWHERE.test(place) && !regions.includes(place));
      places.unshift(remoteLocation(regions));
    }
    const unique = [...new Set(places)];
    if (unique.length === 0) {
      return null;
    }
    return (unique.length > MAX_PLACES) ? `${unique.slice(0, MAX_PLACES).join('; ')} …` : unique.join('; ');
  }

  /**
   * "Remote", or "Remote (Romania, Poland)" where the job names the countries it's open to: not when it names every
   * country there is, as some sites do, or "Anywhere".
   */
  function remoteLocation(regions) {
    const named = regions.map(plainText).filter((region) => region && !ANYWHERE.test(region));
    const label = chrome.i18n.getMessage('remote') || 'Remote';
    return (named.length > 0 && named.length <= MAX_PLACES) ? `${label} (${named.join(', ')})` : label;
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
      .filter(Boolean)
      // Not again what an earlier part says already, as in a locality of "Romania, Remote" in Romania
      .filter((part, index, all) => !all.slice(0, index).some((earlier) => earlier.toLowerCase().includes(part.toLowerCase())));
    return parts.length > 0 ? parts.join(', ') : plainText(place.name);
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
})();
