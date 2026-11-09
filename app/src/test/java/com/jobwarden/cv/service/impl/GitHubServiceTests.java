package com.jobwarden.cv.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvExperience;
import com.jobwarden.cv.dto.CvProject;
import com.jobwarden.cv.dto.GitHubProject;
import com.jobwarden.cv.dto.GitHubRepository;
import com.jobwarden.cv.exception.CvErrorCode;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.cv.service.GitHubService;
import com.jobwarden.cv.service.impl.GitHubApi.GitHubFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * With the cv module, and no GitHub: what GitHub answers is the test's own. In the module's internal package, to give
 * it.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class GitHubServiceTests {

	private static final GitHubRepository JOBWARDEN = new GitHubRepository("CRV96/jobwarden", "jobwarden", null, "Java", 3,
			false, true, Instant.now(), "https://github.com/CRV96/jobwarden");

	@Autowired
	private GitHubService github;

	@Autowired
	private CvService cvs;

	@Autowired
	private JdbcClient jdbc;

	@MockitoBean
	private GitHubApi api;

	private long userId;

	private long otherUserId;

	@BeforeEach
	void createProfiles() {
		this.jdbc.sql("TRUNCATE users CASCADE").update();
		this.userId = insertProfile("Robert");
		this.otherUserId = insertProfile("Someone else");
	}

	@Test
	void keepsTheTokensOwnerAndTheTokenEncrypted() {
		given(this.api.tokenOwner("github_pat_1234")).willReturn(Optional.of("CRV96"));

		assertThat(this.github.connect(this.userId, "typo", " github_pat_1234 ")).satisfies(account -> {
			assertThat(account.username()).isEqualTo("CRV96");
			assertThat(account.hasToken()).isTrue();
			assertThat(account.repositories()).isEmpty();
		});
		assertThat(this.jdbc.sql("SELECT token FROM github_accounts").query(String.class).single()).isNotBlank()
			.doesNotContain("1234");
		// Asked again with the token, as typed
		given(this.api.repositories("CRV96", "github_pat_1234")).willReturn(List.of(JOBWARDEN));
		assertThat(this.github.listRepositories(this.userId)).containsExactly(JOBWARDEN);
		assertThat(this.github.findAccount(this.otherUserId)).isEmpty();
	}

	@Test
	void readsThePublicRepositoriesOfAUserName() {
		given(this.api.user("crv96")).willReturn(Optional.of("CRV96"));

		assertThat(this.github.connect(this.userId, "@crv96", "")).satisfies(account -> {
			assertThat(account.username()).isEqualTo("CRV96");
			assertThat(account.hasToken()).isFalse();
		});
	}

	@Test
	void saysWhyItCouldntConnect() {
		given(this.api.user(anyString())).willReturn(Optional.empty());
		given(this.api.tokenOwner(anyString())).willReturn(Optional.empty());

		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(() -> this.github.connect(this.userId, "nobody", null))
			.satisfies(refused -> assertThat(refused.getErrorCode()).isEqualTo(CvErrorCode.GITHUB_USER_NOT_FOUND));
		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.github.connect(this.userId, "CRV96", "expired"))
			.satisfies(refused -> assertThat(refused.getErrorCode()).isEqualTo(CvErrorCode.GITHUB_TOKEN_REFUSED));
		// Not a user name GitHub could have: never asked
		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.github.connect(this.userId, "../repos/x", null));
		then(this.api).should(never()).user("../repos/x");
		assertThat(this.github.findAccount(this.userId)).isEmpty();
	}

	@Test
	void readsOnlyTheRepositoriesChosen() {
		connect();
		GitHubProject project = new GitHubProject("CRV96/jobwarden", "jobwarden", "https://github.com/CRV96/jobwarden", null,
				"A self-hosted job tracker", List.of("Java"), List.of(), 3, false, null, null, "# JobWarden");
		given(this.api.project("CRV96/jobwarden", null)).willReturn(Optional.of(project));
		given(this.api.project("CRV96/gone", null)).willReturn(Optional.empty());

		this.github.chooseRepositories(this.userId, List.of("CRV96/jobwarden", " CRV96/gone", "CRV96/jobwarden"));

		assertThat(this.github.findAccount(this.userId).orElseThrow().repositories()).containsExactly("CRV96/jobwarden",
				"CRV96/gone");
		assertThat(this.github.findProjects(this.userId)).containsExactly(project);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.github.chooseRepositories(this.userId, List.of("CRV96/../../user")));
		assertThatIllegalArgumentException().isThrownBy(() -> this.github.chooseRepositories(this.userId,
				IntStream.range(0, 13).mapToObj(number -> "CRV96/repo" + number).toList()));
	}

	@Test
	void saysWhenGitHubCantBeAsked() {
		connect();
		given(this.api.repositories(any(), any())).willThrow(new GitHubFailure("GitHub answered 503"));

		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(() -> this.github.listRepositories(this.userId))
			.satisfies(refused -> {
				assertThat(refused.getErrorCode()).isEqualTo(CvErrorCode.GITHUB_NOT_REACHED);
				assertThat(refused.getArguments()).containsExactly("GitHub answered 503");
			});
		this.github.disconnect(this.userId);
		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(() -> this.github.findProjects(this.userId))
			.satisfies(refused -> assertThat(refused.getErrorCode()).isEqualTo(CvErrorCode.NO_GITHUB));
	}

	@Test
	void putsTheProjectsOnTheCvKeepingTheRest() {
		this.cvs.saveDetails(this.userId, CvDetails.builder()
			.fullName("Robert Ciapă")
			.experience(List.of(new CvExperience("Java Developer", "Acme", null, null, null, null)))
			.projects(List.of(new CvProject("Old one", null, null, null)))
			.build());

		CvDetails saved = this.cvs.saveProjects(this.userId, List.of(
				new CvProject(" JobWarden ", "https://github.com/CRV96/jobwarden", "Java, Spring Boot", "A job tracker."),
				new CvProject(" ", null, null, "No name: left out")));

		assertThat(saved.fullName()).isEqualTo("Robert Ciapă");
		assertThat(saved.experience()).hasSize(1);
		assertThat(this.cvs.find(this.userId).details().projects()).containsExactly(
				new CvProject("JobWarden", "https://github.com/CRV96/jobwarden", "Java, Spring Boot", "A job tracker."));
	}

	private void connect() {
		given(this.api.user("CRV96")).willReturn(Optional.of("CRV96"));
		this.github.connect(this.userId, "CRV96", null);
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id").param(name).query(Long.class).single();
	}

}
