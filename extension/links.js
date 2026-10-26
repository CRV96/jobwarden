// The link JobWarden keeps for a page. The server matches captures on the exact link, so saving a posting and
// looking a page up (the popup, and the icon in background.js) both go through canonicalLink.

/** Query parameters that only say where a click came from: dropped, so one posting keeps one link. */
const TRACKING_PARAMETER = /^(utm_.+|fbclid|gclid|gbraid|wbraid|msclkid|mc_[ce]id|_hs(enc|mi)|igshid|trk.*|trackingId|refId|lipi|alternateChannel)$/i;

/**
 * The page's address without what changes from one visit to the next: the hash and tracking parameters. On LinkedIn,
 * Indeed and Working Nomads, the job's own page, also for a search showing the job beside the results; on Glassdoor,
 * the address with only the job's id.
 *
 * @returns {string | null} null for a page that isn't on the web, such as chrome://extensions
 */
export function canonicalLink(address) {
  const url = URL.parse(address ?? '');
  if (url === null || (url.protocol !== 'http:' && url.protocol !== 'https:')) {
    return null;
  }
  const siteLink = linkedInLink(url) ?? indeedLink(url) ?? glassdoorLink(url) ?? workingNomadsLink(url);
  if (siteLink) {
    return siteLink;
  }
  url.hash = '';
  for (const name of [...url.searchParams.keys()]) {
    if (TRACKING_PARAMETER.test(name)) {
      url.searchParams.delete(name);
    }
  }
  return url.href;
}

/** A job page (/jobs/view/4012345678/, maybe with the title in the path) or a search showing one (?currentJobId=). */
function linkedInLink(url) {
  if (!/(^|\.)linkedin\.com$/.test(url.hostname)) {
    return null;
  }
  const id = url.searchParams.get('currentJobId') ?? url.pathname.match(/\/jobs\/view\/(?:[^/]*-)?(\d+)/)?.[1];
  return id ? `https://www.linkedin.com/jobs/view/${id}/` : null;
}

/** A job page (/viewjob?jk=) or a search showing one (?vjk=), on any country's site (ro.indeed.com, indeed.fr). */
function indeedLink(url) {
  if (!/(^|\.)indeed\.[a-z.]+$/.test(url.hostname)) {
    return null;
  }
  const id = url.searchParams.get('vjk') ?? url.searchParams.get('jk');
  return id ? `${url.origin}/viewjob?jk=${encodeURIComponent(id)}` : null;
}

/** A job page or a search showing a job (?jl=), on any country's site: only jl names the job, the rest tracks. */
function glassdoorLink(url) {
  if (!/(^|\.)glassdoor\.[a-z.]+$/.test(url.hostname)) {
    return null;
  }
  const id = url.searchParams.get('jl');
  return id ? `${url.origin}${url.pathname}?jl=${encodeURIComponent(id)}` : null;
}

/** The list showing a job beside it (?job=<the job's name>): the job's own page is /jobs/<that name>. */
function workingNomadsLink(url) {
  if (!/(^|\.)workingnomads\.com$/.test(url.hostname)) {
    return null;
  }
  const slug = url.searchParams.get('job');
  return slug && /^[\w-]+$/.test(slug) ? `${url.origin}/jobs/${slug}` : null;
}
