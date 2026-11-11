package com.jobwarden.identity.service;

import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.Assistant;

import java.util.List;
import java.util.Optional;

public interface UserService {

	/** The largest photo file, in bytes: one straight from a phone fits */
	int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

	User create(String name);

	List<User> findAll();

	Optional<User> findById(long id);

	boolean exists(long id);

	/**
	 * Makes the image the profile's photo: the square in its middle, made small.
	 *
	 * @param content a PNG, JPEG, GIF or BMP file
	 * @return the profile, with when its photo changed
	 */
	User changeImage(long id, byte[] content);

	User removeImage(long id);

	/**
	 * Which AI assistant the profile works with: its pages then write their prompts and commands for that one.
	 *
	 * @return the profile, with its assistant
	 */
	User changeAssistant(long id, Assistant assistant);

	/**
	 * @return the profile's photo, a PNG, if it has one
	 */
	Optional<byte[]> findImage(long id);

}
