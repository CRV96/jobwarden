// The Easy Apply run (easyapply.js) in a LinkedIn job search: the popup starts and stops it, and the background keeps
// it going. The background starts it, as the popup may close before it's done; injects it again when LinkedIn loads
// the page anew (with the LinkedIn permission); and does for it what LinkedIn's page can't: calls the server, and
// reads a job (extract.js) to record it. One run at a time, in one tab; its state is in chrome.storage.local, where
// easyapply.js updates it.

import { capture, formAnswers, learnAnswer, lookUp } from './api.js';
import { loadSettings } from './settings.js';

/** Where the run is kept: {tabId, limit, saveOthers, status, applied, saved, skipped, seen}. */
export const RUN_KEY = 'easyApplyRun';

/**
 * The host permission the run asks for when it starts, to go on after LinkedIn loads the page anew. Without it, the
 * run has the tab while the page stays loaded: the popup's click gave it the tab (activeTab).
 */
export const LINKEDIN_PERMISSION = 'https://www.linkedin.com/*';

/** How many applications a run sends at most, unless the user sets another number: they can't take one back. */
export const DEFAULT_LIMIT = 25;
export const MAX_LIMIT = 100;

/** Whether the address is a LinkedIn job search, or a list of jobs LinkedIn picked (collections), the run goes through. */
export function isJobSearch(address) {
  const url = URL.parse(address ?? '');
  return url !== null && /(^|\.)linkedin\.com$/.test(url.hostname) && /^\/jobs\/(search|collections)\b/.test(url.pathname);
}

/** The options of the last run, in chrome.storage.sync like the settings. */
export async function loadRunOptions() {
  const { runLimit = DEFAULT_LIMIT, runSaveOthers = true } = await chrome.storage.sync.get(['runLimit', 'runSaveOthers']);
  return { limit: runLimit, saveOthers: runSaveOthers };
}

/** @returns {Promise<object | null>} the latest run, going or not */
export async function currentRun() {
  return (await chrome.storage.local.get(RUN_KEY))[RUN_KEY] ?? null;
}

/**
 * Starts a run in the tab, in place of any other.
 *
 * @param {{limit: number, saveOthers: boolean}} options
 */
async function startRun(tabId, { limit: asked, saveOthers }) {
  const limit = Math.min(Math.max(Math.trunc(Number(asked)) || DEFAULT_LIMIT, 1), MAX_LIMIT);
  await chrome.storage.sync.set({ runLimit: limit, runSaveOthers: Boolean(saveOthers) });
  await chrome.storage.local.set({
    [RUN_KEY]: { tabId, limit, saveOthers: Boolean(saveOthers), status: 'running', applied: 0, saved: 0, skipped: 0,
      seen: [] },
  });
  await inject(tabId);
}

/** easyapply.js stops at its next step: it watches the run in storage. */
export async function stopRun() {
  const run = await currentRun();
  if (run?.status === 'running') {
    await chrome.storage.local.set({ [RUN_KEY]: { ...run, status: 'stopped' } });
  }
}

/** For background.js: starts a run for the popup, injects it again after a page load, and answers its messages. */
export function keepRunsGoing() {
  chrome.tabs.onUpdated.addListener(async (tabId, changeInfo, tab) => {
    if (changeInfo.status !== 'complete') {
      return;
    }
    const run = await currentRun();
    if (run?.status === 'running' && run.tabId === tabId && isJobSearch(tab.url)) {
      // easyapply.js does nothing when it's still on the page
      await inject(tabId).catch((error) => console.warn('Could not go on with the Easy Apply run', error));
    }
  });
  chrome.tabs.onRemoved.addListener(async (tabId) => {
    if ((await currentRun())?.tabId === tabId) {
      await stopRun();
    }
  });
  chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    const handler = HANDLERS[request?.type];
    if (!handler || sender.id !== chrome.runtime.id) {
      return false;
    }
    handler(request, sender.tab?.id).then((result) => sendResponse({ result: result ?? null }),
      (error) => sendResponse({ error: error.message ?? String(error) }));
    // The answer comes later
    return true;
  });
}

/** What the popup (start) and easyapply.js (the rest, from the run's tab) ask. */
const HANDLERS = {
  'easyApply:start': async ({ tabId, limit, saveOthers }) => startRun(tabId, { limit, saveOthers }),
  'easyApply:answers': async () => formAnswers(await requireSettings()),
  'easyApply:learn': async ({ question, answer }) => learnAnswer(await requireSettings(), question, answer),
  'easyApply:lookUp': async ({ link }) => lookUp(await requireSettings(), link),
  'easyApply:record': async ({ link, action }, tabId) => record(tabId, link, action),
};

/**
 * Saves the job the tab shows: as applied, by the extension (EASY_APPLY), or bookmarked (BOOKMARK). It reads the job
 * the way the popup does, with extract.js.
 */
async function record(tabId, link, action) {
  const settings = await requireSettings();
  const [injection] = await chrome.scripting.executeScript({ target: { tabId }, files: ['extract.js'] });
  const job = injection?.result;
  if (!job?.title) {
    throw new Error(`JobWarden couldn’t read the job at ${link}`);
  }
  const { application } = await capture(settings, { ...job, action, link, includeTime: settings.includeTime });
  return application;
}

async function requireSettings() {
  const settings = await loadSettings();
  if (settings.userId === undefined) {
    throw new Error(chrome.i18n.getMessage('setupNeeded'));
  }
  return settings;
}

function inject(tabId) {
  return chrome.scripting.executeScript({ target: { tabId }, files: ['easyapply.js'] });
}
