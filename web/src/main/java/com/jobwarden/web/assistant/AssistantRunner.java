package com.jobwarden.web.assistant;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import com.jobwarden.common.exception.JobWardenException;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.enums.Assistant;
import com.jobwarden.web.view.AssistantPrompts;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runs the profile's AI assistant on this computer, in the background, when a page's Run is clicked: claude -p or
 * codex exec on the requests asked, as the terminal loop would, with no one to answer it. It runs on the profile's own
 * plan, signed in as the user JobWarden runs as; in Docker there's no assistant to run, and the pages keep only their
 * prompts to paste.
 * <p>
 * It asks the assistant first whether it's signed in, and when it isn't, the page says how to sign it in. A run that
 * stops before ending its requests (a crash, too long) fails them, saying why, so the page doesn't wait forever. What it printed is kept in the runs directory, a file per run.
 */
@Component
@CustomLog
public class AssistantRunner {

	/** The most runs at once: each is an assistant, with its own share of the plan's limits. */
	static final int MAX_RUNNING = 3;

	/**
	 * Where an assistant's command usually is, besides the PATH JobWarden was started with, which an IDE trims; last,
	 * the Codex that comes inside the ChatGPT (or Codex) app on a Mac, signed in with the app.
	 */
	private static final List<String> USUAL_DIRECTORIES = List.of("/opt/homebrew/bin", "/usr/local/bin",
			"~/.local/bin", "~/.claude/local", "~/.npm-global/bin", "~/.volta/bin", "~/.bun/bin",
			"/Applications/ChatGPT.app/Contents/Resources/codex-cli/bin",
			"/Applications/Codex.app/Contents/Resources/codex-cli/bin",
			"~/Applications/ChatGPT.app/Contents/Resources/codex-cli/bin");

	/** How each says whether it's signed in: it ends with 0 when it is. */
	private static final Map<Assistant, List<String>> SIGN_IN_STATUS = Map.of(Assistant.CLAUDE_CODE,
			List.of("auth", "status"), Assistant.CODEX, List.of("login", "status"));

	/** How long it may take to say: it's quick, a tenth of a second. */
	private static final Duration SIGN_IN_STATUS_TIMEOUT = Duration.ofSeconds(10);

	/** How long the cards keep saying why Run refused, for a request still waiting. */
	static final Duration REFUSAL_SHOWN = Duration.ofMinutes(10);

	/** How much of what a run printed last says why it stopped. */
	private static final int REASON_MAX_LENGTH = 300;

	private final DiscoveryService discovery;

	private final boolean enabled;

	private final Map<Assistant, Optional<Path>> executables = new EnumMap<>(Assistant.class);

	private final Path directory;

	private final Duration timeout;

	/** The runs going on, by request: one each at most. */
	private final Map<Long, Process> running = new ConcurrentHashMap<>();

	/** Why Run last refused each request, which the cards keep saying a while. */
	private final Map<Long, Refusal> refusals = new ConcurrentHashMap<>();

	/**
	 * @param claudeCommand where Claude Code is, when it isn't on the PATH or in a usual place
	 * @param codexCommand where Codex is, likewise
	 * @param directory where the runs start, and keep what they printed
	 */
	AssistantRunner(DiscoveryService discovery, @Value("${jobwarden.assistant.run.enabled:true}") boolean enabled,
			@Value("${jobwarden.assistant.claude-command:claude}") String claudeCommand,
			@Value("${jobwarden.assistant.codex-command:codex}") String codexCommand,
			@Value("${jobwarden.assistant.run.directory:${user.home}/.jobwarden/runs}") Path directory,
			@Value("${jobwarden.assistant.run.timeout:60m}") Duration timeout) {
		this.discovery = discovery;
		this.enabled = enabled;
		this.directory = directory;
		this.timeout = timeout;
		this.executables.put(Assistant.CLAUDE_CODE, find(claudeCommand));
		this.executables.put(Assistant.CODEX, find(codexCommand));
		this.executables.forEach((assistant, path) -> log.info("{}: {}", assistant.product(),
				path.map(Path::toString).orElse("not found, so the pages offer no Run")));
	}

	/**
	 * @return whether Run can start the assistant here
	 */
	public boolean canRun(Assistant assistant) {
		return this.enabled && this.executables.get(assistant).isPresent();
	}

	/**
	 * @return whether a run is going on for any of these requests
	 */
	public boolean isRunning(List<Long> requestIds) {
		return requestIds.stream().anyMatch(this.running::containsKey);
	}

	/**
	 * How one signs the assistant in on this computer, in a terminal, for Run to work.
	 */
	public static String signInCommand(Assistant assistant) {
		return switch (assistant) {
			case CLAUDE_CODE -> "claude auth login";
			case CODEX -> "codex login";
		};
	}

	/**
	 * Starts the assistant on the requests, which must wait for it. It ends them itself, through the MCP server; when
	 * it stops without, they fail.
	 *
	 * @param mcpUrl the MCP server's address, e.g. http://localhost:8080/mcp
	 * @throws RunRefusedException when the assistant isn't here, isn't signed in, the requests are already being run,
	 * or too many runs are going on
	 */
	public void run(Assistant assistant, long userId, List<Long> requestIds, String mcpUrl) {
		Path executable = this.executables.get(assistant)
			.filter(path -> this.enabled)
			.orElseThrow(() -> refuse(assistant, requestIds, new RunRefusedException(RunRefusedException.Reason.UNAVAILABLE)));
		// Asked first, so a run that couldn't do anything doesn't fail the requests: they keep waiting, to run again
		if (!signedIn(assistant, executable)) {
			throw refuse(assistant, requestIds, new RunRefusedException(RunRefusedException.Reason.SIGNED_OUT));
		}
		List<String> command = new ArrayList<>(AssistantPrompts.background(assistant, requestIds, userId, mcpUrl));
		command.set(0, executable.toString());
		Process process;
		Path output;
		synchronized (this.running) {
			if (isRunning(requestIds) || this.running.size() >= MAX_RUNNING) {
				throw new RunRefusedException(RunRefusedException.Reason.BUSY);
			}
			try {
				Files.createDirectories(this.directory);
				output = this.directory.resolve("request-" + requestIds.getFirst() + ".log");
				ProcessBuilder builder = new ProcessBuilder(command).directory(this.directory.toFile())
					.redirectErrorStream(true)
					.redirectOutput(output.toFile());
				// Its own directory first: Claude Code runs on node, which an IDE's trimmed PATH may not find
				builder.environment().put("PATH", path(executable.getParent(), builder.environment().get("PATH")));
				process = builder.start();
				// Nothing to read: codex exec and claude -p would otherwise wait for what's piped in
				process.getOutputStream().close();
			} catch (IOException couldNotStart) {
				throw refuse(assistant, requestIds, new RunRefusedException(RunRefusedException.Reason.FAILED, couldNotStart));
			}
			requestIds.forEach(id -> this.running.put(id, process));
		}
		requestIds.forEach(this.refusals::remove);
		log.info("Started {} on requests {} of profile {}", assistant.product(), requestIds, userId);
		process.onExit()
			.orTimeout(this.timeout.toMillis(), TimeUnit.MILLISECONDS)
			.whenComplete((ended, failure) -> {
				boolean tooLong = failure instanceof TimeoutException;
				if (tooLong) {
					process.descendants().forEach(ProcessHandle::destroy);
					process.destroy();
				}
				finish(assistant, userId, requestIds, process, output, tooLong ? null : process.exitValue());
			});
	}

	/**
	 * Whether the assistant is signed in here, as its status says (claude auth status, codex login status). When it
	 * can't say, the run goes ahead, and says itself what's wrong.
	 */
	private boolean signedIn(Assistant assistant, Path executable) {
		List<String> command = new ArrayList<>();
		command.add(executable.toString());
		command.addAll(SIGN_IN_STATUS.get(assistant));
		try {
			ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			builder.environment().put("PATH", path(executable.getParent(), builder.environment().get("PATH")));
			Process process = builder.start();
			process.getOutputStream().close();
			if (!process.waitFor(SIGN_IN_STATUS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
				process.destroy();
				log.warn("{} didn't say whether it's signed in within {}", assistant.product(), SIGN_IN_STATUS_TIMEOUT);
				return true;
			}
			boolean signedIn = process.exitValue() == 0;
			if (!signedIn) {
				log.warn("{} isn't signed in on this computer: {} signs it in", assistant.product(), signInCommand(assistant));
			}
			return signedIn;
		} catch (IOException couldNotAsk) {
			log.warn("Couldn't ask {} whether it's signed in: {}", assistant.product(), couldNotAsk.getMessage());
			return true;
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return true;
		}
	}

	/**
	 * Why Run last refused these requests, while it's recent: the card that shows them asks again every few seconds,
	 * and keeps saying it, until a run starts or the requests are done.
	 */
	public Optional<Refusal> refusal(List<Long> requestIds) {
		Instant shownSince = Instant.now().minus(REFUSAL_SHOWN);
		return requestIds.stream()
			.map(this.refusals::get)
			.filter(refusal -> refusal != null && refusal.at().isAfter(shownSince))
			.max(Comparator.comparing(Refusal::at));
	}

	/**
	 * Why Run didn't start a request: the assistant isn't here, isn't signed in, or didn't start.
	 *
	 * @param assistant the one it was asked to run
	 * @param at when
	 */
	public record Refusal(RunRefusedException.Reason reason, Assistant assistant, Instant at) {
	}

	/** Keeps why, for the requests' card to keep saying it; it says it was busy only as it happens. */
	private RunRefusedException refuse(Assistant assistant, List<Long> requestIds, RunRefusedException refused) {
		Refusal refusal = new Refusal(refused.getReason(), assistant, Instant.now());
		requestIds.forEach(id -> this.refusals.put(id, refusal));
		return refused;
	}

	/**
	 * Forgets the runs of these requests, as their profile is deleted: stops those going on, without failing their
	 * requests, and deletes what they printed.
	 */
	public void forget(Collection<Long> requestIds) {
		for (long requestId : requestIds) {
			this.refusals.remove(requestId);
			Process process = this.running.remove(requestId);
			if (process != null) {
				process.descendants().forEach(ProcessHandle::destroy);
				process.destroy();
				log.info("Stopped the run of request {}: its profile is deleted", requestId);
			}
			try {
				Files.deleteIfExists(this.directory.resolve("request-" + requestId + ".log"));
			} catch (IOException couldNotDelete) {
				log.warn("Couldn't delete what the run of request {} printed: {}", requestId, couldNotDelete.getMessage());
			}
		}
	}

	/**
	 * Fails the requests the run left open, saying why: the end of what it printed, or that it took too long.
	 *
	 * @param exitValue {@code null} when it was stopped for taking too long
	 */
	private void finish(Assistant assistant, long userId, List<Long> requestIds, Process process, Path output,
			@Nullable Integer exitValue) {
		// Those forgotten meanwhile, as their profile was deleted, are gone: nothing to fail
		List<Long> ours = requestIds.stream().filter(id -> this.running.remove(id, process)).toList();
		log.info("{} ended on requests {} of profile {}: {}", assistant.product(), requestIds, userId,
				(exitValue == null) ? "stopped after " + this.timeout : "exit " + exitValue);
		if (ours.isEmpty()) {
			return;
		}
		String reason = (exitValue == null) ? assistant.product() + " was stopped: it took over " + duration(this.timeout) + "."
				: assistant.product() + " stopped before finishing" + lastLine(output).map(line -> ": " + line).orElse(".");
		for (long requestId : ours) {
			try {
				if (this.discovery.findRequest(userId, requestId).status().isOpen()) {
					this.discovery.finishRequest(userId, requestId, false, reason);
				}
			} catch (JobWardenException gone) {
				log.debug("Request {} of profile {} is gone: {}", requestId, userId, gone.getMessage());
			}
		}
	}

	/** A run's longest, as a person says it: 60 minutes, 30 seconds. */
	private static String duration(Duration duration) {
		return (duration.toMinutes() >= 1) ? duration.toMinutes() + " minutes" : duration.toSeconds() + " seconds";
	}

	/** The last line the run printed that says something, e.g. Not logged in · Please run /login. */
	private static Optional<String> lastLine(Path output) {
		try (Stream<String> lines = Files.lines(output, StandardCharsets.UTF_8)) {
			return lines.map(String::strip)
				.filter(line -> !line.isEmpty())
				.reduce((first, second) -> second)
				.map(line -> (line.length() <= REASON_MAX_LENGTH) ? line : line.substring(0, REASON_MAX_LENGTH - 1) + "…");
		} catch (IOException | RuntimeException unreadable) {
			return Optional.empty();
		}
	}

	/**
	 * Where the command is: as given when it's a path, else on the PATH or in a usual place, as the shell would find it
	 * (on Windows, with .cmd or .exe after it).
	 */
	static Optional<Path> find(String command) {
		if (command.isBlank()) {
			return Optional.empty();
		}
		Path given = Path.of(expand(command));
		if (given.isAbsolute()) {
			return Files.isExecutable(given) ? Optional.of(given) : Optional.empty();
		}
		for (String directory : directories(System.getenv("PATH"))) {
			for (String name : List.of(command, command + ".cmd", command + ".exe")) {
				Path candidate = Path.of(directory, name);
				if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
					return Optional.of(candidate);
				}
			}
		}
		return Optional.empty();
	}

	/** The PATH's directories, then the usual ones, each once. */
	private static Set<String> directories(@Nullable String path) {
		Set<String> directories = new LinkedHashSet<>();
		if (path != null) {
			for (String directory : path.split(File.pathSeparator)) {
				if (!directory.isBlank()) {
					directories.add(directory);
				}
			}
		}
		USUAL_DIRECTORIES.stream().map(AssistantRunner::expand).forEach(directories::add);
		return directories;
	}

	private static String path(@Nullable Path first, @Nullable String path) {
		Set<String> directories = new LinkedHashSet<>();
		if (first != null) {
			directories.add(first.toString());
		}
		directories.addAll(directories(path));
		return String.join(File.pathSeparator, directories);
	}

	private static String expand(String path) {
		return path.startsWith("~/") ? System.getProperty("user.home") + path.substring(1) : path;
	}

}
