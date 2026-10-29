package com.jobwarden.hiring;

import java.time.LocalDate;
import java.util.List;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.hiring.dto.AddedHiringPosts;
import com.jobwarden.hiring.dto.AddedRecruiters;
import com.jobwarden.hiring.dto.ContactJob;
import com.jobwarden.hiring.dto.HiringPost;
import com.jobwarden.hiring.dto.HiringPostQuery;
import com.jobwarden.hiring.dto.NewHiringPost;
import com.jobwarden.hiring.dto.NewRecruiter;
import com.jobwarden.hiring.dto.Recruiter;
import com.jobwarden.hiring.enums.PostedWithin;
import com.jobwarden.hiring.enums.RecruiterOrigin;
import com.jobwarden.hiring.exception.HiringErrorCode;
import com.jobwarden.hiring.exception.HiringPostNotFoundException;
import com.jobwarden.hiring.exception.RecruiterNotFoundException;
import com.jobwarden.hiring.service.HiringService;
import com.jobwarden.jobs.dto.FollowUp;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The hiring module starts here with the jobs module, whose contacts are recruiters too, and without identity, so the
 * profiles that own the posts are inserted with SQL.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class HiringServiceTests {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private HiringService hiring;

	@Autowired
	private ApplicationService applications;

	@Autowired
	private JdbcClient jdbc;

	private long userId;

	private long otherUserId;

	@BeforeEach
	void createProfiles() {
		this.jdbc.sql("TRUNCATE users CASCADE").update();
		this.userId = insertProfile("Robert");
		this.otherUserId = insertProfile("Someone else");
	}

	@Test
	void addsEachPostOnceWithItsAuthorAndHashtags() {
		AddedHiringPosts first = this.hiring.addPosts(this.userId, List.of(
				post("https://www.linkedin.com/posts/ana_hiring-java-activity-1?utm_source=share", "Ana Pop",
						"https://ro.linkedin.com/in/ana-pop/", "We're #Hiring a #Java developer in Cluj! #hiring", TODAY),
				post("https://www.linkedin.com/posts/ana_kotlin-activity-2", "Ana Pop", "https://www.linkedin.com/in/ana-pop",
						"Kotlin role, remote. #kotlin", TODAY.minusDays(3))));
		AddedHiringPosts again = this.hiring.addPosts(this.userId, List.of(
				post("https://www.linkedin.com/posts/ana_hiring-java-activity-1", "Ana Pop", "https://www.linkedin.com/in/ana-pop",
						"Again", TODAY),
				post("javascript:alert(1)", "Ana Pop", "https://www.linkedin.com/in/ana-pop", "Not a link", TODAY),
				post("https://x.com/bob/status/3", "Bob", "mailto:bob@example.com", "Not a profile", TODAY)));

		assertThat(first.added()).extracting(HiringPost::link, HiringPost::tags).containsExactly(
				tuple("https://www.linkedin.com/posts/ana_hiring-java-activity-1", List.of("hiring", "java")),
				tuple("https://www.linkedin.com/posts/ana_kotlin-activity-2", List.of("kotlin")));
		assertThat(first.added()).extracting(post -> post.recruiter().profileUrl())
			.containsOnly("https://www.linkedin.com/in/ana-pop");
		assertThat(again.added()).isEmpty();
		assertThat(again.skipped()).hasSize(3);
		assertThat(this.hiring.findRecruiters(this.userId)).singleElement()
			.satisfies(ana -> {
				assertThat(ana.name()).isEqualTo("Ana Pop");
				assertThat(ana.headline()).isEqualTo("IT Recruiter");
				assertThat(ana.posts()).isEqualTo(2);
			});
		assertThat(this.hiring.findPosts(this.otherUserId, HiringPostQuery.ALL)).isEmpty();
	}

	@Test
	void findsThePostsTheQueryAsksForTheLatestFirst() {
		this.hiring.addPosts(this.userId, List.of(
				post("https://example.com/1", "Ana Pop", "https://example.com/ana", "Java in Cluj #java", TODAY.minusDays(20)),
				post("https://example.com/2", "Bob Ionescu", "https://example.com/bob", "Kotlin, remote #kotlin", TODAY),
				post("https://example.com/3", "Ana Pop", "https://example.com/ana", "Senior Java #java", TODAY.minusDays(3))));
		long ana = this.hiring.findRecruiters(this.userId)
			.stream()
			.filter(recruiter -> recruiter.name().equals("Ana Pop"))
			.findFirst()
			.orElseThrow()
			.id();
		this.hiring.follow(this.userId, ana, true);

		assertThat(links(HiringPostQuery.ALL)).containsExactly("https://example.com/2", "https://example.com/3",
				"https://example.com/1");
		assertThat(links(HiringPostQuery.builder().search("JAVA").build())).containsExactly("https://example.com/3",
				"https://example.com/1");
		assertThat(links(HiringPostQuery.builder().postedWithin(PostedWithin.WEEK).build()))
			.containsExactly("https://example.com/2", "https://example.com/3");
		assertThat(links(HiringPostQuery.builder().tag("Kotlin").build())).containsExactly("https://example.com/2");
		assertThat(links(HiringPostQuery.builder().country("ro").build())).containsExactly("https://example.com/2",
				"https://example.com/3", "https://example.com/1");
		assertThat(links(HiringPostQuery.builder().followedOnly(true).build())).containsExactly("https://example.com/3",
				"https://example.com/1");
		assertThat(this.hiring.findRecruiters(this.userId)).extracting(Recruiter::name, Recruiter::followed)
			.containsExactly(tuple("Ana Pop", true), tuple("Bob Ionescu", false));
	}

	@Test
	void hidesAPostForGoodAndKeepsTheJobItWasSavedAs() {
		HiringPost post = this.hiring.addPosts(this.userId,
				List.of(post("https://example.com/1", "Ana Pop", "https://example.com/ana", "Java", TODAY))).added().getFirst();
		long job = this.jdbc.sql("""
				INSERT INTO applications (user_id, title, current_status, captured_at) VALUES (?, 'Java', 'SAVED', now())
				RETURNING id""").param(this.userId).query(Long.class).single();

		assertThat(this.hiring.markSaved(this.userId, post.id(), job).applicationId()).isEqualTo(job);
		assertThatExceptionOfType(HiringPostNotFoundException.class)
			.isThrownBy(() -> this.hiring.hidePost(this.otherUserId, post.id()));

		this.hiring.hidePost(this.userId, post.id());
		assertThat(this.hiring.findPosts(this.userId, HiringPostQuery.ALL)).isEmpty();
		assertThat(this.hiring.findPostLinks(this.userId)).containsExactly("https://example.com/1");
		assertThat(this.hiring.addPosts(this.userId,
				List.of(post("https://example.com/1", "Ana Pop", "https://example.com/ana", "Java", TODAY))).added()).isEmpty();
	}

	@Test
	void addsARecruiterByHandOnceAndFollowsOnlyTheProfilesOwn() {
		Recruiter added = this.hiring.addRecruiter(this.userId, " Ana Pop ", null, "https://www.linkedin.com/in/ana-pop/");
		Recruiter again = this.hiring.addRecruiter(this.userId, "Ana Pop", "IT Recruiter at Acme",
				"https://linkedin.com/in/ana-pop");

		assertThat(again.id()).isEqualTo(added.id());
		assertThat(again.headline()).isEqualTo("IT Recruiter at Acme");
		assertThat(this.hiring.follow(this.userId, added.id(), true).followed()).isTrue();
		assertThatExceptionOfType(RecruiterNotFoundException.class)
			.isThrownBy(() -> this.hiring.follow(this.otherUserId, added.id(), true));
		assertThatThrownBy(() -> this.hiring.addRecruiter(this.userId, "Bob", null, "bob on LinkedIn"))
			.isInstanceOfSatisfying(BusinessRuleException.class,
					refused -> assertThat(refused.getErrorCode()).isEqualTo(HiringErrorCode.PROFILE_LINK_NOT_A_WEB_ADDRESS));
	}

	@Test
	void addsTheRecruitersClaudeFoundOnceWithWhyToFollowThem() {
		this.hiring.addPosts(this.userId,
				List.of(post("https://example.com/1", "Ana Pop", "https://example.com/ana", "Java", TODAY)));

		AddedRecruiters added = this.hiring.addFoundRecruiters(this.userId, List.of(
				found("Bob Ionescu", "https://ro.linkedin.com/in/bob-ionescu/", "Acme", "ro", "Hires Java developers at Acme."),
				found("Ana Pop", "https://example.com/ana", "Globex", "RO", "Already there"),
				found("Not a profile", "bob at Acme", null, null, null),
				found("Carla Dinu", "https://www.linkedin.com/in/carla", "Initech", "Romania", null)));

		assertThat(added.added()).extracting(Recruiter::name, Recruiter::profileUrl, Recruiter::company,
				Recruiter::country, Recruiter::origin, Recruiter::followed)
			.containsExactly(tuple("Bob Ionescu", "https://www.linkedin.com/in/bob-ionescu", "Acme", "RO",
					RecruiterOrigin.FOUND, false),
					tuple("Carla Dinu", "https://www.linkedin.com/in/carla", "Initech", null, RecruiterOrigin.FOUND, false));
		assertThat(added.added().getFirst().note()).isEqualTo("Hires Java developers at Acme.");
		assertThat(added.skipped()).containsExactly("https://example.com/ana", "bob at Acme");
		assertThat(this.hiring.findRecruiters(this.userId)).extracting(Recruiter::name, Recruiter::origin)
			.containsExactlyInAnyOrder(tuple("Ana Pop", RecruiterOrigin.POST), tuple("Bob Ionescu", RecruiterOrigin.FOUND),
					tuple("Carla Dinu", RecruiterOrigin.FOUND));
	}

	@Test
	void removesARecruiterForGoodUntilAddedByHand() {
		Recruiter bob = this.hiring.addFoundRecruiters(this.userId,
				List.of(found("Bob Ionescu", "https://www.linkedin.com/in/bob", "Acme", "RO", null))).added().getFirst();
		this.hiring.follow(this.userId, bob.id(), true);

		this.hiring.removeRecruiter(this.userId, bob.id());

		assertThat(this.hiring.findRecruiters(this.userId)).isEmpty();
		assertThat(this.hiring.addFoundRecruiters(this.userId,
				List.of(found("Bob Ionescu", "https://www.linkedin.com/in/bob", "Acme", "RO", null))).added()).isEmpty();
		assertThatExceptionOfType(RecruiterNotFoundException.class)
			.isThrownBy(() -> this.hiring.removeRecruiter(this.otherUserId, bob.id()));

		Recruiter back = this.hiring.addRecruiter(this.userId, "Bob Ionescu", null, "https://www.linkedin.com/in/bob");
		assertThat(back.id()).isEqualTo(bob.id());
		assertThat(this.hiring.findRecruiters(this.userId)).extracting(Recruiter::name, Recruiter::followed)
			.containsExactly(tuple("Bob Ionescu", false));
	}

	@Test
	void makesTheContactsOnJobsRecruitersToFollow() {
		long backend = job("Backend engineer", FollowUp.builder()
			.contactName("Dana Marin")
			.contactRole("Technical Recruiter")
			.contactLink("https://www.linkedin.com/in/dana-marin/")
			.build());
		long platform = job("Platform engineer", FollowUp.builder()
			.contactName("Dana Marin")
			.contactLink("https://ro.linkedin.com/in/dana-marin")
			.build());
		job("Data engineer", FollowUp.builder().contactName("Without a link").contactEmail("hr@acme.example").build());

		List<Recruiter> recruiters = this.hiring.findRecruiters(this.userId);

		assertThat(recruiters).singleElement().satisfies(dana -> {
			assertThat(dana.name()).isEqualTo("Dana Marin");
			assertThat(dana.headline()).isEqualTo("Technical Recruiter");
			assertThat(dana.company()).isEqualTo("Acme");
			assertThat(dana.origin()).isEqualTo(RecruiterOrigin.CONTACT);
			assertThat(dana.followed()).isTrue();
			assertThat(dana.contactFor()).extracting(ContactJob::applicationId).containsExactlyInAnyOrder(backend, platform);
		});
		assertThat(this.hiring.findRecruiters(this.otherUserId)).isEmpty();

		// Removed, a contact stays removed
		this.hiring.removeRecruiter(this.userId, recruiters.getFirst().id());
		assertThat(this.hiring.findRecruiters(this.userId)).isEmpty();
	}

	private long job(String title, FollowUp followUp) {
		long id = this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title(title)
			.company("Acme")
			.initialStatus(ApplicationStatus.APPLIED)
			.initialStatusDate(TODAY)
			.build());
		this.applications.updateFollowUp(this.userId, id, followUp);
		return id;
	}

	private static NewRecruiter found(String name, String profile, @Nullable String company, @Nullable String country,
			@Nullable String note) {
		return NewRecruiter.builder()
			.name(name)
			.headline("IT Recruiter")
			.profileUrl(profile)
			.company(company)
			.country(country)
			.note(note)
			.build();
	}

	private List<String> links(HiringPostQuery query) {
		return this.hiring.findPosts(this.userId, query).stream().map(HiringPost::link).toList();
	}

	private static NewHiringPost post(String link, String author, String profile, String text, LocalDate postedOn) {
		return NewHiringPost.builder()
			.link(link)
			.text(text)
			.company("Acme")
			.country("ro")
			.postedOn(postedOn)
			.authorName(author)
			.authorHeadline("IT Recruiter")
			.authorProfileUrl(profile)
			.build();
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id").param(name).query(Long.class).single();
	}

}
