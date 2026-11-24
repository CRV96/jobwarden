package com.jobwarden.web.dto;

import com.jobwarden.cv.dto.CvDetails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * The CV page's name and contact details, asked on their own: by the setup guide, and on a job before its CV is
 * tailored, since every tailored CV shows them. The name and the email are the least a CV sent out needs.
 */
public record ContactsForm(
		@NotBlank(message = "{validation.contacts.name}")
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String fullName,
		@NotBlank(message = "{validation.contacts.email}") @Email(message = "{validation.cv.email}")
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String email,
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String phone,
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String location,
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String website,
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String linkedin,
		@Size(max = CvForm.TEXT_MAX_LENGTH, message = "{validation.cv.size}") @Nullable String github) {

	/**
	 * @return a CV with only these fields, as typed: the service takes them, and tidies them
	 */
	public CvDetails toCvDetails() {
		return CvDetails.builder()
			.fullName(this.fullName)
			.email(this.email)
			.phone(this.phone)
			.location(this.location)
			.website(this.website)
			.linkedin(this.linkedin)
			.github(this.github)
			.build();
	}

}
