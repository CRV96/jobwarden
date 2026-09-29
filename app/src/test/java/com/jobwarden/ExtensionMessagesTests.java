package com.jobwarden;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jobwarden.web.enums.Language;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The browser extension's texts, in {@code extension/_locales/<language>/messages.json}, checked like the server's
 * in {@link MessageBundlesTests}: the extension speaks the same languages, and a missing message shows its key.
 */
class ExtensionMessagesTests {

	/** Tests run in the app module's folder. */
	private static final Path EXTENSION = Path.of("..", "extension");

	private static final Path LOCALES = EXTENSION.resolve("_locales");

	/**
	 * A message named in the pages ({@code data-i18n="save"}, {@code data-i18n-placeholder="..."}), the scripts
	 * ({@code message('saved')}) or the manifest ({@code __MSG_extensionDescription__}). Names put together at run
	 * time aren't found.
	 */
	private static final Pattern MESSAGE_USE = Pattern
		.compile("data-i18n(?:-placeholder)?=\"(\\w+)\"|(?:message|getMessage)\\('(\\w+)'|__MSG_(\\w+)__");

	@Test
	void theExtensionHasExactlyTheAppsLanguages() throws IOException {
		try (Stream<Path> folders = Files.list(LOCALES)) {
			assertThat(folders.filter(Files::isDirectory).map(folder -> folder.getFileName().toString()))
				.containsExactlyInAnyOrderElementsOf(Arrays.stream(Language.values()).map(Language::getTag).toList());
		}
	}

	@Test
	void everyTranslationHasExactlyTheEnglishMessages() {
		Map<String, String> english = messages(Language.ENGLISH);
		for (Language language : Language.values()) {
			Map<String, String> translation = messages(language);
			assertThat(translation.keySet()).as(language.getTag())
				.containsExactlyInAnyOrderElementsOf(english.keySet());
			assertThat(translation.values()).as(language.getTag()).allSatisfy(text -> assertThat(text).isNotBlank());
		}
	}

	@Test
	void everyMessageTheExtensionUsesExists() throws IOException {
		Set<String> used = new HashSet<>();
		try (Stream<Path> files = Files.list(EXTENSION)) {
			for (Path file : files.filter(ExtensionMessagesTests::usesMessages).toList()) {
				Matcher matcher = MESSAGE_USE.matcher(Files.readString(file));
				while (matcher.find()) {
					used.add(Stream.of(matcher.group(1), matcher.group(2), matcher.group(3))
						.filter(Objects::nonNull)
						.findFirst()
						.orElseThrow());
				}
			}
		}
		assertThat(used).isNotEmpty();
		assertThat(messages(Language.ENGLISH)).containsKeys(used.toArray(String[]::new));
	}

	private static boolean usesMessages(Path file) {
		String name = file.getFileName().toString();
		return name.endsWith(".html") || name.endsWith(".js") || name.equals("manifest.json");
	}

	private static Map<String, String> messages(Language language) {
		JsonNode messages = JsonMapper.shared().readTree(LOCALES.resolve(language.getTag()).resolve("messages.json"));
		return messages.properties()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().path("message").asString(),
					(first, second) -> first, LinkedHashMap::new));
	}

}
