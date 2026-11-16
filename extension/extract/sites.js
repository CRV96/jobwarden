// Part of reading a job posting (extract.js): the job sites the JobPosting doesn't cover, or doesn't cover well, and
// where each shows what it leaves out (SITES). LinkedIn has a part of its own (linkedin.js).
globalThis.jobWarden.extract.sites = (() => {
  const {
    htmlToText, joinedText, metaTag, ownText, parseJson, plainText, textOf, webAddress,
  } = globalThis.jobWarden.extract.common;
  const { imageAddress, remoteLocation, usableLogo } = globalThis.jobWarden.extract.posting;

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

  return { SITES, readSite };
})();
