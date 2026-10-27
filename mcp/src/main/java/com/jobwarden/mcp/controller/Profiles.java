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

	static ObjectNode schema() {
		return Schemas.integer("The profile to work on, from list_profiles. Leave it out when there's only one profile, "
				+ "or when the server was told which one to use");
	}

	List<User> all() {
		return this.users.findAll();
	}

	/**
	 * @throws ToolException if the profile doesn't exist, or none is named and there are several
	 */
	long of(ToolCall call) {
		Long named = (call.number(ARGUMENT) != null) ? call.number(ARGUMENT) : call.profileHeader();
		if (named != null) {
			if (!this.users.exists(named)) {
				throw new ToolException("There's no profile " + named + ": list_profiles lists them");
			}
			return named;
		}
		List<User> all = this.users.findAll();
		if (all.size() == 1) {
			return all.getFirst().id();
		}
		throw new ToolException(all.isEmpty() ? "There are no profiles yet: create one in JobWarden first"
				: "There are " + all.size() + " profiles: pass " + ARGUMENT + " (list_profiles lists them)");
	}

}
