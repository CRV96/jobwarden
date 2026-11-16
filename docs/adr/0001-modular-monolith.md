# 1. One application, split into modules with checked boundaries

Accepted, recorded 2026-11-14.

## Context

JobWarden is run by one person on one computer, with one database. It has many areas (jobs, the CV, the inbox, the
watchlist, salaries...) that change at different speeds, and three ways in: the pages, the browser extension's API and
the MCP server.

## Decision

One Spring Boot application, built from Maven modules: domain modules hold the business logic and know nothing of the
web; adapter modules (`web`, `capture`, `mcp`) expose them; `app` wires them together. Spring Modulith checks the
boundaries on every build (`ModularityTests`): a module's API is its root package and the packages marked
`@NamedInterface` (`dto`, `enums`, `service`, `exception`), everything else is private to it, and the dependencies
between modules form no cycle. Each module owns its tables and its Flyway migrations.

## Consequences

- One process to run, one database to back up, no network between modules.
- A module can be tested alone (`@ApplicationModuleTest`), which also proves it doesn't lean on others' beans.
- Splitting a module out into a service later stays possible: its API is already the only way in.
- Modules can't share a bean through `common`, which has none: each module makes its own where two need the same thing
  (the `Secrets` subclasses).
