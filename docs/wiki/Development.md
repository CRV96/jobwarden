# Development

Building JobWarden from its sources, testing it, translating it and releasing it. How the code is organized, and the
conventions the build checks, are on [What's what](https://github.com/CRV96/jobwarden/blob/main/Architecture). [CONTRIBUTING.md](https://github.com/CRV96/jobwarden/blob/main/CONTRIBUTING.md) says how to
propose a change.

## Running locally

Requires **JDK 25** or later and **Docker**. The app starts its own PostgreSQL container from `app/compose.yaml`:

```bash
./mvnw -pl app -am spring-boot:run
```

Build and run every test (the database tests use Testcontainers, so Docker must be running), with NullAway's checks as
CI runs them:

```bash
./mvnw verify -Pnullaway
```

## Frontend

Pages are server-rendered with Thymeleaf; HTMX handles partial updates without a page reload. The menu lists the Search Builder, Who's hiring, AI Assistant, Auto-apply, Jobs (a list or a board), Calendar, Salaries and CV; Settings and the setup guide are in the profile menu at its foot. From 1024 pixels wide every page fills the window (`page-full` in `app.css`), and its lists and panels scroll on their own: the jobs under their header row, each column of the board, the calendar. Pages of two panels side by side (the AI Assistant, the CV, the salaries, a job's page) do so from 1280 pixels (`page-split`, with `scroll-panel` for each panel); narrower, their panels stack and the page scrolls. Tailwind CSS is compiled during the Maven build, which downloads its own Node into `web/target/`, so nothing extra needs installing. While editing templates, keep the CSS up to date with:

```bash
./mvnw -pl web frontend:npm@tailwind-watch
```

The pages' JavaScript is plain ES modules, with no bundler: [static/js/app.js](https://github.com/CRV96/jobwarden/blob/main/web/src/main/resources/static/js/app.js) imports one module per feature from `static/js/app/` (dialogs, forms, the list's selection, fields of chips, the calendar, reminders, the pipeline...). A module listens for the events only it handles; a click, change or input that several features might claim goes through one ordered chain in `app.js`, where the first handler that says it was its own ends it. ESLint checks it on `./mvnw verify` (`web/eslint.config.mjs`); in `web/`, `npm run lint` runs it alone.

## Languages

The pages are in English, Romanian, French, German, Polish, Spanish, Portuguese and Hindi (`Language` in `web`). In Hindi, the CV's own headings (`cv.doc.*`) stay in English: they go into its PDF, whose font (Noto Sans) has no Devanagari, and CVs in India are mostly written in English.

- **Where the texts live:** `web/src/main/resources/i18n/web.properties` (pages, form validation, status and rejection reason names), and each module's error messages next to its code in `<module>/src/main/resources/i18n/<module>-errors.properties`, under `error.<code>` (e.g. `error.JOBS-001`). Each has one file per language, e.g. `web_ro.properties`. English is the file without a suffix, and the fallback for everything.
- **In templates:** `th:text="#{applications.title}"`; with arguments `#{application.added(${date})}`; an enum value `#{${'status.' + status}}`. Validation messages refer to a key: `@NotBlank(message = "{validation.profile.name.required}")`. The modules' exceptions carry arguments for their message's `{0}`, `{1}`...
- **Adding a text:** add the key to the English file and to every translation. [MessageBundlesTests](https://github.com/CRV96/jobwarden/blob/main/app/src/test/java/com/jobwarden/MessageBundlesTests.java) fails if a translation is missing a key, or if an error code has no message.
- **Adding a language:** a constant in `Language`, a file per bundle, and and a folder in `extension/_locales`.
- **Adding a module with error codes:** its `i18n/<module>-errors` files, plus the bundle in `spring.messages.basename` (application.properties) and in `MessageBundlesTests`.
- **Apostrophes:** write `’`, not `'`. In a message with `{0}`, Java's `MessageFormat` treats `'` as a quote character and swallows text.
- **Logs stay in English:** they're for whoever runs the app, not for users.

## Testing

`./mvnw verify` runs everything. On every pull request, every push to `main` and every release tag, GitHub Actions runs one workflow, [CI](https://github.com/CRV96/jobwarden/blob/main/.github/workflows/ci.yml), whose jobs each wait for what they need:

| Job | What | After |
|---|---|---|
| `build` | Compiles every module with Error Prone and NullAway, checks the imports and the pages' JavaScript (ESLint), builds the CSS, packages the jar | |
| `test` | All the tests, with what they cover (`-Pcoverage`, JaCoCo) | `build` |
| `sonar` | SonarCloud's analysis, with that coverage, and its quality gate's verdict. Skipped on pull requests from forks, which GitHub gives no secrets (`SONAR_TOKEN`): their code is analyzed once merged, on `main` (see below) | `test` |
| `docker` | Builds the image | `build` |
| `publish` | On `main` and tags: publishes the image | `test`, `docker` |
| `release` | On tags: publishes the release (below) | `publish` |
| `dependency-review` | On pull requests: the dependencies they add, failing on a high-severity vulnerability | |
| `codeql` | Code scanning of the Java, the JavaScript and the workflows | |

[CodeRabbit](https://github.com/CRV96/jobwarden/blob/main/.coderabbit.yaml) reviews each pull request (a GitHub App, so its check shows beside the workflow's rather than in it), and [Dependabot](https://github.com/CRV96/jobwarden/blob/main/.github/dependabot.yml) keeps the dependencies and the actions up to date. The "Protect Main" ruleset ([.github/rulesets](https://github.com/CRV96/jobwarden/blob/main/.github/rulesets)) makes a pull request wait for the build, the tests, the dependency review, the image, SonarCloud's quality gate and CodeQL, and for the code owner's approval ([CODEOWNERS](https://github.com/CRV96/jobwarden/blob/main/.github/CODEOWNERS)). GitHub doesn't let anyone approve their own pull request, so the owner's go through the admin bypass, which skips the checks too: merge those only once they're green. A pull request from a fork never gets SonarCloud's check, as its run has no `SONAR_TOKEN`: the owner reviews its code with the other checks green, merges it through the same bypass, and `main`'s run analyzes it right after; a quality gate failing there is fixed in a pull request of the owner's. The file doesn't apply itself; after changing it, run `gh api -X PUT repos/CRV96/jobwarden/rulesets/26456384 --input .github/rulesets/protect-main.json`. See [SECURITY.md](https://github.com/CRV96/jobwarden/blob/main/SECURITY.md) for reporting a vulnerability.

Each kind of test has one place:

| Test | Where | Example |
|---|---|---|
| Unit tests (plain JUnit + Mockito) | the module itself | [JobWardenLoggerTests](https://github.com/CRV96/jobwarden/blob/main/common/src/test/java/com/jobwarden/common/logging/JobWardenLoggerTests.java), [CurrentUserTests](https://github.com/CRV96/jobwarden/blob/main/web/src/test/java/com/jobwarden/web/user/CurrentUserTests.java) |
| Controller tests (`@WebMvcTest`) | `web` and `capture`, next to the controller | [ApplicationControllerTests](https://github.com/CRV96/jobwarden/blob/main/web/src/test/java/com/jobwarden/web/controller/ApplicationControllerTests.java), [CaptureControllerTests](https://github.com/CRV96/jobwarden/blob/main/capture/src/test/java/com/jobwarden/capture/controller/CaptureControllerTests.java), [CaptureExceptionHandlerTests](https://github.com/CRV96/jobwarden/blob/main/capture/src/test/java/com/jobwarden/capture/controller/CaptureExceptionHandlerTests.java) |
| Service tests against a real database (`@ApplicationModuleTest`) | `app`, in the module's package, e.g. `app/src/test/java/com/jobwarden/jobs/` | [ApplicationServiceTests](https://github.com/CRV96/jobwarden/blob/main/app/src/test/java/com/jobwarden/jobs/ApplicationServiceTests.java) |
| Whole application and module boundaries | `app` | [ModularityTests](https://github.com/CRV96/jobwarden/blob/main/app/src/test/java/com/jobwarden/ModularityTests.java) |
| The code's conventions (ArchUnit) | `app` | [ConventionsTests](https://github.com/CRV96/jobwarden/blob/main/app/src/test/java/com/jobwarden/ConventionsTests.java) |
| What the AI assistant reads: the tool list and each kind of request, in characters, with budgets | `mcp` | [McpBudgetTests](https://github.com/CRV96/jobwarden/blob/main/mcp/src/test/java/com/jobwarden/mcp/controller/McpBudgetTests.java), which prints the table and writes it to `mcp/target/mcp-budget.tsv` |

Why database tests live in `app`: the migrations only run there, and `jobs`' tables reference `identity`'s. `@ApplicationModuleTest` still starts just the one module, which also proves the module doesn't depend on other modules' beans.

`web` and `capture` can't see the real application class, so each has a small `*TestApplication` in its test sources for `@WebMvcTest` to start from.

## Versions and releases

What changed from one release to the next is in [CHANGELOG.md](https://github.com/CRV96/jobwarden/blob/main/CHANGELOG.md):
each change adds its line under *Unreleased*.

The app and the extension share one version: the Maven projects' (`<version>` in every `pom.xml`) and `version` in
`extension/manifest.json`; [VersionTests](https://github.com/CRV96/jobwarden/blob/main/app/src/test/java/com/jobwarden/VersionTests.java)
fails when they differ. Maven writes it into `jobwarden.version`, which Settings, the MCP server and the extension
show.

To release a version:

1. Set it everywhere: `./mvnw versions:set -DnewVersion=1.0.2 -DgenerateBackupPoms=false`, and `"version": "1.0.2"` in
   the manifest.
2. Commit, then tag the commit and push the tag: `git tag v1.0.2 && git push origin v1.0.2`.

The tag's run of [CI](https://github.com/CRV96/jobwarden/blob/main/.github/workflows/ci.yml) checks that the tag, the
Maven projects and the manifest say the same, builds and tests, publishes the tag's image, then a GitHub release with
the app's jar, the extension's zip, a `compose.yaml` for that version, and notes made from the pull requests since the
last release. Each is attested as built from the tag
([SECURITY.md](https://github.com/CRV96/jobwarden/blob/main/SECURITY.md#how-the-project-is-checked) shows how to
verify it).

## The wiki

These pages are `docs/wiki/` in the repository: change them in a pull request like the code. On every push to `main`,
the [Wiki workflow](https://github.com/CRV96/jobwarden/blob/main/.github/workflows/wiki.yml) copies them to the GitHub wiki, replacing what was there:
an edit made on the wiki itself doesn't last. A page links to another by its name without `.md`, e.g.
`[Updating](https://github.com/CRV96/jobwarden/blob/main/Updating)`; `_Sidebar.md` is the menu beside every page.
