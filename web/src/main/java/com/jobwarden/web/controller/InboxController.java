package com.jobwarden.web.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.inbox.dto.EmailUpdate;
import com.jobwarden.inbox.dto.MailAccount;
import com.jobwarden.inbox.enums.UpdateDecider;
import com.jobwarden.inbox.exception.EmailUpdateNotFoundException;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.HtmxEvents;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.EmailUpdateForm;
import com.jobwarden.web.dto.MailAccountForm;
import com.jobwarden.web.enums.MailProvider;
import com.jobwarden.web.enums.ReadingInterval;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The inbox: recruiters' emails JobWarden read from the profile's mailbox, each with what it says about one of the
 * profile's jobs, to apply to the job's timeline or dismiss, or for Claude to sort; what JobWarden and Claude applied
 * or dismissed on their own lately, to look over; and the mailbox itself, to connect, check now or disconnect. How often
 * it's read, and what becomes of the updates, are in Settings.
 */
@Controller
@RequestMapping(ControllerConstants.INBOX)
@CustomLog
@RequiredArgsConstructor
class InboxController extends BaseController {

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

	private final InboxService inbox;

	private final ApplicationService applications;

	private final DiscoveryService discovery;

	private final MessageSource messages;

	@GetMapping
	String page(Model model) {
		long userId = requireProfile(model).id();
		addMailbox(model, userId);
		addUpdates(model, userId);
		return Views.INBOX;
	}

	/**
	 * Called by HTMX. Connected, the mailbox is read at once, and the page reloads with what it found; not, the card
	 * says why, with the form as it was typed, but its password.
	 */
	@PostMapping("/account")
	String connect(@Valid MailAccountForm form, BindingResult result, Model model, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		String error = null;
		if (result.hasErrors()) {
			error = errorMessage(result);
		} else {
			try {
				this.inbox.connect(userId, form.toNewMailAccount());
			} catch (BusinessRuleException refused) {
				error = message(refused);
			}
		}
		if (error != null) {
			model.addAttribute(ModelAttributes.MAIL_FORM, form);
			model.addAttribute(ModelAttributes.MAIL_ERROR, error);
			addMailbox(model, userId);
			return Views.MAILBOX_FRAGMENT;
		}
		readNow(userId);
		response.setHeader(HtmxHeaders.REFRESH, "true");
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
	 * Called by HTMX: reads the mailbox now, rather than in the next few minutes, and reloads the page.
	 */
	@PostMapping("/check")
	String check(Model model, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		readNow(userId);
		response.setHeader(HtmxHeaders.REFRESH, "true");
		addMailbox(model, userId);
		return Views.MAILBOX_FRAGMENT;
	}

	/**
	 * Called by HTMX: adds the update to the job's timeline, noting which email it was, and answers with the updates
	 * left. The pages behind reload, as the job changed.
	 */
	@PostMapping("/updates/{id}/apply")
	String apply(@PathVariable long id, @Valid EmailUpdateForm form, BindingResult result, Model model, Locale locale,
			HttpServletResponse response) {
		long userId = requireProfile(model).id();
		if (result.hasErrors()) {
			model.addAttribute(ModelAttributes.MAIL_ERROR, errorMessage(result));
		} else {
			EmailUpdate update = this.inbox.findPending(userId)
				.stream()
				.filter(pending -> pending.id() == id)
				.findFirst()
				.orElseThrow(() -> new EmailUpdateNotFoundException(id));
			String note = this.messages.getMessage("inbox.note", new Object[] {
					(update.subject() != null) ? update.subject() : "", (update.sender() != null) ? update.sender() : "" },
					locale);
			this.inbox.apply(userId, id, form.job(), form.status(), note, UpdateDecider.YOU);
			response.setHeader(HtmxHeaders.TRIGGER, HtmxEvents.APPLICATIONS_CHANGED);
		}
		addUpdates(model, userId);
		return Views.EMAIL_UPDATES_FRAGMENT;
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

	/** A mailbox that can't be read keeps why, which the page then shows. */
	private void readNow(long userId) {
		try {
			this.inbox.check(userId);
		} catch (BusinessRuleException failed) {
			log.debug("Mailbox of profile {} not read: {}", userId, failed.getMessage());
		}
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
				sorting.map(request -> prompt(model, request.id())).orElse(null));
		model.addAttribute(ModelAttributes.DECIDED_FOR_YOU,
				this.inbox.findDecidedForYou(userId, Instant.now().minus(DECIDED_FOR_YOU)));
		List<EmailUpdate> updates = this.inbox.findPending(userId);
		List<ApplicationSummary> jobs = new ArrayList<>(this.applications.findAll(userId, null)
			.stream()
			.filter(job -> IN_PLAY.contains(job.status()))
			.sorted(Comparator.comparing(ApplicationSummary::lastModified).reversed())
			.limit(JOBS_TO_PICK)
			.toList());
		for (EmailUpdate update : updates) {
			if (update.applicationId() != null && jobs.stream().noneMatch(job -> job.id() == update.applicationId())) {
				jobs.add(ApplicationSummary.builder()
					.id(update.applicationId())
					.title(update.jobTitle())
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
