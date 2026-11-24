package com.jobwarden.web.controller;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.inbox.dto.EmailUpdate;
import com.jobwarden.inbox.dto.MailAccount;
import com.jobwarden.inbox.enums.UpdateDecider;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.assistant.PromptCards;
import com.jobwarden.web.constants.ControllerConstants;
import com.jobwarden.web.constants.HtmxEvents;
import com.jobwarden.web.constants.HtmxHeaders;
import com.jobwarden.web.constants.ModelAttributes;
import com.jobwarden.web.constants.Views;
import com.jobwarden.web.dto.MailAccountForm;
import com.jobwarden.web.enums.MailProvider;
import com.jobwarden.web.enums.ReadingInterval;
import com.jobwarden.web.view.FormTexts;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static com.jobwarden.web.controller.Pages.requireProfile;

/**
 * The inbox: recruiters' emails JobWarden read from the profile's mailbox, each with what it says about one of the
 * profile's jobs, to apply to the job's timeline or dismiss, or for Claude to sort; what JobWarden and Claude applied
 * or dismissed on their own lately, to look over; and the mailbox itself, to connect, check now or disconnect. How often
 * it's read, and what becomes of the updates, are in Settings.
 */
@PageController
@RequestMapping(ControllerConstants.INBOX)
@CustomLog
@RequiredArgsConstructor
class InboxController {

	/** How many of the jobs an update can be applied to the page offers: those changed last. */
	static final int JOBS_TO_PICK = 200;

	/** The jobs an update can be applied to: as the inbox reads them. */
	private static final Set<ApplicationStatus> IN_PLAY = EnumSet.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED,
			ApplicationStatus.INTERVIEWING, ApplicationStatus.OFFER, ApplicationStatus.NO_RESPONSE);

	/** What an email can say. */
	private static final List<ApplicationStatus> EMAIL_STATUSES = List.of(ApplicationStatus.APPLIED,
			ApplicationStatus.INTERVIEWING, ApplicationStatus.OFFER, ApplicationStatus.REJECTED);

	/** How far back the page shows what JobWarden and Claude did on their own. */
	static final Duration DECIDED_FOR_YOU = Duration.ofDays(14);

	/** How long the card waits for a reading in the background: a slow server gives up well before. */
	static final Duration READING_PATIENCE = Duration.ofMinutes(5);

	private final InboxService inbox;

	private final ApplicationService applications;

	private final DiscoveryService discovery;

	private final MessageSource messages;

	private final Clock clock;

	private final FormTexts formTexts;

	private final PromptCards promptCards;

	@GetMapping
	String page(Model model) {
		long userId = requireProfile(model).id();
		addMailbox(model, userId);
		addUpdates(model, userId);
		return Views.INBOX;
	}

	/**
	 * Called by HTMX. Connected, the card says so at once, and the mailbox is read in the background, which the card
	 * then waits for (see {@link #account}); not, the card says why, with the form as it was typed, but its password.
	 */
	@PostMapping("/account")
	String connect(@Valid MailAccountForm form, BindingResult result, Model model, Locale locale) {
		long userId = requireProfile(model).id();
		String error = null;
		if (result.hasErrors()) {
			error = this.formTexts.errorMessage(result);
		} else {
			try {
				this.inbox.connect(userId, form.toNewMailAccount());
			} catch (BusinessRuleException refused) {
				error = this.formTexts.message(refused);
			}
		}
		if (error != null) {
			model.addAttribute(ModelAttributes.MAIL_FORM, form);
			model.addAttribute(ModelAttributes.MAIL_ERROR, error);
		} else {
			readInBackground(model, userId);
			model.addAttribute(ModelAttributes.TOAST, this.messages.getMessage("inbox.connected", null, locale));
		}
		addMailbox(model, userId);
		return Views.MAILBOX_FRAGMENT;
	}

	/**
	 * Called by HTMX every few seconds while the mailbox is read in the background: the card as it is now. Once it's
	 * read (after {@code readSince}), the card says how it went, and the emails waiting load again.
	 *
	 * @param readSince when the reading started; none for the card as it is
	 */
	@GetMapping("/account")
	String account(@RequestParam(required = false) @Nullable Instant readSince, Model model, Locale locale,
			HttpServletResponse response) {
		long userId = requireProfile(model).id();
		Optional<MailAccount> account = this.inbox.findAccount(userId);
		Instant now = this.clock.instant();
		if (readSince != null && account.isPresent()) {
			Instant read = account.get().lastCheckedAt();
			boolean done = read != null && !read.isBefore(readSince);
			if (!done && now.isBefore(readSince.plus(READING_PATIENCE))) {
				model.addAttribute(ModelAttributes.MAIL_READ_SINCE, readSince);
			} else if (done) {
				response.setHeader(HtmxHeaders.TRIGGER, HtmxEvents.INBOX_READ);
				if (account.get().lastError() == null) {
					model.addAttribute(ModelAttributes.TOAST, this.messages.getMessage("inbox.read",
							new Object[] { this.inbox.countPending(userId) }, locale));
				}
			}
		}
		addMailbox(model, userId);
		return Views.MAILBOX_FRAGMENT;
	}

	@PostMapping("/account/delete")
	String disconnect(Model model, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		this.inbox.disconnect(userId);
		response.setHeader(HtmxHeaders.REFRESH, "true");
		addMailbox(model, userId);
		return Views.MAILBOX_FRAGMENT;
	}

	/**
	 * Called by HTMX: reads the mailbox now, rather than in the next few minutes, in the background, which the card
	 * then waits for (see {@link #account}).
	 */
	@PostMapping("/check")
	String check(Model model) {
		long userId = requireProfile(model).id();
		readInBackground(model, userId);
		addMailbox(model, userId);
		return Views.MAILBOX_FRAGMENT;
	}

	/**
	 * Called by HTMX: adds the updates ticked, or the one whose Apply was pressed (ids), to their jobs' timelines,
	 * noting which email each was, and answers with the updates left. Each goes to the job and with the status its
	 * card shows (job-{id}, status-{id}), else those it matched; one with no job stays, and the answer says how many
	 * need one picked. The pages behind reload, as the jobs changed.
	 */
	@PostMapping("/updates/apply")
	String apply(@RequestParam(name = "ids", required = false) @Nullable List<Long> ids,
			@RequestParam MultiValueMap<String, String> form, Model model, Locale locale, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		Map<Long, EmailUpdate> pending = this.inbox.findPending(userId)
			.stream()
			.collect(Collectors.toMap(EmailUpdate::id, Function.identity()));
		Set<Long> chosen = (ids != null) ? new LinkedHashSet<>(ids) : Set.of();
		int applied = 0;
		int withoutJob = 0;
		for (long id : chosen) {
			EmailUpdate update = pending.get(id);
			if (update == null) {
				// Applied or dismissed already, e.g. in another tab
				continue;
			}
			Long job = number(form.getFirst("job-" + id)).orElse(update.applicationId());
			if (job == null) {
				withoutJob++;
				continue;
			}
			ApplicationStatus status = emailStatus(form.getFirst("status-" + id)).orElse(update.status());
			this.inbox.apply(userId, id, job, status, note(update, locale), UpdateDecider.YOU);
			applied++;
		}
		log.debug("Applied {} of the {} email updates chosen by profile {}; {} without a job", applied, chosen.size(),
				userId, withoutJob);
		if (applied > 0) {
			response.setHeader(HtmxHeaders.TRIGGER, HtmxEvents.APPLICATIONS_CHANGED);
		}
		if (withoutJob > 0) {
			model.addAttribute(ModelAttributes.UPDATES_ERROR,
					this.messages.getMessage("inbox.pickJobsFirst", new Object[] { withoutJob }, locale));
		}
		if (chosen.size() > 1 && applied > 0) {
			model.addAttribute(ModelAttributes.TOAST,
					this.messages.getMessage("inbox.appliedMany", new Object[] { applied }, locale));
		}
		addUpdates(model, userId);
		return Views.EMAIL_UPDATES_FRAGMENT;
	}

	/**
	 * Called by HTMX: dismisses the updates ticked, and answers with the updates left.
	 */
	@PostMapping("/updates/dismiss")
	String dismissSelected(@RequestParam(name = "ids", required = false) @Nullable List<Long> ids, Model model,
			Locale locale) {
		long userId = requireProfile(model).id();
		Set<Long> pending = this.inbox.findPending(userId).stream().map(EmailUpdate::id).collect(Collectors.toSet());
		List<Long> chosen = (ids != null) ? ids.stream().distinct().filter(pending::contains).toList() : List.of();
		chosen.forEach(id -> this.inbox.dismiss(userId, id, UpdateDecider.YOU));
		log.debug("Dismissed {} email updates of profile {}", chosen.size(), userId);
		if (!chosen.isEmpty()) {
			model.addAttribute(ModelAttributes.TOAST,
					this.messages.getMessage("inbox.dismissedMany", new Object[] { chosen.size() }, locale));
		}
		addUpdates(model, userId);
		return Views.EMAIL_UPDATES_FRAGMENT;
	}

	/** The note on the job's timeline: which email it was. */
	private String note(EmailUpdate update, Locale locale) {
		return this.messages.getMessage("inbox.note", new Object[] { (update.subject() != null) ? update.subject() : "",
				(update.sender() != null) ? update.sender() : "" }, locale);
	}

	/** A job's id as a form sends it: empty when it sent none, or text that isn't one. */
	private static Optional<Long> number(@Nullable String text) {
		try {
			return (text == null || text.isBlank()) ? Optional.empty() : Optional.of(Long.valueOf(text.strip()));
		} catch (NumberFormatException notANumber) {
			return Optional.empty();
		}
	}

	/** A status an email can say, as a form sends it: empty when it sent none, or another. */
	private static Optional<ApplicationStatus> emailStatus(@Nullable String text) {
		return EMAIL_STATUSES.stream().filter(status -> status.name().equals(text)).findFirst();
	}

	@PostMapping("/updates/{id}/dismiss")
	String dismiss(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.inbox.dismiss(userId, id, UpdateDecider.YOU);
		addUpdates(model, userId);
		return Views.EMAIL_UPDATES_FRAGMENT;
	}

	/**
	 * Called by HTMX: one Claude dismissed was news after all, and waits again.
	 */
	@PostMapping("/updates/{id}/reopen")
	String reopen(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.inbox.reopen(userId, id);
		addUpdates(model, userId);
		return Views.EMAIL_UPDATES_FRAGMENT;
	}

	/**
	 * Called by HTMX: asks Claude to sort the updates waiting, unless it's been asked and hasn't finished. The card
	 * then shows the prompt that hands the request to Claude Code, and asks again until Claude is done.
	 */
	@PostMapping("/sort")
	String sort(Model model) {
		long userId = requireProfile(model).id();
		if (latestSorting(userId).filter(request -> request.status().isOpen()).isEmpty()) {
			this.discovery.createRequest(userId, AiRequestKind.SORT_INBOX, AiRequirements.NONE);
		}
		addUpdates(model, userId);
		return Views.EMAIL_UPDATES_FRAGMENT;
	}

	/**
	 * Called by HTMX every few seconds while Claude is asked: the card as it is now. Once Claude is done, the card
	 * loads the updates anew, as Claude applied or dismissed them.
	 */
	@GetMapping("/sort")
	String sorting(Model model) {
		long userId = requireProfile(model).id();
		addUpdates(model, userId);
		model.addAttribute(ModelAttributes.SORT_FINISHED,
				!(model.getAttribute(ModelAttributes.SORT_REQUEST) instanceof AiRequest request && request.status().isOpen()));
		return Views.SORTING_FRAGMENT;
	}

	@PostMapping("/sort/cancel")
	String cancelSorting(Model model) {
		long userId = requireProfile(model).id();
		latestSorting(userId).filter(request -> request.status().isOpen())
			.ifPresent(request -> this.discovery.cancelRequest(userId, request.id()));
		addUpdates(model, userId);
		return Views.EMAIL_UPDATES_FRAGMENT;
	}

	/**
	 * The updates as they are now, while Claude sorts them: the card asks for them every few seconds.
	 */
	@GetMapping("/updates")
	String updates(Model model) {
		addUpdates(model, requireProfile(model).id());
		return Views.EMAIL_UPDATES_FRAGMENT;
	}

	/** A mailbox that can't be read keeps why, which the card shows once the reading is over. */
	private void readInBackground(Model model, long userId) {
		model.addAttribute(ModelAttributes.MAIL_READ_SINCE, this.clock.instant());
		this.inbox.checkSoon(userId);
		log.debug("Reading the mailbox of profile {} in the background", userId);
	}

	/**
	 * The mailbox, or the form to connect one: for the provider whose server the form came back with, else Gmail.
	 */
	private void addMailbox(Model model, long userId) {
		Optional<MailAccount> account = this.inbox.findAccount(userId);
		model.addAttribute(ModelAttributes.MAIL_ACCOUNT, account.orElse(null));
		model.addAttribute(ModelAttributes.READING_INTERVAL,
				account.map(mailbox -> ReadingInterval.of(mailbox.readEveryMinutes())).orElse(null));
		model.addAttribute(ModelAttributes.MAIL_PROVIDERS, MailProvider.values());
		model.addAttribute(ModelAttributes.MAIL_PROVIDER,
				(model.getAttribute(ModelAttributes.MAIL_FORM) instanceof MailAccountForm form)
						? MailProvider.of(form.host()) : MailProvider.GMAIL);
	}

	/**
	 * The updates waiting, and the jobs to pick for one: those changed last, with every job an update matched. And
	 * Claude's latest sorting of them, with its prompt, and what JobWarden and Claude decided on their own lately.
	 */
	private void addUpdates(Model model, long userId) {
		if (!model.containsAttribute(ModelAttributes.MAIL_ACCOUNT)) {
			// The empty list says what to do: connect a mailbox, or wait for it to be read
			model.addAttribute(ModelAttributes.MAIL_ACCOUNT, this.inbox.findAccount(userId).orElse(null));
		}
		Optional<AiRequest> sorting = latestSorting(userId);
		model.addAttribute(ModelAttributes.SORT_REQUEST, sorting.orElse(null));
		model.addAttribute(ModelAttributes.SORT_PROMPT,
				sorting.map(request -> this.promptCards.prompt(requireProfile(model), request.id())).orElse(null));
		model.addAttribute(ModelAttributes.DECIDED_FOR_YOU,
				this.inbox.findDecidedForYou(userId, this.clock.instant().minus(DECIDED_FOR_YOU)));
		List<EmailUpdate> updates = this.inbox.findPending(userId);
		List<ApplicationSummary> jobs = new ArrayList<>(this.applications.findAll(userId, null)
			.stream()
			.filter(job -> IN_PLAY.contains(job.status()))
			.sorted(Comparator.comparing(ApplicationSummary::lastModified).reversed())
			.limit(JOBS_TO_PICK)
			.toList());
		for (EmailUpdate update : updates) {
			Long applicationId = update.applicationId();
			if (applicationId != null && jobs.stream().noneMatch(job -> job.id() == applicationId)) {
				String jobTitle = update.jobTitle();
				jobs.add(ApplicationSummary.builder()
					.id(applicationId)
					.title((jobTitle != null) ? jobTitle : "")
					.company(update.company())
					.status(ApplicationStatus.SAVED)
					.capturedAt(update.receivedAt())
					.build());
			}
		}
		model.addAttribute(ModelAttributes.EMAIL_UPDATES, updates);
		model.addAttribute(ModelAttributes.INBOX_JOBS, jobs);
		model.addAttribute(ModelAttributes.EMAIL_STATUSES, EMAIL_STATUSES);
	}

	private Optional<AiRequest> latestSorting(long userId) {
		return this.discovery.findLatestRequest(userId, AiRequestKind.SORT_INBOX);
	}

}
