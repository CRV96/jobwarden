package com.jobwarden.cv.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvCertification;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvEducation;
import com.jobwarden.cv.dto.CvExperience;
import com.jobwarden.cv.dto.CvFile;
import com.jobwarden.cv.dto.CvFileContent;
import com.jobwarden.cv.dto.CvLanguage;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.entity.CvDetailsEntity;
import com.jobwarden.cv.entity.CvFileEntity;
import com.jobwarden.cv.entity.TailoredCvEntity;
import com.jobwarden.cv.exception.CvErrorCode;
import com.jobwarden.cv.exception.CvFileNotFoundException;
import com.jobwarden.cv.repository.CvDetailsRepository;
import com.jobwarden.cv.repository.CvFileRepository;
import com.jobwarden.cv.repository.CvFileSummary;
import com.jobwarden.cv.repository.TailoredCvRepository;
import com.jobwarden.cv.service.CvService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@CustomLog
@RequiredArgsConstructor
class DefaultCvService implements CvService {

	private static final long MAX_FILE_BYTES = MAX_FILE_MEGABYTES * 1024L * 1024L;

	/** The longest file name it keeps: the rest goes. */
	private static final int MAX_FILE_NAME_LENGTH = 200;

	private final CvFileRepository files;

	private final CvDetailsRepository details;

	private final TailoredCvRepository tailored;

	@Override
	@Transactional(readOnly = true)
	public Cv find(long userId) {
		CvFile file = this.files.findSummary(userId).map(DefaultCvService::toCvFile).orElse(null);
		CvDetails found = this.details.findById(userId).map(DefaultCvService::toCvDetails).orElse(CvDetails.NONE);
		log.debug("Read the CV of profile {}", userId);
		return new Cv(file, found);
	}

	@Override
	@Transactional
	public CvDetails saveDetails(long userId, CvDetails changed) {
		CvDetails tidied = tidied(changed);
		CvDetailsEntity entity = this.details.findById(userId).orElseGet(() -> new CvDetailsEntity(userId));
		entity.changePersonalDetails(tidied.fullName(), tidied.headline(), tidied.email(), tidied.phone(),
				tidied.location(), tidied.website(), tidied.linkedin(), tidied.github(), tidied.summary());
		entity.changeLists(tidied.skills(), CvJson.write(tidied.experience()), CvJson.write(tidied.education()),
				CvJson.write(tidied.languages()), CvJson.write(tidied.certifications()));
		CvDetails saved = toCvDetails(this.details.saveAndFlush(entity));
		log.info("Saved the CV details of profile {}", userId);
		return saved;
	}

	/**
	 * @throws BusinessRuleException if it's empty, too large, not a PDF, a Word document or text, or not what its name
	 * says
	 */
	@Override
	@Transactional
	public CvFile uploadFile(long userId, String fileName, @Nullable String contentType, byte[] content) {
		if (content.length == 0) {
			throw new BusinessRuleException(CvErrorCode.FILE_EMPTY, "The CV file is empty");
		}
		if (content.length > MAX_FILE_BYTES) {
			throw new BusinessRuleException(CvErrorCode.FILE_TOO_LARGE, "The CV file has " + content.length + " bytes",
					String.valueOf(MAX_FILE_MEGABYTES));
		}
		String name = fileName(fileName);
		CvFileType type = CvFileType.claimedBy(name, contentType)
			.orElseThrow(() -> new BusinessRuleException(CvErrorCode.FILE_TYPE_NOT_SUPPORTED,
					"A CV file can't be " + name + " (" + contentType + ")", extension(name, contentType)));
		if (!type.matches(content)) {
			throw new BusinessRuleException(CvErrorCode.FILE_NOT_WHAT_IT_SAYS, "The CV file " + name + " isn't a " + type,
					extension(name, contentType));
		}
		String text;
		try {
			text = CvText.read(type, content);
		} catch (IOException | RuntimeException unreadable) {
			// Kept anyway: it may open fine elsewhere, and the fields can stand in for its text
			log.warn("Couldn't read the text of the CV file of profile {}: {}", userId, unreadable.toString());
			text = null;
		}
		CvFileEntity file = this.files.findById(userId).orElseGet(() -> new CvFileEntity(userId));
		file.replace(name, type.contentType(), content, text);
		this.files.save(file);
		log.info("Saved the CV file of profile {}: {} bytes, {} characters of text", userId, content.length,
				(text != null) ? text.length() : 0);
		return new CvFile(name, type.contentType(), content.length, (text != null) ? text.length() : 0, file.getUploadedAt());
	}

	/**
	 * @throws CvFileNotFoundException if there's none
	 */
	@Override
	@Transactional(readOnly = true)
	public CvFileContent downloadFile(long userId) {
		CvFileEntity file = this.files.findById(userId).orElseThrow(() -> new CvFileNotFoundException(userId));
		return new CvFileContent(file.getFileName(), file.getContentType(), file.getContent());
	}

	@Override
	@Transactional
	public void deleteFile(long userId) {
		if (this.files.existsById(userId)) {
			this.files.deleteById(userId);
			log.info("Deleted the CV file of profile {}", userId);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<String> fileText(long userId) {
		return this.files.findText(userId);
	}

	/** The entries with their main field, tidied. */
	@Override
	@Transactional
	public TailoredCv saveTailored(long userId, long applicationId, CvDetails details, List<String> matchedKeywords,
			List<String> missingKeywords, @Nullable String notes) {
		TailoredCvEntity entity = this.tailored.findByUserIdAndApplicationId(userId, applicationId)
			.orElseGet(() -> new TailoredCvEntity(userId, applicationId));
		entity.replace(CvJson.write(tidied(details)), keywords(matchedKeywords), keywords(missingKeywords), text(notes));
		TailoredCv saved = toTailoredCv(this.tailored.saveAndFlush(entity));
		log.info("Saved a CV tailored to application {} for profile {}", applicationId, userId);
		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<TailoredCv> findTailored(long userId, long applicationId) {
		return this.tailored.findByUserIdAndApplicationId(userId, applicationId).map(DefaultCvService::toTailoredCv);
	}

	@Override
	@Transactional(readOnly = true)
	public List<TailoredCv> findAllTailored(long userId) {
		return this.tailored.findByUserIdOrderByUpdatedAtDescIdDesc(userId).stream().map(DefaultCvService::toTailoredCv).toList();
	}

	@Override
	@Transactional
	public void deleteTailored(long userId, long applicationId) {
		if (this.tailored.deleteOwned(userId, applicationId) > 0) {
			log.info("Deleted the CV tailored to application {} for profile {}", applicationId, userId);
		}
	}

	/**
	 * The fields as they're kept: blank text is no value, and list entries without their main field are left out.
	 * Without the time they were saved, which the table sets.
	 */
	private static CvDetails tidied(CvDetails details) {
		return CvDetails.builder()
			.fullName(text(details.fullName()))
			.headline(text(details.headline()))
			.email(text(details.email()))
			.phone(text(details.phone()))
			.location(text(details.location()))
			.website(text(details.website()))
			.linkedin(text(details.linkedin()))
			.github(text(details.github()))
			.summary(text(details.summary()))
			.skills(keywords(details.skills()))
			.experience(entries(details.experience(), job -> new CvExperience(text(job.role()), text(job.company()),
					text(job.location()), text(job.start()), text(job.end()), text(job.description())), CvExperience::role))
			.education(entries(details.education(), school -> new CvEducation(text(school.degree()), text(school.school()),
					text(school.location()), text(school.start()), text(school.end()), text(school.description())),
					CvEducation::degree))
			.languages(entries(details.languages(), language -> new CvLanguage(text(language.language()),
					text(language.level())), CvLanguage::language))
			.certifications(entries(details.certifications(), certification -> new CvCertification(
					text(certification.name()), text(certification.issuer()), text(certification.year())),
					CvCertification::name))
			.build();
	}

	/**
	 * Each stripped, blank ones left out, and each kept once.
	 */
	private static List<String> keywords(List<String> keywords) {
		return keywords.stream().map(DefaultCvService::text).filter(keyword -> keyword != null).distinct().toList();
	}

	private static TailoredCv toTailoredCv(TailoredCvEntity entity) {
		return TailoredCv.builder()
			.applicationId(entity.getApplicationId())
			.details(CvJson.details(entity.getDetails()))
			.matchedKeywords(entity.getMatchedKeywords())
			.missingKeywords(entity.getMissingKeywords())
			.notes(entity.getNotes())
			.updatedAt(entity.getUpdatedAt())
			.build();
	}

	private static <T> List<T> entries(List<T> entries, UnaryOperator<T> tidied, Function<T, @Nullable String> mainField) {
		Predicate<T> hasMainField = entry -> mainField.apply(entry) != null;
		return entries.stream().filter(entry -> entry != null).map(tidied).filter(hasMainField).toList();
	}

	private static @Nullable String text(@Nullable String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

	/** Only the name, without the folders some browsers send, and without control characters. */
	private static String fileName(String fileName) {
		String name = fileName.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
		if (name.isEmpty()) {
			return "cv";
		}
		return (name.length() <= MAX_FILE_NAME_LENGTH) ? name : name.substring(name.length() - MAX_FILE_NAME_LENGTH);
	}

	private static String extension(String fileName, @Nullable String contentType) {
		int dot = fileName.lastIndexOf('.');
		if (dot >= 0 && dot < fileName.length() - 1) {
			return fileName.substring(dot);
		}
		return (contentType != null) ? contentType : fileName;
	}

	private static CvFile toCvFile(CvFileSummary file) {
		return new CvFile(file.fileName(), file.contentType(), file.sizeBytes(),
				(file.textLength() != null) ? file.textLength() : 0, file.uploadedAt());
	}

	private static CvDetails toCvDetails(CvDetailsEntity entity) {
		return CvDetails.builder()
			.fullName(entity.getFullName())
			.headline(entity.getHeadline())
			.email(entity.getEmail())
			.phone(entity.getPhone())
			.location(entity.getLocation())
			.website(entity.getWebsite())
			.linkedin(entity.getLinkedin())
			.github(entity.getGithub())
			.summary(entity.getSummary())
			.skills(entity.getSkills())
			.experience(CvJson.experience(entity.getExperience()))
			.education(CvJson.education(entity.getEducation()))
			.languages(CvJson.languages(entity.getLanguages()))
			.certifications(CvJson.certifications(entity.getCertifications()))
			.updatedAt(entity.getUpdatedAt())
			.build();
	}

}
