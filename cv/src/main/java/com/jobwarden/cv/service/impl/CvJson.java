package com.jobwarden.cv.service.impl;

import java.util.List;

import com.jobwarden.cv.dto.CvCertification;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvEducation;
import com.jobwarden.cv.dto.CvExperience;
import com.jobwarden.cv.dto.CvLanguage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The CV's lists of entries as the JSON the {@code cv_details} table keeps: an array of objects, without the fields
 * that have no value. A tailored CV is all its fields as one object.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CvJson {

	private static final JsonMapper JSON = JsonMapper.builder()
		.changeDefaultPropertyInclusion(inclusion -> inclusion
			.withValueInclusion(JsonInclude.Include.NON_NULL))
		// What a record's methods add, e.g. CvDetails.isFilledIn(), is written but not read back
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.build();

	static String write(List<?> entries) {
		return JSON.writeValueAsString(entries);
	}

	/**
	 * Every field of a CV as one object, for the {@code tailored_cvs} table.
	 */
	static String write(CvDetails details) {
		return JSON.writeValueAsString(details);
	}

	static CvDetails details(String json) {
		return JSON.readValue(json, CvDetails.class);
	}

	static List<CvExperience> experience(String json) {
		return JSON.readValue(json, new TypeReference<>() {
		});
	}

	static List<CvEducation> education(String json) {
		return JSON.readValue(json, new TypeReference<>() {
		});
	}

	static List<CvLanguage> languages(String json) {
		return JSON.readValue(json, new TypeReference<>() {
		});
	}

	static List<CvCertification> certifications(String json) {
		return JSON.readValue(json, new TypeReference<>() {
		});
	}

}
