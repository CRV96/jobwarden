# 3. The AI assistant works through JobWarden's MCP server; the server calls no AI

Accepted, recorded 2026-11-14.

## Context

Finding jobs, scoring them, tailoring a CV or writing a cover letter need an AI model. Calling one from the server would
need an API key, a bill per token, and prompts kept in step with every model.

## Decision

The server calls no AI. The pages ask for work as requests; the person's own assistant (Claude Code or Codex, on their
own subscription) does them through JobWarden's MCP server (`/mcp`, JSON-RPC over HTTP), which gives it each request's
steps and the data they need, and takes back what it made. The server can start the assistant itself (Run), on its own
computer, as the user it runs as.

## Consequences

- No API keys or AI costs in JobWarden; the assistant's own web tools do the searching.
- What the assistant reads costs tokens, so the tools answer briefly and within budgets (`McpBudgetTests`).
- The assistant acts on pages it reads, which may try to mislead it. Run unattended (the terminal loop, Run), it can't
  reach Auto-apply's requests, which need the person at the browser; messages to people are always drafts the person
  sends.
