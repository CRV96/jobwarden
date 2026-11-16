// Part of the Easy Apply run (easyapply.js): a form step's questions, how each is labelled and asked, which of the
// profile's answers fits it (RULES), and putting an answer in, as the user would.
globalThis.jobWarden.easyApply.questions = (() => {
  const { settle, shown, waitFor } = globalThis.jobWarden.easyApply.waiting;

  /** Field types that aren't questions: files go with the CV LinkedIn has, buttons are LinkedIn's. */
  const IGNORED_TYPES = new Set(['hidden', 'file', 'submit', 'button', 'reset', 'image', 'search']);

  /** Yes and no as LinkedIn's options say them, in the extension's languages. */
  const YES = /^(yes|da|ja|oui|sí|si|sim|tak)\b/i;
  const NO = /^(no|nu|nein|non|nie|não|nao)\b/i;

  /** The option that declines a voluntary question. */
  const DECLINE_OPTION = /decline|prefer not|rather not|don.?t wish|do not wish|choose not|not (to )?(say|answer|disclose)|nu doresc|nu vreau|keine angabe|möchte (ich )?nicht|ne (souhaite|pas)|prefiero no|nie chcę|wolę nie|prefiro não/i;

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

  return {
    IGNORED_TYPES, RULES, describedBy, questionsOf, fieldsOf, boxOf, isAnswered, put, valueOf, cleanText, normalized,
  };
})();
