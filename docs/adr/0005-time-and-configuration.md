# 5. "Now" from one Clock, settings as typed records

Accepted, recorded 2026-11-14.

## Context

About seventy places asked the system for the time (`Instant.now()`, `LocalDate.now()`, `ZoneId.systemDefault()`), so
nothing that depends on the day (reminders due, follow-ups, caches, statistics) could be tested at a moment of the
test's choosing. And the modules read their settings one `@Value` at a time, each with its default in a string, checked
only when first used.

## Decision

- **Time:** one `Clock` bean (`JobWardenApplication`), injected wherever "now" or "today" matters; a test passes a fixed
  one. Entities stamp their own audit times (`updatedAt`...), like Hibernate's `@UpdateTimestamp`, with the system's
  clock. `ConventionsTests` refuses the no-argument `now()` calls everywhere else.
- **Settings:** each domain module binds its settings to one `@ConfigurationProperties` record (`UpdatesProperties`,
  `MarketProperties`...), validated as the app starts, with its defaults and docs in one place, registered by the module
  itself so its `@ApplicationModuleTest` still starts it alone. The adapters keep `@Value` for the route paths their
  `@RequestMapping`s share, on final fields that Lombok copies onto the constructor.

## Consequences

- A wrong setting stops the start with a message naming it, rather than failing later.
- IDEs complete the settings (the configuration processor's metadata).
- A class that needs "now" says so in its constructor.
