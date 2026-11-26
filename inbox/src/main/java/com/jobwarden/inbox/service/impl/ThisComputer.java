package com.jobwarden.inbox.service.impl;

import com.jobwarden.common.runtime.Containers;
import lombok.CustomLog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Where a mail server on the computer JobWarden runs on answers, such as Proton Mail Bridge, which listens on the
 * computer's own address alone. Run straight on the computer, that's 127.0.0.1. In a container, 127.0.0.1 is the
 * container itself, and the computer is host.docker.internal, as Docker Desktop, OrbStack and Podman all name it.
 * <p>
 * A container is told as {@link Containers} tells it.
 */
@Component
@CustomLog
class ThisComputer {

	static final String LOOPBACK = "127.0.0.1";

	static final String FROM_A_CONTAINER = "host.docker.internal";

	private final boolean inContainer;

	@Autowired
	ThisComputer() {
		this(Containers.inside());
		if (this.inContainer) {
			log.info("Running in a container: a mail server on this computer is reached at {}", FROM_A_CONTAINER);
		}
	}

	ThisComputer(boolean inContainer) {
		this.inContainer = inContainer;
	}

	boolean inContainer() {
		return this.inContainer;
	}

	/**
	 * @return the address of a mail server on the computer: host.docker.internal in a container, else 127.0.0.1
	 */
	String host() {
		return this.inContainer ? FROM_A_CONTAINER : LOOPBACK;
	}

}
