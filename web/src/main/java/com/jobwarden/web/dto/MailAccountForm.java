package com.jobwarden.web.dto;

import com.jobwarden.inbox.dto.NewMailAccount;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * The inbox page's mailbox form. The password is never shown back, not even when the form comes back with an error.
 *
 * @param folder where the recruiters' emails arrive; INBOX when left empty
 */
public record MailAccountForm(@NotBlank(message = "{validation.mail.host.required}") @Nullable String host,
		@NotNull(message = "{validation.mail.port.range}") @Min(value = 1, message = "{validation.mail.port.range}")
		@Max(value = 65_535, message = "{validation.mail.port.range}") @Nullable Integer port,
		@NotBlank(message = "{validation.mail.username.required}") @Nullable String username,
		@NotBlank(message = "{validation.mail.password.required}") @Nullable String password,
		@Nullable String folder) {

	public NewMailAccount toNewMailAccount() {
		String inFolder = FormValues.blankToNull(this.folder);
		return new NewMailAccount(this.host.strip(), this.port, this.username.strip(), this.password,
				(inFolder != null) ? inFolder : "INBOX");
	}

}
