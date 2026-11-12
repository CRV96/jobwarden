package com.jobwarden.web.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.service.CoverLetterService;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.JobFit;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.InterviewBriefService;
import com.jobwarden.jobs.service.JobFitService;
import com.jobwarden.jobs.service.OfferService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.view.AssistantCard;
import com.jobwarden.web.view.AssistantCard.State;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * What the AI Assistant has for a job, at a glance, under its facts: the fit, its score and why, and each card (the
 * AI-proof CV, the cover letter, the messages, the interview prep, and at Offer the negotiation), made, in progress or
 * not yet, each a link to its card. HTMX loads it after the job, and again while the assistant is on one.
 */
@Controller
@RequestMapping(ControllerConstants.APPLICATIONS + "/{id}/assistant")
@RequiredArgsConstructor
class JobAssistantController extends BaseController {

	private final ApplicationService applications;

	private final DiscoveryService discovery;

	private final JobFitService fits;

	private final CvService cvs;

	private final CoverLetterService letters;

	private final InterviewBriefService briefs;

	private final OfferService offers;

	@GetMapping
	String overview(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		ApplicationDetails job = this.applications.findById(userId, id).orElseThrow(() -> new ApplicationNotFoundException(id));
		Optional<JobFit> fit = this.fits.find(userId, id);
		List<AssistantCard> cards = new ArrayList<>();
		cards.add(card(userId, id, "fit.title", "job-fit", AiRequestKind.SCORE_JOB, fit.isPresent()));
		cards.add(card(userId, id, "tailoredCv.title", "tailored-cv", AiRequestKind.TAILOR_CV,
				this.cvs.findTailored(userId, id).isPresent()));
		cards.add(card(userId, id, "letter.title", "cover-letter", AiRequestKind.WRITE_COVER_LETTER,
				this.letters.findLetter(userId, id).isPresent()));
		cards.add(card(userId, id, "outreach.title", "outreach", AiRequestKind.WRITE_OUTREACH,
				!this.letters.findDrafts(userId, id).isEmpty()));
		cards.add(card(userId, id, "prep.title", "interview-brief", AiRequestKind.PREPARE_INTERVIEW,
				this.briefs.find(userId, id).isPresent()));
		// The negotiation's card is there only while the job is at Offer
		if (job.status() == ApplicationStatus.OFFER) {
			cards.add(card(userId, id, "negotiation.title", "negotiation", AiRequestKind.NEGOTIATE_OFFER,
					this.offers.findBrief(userId, id).isPresent()));
		}
		model.addAttribute(ModelAttributes.OVERVIEW_JOB_ID, id);
		model.addAttribute(ModelAttributes.JOB_FIT, fit.orElse(null));
		model.addAttribute(ModelAttributes.OVERVIEW_CARDS, cards);
		model.addAttribute(ModelAttributes.OVERVIEW_WORKING, cards.stream().anyMatch(card -> card.state() == State.WORKING));
		return Views.JOB_ASSISTANT_FRAGMENT;
	}

	/** In progress while its latest request is open, whatever was made before; else made, or not yet. */
	private AssistantCard card(long userId, long jobId, String title, String anchor, AiRequestKind kind, boolean made) {
		Optional<AiRequest> latest = this.discovery.findLatestRequest(userId, kind, jobId);
		boolean working = latest.filter(request -> request.status().isOpen()).isPresent();
		return new AssistantCard(title, anchor, working ? State.WORKING : (made ? State.DONE : State.NONE));
	}

}
