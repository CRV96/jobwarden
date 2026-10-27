package com.jobwarden.web.controller;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(HomeController.class)
class HomeControllerTests {

	@Autowired
	private MockMvcTester mvc;

	// Every page controller extends BaseController, which needs them for the sidebar
	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@Test
	void redirectsToApplications() {
		assertThat(this.mvc.get().uri("/")).hasStatus(HttpStatus.FOUND)
			.headers()
			.hasValue(HttpHeaders.LOCATION, "/applications");
	}

}
