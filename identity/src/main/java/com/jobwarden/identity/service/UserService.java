package com.jobwarden.identity.service;

import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.Assistant;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public interface UserService {

	/** The largest photo file, in bytes: one straight from a phone fits */
	int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

	User create(String name);

	List<User> findAll();

	Optional<User> findById(long id);

	/**
	 * The profile a key names, as the browser extension and an assistant's settings give it: its public id (a UUID),
	 * or its id, as given before profiles had one.
	 *
	 * @return nothing for a key that's neither, or names no profile
	 */
	Optional<User> findByKey(@Nullable String key);

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
	 * Gives the profile another name, unique like a new one's, ignoring case.
	 *
	 * @throws com.jobwarden.common.exception.BusinessRuleException USER_NAME_TAKEN when another profile has it
	 * @return the profile, with its name
	 */
	User rename(long id, String name);

	/**
	 * Deletes the profile, and with it everything that is its own, for good: its jobs and their timelines, its CV and
	 * everything written from it, its offers, searches, found jobs, recruiters, inbox, reminders, calendar feed and the
	 * assistant's requests. The database deletes them with it (every table of a profile's cascades from users). The
	 * other profiles, and what the server keeps for all of them (the market's salaries, the Adzuna key), stay.
	 *
	 * @throws com.jobwarden.identity.exception.UserNotFoundException if there's no such profile
	 */
	void delete(long id);

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
