// Part of reading a job posting (extract.js): LinkedIn, which gives signed-in members no JobPosting. Its public job
// page has every field; the signed-in pages show them, under classes LinkedIn changes now and then.
globalThis.jobWarden.extract.linkedin = (() => {
  const { fetchPage, firstOfEach, htmlToText, pageTitle, textOf } = globalThis.jobWarden.extract.common;
  const { employmentTypeOf, imageAddress, usableLogo } = globalThis.jobWarden.extract.posting;

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
    return fetchPage(`${window.location.origin}/jobs-guest/jobs/api/jobPosting/${id}`);
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

  return { fromLinkedIn };
})();
