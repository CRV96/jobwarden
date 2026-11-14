package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.jobwarden.jobs.dto.JobStatistics;
import com.jobwarden.jobs.dto.JobStatistics.ReasonCount;
import com.jobwarden.jobs.dto.JobStatistics.ReplyRate;
import com.jobwarden.jobs.dto.JobStatistics.SkillGap;
import com.jobwarden.jobs.enums.AppliedBy;
import com.jobwarden.jobs.enums.FitVerdict;
import com.jobwarden.jobs.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One query reads each job's timeline as a few facts (sent, answered, interviewed, offered, when, the last rejection's
 * reason, the fit's score), and the rest is counting them. An answer is an interview, an offer or a rejection; a
 * rejection for no response is none.
 */
@Service
@RequiredArgsConstructor
class DefaultStatisticsService implements StatisticsService {

	private static final String JOBS = """
			SELECT a.source, a.applied_by, f.score,
			       bool_or(e.stage_category IN ('APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'NO_RESPONSE')) AS sent,
			       bool_or(e.stage_category IN ('INTERVIEWING', 'OFFER')
			               OR (e.stage_category = 'REJECTED' AND coalesce(e.rejection_reason, '') <> 'NO_RESPONSE')) AS replied,
			       bool_or(e.stage_category IN ('INTERVIEWING', 'OFFER')) AS interviewed,
			       bool_or(e.stage_category = 'OFFER') AS offered,
			       min(e.event_date) FILTER (WHERE e.stage_category = 'APPLIED') AS applied_on,
			       min(e.event_date) FILTER (WHERE e.stage_category IN ('INTERVIEWING', 'OFFER')
			               OR (e.stage_category = 'REJECTED' AND coalesce(e.rejection_reason, '') <> 'NO_RESPONSE')) AS replied_on,
			       (array_agg(e.rejection_reason ORDER BY e.event_date DESC, e.id DESC)
			               FILTER (WHERE e.rejection_reason IS NOT NULL))[1] AS rejection_reason
			FROM applications a
			JOIN application_events e ON e.application_id = a.id
			LEFT JOIN job_fits f ON f.application_id = a.id AND f.user_id = a.user_id
			WHERE a.user_id = :userId
			GROUP BY a.id, f.score
			""";

	private static final String GAPS = """
			SELECT r.text, r.importance, r.meets
			FROM job_fit_requirements r
			JOIN job_fits f ON f.id = r.job_fit_id
			WHERE f.user_id = :userId AND r.meets <> 'MET'
			""";

	private final JdbcClient jdbc;

	@Override
	@Transactional(readOnly = true)
	public JobStatistics find(long userId) {
		List<Job> jobs = this.jdbc.sql(JOBS).param("userId", userId).query((row, number) -> new Job(row.getString("source"),
				Objects.requireNonNullElse(row.getString("applied_by"), AppliedBy.YOU.name()), row.getBigDecimal("score"),
				row.getBoolean("sent"), row.getBoolean("replied"), row.getBoolean("interviewed"), row.getBoolean("offered"),
				row.getObject("applied_on", LocalDate.class), row.getObject("replied_on", LocalDate.class),
				row.getString("rejection_reason"))).list();
		List<Job> sent = jobs.stream().filter(Job::sent).toList();
		return new JobStatistics(jobs.size(), sent.size(), count(sent, Job::replied), count(sent, Job::interviewed),
				count(sent, Job::offered), medianDaysToReply(sent),
				rates(sent, Job::source).stream().sorted(Comparator.comparingInt(ReplyRate::sent).reversed()).limit(SITES).toList(),
				rates(sent, Job::appliedBy).stream().sorted(Comparator.comparingInt(ReplyRate::sent).reversed()).toList(),
				byFit(sent), rejectionReasons(jobs), skillGaps(userId));
	}

	private static List<ReplyRate> rates(List<Job> sent, Function<Job, @Nullable String> key) {
		Map<@Nullable String, List<Job>> groups = new LinkedHashMap<>();
		for (Job job : sent) {
			groups.computeIfAbsent(key.apply(job), any -> new ArrayList<>()).add(job);
		}
		return groups.entrySet()
			.stream()
			.map(group -> new ReplyRate(group.getKey(), group.getValue().size(), count(group.getValue(), Job::replied),
					count(group.getValue(), Job::interviewed)))
			.toList();
	}

	/** The strong fits first, then the partial and the weak ones, then those with no score, each when there's one. */
	private static List<ReplyRate> byFit(List<Job> sent) {
		Map<@Nullable String, ReplyRate> rates = new LinkedHashMap<>();
		rates(sent, job -> (job.score() != null) ? FitVerdict.of(job.score()).name() : null)
			.forEach(rate -> rates.put(rate.key(), rate));
		List<ReplyRate> ordered = new ArrayList<>();
		for (FitVerdict verdict : FitVerdict.values()) {
			if (rates.containsKey(verdict.name())) {
				ordered.add(rates.get(verdict.name()));
			}
		}
		if (rates.containsKey(null)) {
			ordered.add(rates.get(null));
		}
		return ordered;
	}

	/** The reasons of the rejections, a job's last one each, the most given first. */
	private static List<ReasonCount> rejectionReasons(List<Job> jobs) {
		return jobs.stream()
			.map(Job::rejectionReason)
			.filter(Objects::nonNull)
			.collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
			.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
			.map(reason -> new ReasonCount(reason.getKey(), reason.getValue().intValue()))
			.toList();
	}

	/**
	 * The requirements met least, the same requirement counted together however its postings wrote it in capitals,
	 * spaces or a full stop: shown as the first posting wrote it, without the full stop.
	 */
	private List<SkillGap> skillGaps(long userId) {
		Map<String, Gap> gaps = new LinkedHashMap<>();
		this.jdbc.sql(GAPS).param("userId", userId).query((row, number) -> {
			String text = withoutTrailingPunctuation(row.getString("text").strip().replaceAll("\\s+", " "));
			Gap gap = gaps.computeIfAbsent(text.toLowerCase(Locale.ROOT), any -> new Gap(text));
			gap.count("MISSING".equals(row.getString("meets")), "MUST".equals(row.getString("importance")));
			return gap;
		}).list();
		return gaps.values()
			.stream()
			.map(gap -> new SkillGap(gap.wording, gap.missing, gap.partly, gap.mustHave))
			.sorted(Comparator.comparingInt(SkillGap::missing)
				.thenComparingInt(SkillGap::partly)
				.thenComparing(SkillGap::mustHave)
				.reversed())
			.limit(SKILL_GAPS)
			.toList();
	}

	/** Without the full stops, commas, colons and semicolons it ends with: counted without a pattern, in one pass. */
	static String withoutTrailingPunctuation(String text) {
		int end = text.length();
		while (end > 0 && ".;:,".indexOf(text.charAt(end - 1)) >= 0) {
			end--;
		}
		return text.substring(0, end);
	}

	private static @Nullable Integer medianDaysToReply(List<Job> sent) {
		List<Long> days = sent.stream()
			.filter(job -> job.appliedOn() != null && job.repliedOn() != null && !job.repliedOn().isBefore(job.appliedOn()))
			.map(job -> ChronoUnit.DAYS.between(job.appliedOn(), job.repliedOn()))
			.sorted()
			.toList();
		if (days.isEmpty()) {
			return null;
		}
		int middle = days.size() / 2;
		long median = (days.size() % 2 == 1) ? days.get(middle) : Math.round((days.get(middle - 1) + days.get(middle)) / 2.0);
		return (int) median;
	}

	private static int count(List<Job> jobs, Predicate<Job> which) {
		return (int) jobs.stream().filter(which).count();
	}

	/** A requirement as the postings wrote it first, and how often it was missing or met in part. */
	private static final class Gap {

		private final String wording;

		private int missing;

		private int partly;

		private boolean mustHave;

		Gap(String wording) {
			this.wording = wording;
		}

		void count(boolean isMissing, boolean isMustHave) {
			if (isMissing) {
				this.missing++;
			} else {
				this.partly++;
			}
			this.mustHave |= isMustHave;
		}

	}

	/** A job's timeline, as the few facts the statistics count. */
	private record Job(@Nullable String source, String appliedBy, @Nullable BigDecimal score, boolean sent,
			boolean replied, boolean interviewed, boolean offered, @Nullable LocalDate appliedOn,
			@Nullable LocalDate repliedOn, @Nullable String rejectionReason) {
	}

}
