package com.jobwarden.cv.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.GitHubAccount;
import com.jobwarden.cv.dto.GitHubProject;
import com.jobwarden.cv.dto.GitHubRepository;
import com.jobwarden.cv.entity.GitHubAccountEntity;
import com.jobwarden.cv.exception.CvErrorCode;
import com.jobwarden.cv.repository.GitHubAccountRepository;
import com.jobwarden.cv.service.GitHubService;
import com.jobwarden.cv.service.impl.GitHubApi.GitHubFailure;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asking GitHub happens outside a transaction where it can; the token is decrypted only to ask.
 */
@Service
@CustomLog
@RequiredArgsConstructor
class DefaultGitHubService implements GitHubService {

	/** GitHub's user names: letters, digits and single hyphens, up to 39. */
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]{0,38}");

	/** owner/name, nothing that could reach another part of the API. */
	private static final Pattern FULL_NAME = Pattern.compile("[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}");

	private final GitHubAccountRepository accounts;

	private final GitHubApi github;

	private final GitHubTokens tokens;

	@Override
	@Transactional(readOnly = true)
	public Optional<GitHubAccount> findAccount(long userId) {
		return this.accounts.findById(userId).map(DefaultGitHubService::toAccount);
	}

	/**
	 * @throws BusinessRuleException if GitHub has no such user, refuses the token, or can't be reached
	 */
	@Override
	@Transactional
	public GitHubAccount connect(long userId, String username, @Nullable String token) {
		String name = username.strip().replaceFirst("^@", "");
		String key = (token != null && !token.isBlank()) ? token.strip() : null;
		String login;
		try {
			if (key != null) {
				login = this.github.tokenOwner(key)
					.orElseThrow(() -> new BusinessRuleException(CvErrorCode.GITHUB_TOKEN_REFUSED, "GitHub refused the token"));
			} else {
				if (!USERNAME.matcher(name).matches()) {
					throw new BusinessRuleException(CvErrorCode.GITHUB_USER_NOT_FOUND, "Not a GitHub user name", name);
				}
				login = this.github.user(name)
					.orElseThrow(() -> new BusinessRuleException(CvErrorCode.GITHUB_USER_NOT_FOUND, "No GitHub user " + name,
							name));
			}
		} catch (GitHubFailure failed) {
			throw unreachable(failed);
		}
		GitHubAccountEntity account = this.accounts.findById(userId).orElseGet(() -> new GitHubAccountEntity(userId));
		account.connect(login, (key != null) ? this.tokens.encrypt(key) : null);
		log.info("Connected the GitHub account {} to profile {}{}", login, userId, (key != null) ? ", with a token" : "");
		return toAccount(this.accounts.save(account));
	}

	@Override
	@Transactional
	public void disconnect(long userId) {
		if (this.accounts.existsById(userId)) {
			this.accounts.deleteById(userId);
			log.info("Disconnected the GitHub account of profile {}", userId);
		}
	}

	/**
	 * @throws BusinessRuleException if the profile has no account, or GitHub can't be reached
	 */
	@Override
	public List<GitHubRepository> listRepositories(long userId) {
		GitHubAccountEntity account = account(userId);
		try {
			return this.github.repositories(account.getUsername(), token(account));
		} catch (GitHubFailure failed) {
			throw unreachable(failed);
		}
	}

	@Override
	@Transactional
	public GitHubAccount chooseRepositories(long userId, List<String> fullNames) {
		List<String> chosen = fullNames.stream().map(String::strip).filter(name -> !name.isEmpty()).distinct().toList();
		if (chosen.size() > MAX_REPOSITORIES) {
			throw new IllegalArgumentException("At most " + MAX_REPOSITORIES + " repositories, not " + chosen.size());
		}
		for (String name : chosen) {
			if (!FULL_NAME.matcher(name).matches()) {
				throw new IllegalArgumentException("Not owner/name: " + name);
			}
		}
		GitHubAccountEntity account = account(userId);
		account.choose(chosen);
		log.info("Profile {} chose {} GitHub repositories for its CV", userId, chosen.size());
		return toAccount(account);
	}

	/**
	 * @throws BusinessRuleException if the profile has no account, or GitHub can't be reached
	 */
	@Override
	public List<GitHubProject> findProjects(long userId) {
		GitHubAccountEntity account = account(userId);
		String token = token(account);
		List<GitHubProject> projects = new ArrayList<>();
		try {
			for (String name : account.getRepositories()) {
				this.github.project(name, token).ifPresent(projects::add);
			}
		} catch (GitHubFailure failed) {
			throw unreachable(failed);
		}
		return projects;
	}

	private GitHubAccountEntity account(long userId) {
		return this.accounts.findById(userId)
			.orElseThrow(() -> new BusinessRuleException(CvErrorCode.NO_GITHUB, "Profile " + userId + " has no GitHub account"));
	}

	private @Nullable String token(GitHubAccountEntity account) {
		return (account.getToken() != null) ? this.tokens.decrypt(account.getToken()) : null;
	}

	private static BusinessRuleException unreachable(GitHubFailure failed) {
		log.warn("GitHub couldn't be asked: {}", failed.getMessage());
		return new BusinessRuleException(CvErrorCode.GITHUB_NOT_REACHED, "GitHub couldn't be asked", failed.getMessage());
	}

	private static GitHubAccount toAccount(GitHubAccountEntity account) {
		return new GitHubAccount(account.getUsername(), account.getToken() != null, account.getRepositories(),
				account.getConnectedAt());
	}

}
