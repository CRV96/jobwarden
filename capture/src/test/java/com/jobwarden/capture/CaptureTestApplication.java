package com.jobwarden.capture;

import java.time.Clock;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Web tests like {@code @WebMvcTest} start from a {@code @SpringBootApplication} class. The real one lives
 * in the app module, which this module can't see, so this one stands in for it during tests.
 */
@SpringBootApplication
class CaptureTestApplication {

	/** "Now", as the app's own JobWardenApplication gives it to every module. */
	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

}
