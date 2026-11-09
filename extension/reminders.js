// The reminders set on JobWarden's calendar, as the system's notifications. About once a minute while the browser
// runs, the background asks the server for the ones that are due, and shows each. Only once the user turns it on in
// the settings, which grants the "notifications" permission. The server gives each reminder once, to the extension or
// to a JobWarden page, whichever asks first. A click on one opens its job in JobWarden.

import { dueReminders } from './api.js';
import { APPLICATIONS_PATH, hostPermission, loadSettings } from './settings.js';

const ALARM = 'jobwarden-reminders';

/** A notification's id: the prefix, the reminder, and its job, e.g. jobwarden-reminder-12-345. */
const NOTIFICATION_PREFIX = 'jobwarden-reminder-';

/** Whether the user turned the reminders on in the settings: the permission is the setting. */
export function remindersShown() {
  return chrome.permissions.contains({ permissions: ['notifications'] });
}

/** Without the permission, the browser has no notifications API: the click listener waits until it's granted. */
let listening = false;

/** Called once, as the background starts: asks every minute while the permission is there. */
export function keepRemindersComing() {
  // Without the alarms permission, from a manifest loaded before it had it: nothing until the extension is reloaded,
  // rather than stopping the rest of the background with it
  if (!chrome.alarms) {
    console.warn('Reload the extension for the reminders: the manifest it was loaded with has no alarms permission');
    return;
  }
  chrome.alarms.onAlarm.addListener((alarm) => {
    if (alarm.name === ALARM) {
      showDueReminders();
    }
  });
  chrome.permissions.onAdded.addListener(schedule);
  chrome.permissions.onRemoved.addListener(schedule);
  schedule();
}

async function schedule() {
  if (!(await remindersShown())) {
    await chrome.alarms.clear(ALARM);
    return;
  }
  if (!listening && chrome.notifications) {
    chrome.notifications.onClicked.addListener(openJob);
    listening = true;
  }
  // Every minute, as often as the browsers let an extension's alarm go off. The alarm outlives the background, which
  // the browser stops when it's idle, and wakes it up.
  if (!(await chrome.alarms.get(ALARM))) {
    await chrome.alarms.create(ALARM, { periodInMinutes: 1 });
  }
  showDueReminders();
}

async function showDueReminders() {
  if (!(await remindersShown())) {
    return;
  }
  const settings = await loadSettings();
  if (settings.userId === undefined
      || !(await chrome.permissions.contains({ origins: [hostPermission(settings.serverUrl)] }))) {
    return;
  }
  let due;
  try {
    due = await dueReminders(settings);
  } catch (error) {
    // The server is down, e.g. the computer just woke up: the reminders wait for the next minute
    console.warn('Could not ask for the reminders', error);
    return;
  }
  for (const reminder of due) {
    chrome.notifications.create(`${NOTIFICATION_PREFIX}${reminder.id}-${reminder.applicationId}`, {
      type: 'basic',
      iconUrl: chrome.runtime.getURL('icons/icon-128.png'),
      title: reminder.title,
      message: reminder.body,
      priority: 2,
    });
  }
}

async function openJob(notificationId) {
  if (!notificationId.startsWith(NOTIFICATION_PREFIX)) {
    return;
  }
  const applicationId = notificationId.slice(NOTIFICATION_PREFIX.length).split('-')[1];
  const { serverUrl } = await loadSettings();
  await chrome.tabs.create({ url: `${serverUrl}${APPLICATIONS_PATH}/${applicationId}` });
  chrome.notifications.clear(notificationId);
}
