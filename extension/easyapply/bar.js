// Part of the Easy Apply run (easyapply.js): the bar at the bottom of LinkedIn's page that says where the run is
// at, asks the user when it needs them, and has Stop; and the extension's messages, in the browser's language.
globalThis.jobWarden.easyApply.bar = (() => {
  const { markStopped } = globalThis.jobWarden.easyApply.waiting;

  /**
   * The bar at the bottom of the page, in a shadow root so LinkedIn's styles leave it alone. Its Stop stops the run
   * where it waits, then calls onStop.
   */
  function createBar(onStop) {
    document.getElementById('jobwarden-easy-apply')?.remove();
    const host = document.createElement('div');
    host.id = 'jobwarden-easy-apply';
    const root = host.attachShadow({ mode: 'open' });
    const style = document.createElement('style');
    // JobWarden's own look (ui.css, from the app's tokens), light or dark as the system is
    style.textContent = `
      :host { --ink: #1d1d1f; --surface: #ffffff; --accent: #007aff; --orange: #c56a00; --red: #e0302a;
        --control: #ffffff; --shadow: 0 0 0 0.5px rgb(0 0 0 / 0.12), 0 20px 50px rgb(0 0 0 / 0.18); }
      @media (prefers-color-scheme: dark) {
        :host { --ink: #f5f5f7; --surface: #2a2a2c; --accent: #0a84ff; --orange: #ff9f0a; --red: #ff453a;
          --control: #48484a; --shadow: 0 0 0 0.5px rgb(255 255 255 / 0.16), 0 20px 50px rgb(0 0 0 / 0.55); }
      }
      section { position: fixed; left: 16px; bottom: 16px; z-index: 2147483647; width: 340px; box-sizing: border-box;
        padding: 12px 14px; border-radius: 12px; background: var(--surface); color: var(--ink); box-shadow: var(--shadow);
        font: 13px/1.5 -apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", "Segoe UI", Roboto, sans-serif;
        letter-spacing: -0.005em; -webkit-font-smoothing: antialiased; }
      header { display: flex; align-items: center; justify-content: space-between; gap: 8px; font-weight: 600; }
      header span { display: flex; align-items: center; gap: 8px; }
      header span::before { content: ""; width: 10px; height: 10px; border-radius: 50%; background: var(--accent);
        box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 25%, transparent); }
      p { margin: 6px 0 0; }
      .counts { color: color-mix(in srgb, var(--ink) 60%, transparent); font-size: 12px; }
      .question { padding: 8px 10px; border-radius: 8px; background: color-mix(in srgb, var(--orange) 12%, transparent); }
      .choices { display: flex; gap: 8px; margin-top: 10px; }
      button { flex: 1; padding: 6px 12px; border: 0; border-radius: 6px; background: var(--control); color: var(--ink);
        box-shadow: 0 0 0 0.5px rgb(0 0 0 / 0.18), 0 0.5px 1.5px rgb(0 0 0 / 0.12); font: inherit; font-weight: 500;
        cursor: pointer; }
      button:hover { background: color-mix(in srgb, var(--control) 92%, var(--ink)); }
      button.primary { background: var(--accent); color: #fff; box-shadow: 0 0.5px 1px rgb(0 0 0 / 0.15); }
      button.primary:hover { background: color-mix(in srgb, var(--accent) 88%, #fff); }
      button:focus-visible { outline: 3px solid color-mix(in srgb, var(--accent) 45%, transparent); outline-offset: 1px; }
      header button { flex: none; padding: 3px 10px; background: transparent; box-shadow: none; color: var(--red); }
      header button:hover { background: color-mix(in srgb, var(--red) 10%, transparent); }
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
      markStopped();
      answer?.(null);
      await onStop();
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

  return { createBar, message };
})();
