# Running JobWarden

For whoever runs a JobWarden server: what it needs, how it's configured, how to tell it's healthy, and how to back it
up and upgrade it. [README.md](../README.md) has the quick start; [SECURITY.md](../SECURITY.md) what to keep in mind
before exposing it to anything but your own computer.

## What it needs

- **Java 25** (the jar) or **Docker** (the image, `ghcr.io/crv96/jobwarden`).
- **PostgreSQL 18**. The schema is JobWarden's own: Flyway creates it and upgrades it as the server starts.
- A writable `~/.jobwarden` folder (in Docker, the `/home/jobwarden/.jobwarden` volume): the key that encrypts the
  mailboxes' passwords and GitHub tokens, and what the assistant's runs printed.

## Configuration

Every setting is a Spring property: in `application.properties` next to the jar, as a command-line argument
(`--server.port=9090`) or as an environment variable (`SERVER_PORT=9090`: upper case, `_` for `.` and `-`). The
defaults suit a server on your own computer.

| Setting | Default | What |
|---|---|---|
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | the Docker Compose database, when run from the sources | The PostgreSQL database |
| `SERVER_PORT` | `8080` | The port. In Docker, set it this way only: the image's health check reads it. To publish the server on another port, `-p 9090:8080` is enough |
| `SERVER_ADDRESS` | every address | `127.0.0.1` keeps the server to this computer |
| `TZ` | the machine's (UTC in Docker) | The time zone "today" and every time on the pages are in |
| `JOBWARDEN_SECRET_KEY` | a key file made in `~/.jobwarden` | The key (32 bytes, Base64) the passwords and tokens are encrypted with. A wrong one stops the server from starting |
| `JOBWARDEN_UPDATES_CHECK` | `true` | Whether to ask GitHub for a newer release, at start and every six hours |
| `JOBWARDEN_INBOX_TICK`, `_FIRST_CHECK` | `PT1M`, `PT2M` | How often the mailboxes whose time has come are read, and the first time after the start |
| `JOBWARDEN_INBOX_TIMEOUT_MILLIS` | `20000` | How long the mail server may take to answer |
| `JOBWARDEN_WATCHLIST_EVERY` | `PT6H` | How often each watched company's job board is read |
| `JOBWARDEN_WATCHLIST_TICK`, `_FIRST_CHECK` | `PT10M`, `PT3M` | How often the boards whose time has come are looked for |
| `JOBWARDEN_ASSISTANT_RUN_ENABLED` | `true` | Whether the pages' Run starts the assistant on this computer |
| `JOBWARDEN_ASSISTANT_CLAUDE_COMMAND`, `_CODEX_COMMAND` | found on the `PATH` | Where `claude` and `codex` are, when the log says they weren't found |
| `JOBWARDEN_ASSISTANT_RUN_TIMEOUT` | `60m` | How long a run may take before it's stopped |
| `ADZUNA_APP_ID`, `ADZUNA_APP_KEY` | none | Adzuna's key, for salaries from job ads; one saved in Settings takes its place |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | plain text | `ecs` or `logstash` for JSON logs |
| `LOGGING_LEVEL_COM_JOBWARDEN` | `INFO` | `DEBUG` adds the reads, the rejected forms and the captures |

The modules' own settings are typed and checked as the server starts (`@ConfigurationProperties`): a duration that
isn't one, a repository that isn't `owner/name`, a missing survey address, each stops the start with a message saying
which.

## Is it healthy?

| Address | Answers |
|---|---|
| `/actuator/health` | `{"status":"UP"}`, or `DOWN` with status 503 |
| `/actuator/health/liveness` | Whether the server runs at all: Docker's `HEALTHCHECK` asks it every 30 seconds |
| `/actuator/health/readiness` | Whether it can answer requests: the database too |
| `/actuator/info` | The version, the commit it was built from, the Java it runs on |
| `/actuator/metrics` | The metrics' names; `/actuator/metrics/<name>` one of them |

Metrics worth a look: `http.server.requests` (each page's count and time, by status), `tasks.scheduled.execution` (each
scheduled check's runs, time and outcome: `?tag=outcome:ERROR`), `hikaricp.connections.active`, `jvm.memory.used`.

## Logs

One line per event, on the console:

```
2026-11-14T18:53:02.237+02:00  INFO 6790 --- [jobwarden] [omcat-handler-0] [3f9c2a71d04e8b65] c.j.jobs.service.impl.DefaultApplicationService : Created application 42 for profile 1 as SAVED
```

- **The id in brackets** ties the lines of one request together; the `X-Request-Id` response header gives it, so a
  failure someone reports ("it said that didn't work at 18:53") leads to its lines. Each run of a scheduled check (the
  inboxes, the watchlist, the reminders' cleanup) has an id of its own too. A request that comes with an `X-Request-Id`
  (from a proxy in front, say) keeps it.
- **Levels:** INFO for what changes, WARN for what users did that the server refused (a stale link, a broken rule),
  ERROR only for what shouldn't happen, always with an error code (`[JOBS-001]`) to search for. DEBUG adds the reads.
- **No personal data:** lines name profiles and jobs by id, never by name, title or company.
- **JSON** (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`): the id is the `requestId` field, the code `errorCode`.

## Backups

Two things make up a JobWarden: its **database** and its **key**. Back up both, and keep them apart: whoever has both
can read the mailboxes' passwords and the GitHub tokens.

```bash
pg_dump --format=custom --file=jobwarden-$(date +%F).dump "postgresql://jobwarden@localhost:5432/jobwarden"
```

The key is `JOBWARDEN_SECRET_KEY` when it's set, else `~/.jobwarden/inbox.key` (in Docker, on the `jobwarden-key`
volume). Without it, the server still starts, but every mailbox and the GitHub connection have to be entered again.

To restore: stop the server, `pg_restore --clean --dbname=...` the dump into an empty database, put the key back, and
start the same version as the dump's or a newer one.

## Upgrading

1. Back up the database and the key (above).
2. Start the new version: Flyway upgrades the schema as it starts, and the log says which migrations ran.
3. Reload the browser extension, which should be the same version (the popup says when it isn't).

There's no going back down: an older version refuses a schema a newer one migrated. To undo an upgrade, restore the
backup taken before it, then start the older version.

## When something's wrong

| What | Why, and what to do |
|---|---|
| `Detected resolved migration not applied to database` at start | A migration was added while the server ran from sources, before an earlier one existed. Apply it with Flyway's `outOfOrder`, or restore the backup |
| `jobwarden.secret-key must be 32 bytes in Base64` at start | `JOBWARDEN_SECRET_KEY` is set, but isn't a key: `openssl rand -base64 32` makes one (and the secrets kept with the old one need entering again) |
| A mailbox says it couldn't be read | The inbox page says why; the log has the line, with the profile's id. An app password, not the account's, is what most providers need |
| Run is refused on the AI Assistant page | The card says why: the assistant isn't installed or signed in on this computer, or too many runs are going on. `claude auth status` / `codex login status` tell |
| A page says "That didn't work" | The response had an error: find its `X-Request-Id` in the browser's network tab, then its lines in the log |
