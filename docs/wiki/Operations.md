# Operations

Telling whether a JobWarden server is healthy, reading its logs, and what to do when something's wrong. Backups and
upgrades are on [Updating](Updating); every setting on [Configuration](Configuration).

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

Stopping the server (Ctrl+C, `docker stop`) lets the requests under way finish first, for up to 20 seconds. The jar
carries its software bill of materials (CycloneDX) at `META-INF/sbom/application.cdx.json`, for vulnerability scanners.

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

## When something's wrong

| What | Why, and what to do |
|---|---|
| `Detected resolved migration not applied to database` at start | A migration was added while the server ran from sources, before an earlier one existed. Apply it with Flyway's `outOfOrder`, or restore the backup |
| `The database refused the password changed in Settings` in the log at start | PostgreSQL doesn't take the password kept in `~/.jobwarden/database.password` (an older backup restored, say), so the server connects with the configured one. Change it again in Settings, or delete the file |
| `password authentication failed` at start | Neither the password changed in Settings nor the configured one is PostgreSQL's: set `SPRING_DATASOURCE_PASSWORD` to the one it has, or reset it where PostgreSQL runs: in Docker, `docker compose exec postgres psql -U jobwarden -c "ALTER ROLE jobwarden PASSWORD '...'"`, which the container lets in without one |
| `jobwarden.secret-key must be 32 bytes in Base64` at start | `JOBWARDEN_SECRET_KEY` is set, but isn't a key: `openssl rand -base64 32` makes one (and the secrets kept with the old one need entering again) |
| A mailbox says it couldn't be read | The inbox page says why; the log has the line, with the profile's id. An app password, not the account's, is what most providers need |
| Run is refused on the AI Assistant page | The card says why: the assistant isn't installed or signed in on this computer, or too many runs are going on. `claude auth status` / `codex login status` tell |
| A page says "That didn't work" | The response had an error: find its `X-Request-Id` in the browser's network tab, then its lines in the log |

Still stuck? Ask on [Discord](https://discord.gg/A4U2eVNck), or open an
[issue](https://github.com/CRV96/jobwarden/issues/new/choose) with the version, what you did and the log's lines.
