package com.jobwarden;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * The application: every module, wired together. Scheduling is set up in {@link SchedulingConfiguration}.
 */
@SpringBootApplication
public class JobWardenApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobWardenApplication.class, args);
	}

	/**
	 * What every module takes as "now": the system's clock, in the server's time zone (set {@code TZ} in Docker, which
	 * runs in UTC). A test that needs a moment of its own gives its class a fixed one ({@code Clock.fixed}).
	 */
	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

}
