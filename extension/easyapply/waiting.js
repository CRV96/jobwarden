// Part of the Easy Apply run (easyapply.js, which says how): waiting for LinkedIn to show something, and stopping
// wherever the run waits once the user stopped it. Injected before easyapply.js, like the other parts, each
// registering what it gives on globalThis.jobWarden: a classic script, as injected scripts are, so injecting it
// again into the same page only registers it anew (the run going on keeps the parts it started with).
globalThis.jobWarden ??= {};
globalThis.jobWarden.easyApply = {};
globalThis.jobWarden.easyApply.waiting = (() => {
  /** How long to wait for LinkedIn to show something (a job, a form, the next step) before asking the user. */
  const TIMEOUT_MILLIS = 10000;

  /** Thrown wherever the run waits, once the user stopped it. */
  class Stopped extends Error {}

  // Waiting

  /** Set once the user stopped the run: on the bar, or in the popup. */
  let stopped = false;

  function markStopped() {
    stopped = true;
  }

  function isStopped() {
    return stopped;
  }

  function shown(element) {
    return Boolean(element) && element.getClientRects().length > 0 && getComputedStyle(element).visibility !== 'hidden';
  }

  function usable(button) {
    return shown(button) && !button.disabled && button.getAttribute('aria-disabled') !== 'true';
  }

  /**
   * @returns {Promise<any>} what find found, or null after the timeout
   * @throws {Stopped} once the user stopped the run, unless it can't stop here
   */
  async function waitFor(find, timeout = TIMEOUT_MILLIS, { stoppable = true } = {}) {
    const end = Date.now() + timeout;
    for (;;) {
      if (stoppable && stopped) {
        throw new Stopped();
      }
      const found = find();
      if (found) {
        return found;
      }
      if (Date.now() >= end) {
        return null;
      }
      await new Promise((resolve) => setTimeout(resolve, 200));
    }
  }

  /** Waits until LinkedIn is done changing the part of the page: no change for a moment, or at most a few seconds. */
  function settle(root, quietMillis = 400, maxMillis = 3000) {
    return new Promise((resolve) => {
      let quiet;
      const observer = new MutationObserver(() => {
        clearTimeout(quiet);
        quiet = setTimeout(done, quietMillis);
      });
      function done() {
        observer.disconnect();
        clearTimeout(quiet);
        clearTimeout(longest);
        resolve();
      }
      observer.observe(root, { childList: true, subtree: true, attributes: true, characterData: true });
      quiet = setTimeout(done, quietMillis);
      // done runs later, from a timer or the observer, once this is set
      const longest = setTimeout(done, maxMillis);
    });
  }

  return { TIMEOUT_MILLIS, Stopped, markStopped, isStopped, shown, usable, waitFor, settle };
})();
