package com.jobwarden.web.view;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

import com.jobwarden.cv.dto.CoverLetter;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * A cover letter as a page and as a PDF: the CV page's name and contact details at the top, the date, the job it's
 * for, the letter, and the name under it. Made like the CV's ({@link CvPdf}), from {@code applications/letter-document}.
 */
@Component
@RequiredArgsConstructor
public class LetterPdf {

	private final CvPdf pdf;

	private final Dates dates;

	/**
	 * What the letter's template reads: today's date is written out here, as the PDF has no beans to ask.
	 */
	public Map<String, Object> variables(CoverLetter letter, CvDetails cv, ApplicationDetails job) {
		return Map.of(ModelAttributes.COVER_LETTER, letter, ModelAttributes.CV_DETAILS, cv,
				ModelAttributes.JOB_APPLICATION, job, ModelAttributes.LETTER_DATE, this.dates.date(LocalDate.now(ZoneId.systemDefault())));
	}

	public byte[] render(CoverLetter letter, CvDetails cv, ApplicationDetails job, Locale locale) {
		return this.pdf.render(Views.COVER_LETTER_DOCUMENT, variables(letter, cv, job), locale);
	}

}
