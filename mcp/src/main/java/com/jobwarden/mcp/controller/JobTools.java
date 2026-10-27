package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.dto.TimelineEvent;
import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.RejectionReason;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.mcp.dto.PostingCheck;
import com.jobwarden.mcp.enums.Verdict;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.Schemas.bool;
import static com.jobwarden.mcp.controller.Schemas.date;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.list;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.oneOf;
import static com.jobwarden.mcp.controller.Schemas.string;

/**
 * The jobs a profile tracks: listing and reading them, checking whether their postings are still up, and moving them
 * to another status.
 */
@Component
@CustomLog
@RequiredArgsConstructor
class JobTools implements McpToolSet {

	/** What check_postings checks unless told otherwise: the jobs still waiting on the posting. */
	private static final Set<ApplicationStatus> WAITING = EnumSet.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED,
			ApplicationStatus.INTERVIEWING);

	/** Which statuses close_when_gone closes: one further along may have been filled by this very application. */
	private static final Set<ApplicationStatus> CLOSABLE = EnumSet.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED);

	private static final int MAX_CHECKS = 30;

	/** How many pages to fetch at once: quick enough, and gentle with the sites. */
	private static final int PARALLEL_CHECKS = 6;

	private static final int NOTE_MAX_LENGTH = 2000;

	private final Profiles profiles;

	private final ApplicationService applications;

	private final PostingChecker checker;

	@Override
	public List<McpTool> tools() {
		return List.of(listProfiles(), listJobs(), getJob(), checkPostings(), updateJobStatus());
	}

	private McpTool listProfiles() {
		return new McpTool("list_profiles", "List profiles",
				"The profiles in JobWarden, each with its own jobs. Tools take a profile_id when there's more than one.",
				object(Map.of()), true, false,
				call -> Map.of("profiles", this.profiles.all().stream().map(user -> new Profile(user.id(), user.name())).toList()));
	}

	private McpTool listJobs() {
		return new McpTool("list_jobs", "List jobs",
				"The jobs the profile tracks, the latest changed first, with each one's status and the posting's link.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"status", oneOf(ApplicationStatus.values(), "Only the jobs with this status"),
						"search", string("Only the jobs whose title or company has these words"),
						"limit", integer("How many jobs at most, up to 200; 50 if left out"))),
				true, false, call -> {
					long userId = this.profiles.of(call);
					Page<ApplicationSummary> page = this.applications.find(userId, ApplicationQuery.builder()
						.search(call.string("search"))
						.status(call.constant("status", ApplicationStatus.class))
						.sort(ApplicationSort.MODIFIED)
						.direction(Sort.Direction.DESC)
						.page(0)
						.size(call.limit("limit", 50, 200))
						.build());
					return Map.of("total", page.getTotalElements(), "jobs", page.getContent().stream().map(JobTools::row).toList());
				});
	}

	private McpTool getJob() {
		return new McpTool("get_job", "Get a job",
				"One job: its details, the posting's description as it was saved, the notes, and its timeline.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(), "job_id", integer("The job, from list_jobs")),
						"job_id"),
				true, false, call -> {
					long userId = this.profiles.of(call);
					long jobId = call.requiredNumber("job_id");
					ApplicationDetails job = this.applications.findById(userId, jobId)
						.orElseThrow(() -> new ApplicationNotFoundException(jobId));
					return new JobDetails(job.id(), job.title(), job.company(), job.location(), job.salary(),
							job.employmentType(), job.status(), job.link(), job.source(), job.notes(), job.description(),
							job.capturedAt(), job.timeline().stream().map(JobTools::step).toList());
				});
	}

	private McpTool checkPostings() {
		return new McpTool("check_postings", "Check postings",
				"Fetches the postings' pages to tell whether they're still up: OPEN, CLOSED (the page is gone, it redirects "
						+ "away, its job data has expired, or it says so) or UNKNOWN (the site blocks automated visits: open "
						+ "it yourself if you can). Checks the given jobs, or else up to " + MAX_CHECKS + " of the jobs still "
						+ "Saved, Applied or Interviewing, or one url. With close_when_gone, jobs that are Saved or Applied and "
						+ "whose posting is certainly CLOSED move to Position closed, with a note saying why.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_ids", list(integer("A job"), "The jobs to check, from list_jobs"),
						"statuses", list(oneOf(ApplicationStatus.values(), "A status"),
								"Without job_ids, check the jobs with these statuses; Saved, Applied and Interviewing if left out"),
						"url", string("A posting's address to check instead, e.g. before adding it as a found job"),
						"close_when_gone", bool("Move the Saved and Applied jobs whose posting is certainly closed to Position closed"))),
				false, true, this::check);
	}

	private Object check(ToolCall call) {
		String url = call.string("url");
		if (url != null) {
			return this.checker.check(url);
		}
		long userId = this.profiles.of(call);
		List<Long> ids = numbers(call);
		List<ApplicationSummary> jobs = ids.isEmpty() ? waiting(userId, call) : ids.stream()
			.map(id -> this.applications.findById(userId, id).orElseThrow(() -> new ApplicationNotFoundException(id)))
			.map(JobTools::summary)
			.toList();
		boolean closeWhenGone = call.arguments().path("close_when_gone").asBoolean(false);

		List<CheckedJob> checked = new ArrayList<>();
		List<Map<String, Object>> skipped = new ArrayList<>();
		Map<ApplicationSummary, Future<PostingCheck>> running = new LinkedHashMap<>();
		Semaphore slots = new Semaphore(PARALLEL_CHECKS);
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (ApplicationSummary job : jobs.stream().limit(MAX_CHECKS).toList()) {
				if (job.link() == null || !job.link().startsWith("http")) {
					skipped.add(Map.of("job_id", job.id(), "reason", "no link to check"));
					continue;
				}
				running.put(job, executor.submit(() -> {
					slots.acquire();
					try {
						return this.checker.check(job.link());
					} finally {
						slots.release();
					}
				}));
			}
			for (Map.Entry<ApplicationSummary, Future<PostingCheck>> entry : running.entrySet()) {
				ApplicationSummary job = entry.getKey();
				PostingCheck result = entry.getValue().get();
				boolean closed = closeWhenGone && result.verdict() == Verdict.CLOSED && result.certain()
						&& CLOSABLE.contains(job.status());
				if (closed) {
					close(userId, job, result);
				}
				checked.add(new CheckedJob(job.id(), job.title(), job.company(),
						closed ? ApplicationStatus.POSITION_CLOSED : job.status(), closed, result));
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new ToolException("The checks were interrupted");
		} catch (ExecutionException failed) {
			throw new IllegalStateException("A check failed", failed.getCause());
		}
		Map<Verdict, Long> counts = new LinkedHashMap<>();
		for (Verdict verdict : Verdict.values()) {
			counts.put(verdict, checked.stream().filter(job -> job.check().verdict() == verdict).count());
		}
		if (jobs.size() > MAX_CHECKS) {
			skipped.add(Map.of("reason", (jobs.size() - MAX_CHECKS) + " more jobs: check them in another call, by job_ids"));
		}
		return Map.of("summary", counts, "checked", checked, "skipped", skipped);
	}

	private List<Long> numbers(ToolCall call) {
		List<Long> ids = new ArrayList<>();
		call.arguments().path("job_ids").forEach(id -> {
			if (!id.isIntegralNumber()) {
				throw new ToolException("job_ids must be whole numbers");
			}
			ids.add(id.asLong());
		});
		return ids;
	}

	private List<ApplicationSummary> waiting(long userId, ToolCall call) {
		List<ApplicationStatus> asked = call.constants("statuses", ApplicationStatus.class);
		Set<ApplicationStatus> statuses = asked.isEmpty() ? WAITING : EnumSet.copyOf(asked);
		return this.applications.findAll(userId, null).stream().filter(job -> statuses.contains(job.status())).toList();
	}

	private void close(long userId, ApplicationSummary job, PostingCheck result) {
		String note = "The posting is no longer up: " + result.reason()
				+ (result.evidence() != null ? " (\"" + result.evidence() + "\")" : "") + ". Checked by Claude.";
		this.applications.addEvent(userId, job.id(), NewTimelineEvent.builder()
			.eventDate(LocalDate.now())
			.eventTime(LocalTime.now().truncatedTo(ChronoUnit.MINUTES))
			.stageCategory(ApplicationStatus.POSITION_CLOSED)
			.note(shortened(note))
			.build());
		log.info("Closed application {} of profile {}: its posting is gone", job.id(), userId);
	}

	private McpTool updateJobStatus() {
		return new McpTool("update_job_status", "Update a job's status",
				"Adds a step to the job's timeline, which moves it to that status: e.g. POSITION_CLOSED when its posting "
						+ "is gone, NO_RESPONSE when the company never answered. Without a date, it's now.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer("The job, from list_jobs"),
						"status", oneOf(ApplicationStatus.values(), "The status to move the job to"),
						"note", string("Why, for the timeline: e.g. what showed the posting is gone"),
						"date", date("When it happened, as YYYY-MM-DD; today if left out"),
						"stage", string("For INTERVIEWING and OFFER: the company's own step, e.g. Technical interview"),
						"rejection_reason", oneOf(RejectionReason.values(), "For REJECTED: why, if known")),
						"job_id", "status"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					long jobId = call.requiredNumber("job_id");
					ApplicationStatus status = call.constant("status", ApplicationStatus.class);
					LocalDate date = call.date("date");
					ApplicationStatus before = this.applications.findById(userId, jobId)
						.orElseThrow(() -> new ApplicationNotFoundException(jobId))
						.status();
					this.applications.addEvent(userId, jobId, NewTimelineEvent.builder()
						.eventDate((date != null) ? date : LocalDate.now())
						.eventTime((date != null) ? null : LocalTime.now().truncatedTo(ChronoUnit.MINUTES))
						.stageCategory(status)
						.stageLabel(status.hasStages() ? call.string("stage") : null)
						.note(shortened(call.string("note")))
						.rejectionReason(call.constant("rejection_reason", RejectionReason.class))
						.build());
					ApplicationStatus after = this.applications.findById(userId, jobId).orElseThrow().status();
					log.info("Claude moved application {} of profile {} from {} to {}", jobId, userId, before, after);
					return Map.of("job_id", jobId, "previous_status", before, "status", after);
				});
	}

	private static @Nullable String shortened(@Nullable String note) {
		return (note == null || note.length() <= NOTE_MAX_LENGTH) ? note : note.substring(0, NOTE_MAX_LENGTH - 1) + "…";
	}

	/** Argument names and their schemas, in this order. */
	static Map<String, ObjectNode> properties(Object... namesAndSchemas) {
		Map<String, ObjectNode> properties = new LinkedHashMap<>();
		for (int i = 0; i < namesAndSchemas.length; i += 2) {
			properties.put((String) namesAndSchemas[i], (ObjectNode) namesAndSchemas[i + 1]);
		}
		return properties;
	}

	private static JobRow row(ApplicationSummary job) {
		return new JobRow(job.id(), job.title(), job.company(), job.location(), job.salary(), job.status(), job.link(),
				job.source(), job.capturedAt(), job.lastModified());
	}

	private static ApplicationSummary summary(ApplicationDetails job) {
		return ApplicationSummary.builder()
			.id(job.id())
			.title(job.title())
			.company(job.company())
			.link(job.link())
			.status(job.status())
			.capturedAt(job.capturedAt())
			.build();
	}

	private static Step step(TimelineEvent event) {
		return new Step(event.eventDate(), event.eventTime(), event.stageCategory(), event.stageLabel(), event.note(),
				event.rejectionReason());
	}

	record Profile(long id, String name) {
	}

	record JobRow(long id, String title, @Nullable String company, @Nullable String location, @Nullable String salary,
			ApplicationStatus status, @Nullable String link, @Nullable String source, Instant added,
			Instant lastModified) {
	}

	record JobDetails(long id, String title, @Nullable String company, @Nullable String location,
			@Nullable String salary, @Nullable String employmentType, ApplicationStatus status, @Nullable String link,
			@Nullable String source, @Nullable String notes, @Nullable String description, Instant added,
			List<Step> timeline) {
	}

	record Step(LocalDate date, @Nullable LocalTime time, ApplicationStatus status, @Nullable String stage,
			@Nullable String note, @Nullable RejectionReason rejectionReason) {
	}

	/**
	 * @param closed whether close_when_gone moved it to Position closed
	 */
	record CheckedJob(long jobId, String title, @Nullable String company, ApplicationStatus status, boolean closed,
			PostingCheck check) {
	}

}
