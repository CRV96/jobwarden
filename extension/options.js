import { message, translatePage } from './i18n.js';
import { hostPermission, loadSettings, normalizeServerUrl, PROFILES_PATH, saveSettings } from './settings.js';
import { iconShowsStatus } from './status.js';

const form = document.getElementById('options');
const serverUrlInput = form.elements.serverUrl;
const userIdInput = form.elements.userId;
const openProfiles = document.getElementById('open-profiles');
const showOnIconInput = document.getElementById('show-on-icon');
const status = document.getElementById('status');

translatePage();
document.getElementById('version').textContent = message('extensionVersion', chrome.runtime.getManifest().version);

// Listen before the first await, so a quick click can't submit the form the browser's way
form.addEventListener('submit', save);
serverUrlInput.addEventListener('input', () => {
  serverUrlInput.setCustomValidity('');
  updateProfilesLink();
});
showOnIconInput.addEventListener('change', changeShowOnIcon);

const settings = await loadSettings();
serverUrlInput.value = settings.serverUrl;
userIdInput.value = settings.userId ?? '';
updateProfilesLink();
showOnIconInput.checked = await iconShowsStatus();

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
 * Turned on, it's the "tabs" permission: requested straight from the click, like the server's in save(). Turned off,
 * the permission goes back, and background.js clears the icons.
 */
function changeShowOnIcon() {
  const wanted = showOnIconInput.checked;
  const change = wanted
    ? chrome.permissions.request({ permissions: ['tabs'] })
    : chrome.permissions.remove({ permissions: ['tabs'] }).then(() => false);
  change
    .then((on) => {
      showOnIconInput.checked = on;
      if (on === wanted) {
        showStatus(message('saved'), 'success');
      }
    })
    .catch((error) => {
      console.warn('Could not change the icon option', error);
      showOnIconInput.checked = !wanted;
      showStatus(message('notSaved'), 'error');
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
  status.className = kind;
}
