package com.jobwarden.web.controller;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvFileContent;
import com.jobwarden.cv.dto.CvFilePreview;
import com.jobwarden.cv.dto.GitHubAccount;
import com.jobwarden.cv.dto.GitHubRepository;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.cv.service.GitHubService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.CvForm;
import com.jobwarden.web.view.CvPdf;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * The profile's CV: a file uploaded as it is, which Claude reads, and shown beside the fields; the fields to build one
 * from, previewed as HTML and downloaded as a PDF made from the same template; and the profile's GitHub account, whose
 * repositories it chooses for Claude to write the CV's projects from.
 */
@Controller
@RequestMapping(ControllerConstants.CV)
@CustomLog
@RequiredArgsConstructor
class CvController extends BaseController {

	private final CvService cvs;

	private final GitHubService github;

	private final DiscoveryService discovery;

	private final ApplicationService applications;

	private final CvPdf pdf;

	private final MessageSource messageSource;

	@GetMapping
	String cv(Model model) {
		long userId = requireProfile(model).id();
		return showPage(model, this.cvs.find(userId), null, Map.of(), null);
	}

	/**
	 * Saves the fields. Invalid ones show the form again, as it was typed, with the errors.
	 */
	@PostMapping
	String save(@Valid CvForm form, BindingResult result, Model model) {
		long userId = requireProfile(model).id();
		if (result.hasErrors()) {
			Map<String, String> errors = fieldErrors(result);
			log.debug("CV of profile {} not saved, fields with errors: {}", userId, errors.keySet());
			return showPage(model, this.cvs.find(userId), form.toCvDetails(), errors, null);
		}
		this.cvs.saveDetails(userId, form.toCvDetails());
		return redirectTo(ControllerConstants.CV + "?saved");
	}

	@PostMapping("/file")
	String upload(@RequestParam MultipartFile file, Model model) throws IOException {
		long userId = requireProfile(model).id();
		try {
			this.cvs.uploadFile(userId, Objects.requireNonNullElse(file.getOriginalFilename(), "cv"), file.getContentType(),
					file.getBytes());
		} catch (BusinessRuleException refused) {
			log.debug("CV file of profile {} not saved: {}", userId, refused.getMessage());
			return showPage(model, this.cvs.find(userId), null, Map.of(), message(refused));
		}
		return redirectTo(ControllerConstants.CV + "?uploaded");
	}

	@GetMapping("/file")
	ResponseEntity<byte[]> download(Model model) {
		CvFileContent file = this.cvs.downloadFile(requireProfile(model).id());
		return attachment(file.content(), MediaType.parseMediaType(file.contentType()), file.fileName());
	}

	/**
	 * The file as the CV page shows it, beside the form: a PDF as it is (the browser shows it), a Word document as
	 * HTML, a text file as its text.
	 */
	@GetMapping("/file/preview")
	String previewFile(Model model) {
		CvFilePreview preview = this.cvs.previewFile(requireProfile(model).id());
		if (preview.pdf()) {
			return redirectTo(ControllerConstants.CV + "/file/inline");
		}
		model.addAttribute(ModelAttributes.CV_FILE_PREVIEW, preview);
		return Views.CV_FILE_PREVIEW;
	}

	/**
	 * A PDF, for the browser to show; anything else is downloaded.
	 */
	@GetMapping("/file/inline")
	ResponseEntity<byte[]> showFile(Model model) {
		CvFileContent file = this.cvs.downloadFile(requireProfile(model).id());
		MediaType type = MediaType.parseMediaType(file.contentType());
		return MediaType.APPLICATION_PDF.isCompatibleWith(type) ? Attachments.inline(file.content(), type, file.fileName())
				: attachment(file.content(), type, file.fileName());
	}

	@PostMapping("/file/delete")
	String deleteFile(Model model) {
		this.cvs.deleteFile(requireProfile(model).id());
		return redirectTo(ControllerConstants.CV);
	}

	/**
	 * The CV built from the saved fields, as a page of its own: the CV page shows it beside the form.
	 */
	@GetMapping("/preview")
	String preview(Model model) {
		model.addAttribute(ModelAttributes.CV_DETAILS, this.cvs.find(requireProfile(model).id()).details());
		return Views.CV_DOCUMENT;
	}

	@GetMapping("/pdf")
	ResponseEntity<byte[]> downloadPdf(Model model, Locale locale) {
		CvDetails details = this.cvs.find(requireProfile(model).id()).details();
		byte[] document = this.pdf.render(Map.of(ModelAttributes.CV_DETAILS, details), locale);
		String name = (details.fullName() != null) ? "CV " + details.fullName() + ".pdf" : "CV.pdf";
		return attachment(document, MediaType.APPLICATION_PDF, name);
	}

	/**
	 * The GitHub card, which the CV page loads after the rest, as it asks GitHub for the repositories; and asks again
	 * every few seconds while Claude writes the projects.
	 */
	@GetMapping("/github")
	String github(Model model) {
		return showGitHub(model, requireProfile(model).id(), null);
	}

	/**
	 * Called by HTMX: connects the account, after GitHub said it's there, or the token is good. A token never goes
	 * back to the page, not even when it's refused.
	 */
	@PostMapping("/github")
	String connectGitHub(@RequestParam String username, @RequestParam(required = false) @Nullable String token,
			Model model) {
		long userId = requireProfile(model).id();
		try {
			this.github.connect(userId, username, token);
		} catch (BusinessRuleException refused) {
			model.addAttribute(ModelAttributes.GITHUB_USERNAME, username);
			return showGitHub(model, userId, message(refused));
		}
		return showGitHub(model, userId, null);
	}

	@PostMapping("/github/delete")
	String disconnectGitHub(Model model) {
		long userId = requireProfile(model).id();
		this.github.disconnect(userId);
		return showGitHub(model, userId, null);
	}

	/**
	 * Called by HTMX: the repositories ticked are those Claude may read for the CV's projects.
	 */
	@PostMapping("/github/repositories")
	String chooseRepositories(@RequestParam(name = "repository", required = false) @Nullable List<String> repositories,
			Model model, Locale locale) {
		long userId = requireProfile(model).id();
		List<String> chosen = (repositories != null) ? repositories : List.of();
		if (chosen.size() > GitHubService.MAX_REPOSITORIES) {
			return showGitHub(model, userId, this.messageSource.getMessage("cv.github.tooMany",
					new Object[] { GitHubService.MAX_REPOSITORIES }, locale));
		}
		this.github.chooseRepositories(userId, chosen);
		return showGitHub(model, userId, null);
	}

	/**
	 * Called by HTMX: asks Claude to write the CV's projects from the repositories chosen, unless it's been asked and
	 * hasn't finished.
	 */
	@PostMapping("/github/write")
	String writeProjects(Model model) {
		long userId = requireProfile(model).id();
		if (latestProjects(userId).filter(request -> request.status().isOpen()).isEmpty()) {
			this.discovery.createRequest(userId, AiRequestKind.WRITE_PROJECTS, AiRequirements.NONE);
		}
		return showGitHub(model, userId, null);
	}

	@PostMapping("/github/write/cancel")
	String cancelProjects(Model model) {
		long userId = requireProfile(model).id();
		latestProjects(userId).filter(request -> request.status().isOpen())
			.ifPresent(request -> this.discovery.cancelRequest(userId, request.id()));
		return showGitHub(model, userId, null);
	}

	/**
	 * @param error why what was asked couldn't be done, or {@code null}
	 */
	private String showGitHub(Model model, long userId, @Nullable String error) {
		Optional<GitHubAccount> account = this.github.findAccount(userId);
		model.addAttribute(ModelAttributes.GITHUB_ACCOUNT, account.orElse(null));
		String problem = error;
		List<GitHubRepository> repositories = List.of();
		if (account.isPresent()) {
			try {
				repositories = this.github.listRepositories(userId);
			} catch (BusinessRuleException unreachable) {
				problem = (problem != null) ? problem : message(unreachable);
			}
		}
		model.addAttribute(ModelAttributes.GITHUB_REPOSITORIES, repositories);
		model.addAttribute(ModelAttributes.GITHUB_ERROR, problem);
		Optional<AiRequest> request = latestProjects(userId);
		model.addAttribute(ModelAttributes.PROJECTS_REQUEST, request.orElse(null));
		model.addAttribute(ModelAttributes.PROJECTS_PROMPT,
				request.map(asked -> prompt(model, asked.id())).orElse(null));
		model.addAttribute(ModelAttributes.MAX_REPOSITORIES, GitHubService.MAX_REPOSITORIES);
		return Views.GITHUB_FRAGMENT;
	}

	private Optional<AiRequest> latestProjects(long userId) {
		return this.discovery.findLatestRequest(userId, AiRequestKind.WRITE_PROJECTS);
	}

	/**
	 * @param shown the fields to show: those typed, when they didn't save; else the CV's own
	 * @param uploadError why the file wasn't kept, or {@code null}
	 */
	private String showPage(Model model, Cv cv, @Nullable CvDetails shown, Map<String, String> fieldErrors,
			@Nullable String uploadError) {
		model.addAttribute(ModelAttributes.CV, cv);
		model.addAttribute(ModelAttributes.CV_DETAILS, (shown != null) ? shown : cv.details());
		model.addAttribute(ModelAttributes.FIELD_ERRORS, fieldErrors);
		model.addAttribute(ModelAttributes.UPLOAD_ERROR, uploadError);
		model.addAttribute(ModelAttributes.CV_MAX_BYTES, CvService.MAX_FILE_MEGABYTES * 1024L * 1024L);
		long userId = requireProfile(model).id();
		List<TailoredCv> tailored = this.cvs.findAllTailored(userId);
		Set<Long> tailoredJobs = tailored.stream().map(TailoredCv::applicationId).collect(Collectors.toSet());
		model.addAttribute(ModelAttributes.TAILORED_CVS, tailored);
		model.addAttribute(ModelAttributes.TAILORED_JOBS, tailoredJobs.isEmpty() ? Map.of()
				: this.applications.findAll(userId, null)
					.stream()
					.filter(job -> tailoredJobs.contains(job.id()))
					.collect(Collectors.toMap(ApplicationSummary::id, Function.identity())));
		return Views.CV;
	}

}
