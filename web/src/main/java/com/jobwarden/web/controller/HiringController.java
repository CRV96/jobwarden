package com.jobwarden.web.controller;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.hiring.dto.HiringPost;
import com.jobwarden.hiring.dto.HiringPostQuery;
import com.jobwarden.hiring.dto.Recruiter;
import com.jobwarden.hiring.enums.PostedWithin;
import com.jobwarden.hiring.service.HiringService;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.HiringParams;
import com.jobwarden.web.dto.HiringRequestForm;
import com.jobwarden.web.enums.HiringTab;
import com.jobwarden.web.enums.RecruiterFilter;
import com.jobwarden.web.view.ClaudePrompts;
import jakarta.servlet.http.HttpServletResponse;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Who's hiring: recruiters' posts saying they're hiring, which Claude finds on LinkedIn, X and Reddit when the page
 * asks it to (a request Claude Code does through the MCP server), and the recruiters, to follow, which Claude finds
 * too, or come from the contacts saved on jobs. A post saved as a job becomes an application, bookmarked.
 */
@Controller
@RequestMapping(ControllerConstants.HIRING)
@CustomLog
@RequiredArgsConstructor
class HiringController extends BaseController {

	/** How many hashtags the page offers to filter by: the most used. */
	static final int POPULAR_TAGS = 12;

	/** The longest job title a post saved as a job gets from its first line. */
	private static final int MAX_TITLE_LENGTH = 120;

	private final HiringService hiring;

	private final ApplicationService applications;

	private final DiscoveryService discovery;

	@GetMapping
	String page(@ModelAttribute(ModelAttributes.HIRING_PARAMS) HiringParams params, Model model) {
		long userId = requireProfile(model).id();
		List<HiringPost> all = this.hiring.findPosts(userId, HiringPostQuery.ALL);
		List<Recruiter> recruiters = this.hiring.findRecruiters(userId);
		model.addAttribute(ModelAttributes.HIRING_POSTS,
				(params.tab() == HiringTab.RECRUITERS) ? List.of() : this.hiring.findPosts(userId, params.toQuery()));
		model.addAttribute(ModelAttributes.RECRUITERS, recruiters);
		model.addAttribute(ModelAttributes.RECRUITERS_SHOWN, recruiters.stream()
			.filter(recruiter -> params.who().shows(recruiter) && matches(recruiter, params.q()))
			.toList());
		model.addAttribute(ModelAttributes.RECRUITER_COUNTS, Arrays.stream(RecruiterFilter.values())
			.collect(Collectors.toMap(Function.identity(),
					filter -> recruiters.stream().filter(filter::shows).count(), (a, b) -> a,
					() -> new EnumMap<>(RecruiterFilter.class))));
		model.addAttribute(ModelAttributes.HIRING_TAGS, popularTags(all));
		model.addAttribute(ModelAttributes.HIRING_COUNTRIES,
				all.stream().map(HiringPost::country).filter(country -> country != null).distinct().sorted().toList());
		model.addAttribute(ModelAttributes.HIRING_TABS, HiringTab.values());
		model.addAttribute(ModelAttributes.POSTED_PERIODS, PostedWithin.values());
		model.addAttribute(ModelAttributes.RECRUITER_FILTERS, RecruiterFilter.values());
		addRequest(model, userId, (params.tab() == HiringTab.RECRUITERS) ? AiRequestKind.FIND_RECRUITERS
				: AiRequestKind.FIND_HIRING_POSTS);
		return Views.HIRING;
	}

	/**
	 * Asks Claude for posts, or for recruiters. The page then shows the prompt that hands the request to Claude Code.
	 */
	@PostMapping("/requests")
	String ask(HiringRequestForm form, Model model) {
		long userId = requireProfile(model).id();
		this.discovery.createRequest(userId, form.kind(), form.toRequirements());
		addRequest(model, userId, form.kind());
		return Views.HIRING_ASK_FRAGMENT;
	}

	/**
	 * The request again, which the page asks for every few seconds while Claude is on it. Once it's done, the page
	 * reloads, with the posts Claude added.
	 *
	 * @param waiting whether the page showed it open
	 */
	@GetMapping("/requests/latest")
	String latestRequest(@RequestParam(defaultValue = "FIND_HIRING_POSTS") AiRequestKind kind,
			@RequestParam(defaultValue = "false") boolean waiting, Model model, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		AiRequest request = addRequest(model, userId, kind);
		if (waiting && request != null && !request.status().isOpen()) {
			response.setHeader(HtmxHeaders.REFRESH, "true");
		}
		return Views.HIRING_ASK_FRAGMENT;
	}

	@PostMapping("/requests/{id}/cancel")
	String cancel(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		AiRequest cancelled = this.discovery.cancelRequest(userId, id);
		addRequest(model, userId, cancelled.kind());
		return Views.HIRING_ASK_FRAGMENT;
	}

	/**
	 * Adds a recruiter by hand. Why one isn't added shows on the recruiters' tab.
	 */
	@PostMapping("/recruiters")
	String addRecruiter(@RequestParam String name, @RequestParam String profileUrl,
			@RequestParam(required = false) @Nullable String headline, Model model, RedirectAttributes redirect) {
		long userId = requireProfile(model).id();
		try {
			// Added by hand, they're someone to follow
			this.hiring.follow(userId, this.hiring.addRecruiter(userId, name, headline, profileUrl).id(), true);
		} catch (BusinessRuleException refused) {
			redirect.addFlashAttribute(ModelAttributes.HIRING_ERROR, message(refused));
		}
		return redirectTo(HiringParams.defaults().withTab(HiringTab.RECRUITERS).url());
	}

	/**
	 * Called by HTMX, which takes the recruiter off the page. Claude won't add them again.
	 */
	@PostMapping("/recruiters/{id}/remove")
	ResponseEntity<Void> removeRecruiter(@PathVariable long id, Model model) {
		this.hiring.removeRecruiter(requireProfile(model).id(), id);
		return ResponseEntity.ok().build();
	}

	/**
	 * Called by HTMX. The answer is the recruiter's follow button, which takes the place of each of theirs on the
	 * page (hx-swap-oob).
	 */
	@PostMapping("/recruiters/{id}/follow")
	String follow(@PathVariable long id, @RequestParam boolean followed, Model model) {
		model.addAttribute(ModelAttributes.RECRUITER, this.hiring.follow(requireProfile(model).id(), id, followed));
		model.addAttribute(ModelAttributes.FOLLOW_OOB, true);
		return Views.HIRING_FOLLOW_FRAGMENT;
	}

	/**
	 * Called by HTMX, which takes the post off the page.
	 */
	@PostMapping("/posts/{id}/hide")
	ResponseEntity<Void> hide(@PathVariable long id, Model model) {
		this.hiring.hidePost(requireProfile(model).id(), id);
		return ResponseEntity.ok().build();
	}

	/**
	 * Saves the post as a job, bookmarked: its role (or its first line) as the title, the post as the description,
	 * and its link, so the job opens the post. One already saved under that link stays as it is.
	 */
	@PostMapping("/posts/{id}/save")
	String save(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		HiringPost post = this.hiring.findPost(userId, id);
		Locale locale = LocaleContextHolder.getLocale();
		long applicationId = this.applications.track(NewApplication.builder()
			.userId(userId)
			.title((post.role() != null) ? post.role() : title(post.text()))
			.company(post.company())
			.location((post.country() != null) ? Locale.of("", post.country()).getDisplayCountry(locale) : null)
			.description(post.text())
			.link(post.link())
			.initialStatus(ApplicationStatus.SAVED)
			.initialStatusDate(LocalDate.now())
			.build()).id();
		model.addAttribute(ModelAttributes.POST, this.hiring.markSaved(userId, id, applicationId));
		model.addAttribute(ModelAttributes.HIRING_PARAMS, HiringParams.defaults());
		return Views.HIRING_POST_FRAGMENT;
	}

	/**
	 * The latest request of the kind, posts or recruiters, with what to paste into Claude Code while it waits.
	 *
	 * @return it, or {@code null} if the profile never asked
	 */
	private @Nullable AiRequest addRequest(Model model, long userId, AiRequestKind kind) {
		model.addAttribute(ModelAttributes.ASK_KIND, kind);
		model.addAttribute(ModelAttributes.RECRUITER_COUNTS_OFFERED, HiringRequestForm.COUNTS);
		Optional<AiRequest> latest = this.discovery.findRequests(userId, 50)
			.stream()
			.filter(request -> request.kind() == kind)
			.findFirst();
		model.addAttribute(ModelAttributes.HIRING_REQUEST, latest.orElse(null));
		model.addAttribute(ModelAttributes.HIRING_PROMPT,
				latest.map(request -> ClaudePrompts.forRequest(request.id(), userId)).orElse(null));
		return latest.orElse(null);
	}

	/**
	 * @return the hashtags most posts have, the most used first
	 */
	static List<String> popularTags(List<HiringPost> posts) {
		Map<String, Long> counts = posts.stream()
			.flatMap(post -> post.tags().stream())
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
		return counts.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
			.limit(POPULAR_TAGS)
			.map(Map.Entry::getKey)
			.toList();
	}

	/**
	 * @return whether the recruiter's name, headline or company has the search, or there's none
	 */
	private static boolean matches(Recruiter recruiter, @Nullable String search) {
		if (search == null) {
			return true;
		}
		String text = String.join(" ", recruiter.name(), Objects.toString(recruiter.headline(), ""),
				Objects.toString(recruiter.company(), "")).toLowerCase(Locale.ROOT);
		return text.contains(search.strip().toLowerCase(Locale.ROOT));
	}

	/**
	 * A post's first line, as a job title, when it doesn't say the role.
	 */
	private static String title(String text) {
		String first = text.strip().lines().findFirst().orElse(text).strip();
		return (first.length() <= MAX_TITLE_LENGTH) ? first : first.substring(0, MAX_TITLE_LENGTH - 1) + "…";
	}

}
