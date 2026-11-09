package com.jobwarden.identity.mapper;

import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.entity.UserEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Converts entities to the records the service returns. Entities never leave the module.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserMapper {

	public static User toUser(UserEntity user) {
		return new User(user.getId(), user.getName(), user.getCreatedAt(), user.getImageChangedAt());
	}

}
