package com.jobwarden.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionsTests {

	@Test
	void knowsTheCountriesOfEveryRegionTheSearchPageSuggests() {
		for (Region region : SearchSuggestions.REGIONS) {
			if (region.name().equals("Remote")) {
				assertThat(Regions.countriesOf(region.name())).as(region.name()).isEmpty();
			} else if (region.name().equals("remote EU")) {
				assertThat(Regions.countriesOf("EU")).contains("DE", "RO").doesNotContain("GB", "CH");
			} else {
				assertThat(Regions.countriesOf(region.name())).as(region.name()).isNotEmpty();
			}
		}
		assertThat(Regions.countriesOf("DACH")).containsExactlyInAnyOrder("DE", "AT", "CH");
		assertThat(Regions.countriesOf("Nordics")).containsExactlyInAnyOrder("SE", "NO", "DK", "FI", "IS");
		assertThat(Regions.countriesOf("Benelux")).containsExactlyInAnyOrder("BE", "NL", "LU");
		assertThat(Regions.countriesOf("Europe")).contains("DE", "GB", "CH", "NO", "UA").doesNotContain("US", "TR");
		assertThat(Regions.countriesOf("EMEA")).contains("DE", "AE", "ZA", "TR").doesNotContain("US", "IN");
		assertThat(Regions.countriesOf("Worldwide")).isSameAs(Regions.EVERYWHERE);
	}

	@Test
	void knowsCountriesByTheirNamesInSeveralLanguagesButNotCities() {
		assertThat(Regions.countriesOf("Germany")).containsExactly("DE");
		assertThat(Regions.countriesOf("Deutschland")).containsExactly("DE");
		assertThat(Regions.countriesOf("Allemagne")).containsExactly("DE");
		assertThat(Regions.countriesOf("România")).containsExactly("RO");
		assertThat(Regions.countriesOf("UK")).containsExactly("GB");
		assertThat(Regions.countriesOf("USA")).containsExactly("US");
		assertThat(Regions.countriesOf("United States")).containsExactly("US");
		assertThat(Regions.countriesOf("Berlin")).isEmpty();
		assertThat(Regions.countriesOf("Remote")).isEmpty();
	}

	@Test
	void readsTheCountriesOfAPostingsLocation() {
		assertThat(Regions.countriesIn("Remote, Canada; Remote, United States")).containsExactly("CA", "US");
		assertThat(Regions.countriesIn("Berlin")).containsExactly("DE");
		assertThat(Regions.countriesIn("Cluj-Napoca, Romania")).containsExactly("RO");
		assertThat(Regions.countriesIn("San Francisco, CA")).containsExactly("US");
		assertThat(Regions.countriesIn("Remote - EMEA")).contains("DE", "AE").doesNotContain("US");
		assertThat(Regions.countriesIn("Remote")).isEmpty();
		assertThat(Regions.countriesIn(null)).isEmpty();
		assertThat(Regions.countriesIn("Anywhere")).isEqualTo(Regions.EVERYWHERE);
	}

	@Test
	void readsTheLongestNameFirst() {
		// States, not the countries their names have
		assertThat(Regions.countriesIn("Albuquerque, New Mexico")).containsExactly("US");
		assertThat(Regions.countriesIn("Newark, New Jersey")).containsExactly("US");
		assertThat(Regions.countriesIn("Atlanta, Georgia")).containsExactly("US");
		assertThat(Regions.countriesIn("Porto Alegre, Brazil")).containsExactly("BR");
		assertThat(Regions.countriesIn("Porto")).containsExactly("PT");
		assertThat(Regions.countriesIn("Belfast, Northern Ireland")).containsExactly("GB");
		// "america" alone is the United States, not Central America
		assertThat(Regions.countriesIn("Remote - Central America")).contains("CR", "PA").doesNotContain("US");
		// "in" is no country: India's code only counts as a name
		assertThat(Regions.countriesIn("Remote in Europe")).doesNotContain("IN");
	}

}
