package com.jobwarden.market.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The Stack Overflow Developer Survey's answers: downloaded (about 140 MB of CSV) and summed up as quartiles of what
 * developers earn in a year, per country, role and programming language, and for every country, role and language
 * together. Only the answers of developers in work, with an amount between 1,000 and 1,000,000 dollars; and only
 * groups of at least five answers, so no one answer shows.
 */
@Component
@CustomLog
class StackOverflowSurvey {

	/** {@code ConvertedCompYearly}: the survey converts every amount to US dollars a year. */
	static final String CURRENCY = "USD";

	static final int MIN_ANSWERS = 5;

	private static final double LOWEST = 1_000;

	private static final double HIGHEST = 1_000_000;

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);

	/** An answer left out: the survey writes {@code NA}. */
	private static final String NOT_ANSWERED = "NA";

	private final URI address;

	private final int year;

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(CONNECT_TIMEOUT)
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	StackOverflowSurvey(@Value("${jobwarden.market.stack-overflow.url}") URI address,
			@Value("${jobwarden.market.stack-overflow.year}") int year) {
		this.address = address;
		this.year = year;
	}

	int year() {
		return this.year;
	}

	/**
	 * @throws IOException if it can't be downloaded or isn't the survey's CSV
	 */
	List<SurveyFigure> download() throws IOException, InterruptedException {
		try (InputStream answers = open()) {
			return read(new InputStreamReader(answers, StandardCharsets.UTF_8));
		}
	}

	private InputStream open() throws IOException, InterruptedException {
		if ("file".equals(this.address.getScheme())) {
			return Files.newInputStream(Path.of(this.address));
		}
		HttpResponse<InputStream> response = this.http.send(
				HttpRequest.newBuilder(this.address).timeout(DOWNLOAD_TIMEOUT).GET().build(),
				HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			response.body().close();
			throw new IOException("The survey's address answered " + response.statusCode());
		}
		return response.body();
	}

	/**
	 * @throws IOException if the header lacks one of the columns it reads
	 */
	static List<SurveyFigure> read(Reader answers) throws IOException {
		SurveyCsv csv = new SurveyCsv(new BufferedReader(answers, 1 << 16));
		List<String> header = csv.read();
		if (header == null) {
			throw new IOException("The survey's CSV is empty");
		}
		int country = column(header, "Country");
		int role = column(header, "DevType");
		int languages = column(header, "LanguageHaveWorkedWith");
		int employment = column(header, "Employment");
		int pay = column(header, "ConvertedCompYearly");

		Map<Group, Amounts> groups = new HashMap<>();
		int answersRead = 0;
		for (List<String> answer = csv.read(); answer != null; answer = csv.read()) {
			if (answer.size() < header.size()) {
				continue;
			}
			Double amount = amount(answer.get(pay));
			if (amount == null || !inWork(answer.get(employment))) {
				continue;
			}
			answersRead++;
			String code = Countries.ofSurveyName(answer.get(country));
			List<String> countries = (code != null) ? List.of(code, "") : List.of("");
			List<String> roles = answered(answer.get(role)) ? List.of(answer.get(role), "") : List.of("");
			List<String> skills = new ArrayList<>();
			if (answered(answer.get(languages))) {
				skills.addAll(Arrays.asList(answer.get(languages).split(";")));
			}
			skills.add("");
			for (String inCountry : countries) {
				for (String asRole : roles) {
					for (String withSkill : skills) {
						groups.computeIfAbsent(new Group(inCountry, asRole, withSkill), group -> new Amounts()).add(amount);
					}
				}
			}
		}
		List<SurveyFigure> figures = new ArrayList<>();
		groups.forEach((group, amounts) -> {
			if (amounts.size() >= MIN_ANSWERS) {
				Quartiles quartiles = Quartiles.of(amounts.toArray());
				figures.add(new SurveyFigure(group.country(), group.role(), group.skill(), amounts.size(),
						quartiles.lower(), quartiles.median(), quartiles.upper()));
			}
		});
		log.info("Read {} survey answers with a salary into {} figures", answersRead, figures.size());
		return figures;
	}

	private static int column(List<String> header, String name) throws IOException {
		int index = header.indexOf(name);
		if (index < 0) {
			throw new IOException("The survey's CSV has no column " + name);
		}
		return index;
	}

	private static @Nullable Double amount(String text) {
		if (!answered(text)) {
			return null;
		}
		try {
			double amount = Double.parseDouble(text);
			return (amount >= LOWEST && amount <= HIGHEST) ? amount : null;
		} catch (NumberFormatException notANumber) {
			return null;
		}
	}

	/** Employed, or working for themselves: not students, not retired, not looking for work. */
	private static boolean inWork(String employment) {
		return employment.contains("Employed") || employment.contains("Independent contractor");
	}

	private static boolean answered(String text) {
		return !text.isBlank() && !NOT_ANSWERED.equals(text);
	}

	/**
	 * What developers earn in a year, in dollars. An empty country, role or skill is all of them.
	 */
	record SurveyFigure(String country, String role, String skill, int sampleSize, BigDecimal lowerQuartile,
			BigDecimal median, BigDecimal upperQuartile) {
	}

	private record Group(String country, String role, String skill) {
	}

	/** A growing list of amounts, without boxing each. */
	private static final class Amounts {

		private double[] values = new double[8];

		private int size;

		void add(double value) {
			if (this.size == this.values.length) {
				this.values = Arrays.copyOf(this.values, this.size * 2);
			}
			this.values[this.size++] = value;
		}

		int size() {
			return this.size;
		}

		double[] toArray() {
			return Arrays.copyOf(this.values, this.size);
		}

	}

}
