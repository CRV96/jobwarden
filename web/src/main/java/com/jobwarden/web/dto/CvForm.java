package com.jobwarden.web.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.jobwarden.cv.dto.CvCertification;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvEducation;
import com.jobwarden.cv.dto.CvExperience;
import com.jobwarden.cv.dto.CvLanguage;
import com.jobwarden.cv.dto.CvProject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * The CV page's fields. The lists' rows are named {@code experience[0].role}... (app.js numbers them as they're added
 * and removed); rows left empty are dropped by the service.
 *
 * @param skills one per line, or separated by commas
 */
public record CvForm(
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String fullName,
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String headline,
		@Email(message = "{validation.cv.email}")
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String email,
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String phone,
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String location,
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String website,
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String linkedin,
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String github,
		@Size(max = CvForm.LONG_TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String summary,
		@Size(max = CvForm.LONG_TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String skills,
		@Valid @Nullable List<@Nullable ExperienceRow> experience,
		@Valid @Nullable List<@Nullable ProjectRow> projects,
		@Valid @Nullable List<@Nullable EducationRow> education,
		@Valid @Nullable List<@Nullable LanguageRow> languages,
		@Valid @Nullable List<@Nullable CertificationRow> certifications) {

	/** Also the {@code maxlength} of the one-line fields in cv/cv.html. */
	public static final int TEXT_MAX_LENGTH = 200;

	/** Also the {@code maxlength} of the summary, the skills and each entry's description. */
	public static final int LONG_TEXT_MAX_LENGTH = 5000;

	public record ExperienceRow(
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String role,
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String company,
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String location,
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String start,
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String end,
			@Size(max = LONG_TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String description) {
	}

	public record ProjectRow(
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String name,
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String link,
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String technologies,
			@Size(max = LONG_TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String description) {
	}

	public record EducationRow(
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String degree,
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String school,
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String location,
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String start,
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String end,
			@Size(max = LONG_TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String description) {
	}

	public record LanguageRow(
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String language,
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String level) {
	}

	public record CertificationRow(
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String name,
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String issuer,
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String year) {
	}

	/**
	 * As the service takes them: it tidies the text and drops the empty rows.
	 */
	public CvDetails toCvDetails() {
		return CvDetails.builder()
			.fullName(this.fullName)
			.headline(this.headline)
			.email(this.email)
			.phone(this.phone)
			.location(this.location)
			.website(this.website)
			.linkedin(this.linkedin)
			.github(this.github)
			.summary(this.summary)
			.skills((this.skills == null) ? List.of() : Arrays.stream(this.skills.split("[,\\n]")).toList())
			.experience(entries(this.experience, ExperienceRow::role, (row, role) -> new CvExperience(role, row.company(),
					row.location(), row.start(), row.end(), row.description())))
			.projects(entries(this.projects, ProjectRow::name,
					(row, name) -> new CvProject(name, row.link(), row.technologies(), row.description())))
			.education(entries(this.education, EducationRow::degree, (row, degree) -> new CvEducation(degree,
					row.school(), row.location(), row.start(), row.end(), row.description())))
			.languages(entries(this.languages, LanguageRow::language,
					(row, language) -> new CvLanguage(language, row.level())))
			.certifications(entries(this.certifications, CertificationRow::name,
					(row, name) -> new CvCertification(name, row.issuer(), row.year())))
			.build();
	}

	/**
	 * The CV's entries from the rows that have their main field, e.g. a job's role: one without it isn't an entry.
	 */
	private static <R, E> List<E> entries(@Nullable List<@Nullable R> rows, Function<R, @Nullable String> mainField,
			BiFunction<R, String, E> entry) {
		return rows(rows).stream().<E>mapMulti((row, kept) -> {
			String main = mainField.apply(row);
			if (main != null) {
				kept.accept(entry.apply(row, main));
			}
		}).toList();
	}

	/** The rows sent: a removed row can leave a gap, which the list has as {@code null}. */
	private static <T> List<T> rows(@Nullable List<@Nullable T> rows) {
		return (rows == null) ? List.of() : rows.stream().filter(Objects::nonNull).toList();
	}

}
