package com.jobwarden.mcp.controller;

import java.util.List;

import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

/**
 * Which profile a tool works on: the one its arguments name, or else the one the client's header names, or else the
 * only one there is.
 */
@Component
@RequiredArgsConstructor
class Profiles {

	static final String ARGUMENT = "profile_id";

	private final UserService users;

	/**
	 * Without a description, which every tool would repeat: list_profiles says what it is, once.
	 */
	static ObjectNode schema() {
		return Schemas.integer();
	}

	List<User> all() {
		return this.users.findAll();
	}

	/**
	 * @throws ToolException if the profile doesn't exist, or none is named and there are several
	 */
	long of(ToolCall call) {
		Long named = call.number(ARGUMENT);
		if (named != null) {
			if (!this.users.exists(named)) {
				throw new ToolException("There's no profile " + named + ": list_profiles lists them");
			}
			return named;
		}
		// The client's own: the profile's public id, or its id as given before
		String header = call.profileHeader();
		if (header != null && !header.isBlank()) {
			return this.users.findByKey(header)
				.map(User::id)
				.orElseThrow(() -> new ToolException("There's no profile " + header.strip() + ": list_profiles lists them"));
		}
		List<User> all = this.users.findAll();
		if (all.size() == 1) {
			return all.getFirst().id();
		}
		throw new ToolException(all.isEmpty() ? "There are no profiles yet: create one in JobWarden first"
				: "There are " + all.size() + " profiles: pass " + ARGUMENT + " (list_profiles lists them)");
	}

}
