package com.jobwarden.web.controller;

import java.io.IOException;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.common.exception.CommonErrorCode;
import com.jobwarden.common.exception.JobWardenException;
import com.jobwarden.common.exception.NotFoundException;
import com.jobwarden.web.constants.ControllerConstants;
import com.jobwarden.web.constants.HtmxHeaders;
import com.jobwarden.web.user.ProfileNotSelectedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.CustomLog;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

/**
 * Maps exceptions from the page controllers to HTTP statuses. Spring Boot then renders the status with
 * templates/error.html, the same page every other error uses.
 * <p>
 * The modules' exceptions are expected (a stale link, a broken rule), so they're logged once as warnings. Anything
 * else is a bug or an outage: logged as an error with its code and stack trace, and answered with 500.
 */
@ControllerAdvice(basePackageClasses = WebExceptionHandler.class)
@CustomLog
class WebExceptionHandler {

	private final DefaultHandlerExceptionResolver springMvcExceptions = new DefaultHandlerExceptionResolver();

	/**
	 * Not an error: the page needs a profile, so the browser goes to the profile picker first.
	 */
	@ExceptionHandler(ProfileNotSelectedException.class)
	void profileNotSelected(HttpServletRequest request, HttpServletResponse response) throws IOException {
		log.debug("No profile selected for {} {}, sending the browser to the profile picker", request.getMethod(),
				request.getRequestURI());
		String profiles = request.getContextPath() + ControllerConstants.PROFILES;
		if (request.getHeader(HtmxHeaders.REQUEST) != null) {
			// A normal redirect would make HTMX swap the whole picker page into the current one
			response.setHeader(HtmxHeaders.REDIRECT, profiles);
		} else {
			response.sendRedirect(profiles);
		}
	}

	@ExceptionHandler(NotFoundException.class)
	void notFound(NotFoundException exception, HttpServletResponse response) throws IOException {
		sendError(response, HttpStatus.NOT_FOUND, exception);
	}

	@ExceptionHandler(BusinessRuleException.class)
	void businessRuleBroken(BusinessRuleException exception, HttpServletResponse response) throws IOException {
		sendError(response, HttpStatus.BAD_REQUEST, exception);
	}

	@ExceptionHandler(Exception.class)
	void unexpected(Exception exception, HttpServletRequest request, HttpServletResponse response) throws IOException {
		// Catching Exception also catches Spring MVC's own errors, e.g. text where a number was expected.
		// Spring's resolver answers those with their usual 4xx status; they aren't bugs.
		if (this.springMvcExceptions.resolveException(request, response, null, exception) != null) {
			return;
		}
		log.error(CommonErrorCode.UNEXPECTED_ERROR, "Unexpected error on {} {}", request.getMethod(),
				request.getRequestURI(), exception);
		response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value());
	}

	private void sendError(HttpServletResponse response, HttpStatus status, JobWardenException exception)
			throws IOException {
		log.warn("{}", exception.getMessage());
		response.sendError(status.value());
	}

}
