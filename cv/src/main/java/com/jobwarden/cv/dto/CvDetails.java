package com.jobwarden.cv.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * The fields a CV is built from. Every field is optional; the lists are in the order the CV shows them. Build it with
 * {@link #builder()}.
 *
 * @param headline what the person does, under the name, e.g. Senior Java Developer
 * @param skills one skill or keyword each, e.g. Java, Spring Boot, Kafka
 * @param updatedAt when they were last saved; {@code null} before they ever were
 */
@Builder
public record CvDetails(@Nullable String fullName, @Nullable String headline, @Nullable String email,
		@Nullable String phone, @Nullable String location, @Nullable String website, @Nullable String linkedin,
		@Nullable String github, @Nullable String summary, List<String> skills, List<CvExperience> experience,
		List<CvEducation> education, List<CvLanguage> languages, List<CvCertification> certifications,
		@Nullable Instant updatedAt) {

	/** No field filled in. */
	public static final CvDetails NONE = CvDetails.builder().build();

	public CvDetails {
		skills = (skills != null) ? List.copyOf(skills) : List.of();
		experience = (experience != null) ? List.copyOf(experience) : List.of();
		education = (education != null) ? List.copyOf(education) : List.of();
		languages = (languages != null) ? List.copyOf(languages) : List.of();
		certifications = (certifications != null) ? List.copyOf(certifications) : List.of();
	}

	/**
	 * @return how to reach the person, as the CV's contact line shows it: the location, email, phone and addresses
	 * that are filled in
	 */
	public List<String> contacts() {
		return Stream.of(this.location, this.email, this.phone, this.website, this.linkedin, this.github)
			.filter(Objects::nonNull)
			.toList();
	}

	/**
	 * @return whether there's enough to build a CV from: at least a name, a job or a degree
	 */
	public boolean isFilledIn() {
		return this.fullName != null || !this.experience.isEmpty() || !this.education.isEmpty();
	}

}
