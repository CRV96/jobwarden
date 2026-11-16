package com.jobwarden.web.user;

import java.util.Optional;

import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.exception.UserNotFoundException;
import com.jobwarden.identity.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * The profile chosen in the current browser session. The self-hosted version has no login,
 * so the selected profile's id is simply kept in the HTTP session.
 */
@Component
@CustomLog
@RequiredArgsConstructor
public class CurrentUser {

	private static final String SESSION_ATTRIBUTE = CurrentUser.class.getName() + ".userId";

	// Spring injects a proxy that always uses the session of the request being handled
	private final HttpSession session;

	private final UserService users;

	/**
	 * @return the selected profile, or empty if none is selected or it no longer exists
	 */
	public Optional<User> get() {
		if (!(this.session.getAttribute(SESSION_ATTRIBUTE) instanceof Long userId)) {
			return Optional.empty();
		}
		Optional<User> user = this.users.findById(userId);
		if (user.isEmpty()) {
			// Gone since it was selected, e.g. the database was reset
			log.debug("Selected profile {} no longer exists, forgetting it", userId);
			this.session.removeAttribute(SESSION_ATTRIBUTE);
		}
		return user;
	}

	/**
	 * Selects no profile in this browser session, e.g. once the one selected is deleted.
	 */
	public void forget() {
		this.session.removeAttribute(SESSION_ATTRIBUTE);
	}

	/**
	 * @throws UserNotFoundException if there's no such profile
	 */
	public void select(long userId) {
		if (!this.users.exists(userId)) {
			throw new UserNotFoundException(userId);
		}
		this.session.setAttribute(SESSION_ATTRIBUTE, userId);
		log.debug("Profile {} selected for this browser session", userId);
	}

}
