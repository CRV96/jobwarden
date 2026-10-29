package com.jobwarden.web.controller;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvFileContent;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.service.CvService;
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
 * The profile's CV: a file uploaded as it is, which Claude reads; and the fields to build one from, previewed as HTML
 * and downloaded as a PDF made from the same template.
 */
@Controller
@RequestMapping(ControllerConstants.CV)
@CustomLog
@RequiredArgsConstructor
class CvController extends BaseController {

	private final CvService cvs;

	private final ApplicationService applications;

	private final CvPdf pdf;

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
