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
// Its parts, in easyapply/, are injected before it (EASY_APPLY_SCRIPTS, injected.js): waiting for LinkedIn and
// stopping (waiting.js), the form's questions and the answers that fit them (questions.js), where things are on
// LinkedIn's pages (linkedin.js), and the bar (bar.js). What's left here is the run itself: the results, a job, its form.
(async () => {
  // Injected again into a page that has it: the run is already going
  if (window.jobWardenEasyApply) {
    return;
  }
  window.jobWardenEasyApply = true;

  const {
    Stopped, TIMEOUT_MILLIS, isStopped, markStopped, settle, shown, usable, waitFor,
  } = globalThis.jobWarden.easyApply.waiting;
  const {
    RULES, fieldsOf, isAnswered, normalized, put, questionsOf, valueOf,
  } = globalThis.jobWarden.easyApply.questions;
  const {
    NOT_NOW, SELECTORS, applyButton, cardOf, cardTitle, detailsShow, discardConfirmation, dismissButton,
    easyApplyDialog, formErrors, forwardButton, hasFields, isEasyApply, isSubmit, pageAfter, postApplyDialog, resultIds,
    securityCheck, shownJobId, stepOf, textButtons,
  } = globalThis.jobWarden.easyApply.linkedin;
  const { createBar, message } = globalThis.jobWarden.easyApply.bar;

  /** Where runs.js keeps the run: its options, what it did, and whether it's going. */
  const RUN_KEY = 'easyApplyRun';

  /** How long sending can take before the run asks the user whether it went. */
  const SENDING_TIMEOUT_MILLIS = 15000;

  /** More rounds than this on one form, and it isn't the form it looks like: the user takes over. */
  const MAX_ROUNDS = 30;

  /** How many times a step's fields are filled in: an answer can make LinkedIn show another question. */
  const FILL_PASSES = 3;

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
  // Stop on the bar, or Stop the run in the popup
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === 'local' && changes[RUN_KEY] && changes[RUN_KEY].newValue?.status !== 'running') {
      markStopped();
    }
  });
  const bar = createBar(() => saveRun({ status: 'stopped' }));
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
    if (choice === null || isStopped()) {
      throw new Stopped();
    }
    return choice;
  }
})();

// What the script ends with is what injecting it gives back, and Chrome waits for a promise: not the run's, which would
// keep runs.js from answering until the run is over
undefined;
