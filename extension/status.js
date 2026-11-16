// How an application's status looks: on the popup's card, and on the toolbar icon of a tab showing its posting.

import { message } from './i18n.js';

/**
 * The mark and colour of each status on the toolbar icon, the app's colour of the status (web/src/main/frontend/app.css,
 * --jw-*), and the icon the popup's card shows next to it: bookmarked, or applied and further.
 */
const LOOKS = {
  SAVED: { mark: '★', color: '#8e8e93', icon: 'bookmark-simple' },
  APPLIED: { mark: '✓', color: '#007aff', icon: 'check' },
  INTERVIEWING: { mark: '✓', color: '#c56a00', icon: 'check' },
  OFFER: { mark: '✓', color: '#1f9e45', icon: 'check' },
  POSITION_REOPENED: { mark: '✓', color: '#0f8a8a', icon: 'check' },
  REJECTED: { mark: '✓', color: '#e0302a', icon: 'check' },
  NO_RESPONSE: { mark: '✓', color: '#9a3fd0', icon: 'check' },
  POSITION_CLOSED: { mark: '✓', color: '#8f7150', icon: 'check' },
  WITHDRAWN: { mark: '✓', color: '#8e8e93', icon: 'check' },
};

/** @param {import('./api.js').ApplicationStatus} status */
export function statusLook(status) {
  return LOOKS[status];
}

/** "Bookmarked", "Applied"... in the browser's language: the status_* messages. */
export function statusName(status) {
  return message(`status_${status}`);
}

/**
 * Whether the icon shows the status of each tab's page, which the user turns on in the settings. It needs the
 * "tabs" permission: without it, Chrome doesn't tell extensions which page a tab shows.
 */
export function iconShowsStatus() {
  return chrome.permissions.contains({ permissions: ['tabs'] });
}

/**
 * Shows the status on the toolbar icon, for this tab only: ★ bookmarked, ✓ applied. Nothing for null.
 *
 * @param {import('./api.js').ApplicationStatus | null} status
 */
export async function showOnIcon(tabId, status) {
  const look = status ? LOOKS[status] : null;
  await chrome.action.setBadgeText({ tabId, text: look?.mark ?? '' });
  await chrome.action.setTitle({ tabId, title: look ? `JobWarden · ${statusName(status)}` : 'JobWarden' });
  if (look) {
    await chrome.action.setBadgeBackgroundColor({ tabId, color: look.color });
    await chrome.action.setBadgeTextColor({ tabId, color: '#ffffff' });
  }
}
