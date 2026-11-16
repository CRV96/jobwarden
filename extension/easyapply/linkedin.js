// Part of the Easy Apply run (easyapply.js): where things are on LinkedIn's two job searches, the older one
// (/jobs/search/) and the newer one (/jobs/search-results/): the results, the job shown, its apply button, the
// form's dialog, its buttons and steps, and the errors and security checks LinkedIn shows.
globalThis.jobWarden.easyApply.linkedin = (() => {
  const { shown, usable } = globalThis.jobWarden.easyApply.waiting;
  const {
    IGNORED_TYPES, boxOf, cleanText, describedBy, fieldsOf, questionsOf,
  } = globalThis.jobWarden.easyApply.questions;

  /**
   * Where things are on LinkedIn's two job searches. The older one has classes such as jobs-apply-button; the newer
   * one's classes are made up anew with each of its releases, so only its attributes (componentkey, data-testid,
   * data-sdui-screen), aria-labels and icon ids are used there. LinkedIn changes its pages now and then: each place
   * has a few, and the run stops for the user rather than guess when none is there.
   */
  const SELECTORS = {
    results: '.scaffold-layout__list, .jobs-search-results-list, .jobs-search__results-list, '
      + '[componentkey="SearchResultsMainContent"]',
    card: '[data-occludable-job-id], [data-job-id], [componentkey^="job-card-component-ref-"]',
    cardLink: '.job-card-container--clickable, a.job-card-list__title--link, a.job-card-container__link, '
      + 'a[href*="/jobs/view/"]',
    cardTitle: '.job-card-list__title--link, .job-card-list__title, .artdeco-entity-lockup__title, '
      + 'a[href*="/jobs/view/"]',
    details: '.jobs-search__job-details--wrapper, .jobs-search__job-details, .jobs-details, .job-view-layout, '
      + '[data-sdui-screen*="JobDetails"]',
    applyButton: '.jobs-apply-button--top-card button, button.jobs-apply-button, a.jobs-apply-button, '
      + 'button[data-live-test-job-apply-button], button[aria-label]:has(svg[id^="inbug"]), '
      + 'a[target="_blank"][aria-label]:has(svg[id*="arrow-diagonal"])',
    easyApplyIcon: '[data-test-icon^="linkedin-bug"], use[href*="linkedin-bug"], svg[id^="inbug"]',
    externalIcon: '[data-test-icon="link-external-small"], use[href*="link-external"], svg[id*="arrow-diagonal"]',
    easyApplyDialog: '.jobs-easy-apply-modal, [data-test-modal-id="easy-apply-modal"]',
    forward: 'button[data-live-test-easy-apply-submit-button], button[data-live-test-easy-apply-review-button], '
      + 'button[data-live-test-easy-apply-next-button], button[data-easy-apply-next-button]',
    primaryButton: 'button.artdeco-button--primary',
    dismiss: '.artdeco-modal__dismiss, button[data-test-modal-close-btn], button[aria-label="Dismiss"]',
    discard: 'button[data-control-name="discard_application_confirm_btn"], button[data-test-dialog-secondary-btn]',
    // On the newer page, a field's helper (its aria-describedby) has the error in front of its character count
    fieldError: '.artdeco-inline-feedback--error, [data-test-form-element-error-messages], '
      + '.fb-dash-form-element-error, [data-testid$="helper-text"] > p',
    fieldGroup: 'fieldset, .jobs-easy-apply-form-element, .fb-dash-form-element, [data-test-form-element], '
      + '.jobs-easy-apply-form-section__grouping',
    nextPage: 'button.jobs-search-pagination__button--next, button[aria-label="View next page"]',
    currentPage: '.artdeco-pagination__indicator--number.active, .artdeco-pagination__indicator--number.selected, '
      + '[data-testid^="pagination-indicator-"][aria-current="true"]',
    securityCheck: '#captcha-internal, iframe[src*="captcha" i], iframe[title*="captcha" i], '
      + 'iframe[src*="arkoselabs"], iframe[src*="challenge" i]',
  };

  /** The button that sends the application, in the extension's languages, when LinkedIn's attribute isn't there. */
  const SUBMIT = /submit|trimite|envoyer|soumettre|absenden|enviar|wyślij|złóż/i;

  /**
   * How to close what LinkedIn shows once it sent an application ("Your application was sent", and an offer to do
   * more with the CV): Not now, or Done, never what it offers.
   */
  const NOT_NOW = /^(not now|done|no,? thanks|nu acum|nu,? mulțumesc|gata|jetzt nicht|nicht jetzt|fertig|pas maintenant|terminé|ahora no|listo|agora não|concluído|nie teraz|gotowe)$/i;

  /** The jobs of the results' page, in the order it shows them. */
  function resultIds() {
    const list = document.querySelector(SELECTORS.results) ?? document;
    const ids = [...list.querySelectorAll(SELECTORS.card)]
      .filter((card) => !card.closest(SELECTORS.details))
      .map((card) => card.dataset.occludableJobId ?? card.dataset.jobId
        ?? card.getAttribute('componentkey')?.replace('job-card-component-ref-', ''));
    return [...new Set(ids.filter((id) => /^\d+$/.test(id ?? '')))];
  }

  /** On the older page, the card's list item; on the newer one, the card, which is a button itself. */
  function cardOf(id) {
    return document.querySelector(`[data-occludable-job-id="${id}"], [data-job-id="${id}"], `
      + `[componentkey="job-card-component-ref-${id}"][role="button"]`)
      ?? document.querySelector(`[componentkey="job-card-component-ref-${id}"]`);
  }

  /** The newer page's card tells screen readers "Selected, <title>" before the title it shows. */
  function cardTitle(card) {
    const titled = card?.querySelector(SELECTORS.cardTitle);
    if (titled) {
      return cleanText(titled);
    }
    const [first = '', second = ''] = [...(card?.querySelectorAll('p, span, strong') ?? [])]
      .filter((element) => element.childElementCount === 0)
      .map((element) => cleanText(element.textContent))
      .filter(Boolean);
    return (second && first.endsWith(second)) ? second : first;
  }

  /** The newer page numbers its page buttons: pagination-indicator-0, -1... */
  function pageAfter(current) {
    const index = current?.dataset.testid?.match(/^pagination-indicator-(\d+)$/)?.[1];
    if (index !== undefined) {
      return document.querySelector(`[data-testid="pagination-indicator-${Number(index) + 1}"]`);
    }
    return current?.nextElementSibling?.querySelector('button') ?? null;
  }

  /** The job the page shows beside the results, by its address. */
  function shownJobId() {
    const url = new URL(window.location.href);
    return url.searchParams.get('currentJobId') ?? url.pathname.match(/\/jobs\/view\/(?:[^/]*-)?(\d+)/)?.[1] ?? null;
  }

  /** Whether the job's details are the job's: the address changes before them. */
  function detailsShow(id) {
    return Boolean(document.querySelector(SELECTORS.details)
      ?.querySelector(`a[href*="/jobs/view/${id}"], [data-job-id="${id}"]`));
  }

  function applyButton() {
    const details = document.querySelector(SELECTORS.details) ?? document;
    return [...details.querySelectorAll(SELECTORS.applyButton)].find(usable) ?? null;
  }

  /** Easy Apply, or the company's own site: LinkedIn's icon on the button says which, whatever its language. */
  function isEasyApply(button) {
    if (button.querySelector(SELECTORS.externalIcon) || button.getAttribute('role') === 'link'
        || (button.tagName === 'A' && button.target === '_blank')) {
      return false;
    }
    return Boolean(button.querySelector(SELECTORS.easyApplyIcon))
      || /easy apply/i.test(`${button.getAttribute('aria-label') ?? ''} ${button.textContent}`);
  }

  /**
   * Whether the button sends the application: LinkedIn's attribute for it, the form's last step (its progress at
   * 100%), or the button saying so.
   */
  function isSubmit(dialog, button) {
    return button.matches('[data-live-test-easy-apply-submit-button]') || progressOf(dialog) >= 100
      || SUBMIT.test(`${button.getAttribute('aria-label') ?? ''} ${button.textContent}`);
  }

  /** @returns {number} how far through the form, in percent; 0 when it doesn't say */
  function progressOf(dialog) {
    const progress = dialog.querySelector('progress');
    if (progress) {
      return (100 * progress.value) / (progress.max || 100);
    }
    const bar = dialog.querySelector('[role="progressbar"]');
    const max = Number(bar?.getAttribute('aria-valuemax') ?? 100) || 100;
    return (100 * Number(bar?.getAttribute('aria-valuenow') ?? 0)) / max;
  }

  /** Whether a dialog asks anything: a field the run would fill in, not a file to upload. */
  function hasFields(dialog) {
    return [...dialog.querySelectorAll('input, select, textarea')]
      .some((field) => !IGNORED_TYPES.has(field.type) && !field.disabled && shown(boxOf(field)));
  }

  /** The form: on the older page a modal with known classes, on the newer one the <dialog> with a progress bar. */
  function easyApplyDialog() {
    const known = [...document.querySelectorAll(SELECTORS.easyApplyDialog)].find(shown);
    return known ?? [...document.querySelectorAll('dialog[open], [role="dialog"]')]
      .find((dialog) => shown(dialog) && dialog.querySelector('progress, [role="progressbar"]')) ?? null;
  }

  /** LinkedIn's "Application sent", or what it shows once a form is gone. */
  function postApplyDialog() {
    return [...document.querySelectorAll('dialog[open], [role="dialog"]')]
      .find((dialog) => shown(dialog) && !dialog.querySelector('form, progress, [role="progressbar"]')) ?? null;
  }

  /** Next, Review or Submit: the form's one primary button; on the newer page, the last one in its footer. */
  function forwardButton(dialog) {
    return [...dialog.querySelectorAll(SELECTORS.forward)].find(usable)
      ?? [...dialog.querySelectorAll(SELECTORS.primaryButton)].filter(usable).at(-1)
      ?? textButtons(dialog.querySelectorAll('footer button')).at(-1) ?? null;
  }

  /** A dialog's close button: on the newer page, the one with an icon and no text. */
  function dismissButton(dialog) {
    return dialog?.querySelector(SELECTORS.dismiss)
      ?? [...(dialog?.querySelectorAll('button') ?? [])].find((button) => usable(button) && !button.textContent.trim())
      ?? null;
  }

  /** "Save this application?", which closing a form asks: its Discard button, which comes before Save. */
  function discardConfirmation() {
    const known = document.querySelector(SELECTORS.discard);
    if (known) {
      return known;
    }
    const confirmation = [...document.querySelectorAll('dialog[open], [role="dialog"], [role="alertdialog"]')]
      .find((dialog) => shown(dialog) && !dialog.querySelector('progress, [role="progressbar"]')
        && textButtons(dialog.querySelectorAll('button')).length >= 2);
    return confirmation ? textButtons(confirmation.querySelectorAll('button'))[0] : null;
  }

  function textButtons(buttons) {
    return [...buttons].filter((button) => usable(button) && button.textContent.trim());
  }

  /**
   * Changes from one step to the next: the headings, the progress, the fields, the buttons. LinkedIn's "Your application
   * was sent" can come in the form's own dialog, under the same title, at 100% and without fields, like the review
   * step before it: its heading and buttons tell them apart.
   */
  function stepOf(dialog) {
    const headings = [...dialog.querySelectorAll('h1, h2, h3, h4')].map((heading) => cleanText(heading));
    const fields = [...dialog.querySelectorAll('input, select, textarea')].map((field) => field.id || field.name);
    const buttons = textButtons(dialog.querySelectorAll('button')).map((button) => cleanText(button));
    return [headings.join('/'), progressOf(dialog), fields.join(), buttons.join('/')].join('|');
  }

  function securityCheck() {
    return window.location.pathname.startsWith('/checkpoint/')
      || [...document.querySelectorAll(SELECTORS.securityCheck)].some(shown);
  }

  /** The errors LinkedIn shows under fields, and the questions they're about. */
  function formErrors(dialog) {
    const errors = dialog ? [...dialog.querySelectorAll(SELECTORS.fieldError)]
      .filter((error) => shown(error) && error.textContent.trim()) : [];
    const groups = errors.map((error) => error.closest(SELECTORS.fieldGroup) ?? error.parentElement);
    const questions = dialog ? questionsOf(dialog).filter((question) => fieldsOf(question).some((field) =>
      field.getAttribute('aria-invalid') === 'true' || groups.some((group) => group.contains(field))
      || describedBy(field).some((element) => errors.some((error) => element.contains(error))))) : [];
    return { text: [...new Set(errors.map((error) => cleanText(error)))].join(' '), questions };
  }

  return {
    SELECTORS, NOT_NOW, resultIds, cardOf, cardTitle, pageAfter, shownJobId, detailsShow, applyButton, isEasyApply,
    isSubmit, hasFields, easyApplyDialog, postApplyDialog, forwardButton, dismissButton, discardConfirmation,
    textButtons, stepOf, securityCheck, formErrors,
  };
})();
