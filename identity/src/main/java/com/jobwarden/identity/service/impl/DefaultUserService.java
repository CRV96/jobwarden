package com.jobwarden.identity.service.impl;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.common.image.Images;
import com.jobwarden.common.image.Images.TooLargeImageException;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.entity.ProfileImageEntity;
import com.jobwarden.identity.entity.UserEntity;
import com.jobwarden.identity.enums.IdentityErrorCode;
import com.jobwarden.identity.exception.UserNotFoundException;
import com.jobwarden.identity.mapper.UserMapper;
import com.jobwarden.identity.repository.ProfileImageRepository;
import com.jobwarden.identity.repository.UserRepository;
import com.jobwarden.identity.service.UserService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logs by profile id only: names are personal data and stay out of the logs.
 */
@Service
@Transactional(readOnly = true)
@CustomLog
@RequiredArgsConstructor
class DefaultUserService implements UserService {

	private static final Sort BY_NAME = Sort.by(Sort.Order.asc("name").ignoreCase());

	/** The photo's side in pixels: the largest avatar, on a screen with twice the pixels, and some to spare */
	static final int IMAGE_SIZE = 160;

	private final UserRepository users;

	private final ProfileImageRepository images;

	/**
	 * @throws BusinessRuleException if a profile already has this name, ignoring case
	 */
	@Override
	@Transactional
	public User create(String name) {
		String trimmedName = name.strip();
		if (this.users.existsByNameIgnoreCase(trimmedName)) {
			throw new BusinessRuleException(IdentityErrorCode.USER_NAME_TAKEN, "A profile with this name already exists",
					trimmedName);
		}
		User user = UserMapper.toUser(this.users.save(new UserEntity(trimmedName)));
		log.info("Created profile {}", user.id());
		return user;
	}

	@Override
	public List<User> findAll() {
		List<User> users = this.users.findAll(BY_NAME).stream().map(UserMapper::toUser).toList();
		log.debug("Found {} profiles", users.size());
		return users;
	}

	@Override
	public Optional<User> findById(long id) {
		Optional<User> user = this.users.findById(id).map(UserMapper::toUser);
		log.debug("Profile {} {}", id, user.isPresent() ? "found" : "not found");
		return user;
	}

	@Override
	public boolean exists(long id) {
		return this.users.existsById(id);
	}

	/**
	 * @throws UserNotFoundException if there's no such profile
	 * @throws BusinessRuleException if it's too large, or not an image JobWarden reads
	 */
	@Override
	@Transactional
	public User changeImage(long id, byte[] content) {
		UserEntity user = getUser(id);
		byte[] png = squarePng(content);
		this.images.findById(id).ifPresentOrElse(image -> image.replace(png),
				() -> this.images.save(new ProfileImageEntity(id, png)));
		// To the microsecond, as PostgreSQL keeps it: the profile returned and the one read back say the same moment
		user.imageChanged(Instant.now().truncatedTo(ChronoUnit.MICROS));
		log.info("Changed the photo of profile {}", id);
		return UserMapper.toUser(user);
	}

	/**
	 * @throws UserNotFoundException if there's no such profile
	 */
	@Override
	@Transactional
	public User removeImage(long id) {
		UserEntity user = getUser(id);
		this.images.deleteById(id);
		user.imageChanged(null);
		log.info("Removed the photo of profile {}", id);
		return UserMapper.toUser(user);
	}

	@Override
	public Optional<byte[]> findImage(long id) {
		return this.images.findById(id).map(ProfileImageEntity::getImage);
	}

	private UserEntity getUser(long id) {
		return this.users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
	}

	private static byte[] squarePng(byte[] content) {
		if (content.length > MAX_IMAGE_BYTES) {
			throw new BusinessRuleException(IdentityErrorCode.IMAGE_TOO_LARGE, "The photo has " + content.length + " bytes",
					String.valueOf(MAX_IMAGE_BYTES / (1024 * 1024)));
		}
		try {
			return Images.squarePng(Images.read(content), IMAGE_SIZE);
		} catch (TooLargeImageException tooLarge) {
			throw new BusinessRuleException(IdentityErrorCode.IMAGE_TOO_LARGE, tooLarge.getMessage(),
					String.valueOf(MAX_IMAGE_BYTES / (1024 * 1024)));
		} catch (IOException | RuntimeException unreadable) {
			// Java's image readers throw more than IOException on a broken file
			throw new BusinessRuleException(IdentityErrorCode.IMAGE_UNREADABLE, "The photo isn't an image Java reads: "
					+ unreadable.getMessage());
		}
	}

}
