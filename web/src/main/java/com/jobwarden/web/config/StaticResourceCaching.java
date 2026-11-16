package com.jobwarden.web.config;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * The pages' own files (scripts, styles, fonts, icons) carry an ETag made from their content, and the browser asks
 * with it every time it uses one ({@code Cache-Control: no-cache}, in application.properties): an unchanged file costs a
 * 304, and one an upgrade changed is fetched at once. Not Last-Modified, which application.properties turns off: the
 * reproducible build gives every file of every version the same date (project.build.outputTimestamp), so a browser
 * asking whether a file changed since then would be told no after an upgrade too, and run the old scripts.
 */
@Configuration(proxyBeanMethods = false)
class StaticResourceCaching {

	/** Where the pages' files are served from: src/main/resources/static, and the CSS the build makes */
	static final List<String> PATHS = List.of("/js/*", "/css/*", "/fonts/*", "/favicon.svg", "/favicon-32.png",
			"/apple-touch-icon.png");

	@Bean
	FilterRegistrationBean<ShallowEtagHeaderFilter> staticResourceEtags() {
		FilterRegistrationBean<ShallowEtagHeaderFilter> registration = new FilterRegistrationBean<>(
				new ShallowEtagHeaderFilter());
		registration.setUrlPatterns(PATHS);
		return registration;
	}

}
