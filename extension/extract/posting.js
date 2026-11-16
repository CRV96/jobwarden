// Part of reading a job posting (extract.js): the posting's schema.org JobPosting, which job sites add for Google's
// job search, as JSON-LD (<script type="application/ld+json">) or microdata (itemprop attributes), and each of its
// fields as the web app takes it: the place, the salary, the kind of employment, the company's logo.
globalThis.jobWarden.extract.posting = (() => {
  const { htmlToText, parseJson, plainText, webAddress } = globalThis.jobWarden.extract.common;

  /** More places than this, and a location lists only the first, and a remote job only "Remote". */
  const MAX_PLACES = 3;

  /** Places that say no more than "remote": a remote job's location shows "Remote" once. */
  const ANYWHERE = /^(anywhere|worldwide|global|everywhere|remote)$/i;

  /** Logos a JobPosting has that aren't the company's: the job board's own, for a company without one. */
  const IGNORED_LOGOS = [/^https?:\/\/[^/]*indeed\.com\/jsf\/images\//];

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

  return {
    fromJobPosting, findMicrodataPosting, findJobPosting, imageAddress, usableLogo, remoteLocation, employmentTypeOf,
  };
})();
