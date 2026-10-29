package com.jobwarden.capture.controller;

import com.jobwarden.apply.dto.ApplicationAnswers;
import com.jobwarden.apply.service.AutoApplyService;
import com.jobwarden.capture.dto.FormAnswers;
import com.jobwarden.capture.dto.LearnedAnswer;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.identity.exception.UserNotFoundException;
import com.jobwarden.identity.service.UserService;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The answers the extension's Easy Apply run fills LinkedIn's forms in with, from the CV and Auto-apply pages; and the
 * ones the user types during a run, kept among the Auto-apply page's own questions for the next forms. Like
 * {@link CaptureController}, the extensions in Firefox and Chrome may call it, and web pages may not.
 */
@RestController
@RequestMapping("${jobwarden.capture.path}/answers")
@CrossOrigin(originPatterns = { "moz-extension://*", "chrome-extension://*" })
@CustomLog
@RequiredArgsConstructor
class AnswersController {

	private final AutoApplyService autoApply;

	private final CvService cvs;

	private final UserService users;

	@GetMapping
	FormAnswers find(@RequestParam long userId) {
		requireProfile(userId);
		return toFormAnswers(userId, this.autoApply.findAnswers(userId));
	}

	/**
	 * @return the answers with the new one
	 */
	@PostMapping
	FormAnswers learn(@RequestParam long userId, @Valid @RequestBody LearnedAnswer learned) {
		requireProfile(userId);
		ApplicationAnswers answers = this.autoApply.learn(userId, learned.question(), learned.answer());
		log.info("Profile {} answered a form's question during an Easy Apply run", userId);
		return toFormAnswers(userId, answers);
	}

	private void requireProfile(long userId) {
		if (!this.users.exists(userId)) {
			throw new UserNotFoundException(userId);
		}
	}

	private FormAnswers toFormAnswers(long userId, ApplicationAnswers answers) {
		CvDetails cv = this.cvs.find(userId).details();
		return FormAnswers.builder()
			.contact(FormAnswers.Contact.builder()
				.fullName(cv.fullName())
				.email(cv.email())
				.phone(cv.phone())
				.location(cv.location())
				.linkedin(cv.linkedin())
				.github(cv.github())
				.website(cv.website())
				.build())
			.workAuthorization(answers.workAuthorization())
			.needsSponsorship(answers.needsSponsorship())
			.noticePeriod(answers.noticePeriod())
			.earliestStart(answers.earliestStart())
			.salaryExpectation(answers.salaryExpectation())
			.willingToRelocate(answers.willingToRelocate())
			.workPreference(answers.workPreference())
			.yearsOfExperience(answers.yearsOfExperience())
			.declineVoluntary(answers.declineVoluntary())
			.custom(answers.custom())
			.build();
	}

}
