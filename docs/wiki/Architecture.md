# What's what

How JobWarden is built: one Spring Boot application split into modules with checked boundaries, the packages every
module has, and the conventions the build holds the code to. For using JobWarden, start at [Home](Home).

Why it's built this way: the [architecture decision records](https://github.com/CRV96/jobwarden/blob/main/docs/adr/README.md). What's being improved next: the [code quality roadmap](https://github.com/CRV96/jobwarden/blob/main/docs/quality-roadmap.md).

A modular monolith: one Spring Boot application split into Maven modules. The domain modules hold the business logic and know nothing about the web. Two adapter modules expose them: `web` to the browser, `capture` to the extension. `common` holds the little code they all share.

| Module | Responsibility | Depends on |
|---|---|---|
| `common` | Shared: `ErrorCode`, the base exceptions, the application logger (`JobWardenLogger`) and `Secrets`, which encrypts what the server keeps. No beans, no tables, no business logic | none |
| `identity` | Domain: local profiles (`users` table) | `common` |
| `jobs` | Domain: jobs bookmarked or applied to, with each application's status and event timeline, the reminders set on their calendar and its feed's secret, their fit scores and interview briefs, their salaries compared, their offers' terms and negotiation briefs, and the statistics of how they went | `common` |
| `search` | Domain: the Warden Queries (Boolean job-search queries) for Google, LinkedIn, Reddit and X, the description parser that fills in their filters, and the countries of the regions and places postings name; stateless | none |
| `discover` | Domain: saved searches, the jobs found for them, and the requests the AI Assistant page makes of the assistant | `common`, `jobs`, `search` |
| `market` | Domain: market salaries across countries, from the Stack Overflow survey, Adzuna and Eurostat | `common`, `jobs` |
| `cv` | Domain: the profile's CV, as an uploaded file with its text and its preview, as fields to build one from, and rewritten by the assistant for each job; the GitHub repositories its projects come from; each job's cover letter and messages to the people hiring; and the story bank | `common` |
| `hiring` | Domain: who's hiring: recruiters, found by the assistant or saved as jobs' contacts, and their posts saying they're hiring | `common`, `jobs` |
| `updates` | Domain: the version running, and the latest release on GitHub | `common` |
| `database` | Domain: the PostgreSQL login the server connects with: its password, changed in Settings, given to the connection pool at once and, kept encrypted, at the next start; no table | `common` |
| `apply` | Domain: auto-apply: the answers application forms ask for, and the jobs queued for the assistant to apply to | `common`, `jobs` |
| `inbox` | Domain: recruiters' emails, read from the profile's mailbox over IMAP as often as it says, and offered as updates of its jobs, applied on their own or sorted by the assistant when it says so | `common`, `jobs`, `discover` |
| `watchlist` | Domain: the companies a profile watches, by their job boards on Greenhouse, Lever, Ashby, SmartRecruiters, Workable, Recruitee and Personio, read every few hours with no AI; the postings that match its saved searches become found jobs | `common`, `jobs`, `discover`, `search` |
| `web` | Adapter: browser UI. Page controllers, templates, shared layout, Tailwind and HTMX; the CV's and the cover letters' PDFs and the calendar feed | `common`, `identity`, `jobs`, `search`, `discover`, `market`, `cv`, `hiring`, `apply`, `inbox`, `watchlist`, `updates`, `database` |
| `capture` | Adapter: REST endpoint for the browser extension, and the versions it compares with its own | `common`, `identity`, `jobs`, `updates`, `apply`, `cv` |
| `mcp` | Adapter: the MCP server the AI assistant (Claude Code or Codex) works through, at `/mcp` | `common`, `identity`, `jobs`, `search`, `discover`, `market`, `cv`, `hiring`, `apply`, `inbox`, `watchlist` |
| `app` | Runnable application and config; wires the modules together | all |
| `extension/` | Chrome and Firefox extension (Manifest V3, plain JavaScript); not a Maven module | the `capture` API |

Every module groups its classes by type, with the same package names everywhere:

| Package | Holds | Where |
|---|---|---|
| `dto` | Records that carry data: service inputs and outputs, form objects, REST request and response bodies | all modules |
| `enums` | Enums, e.g. `ApplicationStatus`, `IdentityErrorCode`, `Language` | `jobs`, `identity`, `capture`, `web` |
| `service` | Service interfaces | domain modules |
| `service.impl` | Service implementations (`Default*Service`) | domain modules |
| `entity` | JPA entities | domain modules |
| `repository` | Spring Data repositories | domain modules |
| `mapper` | Entity → DTO conversion (`*Mapper`, static methods) | domain modules |
| `exception` | Exceptions the service throws, e.g. `ApplicationNotFoundException`, and the module's error codes, e.g. `JobsErrorCode`; in `common`, `ErrorCode`, `CommonErrorCode` and the base exceptions | domain modules, `common` |
| `logging` | `JobWardenLogger` | `common` |
| `controller` | Controllers and their exception handlers | `web`, `capture` |
| `constants`, `user` | Route paths; `CurrentUser` | `web` |
| `config` | Spring configuration, e.g. `LocaleConfiguration`, and the filters every request goes through: `RequestIdFilter`, `SecurityHeadersFilter` | `web` |

In the domain modules, `dto`, `enums`, `service` and `exception` are the public API. Each is marked `@NamedInterface` in its `package-info.java`, which tells Spring Modulith that other modules may use it. `service` uses `propagate = false`, so `service.impl` stays internal, as do `entity` and `repository`. In `common`, `exception` and `logging` are marked the same way.

Conventions:

- **Null-safety:** every package is `@NullMarked` (JSpecify) in its `package-info.java`: nothing is `null` unless its type says `@Nullable`. IntelliJ checks it as you type; NullAway checks it as the code compiles, with `./mvnw verify -Pnullaway`, which CI always runs (it's a profile because Error Prone needs the JVM flags in `.mvn/jvm.config`, which IntelliJ's own compiler doesn't get). A form field that validation requires is still `@Nullable` (binding leaves out what a request didn't send): read it with the form's `required…()` method, after validation. A helper that keeps a non-null argument non-null says so with Spring's `@Contract("!null -> !null")`. A new package needs its `package-info.java`, with `@NullMarked`.
- **Time:** "now" comes from the `Clock` bean (`JobWardenApplication`): a class that needs it takes a `Clock` in its constructor and calls `Instant.now(this.clock)`, `LocalDate.now(this.clock)`, `this.clock.getZone()`, so a test can fix the moment. Entities stamp their own audit times (`updatedAt`...) with the system's clock.
- **Settings:** a domain module's settings are one `@ConfigurationProperties` record in its `service.impl` (e.g. `WatchlistProperties`, `jobwarden.watchlist.*`), with its defaults (`@DefaultValue`) and constraints, registered by the module's `*Configuration` and checked as the app starts. Elsewhere, `@Value` goes on a `private final` field, which Lombok copies onto the constructor (`lombok.config`); never on a setter.
- **Records for DTOs:** they need no constructors, getters or `equals`. Records with more than three fields also get Lombok's `@Builder`: build them with `NewApplication.builder().title(...).build()` rather than a constructor call full of `null`s, and leave out the fields that have no value. The records the modules take as input (`NewApplication`, `NewTimelineEvent`) check their required fields in their constructor, since a builder can't make sure every required field is set.
- **Lombok for entities:** only `@Getter` and `@NoArgsConstructor(access = PROTECTED)`. Never use `@Data`, `@EqualsAndHashCode` or `@ToString` on entities. They trigger lazy loading, and on the two-way `ApplicationEntity` ↔ `TimelineEventEntity` link `toString` would recurse forever.
- **Package-private controllers and service implementations:** only the service interfaces can be called from Java code.
- **Entities never leave their module:** services return records, converted with the module's mapper.
- **Errors:**
  - Services throw the module's own exceptions. Each extends a base class from `common`, which decides the HTTP status: `NotFoundException` (404, one subclass per resource, such as `ApplicationNotFoundException`) or `BusinessRuleException` (400, thrown as is with the rule's code).
  - Every exception carries an error code from its module's enum (`IdentityErrorCode`, `JobsErrorCode`), such as `JOBS-001`.
  - The exception handlers log each one once, as a warning. Codes only go into ERROR logs, so these lines have none; `capture` still returns the code to the extension. Services don't log the exceptions they throw.
  - HTMX ignores error responses, so `layout.html` shows a "That didn't work" banner when an HTMX request fails.
  - Any other exception is unexpected: a bug or an outage. The handlers log it at ERROR with `COMMON-001` and its stack trace, and answer 500 without internal details. Spring MVC's own errors, such as text where a number was expected, keep their usual 4xx status.
  - In `web`, `WebExceptionHandler` turns them into an HTTP status, and every error page renders with [error.html](https://github.com/CRV96/jobwarden/blob/main/web/src/main/resources/templates/error.html) inside the layout.
  - In `capture`, `CaptureExceptionHandler` returns [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details (JSON) for the extension, with the code in an `errorCode` field. Every error carries a code, Spring MVC's own ones too: `COMMON-002` for a malformed request, and validation errors add an `errors` object (field → message).
- **Logging:** put Lombok's `@CustomLog` on a class (not `@Slf4j`) to get a `log` field of type `JobWardenLogger`, set up in [lombok.config](https://github.com/CRV96/jobwarden/blob/main/lombok.config). `debug`, `info` and `warn` are plain SLF4J. `error` also takes an error code as its first argument, and needs one: `log.error(JobsErrorCode.SAVE_FAILED, "Could not save application {}", id, exception)`. The code is added to the message and as a separate `errorCode` field. Each module defines its codes as an enum implementing `ErrorCode` in its `exception` package, prefixed with the module's name (`JOBS-001`). Levels:
  - **INFO:** what changes (created, moved to another status, deleted).
  - **DEBUG:** reads, rejected forms and captures. Turn it on with `logging.level.com.jobwarden=DEBUG`; the line is ready in `application.properties`.
  - **WARN:** the modules' own exceptions, logged once by the exception handlers.
  - **ERROR:** only unexpected failures, always with a code.

  Log ids, never names or job details.
- **Page controllers are made by composition, not inheritance:**
  - A controller whose pages render in the layout is a `@PageController`. Before each of its handlers, `LayoutAdvice` (a `@ControllerAdvice`) puts the selected profile in the model as `currentProfile`, which the header shows, and what the sidebar counts. That's the only time a request looks the profile up. The other controllers (images, downloads, the calendar feed) are plain `@Controller`s and skip it.
  - Handlers that need a profile call `requireProfile(model)`. With no profile selected it throws `ProfileNotSelectedException`, and `WebExceptionHandler` sends the browser to `/profiles` (HTMX requests get an `HX-Redirect` header instead of a redirect).
  - `Pages` has that and the other static helpers, `redirectTo(path)` and `attachment(...)`, imported statically. What needs a bean is a component the controller takes in its constructor like anything else: `FormTexts` (an exception's or a form's messages, in the user's language: `fieldErrors(result)` maps each field to its first validation message), `PromptCards` (the cards that ask the AI assistant), `JobImages` (the sites' icons and companies' logos). Optional beans (the runner, the update check) come through `ObjectProvider`.
  - Their `@WebMvcTest`s need `@MockitoBean`s of what `LayoutAdvice` reads: `CurrentUser`, `UserService`, `ApplicationService`, `DiscoveryService`. `WebTestApplication` imports the components.
- **Constants instead of repeated strings:**
  - `web`'s `constants` package holds the routes (`ControllerConstants`), template names (`Views`), model attribute names (`ModelAttributes`) and HTMX's headers and events (`HtmxHeaders`, `HtmxEvents`).
  - A form's length limits are constants on the form record (e.g. `NewEventForm.STAGE_LABEL_MAX_LENGTH`), and its validation messages use `{max}`.
  - `ErrorCode.PROPERTY_NAME` is the one name for the error code, in logs and in error responses.
- **Dates and times:** a timeline event has a date, and a time of day only when it's known (`LocalDate` + `LocalTime`; `DATE` + `TIME` columns). Within a day, events without a time come first. They're wall-clock values, as entered; "now" is taken in the server's time zone, so set `TZ` when running in Docker (which defaults to UTC). Moments that are only ever recorded, like `capturedAt`, are instants (`Instant`, `TIMESTAMPTZ`). The forms have a date field and an optional time field with a Clear time button (a time emptied only in part is invalid, and the browser won't send the form); a form that sends no date, like the status buttons, means now, with the time. The capture API adds the time too, unless the request says `"includeTime": false`. Display patterns are the `format.date` and `format.dateTime` messages, so each language has its own.
- **Entities guard their own rules:** `ApplicationEntity.getEvents()` is read-only and `TimelineEventEntity`'s constructor is package-private, so events can only be added through `addEvent`, which keeps the status in step with the timeline.
- **Formatting:** [.editorconfig](https://github.com/CRV96/jobwarden/blob/main/.editorconfig) sets tabs for Java and XML plus the import order. In IntelliJ, *Code → Reformat Code* with *Optimize imports* applies it. The build checks the imports (Spotless: `java`, `javax`, the rest, `org.springframework`, then static, none unused) and `./mvnw spotless:apply` puts them in order.
- **The build** also stops on an older Java or Maven than the project needs, a dependency declared twice, or a version range (Maven Enforcer), and shows every compiler warning (`-Xlint:all`): don't add new ones.

Templates live in `web/src/main/resources/templates/<feature>/`, and all pages share [layout.html](https://github.com/CRV96/jobwarden/blob/main/web/src/main/resources/templates/layout.html). Never name a model attribute `application`, `session` or `param`: Thymeleaf reserves those names, so the template would read something else. HTMX endpoints return a fragment of the page's template, e.g. `applications/detail :: application`. Pages run under a Content Security Policy (`SecurityHeadersFilter`): no inline `<script>`, no `onclick=` and the like, no `hx-on` or `js:` values. Behaviour goes in `static/js/app.js`, asked for with a `data-` attribute.

Rules:

- A module's public API is its root package plus the packages marked `@NamedInterface`. Everything else is private to that module. Other modules never touch another module's entities or repositories.
- Domain modules never depend on the adapters. Business logic belongs in a domain module, never in a controller.
- [ModularityTests](https://github.com/CRV96/jobwarden/blob/main/app/src/test/java/com/jobwarden/ModularityTests.java) (Spring Modulith) fails the build if a module uses another module's internals or if module dependencies form a cycle.
- [ConventionsTests](https://github.com/CRV96/jobwarden/blob/main/app/src/test/java/com/jobwarden/ConventionsTests.java) (ArchUnit) fails it if the code leaves these conventions: package-private `Default*Service` implementations and controllers, records for public DTOs, constructor injection (no setter injection, `@Value` only on final fields), logging through `JobWardenLogger`, no `System.out`, no generic exceptions, no transactions in controllers, entities without `toString`/`equals`/`hashCode`, exceptions with an error code, `java.time` only, "now" from the `Clock`, and every error code unique and prefixed with its module's name, and every package `@NullMarked`.
- Each module keeps its own Flyway migrations in `src/main/resources/db/migration`, named `V<n>__<module>_<description>.sql`. Version numbers are shared by all modules: a new migration takes the next free number across the whole project, not just its own module. Hibernate only validates the schema; it never changes it.
