package com.jobwarden.web.controller;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvFileContent;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.view.CvPdf;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The CV Claude uploads to an application form, to download as a file: the one tailored to the job, else the one built
 * from the CV page's fields, else the file uploaded there. Claude has no session: the profile is a parameter, like on
 * the MCP server, which trusts whoever can reach the server, so JobWarden stays on the profile's own machine or
 * network.
 */
@Controller
@CustomLog
@RequiredArgsConstructor
class ClaudeFilesController {

	private final ApplicationService applications;

	private final CvService cvs;

	private final CvPdf pdf;

	/**
	 * @return the PDF or the file, or 404 when the profile has no such job, or no CV at all
	 */
	@GetMapping("${jobwarden.claude.cv-path:/claude/cv}")
	ResponseEntity<byte[]> cv(@RequestParam long profile, @RequestParam long job, Locale locale) {
		Optional<ApplicationDetails> application = this.applications.findById(profile, job);
		if (application.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		String company = application.get().company();
		Optional<TailoredCv> tailored = this.cvs.findTailored(profile, job);
		Cv cv = this.cvs.find(profile);
		log.debug("Claude downloads the CV of profile {} for application {}", profile, job);
		if (tailored.isPresent() || cv.details().isFilledIn()) {
			CvDetails details = tailored.map(TailoredCv::details).orElse(cv.details());
			return Attachments.of(this.pdf.render(Map.of(ModelAttributes.CV_DETAILS, details), locale),
					MediaType.APPLICATION_PDF, Attachments.cvName(details.fullName(), tailored.isPresent() ? company : null));
		}
		if (cv.file() != null) {
			CvFileContent file = this.cvs.downloadFile(profile);
			return Attachments.of(file.content(), MediaType.parseMediaType(file.contentType()), file.fileName());
		}
		return ResponseEntity.notFound().build();
	}

}
