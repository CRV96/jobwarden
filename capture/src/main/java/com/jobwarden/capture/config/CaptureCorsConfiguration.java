package com.jobwarden.capture.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Who may call the extension's API from another origin: the extension itself, in Firefox and Chrome, with an id that
 * differs from one install to the next; web pages may not. Firefox applies CORS to its extensions (moz-extension://).
 * Chrome doesn't to one with the host permission, but it still sends its origin (chrome-extension://) with a POST,
 * which Spring would turn away. One mapping for the whole API, with what each endpoint answers to.
 */
@Configuration(proxyBeanMethods = false)
class CaptureCorsConfiguration implements WebMvcConfigurer {

	private static final String[] EXTENSION_ORIGINS = { "moz-extension://*", "chrome-extension://*" };

	private final String capturePath;

	CaptureCorsConfiguration(@Value("${jobwarden.capture.path}") String capturePath) {
		this.capturePath = capturePath;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping(this.capturePath + "/**")
			.allowedOriginPatterns(EXTENSION_ORIGINS)
			.allowedMethods("GET", "POST", "DELETE");
	}

}
