# Publishing the extension

For the maintainer, not for people using JobWarden: what the stores ask for, and how to update the extension there.

How to put the browser extension on [Firefox Add-ons](https://addons.mozilla.org/developers/) and the
[Chrome Web Store](https://chrome.google.com/webstore/devconsole), and how to update it there. The extension is ready
for both: Mozilla's linter (`npx web-ext lint --source-dir extension`) finds no errors, the code is plain JavaScript
with no build step (so no source code to submit apart), and nothing is loaded from the internet.

## What to upload

Each release has `jobwarden-extension-<version>.zip`, with `manifest.json` at its root: upload it as it is to both
stores. Its version is the release's, and a store only takes a version higher than the one it has.

The privacy policy is [PRIVACY.md](https://github.com/CRV96/jobwarden/blob/main/PRIVACY.md): Chrome asks for its
address, Firefox for its text.

## Firefox Add-ons

1. Sign in on the [Developer Hub](https://addons.mozilla.org/developers/) and **Submit a New Add-on**: **On this
   site**, the zip.
2. Compatibility: **Firefox** only (desktop). Leave Firefox for Android unticked: the extension is made for a desktop
   browser, and the linter's warning about Android's minimum version is about that.
3. Source code: **No**: nothing is minified or built.
4. The listing: the name, the summary (the manifest's description), the description from
   [Browser extension](https://github.com/CRV96/jobwarden/wiki/Browser-extension), up to two categories (*Bookmarks* fits best), the support site
   (`https://github.com/CRV96/jobwarden/issues`), the MIT license, and screenshots. Tick **This add-on has a privacy
   policy** and paste PRIVACY.md's text in: Firefox keeps the policy itself, not a link.
5. **Notes to reviewer** (paste):

   > The extension needs a JobWarden server, which is self-hosted. To run one: download `compose.yaml` from
   > https://github.com/CRV96/jobwarden/releases/latest and run `docker compose up -d` next to it; open
   > http://localhost:9273, create a profile, and copy its profile ID from Settings → Your profile. In the extension's
   > settings, enter http://localhost:9273 and that ID. Then open any job posting (e.g. on a company's Greenhouse or
   > Lever board) and click the extension's icon: it reads the posting, and Bookmark saves it to the server. The
   > extension talks only to the server entered in its settings.

What it declares: the manifest's `data_collection_permissions` says it transmits **website content** (the posting
saved) and **browsing activity** (the address of the page the popup is opened on, and with the toolbar status every
page's), to the user's own server; Firefox asks for consent as it's installed. That consent needs Firefox 140, the
manifest's `strict_min_version`. The add-on's id, `jobwarden@crv96.github.io`, ties every version to the listing:
**never change it**, or Firefox treats the next version as another add-on.

## Chrome Web Store

1. Register as a developer in the [Developer Dashboard](https://chrome.google.com/webstore/devconsole) (a one-time
   fee), then **New item**: the zip.
2. **Store listing**: the description, the category *Productivity*, the language, and at least one screenshot
   (1280×800 or 640×400, up to five). The store icon is the zip's 128×128 one. A small promo tile (440×280) is
   optional, but items without one show after those with one.
3. **Privacy**, with these texts:

   **Single purpose:** Saves job postings from the page you're on to your own self-hosted JobWarden server, and shows
   whether a page is saved already.

   | Permission | Justification |
   |---|---|
   | `activeTab` | Reads the job posting on the tab you click the icon on, only then. |
   | `scripting` | Runs the script that reads the posting (title, company, place, description) in that tab. |
   | `storage` | Keeps the extension's settings: your server's address, your profile ID, its options. |
   | `favicon` | Takes the site's icon, saved with the posting so JobWarden shows it next to the site. |
   | `alarms` | Asks your server for the reminders due about once a minute, when you turn reminders on. |
   | Host `http://localhost:9273/*` | The JobWarden server on this computer, at its default address. |
   | Optional hosts `http://*/*`, `https://*/*` | Asked only for the server address you enter, when it isn't the default, and for the job boards it reads a job's own page from. |
   | Optional `tabs` | Asked only when you turn on the toolbar status, to see which page each tab shows. |
   | Optional `notifications` | Asked only when you turn on reminders as notifications. |

   **Remote code:** No.

   **Data usage:** *Website content* and *Web history*, sent only to the user's own JobWarden server. Tick the three
   certifications (not sold, not used for anything else, not used for creditworthiness). Privacy policy: the address
   above.
4. **Submit for review.** A review takes from a day to a few weeks the first time.

## Updating

Users of either store get updates on their own, a few hours after a version is approved.

1. Release JobWarden as usual ([Development](https://github.com/CRV96/jobwarden/wiki/Development#versions-and-releases)): the version goes into every `pom.xml`
   and `extension/manifest.json` together, and the tag's release has the zip.
2. Firefox: the add-on's page in the Developer Hub → **Upload New Version**, the zip, and the notes to reviewer again.
   Chrome: the item in the dashboard → **Package** → **Upload new package**, then **Submit for review**.
3. A new permission, or new data sent, needs its justification (Chrome) and the manifest's declaration (Firefox)
   before the upload, and users may be asked to accept it.

Both stores can take uploads from CI with an API key (`web-ext sign --channel listed` for Firefox, the Chrome Web Store
API for Chrome), once the first version is listed by hand.

## The listing's texts

As both stores have them, to keep them the same when they change.

**Summary** (Firefox, 250 characters at most; Chrome takes the manifest's description)

```
Save job postings to your self-hosted JobWarden in one click, see which pages you saved already, watch companies' job boards and get your job hunt's reminders as notifications.
```

**Description** (both stores)

```
JobWarden keeps your job hunt in one place: every job you save, with its story from the first save to the offer. This extension is its companion: it saves the job posting you're looking at to your own JobWarden, in one click.

• Save a posting in one click: it reads the title, company, place, salary and description for you, on most job boards and career sites (LinkedIn, Indeed, Glassdoor, Greenhouse, Lever, Workday and more).
• Mark it Bookmarked or Applied; move it back or remove it.
• See ★ or ✓ on the toolbar icon of the postings you saved (optional).
• Watch a company from its Greenhouse, Lever or Ashby job board: JobWarden reads its new jobs for you.
• Get your interviews', deadlines' and follow-ups' reminders as notifications (optional).
• In 8 languages: English, Deutsch, Español, Français, हिन्दी, Polski, Português, Română.

It needs JobWarden, a free and open-source server you run on your own computer, set up with Docker in a few minutes: https://github.com/CRV96/jobwarden/wiki/Installation

Your data goes only to your own JobWarden server: nothing to the developer, nothing to anyone else.
```

**Notes to Mozilla's reviewers, and Chrome's test instructions** (the release named in step 1 is the one uploaded)

```
The extension needs a JobWarden server, which is self-hosted and free. To run one:

1. Download compose.yaml from https://github.com/CRV96/jobwarden/releases/tag/v1.0.0 and run `docker compose up -d` next to it.
2. Open http://localhost:9273, create a profile (any name), then open Settings → Your profile and copy its Profile ID.
3. In the extension's settings (the gear in its popup), enter http://localhost:9273 and that Profile ID, and click Save.
4. Open a job posting, for example any job on a company's Greenhouse or Lever board (boards.greenhouse.io/..., jobs.lever.co/...), and click the extension's icon: it reads the posting, and Bookmark saves it to the server. Opening JobWarden's Jobs page shows it.

The extension talks only to the server entered in its settings. The code is plain JavaScript with no build step and no remote code. Source: https://github.com/CRV96/jobwarden/tree/v1.0.0/extension
```
