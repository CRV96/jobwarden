package com.jobwarden;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import com.jobwarden.common.exception.CommonErrorCode;
import com.jobwarden.common.exception.ErrorCode;
import com.jobwarden.discover.exception.DiscoverErrorCode;
import com.jobwarden.identity.enums.IdentityErrorCode;
import com.jobwarden.jobs.exception.JobsErrorCode;
import com.jobwarden.web.enums.Language;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catches a forgotten translation before a user sees a message key, or the English text, in the middle of a page.
 */
class MessageBundlesTests {

	/** Must match spring.messages.basename in application.properties. */
	private static final List<String> BUNDLES = List.of("i18n/web", "i18n/common-errors", "i18n/identity-errors",
			"i18n/jobs-errors", "i18n/discover-errors", "i18n/cv-errors", "i18n/hiring-errors", "i18n/apply-errors");

	@Test
	void everyTranslationHasExactlyTheEnglishKeys() throws IOException {
		for (String bundle : BUNDLES) {
			Properties english = load(bundle + ".properties");
			for (Language language : Language.values()) {
				if (language == Language.ENGLISH) {
					continue;
				}
				String file = bundle + "_" + language.getTag() + ".properties";
				Properties translation = load(file);
				assertThat(translation.stringPropertyNames()).as(file)
					.containsExactlyInAnyOrderElementsOf(english.stringPropertyNames());
				assertThat(translation.values()).as(file).allSatisfy(text -> assertThat(text.toString()).isNotBlank());
			}
		}
	}

	@Test
	void everyErrorCodeHasAMessage() throws IOException {
		Properties english = new Properties();
		for (String bundle : BUNDLES) {
			english.putAll(load(bundle + ".properties"));
		}
		Stream.of(CommonErrorCode.values(), IdentityErrorCode.values(), JobsErrorCode.values(), DiscoverErrorCode.values())
			.flatMap(Arrays::stream)
			.map(ErrorCode::getMessageKey)
			.forEach(key -> assertThat(english).as(key).containsKey(key));
	}

	@Test
	void theAppLoadsEveryBundle() throws IOException {
		assertThat(load("application.properties").getProperty("spring.messages.basename"))
			.isEqualTo(String.join(",", BUNDLES));
	}

	private static Properties load(String resource) throws IOException {
		try (InputStream stream = MessageBundlesTests.class.getClassLoader().getResourceAsStream(resource)) {
			assertThat(stream).as(resource).isNotNull();
			Properties properties = new Properties();
			properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
			return properties;
		}
	}

}
