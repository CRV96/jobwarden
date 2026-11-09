# Security policy

## Supported versions

JobWarden is young (0.x), so fixes go into the latest release only. The app says when there's a new one, in the sidebar, in Settings and in the extension's popup.

| Version                  | Supported          |
| ------------------------ | ------------------ |
| Latest release (0.1.x)   | :white_check_mark: |
| Older releases           | :x:                |
| `main` between releases  | Best effort        |

## Reporting a vulnerability

**Please don't open an issue, a discussion or a pull request for it**: they're public. Report it in private on GitHub instead: **[Report a vulnerability](https://github.com/CRV96/jobwarden/security/advisories/new)** (the repository's **Security** tab → **Advisories** → **Report a vulnerability**).

Tell me:

- what it is and where: the version, and which part (the server's pages, the extension's API, the MCP server, the calendar feed, the browser extension, the Docker image, a workflow);
- how to make it happen, step by step, with a proof of concept if you have one;
- what someone could do with it;
- a fix, if you have one in mind.

What happens next. JobWarden is kept by one person, so these are targets, not promises:

| When                     | What                                                                                       |
| ------------------------ | ------------------------------------------------------------------------------------------ |
| Within 5 days            | I confirm I got the report.                                                                |
| Within 14 days           | I tell you whether I can reproduce it and accept it, or why not.                           |
| Within 30 days           | A release fixes a critical or high one; a lower one comes in a release after.              |
| With the fix             | The advisory is published, with a CVE when one applies, and with credit to you if you like. |

You hear from me in the advisory as it goes. Please keep it to yourself until the fix is released, or for 90 days at most from your report, whichever comes first.

## What's in scope

- The server: its pages, the extension's API (`/api/captures`), the MCP server (`/mcp`), the calendar feed (`/calendar/feed.ics`).
- The browser extension, for Chrome and Firefox.
- What a release publishes: the jar, the extension's zip, the Docker image (`ghcr.io/crv96/jobwarden`).
- This repository's workflows.

Out of scope:

- **That anyone who reaches the server sees its data.** JobWarden has no sign-in, by design: see below. Do report a way past what it does protect: a web page calling the extension's API, the calendar feed without its token, the server reaching its own network through an address it's given, encrypted secrets read without the key.
- A vulnerable dependency with no way to exploit it through JobWarden. Dependabot takes care of those; if you found a way, report it.
- Denial of service by sheer volume, social engineering, and missing headers or settings without a way to exploit them.
- What Claude Code, LinkedIn or other sites do on their own.

## Running JobWarden safely

- **It has no sign-in.** Profiles keep people's job hunts apart; they don't protect them from each other, or from anyone who reaches the server. Run it on your own computer (`http://localhost:8080`) or on a network you trust, and never publish it to the internet. To keep it to your own computer, set `server.address=127.0.0.1` (the `SERVER_ADDRESS` environment variable); with Docker, publish the port as `-p 127.0.0.1:8080:8080`.
- **The calendar feed.** For Google Calendar, which fetches the feed from Google's servers, put only `/calendar/feed.ics` behind a reverse proxy with HTTPS, not the whole server. The token in its address is all that protects it: **New address** on the calendar page stops one that got where it shouldn't.
- **Passwords and tokens.** The mailboxes' passwords and the GitHub token are kept encrypted (AES-GCM), with `JOBWARDEN_SECRET_KEY` or else a key file JobWarden makes in `~/.jobwarden/`, readable only by you. Keep a backup of the key apart from the database's: whoever has both can read them. Give JobWarden an app password for the mailbox, never your account's own, and a fine-grained, read-only GitHub token.
- **The extension** only talks to the server you set in its settings. The server answers its API for the extension alone, never for web pages (CORS).
- **Addresses from pages.** A company logo's address comes from the page it was captured on, so the server downloads it from the public internet only, never from its own computer or network.
- **Claude.** JobWarden calls no AI itself; Claude Code works through its MCP server, on your Claude subscription. The AI Assistant's terminal loop runs Claude unattended, with JobWarden's tools and the web. It can't apply to jobs or read your answers for applying, and it is told to treat what pages say as data, never as instructions. A page can still try to mislead it, so look over what it does on the AI Assistant page.

## How the project is checked

- **CodeQL**, with the security-extended queries, on every pull request, on `main` and weekly: the Java, the JavaScript and the workflows.
- **SonarCloud**: a pull request merges only when its quality gate passes.
- **Dependabot**: alerts and security fixes as soon as a vulnerability is known, version updates weekly. A pull request that adds a dependency with a high-severity vulnerability fails its dependency review.
- **Secret scanning**, with push protection: a push with a token in it is refused.
- **Workflows** with the least permissions each job needs, and their actions pinned to a commit.
- **Releases you can verify.** Each jar, extension zip and Docker image is attested as built by this repository's workflows from its tag:

  ```bash
  gh attestation verify jobwarden-app-0.2.0.jar --repo CRV96/jobwarden
  gh attestation verify oci://ghcr.io/crv96/jobwarden:0.2.0 --repo CRV96/jobwarden
  ```
