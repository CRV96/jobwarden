// The server's capture API, for the popup and the icon (background.js). Every answer about an application is a
// CapturedApplication, so what the extension shows always follows what the server has.
//
// A call throws a ServerError when the server answers with an error, and fetch's TypeError when it can't be
// reached at all.

import { CAPTURE_PATH } from './settings.js';

/**
 * @typedef {'SAVED' | 'APPLIED' | 'INTERVIEWING' | 'OFFER' | 'REJECTED' | 'WITHDRAWN'} ApplicationStatus
 * @typedef {{
 *   applicationId: number,
 *   title: string,
 *   company: string | null,
 *   status: ApplicationStatus,
 *   statusDate: string,
 *   statusTime: string | null,
 * }} CapturedApplication statusDate and statusTime say when it got its status, e.g. "2026-09-29" and "20:31:05"
 * @typedef {{serverUrl: string, userId: number}} Settings
 */

/**
 * An error answer. Its problem is the server's problem details: a message in the request's language (detail), an
 * error code (errorCode), and for invalid fields an errors object (field → message). Empty if the answer wasn't
 * one, e.g. a proxy's error page.
 */
export class ServerError extends Error {
  constructor(status, problem) {
    super(problem.detail ?? `The server answered ${status}`);
    this.status = status;
    this.problem = problem;
  }
}

/**
 * @param {Settings} settings
 * @returns {Promise<CapturedApplication | null>} null if the profile doesn't track the link
 */
export async function lookUp({ serverUrl, userId }, link) {
  const response = await send(serverUrl, '', { query: { userId, link } });
  return response.status === 204 ? null : response.json();
}

/**
 * Saves the posting, or moves a bookmark to applied when it's already saved and the action is APPLY.
 *
 * @param {Settings} settings
 * @returns {Promise<{application: CapturedApplication, created: boolean}>}
 */
export async function capture({ serverUrl, userId }, posting) {
  const response = await send(serverUrl, '', { method: 'POST', body: { ...posting, userId } });
  return { application: await response.json(), created: response.status === 201 };
}

/**
 * Takes back "I applied".
 *
 * @param {Settings} settings
 * @returns {Promise<CapturedApplication>} the application, bookmarked again
 */
export async function moveBackToSaved({ serverUrl, userId }, applicationId) {
  const response = await send(serverUrl, `/${applicationId}/move-back-to-saved`, { method: 'POST', query: { userId } });
  return response.json();
}

/** @param {Settings} settings */
export async function remove({ serverUrl, userId }, applicationId) {
  await send(serverUrl, `/${applicationId}`, { method: 'DELETE', query: { userId } });
}

async function send(serverUrl, path, { method = 'GET', query = {}, body } = {}) {
  const url = new URL(serverUrl + CAPTURE_PATH + path);
  for (const [name, value] of Object.entries(query)) {
    url.searchParams.set(name, value);
  }
  const response = await fetch(url, {
    method,
    headers: {
      ...(body && { 'Content-Type': 'application/json' }),
      Accept: 'application/json, application/problem+json',
      // The server's error messages then match the extension's language
      'Accept-Language': chrome.i18n.getUILanguage(),
    },
    body: body && JSON.stringify(body),
  });
  if (!response.ok) {
    throw new ServerError(response.status, await response.json().catch(() => ({})));
  }
  return response;
}
