# Changelog

What changes from one release to the next, for whoever runs JobWarden: new features, changed behaviour, fixes, and
what to do when upgrading. The release notes on GitHub list the pull requests as well.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Add to *Unreleased* with each change;
a release renames it to its version and date.

## Unreleased

### Added

- Health checks at `/actuator/health`, with `/liveness` and `/readiness`; the version, commit and Java at
  `/actuator/info`; metrics at `/actuator/metrics`, scheduled checks included (`tasks.scheduled.execution`). Nothing
  else of Actuator is exposed.
- A request id in every log line and in the `X-Request-Id` response header, and one for each run of a scheduled check.
  JSON logs with `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`.
- The Docker image checks its own health (`HEALTHCHECK`), sizes its heap to the container's memory, and stops on running
  out of it. In Docker, change the port with `SERVER_PORT`, which the health check reads.
- The jar lists every library it carries (CycloneDX SBOM), for vulnerability scanners.
- [docs/operations.md](docs/operations.md): settings, health, logs, backups, upgrading, troubleshooting.
- A "Skip to the page" link, the first stop of the Tab key on every page, past the sidebar's links.
- A password for a profile, in Settings → Your profile: choosing the profile asks for it, on the Profiles page or from
  the menu. Not a sign-in: the browser extension and the assistants don't need it.
- Log out, in the profile menu: leaves the profile in this browser and goes back to the Profiles page.
- The browser extension's popup can hide the notice of a newer JobWarden, until the next version.
- Each release has a `compose.yaml` that runs its Docker image with PostgreSQL, and notes on upgrading with the data
  kept: it lives in Docker volumes, so `docker compose pull && docker compose up -d` only replaces the server.
- The database's password, in Settings → Database: changed with the current one, it's given to PostgreSQL (its SCRAM
  hash only), JobWarden connects with it at once, and `~/.jobwarden/database.password` keeps it, encrypted with the
  secret key, for the next start, where it takes the place of the configured one (`SPRING_DATASOURCE_PASSWORD`, or the
  Compose files' `jobwarden`). Settings says when it's still that default. When the database refuses the kept one, say
  after restoring an older backup, the server starts with the configured one, and its log says so.

### Changed

- Every page runs under a Content Security Policy, with the browser's other defences on: no inline scripts, no framing
  by other sites, no referrer sent to the job boards the pages link to.
- A setting that's wrong (`JOBWARDEN_SECRET_KEY` that isn't a key, a duration that isn't one, an update repository that
  isn't `owner/name`) stops the server as it starts, with a message naming it, instead of failing later.
- Stopping the server lets the requests under way finish first, for up to 20 seconds.
- The server asks GitHub for a newer release every day on its own (`JOBWARDEN_UPDATES_EVERY`), not only when a page
  was opened six hours after the last check.
- Who's hiring: the roles and places to look for are limited to 500 characters each.

### Fixed

- Ashby boards: a hybrid job was saved as remote, and the countries of a job's other locations were left out.
- Watched boards: a posting dated without a time zone had no date at all, so it passed a search for recent postings
  however old it was.
- Lever boards: salaries with cents lost them (23.75–28.50 came out as 23–28).
- The MCP server answered a tool call without its required status or result with an internal error rather than saying
  what was missing.
- The home page: in Safari and Firefox, "The last 30 days" showed the charts' hidden text over their labels.
- After an upgrade, the pages could keep running the previous version's scripts and styles from the browser's cache.
  They're now checked with the server on every load (an unchanged one costs a "not modified").
- The jobs list: ticking a row's box opened it in the side panel (since the Content Security Policy above, before any
  release).

### Security

- The server answers only to the host names it knows as its own, which stops a web page from reaching it through a
  domain pointed at your computer (DNS rebinding). Reaching it by another name, e.g. through a reverse proxy, needs
  that name in `JOBWARDEN_ALLOWED_HOSTS`; IP addresses, `localhost` and LAN names work as before.
- A Lever board's salary written with a huge exponent (`1E+1000000000`) could tie the server up writing out a number of
  a billion digits; such amounts are now left out.
