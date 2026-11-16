package com.jobwarden.web.assistant;

import java.util.List;

import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.Assistant;
import com.jobwarden.web.assistant.AssistantConnections.Connection;
import com.jobwarden.web.view.AssistantPrompts;
import com.jobwarden.web.view.AssistantPrompts.Prompt;
import com.jobwarden.web.view.AssistantPrompts.RunRefusal;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The cards that ask the profile's AI assistant for something: what to paste into it, the terminal command that starts
 * it with it, whether JobWarden can run it itself, here, and does, and whether each assistant has JobWarden's MCP
 * server on this computer. Without the runner (a test of one controller) the cards offer the prompts alone; without a
 * way to tell the connections, they open the apps without asking.
 */
@Component
@RequiredArgsConstructor
public class PromptCards {

	private final ObjectProvider<AssistantRunner> runner;

	private final ObjectProvider<AssistantConnections> connections;

	public Prompt prompt(User profile, long requestId) {
		return prompt(profile, List.of(requestId));
	}

	/**
	 * The same for several requests, done one after the other in one session.
	 */
	public Prompt prompt(User profile, List<Long> requestIds) {
		Prompt prompt = AssistantPrompts.forRequests(profile.assistant(), requestIds, profile.id());
		AssistantRunner runner = this.runner.getIfAvailable();
		if (runner == null) {
			return connected(prompt, profile);
		}
		// Why Run last refused them, kept a while, so the card keeps saying it as it asks again
		RunRefusal refused = runner.refusal(requestIds)
			.map(refusal -> new RunRefusal(refusal.reason().name(), refusal.assistant().product(),
					AssistantRunner.signInCommand(refusal.assistant())))
			.orElse(null);
		return connected(prompt.run(runner.canRun(profile.assistant()), runner.isRunning(requestIds)).refused(refused),
				profile);
	}

	/**
	 * Whether the assistant has JobWarden's MCP server on this computer; unknown without a way to tell.
	 */
	public Connection connection(Assistant assistant) {
		AssistantConnections connections = this.connections.getIfAvailable();
		@Nullable Connection connection = (connections == null) ? null : connections.find(assistant);
		return (connection == null) ? Connection.UNKNOWN : connection;
	}

	/**
	 * The prompt, its apps knowing whether each assistant has JobWarden's MCP server on this computer: one that hasn't
	 * says to connect it first, in Settings; one set up for another profile says so, since a call that leaves the
	 * profile out goes there.
	 */
	public Prompt connected(Prompt prompt, User profile) {
		if (this.connections.getIfAvailable() == null) {
			return prompt;
		}
		return prompt.withApps(prompt.apps().stream().map(link -> {
			Connection connection = connection(link.assistant());
			return link.checked(!connection.isMissing(), connection.profile() != null && !connection.isFor(profile),
					connection.folder());
		}).toList());
	}

}
