# Installation

JobWarden is a server you run yourself: on your own computer, or one on your network. The pages open in any browser;
the [browser extension](Browser-extension) saves postings to it; your [AI assistant](AI-assistant) connects to it.

> **JobWarden has no sign-in, by design.** Whoever can reach the server can read and change your job hunt, so keep it to
> your own computer or network. [SECURITY.md](https://github.com/CRV96/jobwarden/blob/main/SECURITY.md#running-jobwarden-safely)
> says what to keep in mind before exposing it to anything else.

## With Docker (recommended)

You need [Docker](https://docs.docker.com/get-docker/) with Compose (Docker Desktop has both).

1. Make a folder for JobWarden, e.g. `~/jobwarden`, and download the latest release's `compose.yaml` into it, from
   [Releases](https://github.com/CRV96/jobwarden/releases/latest). It runs JobWarden's image
   (`ghcr.io/crv96/jobwarden`) with PostgreSQL.
2. In that folder:

   ```bash
   docker compose up -d
   ```

3. Open <http://localhost:9273>.

That's it. The first start takes a minute: Docker downloads the images, and JobWarden creates its database's tables.

**Where your data is:** in two Docker volumes, never in a container: `postgres-data`, the database, and `jobwarden-key`,
the key that encrypts the mailboxes' passwords and GitHub tokens. Updating replaces the container and keeps both: see
[Updating](Updating). Keep the folder: Compose names the volumes after it.

**Worth setting**, in a `.env` file next to `compose.yaml`:

```bash
TZ=Europe/Bucharest           # the time zone "today" and every time on the pages are in (UTC otherwise)
POSTGRES_PASSWORD=...         # the database's password, for a new database; afterwards change it in Settings → Database
```

The port is published on this computer only (`127.0.0.1:9273`). Every other setting is on
[Configuration](Configuration).

## From the sources

You need **JDK 25** or later and **Docker** (for the database). Clone the repository, then:

```bash
./mvnw -pl app -am spring-boot:run
```

The app starts its own PostgreSQL container from `app/compose.yaml`, then opens on <http://localhost:9273>. The first
build downloads Maven's dependencies and its own Node, for the CSS: nothing else needs installing.
[Development](Development) has the rest: tests, the CSS while editing templates, releases.

## Without Docker

Run the jar (from a [release](https://github.com/CRV96/jobwarden/releases), or `./mvnw -pl app -am package`) with Java 25
against a PostgreSQL 18 of your own:

```bash
java -jar jobwarden-app-0.2.0.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/jobwarden \
  --spring.datasource.username=jobwarden --spring.datasource.password=...
```

The schema is JobWarden's own: it creates and upgrades it as it starts. It keeps its key in `~/.jobwarden`.

## Next

1. [Getting started](Getting-started): your profile, your first saved job.
2. [The browser extension](Browser-extension), to save postings in one click.
3. [Your AI assistant](AI-assistant), for the AI features.
