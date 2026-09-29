// The extension's texts live in _locales/<language>/messages.json. Chrome picks the browser's language,
// falling back to English (default_locale in manifest.json).

/**
 * @param {string} key a message name from messages.json
 * @param {...string} substitutions for its $1, $2...
 * @returns {string} the key itself if there's no such message, so a missing one shows up instead of blank text
 */
export function message(key, ...substitutions) {
  return chrome.i18n.getMessage(key, substitutions) || key;
}

/**
 * Replaces the text of every element with a data-i18n attribute by that message, and the placeholder of every
 * field with a data-i18n-placeholder attribute.
 */
export function translatePage() {
  document.documentElement.lang = chrome.i18n.getUILanguage();
  for (const element of document.querySelectorAll('[data-i18n]')) {
    element.textContent = message(element.dataset.i18n);
  }
  for (const element of document.querySelectorAll('[data-i18n-placeholder]')) {
    element.placeholder = message(element.dataset.i18nPlaceholder);
  }
}
