# Updating

A running JobWarden asks GitHub for its releases as it starts and then every day. When there's a newer one, the
sidebar says so (until you hide the notice for that version), and so do **Settings → About** and the browser
extension's popup. Updating keeps all your data; going back to an older version needs the backup you took first.

**What's new** (in the profile menu, and where the sidebar says there's a newer version) lists every release with what
it changed, marks the one you run, and links here. After an update, the first page each profile opens shows what
changed since the version it saw before, once; this browser remembers which one that was. A profile made after the
release came out isn't told of it. Without GitHub (or with `jobwarden.updates.check=false`), the page lists nothing,
and no popup opens.

## Back up first

Two things make up a JobWarden: its **database** and its **key**. Back up both, and keep them apart: whoever has both
can read the mailboxes' passwords and the GitHub tokens. When the database password was changed in Settings, keep
`~/.jobwarden/database.password` with the key: without it, the server starts with the configured password, which
PostgreSQL may not take any more.

```bash
pg_dump --format=custom --file=jobwarden-$(date +%F).dump "postgresql://jobwarden@localhost:5432/jobwarden"
```

The key is `JOBWARDEN_SECRET_KEY` when it's set, else `~/.jobwarden/inbox.key` (in Docker, on the `jobwarden-key`
volume). Without it, the server still starts, but every mailbox and the GitHub connection have to be entered again.

To restore: stop the server, `pg_restore --clean --dbname=...` the dump into an empty database, put the key back, and
start the same version as the dump's or a newer one.

## With Docker

The data lives in two Docker volumes, never in a container: `postgres-data` (the database) and `jobwarden-key` (the key,
and the database password once it's changed in Settings). An upgrade only replaces the JobWarden container.

1. Back up (above).
2. Put the new release's `compose.yaml` in place of yours, in the **same folder**: Compose names the volumes after the
   folder, so another folder would start with empty ones. Or keep yours and set `JOBWARDEN_VERSION=1.1.0`. In that
   folder, this downloads the latest one over yours (`curl.exe` on Windows):

   ```bash
   curl -L -o compose.yaml https://github.com/CRV96/jobwarden/releases/latest/download/compose.yaml
   ```

3. Next to it:

   ```bash
   docker compose pull && docker compose up -d
   ```

The new version upgrades the database's schema as it starts, and its log says which migrations ran. `docker compose
down` keeps the volumes; only `docker compose down --volumes` deletes them.

## From the sources or the jar

1. Back up (above).
2. Get the new version: `git pull` (or `git checkout v1.1.0`) in your clone, or the release's jar.
3. Start it as before (`./mvnw -pl app -am spring-boot:run`, or `java -jar jobwarden-app-1.1.0.jar`). Flyway upgrades
   the schema as it starts, and the log says which migrations ran.

PostgreSQL itself stays as it is: in Docker its image names its major version (18), and moving to the next major
version is a dump with the old one and a restore into the new one (above).

## Going back

There's no going back down: an older version refuses a schema a newer one migrated. To undo an upgrade, restore the
backup taken before it, then start the older version.

## The browser extension

Installed from the [Chrome Web Store](https://chromewebstore.google.com/detail/bjklifdmffkhiffbicofeohgdcpmobek) or
[Firefox Add-ons](https://addons.mozilla.org/firefox/addon/jobwarden/), the browser updates it on its own. One loaded
from the sources: take the extension's zip from the [release](https://github.com/CRV96/jobwarden/releases) with the same
version as the server (or `git pull`), put its folder in place of the old one, and click the extension's reload button
in `chrome://extensions` (in Firefox, `about:debugging`). The popup says when the server runs a newer version than the
extension.
