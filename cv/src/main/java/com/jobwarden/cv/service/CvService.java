package com.jobwarden.cv.service;

import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvFile;
import com.jobwarden.cv.dto.CvFileContent;
import com.jobwarden.cv.dto.TailoredCv;
import org.jspecify.annotations.Nullable;

/**
 * Every method takes the owning user's id, so one profile can never read or change another's CV.
 */
public interface CvService {

	/** The largest file it keeps, in megabytes. Also {@code spring.servlet.multipart.max-file-size}. */
	int MAX_FILE_MEGABYTES = 5;

	Cv find(long userId);

	/**
	 * Replaces the fields. Blank text is no value, and list entries without their main field (a job's role, a
	 * degree...) are left out.
	 */
	CvDetails saveDetails(long userId, CvDetails details);

	/**
	 * Keeps the file, replacing the one there was, and reads its text out for Claude.
	 *
	 * @param contentType as the browser sent it; the file's extension decides when it's missing or generic
	 * @throws com.jobwarden.common.exception.BusinessRuleException if it's empty, too large, or not a PDF, a Word
	 * document or text
	 */
	CvFile uploadFile(long userId, String fileName, @Nullable String contentType, byte[] content);

	/**
	 * @throws com.jobwarden.cv.exception.CvFileNotFoundException if there's none
	 */
	CvFileContent downloadFile(long userId);

	/**
	 * Deletes the file, if there's one.
	 */
	void deleteFile(long userId);

	/**
	 * @return the text read out of the uploaded file; empty without a file, or when none could be read
	 */
	Optional<String> fileText(long userId);

	/**
	 * Keeps the CV Claude rewrote for a job, replacing the one there was for it. Its fields are tidied like
	 * {@link #saveDetails}'s; the keywords are stripped, and each kept once.
	 *
	 * @param applicationId a job of the profile's: the caller makes sure it is
	 */
	TailoredCv saveTailored(long userId, long applicationId, CvDetails details, List<String> matchedKeywords,
			List<String> missingKeywords, @Nullable String notes);

	Optional<TailoredCv> findTailored(long userId, long applicationId);

	/**
	 * @return every CV tailored to one of the profile's jobs, the latest first
	 */
	List<TailoredCv> findAllTailored(long userId);

	void deleteTailored(long userId, long applicationId);

}
