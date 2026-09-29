// The extension's settings, in chrome.storage.sync: they follow the user to every browser they sign in to.

export const DEFAULT_SERVER_URL = 'http://localhost:8080';

/** Where the server takes captures: jobwarden.capture.path in the server's application.properties. */
export const CAPTURE_PATH = '/api/captures';

/** Where the web app shows an application, and where it lists the profiles with the current one's id. */
export const APPLICATIONS_PATH = '/applications';
export const PROFILES_PATH = '/profiles';

/**
 * @returns {Promise<{serverUrl: string, userId: number | undefined, includeTime: boolean}>}
 */
export async function loadSettings() {
  const {
    serverUrl = DEFAULT_SERVER_URL,
    userId,
    includeTime = true,
  } = await chrome.storage.sync.get(['serverUrl', 'userId', 'includeTime']);
  return { serverUrl, userId, includeTime };
}

/** @param {Partial<{serverUrl: string, userId: number, includeTime: boolean}>} settings only the ones to change */
export function saveSettings(settings) {
  return chrome.storage.sync.set(settings);
}

/**
 * The server's address without a trailing slash, so paths can be appended to it. Keeps a path, for a server
 * behind a reverse proxy: "https://example.com/jobwarden/" becomes "https://example.com/jobwarden".
 *
 * @returns {string | null} null if it isn't an http or https address
 */
export function normalizeServerUrl(value) {
  let url;
  try {
    url = new URL(value.trim());
  } catch {
    return null;
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    return null;
  }
  return url.origin + url.pathname.replace(/\/+$/, '');
}

/**
 * The host permission the extension needs to call the server, e.g. "https://example.com/*". The manifest grants
 * it for the default server; any other needs the user's consent (optional_host_permissions).
 */
export function hostPermission(serverUrl) {
  return `${new URL(serverUrl).origin}/*`;
}
