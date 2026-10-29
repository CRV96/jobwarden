// The Easy Apply run: runs.js injects it into a LinkedIn job search once the user starts it from the popup, and again
// when LinkedIn loads the page anew. It goes down the search's results, one job at a time:
//   - a job JobWarden has as applied to or further along, or with no apply button (applied to on LinkedIn already, or
//     closed): skipped;
//   - an Easy Apply job: it opens the form, fills each step in from the profile's answers (the CV page's contact
//     details, the Auto-apply page's answers and own questions) and sends it; JobWarden then has it as applied, by the
//     extension;
//   - any other job: saved to JobWarden for the Auto-apply page, when the user asked for that; else skipped.
// It stops for the user at a question no answer covers (what they fill in is kept for the next forms), at an error
// LinkedIn shows, and at a security check, which it never touches. It never makes an answer up: a field it has no
// answer for stays as LinkedIn left it. A bar at the bottom of the page says where it's at, and has Stop.
// It works on both of LinkedIn's job searches: the older one (/jobs/search/) and the newer one (/jobs/search-results/),
// whose form is a modal <dialog>. It runs in the tab's isolated world: it sees the page's DOM, not its JavaScript. It reaches the server through
// runs.js in the background, as LinkedIn's page can't, and keeps its state in chrome.storage.local (RUN_KEY), so it
// goes on where it was after LinkedIn loads the page anew.
(async () => {
  // Injected again into a page that has it: the run is already going
  if (window.jobWardenEasyApply) {
    return;
  }
  window.jobWardenEasyApply = true;

  /** Where runs.js keeps the run: its options, what it did, and whether it's going. */
  const RUN_KEY = 'easyApplyRun';

  /** How long to wait for LinkedIn to show something (a job, a form, the next step) before asking the user. */
  const TIMEOUT_MILLIS = 10000;

  /** How long sending can take before the run asks the user whether it went. */
  const SENDING_TIMEOUT_MILLIS = 15000;

  /** More rounds than this on one form, and it isn't the form it looks like: the user takes over. */
  const MAX_ROUNDS = 30;

  /** How many times a step's fields are filled in: an answer can make LinkedIn show another question. */
  const FILL_PASSES = 3;

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

  /** Field types that aren't questions: files go with the CV LinkedIn has, buttons are LinkedIn's. */
  const IGNORED_TYPES = new Set(['hidden', 'file', 'submit', 'button', 'reset', 'image', 'search']);

  /** Yes and no as LinkedIn's options say them, in the extension's languages. */
  const YES = /^(yes|da|ja|oui|sí|si|sim|tak)\b/i;
  const NO = /^(no|nu|nein|non|nie|não|nao)\b/i;

  /** The option that declines a voluntary question. */
  const DECLINE_OPTION = /decline|prefer not|rather not|don.?t wish|do not wish|choose not|not (to )?(say|answer|disclose)|nu doresc|nu vreau|keine angabe|möchte (ich )?nicht|ne (souhaite|pas)|prefiero no|nie chcę|wolę nie|prefiro não/i;

  /** The button that sends the application, in the extension's languages, when LinkedIn's attribute isn't there. */
  const SUBMIT = /submit|trimite|envoyer|soumettre|absenden|enviar|wyślij|złóż/i;

  /**
   * How to close what LinkedIn shows once it sent an application ("Your application was sent", and an offer to do
   * more with the CV): Not now, or Done, never what it offers.
   */
  const NOT_NOW = /^(not now|done|no,? thanks|nu acum|nu,? mulțumesc|gata|jetzt nicht|nicht jetzt|fertig|pas maintenant|terminé|ahora no|listo|agora não|concluído|nie teraz|gotowe)$/i;

  /** A select's first option when nothing is chosen yet. */
  const PLACEHOLDER = /select an option|selectați|alege|wählen|sélectionn|selecciona|wybierz|selecione/i;

  /** A label's mark of a required question. */
  const REQUIRED_MARK = /\*\s*$|\(required\)\s*$/i;

  /**
   * A question that asks for a number: the newer page's number fields are text fields like any other, so the question
   * says it. "Notice period (in days)" then takes "30", not "1 month".
   */
  const NUMBER_QUESTION = /how many|number of|\(?in (days|weeks|months|years)\)?|câț[ia]|câte|număr(ul)? de|în (zile|săptămâni|luni|ani)|wie viele|anzahl|in (tagen|wochen|monaten|jahren)|combien|nombre d|en (jours|semaines|mois|années)|cuánt[oa]s|número de|en (días|semanas|meses|años)|\bile\b|liczba|w (dniach|tygodniach|miesiącach|latach)|quant[oa]s|em (dias|semanas|meses|anos)/i;

  /** The voluntary questions the Auto-apply page's "Decline the voluntary questions" is about. */
  const VOLUNTARY = /gender|\bsex\b|race|ethnic|veteran|disabilit|sexual orientation|pronoun|transgender|lgbt/i;

  /** The answer to a voluntary question when the profile declines them: the option that says so. */
  const DECLINE = Symbol('decline');

  /**
   * The questions the profile's answers cover, by what they ask, in the extension's languages. The first rule whose
   * question matches decides, also when its answer is empty: then the user answers. Answers learned during runs come
   * before all of these.
   */
  const RULES = [
    { question: /country code|prefix|prefijo|indicatif|vorwahl|kierunkowy|indicativo/i, answer: () => null },
    { question: /e-?mail/i, answer: (answers) => answers.contact.email },
    { question: /phone|mobile|telefon|téléphone|teléfono|celular|komórk/i, answer: (answers) => answers.contact.phone },
    { question: /linkedin/i, answer: (answers) => answers.contact.linkedin },
    { question: /github/i, answer: (answers) => answers.contact.github },
    {
      question: /website|portfolio|site web|sitio web|página web|webseite|strona (www|internetowa)/i,
      answer: (answers) => answers.contact.website,
    },
    {
      question: /^(first name|given name|prenume|vorname|prénom|nombre|imię|primeiro nome)$/i,
      answer: (answers) => nameParts(answers.contact.fullName)?.first,
    },
    {
      question: /^(last name|surname|family name|nume( de familie)?|nachname|nom( de famille)?|apellidos?|nazwisko|sobrenome|apelido)$/i,
      answer: (answers) => nameParts(answers.contact.fullName)?.last,
    },
    {
      question: /^(full name|name|nume complet|vollständiger name|nom complet|nombre completo|imię i nazwisko|nome completo)$/i,
      answer: (answers) => answers.contact.fullName,
    },
    {
      question: /^(city|location|town|oraș|localitate|stadt|wohnort|ville|ciudad|ubicación|miasto|lokalizacja|cidade|localização)\b/i,
      answer: (answers) => answers.contact.location,
    },
    { question: /sponsor/i, answer: (answers) => answers.needsSponsorship },
    {
      question: /relocat|relocare|mutați|umzieh|umzug|déménag|reubic|przeprowadz|mudar-se|realoca/i,
      answer: (answers) => answers.willingToRelocate,
    },
    {
      question: /notice period|preaviz|kündigungsfrist|préavis|preaviso|okres wypowiedzenia|aviso prévio/i,
      answer: (answers) => answers.noticePeriod,
    },
    {
      question: /salary|compensation|remuneration|pay expectation|salari|gehalt|salaire|wynagrodzeni|salário|remunera/i,
      answer: (answers) => answers.salaryExpectation,
    },
    {
      question: /start date|earliest start|when can you start|available to start|data de (î|i)nceput|startdatum|eintrittsdatum|date de début|fecha de (inicio|incorporación)|data rozpoczęcia|data de início/i,
      answer: (answers) => answers.earliestStart,
    },
    {
      // Years of work in all, not with one skill: "...experience do you have with Java?" is the profile's to answer
      question: /^how many years of (total |overall )?(work |professional )?experience do you have\??$/i,
      answer: (answers) => answers.yearsOfExperience,
    },
    {
      question: /authori[sz]ed to work|work authori[sz]ation|work permit|right to work|eligible to work/i,
      kinds: ['text'],
      answer: (answers) => answers.workAuthorization,
    },
    {
      question: /work preference|preferred work (arrangement|setting|model)|remote or (on-?site|hybrid)/i,
      kinds: ['text'],
      answer: (answers) => answers.workPreference,
    },
    { question: VOLUNTARY, answer: (answers) => (answers.declineVoluntary ? DECLINE : null) },
  ];

  /** Thrown wherever the run waits, once the user stopped it. */
  class Stopped extends Error {}

  /** The buttons the bar shows when the run needs the user. */
  const CONTINUE = 'runContinue';
  const SKIP = 'runSkip';
  const SENT = 'runSent';
  const NOT_SENT = 'runNotSent';

  let run = (await chrome.storage.local.get(RUN_KEY))[RUN_KEY];
  if (run?.status !== 'running') {
    window.jobWardenEasyApply = false;
    return;
  }
  let stopped = false;
  // Stop on the bar, or Stop the run in the popup
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === 'local' && changes[RUN_KEY] && changes[RUN_KEY].newValue?.status !== 'running') {
      stopped = true;
    }
  });
  const bar = createBar();
  bar.state(message('runStarting'));
  bar.counts(run);

  /** The profile's answers (FormAnswers on the server), and its own questions by normalized question. */
  let answers;
  let learned;

  try {
    await readAnswers();
    await goThroughResults();
  } catch (error) {
    if (error instanceof Stopped) {
      bar.end(message('runStopped'), run);
    } else {
      console.error('The Easy Apply run failed', error);
      bar.end(message('runFailed', error.message), run);
      await saveRun({ status: 'stopped' });
    }
  }

  // The results

  async function goThroughResults() {
    await waitFor(() => resultIds().length > 0);
    for (;;) {
      if (run.applied >= run.limit) {
        return finish(message('runLimitReached', String(run.applied)));
      }
      if (/^\/(login|authwall|uas\/login)/.test(window.location.pathname)) {
        return finish(message('runSignedOut'));
      }
      const id = resultIds().find((candidate) => !run.seen.includes(candidate));
      if (id === undefined) {
        if (await nextPage()) {
          continue;
        }
        return finish(message('runDone'));
      }
      await handle(id);
      await saveRun({ seen: [...run.seen, id] });
    }
  }

  async function finish(text) {
    await saveRun({ status: 'done' });
    bar.end(text, run);
  }

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

  /** @returns {Promise<boolean>} false on the last page */
  async function nextPage() {
    const button = [...document.querySelectorAll(SELECTORS.nextPage)].find(usable)
      ?? pageAfter(document.querySelector(SELECTORS.currentPage));
    if (!button || !usable(button)) {
      return false;
    }
    const before = resultIds().join();
    button.click();
    return Boolean(await waitFor(() => resultIds().length > 0 && resultIds().join() !== before, TIMEOUT_MILLIS));
  }

  /** The newer page numbers its page buttons: pagination-indicator-0, -1... */
  function pageAfter(current) {
    const index = current?.dataset.testid?.match(/^pagination-indicator-(\d+)$/)?.[1];
    if (index !== undefined) {
      return document.querySelector(`[data-testid="pagination-indicator-${Number(index) + 1}"]`);
    }
    return current?.nextElementSibling?.querySelector('button') ?? null;
  }

  // A job

  async function handle(id) {
    const card = cardOf(id);
    const title = cardTitle(card) || message('runThisJob');
    bar.state(message('runOpening', title));
    card?.scrollIntoView({ block: 'center' });
    // LinkedIn fills a card in once it's scrolled to
    const target = card && await waitFor(() => ((card.getAttribute('role') === 'button') ? card
      : card.querySelector(SELECTORS.cardLink) ?? null), 5000);
    if (!target) {
      return skipped();
    }
    target.click();
    if (!(await waitFor(() => shownJobId() === id))) {
      return skipped();
    }
    await waitFor(() => detailsShow(id), 3000);
    await settle(document.querySelector(SELECTORS.details) ?? document.body);

    const link = `https://www.linkedin.com/jobs/view/${id}/`;
    const known = await server('easyApply:lookUp', { link });
    if (known && known.status !== 'SAVED') {
      return skipped();
    }
    const button = await waitFor(applyButton, 3000);
    if (!button) {
      // Applied to on LinkedIn already, or no longer taking applications
      return skipped();
    }
    if (!isEasyApply(button)) {
      if (!run.saveOthers || known) {
        return skipped();
      }
      await server('easyApply:record', { link, action: 'BOOKMARK' });
      return saveRun({ saved: run.saved + 1 });
    }
    bar.state(message('runFilling', title));
    await readAnswers();
    if (await apply(button)) {
      await server('easyApply:record', { link, action: 'EASY_APPLY' });
      return saveRun({ applied: run.applied + 1 });
    }
    return skipped();
  }

  /**
   * The profile's answers as JobWarden has them now: read before each form, and after the run stopped at a question,
   * so what the user answers on the Auto-apply page counts at once.
   */
  async function readAnswers() {
    answers = await server('easyApply:answers');
    learned = new Map(answers.custom.map(({ question, answer }) => [normalized(question), answer]));
  }

  function skipped() {
    return saveRun({ skipped: run.skipped + 1 });
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

  // The form

  /** @returns {Promise<boolean>} whether LinkedIn sent the application */
  async function apply(button) {
    button.click();
    while (!(await waitFor(easyApplyDialog))) {
      if (await askUser(message(securityCheck() ? 'runSecurityCheck' : 'runFormDidntOpen'), [CONTINUE, SKIP]) === SKIP) {
        return false;
      }
      if (!easyApplyDialog()) {
        applyButton()?.click();
      }
    }
    /** The step the run pressed Submit on, once it did */
    let submittedFrom = null;
    for (let round = 0; round < MAX_ROUNDS; round++) {
      const dialog = await waitFor(easyApplyDialog, 3000);
      if (!dialog) {
        return confirmSent(submittedFrom !== null);
      }
      await settle(dialog);
      // LinkedIn says it sent it in the form's own dialog: after Submit, a step without questions
      if (submittedFrom !== null && stepOf(dialog) !== submittedFrom && !hasFields(dialog)) {
        return sent();
      }
      if (securityCheck()) {
        if (await askUser(message('runSecurityCheck'), [CONTINUE, SKIP]) === SKIP) {
          return discard();
        }
        continue;
      }
      const missing = await fill(dialog);
      if (missing.length > 0) {
        if (!(await answerOnTheForm(missing, message('runNeedsAnswer', missing[0].text || message('runAQuestion'))))) {
          return discard();
        }
        continue;
      }
      const forward = forwardButton(dialog);
      if (!forward) {
        if (await askUser(message('runStuck'), [CONTINUE, SKIP]) === SKIP) {
          return discard();
        }
        continue;
      }
      const before = stepOf(dialog);
      const submitting = isSubmit(dialog, forward);
      if (submitting) {
        submittedFrom = before;
      }
      forward.click();
      const after = await waitFor(() => afterForward(before), SENDING_TIMEOUT_MILLIS);
      if (after === 'gone') {
        return confirmSent(submitting);
      }
      if (after === 'next' && submitting) {
        return sent();
      }
      if (after === 'errors') {
        const errors = formErrors(easyApplyDialog());
        if (!(await answerOnTheForm(errors.questions, message('runFormError', errors.text)))) {
          return discard();
        }
      } else if (after === null && await askUser(message('runStuck'), [CONTINUE, SKIP]) === SKIP) {
        return discard();
      }
      // 'next', or a security check, which the next round shows
    }
    await askUser(message('runStuck'), [SKIP]);
    return discard();
  }

  /** What changed after Next, Review or Submit; null while nothing has. */
  function afterForward(before) {
    const dialog = easyApplyDialog();
    if (!dialog) {
      return 'gone';
    }
    if (securityCheck()) {
      return 'check';
    }
    if (formErrors(dialog).text) {
      return 'errors';
    }
    return (stepOf(dialog) !== before) ? 'next' : null;
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

  /**
   * The form closed. After Submit, LinkedIn sent it; otherwise (the user closed it?) LinkedIn shows that it sent it,
   * and the job has no apply button any more, or the user says whether it went.
   */
  async function confirmSent(submitted) {
    // Not stopped halfway: an application LinkedIn sent is recorded
    const shown = submitted || await waitFor(() => !easyApplyDialog() && (postApplyDialog() || !applyButton()),
      SENDING_TIMEOUT_MILLIS, { stoppable: false });
    await closeSentPopups();
    if (shown) {
      return true;
    }
    return await askUser(message('runUnsure'), [SENT, NOT_SENT]) === SENT;
  }

  /** LinkedIn sent it: what it shows about that is closed, and the run goes on. */
  async function sent() {
    await closeSentPopups();
    return true;
  }

  /**
   * Closes what LinkedIn shows once it sent an application, one after the other: Not now or Done, else the close
   * button. Never another of its buttons, such as one to upload a CV.
   */
  async function closeSentPopups() {
    for (let tries = 0; tries < 3; tries++) {
      const popup = [...document.querySelectorAll('dialog[open], [role="dialog"]')]
        .find((dialog) => shown(dialog) && !hasFields(dialog));
      const close = popup && (textButtons(popup.querySelectorAll('button'))
        .find((button) => NOT_NOW.test(button.textContent.trim())) ?? dismissButton(popup));
      if (!close) {
        return;
      }
      close.click();
      await waitFor(() => !popup.isConnected || !shown(popup), 3000, { stoppable: false });
    }
  }

  /** Whether a dialog asks anything: a field the run would fill in, not a file to upload. */
  function hasFields(dialog) {
    return [...dialog.querySelectorAll('input, select, textarea')]
      .some((field) => !IGNORED_TYPES.has(field.type) && !field.disabled && shown(boxOf(field)));
  }

  /** Closes the form without sending it, as the user chose to skip the job. */
  async function discard() {
    const dialog = easyApplyDialog();
    if (dialog) {
      dismissButton(dialog)?.click();
      const discardButton = await waitFor(discardConfirmation, 3000);
      discardButton?.click();
      await waitFor(() => !easyApplyDialog(), 5000);
    }
    return false;
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

  /**
   * Fills the step's fields in that are empty and that an answer covers.
   *
   * @returns {Promise<object[]>} the required questions still empty: no answer covers them
   */
  async function fill(dialog) {
    let missing = [];
    for (let pass = 0; pass < FILL_PASSES; pass++) {
      let changed = false;
      missing = [];
      for (const question of questionsOf(dialog)) {
        if (isAnswered(question)) {
          continue;
        }
        const answer = answerFor(question);
        if (answer !== null && await put(question, answer)) {
          changed = true;
        } else if (question.required) {
          missing.push(question);
        }
      }
      if (!changed) {
        break;
      }
      await settle(dialog);
    }
    return missing;
  }

  /**
   * Stops for the user, who answers the questions on the form or on the Auto-apply page, or chooses to skip the job.
   * What they answer on the form is kept for the next forms, also when they go on with LinkedIn's own Next; what they
   * answer on the page, the form gets once the run reads the answers again.
   *
   * @returns {Promise<boolean>} false if they skip the job
   */
  async function answerOnTheForm(questions, text) {
    const given = new Map();
    const listeners = questions.filter((question) => question.text).map((question) => {
      const listener = () => {
        const value = valueOf(question);
        if (value) {
          given.set(question.text, value);
        }
      };
      fieldsOf(question).forEach((field) => field.addEventListener('change', listener));
      return [question, listener];
    });
    const [first] = fieldsOf(questions[0] ?? { fields: [] });
    first?.scrollIntoView({ block: 'center' });
    first?.focus();
    const choice = await askUser(text, [CONTINUE, SKIP]);
    for (const [question, listener] of listeners) {
      listener();
      fieldsOf(question).forEach((field) => field.removeEventListener('change', listener));
    }
    if (choice === SKIP) {
      return false;
    }
    for (const [question, answer] of given) {
      await server('easyApply:learn', { question, answer });
    }
    await readAnswers();
    return true;
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

  /** What a field's aria-describedby names: on the newer page, its helper with its error. */
  function describedBy(field) {
    return (field.getAttribute('aria-describedby') ?? '').split(/\s+/).filter(Boolean)
      .map((id) => document.getElementById(id)).filter(Boolean);
  }

  // The questions

  /**
   * The step's questions: a text field, a select, a group of radio buttons or of checkboxes, each with its label.
   * A question is {kind, text, required, field} or, for a group, {kind, text, required, fields}.
   */
  function questionsOf(dialog) {
    const scope = dialog.querySelector('form') ?? dialog;
    const questions = [];
    const grouped = new Set();
    for (const field of scope.querySelectorAll('input, select, textarea')) {
      if (grouped.has(field) || IGNORED_TYPES.has(field.type) || field.disabled || !shown(boxOf(field))) {
        continue;
      }
      if (field.type === 'radio' || field.type === 'checkbox') {
        const fieldset = field.closest('fieldset');
        const fields = fieldset ? [...fieldset.querySelectorAll(`input[type="${field.type}"]`)]
          : (field.type === 'radio' && field.name)
            ? [...scope.querySelectorAll(`input[type="radio"][name="${CSS.escape(field.name)}"]`)] : [field];
        fields.forEach((member) => grouped.add(member));
        const legend = fieldset?.querySelector('legend');
        const rawText = legend ? rawTextOf(legend) : labelOf(field);
        questions.push({
          kind: field.type,
          text: cleanText(rawText),
          required: REQUIRED_MARK.test(rawText) || fields.some((member) => member.required)
            || fieldset?.getAttribute('aria-required') === 'true',
          fields,
          single: !fieldset && fields.length === 1,
        });
        continue;
      }
      const rawText = labelOf(field);
      questions.push({
        kind: kindOf(field, cleanText(rawText)),
        text: cleanText(rawText),
        required: field.required || field.getAttribute('aria-required') === 'true' || REQUIRED_MARK.test(rawText),
        field,
      });
    }
    return questions;
  }

  function kindOf(field, text) {
    if (field.tagName === 'SELECT') {
      return 'select';
    }
    if (field.getAttribute('role') === 'combobox' || field.getAttribute('aria-autocomplete') === 'list') {
      return 'typeahead';
    }
    if (field.type === 'number' || field.inputMode === 'numeric' || /numeric/i.test(field.id)
        || (field.tagName === 'INPUT' && NUMBER_QUESTION.test(text))) {
      return 'number';
    }
    return 'text';
  }

  function fieldsOf(question) {
    return question.fields ?? (question.field ? [question.field] : []);
  }

  /** What to check the visibility of: LinkedIn hides radio buttons and checkboxes behind their labels. */
  function boxOf(field) {
    return (field.type === 'radio' || field.type === 'checkbox')
      ? (field.closest('fieldset') ?? labelElementOf(field) ?? field) : field;
  }

  function isAnswered(question) {
    switch (question.kind) {
      case 'select': {
        const option = question.field.options[question.field.selectedIndex];
        return Boolean(option) && !isPlaceholder(option);
      }
      case 'radio':
        return question.fields.some((field) => field.checked);
      case 'checkbox':
        // A single box left unticked is an answer too, unless it must be ticked
        return question.fields.some((field) => field.checked) || (question.single && !question.required);
      default:
        return question.field.value.trim() !== '';
    }
  }

  /** @returns {string | number | boolean | symbol | null} null when nothing covers the question */
  function answerFor(question) {
    const known = learned.get(normalized(question.text));
    if (known !== undefined) {
      return known;
    }
    const rule = RULES.find((candidate) => candidate.question.test(question.text)
      && (!candidate.kinds || candidate.kinds.includes(question.kind)));
    const answer = rule?.answer(answers);
    return (answer === undefined || answer === '') ? null : answer;
  }

  /** @returns {Promise<boolean>} whether the answer fit the field */
  async function put(question, answer) {
    switch (question.kind) {
      case 'text': {
        const text = textOf(answer);
        return text !== null && type(question.field, text);
      }
      case 'number': {
        const number = numberOf(answer);
        return number !== null && type(question.field, number);
      }
      case 'typeahead':
        return pickSuggestion(question.field, textOf(answer));
      case 'select': {
        const option = [...question.field.options].find((candidate) => !isPlaceholder(candidate)
          && fits(candidate.text, answer));
        if (!option) {
          return false;
        }
        question.field.value = option.value;
        fire(question.field, 'input', 'change');
        return true;
      }
      case 'radio': {
        const choice = question.fields.find((field) => fits(labelOf(field), answer));
        choice?.click();
        return Boolean(choice);
      }
      case 'checkbox': {
        const wanted = question.single ? [] : String(answer).split(/\s*,\s*/).map(normalized);
        const boxes = question.single ? (YES.test(String(answer)) || answer === true ? question.fields : [])
          : question.fields.filter((field) => wanted.includes(normalized(cleanText(labelOf(field)))));
        boxes.filter((field) => !field.checked).forEach((field) => field.click());
        return boxes.length > 0;
      }
      default:
        return false;
    }
  }

  /** The answer the user gave on the form, to learn: for a choice, its option's text. */
  function valueOf(question) {
    switch (question.kind) {
      case 'select': {
        const option = question.field.options[question.field.selectedIndex];
        return (option && !isPlaceholder(option)) ? cleanText(option.text) : null;
      }
      case 'radio':
      case 'checkbox': {
        const checked = question.fields.filter((field) => field.checked);
        if (question.single) {
          return checked.length > 0 ? 'Yes' : null;
        }
        return checked.map((field) => cleanText(labelOf(field))).join(', ') || null;
      }
      default:
        return question.field.value.trim() || null;
    }
  }

  /** Whether an option says the answer: yes or no for a yes-or-no answer, the same words for any other. */
  function fits(optionText, answer) {
    const option = cleanText(optionText);
    if (answer === DECLINE) {
      return DECLINE_OPTION.test(option);
    }
    if (typeof answer === 'boolean') {
      return (answer ? YES : NO).test(option);
    }
    return normalized(option) === normalized(String(answer));
  }

  /** Text for a text field: a yes or no, or declining, isn't one. */
  function textOf(answer) {
    if (typeof answer === 'number') {
      return String(answer);
    }
    return (typeof answer === 'string') ? answer : null;
  }

  /** A number field takes an answer that's only a number: "5,500 EUR a month" isn't, and the user answers. */
  function numberOf(answer) {
    if (typeof answer === 'number') {
      return String(answer);
    }
    return (typeof answer === 'string' && /^\s*\d+([.,]\d+)?\s*$/.test(answer)) ? answer.trim().replace(',', '.')
      : null;
  }

  function isPlaceholder(option) {
    return option.value === '' || option.disabled || PLACEHOLDER.test(option.text);
  }

  /** Types into a field the way LinkedIn's own scripts notice: they listen for input and change. */
  function type(field, value) {
    field.focus();
    field.value = value;
    fire(field, 'input', 'change');
    field.blur();
    return true;
  }

  /** A field that suggests as it's typed in, such as the city: the first suggestion, or nothing. */
  async function pickSuggestion(field, text) {
    if (!text) {
      return false;
    }
    field.focus();
    field.value = text;
    fire(field, 'input');
    const listId = field.getAttribute('aria-controls') ?? field.getAttribute('aria-owns');
    const option = await waitFor(() => [...(listId ? document.getElementById(listId) ?? document : document)
      .querySelectorAll('[role="listbox"] [role="option"], [role="option"]')].find(shown) ?? null, 4000);
    if (!option) {
      field.value = '';
      fire(field, 'input', 'change');
      return false;
    }
    option.click();
    await settle(field.closest('form') ?? document.body);
    return field.value.trim() !== '';
  }

  function fire(field, ...types) {
    for (const eventType of types) {
      field.dispatchEvent(new Event(eventType, { bubbles: true }));
    }
  }

  // Labels

  function labelElementOf(field) {
    return (field.id && document.querySelector(`label[for="${CSS.escape(field.id)}"]`)) || field.closest('label');
  }

  /** A field's label as it's written, the required mark included. */
  function labelOf(field) {
    const label = labelElementOf(field);
    if (label) {
      return rawTextOf(label);
    }
    const labelledBy = field.getAttribute('aria-labelledby');
    if (labelledBy) {
      return labelledBy.split(/\s+/).map((id) => document.getElementById(id)).filter(Boolean).map(rawTextOf).join(' ');
    }
    return field.getAttribute('aria-label') ?? field.placeholder ?? '';
  }

  /** LinkedIn writes a label twice, once for the eye (aria-hidden) and once for screen readers: the first. */
  function rawTextOf(element) {
    const shownText = element.querySelector('[aria-hidden="true"]')?.textContent.trim();
    return (shownText || element.textContent).replace(/\s+/g, ' ').trim();
  }

  /** The text without its required mark, and said once where it's written twice. */
  function cleanText(elementOrText) {
    if (!elementOrText) {
      return '';
    }
    let text = (typeof elementOrText === 'string') ? elementOrText.replace(/\s+/g, ' ').trim()
      : rawTextOf(elementOrText);
    text = text.replace(REQUIRED_MARK, '').trim();
    const half = text.slice(0, Math.floor(text.length / 2)).trim();
    return (half && text === `${half} ${half}`) ? half : text;
  }

  /** Questions match whatever their case and spacing, like the server's. */
  function normalized(text) {
    return String(text).trim().replace(/\s+/g, ' ').toLowerCase();
  }

  function nameParts(fullName) {
    const [first, ...rest] = (fullName ?? '').trim().split(/\s+/);
    return first ? { first, last: rest.join(' ') || null } : null;
  }

  // Waiting

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
      let longest;
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
      longest = setTimeout(done, maxMillis);
    });
  }

  // The server, through runs.js

  /**
   * Asks runs.js in the background. When the server can't be reached, the user says when to try again.
   *
   * @throws {Stopped} if they stop the run instead
   */
  async function server(type, data = {}) {
    for (;;) {
      try {
        return await new Promise((resolve, reject) => {
          chrome.runtime.sendMessage({ type, ...data }, (response) => {
            if (chrome.runtime.lastError) {
              reject(new Error(chrome.runtime.lastError.message));
            } else if (response?.error) {
              reject(new Error(response.error));
            } else {
              resolve(response?.result ?? null);
            }
          });
        });
      } catch (error) {
        await askUser(message('runServerError', error.message), [CONTINUE]);
      }
    }
  }

  async function saveRun(changes) {
    run = { ...run, ...changes };
    await chrome.storage.local.set({ [RUN_KEY]: run });
    bar.counts(run);
  }

  // The bar

  /**
   * Waits for the user's choice on the bar.
   *
   * @throws {Stopped} if they stop the run instead
   */
  async function askUser(text, choices) {
    const choice = await bar.ask(text, choices);
    if (choice === null || stopped) {
      throw new Stopped();
    }
    return choice;
  }

  /** The bar at the bottom of the page, in a shadow root so LinkedIn's styles leave it alone. */
  function createBar() {
    document.getElementById('jobwarden-easy-apply')?.remove();
    const host = document.createElement('div');
    host.id = 'jobwarden-easy-apply';
    const root = host.attachShadow({ mode: 'open' });
    const style = document.createElement('style');
    style.textContent = `
      section { position: fixed; left: 16px; bottom: 16px; z-index: 2147483647; width: 340px; box-sizing: border-box;
        padding: 12px 14px; border: 1px solid #d1d5db; border-radius: 8px; background: #fff; color: #111827;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18); font: 14px/1.4 system-ui, sans-serif; }
      header { display: flex; align-items: center; justify-content: space-between; gap: 8px; font-weight: 600; }
      p { margin: 6px 0 0; }
      .counts { color: #6b7280; font-size: 12px; }
      .question { padding: 8px 10px; border: 1px solid #fcd34d; border-radius: 6px; background: #fffbeb; }
      .choices { display: flex; gap: 8px; margin-top: 10px; }
      button { flex: 1; padding: 6px 8px; border: 1px solid #d1d5db; border-radius: 4px; background: #fff;
        color: #111827; font: inherit; cursor: pointer; }
      button.primary { border-color: #111827; background: #111827; color: #fff; }
      header button { flex: none; padding: 2px 10px; border-color: #fca5a5; color: #b91c1c; font-weight: 400; }
      [hidden] { display: none !important; }`;
    const section = element('section', { role: 'status', 'aria-live': 'polite' });
    const header = element('header');
    const title = element('span');
    title.textContent = message('runBarTitle');
    const stop = element('button', { type: 'button' });
    stop.textContent = message('runStop');
    header.append(title, stop);
    const state = element('p');
    const question = element('p', { class: 'question', hidden: '' });
    const counts = element('p', { class: 'counts' });
    const choices = element('div', { class: 'choices', hidden: '' });
    section.append(header, state, question, counts, choices);
    root.append(style, section);
    document.body.append(host);
    // A modal <dialog>, like the newer page's form, leaves the rest of the page unclickable: the bar goes into it
    // while it's open, and back when LinkedIn takes it away
    const placing = setInterval(() => {
      const parent = document.querySelector('dialog:modal') ?? document.body;
      if (host.parentElement !== parent) {
        parent.append(host);
      }
    }, 300);

    /** Resolves the question the bar shows: with the choice, or null on Stop. */
    let answer = null;
    stop.addEventListener('click', async () => {
      stopped = true;
      answer?.(null);
      await saveRun({ status: 'stopped' });
    });

    return {
      state(text) {
        state.textContent = text;
      },
      counts({ applied, limit, saved, skipped }) {
        counts.textContent = message('runCounts', String(applied), String(limit), String(saved), String(skipped));
      },
      ask(text, options) {
        question.textContent = text;
        question.hidden = false;
        choices.replaceChildren(...options.map((option, index) => {
          const button = element('button', { type: 'button', class: (index === 0) ? 'primary' : '' });
          button.textContent = message(option);
          button.addEventListener('click', () => answer?.(option));
          return button;
        }));
        choices.hidden = false;
        return new Promise((resolve) => {
          answer = (choice) => {
            answer = null;
            question.hidden = true;
            choices.hidden = true;
            resolve(choice);
          };
        });
      },
      end(text, finalRun) {
        state.textContent = text;
        question.hidden = true;
        this.counts(finalRun);
        const close = element('button', { type: 'button' });
        close.textContent = message('runClose');
        close.addEventListener('click', () => {
          clearInterval(placing);
          host.remove();
        });
        choices.replaceChildren(close);
        choices.hidden = false;
        stop.hidden = true;
        window.jobWardenEasyApply = false;
      },
    };
  }

  function element(tag, attributes = {}) {
    const created = document.createElement(tag);
    for (const [name, value] of Object.entries(attributes)) {
      if (value !== '' || name === 'hidden') {
        created.setAttribute(name, value);
      }
    }
    return created;
  }

  function message(key, ...substitutions) {
    return chrome.i18n.getMessage(key, substitutions) || key;
  }
})();

// What the script ends with is what injecting it gives back, and Chrome waits for a promise: not the run's, which would
// keep runs.js from answering until the run is over
undefined;
