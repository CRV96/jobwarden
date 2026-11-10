package com.jobwarden.search;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Places as the countries they're in, so a posting's location can be told to fit a search's without AI: the regions
 * the search page suggests ({@link SearchSuggestions#REGIONS}) and those postings name, by the countries they stand for;
 * the countries by their names in English, the app's languages and their neighbours'; and the cities and states job
 * boards name without their country ("London", "San Francisco, CA"). Countries are ISO 3166-1 alpha-2 codes, in upper
 * case.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Regions {

	/** Every country: what "Worldwide" and "Anywhere" stand for. */
	public static final Set<String> EVERYWHERE = Set.of(Locale.getISOCountries());

	private static final Set<String> EU = codes(
			"AT BE BG CY CZ DE DK EE ES FI FR GR HR HU IE IT LT LU LV MT NL PL PT RO SE SI SK");

	private static final Set<String> EUROPE = union(EU,
			codes("AD AL BA BY CH FO GB GG GI IM IS JE LI MC MD ME MK NO RS SM UA VA"));

	private static final Set<String> NORDICS = codes("DK FI IS NO SE");

	private static final Set<String> MIDDLE_EAST = codes("AE BH IL IQ IR JO KW LB OM PS QA SA SY TR YE");

	private static final Set<String> AFRICA = codes("AO BF BI BJ BW CD CF CG CI CM CV DJ DZ EG ER ET GA GH GM GN GQ "
			+ "GW KE KM LR LS LY MA MG ML MR MU MW MZ NA NE NG RW SC SD SL SN SO SS ST SZ TD TG TN TZ UG ZA ZM ZW");

	private static final Set<String> APAC = codes(
			"AU BD BN CN FJ HK ID IN JP KH KR LA LK MM MN MO MY NP NZ PG PH PK SG TH TW VN");

	private static final Set<String> SOUTH_AMERICA = codes("AR BO BR CL CO EC PE PY UY VE");

	private static final Set<String> LATAM = union(SOUTH_AMERICA, codes("BZ CR CU DO GT HN MX NI PA PR SV"));

	private static final Set<String> NORTH_AMERICA = codes("CA MX US");

	/** The languages whose names of the countries a posting may use. */
	private static final List<String> LANGUAGES = List.of("en", "de", "fr", "es", "pt", "pl", "ro", "it", "nl", "sv",
			"da", "nb", "fi", "cs", "hu");

	/** Country names that name somewhere else more often in postings: Georgia and New Jersey are US states. */
	private static final Set<String> AMBIGUOUS = Set.of("georgia", "jersey");

	/** Regions and countries, by their names in {@link Words} form. */
	private static final Map<String, Set<String>> AREAS = areas();

	/** Areas, and the cities and states postings name without their country. */
	private static final Map<String, Set<String>> PLACES = places();

	private static final int LONGEST = PLACES.keySet().stream().mapToInt(name -> name.split(" ").length).max().orElse(1);

	/**
	 * @param place a region, e.g. EMEA or Nordics, or a country, e.g. Germany, Deutschland or UK, as a search names it
	 * @return the countries it stands for; none when it's neither, e.g. a city or "Remote"
	 */
	public static Set<String> countriesOf(String place) {
		return AREAS.getOrDefault(Words.of(place).strip(), Set.of());
	}

	/**
	 * @param location where a posting is, as its board says it, e.g. "Remote, Canada; Remote, United States", "Berlin"
	 * or "Remote - EMEA"
	 * @return the countries the regions, countries, cities and states it names are in; {@link #EVERYWHERE} for one
	 * open worldwide; none when it names no place, e.g. "Remote"
	 */
	public static Set<String> countriesIn(@Nullable String location) {
		String[] words = Words.of(location).strip().split(" ");
		Set<String> countries = new TreeSet<>();
		int at = 0;
		while (at < words.length) {
			int matched = 0;
			// The longest name first: "new mexico" is a state, "mexico" a country
			for (int length = Math.min(LONGEST, words.length - at); length > 0 && matched == 0; length--) {
				Set<String> found = PLACES.get(String.join(" ", Arrays.copyOfRange(words, at, at + length)));
				if (found != null) {
					countries.addAll(found);
					matched = length;
				}
			}
			at += Math.max(matched, 1);
		}
		return Collections.unmodifiableSet(countries);
	}

	private static Map<String, Set<String>> areas() {
		Map<String, Set<String>> areas = new HashMap<>();
		for (String code : Locale.getISOCountries()) {
			Locale country = Locale.of("", code);
			for (String language : LANGUAGES) {
				String name = country.getDisplayCountry(Locale.of(language));
				// "Myanmar (Burma)": also "myanmar"; "Hong Kong SAR China" comes below
				Stream.of(name, name.replaceAll("\\s*\\(.*", ""))
					.map(Words::of)
					.map(String::strip)
					.filter(words -> !words.isEmpty() && !words.equalsIgnoreCase(code) && !AMBIGUOUS.contains(words))
					.forEach(words -> areas.putIfAbsent(words, Set.of(code)));
			}
		}
		Map.of("usa", "US", "us", "US", "u s", "US", "u s a", "US", "united states of america", "US", "america", "US")
			.forEach((name, code) -> areas.put(name, Set.of(code)));
		Map.of("uk", "GB", "u k", "GB", "great britain", "GB", "britain", "GB", "england", "GB", "scotland", "GB",
				"wales", "GB", "northern ireland", "GB")
			.forEach((name, code) -> areas.put(name, Set.of(code)));
		Map.of("uae", "AE", "emirates", "AE", "holland", "NL", "czech republic", "CZ", "turkey", "TR", "korea", "KR",
				"hong kong", "HK", "macau", "MO", "russia", "RU", "vietnam", "VN")
			.forEach((name, code) -> areas.put(name, Set.of(code)));
		Map<String, Set<String>> regions = new HashMap<>();
		regions.put("europe", EUROPE);
		regions.put("european union", EU);
		regions.put("eu", EU);
		regions.put("eea", union(EU, codes("IS LI NO")));
		regions.put("emea", union(EUROPE, MIDDLE_EAST, AFRICA, codes("AM AZ GE RU")));
		regions.put("dach", codes("AT CH DE"));
		regions.put("nordics", NORDICS);
		regions.put("nordic", NORDICS);
		regions.put("nordic countries", NORDICS);
		regions.put("scandinavia", codes("DK NO SE"));
		regions.put("benelux", codes("BE LU NL"));
		regions.put("cee", codes("BG CZ EE HR HU LT LV PL RO SI SK"));
		regions.put("mena", union(MIDDLE_EAST, codes("DZ EG LY MA SD TN")));
		regions.put("middle east", MIDDLE_EAST);
		regions.put("africa", AFRICA);
		regions.put("apac", APAC);
		regions.put("asia pacific", APAC);
		regions.put("latam", LATAM);
		regions.put("latin america", LATAM);
		regions.put("south america", SOUTH_AMERICA);
		regions.put("north america", NORTH_AMERICA);
		regions.put("americas", union(NORTH_AMERICA, LATAM));
		regions.put("worldwide", EVERYWHERE);
		regions.put("anywhere", EVERYWHERE);
		regions.put("global", EVERYWHERE);
		areas.putAll(regions);
		return Map.copyOf(areas);
	}

	private static Map<String, Set<String>> places() {
		Map<String, Set<String>> places = new HashMap<>(AREAS);
		Map.ofEntries(
				// Europe's tech cities, with their own names too
				Map.entry("GB", "London, Manchester, Edinburgh, Glasgow, Bristol, Leeds, Belfast, Birmingham"),
				Map.entry("IE", "Dublin, Cork, Galway, Limerick"),
				Map.entry("DE", "Berlin, Munich, München, Hamburg, Frankfurt, Cologne, Köln, Stuttgart, Düsseldorf, "
						+ "Leipzig, Dresden, Karlsruhe, Nuremberg, Nürnberg, Bonn, Hannover, Heidelberg"),
				Map.entry("AT", "Vienna, Wien, Graz, Linz, Salzburg"),
				Map.entry("CH", "Zurich, Geneva, Genève, Genf, Basel, Lausanne, Bern"),
				Map.entry("NL", "Amsterdam, Rotterdam, Utrecht, Eindhoven, The Hague, Den Haag, Delft, Groningen"),
				Map.entry("BE", "Brussels, Bruxelles, Brussel, Antwerp, Antwerpen, Ghent, Gent, Leuven"),
				Map.entry("FR", "Paris, Lyon, Toulouse, Nantes, Bordeaux, Lille, Marseille, Montpellier, Grenoble, "
						+ "Sophia Antipolis, Rennes"),
				Map.entry("ES", "Madrid, Barcelona, Valencia, Malaga, Seville, Sevilla, Bilbao"),
				Map.entry("PT", "Lisbon, Lisboa, Porto, Braga, Coimbra"),
				Map.entry("IT", "Milan, Milano, Rome, Roma, Turin, Torino, Bologna, Florence, Firenze, Naples"),
				Map.entry("SE", "Stockholm, Gothenburg, Göteborg, Malmö, Uppsala, Lund"),
				Map.entry("NO", "Oslo, Bergen, Trondheim, Stavanger"),
				Map.entry("DK", "Copenhagen, København, Aarhus, Odense"),
				Map.entry("FI", "Helsinki, Espoo, Tampere, Oulu, Turku"),
				Map.entry("IS", "Reykjavik"),
				Map.entry("PL", "Warsaw, Warszawa, Krakow, Kraków, Wroclaw, Wrocław, Gdansk, Gdańsk, Poznan, Poznań, Lodz, "
						+ "Łódź, Katowice, Lublin, Szczecin, Gdynia"),
				Map.entry("CZ", "Prague, Praha, Brno, Ostrava"),
				Map.entry("SK", "Bratislava, Kosice"),
				Map.entry("HU", "Budapest, Debrecen, Szeged"),
				Map.entry("RO", "Bucharest, București, Cluj-Napoca, Cluj, Iasi, Iași, Timisoara, Timișoara, Brasov, "
						+ "Brașov, Sibiu, Oradea, Constanta, Constanța, Craiova"),
				Map.entry("BG", "Sofia, Plovdiv, Varna"),
				Map.entry("GR", "Athens, Thessaloniki"),
				Map.entry("RS", "Belgrade, Beograd, Novi Sad"),
				Map.entry("HR", "Zagreb, Split"),
				Map.entry("SI", "Ljubljana"),
				Map.entry("LT", "Vilnius, Kaunas"),
				Map.entry("LV", "Riga"),
				Map.entry("EE", "Tallinn, Tartu"),
				Map.entry("UA", "Kyiv, Kiev, Lviv, Kharkiv, Odesa, Dnipro"),
				Map.entry("CY", "Limassol, Nicosia, Larnaca"),
				Map.entry("MT", "Valletta"),
				Map.entry("TR", "Istanbul, Ankara, Izmir"),
				Map.entry("IL", "Tel Aviv, Jerusalem, Haifa, Herzliya"),
				Map.entry("AE", "Dubai, Abu Dhabi"),
				Map.entry("SA", "Riyadh, Jeddah"),
				Map.entry("QA", "Doha"),
				Map.entry("EG", "Cairo, Alexandria"),
				Map.entry("MA", "Casablanca, Rabat"),
				Map.entry("NG", "Lagos, Abuja"),
				Map.entry("KE", "Nairobi"),
				Map.entry("ZA", "Cape Town, Johannesburg, Pretoria, Durban"),
				Map.entry("GH", "Accra"),
				// The United States' tech cities, and its states, which boards often name alone
				Map.entry("US", "New York, NYC, San Francisco, Bay Area, Silicon Valley, Seattle, Austin, Boston, Chicago, "
						+ "Los Angeles, Denver, Atlanta, Miami, San Diego, Portland, Palo Alto, Mountain View, Menlo Park, "
						+ "San Jose, Sunnyvale, Redmond, Philadelphia, Pittsburgh, Dallas, Houston, Phoenix, Salt Lake City, "
						+ "Minneapolis, Detroit, Raleigh, Nashville, Brooklyn, Oakland, Santa Monica, Boulder, "
						+ "Washington DC, District of Columbia, Alabama, Alaska, Arizona, Arkansas, California, Colorado, "
						+ "Connecticut, Delaware, Florida, Georgia, Hawaii, Idaho, Illinois, Indiana, Iowa, Kansas, Kentucky, "
						+ "Louisiana, Maine, Maryland, Massachusetts, Michigan, Minnesota, Mississippi, Missouri, Montana, "
						+ "Nebraska, Nevada, New Hampshire, New Jersey, New Mexico, North Carolina, North Dakota, Ohio, "
						+ "Oklahoma, Oregon, Pennsylvania, Rhode Island, South Carolina, South Dakota, Tennessee, Texas, Utah, "
						+ "Vermont, Virginia, Washington, West Virginia, Wisconsin, Wyoming"),
				Map.entry("CA", "Toronto, Vancouver, Montreal, Ottawa, Waterloo, Calgary, Edmonton, Ontario, Quebec, "
						+ "British Columbia, Alberta"),
				Map.entry("MX", "Guadalajara, Monterrey"),
				Map.entry("BR", "São Paulo, Rio de Janeiro, Belo Horizonte, Florianópolis, Curitiba, Porto Alegre, Recife"),
				Map.entry("AR", "Buenos Aires"),
				Map.entry("CO", "Bogotá, Medellín"),
				Map.entry("CL", "Santiago"),
				Map.entry("PE", "Lima"),
				Map.entry("UY", "Montevideo"),
				Map.entry("IN", "Bangalore, Bengaluru, Hyderabad, Pune, Mumbai, Delhi, New Delhi, Gurgaon, Gurugram, Noida, "
						+ "Chennai, Kolkata, Ahmedabad, Kochi, Jaipur"),
				Map.entry("AU", "Sydney, Melbourne, Brisbane, Perth, Adelaide, Canberra"),
				Map.entry("NZ", "Auckland, Wellington, Christchurch"),
				Map.entry("JP", "Tokyo, Osaka, Kyoto"),
				Map.entry("KR", "Seoul, Busan"),
				Map.entry("CN", "Beijing, Shanghai, Shenzhen, Hangzhou, Guangzhou, Chengdu"),
				Map.entry("TW", "Taipei"),
				Map.entry("PH", "Manila, Cebu"),
				Map.entry("ID", "Jakarta"),
				Map.entry("MY", "Kuala Lumpur"),
				Map.entry("TH", "Bangkok"),
				Map.entry("VN", "Ho Chi Minh City, Hanoi, Saigon"),
				Map.entry("PK", "Karachi, Lahore, Islamabad"),
				Map.entry("BD", "Dhaka"))
			.forEach((code, names) -> Arrays.stream(names.split(","))
				.map(Words::of)
				.map(String::strip)
				.forEach(name -> places.put(name, Set.of(code))));
		return Map.copyOf(places);
	}

	private static Set<String> codes(String codes) {
		return Set.of(codes.split(" "));
	}

	@SafeVarargs
	private static Set<String> union(Set<String>... sets) {
		return Arrays.stream(sets).flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
	}

}
