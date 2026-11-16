package com.jobwarden.jobs.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * The secret a profile's calendar feed is read with: calendar apps send it in the feed's address.
 */
@Entity
@Table(name = "calendar_feeds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class CalendarFeedEntity {

	@Id
	private Long userId;

	private String token;

	@CreationTimestamp
	private Instant createdAt;

	public CalendarFeedEntity(long userId, String token) {
		this.userId = userId;
		this.token = token;
	}

	/**
	 * A new secret: the address with the old one stops working.
	 */
	public void changeToken(String token) {
		this.token = token;
	}

}
