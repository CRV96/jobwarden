package com.jobwarden.web.controller;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.web.assistant.AssistantRunner;
import com.jobwarden.web.assistant.RunRefusedException;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Run, next to a request's prompt: JobWarden starts the profile's AI assistant itself, on this computer, in the
 * background, rather than the profile pasting the prompt. The card then follows the request as it would.
 * <p>
 * It starts a program, so only this app's own pages may ask: HTMX's header, which a form on another site can't send,
 * and the page's Origin, when there is one. Auto-apply isn't run: the profile is needed at the browser.
 */
@Controller
@RequestMapping(ControllerConstants.ASSISTANT_RUN)
@CustomLog
class AssistantRunController extends BaseController {

	private final AssistantRunner runner;

	private final DiscoveryService discovery;

	private final String mcpPath;

	AssistantRunController(AssistantRunner runner, DiscoveryService discovery,
			@Value("${jobwarden.mcp.path:/mcp}") String mcpPath) {
		this.runner = runner;
		this.discovery = discovery;
		this.mcpPath = mcpPath;
	}

	/**
	 * Starts the assistant on the requests, which must be the profile's, waiting, and not Auto-apply; answers, in place
	 * of the button, that it started, or why not.
	 */
	@PostMapping
	String run(@RequestParam List<Long> requests, Model model,
			@RequestHeader(name = HtmxHeaders.REQUEST, required = false) @Nullable String htmx,
			@RequestHeader(name = HttpHeaders.ORIGIN, required = false) @Nullable String origin) {
		String server = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
		if (htmx == null || !sameOrigin(origin, server)) {
			log.warn("Refused to run the assistant for a page from {}", origin);
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Run is for JobWarden's own pages");
		}
		User profile = requireProfile(model);
		List<Long> ids = requests.stream().distinct().toList();
		if (ids.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No request to run");
		}
		for (long id : ids) {
			AiRequest request = this.discovery.findRequest(profile.id(), id);
			if (request.status() != AiRequestStatus.WAITING || request.kind() == AiRequestKind.AUTO_APPLY) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Request " + id + " isn't one to run: " + request.status());
			}
		}
		model.addAttribute(ModelAttributes.RUN_ASSISTANT, profile.assistant().product());
		model.addAttribute(ModelAttributes.RUN_SIGN_IN, AssistantRunner.signInCommand(profile.assistant()));
		try {
			this.runner.run(profile.assistant(), profile.id(), ids, server + this.mcpPath);
			model.addAttribute(ModelAttributes.RUN_STARTED, true);
		} catch (RunRefusedException refused) {
			log.warn("Couldn't run {} for profile {}: {}", profile.assistant().product(), profile.id(), refused.getMessage());
			model.addAttribute(ModelAttributes.RUN_STARTED, false);
			model.addAttribute(ModelAttributes.RUN_REFUSED, refused.getReason());
		}
		return Views.ASSISTANT_RUN_FRAGMENT;
	}

	/** No Origin is a client that isn't a browser page; a page must be this server's. */
	private static boolean sameOrigin(@Nullable String origin, String server) {
		if (origin == null || origin.isBlank()) {
			return true;
		}
		try {
			URI page = URI.create(origin);
			URI self = URI.create(server);
			return Objects.equals(lower(page.getScheme()), lower(self.getScheme()))
					&& Objects.equals(lower(page.getHost()), lower(self.getHost())) && port(page) == port(self);
		} catch (IllegalArgumentException notAnOrigin) {
			return false;
		}
	}

	private static @Nullable String lower(@Nullable String text) {
		return (text == null) ? null : text.toLowerCase(Locale.ROOT);
	}

	private static int port(URI uri) {
		if (uri.getPort() != -1) {
			return uri.getPort();
		}
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}

}
