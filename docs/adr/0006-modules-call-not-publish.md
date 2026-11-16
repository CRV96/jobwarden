# 6. Modules call each other's services; they don't publish events

Accepted, recorded 2026-11-15.

## Context

Spring Modulith offers events between modules (`@ApplicationModuleListener`), which decouple a module that says
something happened from the ones that react to it. The question is whether JobWarden's modules should talk that way.

## Decision

No events, for now. Every call one domain module makes to another is either a query (`findById`, `findByLink`,
`findKnownLinks`...) or a command whose result the caller needs at once: the inbox adding a job's event from an email,
Auto-apply recording that it applied, the watchlist adding the jobs it found. None only tells another module that
something happened. And what follows from a deletion, the other modules' rows of a job or a profile, the database
does itself: all 42 references to `applications` and `users` cascade (or set null) in the same transaction.

## Consequences

- One transaction keeps the modules consistent; there's no event publication log to keep, nor retries.
- A module that one day must react to something it isn't asked to do (say, a job's statistics kept up to date as its
  timeline changes) is when to publish an event, with Modulith's JDBC event publication registry so none is lost.
