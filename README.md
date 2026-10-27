# JobWarden

The keeper of your job hunt: save job postings from the browser in one click, and keep every application's story, from the first save to the offer.

[![Build](https://github.com/CRV96/job_tracker/actions/workflows/build.yml/badge.svg)](https://github.com/CRV96/job_tracker/actions/workflows/build.yml)

## Running locally

Requires JDK 25+ and Docker. The app starts its own Postgres container from [app/compose.yaml](app/compose.yaml).

```bash
./mvnw -pl app -am spring-boot:run
```

Build and run all tests (tests use Testcontainers, so Docker must be running):

```bash
./mvnw verify
```

### Frontend

Pages are server-rendered with Thymeleaf; HTMX handles partial updates without a page reload. Tailwind CSS is compiled during the Maven build, which downloads its own Node into `web/target/`, so nothing extra needs installing. While editing templates, keep the CSS up to date with:

```bash
./mvnw -pl web frontend:npm@tailwind-watch
```

### Browser extension

The same `extension/` folder works in Chrome and Firefox (128 or later):

- **Chrome:** open `chrome://extensions`, turn on Developer mode, click **Load unpacked** and pick the `extension/` folder.
- **Firefox:** open `about:debugging#/runtime/this-firefox`, click **Load Temporary Add-on…** and pick `extension/manifest.json`. Firefox keeps it until it restarts; to keep it for good, sign it as an unlisted add-on on [addons.mozilla.org](https://addons.mozilla.org/developers/), e.g. with `npx web-ext sign --source-dir extension --channel unlisted`.

In the extension's settings (⚙ in the popup, or **Options** in its menu), set the server URL and your profile id; the Profiles page shows your id. The browser asks you to allow any server other than `http://localhost:8080`.

On a job posting, click the extension's icon. It first looks the page up in JobWarden:

- **Not saved yet:** it reads the posting and shows the title, company, location and description, which you can correct or paste in. Then click **I applied** or **Bookmark**.
- **Bookmarked** (★): **I applied**, or **Remove bookmark**.
- **Applied** (✓): **Move back to bookmarks**, for an application you didn't actually send, or **Remove application**. Moving back removes the Applied entries from the timeline; it's only offered while nothing else has happened since.
- **Further along** (interviewing, offer, rejected, no response, position closed, withdrawn): only **Open in JobWarden**. The timeline says more than a click in the extension should undo.

Removing asks for a second click, and can't be undone. **Include the time** adds the time of day to the timeline entry, not only the date, and the extension remembers the choice.

In the settings, **Show on the toolbar icon whether a page is already in JobWarden** puts ★ or ✓ on the icon of every tab showing a saved posting, as you browse. The browser asks you to let the extension see your tabs' addresses for it, and each page you open is then looked up on your server.

- **What it reads:** each field from the first of these that has it:
  - Text you selected on the page before clicking the icon, as the description. Use it where nothing else finds the description.
  - The posting's schema.org `JobPosting`, which most job boards and career sites include for Google's job search, as JSON-LD or microdata. It has every field, and the whole object is kept as the application's raw payload.
  - On a search that shows a job beside the results, the job's own page, fetched in the background, with its `JobPosting`: on Indeed and Glassdoor. On LinkedIn, which doesn't include it for signed-in members: the job's public page (what visitors who aren't signed in see), then what's on screen, then the page title.
  - What the page shows, on sites whose `JobPosting` leaves fields out or that have none: Indeed, Glassdoor, Greenhouse, Workday, pracuj.pl, InfoJobs and BestJobs (`SITES` in [extract.js](extension/extract.js)). Sites like Lever, Ashby, StepStone, XING, Reed, NoFluffJobs, Just Join IT and eJobs need nothing more than their `JobPosting`.
  - The page itself: the Open Graph tags, a block marked as the job description, the `h1` heading, the page title.
- **The company's logo:** from the posting's `JobPosting` (its `hiringOrganization.logo`), which most job boards and career sites have, or on LinkedIn from the job's public page. The extension sends its address and the server downloads it, since most sites don't let the extension read their images, then shrinks it to a 64-pixel PNG shown before the job's title. The server only downloads from the public internet, never from its own machine or the local network: the address comes from the page. Without a logo, the pages show the company's initials.
- **The site's icon:** what the tab shows, drawn into a 32×32 PNG and saved with the posting, so JobWarden shows it next to the site instead of a globe. Chrome gives the extension its own copy of any page's icon. Firefox doesn't, so there the extension uses the tab's icon only when it can read it (when the icon is already part of the page, or its site lets anyone fetch it); otherwise the site keeps the globe.
- **One posting, one application:** the server matches captures on the exact link. So the extension drops tracking parameters (`utm_*`, `trackingId`...), and on LinkedIn, Indeed and Glassdoor it saves the job's own link, even from a search page. Saving a posting you already saved doesn't create a duplicate, and clicking **I applied** on a posting you bookmarked marks it applied.
- **The API it uses:** under `/api/captures`, set by `jobwarden.capture.path` in [application.properties](app/src/main/resources/application.properties); if you change it, change `CAPTURE_PATH` in [settings.js](extension/settings.js) too. `POST` saves a posting, with the site's icon as an optional `data:image/png;base64,...` URL in `icon` and the company logo's address in `logoUrl`; `GET ?userId=&link=` looks a link up (204 if it isn't saved); `POST /{id}/move-back-to-saved` takes back "I applied"; `DELETE /{id}?userId=` removes an application. Each answers with the application's current state: its id, title, company, status, and when it got that status. Firefox, unlike Chrome, applies CORS to an extension's requests even with the host permission, so this API allows extension origins from Firefox (`moz-extension://`); web pages still can't call it.
- **Languages:** the app's, in `extension/_locales/<language>/messages.json`. Chrome uses the browser's language.

## Salaries and the Boolean search

**Salaries** (`/salaries`) compares what the jobs you track pay. Salaries are kept as the postings wrote them, so `SalaryService` in `jobs` reads them first: the first two amounts make the range (80,000, 80.000, 80 000, 80'000, 80k), a code or symbol gives the currency, and words in the extension's languages give the period (per hour, pro Monat, pe lună...). A salary without a period gets the one its amount suggests: 15,000 RON is a month, $80k a year. Each is then converted to the currency and period you pick, with the European Central Bank's daily reference rates (downloaded when needed and kept for six hours; approximate built-in rates stand in when they can't be loaded, and the page says so). A year counts as 2,080 hours, 260 days or 12 months. The page shows the median, the middle half, the highest salary, each job's range, and the median by status; salaries it can't read are listed with a field to correct them.

**Search** (`/search`) builds Boolean queries from job titles, locations, skills, work and employment types and terms to exclude: for Google limited to the job boards companies use (Lever, Greenhouse, Ashby...), LinkedIn, Reddit, X and the remote job boards. Google reads only the first 32 words of a query, so the generator leaves out the terms that don't fit, last ones first, and says so. A description of the job you want ("senior backend engineer, remote Europe, contract") fills in the filters. The address keeps the filters, so a search can be bookmarked. The rules are in the `search` module (`QueryGenerator`, `DescriptionParser`).

## Project structure

A modular monolith: one Spring Boot application split into Maven modules. The domain modules hold the business logic and know nothing about the web. Two adapter modules expose them: `web` to the browser, `capture` to the extension. `common` holds the little code they all share.

| Module | Responsibility | Depends on |
|---|---|---|
| `common` | Shared: `ErrorCode`, the base exceptions and the application logger (`JobWardenLogger`). No beans, no tables, no business logic | none |
| `identity` | Domain: local profiles (`users` table) | `common` |
| `jobs` | Domain: jobs bookmarked or applied to, with each application's status and event timeline, and their salaries compared | `common` |
| `search` | Domain: Boolean job-search queries for Google, LinkedIn, Reddit and X, and the description parser that fills in their filters; stateless | none |
| `web` | Adapter: browser UI. Page controllers, templates, shared layout, Tailwind and HTMX | `common`, `identity`, `jobs`, `search` |
| `capture` | Adapter: REST endpoint for the browser extension | `common`, `identity`, `jobs` |
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
| `config` | Spring configuration, e.g. `LocaleConfiguration` | `web` |

In the domain modules, `dto`, `enums`, `service` and `exception` are the public API. Each is marked `@NamedInterface` in its `package-info.java`, which tells Spring Modulith that other modules may use it. `service` uses `propagate = false`, so `service.impl` stays internal, as do `entity` and `repository`. In `common`, `exception` and `logging` are marked the same way.

Conventions:

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
  - In `web`, `WebExceptionHandler` turns them into an HTTP status, and every error page renders with [error.html](web/src/main/resources/templates/error.html) inside the layout.
  - In `capture`, `CaptureExceptionHandler` returns [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details (JSON) for the extension, with the code in an `errorCode` field. Every error carries a code, Spring MVC's own ones too: `COMMON-002` for a malformed request, and validation errors add an `errors` object (field → message).
- **Logging:** put Lombok's `@CustomLog` on a class (not `@Slf4j`) to get a `log` field of type `JobWardenLogger`, set up in [lombok.config](lombok.config). `debug`, `info` and `warn` are plain SLF4J. `error` also takes an error code as its first argument, and needs one: `log.error(JobsErrorCode.SAVE_FAILED, "Could not save application {}", id, exception)`. The code is added to the message and as a separate `errorCode` field. Each module defines its codes as an enum implementing `ErrorCode` in its `exception` package, prefixed with the module's name (`JOBS-001`). Levels:
  - **INFO:** what changes (created, moved to another status, deleted).
  - **DEBUG:** reads, rejected forms and captures. Turn it on with `logging.level.com.jobwarden=DEBUG`; the line is ready in `application.properties`.
  - **WARN:** the modules' own exceptions, logged once by the exception handlers.
  - **ERROR:** only unexpected failures, always with a code.

  Log ids, never names or job details.
- **Page controllers extend `BaseController`:**
  - Before every handler it puts the selected profile in the model as `currentProfile`, which the header shows. That's the only time a request looks the profile up.
  - Handlers that need a profile call `requireProfile(model)`. With no profile selected it throws `ProfileNotSelectedException`, and `WebExceptionHandler` sends the browser to `/profiles` (HTMX requests get an `HX-Redirect` header instead of a redirect).
  - It also has `redirectTo(path)` and `fieldErrors(result)`, which maps each form field to its first validation message.
  - It gets `CurrentUser` through a setter, so the subclasses keep their Lombok constructors. Its `@WebMvcTest`s need a `@MockitoBean CurrentUser`.
- **Constants instead of repeated strings:**
  - `web`'s `AppConstants` holds the routes, template names, model attribute names and HTMX headers.
  - A form's length limits are constants on the form record (e.g. `NewEventForm.STAGE_LABEL_MAX_LENGTH`), and its validation messages use `{max}`.
  - `ErrorCode.PROPERTY_NAME` is the one name for the error code, in logs and in error responses.
- **Dates and times:** a timeline event has a date, and a time of day only when it's known (`LocalDate` + `LocalTime`; `DATE` + `TIME` columns). Within a day, events without a time come first. They're wall-clock values, as entered; "now" is taken in the server's time zone, so set `TZ` when running in Docker (which defaults to UTC). Moments that are only ever recorded, like `capturedAt`, are instants (`Instant`, `TIMESTAMPTZ`). The forms have a date field and an optional time field with a Clear time button (a time emptied only in part is invalid, and the browser won't send the form); a form that sends no date, like the status buttons, means now, with the time. The capture API adds the time too, unless the request says `"includeTime": false`. Display patterns are the `format.date` and `format.dateTime` messages, so each language has its own.
- **Entities guard their own rules:** `ApplicationEntity.getEvents()` is read-only and `TimelineEventEntity`'s constructor is package-private, so events can only be added through `addEvent`, which keeps the status in step with the timeline.
- **Formatting:** [.editorconfig](.editorconfig) sets tabs for Java and XML plus the import order. In IntelliJ, *Code → Reformat Code* with *Optimize imports* applies it.

Templates live in `web/src/main/resources/templates/<feature>/`, and all pages share [layout.html](web/src/main/resources/templates/layout.html). Never name a model attribute `application`, `session` or `param`: Thymeleaf reserves those names, so the template would read something else. HTMX endpoints return a fragment of the page's template, e.g. `applications/detail :: application`.

Rules:

- A module's public API is its root package plus the packages marked `@NamedInterface`. Everything else is private to that module. Other modules never touch another module's entities or repositories.
- Domain modules never depend on the adapters. Business logic belongs in a domain module, never in a controller.
- [ModularityTests](app/src/test/java/com/jobwarden/ModularityTests.java) (Spring Modulith) fails the build if a module uses another module's internals or if module dependencies form a cycle.
- Each module keeps its own Flyway migrations in `src/main/resources/db/migration`, named `V<n>__<module>_<description>.sql`. Version numbers are shared by all modules: a new migration takes the next free number across the whole project, not just its own module. Hibernate only validates the schema; it never changes it.

## Languages

The pages are in English, Romanian, French, German, Polish, Spanish and Portuguese (`Language` in `web`).

- **Which language:** the one picked in the header (remembered in a cookie for a year), otherwise the browser's preferred language if the app has it, otherwise English. The capture API answers in the language of the request's `Accept-Language` header, which the extension sends like any browser request.
- **Where the texts live:** `web/src/main/resources/i18n/web.properties` (pages, form validation, status and rejection reason names), and each module's error messages next to its code in `<module>/src/main/resources/i18n/<module>-errors.properties`, under `error.<code>` (e.g. `error.JOBS-001`). Each has one file per language, e.g. `web_ro.properties`. English is the file without a suffix, and the fallback for everything.
- **In templates:** `th:text="#{applications.title}"`; with arguments `#{application.added(${date})}`; an enum value `#{${'status.' + status}}`. Validation messages refer to a key: `@NotBlank(message = "{validation.profile.name.required}")`. The modules' exceptions carry arguments for their message's `{0}`, `{1}`...
- **Adding a text:** add the key to the English file and to every translation. [MessageBundlesTests](app/src/test/java/com/jobwarden/MessageBundlesTests.java) fails if a translation is missing a key, or if an error code has no message.
- **The extension:** its texts are in `extension/_locales/<language>/messages.json`; pages name them with `data-i18n="key"`, scripts with `message('key')`. [ExtensionMessagesTests](app/src/test/java/com/jobwarden/ExtensionMessagesTests.java) checks them the same way, and that every message they name exists.
- **Adding a language:** a constant in `Language`, a file per bundle, and a folder in `extension/_locales`.
- **Adding a module with error codes:** its `i18n/<module>-errors` files, plus the bundle in `spring.messages.basename` (application.properties) and in `MessageBundlesTests`.
- **Apostrophes:** write `’`, not `'`. In a message with `{0}`, Java's `MessageFormat` treats `'` as a quote character and swallows text.
- **Logs stay in English:** they're for whoever runs the app, not for users.

## Testing

`./mvnw verify` runs everything; so does CI on every pull request and on every push to `main`. Each kind of test has one place:

| Test | Where | Example |
|---|---|---|
| Unit tests (plain JUnit + Mockito) | the module itself | [JobWardenLoggerTests](common/src/test/java/com/jobwarden/common/logging/JobWardenLoggerTests.java), [CurrentUserTests](web/src/test/java/com/jobwarden/web/user/CurrentUserTests.java) |
| Controller tests (`@WebMvcTest`) | `web` and `capture`, next to the controller | [ApplicationControllerTests](web/src/test/java/com/jobwarden/web/controller/ApplicationControllerTests.java), [CaptureControllerTests](capture/src/test/java/com/jobwarden/capture/controller/CaptureControllerTests.java), [CaptureExceptionHandlerTests](capture/src/test/java/com/jobwarden/capture/controller/CaptureExceptionHandlerTests.java) |
| Service tests against a real database (`@ApplicationModuleTest`) | `app`, in the module's package, e.g. `app/src/test/java/com/jobwarden/jobs/` | [ApplicationServiceTests](app/src/test/java/com/jobwarden/jobs/ApplicationServiceTests.java) |
| Whole application and module boundaries | `app` | [ModularityTests](app/src/test/java/com/jobwarden/ModularityTests.java) |

Why database tests live in `app`: the migrations only run there, and `jobs`' tables reference `identity`'s. `@ApplicationModuleTest` still starts just the one module, which also proves the module doesn't depend on other modules' beans.

`web` and `capture` can't see the real application class, so each has a small `*TestApplication` in its test sources for `@WebMvcTest` to start from.

## License

Source-available under the [PolyForm Shield License 1.0.0](LICENSE.md). You are free to clone, use, modify and self-host it. You may not offer it, or anything built from it, as a competing product, whether hosted, sold or given away.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Contributions require agreeing to the Contributor License Agreement described there.
