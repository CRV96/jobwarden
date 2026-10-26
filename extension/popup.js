import { capture, lookUp, moveBackToSaved, remove, ServerError } from './api.js';
import { message, translatePage } from './i18n.js';
import { canonicalLink } from './links.js';
import { APPLICATIONS_PATH, hostPermission, loadSettings, saveSettings } from './settings.js';
import { iconShowsStatus, showOnIcon, statusLook, statusName } from './status.js';

// The popup first looks the page up. When JobWarden has it, a card shows where it's at, with what can change from
// here: a bookmark can be marked applied or removed, an application moved back to bookmarks or removed. Further
// along (interviewing, offer...), the timeline says more than a click can undo, so that's for the app. When
// JobWarden doesn't have it, the form shows what extract.js found, to correct and save.

/** The site icon's width and height in pixels: what a tab shows, on a screen with twice the pixels. */
const ICON_SIZE = 32;

/** The server's code for a profile id that matches no profile: fixed in the settings. */
const PROFILE_NOT_FOUND = 'IDENTITY-001';

/** The message with each request field's label, for the server's validation errors. */
const FIELD_LABELS = {
  title: 'jobTitle', company: 'company', location: 'location', description: 'description', link: 'link',
  userId: 'profileId',
};

const card = document.getElementById('card');
const form = document.getElementById('capture');
const includeTimeOption = document.getElementById('include-time-option');
const includeTime = document.getElementById('include-time');
const actions = document.getElementById('actions');
const applyButton = document.getElementById('apply');
const bookmarkButton = document.getElementById('bookmark');
const moveBackButton = document.getElementById('move-back');
const removeButton = document.getElementById('remove');
const confirmation = document.getElementById('confirm');
const statusLine = document.getElementById('status');
const openApplication = document.getElementById('open-application');
const openSettings = document.getElementById('open-settings');

/** Thrown instead of calling a server Chrome doesn't let the extension reach yet: fetch would only say it failed. */
class PermissionMissingError extends Error {}

translatePage();
// The settings page is the extension's options page
document.getElementById('settings').addEventListener('click', () => chrome.runtime.openOptionsPage());
openSettings.addEventListener('click', () => chrome.runtime.openOptionsPage());

const settings = await loadSettings();
describeSettings();
const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
/** The link JobWarden keeps for the page; on a search showing a job, the job's own, once extract.js finds it. */
let link = canonicalLink(tab.url);

/** @type {import('./api.js').CapturedApplication | null} the page's application, while JobWarden has one */
let application = null;

/** What extract.js found on the page, or null if it couldn't read it */
let job = null;

/** The site's icon as a PNG data URL, or null if the browser wouldn't give it: sent along with what's saved */
let icon = null;

let reading = Promise.resolve();
let readingIcon = Promise.resolve();

if (settings.userId === undefined) {
  showStatus(message('setupNeeded'));
  openSettings.hidden = false;
} else if (link === null) {
  showStatus(message('pageUnreadable'), 'error');
} else {
  reading = readPage();
  readingIcon = readIcon();
  listen();
  await open();
}

/** Looks the page up while reading it: reading a LinkedIn page can take seconds, and a saved page doesn't need it. */
async function open() {
  showStatus(message('checking'));
  let failed = false;
  try {
    application = await server(() => lookUp(settings, link));
  } catch (error) {
    failed = true;
    showFailure(error);
  }
  if (application === null) {
    if (!failed) {
      showStatus(message('reading'));
    }
    await reading;
    failed = failed || !(await lookUpTheJobsOwnLink());
  }
  if (failed) {
    // The error stays shown
  } else if (application === null && job === null) {
    showStatus(message('pageUnreadable'), 'error');
  } else {
    showStatus('');
  }
  render();
  await updateIcon();
}

/**
 * On a search that shows a job beside the results without naming it in its address (Glassdoor), the tab's address
 * isn't the job's: the job JobWarden may have is under the link extract.js found.
 *
 * @returns {Promise<boolean>} false if looking it up failed (the error is shown)
 */
async function lookUpTheJobsOwnLink() {
  const own = job?.link ? canonicalLink(job.link) : null;
  if (own === null || own === link) {
    return true;
  }
  link = own;
  try {
    application = await server(() => lookUp(settings, link));
    return true;
  } catch (error) {
    showFailure(error);
    return false;
  }
}

function listen() {
  includeTime.checked = settings.includeTime;
  // Remembered for the next posting
  includeTime.addEventListener('change', () => saveSettings({ includeTime: includeTime.checked }));
  applyButton.addEventListener('click', () => save('APPLY'));
  bookmarkButton.addEventListener('click', () => save('BOOKMARK'));
  moveBackButton.addEventListener('click', moveBack);
  removeButton.addEventListener('click', askToRemove);
  document.getElementById('confirm-remove').addEventListener('click', removeApplication);
  document.getElementById('cancel-remove').addEventListener('click', render);
}

/** Shows the card and what can change from here, or the form while JobWarden doesn't have the page. */
function render() {
  const status = application?.status;
  card.hidden = application === null;
  if (application !== null) {
    fillCard(application);
  }
  form.hidden = application !== null || job === null;
  applyButton.hidden = (application !== null) ? status !== 'SAVED' : job === null;
  bookmarkButton.hidden = form.hidden;
  moveBackButton.hidden = status !== 'APPLIED';
  removeButton.hidden = status !== 'SAVED' && status !== 'APPLIED';
  removeButton.textContent = message(status === 'SAVED' ? 'removeBookmark' : 'removeApplication');
  includeTimeOption.hidden = applyButton.hidden;
  actions.hidden = false;
  confirmation.hidden = true;
  openApplication.hidden = application === null;
  if (application !== null) {
    openApplication.href = `${settings.serverUrl}${APPLICATIONS_PATH}/${application.applicationId}`;
  }
}

/** @param {import('./api.js').CapturedApplication} application */
function fillCard(application) {
  const look = statusLook(application.status);
  card.className = `card ${look.tone}`;
  document.getElementById('card-mark').textContent = look.mark;
  document.getElementById('card-status').textContent = statusName(application.status);
  document.getElementById('card-job').textContent = [application.title, application.company].filter(Boolean)
    .join(' · ');
  document.getElementById('card-when').textContent = formatMoment(application.statusDate, application.statusTime);
}

/**
 * Saves the page, or marks its bookmark applied: capturing a bookmarked posting again as applied moves it to
 * APPLIED.
 *
 * @param {'APPLY' | 'BOOKMARK'} action
 */
async function save(action) {
  if (application === null && !form.reportValidity()) {
    return;
  }
  const before = application;
  // Long done by the time the user clicks
  await readingIcon;
  try {
    const { application: saved, created } = await server(() => capture(settings, posting(action)));
    await show(saved);
    let text = 'alreadySaved';
    if (created) {
      text = (action === 'APPLY') ? 'savedApplied' : 'savedBookmarked';
    } else if (action === 'APPLY' && before?.status === 'SAVED') {
      text = 'markedApplied';
    }
    showStatus(message(text), 'success');
  } catch (error) {
    showFailure(error);
  }
}

/**
 * The form's corrections to what was found; for a bookmark, only what the server needs to find it again. The icon
 * goes along either way: the server keeps the site's latest.
 */
function posting(action) {
  const request = { action, link, includeTime: includeTime.checked, ...(icon && { icon }) };
  if (application !== null) {
    return { ...request, title: application.title };
  }
  return {
    ...job,
    ...request,
    title: form.elements.title.value.trim(),
    company: blankToNull(form.elements.company.value),
    location: blankToNull(form.elements.location.value),
    description: blankToNull(form.elements.description.value),
  };
}

async function moveBack() {
  try {
    await show(await server(() => moveBackToSaved(settings, application.applicationId)));
    showStatus(message('movedBack'), 'success');
  } catch (error) {
    showFailure(error);
  }
}

/** Removing can't be undone, so it takes a second click. */
function askToRemove() {
  document.getElementById('confirm-question').textContent = message(application.status === 'SAVED'
    ? 'confirmRemoveBookmark' : 'confirmRemoveApplication');
  actions.hidden = true;
  includeTimeOption.hidden = true;
  confirmation.hidden = false;
}

async function removeApplication() {
  try {
    await server(() => remove(settings, application.applicationId));
    await show(null);
    showStatus(message('removed'), 'success');
  } catch (error) {
    render();
    showFailure(error);
  }
}

/** After a change: the application as the server now has it, or null once it's removed (the form shows again). */
async function show(changed) {
  application = changed;
  if (application === null) {
    await reading;
  }
  render();
  await updateIcon();
}

/** The toolbar icon follows too, when the user turned that on (settings). */
async function updateIcon() {
  if (await iconShowsStatus()) {
    await showOnIcon(tab.id, application?.status ?? null);
  }
}

/**
 * Runs extract.js in the tab and fills the form with what it found. Leaves job null where extensions can't run
 * scripts, such as the Chrome Web Store, or on a page it couldn't read.
 */
async function readPage() {
  try {
    const [injection] = await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ['extract.js'] });
    job = injection?.result ?? null;
  } catch (error) {
    console.warn('Could not read the page', error);
  }
  if (job !== null) {
    form.elements.title.value = job.title ?? '';
    form.elements.company.value = job.company ?? '';
    form.elements.location.value = job.location ?? '';
    form.elements.description.value = job.description ?? '';
  }
}

/**
 * The icon the tab shows, drawn into a 32×32 PNG, the one format the server takes. Chrome hands extensions its own
 * copy of a page's icon (the favicon permission). Firefox has no such copy, so the tab's icon address is fetched
 * instead: often a data: URL already, otherwise it works for an icon the page's site serves to anyone. When neither
 * works, the app shows a globe for the site, as before.
 */
async function readIcon() {
  if (!tab.favIconUrl) {
    // The tab shows no icon of its own: Chrome's copy would be its generic globe
    return;
  }
  for (const address of [browserIconAddress(tab.url), tab.favIconUrl]) {
    try {
      icon = await toPng(address);
      return;
    } catch {
      // Not from here: the next address, or no icon
    }
  }
}

/** Where Chrome serves its copy of a page's icon. Firefox has nothing there, and the fetch fails. */
function browserIconAddress(pageUrl) {
  const address = new URL(chrome.runtime.getURL('/_favicon/'));
  address.searchParams.set('pageUrl', pageUrl);
  address.searchParams.set('size', String(ICON_SIZE));
  return address.href;
}

/**
 * Fetches the image and draws it into a PNG. Through a blob: address, so the canvas can export it whatever site the
 * image came from.
 *
 * @returns {Promise<string>} a data:image/png;base64 URL
 */
async function toPng(address) {
  const response = await fetch(address);
  if (!response.ok) {
    throw new Error(`The icon answered ${response.status}`);
  }
  const blob = await response.blob();
  if (!blob.type.startsWith('image/')) {
    throw new Error(`The icon is a ${blob.type}`);
  }
  const blobAddress = URL.createObjectURL(blob);
  try {
    const image = new Image();
    image.src = blobAddress;
    await image.decode();
    const canvas = document.createElement('canvas');
    canvas.width = ICON_SIZE;
    canvas.height = ICON_SIZE;
    canvas.getContext('2d').drawImage(image, 0, 0, ICON_SIZE, ICON_SIZE);
    return canvas.toDataURL('image/png');
  } finally {
    URL.revokeObjectURL(blobAddress);
  }
}

/** Calls the server with the buttons disabled. */
async function server(call) {
  if (!(await chrome.permissions.contains({ origins: [hostPermission(settings.serverUrl)] }))) {
    throw new PermissionMissingError();
  }
  setBusy(true);
  try {
    return await call();
  } finally {
    setBusy(false);
  }
}

/**
 * The server answers errors with problem details: a message in the request's language, an error code, and for
 * invalid fields an "errors" object (field → message). A proxy in front of it may answer with anything else.
 */
function showFailure(error) {
  if (error instanceof PermissionMissingError) {
    showStatus(message('permissionMissing', settings.serverUrl), 'error');
    openSettings.hidden = false;
  } else if (error instanceof ServerError) {
    const { problem } = error;
    const fieldErrors = Object.entries(problem.errors ?? {})
      .map(([field, text]) => `${FIELD_LABELS[field] ? message(FIELD_LABELS[field]) : field}: ${text}`);
    const text = (fieldErrors.length > 0) ? fieldErrors.join(' ')
      : problem.detail ?? message('unexpectedResponse', String(error.status));
    showStatus(problem.errorCode ? `${text} (${problem.errorCode})` : text, 'error');
    openSettings.hidden = problem.errorCode !== PROFILE_NOT_FOUND;
  } else {
    console.warn('Could not reach the server', error);
    showStatus(message('serverUnreachable', settings.serverUrl), 'error');
  }
}

/** @param {'info' | 'success' | 'error'} kind */
function showStatus(text, kind = 'info') {
  statusLine.textContent = text;
  statusLine.className = kind;
  openSettings.hidden = true;
}

/** The settings button's name, and its tooltip: which server and profile the popup saves to. */
function describeSettings() {
  const button = document.getElementById('settings');
  button.setAttribute('aria-label', message('settings'));
  button.title = (settings.userId === undefined) ? message('settings')
    : `${message('settings')} · ${new URL(settings.serverUrl).host} · ${message('profileId')} ${settings.userId}`;
}

function setBusy(busy) {
  form.setAttribute('aria-busy', String(busy));
  document.querySelectorAll('.actions button').forEach((button) => {
    button.disabled = busy;
  });
}

/**
 * "29 Sep 2026, 20:31" in the browser's language. The server's date and time are already its wall clock, the same
 * the app shows, so they're formatted as they are, not converted from another time zone.
 */
function formatMoment(date, time) {
  const [year, month, day] = date.split('-').map(Number);
  const [hours = 0, minutes = 0] = time ? time.split(':').map(Number) : [];
  return new Intl.DateTimeFormat(chrome.i18n.getUILanguage(), {
    dateStyle: 'medium',
    ...(time && { timeStyle: 'short' }),
  }).format(new Date(year, month - 1, day, hours, minutes));
}

function blankToNull(value) {
  return value.trim() || null;
}
