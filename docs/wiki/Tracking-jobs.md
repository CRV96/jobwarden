# Tracking jobs

Every job you save, from the first bookmark to the offer, with its story on a timeline. Save them with the
[browser extension](Browser-extension), by hand with **Add job** on the Jobs page, from the cards the
[assistant finds](Finding-jobs), or by [importing a spreadsheet](#export-and-import).

The **Jobs** page (`/applications`) lists them, with filters by status, site, tag and text, sorting, and a side panel
that opens a job without leaving the list; ticking several acts on them at once. A job's own page has its facts, its
timeline and the assistant's cards for it.

## The homepage

**Home** (`/`, first in the sidebar, and where choosing a profile leads): a low banner with the logo, whose profile it is and the day; the applications sent today, this week and this month (with the jobs saved, and the week and the month before to compare) and the share answered; the last 30 days day by day, as two charts, the applications sent and the jobs saved, each day's count in its bar's tooltip; what waits (jobs to review, email updates, requests open for the assistant, offers, follow-ups due), each a link to where it's done; the next 7 days' interviews, deadlines and follow-ups; the latest entries on the jobs' timelines; the jobs by status; and the searches saved in the Search Builder, each to run again there or to hand to the assistant with the same filters (a `FIND_JOBS` request). Worked out from the data, without AI (`HomeController`, `ActivityService` in `jobs`).

## Pipeline

**Pipeline** (`/pipeline`): a column per status, newest first. Drag a card to another column to give the job that status, as the status buttons of its side panel do; a click still opens it. Each column shows its first 40 cards and loads the next 40 as it's scrolled to its end (`GET /pipeline/column?status=&page=`), so thousands of jobs load as quickly as ten; the count above it is all of them. The search above narrows every column to the jobs whose title or company has the words.

## Calendar

**Calendar** (`/calendar`): every job's timeline, interviews with their time, format and link, and the deadlines and days to follow up, by month, week or day (`?view=month|week|day&date=2026-10-27`). The month is a grid of days; each shows the entries its cell has room for and links to the day for the rest. The week and the day go hour by hour: interviews as long as they last, the entries without a time above the hours, overlapping ones side by side in a day (in a week, a "+N" badge that opens the day). Their days are never narrower than their text can be read at: the grid scrolls sideways instead, keeping the days and the hours in view, and an entry too short for its text shows all of it when pointed at. Every entry names its job with the company, e.g. "Software Engineer at Microsoft". Next to it, what's coming up in the next 30 days. Both resize with the window. A click opens the job in the side panel. The legend picks what the calendar shows: interviews, other status changes, deadlines, days to follow up (`?show=interview&show=deadline`, kept for the browser session).

## Reminders

**Reminders**: any entry from today on can remind you, at its time, 15 minutes, an hour, a day or a week before; one without a time of day (a deadline, a day to follow up) that morning at 9:00, a day or a week before. Set them with the alarm next to each entry of the calendar's Coming up list, or in a job's side panel and page, which list its interviews, deadline and day to follow up (the `reminders` table, in `jobs`: `ReminderService`). Every JobWarden page asks for the ones due about once a minute (`POST /reminders/due`) and shows each as a card in its corner, or as the system's notification while it's behind other windows and the browser allows them. With **Show my reminders as notifications** in the extension's settings, they come as the system's notifications even with no JobWarden page open. Each comes once, to whichever asks first, and again only if its entry moves. Once its entry is over (a little after a timed one starts, the day after one without a time) or gone, a reminder is deleted: as it's asked for, and every six hours for every profile (`ReminderCleanup`). The texts are in `jobs/src/main/resources/i18n/jobs-reminders*.properties`.

## In your calendar app

**In your calendar app**: **Subscribe** on the calendar page gives the calendar as a feed (iCalendar, `GET /calendar/feed.ics?token=…`): the interviews, deadlines and days to follow up, with your reminders as the app's own alarms, for Apple Calendar, Outlook, Google Calendar and the like, which check for changes about every hour. The address carries a secret of its own (`calendar_feeds`), since calendar apps ask with no session; **New address** stops the old one. Google Calendar fetches it from Google's servers, so JobWarden must be reachable from the internet for it; otherwise download the file once and import it. Pick what the feed has in the Subscribe menu: interviews, other status changes, deadlines, days to follow up, any of them (`?show=interview&show=follow-up`, the calendar's legend's names; interviews, deadlines and follow-ups when it names none). Each choice is an address of its own, named for what it has ("JobWarden · Interviews, Follow-ups"), so a calendar app can subscribe to several, and New address stops them all.

## Contact and follow-up

**Contact & follow-up**: each job keeps who to talk to (name, role, email, phone, profile link), who referred you, when applications close and when to follow up next, saved as you type on its page. An Interviewing step on the timeline can say how the interview takes place (phone, video, on site, take-home), where (an address or the meeting's link) and how long. The assistant sets them too, with `update_follow_up` and `update_job_status`.

## Statuses

**Statuses say something once**: a timeline step can't repeat the status of the step just before it, e.g. Position closed after Position closed (`ApplicationStatus.canRepeat`); Interviewing and Offer can, for each round. When a closed position opens again, **Position re-opened** puts the job back in play (the side panel offers it on a closed job), and it can close again after. The inbox leaves out an email that says what the job's timeline says already, e.g. an application confirmed after it was marked applied, rather than repeat it. The site under a job's facts and in the list opens the posting, and the web addresses in its description, its timeline's notes and Who's hiring posts are links (`Links`, which escapes the rest).

## A job's cards

The cards of a job's page and side panel (fit, AI-proof CV, interview prep, reminders, contact, notes, timeline, description) fold to their header with the arrow on it; each kind stays folded or not, for every job.

## Archive

**Archive**: the jobs you're done with leave the list, the pipeline, the calendar and its feed, the offers, and the jobs the assistant lists, scores and checks, and stay in the statistics and the salaries. Archive one from its page or side panel, or tick several in the list; archive the old ones at once in Settings → Your jobs: those nothing changed on since a day you pick, only the ones that ended (rejected, no response, closed, withdrawn) unless you untick it. The list's **Archived** filter shows them, with their statuses, to bring any back; a posting saved again with the extension comes back on its own. Archiving changes nothing else about a job (`applications.archived_at`).

## Export and import

**Export and import**, in Settings → Your jobs: Export downloads every job as a CSV file (UTF-8, which spreadsheets open with their letters right), one row per job: title, company, location, status, status_date, applied_on, applied_by (YOU, CLAUDE or EXTENSION), salary, employment_type, link, source, rejection_reason, the contact and follow-up fields, notes, description, added_on and last_modified. Import reads one back, or a spreadsheet's, or another tracker's: only `title` is needed, the columns go by their header in any order and under their usual names too (Job title, Company name, URL, Date applied...), commas, semicolons or tabs between the values, and statuses as other trackers name them (Wishlist, Interview, Ghosted...). A job applied to before its status starts its timeline at Applied. Jobs you already have, by link or else by title and company, are skipped, so importing the same file twice adds nothing twice; Settings says how many were added, skipped, and which rows couldn't be read and why. A value that a spreadsheet would run as a formula is exported with an apostrophe in front.

## Statistics

**Statistics** (`/statistics`): how your applications went, counted from the jobs' timelines without AI (`StatisticsService` in `jobs`): how many you applied to, how often they got an answer (an interview, an offer, or a rejection that wasn't for no response) and an interview, the offers, and the median wait from applying to the first answer; from saved to offer; the answers by site and by who applied (you, the assistant, the extension); whether the jobs the assistant scored 4 or more got to an interview more often than the others (the calibration, once there are at least three of each); the requirements the fits found missing or partly met most often, and the keywords the tailored CVs couldn't claim (what to work on); and why you were turned down.

## How it works

**How it works**: every page has a **How it works** button by its title, which opens a short guide to the page, step by step, with a tip (`help/help.html`, its texts `howto.<page>.*` in every language). The Search Builder's guide also answers its questions.
