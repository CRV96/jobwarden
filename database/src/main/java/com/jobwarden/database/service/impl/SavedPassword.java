package com.jobwarden.database.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import com.jobwarden.common.secret.Secrets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.PropertyResolver;
import org.springframework.stereotype.Component;

/**
 * The database password changed in Settings, kept for the next start: encrypted with the key every module's secrets
 * share ({@code jobwarden.secret-key}, else the key file), in a file only its owner can read. By default it's next to
 * the key file, in {@code ~/.jobwarden}, which in Docker is a volume: an upgrade keeps it.
 */
@Component
class SavedPassword {

	/** Where it's kept: {@code jobwarden.database.password-file} */
	static final String FILE = "${jobwarden.database.password-file:${user.home}/.jobwarden/database.password}";

	/** The key, as the inbox's and the CV's secrets name it */
	static final String KEY = "${jobwarden.secret-key:${jobwarden.inbox.secret-key:}}";

	/** The key file, as the inbox's and the CV's secrets name it */
	static final String KEY_FILE = "${jobwarden.key-file:${jobwarden.inbox.key-file:${user.home}/.jobwarden/inbox.key}}";

	private final Path file;

	private final Secrets secrets;

	SavedPassword(@Value(FILE) Path file, @Value(KEY) String key, @Value(KEY_FILE) Path keyFile) {
		this.file = file;
		this.secrets = new Secrets(key, keyFile);
	}

	/**
	 * The same, from the settings themselves: for the data source's post-processor, made before the beans that
	 * {@code @Value} fills.
	 */
	static SavedPassword of(PropertyResolver settings) {
		return new SavedPassword(Path.of(settings.resolvePlaceholders(FILE)), settings.resolvePlaceholders(KEY),
				Path.of(settings.resolvePlaceholders(KEY_FILE)));
	}

	Path file() {
		return this.file;
	}

	/**
	 * @return the password kept, or empty when none is
	 * @throws IllegalStateException when the file isn't encrypted with this key, e.g. the key file was lost
	 * @throws UncheckedIOException when the file is there but can't be read
	 */
	Optional<String> read() {
		if (!Files.exists(this.file)) {
			return Optional.empty();
		}
		try {
			return Optional.of(this.secrets.decrypt(Files.readString(this.file).strip()));
		} catch (IOException failed) {
			throw new UncheckedIOException("Couldn't read " + this.file, failed);
		}
	}

	/**
	 * Writes it to a new file next to the old one, readable by its owner alone, then puts that in the old one's place:
	 * a failure leaves the old one whole.
	 *
	 * @throws IllegalStateException when the key can't be read or made
	 */
	void save(String password) throws IOException {
		Path folder = this.file.toAbsolutePath().getParent();
		Files.createDirectories(folder);
		String encrypted = this.secrets.encrypt(password);
		// Owner only, as a temporary file is on POSIX systems; on Windows, the user's own folder keeps it
		Path written = Files.createTempFile(folder, ".database-", ".password");
		try {
			Files.writeString(written, encrypted);
			Files.move(written, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(written);
		}
	}

	void delete() throws IOException {
		Files.deleteIfExists(this.file);
	}

}
