// Keeps the toolbar icon of every tab showing whether its page is already in JobWarden: ★ bookmarked, ✓ applied.
// Only once the user turns it on in the settings, which grants the "tabs" permission: without it, Chrome
// doesn't tell extensions which page a tab shows. Each page a tab opens is then looked up on the user's server.
// Also keeps an Easy Apply run going (runs.js).
//
// Chrome runs it as a service worker, Firefox as an event page: manifest.json lists it as both, and each browser
// takes the one it supports.

import { lookUp } from './api.js';
import { canonicalLink } from './links.js';
import { keepRunsGoing } from './runs.js';
import { hostPermission, loadSettings } from './settings.js';
import { iconShowsStatus, showOnIcon } from './status.js';

/** The page each tab was last looked up for, so the answer for a page the tab has since left is dropped. */
const lookingUp = new Map();

keepRunsGoing();

// Also for pages that change their address without loading, like LinkedIn's search showing another job
chrome.tabs.onUpdated.addListener((tabId, changeInfo) => {
  if (changeInfo.url) {
    refresh(tabId, changeInfo.url);
  }
});
chrome.tabs.onRemoved.addListener((tabId) => lookingUp.delete(tabId));

// What every icon should show may have changed: another server or profile, or the permission was granted or taken back
chrome.storage.onChanged.addListener((changes) => {
  if (changes.serverUrl || changes.userId) {
    refreshAll();
  }
});
chrome.permissions.onAdded.addListener(refreshAll);
chrome.permissions.onRemoved.addListener(refreshAll);

async function refreshAll() {
  for (const tab of await chrome.tabs.query({})) {
    // Without the permission, tab.url is missing and the icon is cleared
    refresh(tab.id, tab.url);
  }
}

async function refresh(tabId, address) {
  lookingUp.set(tabId, address);
  let status = null;
  try {
    status = await statusOf(address);
  } catch (error) {
    // The server is down or answered with an error: the icon shows nothing rather than something wrong
    console.warn('Could not look the page up', error);
  }
  if (lookingUp.get(tabId) === address) {
    // Fails if the tab closed in the meantime, which is fine
    await showOnIcon(tabId, status).catch(() => {});
  }
}

/** @returns {Promise<import('./api.js').ApplicationStatus | null>} null when there's nothing to show */
async function statusOf(address) {
  const link = canonicalLink(address);
  if (link === null || !(await iconShowsStatus())) {
    return null;
  }
  const settings = await loadSettings();
  if (settings.userId === undefined
      || new URL(link).origin === new URL(settings.serverUrl).origin
      || !(await chrome.permissions.contains({ origins: [hostPermission(settings.serverUrl)] }))) {
    return null;
  }
  return (await lookUp(settings, link))?.status ?? null;
}
