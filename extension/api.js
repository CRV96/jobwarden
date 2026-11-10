// The server's capture API, for the popup, the icon (background.js) and the reminders (reminders.js). Every answer about an application is a
// CapturedApplication, so what the extension shows always follows what the server has.
//
// A call throws a ServerError when the server answers with an error, and fetch's TypeError when it can't be
// reached at all.

import { CAPTURE_PATH } from './settings.js';

/**
 * @typedef {'SAVED' | 'APPLIED' | 'INTERVIEWING' | 'OFFER' | 'REJECTED' | 'NO_RESPONSE' | 'POSITION_CLOSED'
 *   | 'WITHDRAWN'} ApplicationStatus
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

/**
 * The server's version, and the latest released: the popup says when there's a newer JobWarden.
 *
 * @param {Settings} settings
 * @returns {Promise<{version: string, latest: {version: string, name: string | null, url: string} | null,
 *   releases: string}>}
 */
export async function serverVersion({ serverUrl }) {
  const response = await send(serverUrl, '/version');
  return response.json();
}

/**
 * What the Easy Apply run fills LinkedIn's forms in with: the contact details from the CV page, and the answers from
 * the Auto-apply page, the profile's own questions among them.
 *
 * @param {Settings} settings
 */
export async function formAnswers({ serverUrl, userId }) {
  const response = await send(serverUrl, '/answers', { query: { userId } });
  return response.json();
}

/**
 * Keeps an answer the user gave during a run among the profile's own questions, for the next forms.
 *
 * @param {Settings} settings
 * @returns {Promise<object>} the answers with it
 */
export async function learnAnswer({ serverUrl, userId }, question, answer) {
  const response = await send(serverUrl, '/answers', { method: 'POST', query: { userId }, body: { question, answer } });
  return response.json();
}

/**
 * The reminders set on the calendar that are due, for the background to show as notifications. Each comes once: the
 * server takes it, so a JobWarden page asking next doesn't show it again.
 *
 * @param {Settings} settings
 * @returns {Promise<Array<{id: number, applicationId: number, title: string, body: string}>>}
 */
export async function dueReminders({ serverUrl, userId }) {
  const response = await send(serverUrl, '/reminders/due', { method: 'POST', query: { userId } });
  return response.json();
}

/**
 * The job board the page is on, and whether the profile watches its company. Reads no board.
 *
 * @param {Settings} settings
 * @returns {Promise<WatchStatus | null>} null when the page isn't on a Greenhouse, Lever or Ashby board
 * @typedef {{name: string, system: string, boardUrl: string, watched: boolean, companyId: number | null,
 *   added: boolean | null, newJobs: number | null}} WatchStatus
 */
export async function watchStatus({ serverUrl, userId }, link) {
  const response = await send(serverUrl, '/watchlist', { query: { userId, link } });
  return response.status === 204 ? null : response.json();
}

/**
 * Watches the company whose board the page is on: the server reads the board, and adds the jobs that match the
 * saved searches to the AI Assistant page.
 *
 * @param {Settings} settings
 * @param {string | null} name the company's name as the page says it; the board's own if null
 * @returns {Promise<WatchStatus>} with added and newJobs
 */
export async function watch({ serverUrl, userId }, link, name) {
  const response = await send(serverUrl, '/watchlist', { method: 'POST', body: { userId, link, name } });
  return response.json();
}

/**
 * Stops watching the company. The jobs found on its board stay on the AI Assistant page.
 *
 * @param {Settings} settings
 */
export async function unwatch({ serverUrl, userId }, companyId) {
  await send(serverUrl, `/watchlist/${companyId}`, { method: 'DELETE', query: { userId } });
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
