package com.jobwarden.web.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.hiring.dto.ContactJob;
import com.jobwarden.hiring.dto.HiringPost;
import com.jobwarden.hiring.dto.HiringPostQuery;
import com.jobwarden.hiring.dto.Recruiter;
import com.jobwarden.hiring.enums.PostedWithin;
import com.jobwarden.hiring.enums.RecruiterOrigin;
import com.jobwarden.hiring.exception.HiringErrorCode;
import com.jobwarden.hiring.service.HiringService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.TrackedApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.search.Recency;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@WebMvcTest(HiringController.class)
class HiringControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final Recruiter ANA = Recruiter.builder()
		.id(3)
		.name("Ana Pop")
		.headline("IT Recruiter at Acme")
		.profileUrl("https://www.linkedin.com/in/ana-pop")
		.posts(1)
		.build();

	private static final HiringPost POST = HiringPost.builder()
		.id(9)
		.link("https://www.linkedin.com/posts/ana_hiring-activity-1")
		.text("We're hiring a Java developer in Cluj!\n#hiring #java")
		.role("Java Developer")
		.company("Acme")
		.country("RO")
		.tags(List.of("hiring", "java"))
		.postedOn(LocalDate.now())
		.foundAt(Instant.now())
		.recruiter(ANA)
		.build();

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private HiringService hiring;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@BeforeEach
	void selectRobert() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.hiring.findPosts(eq(1L), any())).willReturn(List.of(POST));
		given(this.hiring.findRecruiters(1)).willReturn(List.of(ANA));
	}

	@Test
	void showsThePostsWithTheirAuthorsAndWhatToFilterBy() {
		assertThat(this.mvc.get().uri("/hiring")).hasStatusOk()
			.hasViewName("hiring/hiring")
			.bodyText()
			.contains("Who’s hiring", "Ana Pop", "IT Recruiter at Acme", "Java Developer", "We&#39;re hiring a Java developer",
					"href=\"https://www.linkedin.com/posts/ana_hiring-activity-1\"", "fi-ro", "Romania",
					"href=\"/hiring?tag=java\"", "#java", "hx-post=\"/hiring/posts/9/save\"", "follow-3",
					"hx-post=\"/hiring/recruiters/3/follow\"", "Ask Claude for posts", "Where do these posts come from?")
			.doesNotContain("every 5s");
	}

	@Test
	void filtersAsTheAddressSays() {
		assertThat(this.mvc.get().uri("/hiring?tab=FOLLOWING&q=java&posted=WEEK&country=RO&tag=java")).hasStatusOk()
			.bodyText()
			.contains("href=\"/hiring?tab=FOLLOWING&amp;q=java&amp;posted=MONTH&amp;country=RO&amp;tag=java\"");
		ArgumentCaptor<HiringPostQuery> asked = ArgumentCaptor.forClass(HiringPostQuery.class);
		then(this.hiring).should(times(2)).findPosts(eq(1L), asked.capture());
		assertThat(asked.getAllValues()).contains(HiringPostQuery.ALL, HiringPostQuery.builder()
			.search("java")
			.postedWithin(PostedWithin.WEEK)
			.country("RO")
			.tag("java")
			.followedOnly(true)
			.build());
	}

	@Test
	void listsTheRecruitersToFollowAndOffersToFindMore() {
		assertThat(this.mvc.get().uri("/hiring?tab=RECRUITERS")).bodyText()
			.contains("Ana Pop", "href=\"/hiring?recruiter=3\"", "1 post", "Follow", "hx-post=\"/hiring/recruiters/3/remove\"",
					"Ask Claude for recruiters", "name=\"kind\" value=\"FIND_RECRUITERS\"", "name=\"count\"",
					"href=\"/hiring?tab=RECRUITERS&amp;who=FOUND\"")
			.doesNotContain("hiring/posts/9/save", "name=\"recency\"");
	}

	@Test
	void saysWhereEachRecruiterCameFrom() {
		given(this.hiring.findRecruiters(1)).willReturn(List.of(ANA, Recruiter.builder()
			.id(4)
			.name("Bob Ionescu")
			.profileUrl("https://www.linkedin.com/in/bob")
			.company("Globex")
			.country("RO")
			.note("Hires Java developers for the payments team.")
			.origin(RecruiterOrigin.FOUND)
			.build(), Recruiter.builder()
			.id(5)
			.name("Dana Marin")
			.profileUrl("https://www.linkedin.com/in/dana")
			.origin(RecruiterOrigin.CONTACT)
			.followed(true)
			.contactFor(List.of(new ContactJob(40, "Backend engineer", "Initech")))
			.build()));

		assertThat(this.mvc.get().uri("/hiring?tab=RECRUITERS")).bodyText()
			.contains("Found by Claude", "Hires Java developers for the payments team.", "Globex", "fi-ro",
					"Contact for", "href=\"/applications/40\"", "Backend engineer · Initech");
		assertThat(this.mvc.get().uri("/hiring?tab=RECRUITERS&who=FOUND")).bodyText()
			.contains("Bob Ionescu")
			.doesNotContain("Dana Marin", ">Ana Pop<");
		assertThat(this.mvc.get().uri("/hiring?tab=RECRUITERS&who=CONTACTS")).bodyText()
			.contains("Dana Marin")
			.doesNotContain("Bob Ionescu");
		assertThat(this.mvc.get().uri("/hiring?tab=RECRUITERS&q=globex")).bodyText()
			.contains("Bob Ionescu")
			.doesNotContain("Dana Marin");
	}

	@Test
	void asksClaudeForRecruiters() {
		AiRequest waiting = AiRequest.builder()
			.id(22)
			.kind(AiRequestKind.FIND_RECRUITERS)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build();
		given(this.discovery.findRequests(1, 50)).willReturn(List.of(waiting));

		assertThat(this.mvc.post().uri("/hiring/requests").param("kind", "FIND_RECRUITERS").param("roles", "Java Developer")
			.param("places", "Romania").param("count", "20")).hasStatusOk()
			.bodyText()
			.contains("Waiting for Claude", "Do JobWarden request 22", "/hiring/requests/latest?kind=FIND_RECRUITERS&amp;waiting=true");
		then(this.discovery).should().createRequest(1, AiRequestKind.FIND_RECRUITERS, AiRequirements.builder()
			.titles(List.of("Java Developer"))
			.locations(List.of("Romania"))
			.maxJobs(20)
			.build());
	}

	@Test
	void removesARecruiter() {
		assertThat(this.mvc.post().uri("/hiring/recruiters/3/remove")).hasStatusOk();

		then(this.hiring).should().removeRecruiter(1, 3);
	}

	@Test
	void asksClaudeForPostsAndShowsThePrompt() {
		AiRequest waiting = AiRequest.builder()
			.id(21)
			.kind(AiRequestKind.FIND_HIRING_POSTS)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build();
		given(this.discovery.findRequests(1, 50)).willReturn(List.of(waiting));

		assertThat(this.mvc.post().uri("/hiring/requests").param("roles", "Java Developer, Backend Engineer ,")
			.param("places", "Romania").param("recency", "WEEK")).hasStatusOk()
			.bodyText()
			.contains("Waiting for Claude", "Do JobWarden request 21", "hx-trigger=\"every 5s\"");
		then(this.discovery).should().createRequest(1, AiRequestKind.FIND_HIRING_POSTS, AiRequirements.builder()
			.titles(List.of("Java Developer", "Backend Engineer"))
			.locations(List.of("Romania"))
			.recency(Recency.WEEK)
			.build());
	}

	@Test
	void reloadsThePageOnceClaudeIsDone() {
		given(this.discovery.findRequests(1, 50)).willReturn(List.of(AiRequest.builder()
			.id(21)
			.kind(AiRequestKind.FIND_HIRING_POSTS)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.DONE)
			.summary("Added 12 posts from 9 people.")
			.createdAt(Instant.now())
			.build()));

		assertThat(this.mvc.get().uri("/hiring/requests/latest").param("waiting", "true")).hasStatusOk()
			.hasHeader("HX-Refresh", "true")
			.bodyText()
			.contains("Added 12 posts from 9 people.");
	}

	@Test
	void savesAPostAsAJobAndSaysSo() {
		given(this.hiring.findPost(1, 9)).willReturn(POST);
		given(this.applications.track(any())).willReturn(new TrackedApplication(40, true));
		given(this.hiring.markSaved(1, 9, 40)).willReturn(HiringPost.builder()
			.id(9).link(POST.link()).text(POST.text()).recruiter(ANA).foundAt(POST.foundAt()).applicationId(40L).build());

		assertThat(this.mvc.post().uri("/hiring/posts/9/save")).hasStatusOk()
			.bodyText()
			.contains("href=\"/applications/40\"", "Saved: open the job");
		ArgumentCaptor<NewApplication> saved = ArgumentCaptor.forClass(NewApplication.class);
		then(this.applications).should().track(saved.capture());
		assertThat(saved.getValue()).satisfies(job -> {
			assertThat(job.title()).isEqualTo("Java Developer");
			assertThat(job.company()).isEqualTo("Acme");
			assertThat(job.location()).isEqualTo("Romania");
			assertThat(job.link()).isEqualTo(POST.link());
			assertThat(job.initialStatus()).isEqualTo(ApplicationStatus.SAVED);
		});
	}

	@Test
	void followsARecruiterEverywhereTheirButtonShows() {
		given(this.hiring.follow(1, 3, true)).willReturn(Recruiter.builder()
			.id(3).name("Ana Pop").profileUrl(ANA.profileUrl()).followed(true).build());

		assertThat(this.mvc.post().uri("/hiring/recruiters/3/follow").param("followed", "true")).hasStatusOk()
			.bodyText()
			.contains("hx-swap-oob=\"outerHTML:.follow-3\"", "Following", "{&quot;followed&quot;: false}");
	}

	@Test
	void addsARecruiterByHandAndFollowsThem() {
		given(this.hiring.addRecruiter(1, "Ana Pop", null, "https://www.linkedin.com/in/ana-pop")).willReturn(ANA);

		assertThat(this.mvc.post().uri("/hiring/recruiters").param("name", "Ana Pop")
			.param("profileUrl", "https://www.linkedin.com/in/ana-pop")).hasRedirectedUrl("/hiring?tab=RECRUITERS");
		then(this.hiring).should().follow(1, 3, true);

		willThrow(new BusinessRuleException(HiringErrorCode.PROFILE_LINK_NOT_A_WEB_ADDRESS, "Not a web address"))
			.given(this.hiring).addRecruiter(1, "Bob", null, "bob");
		assertThat(this.mvc.post().uri("/hiring/recruiters").param("name", "Bob").param("profileUrl", "bob"))
			.flash()
			.containsEntry("hiringError",
					"A recruiter’s profile must be a web address, e.g. https://www.linkedin.com/in/name.");
	}

	@Test
	void hidesAPost() {
		assertThat(this.mvc.post().uri("/hiring/posts/9/hide")).hasStatusOk();

		then(this.hiring).should().hidePost(1, 9);
	}

}
