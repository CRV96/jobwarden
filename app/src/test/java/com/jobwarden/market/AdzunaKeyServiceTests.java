package com.jobwarden.market;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.jobs.service.ExchangeRateService;
import com.jobwarden.market.dto.AdzunaKey;
import com.jobwarden.market.enums.AdzunaKeyCheck;
import com.jobwarden.market.enums.AdzunaKeySource;
import com.jobwarden.market.service.AdzunaKeyService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The market module with a key in the environment, and a stand-in for Adzuna that turns down the app ID "unknown".
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class AdzunaKeyServiceTests {

	private static final List<String> ASKED = new CopyOnWriteArrayList<>();

	private static final HttpServer ADZUNA = adzuna();

	@Autowired
	private AdzunaKeyService adzunaKeys;

	@Autowired
	private JdbcClient jdbc;

	@MockitoBean
	private ExchangeRateService exchangeRates;

	@DynamicPropertySource
	static void sources(DynamicPropertyRegistry properties) {
		properties.add("jobwarden.market.adzuna.url",
				() -> "http://127.0.0.1:" + ADZUNA.getAddress().getPort() + "/v1/api");
		properties.add("jobwarden.market.adzuna.app-id", () -> "environment");
		properties.add("jobwarden.market.adzuna.app-key", () -> "environment-key-0042");
		properties.add("jobwarden.market.eurostat.url", () -> "http://127.0.0.1:1/eurostat");
	}

	@AfterAll
	static void stopAdzuna() {
		ADZUNA.stop(0);
	}

	@BeforeEach
	@AfterEach
	void noSavedKey() {
		this.adzunaKeys.remove();
		ASKED.clear();
	}

	@Test
	void usesTheEnvironmentsKeyUntilOneIsSaved() {
		assertThat(this.adzunaKeys.find())
			.isEqualTo(new AdzunaKey(AdzunaKeySource.ENVIRONMENT, "environment", "0042", null));

		assertThat(this.adzunaKeys.save(" mine ", " my-secret-key-9876 ")).isEqualTo(AdzunaKeyCheck.ACCEPTED);

		// Only the end of the key shows
		assertThat(this.adzunaKeys.find()).satisfies(key -> {
			assertThat(key.source()).isEqualTo(AdzunaKeySource.SETTINGS);
			assertThat(key.appId()).isEqualTo("mine");
			assertThat(key.keyEnding()).isEqualTo("9876");
			assertThat(key.savedAt()).isNotNull();
		});
		assertThat(ASKED).singleElement().asString().contains("app_id=mine&app_key=my-secret-key-9876&");
		assertThat(this.jdbc.sql("SELECT app_key FROM market_adzuna_key").query(String.class).single())
			.isEqualTo("my-secret-key-9876");

		this.adzunaKeys.remove();

		assertThat(this.adzunaKeys.find().source()).isEqualTo(AdzunaKeySource.ENVIRONMENT);
	}

	@Test
	void keepsNothingAdzunaTurnsDown() {
		assertThat(this.adzunaKeys.save("unknown", "made-up")).isEqualTo(AdzunaKeyCheck.REFUSED);

		assertThat(this.adzunaKeys.find().source()).isEqualTo(AdzunaKeySource.ENVIRONMENT);
		assertThat(this.jdbc.sql("SELECT count(*) FROM market_adzuna_key").query(Long.class).single()).isZero();
	}

	@Test
	void replacesTheSavedKey() {
		this.adzunaKeys.save("first", "first-key");
		this.adzunaKeys.save("second", "second-key");

		assertThat(this.adzunaKeys.find().appId()).isEqualTo("second");
		assertThat(this.jdbc.sql("SELECT count(*) FROM market_adzuna_key").query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void needsBothHalvesOfTheKey() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.adzunaKeys.save("mine", " "));
		assertThat(ASKED).isEmpty();
	}

	private static HttpServer adzuna() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> {
				String request = exchange.getRequestURI().toString();
				ASKED.add(request);
				boolean known = !request.contains("app_id=unknown&");
				byte[] body = (known ? "{\"histogram\": {\"40000\": 10}}" : "{\"exception\": \"AUTH_FAIL\"}")
					.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(known ? 200 : 401, body.length);
				exchange.getResponseBody().write(body);
				exchange.close();
			});
			server.start();
			return server;
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
