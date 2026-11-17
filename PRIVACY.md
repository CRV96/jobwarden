# Privacy policy

JobWarden is self-hosted: the server runs on your own computer or network, and its data stays in your own database.
This policy covers the JobWarden server and its browser extension for Chrome and Firefox ("the extension").

## What the extension sends, and where

The extension talks to **one place only: the JobWarden server you enter in its settings.** It sends nothing to the
developer, to Mozilla or Google beyond what their stores do on their own, to analytics, to advertisers or to anyone else.
It has no tracking, no telemetry and no remote code.

| What | When | Why |
|---|---|---|
| The posting on the page: its address, title, company, place, salary, description, the company logo's address and the site's icon | When you click **Bookmark** or **I applied** | To save the job to your JobWarden |
| The address of the page you're on | When you open the popup | To show whether that job is saved already |
| The address of every page you open | Only once you turn on **Show on the toolbar icon whether a page is already in JobWarden** (it asks for the browser's permission first) | To put ★ or ✓ on the icon of saved postings |
| Your profile ID | With each of the above | To save it to your own profile |

It reads the reminders due from your server to show them as notifications, once you turn that on. In the browser it
keeps only its settings (the server's address, your profile ID, whether to include the time) and which update notice
you hid; the browser syncs those settings to your other devices if you use its own sync. Firefox users consent to this
data use when installing the extension (Firefox's built-in data consent).

## The server

The JobWarden server keeps what you save, and what you add on its pages, in its own PostgreSQL database. It sends:

- your browsing-related data to **no one**;
- to **GitHub**, once a day, a request for its latest release, which names nothing of you (turn it off with
  `JOBWARDEN_UPDATES_CHECK=false`);
- to the sites you ask for: your mailbox, when you connect one; Adzuna, Eurostat and the Stack Overflow survey for
  market salaries, when you open them; the job boards of companies you watch; a company's logo, when you save a
  posting with one;
- to the AI assistant you connect (Claude Code or Codex, on your own account), the data each request you make needs.
  What that assistant's provider does with it is under their terms.

Passwords for mailboxes and GitHub tokens are stored encrypted. Nothing is shared or sold.

## Your control

Everything is in your own database: export your jobs from **Settings → Your jobs**, delete a profile and all of its
data from **Settings → Your profile**, or remove the extension at any time. The extension's settings are deleted with
it.

## Contact

Questions about this policy: [GitHub Issues](https://github.com/CRV96/jobwarden/issues), or in private as
[SECURITY.md](SECURITY.md) says.
