package com.jobwarden.web.dto;

import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.enums.ApplicationStatus;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationListParamsTests {

	@Test
	void fillsInTheDefaultsForWhatTheAddressLeavesOut() {
		ApplicationListParams params = new ApplicationListParams(" ", null, null, null, 0, 0);

		assertThat(params).isEqualTo(ApplicationListParams.defaults());
		assertThat(params.url()).isEqualTo("/applications");
	}

	@Test
	void replacesAPageSizeTheListDoesNotOffer() {
		assertThat(new ApplicationListParams(null, null, null, null, 1, 1000).size()).isEqualTo(20);
		assertThat(new ApplicationListParams(null, null, null, null, 1, 50).size()).isEqualTo(50);
	}

	@Test
	void linksKeepEverythingElse() {
		ApplicationListParams params = new ApplicationListParams("Java & Kotlin", ApplicationStatus.APPLIED,
				ApplicationSort.TITLE, Sort.Direction.DESC, 3, 50);

		assertThat(params.url())
			.isEqualTo("/applications?q=Java%20%26%20Kotlin&status=APPLIED&sort=TITLE&dir=DESC&page=3&size=50");
		assertThat(params.withStatus(null).url())
			.isEqualTo("/applications?q=Java%20%26%20Kotlin&sort=TITLE&dir=DESC&size=50");
		assertThat(params.withPage(4).page()).isEqualTo(4);
	}

	@Test
	void keepsAPlusInTheSearchAPlus() {
		assertThat(new ApplicationListParams("C++", null, null, null, 1, 20).url()).isEqualTo("/applications?q=C%2B%2B");
	}

	@Test
	void sortingByTheSameColumnAgainTurnsItRound() {
		ApplicationListParams byTitle = ApplicationListParams.defaults().withPage(3).sortedBy(ApplicationSort.TITLE);

		assertThat(byTitle.dir()).isEqualTo(Sort.Direction.ASC);
		assertThat(byTitle.page()).isEqualTo(1);
		assertThat(byTitle.sortedBy(ApplicationSort.TITLE).dir()).isEqualTo(Sort.Direction.DESC);
		// The date added starts newest first, the default, which the address leaves out
		assertThat(byTitle.sortedBy(ApplicationSort.ADDED).url()).isEqualTo("/applications");
		assertThat(ApplicationListParams.defaults().sortedBy(ApplicationSort.ADDED).url())
			.isEqualTo("/applications?sort=ADDED&dir=ASC");
	}

	@Test
	void leavesGapsBetweenFarAwayPageNumbers() {
		assertThat(ApplicationListParams.defaults().withPage(6).pageNumbers(12))
			.containsExactly(1, null, 4, 5, 6, 7, 8, null, 12);
		assertThat(ApplicationListParams.defaults().pageNumbers(4)).containsExactly(1, 2, 3, 4);
		assertThat(ApplicationListParams.defaults().withPage(2).pageNumbers(0)).isEmpty();
		assertThat(ApplicationListParams.defaults().pageNumbers(12)).containsExactly(1, 2, 3, null, 12);
	}

	@Test
	void isFilteredBySearchOrStatusOnly() {
		assertThat(ApplicationListParams.defaults().sortedBy(ApplicationSort.STATUS).isFiltered()).isFalse();
		assertThat(ApplicationListParams.defaults().withStatus(ApplicationStatus.OFFER).isFiltered()).isTrue();
		assertThat(new ApplicationListParams("java", null, null, null, 1, 20).isFiltered()).isTrue();
	}

}
