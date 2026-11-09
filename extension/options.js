import { message, translatePage } from './i18n.js';
import { hostPermission, loadSettings, normalizeServerUrl, PROFILES_PATH, saveSettings } from './settings.js';
import { remindersShown } from './reminders.js';
import { iconShowsStatus } from './status.js';

const form = document.getElementById('options');
const serverUrlInput = form.elements.serverUrl;
const userIdInput = form.elements.userId;
const openProfiles = document.getElementById('open-profiles');
const showOnIconInput = document.getElementById('show-on-icon');
const showRemindersInput = document.getElementById('show-reminders');
const status = document.getElementById('status');

translatePage();
document.getElementById('version').textContent = message('extensionVersion', chrome.runtime.getManifest().version);

// Listen before the first await, so a quick click can't submit the form the browser's way
form.addEventListener('submit', save);
serverUrlInput.addEventListener('input', () => {
  serverUrlInput.setCustomValidity('');
  updateProfilesLink();
});
showOnIconInput.addEventListener('change', () => changePermission(showOnIconInput, 'tabs'));
showRemindersInput.addEventListener('change', () => changePermission(showRemindersInput, 'notifications'));

const settings = await loadSettings();
serverUrlInput.value = settings.serverUrl;
userIdInput.value = settings.userId ?? '';
updateProfilesLink();
showOnIconInput.checked = await iconShowsStatus();
showRemindersInput.checked = await remindersShown();

function save(event) {
  event.preventDefault();
  const serverUrl = normalizeServerUrl(serverUrlInput.value);
  if (serverUrl === null) {
    serverUrlInput.setCustomValidity(message('serverUrlInvalid'));
    form.reportValidity();
    return;
  }
  // Called straight from the click, before anything is awaited: Chrome asks the user only during a user action.
  // It resolves true without asking when the permission is already there, as for the default server.
  chrome.permissions.request({ origins: [hostPermission(serverUrl)] })
    .then(async (granted) => {
      if (!granted) {
        showStatus(message('permissionDenied', serverUrl), 'error');
        return;
      }
      await saveSettings({ serverUrl, userId: Number(userIdInput.value) });
      serverUrlInput.value = serverUrl;
      showStatus(message('saved'), 'success');
    })
    .catch((error) => {
      console.warn('Could not save the settings', error);
      showStatus(message('notSaved'), 'error');
    });
}

/**
 * An option that's a permission: "tabs" for the icon (background.js then clears the icons when it goes), and
 * "notifications" for the reminders (reminders.js). Turned on, it's requested straight from the click, like the
 * server's in save(); turned off, it goes back.
 */
function changePermission(input, permission) {
  const wanted = input.checked;
  // The browser reads the manifest only as it loads the extension: files updated since, e.g. by a git pull, can ask for
  // a permission the loaded manifest doesn't have yet, which the browser refuses
  if (wanted && !chrome.runtime.getManifest().optional_permissions?.includes(permission)) {
    input.checked = false;
    showStatus(message('reloadExtension'), 'error');
    return;
  }
  const change = wanted
    ? chrome.permissions.request({ permissions: [permission] })
    : chrome.permissions.remove({ permissions: [permission] }).then(() => false);
  change
    .then((on) => {
      input.checked = on;
      if (on === wanted) {
        showStatus(message('saved'), 'success');
      }
    })
    .catch((error) => {
      console.warn(`Could not change the ${permission} option`, error);
      input.checked = !wanted;
      showStatus(message('permissionError', error.message ?? String(error)), 'error');
    });
}

/** Points to the Profiles page of the server being typed in, hidden while the address isn't valid. */
function updateProfilesLink() {
  const serverUrl = normalizeServerUrl(serverUrlInput.value);
  openProfiles.hidden = serverUrl === null;
  if (serverUrl !== null) {
    openProfiles.href = serverUrl + PROFILES_PATH;
  }
}

/** @param {'success' | 'error'} kind */
function showStatus(text, kind) {
  status.textContent = text;
  status.className = `status ${kind}`;
}
