# Configuration

What a JobWarden server needs, and every setting it has. The defaults suit a server on your own computer: most people
change nothing. Before letting anything but your own computer reach it, read
[Running JobWarden safely](https://github.com/CRV96/jobwarden/blob/main/SECURITY.md#running-jobwarden-safely): JobWarden has no sign-in.

## What it needs

- **Java 25** (the jar) or **Docker** (the image, `ghcr.io/crv96/jobwarden`).
- **PostgreSQL 18**. The schema is JobWarden's own: Flyway creates it and upgrades it as the server starts.
- A writable `~/.jobwarden` folder (in Docker, the `/home/jobwarden/.jobwarden` volume): the key that encrypts the
  mailboxes' passwords and GitHub tokens, the database password changed in Settings, and what the assistant's runs
  printed.

## Settings

Every setting is a Spring property: in `application.properties` next to the jar, as a command-line argument
(`--server.port=9090`) or as an environment variable (`SERVER_PORT=9090`: upper case, `_` for `.` and `-`). The
defaults suit a server on your own computer.

| Setting | Default | What |
|---|---|---|
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | the Docker Compose database, when run from the sources | The PostgreSQL database |
| `SERVER_PORT` | `9273` | The port: 9273, "WARD" on a phone's keypad, which nothing else asks for by default and no system hands out for its own connections. In Docker, set it this way only: the image's health check reads it. To publish the server on another port, `-p 9090:9273` is enough |
| `SERVER_ADDRESS` | every address | `127.0.0.1` keeps the server to this computer |
| `JOBWARDEN_ALLOWED_HOSTS` | none | Host names it answers to besides its local ones (IP addresses, `localhost`, LAN names), comma-separated: a name a reverse proxy serves it as. Any other is refused with 403 |
| `TZ` | the machine's (UTC in Docker) | The time zone "today" and every time on the pages are in |
| `JOBWARDEN_SECRET_KEY` | a key file made in `~/.jobwarden` | The key (32 bytes, Base64) the passwords and tokens are encrypted with. A wrong one stops the server from starting |
| `JOBWARDEN_DATABASE_PASSWORD_FILE` | `~/.jobwarden/database.password` | Where the database password changed in Settings is kept, encrypted with the key above. At start it takes the place of `SPRING_DATASOURCE_PASSWORD` |
| `JOBWARDEN_UPDATES_CHECK` | `true` | Whether to ask GitHub for a newer release: at start, then daily |
| `JOBWARDEN_UPDATES_EVERY`, `_TICK` | `P1D`, `PT1H` | How often GitHub is asked, and how often the server looks whether that's due (an hour after a failed check, it asks again) |
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

## The database password

Settings → Database changes the password JobWarden connects to PostgreSQL with, given the current one. In this order,
so a failure never leaves PostgreSQL with a password the next start doesn't know:

1. `~/.jobwarden/database.password` (`JOBWARDEN_DATABASE_PASSWORD_FILE`) keeps the new one, encrypted with the secret
   key, readable by its owner alone.
2. PostgreSQL gets it: `ALTER ROLE ... PASSWORD` with its SCRAM hash, made by the driver, so neither the server's log
   nor `pg_stat_statements` ever sees the password. When PostgreSQL refuses it, the file is put back as it was.
3. The connection pool logs in with it from then on; the connections already open are replaced.

At each start the kept password takes the place of the configured one (`SPRING_DATASOURCE_PASSWORD`, or the Docker
Compose database's), once PostgreSQL has accepted it. When PostgreSQL refuses it (a backup from before the change
restored, a password reset by hand), or the file can't be decrypted (another key), the server starts with the
configured one and logs a warning. To go back to the configured password for good, delete the file, and set
`SPRING_DATASOURCE_PASSWORD` to what PostgreSQL has.

With [deploy/compose.yaml](https://github.com/CRV96/jobwarden/blob/main/deploy/compose.yaml), the file is on the `jobwarden-key` volume, so upgrades keep it.
`POSTGRES_PASSWORD` only sets the password of a database that doesn't exist yet; afterwards the one changed in
Settings is the one that counts. Tools that connect themselves, like `pg_dump`, need the new password.
