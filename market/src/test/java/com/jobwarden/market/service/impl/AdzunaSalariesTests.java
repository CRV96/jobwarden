package com.jobwarden.market.service.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.jobwarden.market.enums.AdzunaKeyCheck;
import com.jobwarden.market.service.impl.AdzunaKeys.Key;
import com.jobwarden.market.service.impl.AdzunaSalaries.AdsFigure;
import com.sun.net.httpserver.HttpServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AdzunaSalariesTests {

	private static final String HISTOGRAM = """
			{"__CLASS__": "Adzuna::API::Response::SalaryHistogram",
			 "histogram": {"20000": 10, "40000": 20, "60000": 10}}""";

	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (this.server != null) {
			this.server.stop(0);
		}
	}

	@Test
	void readsTheQuartilesFromTheBandsOfTheHistogram() {
		AdsFigure figure = AdzunaSalaries.fromHistogram(JsonMapper.builder().build().readTree(HISTOGRAM), "GBP");

		// 40 ads: 10 from 20,000 to 40,000, 20 from 40,000 to 60,000, 10 from 60,000 on, as wide as the band before
		assertThat(figure.ads()).isEqualTo(40);
		assertThat(figure.currency()).isEqualTo("GBP");
		assertThat(figure.lowerQuartile()).isEqualByComparingTo(new BigDecimal("40000"));
		assertThat(figure.median()).isEqualByComparingTo(new BigDecimal("50000"));
		assertThat(figure.upperQuartile()).isEqualByComparingTo(new BigDecimal("60000"));
	}

	@Test
	void needsEnoughAdsWithASalary() {
		assertThat(AdzunaSalaries.fromHistogram(JsonMapper.builder().build().readTree("{\"histogram\": {\"30000\": 4}}"),
				"EUR")).isNull();
	}

	@Test
	void asksOnlyWithAKeyAndKeepsTheAnswer() throws IOException {
		List<String> asked = new ArrayList<>();
		URI api = serve(200, HISTOGRAM, asked);

		assertThat(new AdzunaSalaries(keys(null), api).find("GB", "java developer")).isEmpty();

		AdzunaSalaries adzuna = new AdzunaSalaries(keys(new Key("id", "key")), api);
		assertThat(adzuna.find("GB", "java developer")).hasValueSatisfying(figure -> assertThat(figure.ads()).isEqualTo(40));
		assertThat(adzuna.find("GB", "Java developer")).isPresent();
		// Romania has no Adzuna site
		assertThat(adzuna.find("RO", "java developer")).isEmpty();
		assertThat(asked).singleElement().asString()
			.startsWith("/v1/api/jobs/gb/histogram?app_id=id&app_key=key&what=java+developer");
	}

	@Test
	void asksAgainWithANewKey() throws IOException {
		List<String> asked = new ArrayList<>();
		AdzunaKeys keys = keys(new Key("old", "key"));
		AdzunaSalaries adzuna = new AdzunaSalaries(keys, serve(200, HISTOGRAM, asked));
		adzuna.find("GB", "java developer");

		given(keys.current()).willReturn(Optional.of(new Key("new", "key")));
		adzuna.forgetAnswers();

		assertThat(adzuna.find("GB", "java developer")).isPresent();
		assertThat(asked).hasSize(2).last().asString().contains("app_id=new&");
	}

	@Test
	void checksAKeyBeforeItsSaved() throws IOException {
		List<String> asked = new ArrayList<>();
		Key key = new Key("id", "key");

		assertThat(new AdzunaSalaries(keys(null), serve(200, HISTOGRAM, asked)).check(key))
			.isEqualTo(AdzunaKeyCheck.ACCEPTED);
		assertThat(asked).singleElement().asString().startsWith("/v1/api/jobs/gb/histogram?app_id=id&app_key=key&");
		this.server.stop(0);

		assertThat(new AdzunaSalaries(keys(null), serve(401, "Unauthorized", asked)).check(key))
			.isEqualTo(AdzunaKeyCheck.REFUSED);
		this.server.stop(0);
		assertThat(new AdzunaSalaries(keys(null), serve(400, "{\"exception\": \"AUTH_FAIL\"}", asked)).check(key))
			.isEqualTo(AdzunaKeyCheck.REFUSED);
		this.server.stop(0);
		assertThat(new AdzunaSalaries(keys(null), serve(503, "Down for maintenance", asked)).check(key))
			.isEqualTo(AdzunaKeyCheck.UNCHECKED);
		// Nothing listens there
		assertThat(new AdzunaSalaries(keys(null), URI.create("http://127.0.0.1:1/v1/api")).check(key))
			.isEqualTo(AdzunaKeyCheck.UNCHECKED);
	}

	@Test
	void searchesAdsTheWayJobTitlesSayIt() {
		assertThat(AdzunaSearch.of("Developer, back-end", "Java")).isEqualTo("java developer");
		assertThat(AdzunaSearch.of("Developer, back-end", null)).isEqualTo("backend developer");
		assertThat(AdzunaSearch.of("Data engineer", " ")).isEqualTo("data engineer");
		assertThat(AdzunaSearch.of(null, null)).isEqualTo("software developer");
	}

	/** A stand-in for Adzuna that answers every request the same, and notes what was asked. */
	private URI serve(int status, String answer, List<String> asked) throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", exchange -> {
			asked.add(exchange.getRequestURI().toString());
			byte[] body = answer.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		this.server.start();
		return URI.create("http://127.0.0.1:" + this.server.getAddress().getPort() + "/v1/api");
	}

	private static AdzunaKeys keys(@Nullable Key key) {
		AdzunaKeys keys = mock(AdzunaKeys.class);
		given(keys.current()).willReturn(Optional.ofNullable(key));
		return keys;
	}

}
