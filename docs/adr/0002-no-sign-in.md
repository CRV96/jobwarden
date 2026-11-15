# 2. No sign-in: the server trusts whoever reaches it

Accepted, recorded 2026-11-14.

## Context

JobWarden is a personal tool, run on the computer of the person using it, or on their home network. Profiles keep
several people's job hunts apart on one server; they aren't accounts.

## Decision

No authentication. The server protects what a stranger could reach without being on the machine or network: a web page
calling the extension's API (CORS: the extension's origins only), a page calling the MCP server (its `Origin` check), the
calendar feed (an unguessable token), the server fetching addresses from its own network (public addresses only), the
secrets at rest (AES-GCM), and the pages themselves (a Content Security Policy, security headers). Actuator exposes
health, info and metrics only.

## Consequences

- Nothing to set up, no password to forget; the extension and the assistant connect with an address.
- The server must not be published to the internet. SECURITY.md says so, and how to keep it to one computer
  (`SERVER_ADDRESS=127.0.0.1`, `-p 127.0.0.1:8080:8080`).
- A page on a domain that resolves to 127.0.0.1 (DNS rebinding) is same-origin with the server; a `Host` allow-list
  would stop it, at the cost of a setting for servers reached by a LAN name. Open in docs/quality-roadmap.md.
