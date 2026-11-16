/**
 * The scripts injected into a tab (chrome.scripting.executeScript), in order: each one's parts first, as the browser
 * runs them one after the other and hands back what the last one ends with. They're classic scripts, as injected scripts
 * must be: each part registers what it gives on globalThis.jobWarden, for those after it.
 */

/** Reads the job the tab shows (extract.js): its title, company, place, description, salary and logo. */
export const EXTRACT_SCRIPTS = Object.freeze([
  'extract/common.js', 'extract/posting.js', 'extract/sites.js', 'extract/linkedin.js', 'extract.js',
]);

/** The Easy Apply run on a LinkedIn job search (easyapply.js). */
export const EASY_APPLY_SCRIPTS = Object.freeze([
  'easyapply/waiting.js', 'easyapply/questions.js', 'easyapply/linkedin.js', 'easyapply/bar.js', 'easyapply.js',
]);
