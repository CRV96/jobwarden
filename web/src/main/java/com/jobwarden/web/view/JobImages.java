package com.jobwarden.web.view;

import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.ModelAttributes;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * Which images the pages showing applications have: the sites' icons, next to each site, and the companies' logos,
 * before each job's title.
 */
@Component
@RequiredArgsConstructor
public class JobImages {

	private final ApplicationService applications;

	public void addImages(Model model, long userId) {
		model.addAttribute(ModelAttributes.ICON_SOURCES, this.applications.findSourcesWithIcon(userId));
		model.addAttribute(ModelAttributes.LOGO_APPLICATIONS, this.applications.findApplicationsWithLogo(userId));
	}

}
