package com.jobwarden.mcp.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.jobwarden.jobs.dto.ComparedOffer;
import com.jobwarden.jobs.dto.JobOffer;
import com.jobwarden.jobs.dto.NegotiationBrief;
import com.jobwarden.jobs.dto.OfferComparison;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.OfferService;
import com.jobwarden.market.dto.MarketCountry;
import com.jobwarden.market.dto.MarketFigure;
import com.jobwarden.market.dto.MarketQuery;
import com.jobwarden.market.dto.MarketSalaries;
import com.jobwarden.market.enums.MarketSource;
import com.jobwarden.market.enums.MarketStatistic;
import com.jobwarden.market.service.MarketSalaryService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.oneOf;
import static com.jobwarden.mcp.controller.Schemas.string;
import static com.jobwarden.mcp.controller.Schemas.strings;

/**
 * Negotiating an offer: the profile's offers side by side, what the market pays from the sources JobWarden has (the
 * Stack Overflow survey, Adzuna's job ads, Eurostat), and the brief the assistant writes, which the job's page shows.
 */
@Component
@RequiredArgsConstructor
class OfferTools implements McpToolSet {

	/** The most a text field keeps, and the most items a list does: a brief is read before a call. */
	private static final int TEXT_MAX_LENGTH = 6000;

	private static final int ITEMS_MAX = 15;

	/** The most countries the market sources are asked about at once, like the salaries page. */
	static final int MAX_COUNTRIES = 12;

	private static final String DEFAULT_CURRENCY = "EUR";

	private final Profiles profiles;

	private final OfferService offers;

	private final MarketSalaryService market;

	@Override
	public List<McpTool> tools() {
		return List.of(listOffers(), getMarketSalaries(), saveNegotiationBrief());
	}

	private McpTool listOffers() {
		return new McpTool("list_offers", "List the offers",
				"The profile's jobs at Offer: the terms typed in (base salary, bonus, equity, signing bonus, time off, "
						+ "benefits, start, answer-by date, notes) and the yearly salary in one currency (the posting's when "
						+ "the terms give none).",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"currency", string("ISO 4217; default EUR"))),
				true, false, call -> {
					long userId = this.profiles.of(call);
					String currency = currency(call.string("currency"));
					OfferComparison comparison = this.offers.compare(userId, currency, SalaryPeriod.YEAR);
					return new OffersView(comparison.currency(), comparison.offers().stream().map(OfferTools::row).toList());
				});
	}

	private McpTool getMarketSalaries() {
		return new McpTool("get_market_salaries", "Get market salaries",
				"What a role pays by country, gross: the Stack Overflow Developer Survey (median and middle half, by role "
						+ "and language), Adzuna (current ads, ~18 countries, with a key) and Eurostat (mean for ICT "
						+ "professionals, Europe). An unknown role returns the known ones.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"role", string("As the survey names it, e.g. Developer, back-end; Engineering manager"),
						"skill", string("A language, e.g. Java; default any"),
						"countries", strings("ISO 3166 codes, max " + MAX_COUNTRIES + ", e.g. RO, DE"),
						"currency", string("ISO 4217; default EUR"),
						"period", oneOf(new SalaryPeriod[] { SalaryPeriod.YEAR, SalaryPeriod.MONTH, SalaryPeriod.HOUR },
								"Default YEAR")),
						"countries"),
				true, true, call -> {
					this.profiles.of(call);
					String role = call.string("role");
					String skill = call.string("skill");
					List<String> countries = call.strings("countries")
						.stream()
						.map(code -> code.strip().toUpperCase(Locale.ROOT))
						.filter(code -> code.length() == 2)
						.distinct()
						.limit(MAX_COUNTRIES)
						.toList();
					if (countries.isEmpty()) {
						throw new ToolException("countries needs at least one ISO 3166 two-letter code, e.g. DE");
					}
					SalaryPeriod period = (call.string("period") != null) ? call.constant("period", SalaryPeriod.class)
							: SalaryPeriod.YEAR;
					MarketSalaries found = this.market.find(new MarketQuery(role, skill, countries),
							currency(call.string("currency")), period);
					List<String> roles = this.market.roles();
					boolean roleKnown = role == null || roles.stream().anyMatch(known -> known.equalsIgnoreCase(role));
					return new MarketView(found.role(), found.skill(), found.currency(), found.period(),
							found.countries().stream().map(OfferTools::country).toList(), found.survey().imported(),
							found.survey().year(), found.adzunaConfigured(), found.adzunaSearch(), found.eurostatYear(),
							rounded(found.euAverage()), roleKnown ? List.of() : roles);
				});
	}

	private McpTool saveNegotiationBrief() {
		return new McpTool("save_negotiation_brief", "Save a negotiation brief",
				"Saves the brief to negotiate a job's offer, replacing its previous one. Only figures from "
						+ "get_market_salaries, list_offers and pages you read; no offer the profile doesn't have.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer(),
						"position", string("The offer against the market in its country and the other offers, with "
								+ "figures; a paragraph"),
						"target", string("The figure to ask for and why it's fair, 1-2 sentences"),
						"walk_away", string("The least worth accepting, and why"),
						"leverage", strings("What makes the ask fair: figures, other offers, what the person brings"),
						"asks", strings("Other asks if the salary won't move: signing bonus, time off, remote days, title, "
								+ "start date, early review"),
						"script", string("What to say on the call, a few lines, in the person's voice"),
						"email", string("Asking for it, greeting to sign-off"),
						"watch_out", string("Omit if nothing"),
						"sources", strings("Sources with their years, and URLs read")),
						"job_id", "position"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					long jobId = call.requiredNumber("job_id");
					NegotiationBrief saved = this.offers.saveBrief(userId, jobId, NegotiationBrief.builder()
						.position(shortened(call.requiredString("position")))
						.target(shortened(call.string("target")))
						.walkAway(shortened(call.string("walk_away")))
						.leverage(items(call.strings("leverage")))
						.asks(items(call.strings("asks")))
						.script(shortened(call.string("script")))
						.email(shortened(call.string("email")))
						.watchOut(shortened(call.string("watch_out")))
						.sources(items(call.strings("sources")))
						.build());
					return Map.of("job_id", jobId, "leverage", saved.leverage().size(), "asks", saved.asks().size(),
							"page", "/applications/" + jobId);
				});
	}

	private static String currency(@Nullable String currency) {
		return (currency == null) ? DEFAULT_CURRENCY : currency.toUpperCase(Locale.ROOT);
	}

	private static OfferRow row(ComparedOffer offer) {
		JobOffer terms = offer.terms();
		return new OfferRow(offer.application().id(), offer.application().title(), offer.application().company(),
				offer.application().location(), offer.salaryText(), offer.salaryFromPosting(),
				(offer.salary() != null) ? rounded(offer.salary().low()) : null,
				(offer.salary() != null) ? rounded(offer.salary().high()) : null, terms.bonus(), terms.equity(),
				terms.signingBonus(), terms.timeOff(), terms.benefits(), terms.startDate(), terms.respondBy(),
				terms.notes(), offer.fitScore());
	}

	private static CountryView country(MarketCountry country) {
		return new CountryView(country.isWorld() ? "ALL" : country.country(),
				country.figures().stream().map(OfferTools::figure).toList());
	}

	private static FigureView figure(MarketFigure figure) {
		return new FigureView(figure.source(), figure.statistic(), figure.year(), rounded(figure.lowerQuartile()),
				rounded(figure.middle()), rounded(figure.upperQuartile()), figure.sampleSize());
	}

	private static @Nullable BigDecimal rounded(@Nullable BigDecimal amount) {
		return (amount == null) ? null : amount.setScale(0, RoundingMode.HALF_UP);
	}

	private static List<String> items(List<String> items) {
		return items.stream().limit(ITEMS_MAX).map(OfferTools::shortened).toList();
	}

	private static @Nullable String shortened(@Nullable String text) {
		return (text == null || text.length() <= TEXT_MAX_LENGTH) ? text : text.substring(0, TEXT_MAX_LENGTH - 1) + "…";
	}

	record OffersView(String currency, List<OfferRow> offers) {
	}

	/**
	 * @param salary the text the yearly figures are read from: the offer's base salary, or the posting's
	 * @param salaryFromPosting whether it's the posting's, as the offer's terms give none
	 * @param yearlyLow the salary per year in the currency asked for; none when it can't be read
	 * @param fitScore how well the profile fits the job, from 1 to 5, when scored
	 */
	record OfferRow(long jobId, String title, @Nullable String company, @Nullable String location,
			@Nullable String salary, boolean salaryFromPosting, @Nullable BigDecimal yearlyLow,
			@Nullable BigDecimal yearlyHigh, @Nullable String bonus, @Nullable String equity,
			@Nullable String signingBonus, @Nullable String timeOff, @Nullable String benefits,
			@Nullable LocalDate startDate, @Nullable LocalDate respondBy, @Nullable String notes,
			@Nullable BigDecimal fitScore) {
	}

	/**
	 * @param countries each country asked for, then ALL for every country together when the survey has it
	 * @param surveyImported whether the Stack Overflow survey's answers are in JobWarden; without them it gives no survey
	 * figures, and the profile imports them on the salaries page
	 * @param euAverage Eurostat's mean for the European Union
	 * @param roles the roles the survey knows, when the one asked for isn't among them
	 */
	record MarketView(@Nullable String role, @Nullable String skill, String currency, SalaryPeriod period,
			List<CountryView> countries, boolean surveyImported, @Nullable Integer surveyYear, boolean adzunaConfigured,
			String adzunaSearch, @Nullable Integer eurostatYear, @Nullable BigDecimal euAverage, List<String> roles) {
	}

	record CountryView(String country, List<FigureView> figures) {
	}

	/**
	 * @param middle the median or the mean, as statistic says
	 * @param sampleSize how many answers or job ads it's from
	 */
	record FigureView(MarketSource source, MarketStatistic statistic, int year, @Nullable BigDecimal lowerQuartile,
			BigDecimal middle, @Nullable BigDecimal upperQuartile, @Nullable Integer sampleSize) {
	}

}
