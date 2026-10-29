package com.jobwarden.hiring.entity;

import java.time.Instant;

import com.jobwarden.hiring.enums.RecruiterOrigin;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "recruiters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruiterEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private String name;

	private @Nullable String headline;

	private String profileUrl;

	private @Nullable String company;

	private @Nullable String country;

	private @Nullable String note;

	@Enumerated(EnumType.STRING)
	private RecruiterOrigin origin;

	private boolean followed;

	/** Removed by the profile: never shown, nor added again */
	private boolean hidden;

	@CreationTimestamp
	private Instant createdAt;

	public RecruiterEntity(long userId, String name, @Nullable String headline, String profileUrl, RecruiterOrigin origin) {
		this.userId = userId;
		this.name = name;
		this.headline = headline;
		this.profileUrl = profileUrl;
		this.origin = origin;
	}

	/**
	 * @param headline the new one, or {@code null} to keep the one there is
	 */
	public void rename(String name, @Nullable String headline) {
		this.name = name;
		if (headline != null) {
			this.headline = headline;
		}
	}

	/**
	 * Fills in what's known of them; each {@code null} keeps what there is.
	 */
	public void describe(@Nullable String company, @Nullable String country, @Nullable String note) {
		this.company = (company != null) ? company : this.company;
		this.country = (country != null) ? country : this.country;
		this.note = (note != null) ? note : this.note;
	}

	/**
	 * Fills in what isn't known yet, and leaves what is.
	 */
	public void complete(@Nullable String headline, @Nullable String company) {
		this.headline = (this.headline != null) ? this.headline : headline;
		this.company = (this.company != null) ? this.company : company;
	}

	public void follow(boolean followed) {
		this.followed = followed;
	}

	/**
	 * Removes them from the page, for good: they're no longer followed, nor added again.
	 */
	public void hide() {
		this.hidden = true;
		this.followed = false;
	}

	/**
	 * Brings them back, e.g. added by hand after they were removed.
	 */
	public void show() {
		this.hidden = false;
	}

}
