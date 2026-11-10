package com.jobwarden.capture.controller;

import java.time.Instant;
import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.watchlist.dto.BoardLink;
import com.jobwarden.watchlist.dto.WatchResult;
import com.jobwarden.watchlist.dto.WatchedCompany;
import com.jobwarden.watchlist.enums.Ats;
import com.jobwarden.watchlist.exception.WatchlistErrorCode;
import com.jobwarden.watchlist.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@WebMvcTest(WatchController.class)
class WatchControllerTests {

	private static final String CHROME_EXTENSION = "chrome-extension://abcdefghijklmnopabcdefghijklmnop";

	private static final String JOB = "https://jobs.lever.co/acme/1a2b";

	private static final WatchedCompany ACME = WatchedCompany.builder()
		.id(7)
		.name("Acme Corp")
		.ats(Ats.LEVER)
		.board("acme")
		.boardUrl("https://jobs.lever.co/acme")
		.addedAt(Instant.now())
		.build();

	@Autowired
	private MockMvcTester mvc;

	@Value("${jobwarden.capture.path}")
	private String capturePath;

	@MockitoBean
	private WatchlistService watchlist;

	@MockitoBean
	private UserService users;

	@BeforeEach
	void profile() {
		given(this.users.exists(1)).willReturn(true);
	}

	@Test
	void saysWhetherTheProfileWatchesTheBoardAPageIsOn() {
		given(this.watchlist.findBoard(1, JOB)).willReturn(Optional.of(new BoardLink(Ats.LEVER, "acme",
				"https://jobs.lever.co/acme", "Acme", null)));
		given(this.watchlist.findBoard(1, "https://jobs.lever.co/globex/9")).willReturn(Optional.of(new BoardLink(
				Ats.LEVER, "globex", "https://jobs.lever.co/globex", "Acme Corp", ACME)));

		assertThat(this.mvc.get().uri(this.capturePath + "/watchlist").param("userId", "1").param("link", JOB)
			.header(HttpHeaders.ORIGIN, CHROME_EXTENSION))
			.hasStatusOk()
			.hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CHROME_EXTENSION)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"name": "Acme", "system": "Lever", "boardUrl": "https://jobs.lever.co/acme", "watched": false,
					 "companyId": null}""");
		assertThat(this.mvc.get().uri(this.capturePath + "/watchlist").param("userId", "1")
			.param("link", "https://jobs.lever.co/globex/9"))
			.bodyJson()
			.isLenientlyEqualTo("""
					{"name": "Acme Corp", "watched": true, "companyId": 7}""");
	}

	@Test
	void saysNothingForAPageThatIsntOnABoard() {
		assertThat(this.mvc.get().uri(this.capturePath + "/watchlist").param("userId", "1")
			.param("link", "https://www.linkedin.com/jobs/view/1"))
			.hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void watchesTheCompanyUnderThePagesNameForIt() {
		given(this.watchlist.watch(1, JOB, "Acme Corp")).willReturn(new WatchResult(ACME, true, 3));

		assertThat(this.mvc.post()
			.uri(this.capturePath + "/watchlist")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\": 1, \"link\": \"" + JOB + "\", \"name\": \"Acme Corp\"}")
			.header(HttpHeaders.ORIGIN, CHROME_EXTENSION))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"name": "Acme Corp", "system": "Lever", "watched": true, "companyId": 7, "added": true,
					 "newJobs": 3}""");
	}

	@Test
	void answersWithTheCompanyWatchedAlready() {
		given(this.watchlist.watch(1, JOB, null)).willReturn(new WatchResult(ACME, false, 0));

		assertThat(this.mvc.post()
			.uri(this.capturePath + "/watchlist")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\": 1, \"link\": \"" + JOB + "\"}"))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"added": false, "newJobs": 0}""");
	}

	@Test
	void saysWhyTheBoardCantBeWatchedInTheExtensionsLanguage() {
		given(this.watchlist.watch(1, "https://jobs.lever.co/nobody", null)).willThrow(new BusinessRuleException(
				WatchlistErrorCode.BOARD_NOT_FOUND, "No board", "https://jobs.lever.co/nobody"));

		assertThat(this.mvc.post()
			.uri(this.capturePath + "/watchlist")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\": 1, \"link\": \"https://jobs.lever.co/nobody\"}")
			.header(HttpHeaders.ACCEPT_LANGUAGE, "ro"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"detail": "Nu există nicio pagină de joburi la https://jobs.lever.co/nobody: verifică linkul.",
					 "errorCode": "WATCHLIST-003"}""");
	}

	@Test
	void refusesAProfileThatDoesntExistAndAWebPage() {
		assertThat(this.mvc.get().uri(this.capturePath + "/watchlist").param("userId", "9").param("link", JOB))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.mvc.post()
			.uri(this.capturePath + "/watchlist")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\": 1, \"link\": \"" + JOB + "\"}")
			.header(HttpHeaders.ORIGIN, "https://evil.example.com"))
			.hasStatus(HttpStatus.FORBIDDEN);
		then(this.watchlist).should(never()).watch(anyLong(), any(), any());
	}

}
