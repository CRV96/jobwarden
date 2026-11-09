package com.jobwarden.web.controller;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.jobs.dto.JobStatistics;
import com.jobwarden.jobs.dto.JobStatistics.ReplyRate;
import com.jobwarden.jobs.enums.FitVerdict;
import com.jobwarden.jobs.service.StatisticsService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * How the profile's applications went, without AI: how often they got an answer, and an interview, by site and by who
 * applied; whether the jobs Claude scored high led to interviews more often than the others (the calibration); what the
 * fits and the tailored CVs found missing most often; and why the profile was turned down.
 */
@Controller
@RequestMapping(ControllerConstants.STATISTICS)
@RequiredArgsConstructor
class StatisticsController extends BaseController {

	/** How many applications a group needs before the calibration compares it: fewer say little. */
	static final int CALIBRATION_MIN_SENT = 3;

	/** How many points apart the interview rates must be to say the scores tell something. */
	static final int CALIBRATION_GAP = 10;

	/** How many of the keywords the tailored CVs couldn't claim the page shows, the most frequent. */
	static final int MISSING_KEYWORDS = 15;

	private final StatisticsService statistics;

	private final CvService cvs;

	@GetMapping
	String page(Model model) {
		long userId = requireProfile(model).id();
		JobStatistics found = this.statistics.find(userId);
		model.addAttribute(ModelAttributes.STATISTICS, found);
		model.addAttribute(ModelAttributes.CALIBRATION, calibration(found.byFit()));
		model.addAttribute(ModelAttributes.MISSING_KEYWORDS, missingKeywords(this.cvs.findAllTailored(userId)));
		addImages(model, userId);
		return Views.STATISTICS;
	}

	/**
	 * Whether the strong fits got to an interview more often than the partial and weak ones: WORKS when clearly more,
	 * BACKWARDS when clearly less, FLAT when about as often, FEW while either has too few applications to tell.
	 */
	static String calibration(List<ReplyRate> byFit) {
		Optional<ReplyRate> strong = byFit.stream().filter(rate -> FitVerdict.STRONG.name().equals(rate.key())).findFirst();
		List<ReplyRate> others = byFit.stream()
			.filter(rate -> rate.key() != null && !FitVerdict.STRONG.name().equals(rate.key()))
			.toList();
		int othersSent = others.stream().mapToInt(ReplyRate::sent).sum();
		if (strong.isEmpty() || strong.get().sent() < CALIBRATION_MIN_SENT || othersSent < CALIBRATION_MIN_SENT) {
			return "FEW";
		}
		int othersInterviewed = others.stream().mapToInt(ReplyRate::interviewed).sum();
		int gap = strong.get().interviewPercent() - (int) Math.round(100.0 * othersInterviewed / othersSent);
		if (gap >= CALIBRATION_GAP) {
			return "WORKS";
		}
		return (gap <= -CALIBRATION_GAP) ? "BACKWARDS" : "FLAT";
	}

	/**
	 * The keywords the tailored CVs couldn't honestly claim, counted across them however they're capitalised, the most
	 * frequent first: shown as first written.
	 */
	static Map<String, Integer> missingKeywords(List<TailoredCv> tailored) {
		Map<String, String> wording = new LinkedHashMap<>();
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (TailoredCv cv : tailored) {
			cv.missingKeywords().stream().map(String::strip).filter(keyword -> !keyword.isEmpty()).distinct().forEach(keyword -> {
				String key = keyword.toLowerCase(Locale.ROOT);
				wording.putIfAbsent(key, keyword);
				counts.merge(key, 1, Integer::sum);
			});
		}
		return counts.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
			.limit(MISSING_KEYWORDS)
			.collect(LinkedHashMap::new, (map, count) -> map.put(wording.get(count.getKey()), count.getValue()), Map::putAll);
	}

}
