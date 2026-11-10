package com.jobwarden.watchlist.service.impl;

import com.jobwarden.watchlist.enums.Ats;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoardTests {

	@Test
	void readsTheBoardOfAnyGreenhouseLink() {
		Board gitlab = new Board(Ats.GREENHOUSE, "gitlab");

		assertThat(Board.of("https://boards.greenhouse.io/gitlab")).contains(gitlab);
		assertThat(Board.of("https://job-boards.greenhouse.io/GitLab/jobs/8638232002?gh_src=abc")).contains(gitlab);
		assertThat(Board.of("boards.greenhouse.io/embed/job_board?for=gitlab&b=x")).contains(gitlab);
		assertThat(Board.of("https://boards-api.greenhouse.io/v1/boards/gitlab/jobs")).contains(gitlab);
		assertThat(gitlab.url()).isEqualTo("https://job-boards.greenhouse.io/gitlab");
	}

	@Test
	void readsTheBoardOfLeverLinksInTheUsAndInEurope() {
		assertThat(Board.of("https://jobs.lever.co/spotify/2a55d6c9-c564-443a-be3e-a3851db5a93b"))
			.contains(new Board(Ats.LEVER, "spotify"));
		assertThat(Board.of("http://www.jobs.lever.co/spotify/")).contains(new Board(Ats.LEVER, "spotify"));
		assertThat(Board.of("https://jobs.eu.lever.co/acme-corp")).contains(new Board(Ats.LEVER_EU, "acme-corp"));
		assertThat(Board.of("https://api.lever.co/v0/postings/spotify?mode=json")).contains(new Board(Ats.LEVER, "spotify"));
		assertThat(new Board(Ats.LEVER_EU, "acme-corp").url()).isEqualTo("https://jobs.eu.lever.co/acme-corp");
	}

	@Test
	void readsTheBoardOfAshbyLinks() {
		assertThat(Board.of("https://jobs.ashbyhq.com/Ramp/0378cc97-de79-42f9-8401-d301c678f4d7"))
			.contains(new Board(Ats.ASHBY, "ramp"));
		assertThat(Board.of("https://api.ashbyhq.com/posting-api/job-board/ramp?includeCompensation=true"))
			.contains(new Board(Ats.ASHBY, "ramp"));
	}

	@Test
	void findsNoBoardWhereThereIsNone() {
		assertThat(Board.of("https://www.linkedin.com/jobs/view/123")).isEmpty();
		assertThat(Board.of("https://jobs.lever.co/")).isEmpty();
		assertThat(Board.of("https://boards.greenhouse.io/embed/job_board")).isEmpty();
		assertThat(Board.of("https://jobs.lever.co/../admin")).isEmpty();
		assertThat(Board.of("ftp://jobs.lever.co/acme")).isEmpty();
		assertThat(Board.of("not a link")).isEmpty();
		assertThat(Board.of("")).isEmpty();
		assertThat(Board.of(null)).isEmpty();
	}

	@Test
	void namesTheCompanyAfterItsBoard() {
		assertThat(new Board(Ats.LEVER, "acme-corp").companyName()).isEqualTo("Acme Corp");
		assertThat(new Board(Ats.ASHBY, "openai").companyName()).isEqualTo("Openai");
	}

}
