# Code quality roadmap

The plan for bringing every module, every `pom.xml` and the frontend up to production standard: how the code is
written, how it is checked, and how it behaves when it runs. It is worked through over several sessions; tick an item
when it lands, and add what new work turns up.

The project already did much of this well: a modular monolith with Spring Modulith's boundary checks, records for
DTOs, package-private implementations, error codes on every failure, `@CustomLog`, Flyway per module, XXE-safe XML
parsing, size-bounded zip reading, `ProcessBuilder` without a shell, the MCP server's `Origin` check, CodeQL,
SonarCloud and Dependabot. What follows is what was missing.

## 1. Build and tooling (all `pom.xml`)

- [x] Maven Enforcer: the Java and Maven versions the build needs, no dependency declared twice, no version ranges.
- [x] Compiler: `-Xlint:all` with warnings as errors; the 17 warnings there were are fixed (deprecated APIs, `this`
      escaping entity constructors, a misplaced Javadoc, redundant casts, unchecked varargs in tests).
- [x] Reproducible builds: `project.build.outputTimestamp`, so the same commit builds the same jar.
- [x] Project metadata filled in (`url`, `scm`, `developers`, `issueManagement`, `ciManagement`).
- [x] `spring-boot-configuration-processor`, so `@ConfigurationProperties` get IDE completion and docs.
- [x] `build-info` and `git.properties` in the jar: `/actuator/info` says which version and commit is running.
- [x] CycloneDX SBOM in the app's jar (`META-INF/sbom/application.cdx.json`), for vulnerability scanners.
- [x] Spotless: import order and unused imports, as `.editorconfig` describes them, checked by the build.
- [x] JSpecify `@NullMarked` on all 102 packages, and NullAway (on Error Prone) in the `nullaway` profile, which CI
      runs: the 110 places where a `null` could reach code that didn't expect one are fixed. A profile because Error
      Prone needs `.mvn/jvm.config`'s flags, which IntelliJ's own compiler doesn't get.
- [ ] More Error Prone checks beyond NullAway (e.g. `MissingOverride`, `ReferenceEquality`, `UnusedVariable`), each
      turned on once the code passes it.

## 2. Operations (production readiness)

- [x] Spring Boot Actuator: `health` (with liveness and readiness groups, the database in readiness) and `info`;
      nothing else exposed, since the server has no sign-in.
- [x] Graceful shutdown: requests in flight finish, for up to 20 seconds.
- [x] A request id on every request: in the logs (MDC `requestId`) and in the `X-Request-Id` response header; an id the
      caller sends is kept when it's a plain token.
- [x] Structured (JSON) logs on demand: `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`.
- [x] Virtual threads for request handling and scheduled tasks.
- [x] Docker: a `HEALTHCHECK` (bash's `/dev/tcp`, as the JRE image has no curl), and JVM settings for a container
      (heap as a share of the container's memory, exit on `OutOfMemoryError`).
- [ ] One way to make outbound HTTP calls: seven classes build their own `java.net.http.HttpClient`, with their own
      timeouts and `User-Agent`. Spring's `RestClient` on the JDK client would give them one configuration, Micrometer
      metrics (`http.client.requests`) and `MockRestServiceServer` in tests. Keep the hand-checked redirects of
      `LogoDownloader` and `PostingChecker`, which refuse private addresses at every hop.
- [x] Metrics: `/actuator/metrics` exposed; each scheduled check's runs, time and outcome are
      `tasks.scheduled.execution`, which Spring records once Actuator is there.
- [x] Each run of a scheduled check logs with an id of its own, like a request (`LogContext`, set by an observation
      handler around each `@Scheduled` run, in `SchedulingConfiguration`), and so does the background update check.
- [x] Runbook ([docs/operations.md](operations.md)): settings, health endpoints and metrics, logs, backups of the
      database and the key, upgrading, what to do when something's wrong.

## 3. Security hardening

- [x] Security headers on every response: `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` (job boards
      learn nothing of the server), `Permissions-Policy`, `Cross-Origin-Opener-Policy`; and a strict
      `Content-Security-Policy` on HTML pages (`script-src 'self'`, no eval). PDFs get none, so Chrome's viewer opens
      them.
- [x] No inline scripts or event handlers in the templates (the theme script is `theme.js`; `onclick` became
      `data-clear-previous`), and HTMX told never to evaluate code from the page.
- [x] Hiring's request form is validated (`@Valid`): one sent without its kind answered 500, now 400.
- [ ] **Decision needed:** a `Host` header allow-list against DNS rebinding. A page on a domain that resolves to
      127.0.0.1 is same-origin with the server, so CORS and the MCP server's `Origin` check don't stop it. Allowing
      `localhost`, IP literals and the hosts in `jobwarden.allowed-hosts` would, but a server reached by a LAN host
      name would then need that setting.
- [x] The configuration is checked as the app starts: the domain modules' `@Validated` properties (section 4), and a
      `JOBWARDEN_SECRET_KEY` that isn't 32 bytes in Base64, which only failed at the first password before.

## 4. Code structure and patterns (each module)

- [x] JDBC reads in `java.time` (`getObject(column, LocalDate.class)`) rather than `java.sql.Date`/`Timestamp`.
- [x] `ApplicationQueriesImpl` takes its `EntityManager` in its constructor, like every other class.
- [x] `mcp`: seven copies of the same `shortened` helper became one (`Texts`); `ToolCall.requiredConstant` for
      required enum arguments, which answered with a `NullPointerException` when left out.
- [x] `jobs`: the reminders keep the entry they're due for instead of looking it up twice; the skill gaps count in one
      object instead of three maps kept in step; the ECB rates hand back the table they settled on.
- [x] A `Clock` bean instead of the ~70 `Instant.now()`, `LocalDate.now()` and `ZoneId.systemDefault()` calls in 32
      classes; entities keep stamping their audit times themselves. `ConventionsTests` refuses new ones.
- [x] `@ConfigurationProperties` records for the domain modules' settings (`updates`, `market`, `inbox`, `watchlist`,
      `cv`): typed, validated, documented, registered by each module. The adapters keep `@Value` for the route paths
      their `@RequestMapping`s share, on final fields Lombok copies onto the constructor; `DiscoverController`'s setter
      injection is gone, and `ConventionsTests` refuses new setter injection.
- [ ] Tests that use the `Clock`: the reminders, follow-ups and statistics at a fixed moment, rather than relative to
      the real one.
- [ ] `BaseController`'s setter injection replaced by composition: the layout's model attributes from a
      `@ControllerAdvice`, the prompt helpers from a component. The layout makes four queries per page; one would do.
- [x] Strategy pattern for the job boards: a sealed `BoardFeed` with `GreenhouseFeed`, `LeverFeed` and `AshbyFeed`,
      each with its addresses and JSON; `BoardFeeds` (514 lines, now 329) does the reading the same for all, and an
      exhaustive `switch` makes a new ATS name its feed.
- [ ] Split the largest classes along their responsibilities: `RequestTools` (835 lines), `AppConstants` (649),
      `JobTools`, `DefaultApplicationService`, `DefaultApplicationCsvService`, `DefaultWatchlistService`.
- [ ] Domain events (Spring Modulith `@ApplicationModuleListener`) where one module now calls another only to tell it
      something happened.

## 5. Tests

- [x] Architecture tests for the conventions README.md describes (`ConventionsTests`, ArchUnit): service
      implementations and controllers package-private, records for public DTOs, constructor injection, one logger, no
      `System.out`, no generic exceptions, no transactions in controllers, entities without
      `toString`/`equals`/`hashCode`, exceptions with an error code, `java.time` only, unique module-prefixed error
      codes, every package `@NullMarked`.
- [x] Tests for the new filters (`RequestIdFilterTests`, `SecurityHeadersFilterTests`).
- [ ] Unit tests in the modules that have none of their own (`identity`, `discover`, `hiring`, `apply`).
- [ ] A coverage floor in the `coverage` profile (JaCoCo `check`), raised as tests are added.

## 6. Frontend (`web` templates and `app.js`, `extension/`)

- [ ] `app.js` (1,044 lines, one global script) split into ES modules by feature.
- [ ] ESLint and Prettier for `web` and `extension`, run by the build.
- [ ] Accessibility pass: labels, focus order, keyboard use of the swipe cards and the pipeline's drag and drop.
- [ ] `extension/easyapply.js` (1,194 lines) and `extract.js` (770) split by site and step.

## 7. Documentation

- [x] Architecture decision records ([docs/adr](adr/README.md)): modular monolith, no sign-in, the assistant through
      MCP, null-safety, time and configuration.
- [ ] `CHANGELOG.md`, kept per release.
