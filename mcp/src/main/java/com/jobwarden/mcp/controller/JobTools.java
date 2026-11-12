package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.jobwarden.jobs.dto.FollowUp;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.dto.TidyResult;
import com.jobwarden.jobs.dto.TimelineEvent;
import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.enums.AppliedBy;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.InterviewFormat;
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
import static com.jobwarden.mcp.controller.Schemas.strings;

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

	/** Who sent an application, as update_job_status names them: the person, or the assistant. */
	private static final String APPLIED_BY_PERSON = "YOU";

	private static final String APPLIED_BY_ASSISTANT = "ASSISTANT";

	/** The most of a posting's description get_job returns: the rest is boilerplate, mostly, and costs tokens. */
	static final int DESCRIPTION_MAX_LENGTH = 12_000;

	private final Profiles profiles;

	private final ApplicationService applications;

	private final PostingChecker checker;

	@Override
	public List<McpTool> tools() {
		return List.of(listProfiles(), listJobs(), getJob(), checkPostings(), updateJobStatus(), updateFollowUp(),
				tidyApplications());
	}

	private McpTool listProfiles() {
		return new McpTool("list_profiles", "List profiles",
				"The profiles, each with its own jobs: tools take one's id as profile_id when there are several.",
				object(Map.of()), true, false,
				call -> Map.of("profiles", this.profiles.all().stream().map(user -> new Profile(user.id(), user.name())).toList()));
	}

	private McpTool listJobs() {
		return new McpTool("list_jobs", "List jobs",
				"The profile's jobs, latest changed first, with status and posting link; archived ones left out.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"status", oneOf(ApplicationStatus.values()),
						"search", string("Words in the title or company"),
						"limit", integer("Default 50, max 200"))),
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
				"One job: details, the saved posting, notes, contact and follow-up, and its timeline with interviews.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(), "job_id", integer()), "job_id"),
				true, false, call -> {
					long userId = this.profiles.of(call);
					long jobId = call.requiredNumber("job_id");
					return details(this.applications.findById(userId, jobId)
						.orElseThrow(() -> new ApplicationNotFoundException(jobId)));
				});
	}

	/**
	 * A job as get_job gives it, which get_request also hands out with a request about the job.
	 */
	static JobDetails details(ApplicationDetails job) {
		return new JobDetails(job.id(), job.title(), job.company(), job.location(), job.salary(), job.employmentType(),
				job.status(), job.link(), job.notes(), followUp(job.followUp()),
				shortened(job.description(), DESCRIPTION_MAX_LENGTH), job.timeline().stream().map(JobTools::step).toList());
	}

	private McpTool checkPostings() {
		return new McpTool("check_postings", "Check postings",
				"Whether postings are still up, cheaper than opening them: OPEN, CLOSED (gone, redirected away, expired, or "
						+ "says so) or UNKNOWN (the site blocks bots: open it yourself). Checks the urls, else job_ids, else "
						+ "up to " + MAX_CHECKS + " jobs still Saved, Applied or Interviewing. close_when_gone moves the Saved "
						+ "and Applied ones certainly CLOSED to POSITION_CLOSED, with a note.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_ids", list(integer()),
						"statuses", list(oneOf(ApplicationStatus.values()), "Without job_ids; default SAVED, APPLIED, INTERVIEWING"),
						"urls", strings("Posting URLs, max " + MAX_CHECKS),
						"url", string("One posting URL"),
						"close_when_gone", bool())),
				false, true, this::check);
	}

	private Object check(ToolCall call) {
		String url = call.string("url");
		if (url != null) {
			return this.checker.check(url);
		}
		List<String> urls = call.strings("urls");
		if (!urls.isEmpty()) {
			return checkUrls(urls);
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

	/**
	 * Each address's verdict, in a line: what the assistant needs to keep or leave a search result.
	 */
	private Map<String, Object> checkUrls(List<String> urls) {
		List<String> asked = urls.stream().distinct().toList();
		if (asked.size() > MAX_CHECKS) {
			throw new ToolException("At most " + MAX_CHECKS + " urls at once: check the rest in another call");
		}
		List<UrlCheck> checked = new ArrayList<>();
		Map<String, Future<PostingCheck>> running = new LinkedHashMap<>();
		Semaphore slots = new Semaphore(PARALLEL_CHECKS);
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (String address : asked) {
				running.put(address, executor.submit(() -> {
					slots.acquire();
					try {
						return this.checker.check(address);
					} finally {
						slots.release();
					}
				}));
			}
			for (Map.Entry<String, Future<PostingCheck>> entry : running.entrySet()) {
				PostingCheck result = entry.getValue().get();
				checked.add(new UrlCheck(entry.getKey(), result.verdict(), result.certain(), result.reason()));
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new ToolException("The checks were interrupted");
		} catch (ExecutionException failed) {
			throw new IllegalStateException("A check failed", failed.getCause());
		}
		return Map.of("checked", checked);
	}

	private McpTool tidyApplications() {
		return new McpTool("tidy_applications", "Tidy up the applications with no answer",
				"In one call, without reading the jobs: moves those applied to " + RequestTools.NO_RESPONSE_AFTER_DAYS
						+ "+ days ago with nothing since to NO_RESPONSE, and gives newer ones without a follow-up day one, "
						+ RequestTools.FOLLOW_UP_AFTER_DAYS + " days after applying, or tomorrow.",
				object(properties(Profiles.ARGUMENT, Profiles.schema())), false, false, call -> {
					long userId = this.profiles.of(call);
					TidyResult result = this.applications.tidyUnanswered(userId, RequestTools.NO_RESPONSE_AFTER_DAYS,
							RequestTools.FOLLOW_UP_AFTER_DAYS, "The company never answered. Moved by the AI Assistant.");
					return Map.of("waiting_at_applied", result.waiting(), "moved_to_no_response",
							result.movedToNoResponse().size(), "follow_ups_set", result.followUpsSet().size());
				});
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
		// Each status apart, as those lists leave the archived jobs out; then the newest first, whatever their status
		return statuses.stream()
			.flatMap(status -> this.applications.findAll(userId, status).stream())
			.sorted(Comparator.comparing(ApplicationSummary::capturedAt).reversed())
			.toList();
	}

	private void close(long userId, ApplicationSummary job, PostingCheck result) {
		String note = "The posting is no longer up: " + result.reason()
				+ (result.evidence() != null ? " (\"" + result.evidence() + "\")" : "") + ". Checked by the AI Assistant.";
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
				"Adds a timeline step, which moves the job to that status: e.g. POSITION_CLOSED (posting gone), "
						+ "POSITION_REOPENED, NO_RESPONSE, INTERVIEWING with its date, time, format and place. No date: now. "
						+ "A status can't follow itself, but INTERVIEWING and OFFER.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer(),
						"status", oneOf(ApplicationStatus.values()),
						"note", string("Why, for the timeline"),
						"date", date("Default today"),
						"time", string("HH:MM, with date"),
						"stage", string("INTERVIEWING, OFFER: the company's step, e.g. Technical interview"),
						"rejection_reason", oneOf(RejectionReason.values(), "REJECTED: why, if known"),
						"interview_format", oneOf(InterviewFormat.values(), "INTERVIEWING"),
						"interview_location", string("INTERVIEWING: address or meeting link"),
						"duration_minutes", integer("INTERVIEWING"),
						"applied_by", oneOf(APPLIED_BY_PERSON, APPLIED_BY_ASSISTANT)
							.put("description", "APPLIED: who sent it; default YOU (the person), ASSISTANT only if you did")),
						"job_id", "status"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					long jobId = call.requiredNumber("job_id");
					ApplicationStatus status = call.constant("status", ApplicationStatus.class);
					LocalDate date = call.date("date");
					LocalTime time = call.time("time");
					Long duration = call.number("duration_minutes");
					if (duration != null && (duration < 1 || duration > 1440)) {
						throw new ToolException("duration_minutes must be between 1 and 1440");
					}
					boolean interview = status.hasInterviewDetails();
					ApplicationStatus before = this.applications.findById(userId, jobId)
						.orElseThrow(() -> new ApplicationNotFoundException(jobId))
						.status();
					this.applications.addEvent(userId, jobId, NewTimelineEvent.builder()
						.eventDate((date != null) ? date : LocalDate.now())
						.eventTime((date != null) ? time : LocalTime.now().truncatedTo(ChronoUnit.MINUTES))
						.stageCategory(status)
						.stageLabel(status.hasStages() ? call.string("stage") : null)
						.note(shortened(call.string("note")))
						.rejectionReason(call.constant("rejection_reason", RejectionReason.class))
						.interviewFormat(interview ? call.constant("interview_format", InterviewFormat.class) : null)
						.interviewLocation(interview ? call.string("interview_location") : null)
						.durationMinutes((interview && duration != null) ? duration.intValue() : null)
						.appliedBy((status == ApplicationStatus.APPLIED) ? appliedBy(call) : null)
						.build());
					ApplicationStatus after = this.applications.findById(userId, jobId).orElseThrow().status();
					log.info("{} moved application {} of profile {} from {} to {}", call.client(), jobId, userId, before, after);
					return Map.of("job_id", jobId, "previous_status", before, "status", after);
				});
	}

	private McpTool updateFollowUp() {
		return new McpTool("update_follow_up", "Update a job's contact and follow-up",
				"Sets the job's contact (recruiter or hiring manager), referrer, application deadline and next follow-up "
						+ "day. Only the fields given change; an empty string clears one.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer(),
						"contact_name", string(),
						"contact_role", string("e.g. Recruiter"),
						"contact_email", string(),
						"contact_phone", string(),
						"contact_link", string("Profile URL, e.g. LinkedIn"),
						"referred_by", string(),
						"deadline", date("When applications close"),
						"follow_up_on", date()),
						"job_id"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					long jobId = call.requiredNumber("job_id");
					FollowUp before = this.applications.findById(userId, jobId)
						.orElseThrow(() -> new ApplicationNotFoundException(jobId))
						.followUp();
					FollowUp after = FollowUp.builder()
						.contactName(changed(call, "contact_name", before.contactName()))
						.contactRole(changed(call, "contact_role", before.contactRole()))
						.contactEmail(changed(call, "contact_email", before.contactEmail()))
						.contactPhone(changed(call, "contact_phone", before.contactPhone()))
						.contactLink(changed(call, "contact_link", before.contactLink()))
						.referredBy(changed(call, "referred_by", before.referredBy()))
						.deadline(call.arguments().has("deadline") ? call.date("deadline") : before.deadline())
						.followUpOn(call.arguments().has("follow_up_on") ? call.date("follow_up_on") : before.followUpOn())
						.build();
					this.applications.updateFollowUp(userId, jobId, after);
					log.info("{} updated the follow-up of application {} of profile {}", call.client(), jobId, userId);
					return Map.of("job_id", jobId, "follow_up", followUp(after));
				});
	}

	/**
	 * Who sent an application: the person unless the assistant says it did. ASSISTANT is stored as CLAUDE, the name it
	 * had when Claude Code was the only assistant; CLAUDE is still understood.
	 */
	private static AppliedBy appliedBy(ToolCall call) {
		String by = call.string("applied_by");
		if (by == null || by.equalsIgnoreCase(APPLIED_BY_PERSON)) {
			return AppliedBy.YOU;
		}
		if (by.equalsIgnoreCase(APPLIED_BY_ASSISTANT) || by.equalsIgnoreCase(AppliedBy.CLAUDE.name())) {
			return AppliedBy.CLAUDE;
		}
		throw new ToolException("applied_by must be " + APPLIED_BY_PERSON + " or " + APPLIED_BY_ASSISTANT);
	}

	/** The argument when it's given, an empty one clearing the value; else the value as it was. */
	private static @Nullable String changed(ToolCall call, String name, @Nullable String before) {
		return call.arguments().has(name) ? call.string(name) : before;
	}

	private static @Nullable String shortened(@Nullable String note) {
		return shortened(note, NOTE_MAX_LENGTH);
	}

	private static @Nullable String shortened(@Nullable String text, int maxLength) {
		return (text == null || text.length() <= maxLength) ? text : text.substring(0, maxLength - 1) + "…";
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
				job.lastModified());
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
				event.rejectionReason(), event.interviewFormat(), event.interviewLocation(), event.durationMinutes());
	}

	private static @Nullable FollowUpView followUp(FollowUp followUp) {
		return followUp.equals(FollowUp.NONE) ? null : new FollowUpView(followUp.contactName(), followUp.contactRole(),
				followUp.contactEmail(), followUp.contactPhone(), followUp.contactLink(), followUp.referredBy(),
				followUp.deadline(), followUp.followUpOn());
	}

	record Profile(long id, String name) {
	}

	/** A job in a list: what tells it apart, without the site (its link says it) or when it was added. */
	record JobRow(long id, String title, @Nullable String company, @Nullable String location, @Nullable String salary,
			ApplicationStatus status, @Nullable String link, Instant lastModified) {
	}

	/** A job: its timeline says when it was saved, and its link the site. */
	record JobDetails(long id, String title, @Nullable String company, @Nullable String location,
			@Nullable String salary, @Nullable String employmentType, ApplicationStatus status, @Nullable String link,
			@Nullable String notes, @Nullable FollowUpView followUp, @Nullable String description, List<Step> timeline) {
	}

	record Step(LocalDate date, @Nullable LocalTime time, ApplicationStatus status, @Nullable String stage,
			@Nullable String note, @Nullable RejectionReason rejectionReason, @Nullable InterviewFormat interviewFormat,
			@Nullable String interviewLocation, @Nullable Integer durationMinutes) {
	}

	record FollowUpView(@Nullable String contactName, @Nullable String contactRole, @Nullable String contactEmail,
			@Nullable String contactPhone, @Nullable String contactLink, @Nullable String referredBy,
			@Nullable LocalDate deadline, @Nullable LocalDate followUpOn) {
	}

	record UrlCheck(String url, Verdict verdict, boolean certain, String reason) {
	}

	/**
	 * @param closed whether close_when_gone moved it to Position closed
	 */
	record CheckedJob(long jobId, String title, @Nullable String company, ApplicationStatus status, boolean closed,
			PostingCheck check) {
	}

}
