package com.jobwarden.jobs.service.impl;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.jobs.dto.PngImage;
import com.jobwarden.jobs.entity.ApplicationEntity;
import com.jobwarden.jobs.entity.CompanyLogoEntity;
import com.jobwarden.jobs.entity.SourceIconEntity;
import com.jobwarden.jobs.entity.SourceIconKey;
import com.jobwarden.jobs.repository.CompanyLogoRepository;
import com.jobwarden.jobs.repository.LogoVersion;
import com.jobwarden.jobs.repository.SourceIconRepository;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/**
 * The jobs' images: each site's icon, kept once per profile and shown next to the site, and each job's company logo.
 * The application service keeps them as jobs are captured, and hands them out; transactions are its.
 */
@Component
@CustomLog
@RequiredArgsConstructor
class JobImageStore {

	private static final String WWW_PREFIX = "www.";

	private final SourceIconRepository sourceIcons;

	private final CompanyLogoRepository companyLogos;

	Optional<PngImage> findSourceIcon(long userId, String source) {
		return this.sourceIcons.findById(new SourceIconKey(userId, source)).map(icon -> new PngImage(icon.getImage()));
	}

	Set<String> findSourcesWithIcon(long userId) {
		return this.sourceIcons.findSources(userId);
	}

	Optional<PngImage> findCompanyLogo(long userId, long applicationId) {
		return this.companyLogos.findOwned(userId, applicationId).map(logo -> new PngImage(logo.getImage()));
	}

	Map<Long, Long> findLogoVersions(long userId) {
		Map<Long, Long> versions = new HashMap<>();
		for (LogoVersion logo : this.companyLogos.findVersions(userId)) {
			versions.put(logo.applicationId(), logo.updatedAt().getEpochSecond());
		}
		return versions;
	}

	/**
	 * The site the posting came from, e.g. {@code linkedin.com}.
	 */
	static @Nullable String sourceOf(@Nullable String link) {
		if (link == null) {
			return null;
		}
		try {
			String host = URI.create(link).getHost();
			return (host != null && host.startsWith(WWW_PREFIX)) ? host.substring(WWW_PREFIX.length()) : host;
		} catch (IllegalArgumentException _) {
			// Not a link Java can parse. The source is optional, so the application is saved without one.
			return null;
		}
	}

	/**
	 * Keeps the icon for the site, replacing the one it had. Nothing without an icon, or without a site to keep it for.
	 */
	void saveSourceIcon(long userId, @Nullable String source, @Nullable PngImage icon) {
		if (source == null || icon == null) {
			return;
		}
		SourceIconKey key = new SourceIconKey(userId, source);
		this.sourceIcons.findById(key)
			.ifPresentOrElse(existing -> existing.changeImage(icon.png()),
					() -> this.sourceIcons.save(new SourceIconEntity(key, icon.png())));
		log.debug("Kept the icon of {} for profile {}", source, userId);
	}

	/**
	 * Keeps the application's company logo, replacing the one it had. Nothing without a logo. It takes the application
	 * itself, which the caller found or made for its profile, so no other profile's logo can be reached by an id.
	 */
	void saveCompanyLogo(ApplicationEntity application, @Nullable PngImage logo) {
		if (logo == null) {
			return;
		}
		long applicationId = application.getId();
		this.companyLogos.findById(applicationId)
			.ifPresentOrElse(existing -> existing.changeImage(logo.png()),
					() -> this.companyLogos.save(new CompanyLogoEntity(applicationId, logo.png())));
		log.debug("Kept the company logo of application {} for profile {}", applicationId, application.getUserId());
	}

	/**
	 * Removes the application's company logo, if it has one: its initials show instead.
	 */
	void deleteCompanyLogo(ApplicationEntity application) {
		this.companyLogos.findById(application.getId()).ifPresent(this.companyLogos::delete);
	}

}
