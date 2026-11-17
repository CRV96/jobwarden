# AI assistant

JobWarden calls no AI itself and needs no API key: the AI features are requests the pages make, which your own
assistant, Claude Code or Codex, does through JobWarden's MCP server, on your own plan. Connect it once, in
**Settings → AI Assistant**, which shows the command with your profile's ID.

## Connecting it

Every AI feature is a request a page makes, which your own AI assistant then does through JobWarden's MCP server, at `http://localhost:9273/mcp`: `get_request` hands it the steps and the data they need, the other tools read and write your jobs, and `finish_request` ends it with a summary the page shows. JobWarden calls no AI itself and needs no API key: the assistant runs on your own plan. Connect one or both: **Settings → AI Assistant** shows how to connect each. Every request waiting on a page has **Open in Claude** and **Open in Codex**, which open the desktop app with the request typed in a new session (`claude://code/new?q=…`, `codex://threads/new?prompt=…`): press send and it starts. Neither app runs what a link brings without you, by design. Folded underneath are the prompt to paste and the terminal command. The profile's **main assistant**, picked in Settings, is the one **Run it here**, the terminal commands and the loop use. The MCP server names the assistant that calls it in the log (`claude-code/…` or `codex-mcp-client/…`, its User-Agent).

| | Claude Code | Codex CLI | The ChatGPT app |
|---|---|---|---|
| Finding jobs, checking postings, scores, tailored CVs, cover letters, messages, interview and negotiation briefs, stories, projects, sorting the inbox | ✓ | ✓ | ✗ |
| Auto-apply (drives your own Chrome) | ✓ with Claude in Chrome | ✗ | ✗ |
| The terminal loop that does every request on its own | ✓ `claude -p` | ✓ `codex exec` | ✗ |

**Claude Code** ([Anthropic](https://www.anthropic.com/claude-code), on a Claude plan):

```bash
claude mcp add --scope user --transport http jobwarden http://localhost:9273/mcp --header "X-JobWarden-Profile: 5b1f3c2e-8a4d-4c6e-9f10-2d7b8e3a6c41"
```

`--scope user` makes it Claude Code's in every folder, so the terminal loop works wherever it runs. The header is optional: it names the profile the server uses when a tool isn't told one (the prompts always name it), by the profile ID Settings shows (a number from before still works); Settings writes the command with yours. A page's command is `claude "…"`; Auto-apply's is `claude --chrome "…"`, which needs Claude in Chrome. The loop runs `claude -p` with JobWarden's tools, `WebSearch` and `WebFetch` allowed, and Auto-apply's four tools denied.

**Codex CLI** ([OpenAI](https://github.com/openai/codex), on a ChatGPT plan): add JobWarden to `~/.codex/config.toml`:

```toml
[mcp_servers.jobwarden]
url = "http://localhost:9273/mcp"
http_headers = { "X-JobWarden-Profile" = "5b1f3c2e-8a4d-4c6e-9f10-2d7b8e3a6c41" }
# Optional: run JobWarden's tools without asking. Codex asks before a tool that isn't read-only and reaches the web
# (check_postings, watch_company), and in codex exec refuses it.
# default_tools_approval_mode = "approve"
```

A page's command is `codex --search "…"`, with live web search: a request reads today's postings, not a cache of them. Codex reads pages through its web search; the steps say "read a page with a short question", which it does as well as Claude Code's `WebFetch`. The loop runs `codex exec --skip-git-repo-check -c 'web_search="live"'`, with `default_tools_approval_mode="approve"` for JobWarden's tools (unattended, `codex exec` would refuse those it asks about) and Auto-apply's four tools in `disabled_tools`, so it never has them. Codex cuts every tool's result to 10,000 tokens; JobWarden keeps its results well under that (`McpBudgetTests`).

**Run it here**: when JobWarden runs on your own computer (not in Docker) and finds `claude` or `codex` there, each prompt also has **Run it here**: JobWarden starts the assistant itself, in the background, on that request alone, as the loop would (`claude -p` or `codex exec`, with the same tools allowed and Auto-apply's denied), and gives it its own MCP server on the command line (`--mcp-config` with `--strict-mcp-config`, or `-c mcp_servers.jobwarden.url=…`), so nothing needs adding first. The card follows the request as usual. It runs as the user JobWarden runs as, on that user's plan, so the command must be signed in there: JobWarden asks it first (`claude auth status`, `codex login status`), and when it isn't, starts nothing, leaves the request waiting, and the page says what to run once in a terminal (`claude auth login`, `codex login`). Claude Desktop's sign-in isn't the `claude` command's; the Codex in the ChatGPT app is signed in with the app. JobWarden looks for the command on its PATH and in the usual places (`/opt/homebrew/bin`, `/usr/local/bin`, `~/.local/bin`, `~/.claude/local`, `~/.npm-global/bin`..., and on a Mac the Codex that comes inside the ChatGPT app, `/Applications/ChatGPT.app/Contents/Resources/codex-cli/bin`), or where `jobwarden.assistant.claude-command` and `jobwarden.assistant.codex-command` say; its log says which it found. Three runs at most go on at once, and one that takes over an hour (`jobwarden.assistant.run.timeout`) is stopped. A run that stops before ending its request fails it with the last line it printed, e.g. "API Error: 529 Overloaded", and what it printed stays in `~/.jobwarden/runs`. Only JobWarden's own pages can ask for a run: it takes HTMX's header and the page's own origin, which a page on another site can't send. `jobwarden.assistant.run.enabled=false` turns it off. Auto-apply has no Run: you're needed at the browser (`AssistantRunner` and `AssistantRunController`, in `web`).

**Auto-apply is Claude Code's alone.** It fills forms in your own Chrome, with your sign-ins, which takes a browser the assistant drives in that Chrome: Claude Code has it, with Claude in Chrome; Codex doesn't. A profile that works with Codex still gets Auto-apply's prompt for Claude Code, and the page says why.

**The ChatGPT app (web and desktop) can't connect.** Its connectors reach only servers on the internet, over HTTPS, from OpenAI's side; JobWarden's MCP server answers only browser pages and clients on its own computer, and has no sign-in, because whoever can reach it can read and change your job hunt. Putting it on the internet through a tunnel would hand that to anyone with the address, so JobWarden doesn't offer it.

**Connected or not**: JobWarden reads each assistant's own configuration on this computer (Claude Code's `~/.claude.json`, for every folder or for one; Codex's `~/.codex/config.toml`, which the Codex app reads too) for a server called `jobwarden` or at this computer's `/mcp` (`AssistantConnections`). Settings tags each assistant **Connected**, **Connected to another profile** (its header names another profile: the pages' requests still work, as they name theirs, and the card says how to switch it), or **Not connected yet**. An **Open in** button whose assistant isn't connected first says so, in a popover, with a link to Settings, or opens anyway; Claude Code set up for one folder alone opens its session in that folder (`&folder=`). Where the configuration can't be read (JobWarden in Docker), the pages say nothing either way.

Other assistants that speak MCP over HTTP can connect the same way and paste the same prompts; the pages write commands for Claude Code and Codex only.

## Sparing tokens

**Sparing tokens**: the work that needs no AI, JobWarden does itself. Tidying up the applications with no answer runs at once when you ask, with no assistant (`ApplicationService.tidyUnanswered`; the assistant has it too, as `tidy_applications`). For the rest, each request comes with what its steps need, so the assistant asks for nothing call by call: the job it's about, the CV (brief, or with every job's points for what's written from it, and the file's text only where the fields don't say it), the story bank, the saved searches with their queries, the jobs to score, what you kept and discarded of the jobs found, the recruiters you follow, your jobs' companies. Adding jobs, posts or recruiters leaves out what you have, so nothing is listed first; `filter_links` tells which search results are new before any is opened; `check_postings` checks many addresses at once, on the server; a posting's description is cut at 12,000 characters, a README at 4,000 without its images, HTML and code; times go to the minute; and each tool's result goes once, as compact JSON without the empty fields. The tools describe themselves in a few words, with no description where a name says it, and the steps ask the assistant to read pages with a question rather than whole, and to judge search results from their snippets. Measured with `McpBudgetTests`, on the same profile: the tool list went from 51,487 bytes to 31,791 (from 43,585 characters the model reads to 25,166), and the 18 kinds of request from 172,701 characters read in 41 calls before their work began to 126,876 in 21. `McpBudgetTests` now also counts the first reads of Auto-apply, the inbox and the negotiation (`get_application_answers`, `list_apply_queue`, `list_email_updates`, `get_market_salaries`): 129,575 characters in 24 calls, which it keeps under 25,000 per request and 25 calls in all.
