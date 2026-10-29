# JobWarden

The keeper of your job hunt: save job postings from the browser in one click, and keep every application's story, from the first save to the offer.

[![Build](https://github.com/CRV96/jobwarden/actions/workflows/build.yml/badge.svg)](https://github.com/CRV96/jobwarden/actions/workflows/build.yml)

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

Pages are server-rendered with Thymeleaf; HTMX handles partial updates without a page reload. The menu lists the Search Builder, Who's hiring, AI Assistant, Auto-apply, Jobs, Pipeline, Calendar, Salaries and CV; Settings are in the profile menu at its foot. From 1024 pixels wide every page fills the window (`page-full` in `app.css`), and its lists and panels scroll on their own: the jobs under their header row, each pipeline column, the calendar. Pages of two panels side by side (the AI Assistant, the CV, the salaries, a job's page) do so from 1280 pixels (`page-split`, with `scroll-panel` for each panel); narrower, their panels stack and the page scrolls. Tailwind CSS is compiled during the Maven build, which downloads its own Node into `web/target/`, so nothing extra needs installing. While editing templates, keep the CSS up to date with:

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

**Easy Apply run:** on a LinkedIn job search, the popup has **Start the run**. The extension then goes down the search's results, page after page, one job at a time ([easyapply.js](extension/easyapply.js), kept going by [runs.js](extension/runs.js)):

- **Easy Apply jobs:** it opens the form, fills each step in and sends it, and the job is saved to JobWarden as Applied, **by the extension** (`applied_by` `EXTENSION`, shown like **by Claude**). The answers are your CV's contact details and your answers on the Auto-apply page, its own questions included; LinkedIn's own answers from earlier applications stay as they are, and the CV is the one LinkedIn has.
- **It never makes an answer up.** A required question none of your answers covers stops the run: a bar at the bottom of the page shows the question, you answer it on LinkedIn's form, or under **Your own questions** on the Auto-apply page, and click **Continue** (or **Skip this job**, which closes the form without sending it). What you answered on the form is kept among the Auto-apply page's own questions, so the next form with that question is filled in; the run reads your answers from JobWarden again before each form and after each stop, so what you change on the Auto-apply page counts at once, during the run too. The run stops the same way at an error LinkedIn shows under a field, and at a LinkedIn security check, which it never touches: you do it, then **Continue**. A number field takes only a number (a salary written as "5,500 EUR a month" is for you to answer), and "how many years of experience with X" is yours too: the years-of-experience answer is for all your work.
- **Other jobs:** saved to JobWarden for the Auto-apply page, unless you untick **Save the other jobs for Auto-apply**. Jobs JobWarden has as applied to or further along, and those LinkedIn shows as applied to or closed, are skipped.
- **When it stops:** after the number of applications you set (25 unless you change it, 100 at most), at the end of the search, or when you click **Stop** on the bar or **Stop the run** in the popup. It doesn't pause between jobs to look like a person, and it doesn't hide from LinkedIn. **LinkedIn's terms don't allow automated applying, and LinkedIn can restrict accounts that do it**: the popup says so, and the risk is yours.
- **Permissions:** starting a run asks for linkedin.com, so the run goes on when LinkedIn loads the page anew; without it, it runs until then. LinkedIn changes its pages now and then: the run works on both its older job search (`/jobs/search/`) and its newer one (`/jobs/search-results/`, whose class names change with each release, so the run goes by its `componentkey`, `data-testid` and `data-sdui-screen` attributes, aria-labels and icon ids), and stops for you rather than guess when a page isn't what it expects. On the newer page a number field looks like any text field, so a question that asks for a count ("how many", "in days") takes only an answer that's a number.

- **What it reads:** each field from the first of these that has it:
  - Text you selected on the page before clicking the icon, as the description. Use it where nothing else finds the description.
  - The posting's schema.org `JobPosting`, which most job boards and career sites include for Google's job search, as JSON-LD or microdata. It has every field, and the whole object is kept as the application's raw payload.
  - On a search that shows a job beside the results, the job's own page, fetched in the background, with its `JobPosting`: on Indeed and Glassdoor. On LinkedIn, which doesn't include it for signed-in members: the job's public page (what visitors who aren't signed in see), then what's on screen, then the page title.
  - What the page shows, on sites whose `JobPosting` leaves fields out or that have none: Indeed, Glassdoor, Greenhouse, Workday, pracuj.pl, InfoJobs and BestJobs (`SITES` in [extract.js](extension/extract.js)). Sites like Lever, Ashby, StepStone, XING, Reed, NoFluffJobs, Just Join IT and eJobs need nothing more than their `JobPosting`.
  - The page itself: the Open Graph tags, a block marked as the job description, the `h1` heading, the page title.
- **The company's logo:** from the posting's `JobPosting` (its `hiringOrganization.logo`), which most job boards and career sites have, or on LinkedIn from the job's public page. The extension sends its address and the server downloads it, since most sites don't let the extension read their images, then shrinks it to a 64-pixel PNG shown before the job's title. The server only downloads from the public internet, never from its own machine or the local network: the address comes from the page. Without a logo, the pages show the company's initials.
- **The site's icon:** what the tab shows, drawn into a 32×32 PNG and saved with the posting, so JobWarden shows it next to the site instead of a globe. Chrome gives the extension its own copy of any page's icon. Firefox doesn't, so there the extension uses the tab's icon only when it can read it (when the icon is already part of the page, or its site lets anyone fetch it); otherwise the site keeps the globe.
- **One posting, one application:** the server matches captures on the exact link. So the extension drops tracking parameters (`utm_*`, `trackingId`...), and on LinkedIn, Indeed and Glassdoor it saves the job's own link, even from a search page. Saving a posting you already saved doesn't create a duplicate, and clicking **I applied** on a posting you bookmarked marks it applied.
- **The API it uses:** under `/api/captures`, set by `jobwarden.capture.path` in [application.properties](app/src/main/resources/application.properties); if you change it, change `CAPTURE_PATH` in [settings.js](extension/settings.js) too. `POST` saves a posting, with the site's icon as an optional `data:image/png;base64,...` URL in `icon` and the company logo's address in `logoUrl`; `GET ?userId=&link=` looks a link up (204 if it isn't saved); `POST /{id}/move-back-to-saved` takes back "I applied"; `DELETE /{id}?userId=` removes an application. Each answers with the application's current state: its id, title, company, status, and when it got that status. The Easy Apply run saves what it sent with `"action": "EASY_APPLY"`, gets the answers to fill forms in with from `GET /answers?userId=`, and keeps what you answered during a run with `POST /answers?userId=` (`{"question", "answer"}`). Firefox applies CORS to an extension's requests even with the host permission, and Chrome, which doesn't, still sends the extension's origin with a POST, so this API allows extension origins (`moz-extension://` and `chrome-extension://`); web pages still can't call it.
- **Languages:** the app's, in `extension/_locales/<language>/messages.json`. Chrome uses the browser's language.

## Salaries and the Search Builder

**Salaries** (`/salaries`) compares what the jobs you track pay. Salaries are kept as the postings wrote them, so `SalaryService` in `jobs` reads them first: the first two amounts make the range (80,000, 80.000, 80 000, 80'000, 80k), a code or symbol gives the currency, and words in the extension's languages give the period (per hour, pro Monat, pe lună...). A salary without a period gets the one its amount suggests: 15,000 RON is a month, $80k a year. Each is then converted to the currency (euros unless you pick another) and period you pick, with the European Central Bank's daily reference rates (downloaded when needed and kept for six hours; approximate built-in rates stand in when they can't be loaded, and the page says so). A year counts as 2,080 hours, 260 days or 12 months. The page shows the median, the middle half, the highest salary, each job's range, and the median by status; salaries it can't read are listed with a field to correct them.

**Market salaries**, beside your own on the same page (below them in a narrower window), show what a role pays across countries, in the page's currency and period, from three sources (the `market` module):

- **The Stack Overflow Developer Survey** (2025): what developers in work said they earn in a year, as the median and the middle half, per country, role (`DevType`) and programming language, from groups of at least five answers. It covers Romania and 160+ other countries. The answers (a 140 MB CSV) are downloaded once, with the section's button, and only the figures are kept, in `market_salaries`. Data under [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/), credited on the page. The address is `jobwarden.market.stack-overflow.url`; a `file:` address works too.
- **Adzuna** (live): the salaries current job ads give, from the API's salary histogram, for the role or the language ("java developer"). Adzuna has about 18 countries, Romania not among them. It needs a free `app_id` and `app_key` from [developer.adzuna.com](https://developer.adzuna.com): enter them in **Settings → Salaries**, which checks them with Adzuna, keeps them in the database (`market_adzuna_key`, one key for every profile) and uses them at once, with no restart; afterwards the page shows only the key's last characters. The `ADZUNA_APP_ID` and `ADZUNA_APP_KEY` environment variables still work, and a key saved in Settings takes their place. Without a key the page leaves job ads out and links to Settings. Answers are kept for a day, and asked again when the key changes. Adzuna is credited where its figures show, as its terms ask.
- **Eurostat's Structure of Earnings Survey** (2022, live, no key): mean gross yearly earnings of professionals in information and communication, per country of the European Economic Area, and for the EU. Broad (every role together), and every four years, but official.

The section loads after the page (HTMX), as its sources may take a moment. The dashed line on its chart is the median of your own jobs.

**Search Builder** (`/search`) builds Boolean queries from job titles, locations, skills, work and employment types and terms to exclude: for Google limited to the job boards companies use (Lever, Greenhouse, Ashby...), LinkedIn, Reddit, X and the remote job boards. Google reads only the first 32 words of a query, so the generator leaves out the terms that don't fit, last ones first, and says so. A description of the job you want ("senior backend engineer, remote Europe, contract") fills in the filters. The address keeps the filters, so a search can be bookmarked. The page fills the window: the filters on the left, and on the right every platform's query at once, its operators, fields, phrases and exclusions in colour (`QuerySyntax` in `web`), each with Copy and Open search. The rules are in the `search` module (`QueryGenerator`, `DescriptionParser`).

## The AI Assistant, the CV, Auto-apply, Who's hiring and the calendar

**AI Assistant** (`/discover`) is where Claude works for you. Its presets ask Claude to find jobs matching your CV, find jobs for requirements you give (titles, where, skills, seniority, minimum salary, words to leave out...), run your saved searches, check which postings closed, or tidy up applications with no answer. Each preset saves a request (`ai_requests`) and shows a short prompt to paste into Claude Code, or a `claude "..."` command to run; Claude Code does the work through JobWarden's MCP server (`get_request` gives it step-by-step instructions, `finish_request` ends it with a summary). Leaving Claude Code running `/loop 15m Do the waiting JobWarden requests...` (the page gives it) has it pick up every request on its own. The page follows each request as it goes, lists the jobs Claude found for it with why each fits, and adds them to the cards to save, apply to or discard. JobWarden calls no AI itself: it needs no API key, and Claude runs on your own Claude subscription.

**CV** (`/cv`): upload your CV (PDF, Word or text, up to 5 MB), kept in PostgreSQL with the text read out of it (PDFBox for PDFs), or fill in the fields (personal details, summary, experience, education, skills, languages, certifications) and JobWarden builds one: previewed as HTML, downloaded as a PDF made from the same template with [openhtmltopdf](https://github.com/openhtmltopdf/openhtmltopdf). The PDF embeds Noto Sans (SIL Open Font License, in `web/src/main/resources/static/fonts`), so Romanian, Polish and German letters show. Claude reads the CV with the MCP tool `get_cv`.

**AI-proof CV**: on a job's page (and its side panel), Claude rewrites your CV for that job, so it gets through the screening software employers run CVs through and then reads well to the recruiter. The card asks Claude with a request, like the AI Assistant's presets; Claude reads your CV and the posting, puts what you really did in the posting's own words (never a skill, job, date or number you don't have), and saves it with `save_tailored_cv`, with the posting's keywords it covers, those it asks for that you can't honestly claim, and what it changed. The card shows all three, previews the CV and downloads it as a PDF ("CV Ana Pop - Acme.pdf"), laid out in one column with standard sections. The contact details stay the CV page's. Each job keeps one (`tailored_cvs`); the CV page lists them.

**Auto-apply** (`/apply`): Claude applies to your Saved jobs for you, in your own Chrome with your own sign-ins (Claude Code with Claude in Chrome: `claude --chrome`). You tick the jobs, choose whether Claude sends each form or fills it in for you to check and send, and paste the prompt the page gives into Claude Code (an `AUTO_APPLY` request). For each job Claude tailors your CV to it if it isn't yet, downloads it as a PDF to upload (from `/claude/cv?profile=…&job=…`, which, like the MCP server, trusts whoever can reach the server), and fills the form in from your CV's contact details and **your answers**, kept on the page: where you can work, visa sponsorship, notice period, earliest start, salary expectation, relocation, where and how you want to work, years of experience, cover letters (Claude writes one per job, or only when a form needs one), the voluntary questions (declined by default), and your own questions and answers. It never makes an answer up: a question none of them covers stops the job, and the page asks it to you; your answer is kept for the next forms, and the job tries again. Claude skips LinkedIn Easy Apply (the extension's Easy Apply run is for that) and sites whose terms forbid automated applying, and stops at a CAPTCHA for you to solve. The page shows how each job went (applied, filled in for you to send, waiting for your answer, skipped, failed); a job Claude applied to moves to Applied, with what it did on its timeline, and shows **by Claude** next to its status in the list, the pipeline and its page (`applications.applied_by`; the `apply` module: `application_answers` and `apply_items`).

**Who's hiring** (`/hiring`): recruiters' and hiring managers' posts saying they're hiring, the way LinkedIn feeds show them. Claude finds them when the page asks (a `FIND_HIRING_POSTS` request, for the roles, places and time you give): it searches LinkedIn, X and Reddit, looks at the people you follow first (`list_recruiters`), and adds the real posts with `add_hiring_posts`; JobWarden logs in nowhere. The Discover tab shows every post, the latest first, filtered by time, country, hashtag or a search; Following, only those of the people you follow; Recruiters, everyone, with their company, their number of posts and how they came. Recruiters come from four places: the authors of the posts; Claude, asked on the Recruiters tab (a `FIND_RECRUITERS` request for the roles and places you give, starting with the companies in your Jobs), which adds them with `add_recruiters` and a note on why each is worth following; the contacts you save on your jobs, with a name and a profile link, which become recruiters you follow the first time the page sees them, linked to their jobs; and those you add by hand. Follow a recruiter from any of their posts, remove one for good (Claude won't add them again), hide a post for good, or save it as a job, bookmarked, with the post as its description (the `hiring` module: `recruiters` and `hiring_posts`).

**Calendar** (`/calendar`): every job's timeline, interviews with their time, format and link, and the deadlines and days to follow up, by month, week or day (`?view=month|week|day&date=2026-10-27`). The month is a grid of days; each shows the entries its cell has room for and links to the day for the rest. The week and the day go hour by hour: interviews as long as they last, the entries without a time above the hours, overlapping ones side by side in a day (in a week, a "+N" badge that opens the day). Their days are never narrower than their text can be read at: the grid scrolls sideways instead, keeping the days and the hours in view, and an entry too short for its text shows all of it when pointed at. Every entry names its job with the company, e.g. "Software Engineer at Microsoft". Next to it, what's coming up in the next 30 days. Both resize with the window. A click opens the job in the side panel.

**Export and import**, in Settings → Your jobs: Export downloads every job as a CSV file (UTF-8, which spreadsheets open with their letters right), one row per job: title, company, location, status, status_date, applied_on, applied_by (YOU, CLAUDE or EXTENSION), salary, employment_type, link, source, rejection_reason, the contact and follow-up fields, notes, description, added_on and last_modified. Import reads one back, or a spreadsheet's, or another tracker's: only `title` is needed, the columns go by their header in any order and under their usual names too (Job title, Company name, URL, Date applied...), commas, semicolons or tabs between the values, and statuses as other trackers name them (Wishlist, Interview, Ghosted...). A job applied to before its status starts its timeline at Applied. Jobs you already have, by link or else by title and company, are skipped, so importing the same file twice adds nothing twice; Settings says how many were added, skipped, and which rows couldn't be read and why. A value that a spreadsheet would run as a formula is exported with an apostrophe in front.

**Contact & follow-up**: each job keeps who to talk to (name, role, email, phone, profile link), who referred you, when applications close and when to follow up next, saved as you type on its page. An Interviewing step on the timeline can say how the interview takes place (phone, video, on site, take-home), where (an address or the meeting's link) and how long. Claude sets them too, with `update_follow_up` and `update_job_status`.

## Project structure

A modular monolith: one Spring Boot application split into Maven modules. The domain modules hold the business logic and know nothing about the web. Two adapter modules expose them: `web` to the browser, `capture` to the extension. `common` holds the little code they all share.

| Module | Responsibility | Depends on |
|---|---|---|
| `common` | Shared: `ErrorCode`, the base exceptions and the application logger (`JobWardenLogger`). No beans, no tables, no business logic | none |
| `identity` | Domain: local profiles (`users` table) | `common` |
| `jobs` | Domain: jobs bookmarked or applied to, with each application's status and event timeline, and their salaries compared | `common` |
| `search` | Domain: Boolean job-search queries for Google, LinkedIn, Reddit and X, and the description parser that fills in their filters; stateless | none |
| `discover` | Domain: saved searches, the jobs found for them, and the requests the AI Assistant page makes of Claude | `common`, `jobs`, `search` |
| `market` | Domain: market salaries across countries, from the Stack Overflow survey, Adzuna and Eurostat | `common`, `jobs` |
| `cv` | Domain: the profile's CV, as an uploaded file with its text, as fields to build one from, and rewritten by Claude for each job | `common` |
| `hiring` | Domain: who's hiring: recruiters, found by Claude or saved as jobs' contacts, and their posts saying they're hiring | `common`, `jobs` |
| `updates` | Domain: the version running, and the latest release on GitHub | `common` |
| `apply` | Domain: auto-apply: the answers application forms ask for, and the jobs queued for Claude to apply to | `common`, `jobs` |
| `web` | Adapter: browser UI. Page controllers, templates, shared layout, Tailwind and HTMX; the CV's PDF | `common`, `identity`, `jobs`, `search`, `discover`, `market`, `cv`, `hiring`, `apply`, `updates` |
| `capture` | Adapter: REST endpoint for the browser extension, the versions it compares with its own, and the answers of its Easy Apply run | `common`, `identity`, `jobs`, `updates`, `apply`, `cv` |
| `mcp` | Adapter: the MCP server Claude works through, at `/mcp` | `common`, `identity`, `jobs`, `search`, `discover`, `cv`, `hiring`, `apply` |
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

## Versions and releases

The app and the extension share one version: the Maven projects' (`<version>` in every `pom.xml`) and `version` in `extension/manifest.json`; [VersionTests](app/src/test/java/com/jobwarden/VersionTests.java) fails when they differ. Maven writes it into `jobwarden.version`, which Settings, the MCP server and the extension show.

To release a version:

1. Set it everywhere: `./mvnw versions:set -DnewVersion=0.2.0 -DgenerateBackupPoms=false`, and `"version": "0.2.0"` in the manifest.
2. Commit, then tag the commit and push the tag: `git tag v0.2.0 && git push origin v0.2.0`.

The [Release workflow](.github/workflows/release.yml) checks that the tag, the Maven projects and the manifest say the same, builds and tests, and publishes a GitHub release with the app's jar, the extension's zip, and notes made from the pull requests since the last release.

Running servers ask GitHub for the latest release as they start and every six hours (`jobwarden.updates.repository`; `jobwarden.updates.check=false` never asks). A newer one shows in the sidebar (until it's hidden for that version), in Settings → About, and in the extension's popup, which also says when the server already runs a newer version than the extension. To update: get the release's code (git pull, or its files), restart the app, then reload the extension.

## License

Source-available under the [PolyForm Shield License 1.0.0](LICENSE.md). You are free to clone, use, modify and self-host it. You may not offer it, or anything built from it, as a competing product, whether hosted, sold or given away.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Contributions require agreeing to the Contributor License Agreement described there.
