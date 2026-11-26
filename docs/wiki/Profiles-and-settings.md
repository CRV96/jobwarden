# Profiles and settings

## Profiles

Several people, or several job hunts, can share one JobWarden: each profile has its own jobs, CV, searches, inbox and assistant's requests, and there's no sign-in (`/profiles` picks one; **Log out**, in the profile menu, leaves it in this browser and goes back there). A profile can have a **password** (Settings → Your profile → Password; changing it needs the current one, and so does **Remove password**, under it, in a form of its own): choosing the profile, on the Profiles page or from the menu, then asks for it first, and five wrong ones in a row make it wait a minute. Only its hash is kept (PBKDF2). It keeps others using the same browser out of the profile; it isn't a sign-in to the server, and the browser extension and the assistants, which name the profile by its ID on this computer, don't need it. **Settings → Your profile** also renames it (names are unique, ignoring case), changes its photo, shows its profile ID to copy, and deletes it: with its name typed to confirm, in a dialog that says what goes and offers to export its jobs first. Deleting a profile deletes everything of it for good, in every module (every table of a profile's cascades from `users`), stops the assistant's runs on its requests and deletes what they printed; the other profiles, and what the server keeps for all of them (the market's salaries, the Adzuna key), stay. `ProfileDeletionTests` fills two profiles across the modules, deletes one, and checks no table keeps a row of it, and none of the other's goes.

## Settings

**Settings** is in the profile menu, at the foot of the sidebar. Some of it is the browser's, some the profile's, some
the server's, for every profile:

| Section | What | Whose |
|---|---|---|
| **Your profile** | Its name, photo and profile ID (which the extension and the assistants ask for); its password; deleting it | The profile's |
| **Your jobs** | Export every job as a CSV file, import one ([how](Tracking-jobs#export-and-import)), archive the old ones at once | The profile's |
| **Salaries** | The [Adzuna](https://developer.adzuna.com) key, for the salaries in current job ads ([Offers and salaries](Offers-and-salaries)) | The server's |
| **Database** | The password JobWarden connects to PostgreSQL with: shows when it's still the default, and changes it ([how](Configuration#the-database-password)) | The server's |
| **Browser extension** | **Add to Chrome** and **Add to Firefox**, from their stores, and what to enter in the extension's settings: the server's address and your profile ID | The browser's |
| **About** | The version running and whether a newer one is out, the license, and **Suggest a feature** | The server's |
| **Appearance and language** | Dark or light, and the pages' language | The browser's |
| **Inbox** | How often your mailbox is read, and what becomes of the updates found ([Inbox](Inbox)) | The profile's |
| **AI Assistant** | How to connect Claude Code and Codex, whether each is connected, and your main assistant ([AI assistant](AI-assistant)) | The profile's |

## Languages

The pages are in English, Romanian, French, German, Polish, Spanish, Portuguese and Hindi (`Language` in `web`). In Hindi, the CV's own headings (`cv.doc.*`) stay in English: they go into its PDF, whose font (Noto Sans) has no Devanagari, and CVs in India are mostly written in English.

- **Which language:** the one picked in the header (remembered in a cookie for a year), otherwise the browser's preferred language if the app has it, otherwise English. The capture API answers in the language of the request's `Accept-Language` header, which the extension sends like any browser request.

## Suggest a feature

**Suggest a feature**, in the profile menu, in **Settings → About** and at the foot of every page's **How it works**,
opens a new discussion in the [Ideas](https://github.com/CRV96/jobwarden/discussions/categories/ideas) category on
GitHub, with the feature request's two questions and your version already in it. Settings also links to the ideas
others suggested, to vote for. Posting or voting takes a GitHub account; JobWarden sends nothing itself.
