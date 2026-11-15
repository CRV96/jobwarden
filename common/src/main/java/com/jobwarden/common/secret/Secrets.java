package com.jobwarden.common.secret;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

/**
 * Encrypts what the server keeps but must never keep as typed (AES-GCM), so it never sits in the database, or a copy of
 * it, readable: mailbox passwords, GitHub tokens. The key is the one configured (Base64, 32 bytes) when there's one;
 * else a key file, made the first time with 32 random bytes and readable by its owner alone. Every module that keeps a
 * secret uses the same key: losing it means entering them all again.
 * <p>
 * Not a bean: the common module has none. Each module makes its own, from {@code jobwarden.secret-key} and
 * {@code jobwarden.key-file}.
 */
@CustomLog
public class Secrets {

	private static final int KEY_BYTES = 32;

	private static final int IV_BYTES = 12;

	private static final int TAG_BITS = 128;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final String configuredKey;

	private final Path keyFile;

	/** Read the first time a secret is: a server that keeps none never makes a key file. */
	private volatile @Nullable SecretKey key;

	/**
	 * @param configuredKey 32 bytes in Base64, or empty for the key file's
	 * @param keyFile read, or made the first time, when no key is configured
	 * @throws IllegalStateException when the configured key isn't 32 bytes in Base64: the app doesn't start with it
	 */
	public Secrets(String configuredKey, Path keyFile) {
		this.configuredKey = configuredKey.strip();
		this.keyFile = keyFile;
		if (!this.configuredKey.isEmpty()) {
			this.key = new SecretKeySpec(decode(this.configuredKey), "AES");
		}
	}

	/**
	 * @return the random IV and the encrypted text after it, in Base64
	 */
	public String encrypt(String text) {
		try {
			byte[] iv = new byte[IV_BYTES];
			RANDOM.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
			byte[] sealed = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array());
		} catch (GeneralSecurityException failed) {
			throw new IllegalStateException("Couldn't encrypt", failed);
		}
	}

	/**
	 * @throws IllegalStateException when it wasn't encrypted with this key, e.g. the key file was lost
	 */
	public String decrypt(String encrypted) {
		try {
			byte[] all = Base64.getDecoder().decode(encrypted);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
			return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException failed) {
			throw new IllegalStateException("Couldn't decrypt: the key isn't the one it was encrypted with", failed);
		}
	}

	private SecretKey key() {
		SecretKey known = this.key;
		if (known == null) {
			synchronized (this) {
				known = this.key;
				if (known == null) {
					known = new SecretKeySpec(this.configuredKey.isEmpty() ? fileKey() : decode(this.configuredKey), "AES");
					this.key = known;
				}
			}
		}
		return known;
	}

	private static byte[] decode(String key) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(key);
		} catch (IllegalArgumentException notBase64) {
			throw new IllegalStateException("jobwarden.secret-key must be " + KEY_BYTES + " bytes in Base64", notBase64);
		}
		if (bytes.length != KEY_BYTES) {
			throw new IllegalStateException("jobwarden.secret-key must be " + KEY_BYTES + " bytes in Base64");
		}
		return bytes;
	}

	/**
	 * The key file's key, or a new one written to it. Only ever made new: two modules making one at the same moment
	 * both end up with the one written first.
	 */
	private byte[] fileKey() {
		try {
			if (Files.exists(this.keyFile)) {
				return readKey();
			}
			byte[] bytes = new byte[KEY_BYTES];
			RANDOM.nextBytes(bytes);
			Files.createDirectories(this.keyFile.toAbsolutePath().getParent());
			if (!writeNewKey(bytes)) {
				return readKey();
			}
			keepToTheOwner();
			log.info("Made the key JobWarden encrypts passwords and tokens with, in {}", this.keyFile);
			return bytes;
		} catch (IOException failed) {
			throw new UncheckedIOException("Couldn't read or make the key file " + this.keyFile, failed);
		}
	}

	private byte[] readKey() throws IOException {
		return decode(Files.readString(this.keyFile).strip());
	}

	/**
	 * @return {@code false} if another module wrote one meanwhile
	 */
	private boolean writeNewKey(byte[] bytes) throws IOException {
		try {
			Files.writeString(this.keyFile, Base64.getEncoder().encodeToString(bytes), StandardOpenOption.CREATE_NEW,
					StandardOpenOption.WRITE);
			return true;
		} catch (FileAlreadyExistsException madeMeanwhile) {
			return false;
		}
	}

	private void keepToTheOwner() throws IOException {
		try {
			Files.setPosixFilePermissions(this.keyFile, PosixFilePermissions.fromString("rw-------"));
		} catch (UnsupportedOperationException notPosix) {
			// Windows: the user's own folder keeps it from the others
		}
	}

}
