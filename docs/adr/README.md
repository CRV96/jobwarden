# Architecture decision records

Why JobWarden is built the way it is: one decision each, with what led to it and what it costs. A decision that
changes gets a new record that supersedes the old one, which stays, marked so.

| # | Decision | Status |
|---|---|---|
| [0001](0001-modular-monolith.md) | One application, split into modules with checked boundaries | Accepted |
| [0002](0002-no-sign-in.md) | No sign-in: the server trusts whoever reaches it | Accepted |
| [0003](0003-assistant-through-mcp.md) | The AI assistant works through JobWarden's MCP server; the server calls no AI | Accepted |
| [0004](0004-null-safety.md) | Null-safety declared with JSpecify and checked with NullAway | Accepted |
| [0005](0005-time-and-configuration.md) | "Now" from one Clock, settings as typed records | Accepted |

To add one: copy the shape of the others (Context, Decision, Consequences), number it next, and list it here.
