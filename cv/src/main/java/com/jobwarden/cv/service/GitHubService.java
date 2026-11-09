package com.jobwarden.cv.service;

import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.dto.GitHubAccount;
import com.jobwarden.cv.dto.GitHubProject;
import com.jobwarden.cv.dto.GitHubRepository;
import org.jspecify.annotations.Nullable;

/**
 * The profile's GitHub account, for the CV's personal projects: the repositories it chooses are what Claude writes
 * them from. A user name reads the public repositories; a token the private ones it was given too, which is how the
 * profile decides what Claude may read. Every method takes the owning user's id.
 */
public interface GitHubService {

	/** How many repositories the profile can choose: a CV shows a few projects. */
	int MAX_REPOSITORIES = 12;

	Optional<GitHubAccount> findAccount(long userId);

	/**
	 * Checks the account with GitHub, then keeps it in place of the one there was, the token encrypted. With a token,
	 * the account is the token's owner.
	 *
	 * @param token a fine-grained token, read-only, for the repositories the profile picks; {@code null} for the public
	 * repositories alone
	 * @throws com.jobwarden.common.exception.BusinessRuleException if GitHub has no such user, refuses the token, or
	 * can't be reached
	 */
	GitHubAccount connect(long userId, String username, @Nullable String token);

	void disconnect(long userId);

	/**
	 * @return the account's repositories, the last pushed to first: what GitHub lists for its user name, or for its
	 * token
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the profile has no account, or GitHub can't be
	 * reached
	 */
	List<GitHubRepository> listRepositories(long userId);

	/**
	 * @param fullNames owner/name each, at most {@value #MAX_REPOSITORIES}
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the profile has no account
	 * @throws IllegalArgumentException for more, or a name that isn't owner/name
	 */
	GitHubAccount chooseRepositories(long userId, List<String> fullNames);

	/**
	 * @return the repositories chosen, with their details, languages and the start of their README, for Claude to
	 * write the CV's projects from; those gone since are left out
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the profile has no account, or GitHub can't be
	 * reached
	 */
	List<GitHubProject> findProjects(long userId);

}
