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
	void readsTheBoardOfSmartRecruitersLinks() {
		Board bosch = new Board(Ats.SMARTRECRUITERS, "boschgroup");

		assertThat(Board.of("https://jobs.smartrecruiters.com/BoschGroup/744000163282223-senior-engineer")).contains(bosch);
		assertThat(Board.of("https://careers.smartrecruiters.com/BoschGroup")).contains(bosch);
		assertThat(Board.of("https://jobs.smartrecruiters.com/oneclick-ui/company/BoschGroup/publication/66e6c27a?dcr_ci=x"))
			.contains(bosch);
		assertThat(Board.of("https://api.smartrecruiters.com/v1/companies/BoschGroup/postings?limit=100")).contains(bosch);
		assertThat(bosch.url()).isEqualTo("https://careers.smartrecruiters.com/boschgroup");
	}

	@Test
	void readsTheBoardOfWorkableLinksThatSayWhoseTheyAre() {
		Board huggingFace = new Board(Ats.WORKABLE, "huggingface");

		assertThat(Board.of("https://apply.workable.com/huggingface/j/81B46579FE/")).contains(huggingFace);
		assertThat(Board.of("apply.workable.com/huggingface")).contains(huggingFace);
		assertThat(Board.of("https://apply.workable.com/api/v1/widget/accounts/huggingface?details=true"))
			.contains(huggingFace);
		assertThat(huggingFace.url()).isEqualTo("https://apply.workable.com/huggingface");
		// The short link to a job doesn't say whose board it's on
		assertThat(Board.of("https://apply.workable.com/j/81B46579FE")).isEmpty();
		assertThat(Board.of("https://apply.workable.com/api/v1/widget")).isEmpty();
	}

	@Test
	void readsTheBoardOfRecruiteeAndPersonioLinksFromTheirHosts() {
		assertThat(Board.of("https://channable.recruitee.com/o/haskell-software-engineer"))
			.contains(new Board(Ats.RECRUITEE, "channable"));
		assertThat(Board.of("https://tado.jobs.personio.de/job/2502124?language=en"))
			.contains(new Board(Ats.PERSONIO, "tado"));
		// The same boards on Personio's other domain
		assertThat(Board.of("TADO.jobs.personio.com")).contains(new Board(Ats.PERSONIO, "tado"));
		assertThat(new Board(Ats.RECRUITEE, "channable").url()).isEqualTo("https://channable.recruitee.com");
		assertThat(new Board(Ats.PERSONIO, "tado").url()).isEqualTo("https://tado.jobs.personio.de");
	}

	@Test
	void findsNoBoardOnRecruiteesOwnHostsOrOnAHostThatIsntOneLabel() {
		assertThat(Board.of("https://app.recruitee.com/login")).isEmpty();
		assertThat(Board.of("https://recruitee.com/")).isEmpty();
		assertThat(Board.of("https://a.b.recruitee.com/o/job")).isEmpty();
		assertThat(Board.of("https://-acme.jobs.personio.de/")).isEmpty();
		assertThat(Board.of("https://jobs.personio.de/")).isEmpty();
		assertThat(Board.isHostLabel("acme-corp")).isTrue();
		assertThat(Board.isHostLabel("acme corp")).isFalse();
		assertThat(Board.isHostLabel("acme.evil.com")).isFalse();
	}

	@Test
	void findsNoBoardWhereThereIsNone() {
		assertThat(Board.of("https://www.linkedin.com/jobs/view/123")).isEmpty();
		assertThat(Board.of("https://jobs.lever.co/")).isEmpty();
		assertThat(Board.of("https://boards.greenhouse.io/embed/job_board")).isEmpty();
		assertThat(Board.of("https://jobs.lever.co/../admin")).isEmpty();
		assertThat(Board.of("ftp://jobs.lever.co/acme")).isEmpty();
		assertThat(Board.of("https://jobs.smartrecruiters.com/")).isEmpty();
		assertThat(Board.of("https://jobs.smartrecruiters.com/oneclick-ui/other/acme")).isEmpty();
		assertThat(Board.of("https://api.smartrecruiters.com/v1/postings")).isEmpty();
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
