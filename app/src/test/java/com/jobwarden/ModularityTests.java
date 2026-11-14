package com.jobwarden;

import org.junit.jupiter.api.Test;

import org.springframework.modulith.core.ApplicationModules;

/**
 * Fails the build if a module uses another module's internals (anything below its root package)
 * or if module dependencies form a cycle.
 */
class ModularityTests {

	@Test
	void verifiesModuleBoundaries() {
		ApplicationModules.of(JobWardenApplication.class).verify();
	}

}
