// How an application's status looks: on the popup's card, and on the toolbar icon of a tab showing its posting.

import { message } from './i18n.js';

/**
 * The mark and colour of each status. The tone is the popup card's style: bookmarked, applied and on its way, or
 * closed.
 */
const LOOKS = {
  SAVED: { mark: '★', color: '#d97706', tone: 'saved' },
  APPLIED: { mark: '✓', color: '#16a34a', tone: 'applied' },
  INTERVIEWING: { mark: '✓', color: '#16a34a', tone: 'applied' },
  OFFER: { mark: '✓', color: '#16a34a', tone: 'applied' },
  REJECTED: { mark: '✓', color: '#6b7280', tone: 'closed' },
  NO_RESPONSE: { mark: '✓', color: '#6b7280', tone: 'closed' },
  POSITION_CLOSED: { mark: '✓', color: '#6b7280', tone: 'closed' },
  WITHDRAWN: { mark: '✓', color: '#6b7280', tone: 'closed' },
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
