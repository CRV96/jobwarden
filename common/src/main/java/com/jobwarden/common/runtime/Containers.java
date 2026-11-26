package com.jobwarden.common.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Whether JobWarden runs in a container (Docker, OrbStack, Podman), where 127.0.0.1 is the container itself and the
 * computer's own programs aren't there: told by the file the engine puts at the container's root, {@code /.dockerenv}
 * (Docker, OrbStack) or {@code /run/.containerenv} (Podman).
 */
public final class Containers {

	private static final List<Path> MARKERS = List.of(Path.of("/.dockerenv"), Path.of("/run/.containerenv"));

	/** Read once: a process doesn't move in or out of a container. */
	private static final boolean INSIDE = MARKERS.stream().anyMatch(Files::exists);

	private Containers() {
	}

	public static boolean inside() {
		return INSIDE;
	}

}
