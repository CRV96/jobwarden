package com.jobwarden;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * With scheduling on, for what the modules do now and then: deleting the reminders that are over, checking the
 * mailboxes for recruiters' emails.
 */
@SpringBootApplication
@EnableScheduling
public class JobWardenApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobWardenApplication.class, args);
	}

}
