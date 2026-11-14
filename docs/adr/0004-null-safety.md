# 4. Null-safety declared with JSpecify and checked with NullAway

Accepted, recorded 2026-11-14.

## Context

The code marked what may be `null` with JSpecify's `@Nullable` (over 1,200 places), but nothing checked it: a
`Map.get`, an exception's message or a form field the request left out could still reach code that dereferenced it.

## Decision

Every package is `@NullMarked`: a type without `@Nullable` is never `null`. NullAway, on Error Prone, checks it as the
code compiles, in JSpecify mode, in the `nullaway` Maven profile, which CI runs. `ConventionsTests` fails when a package
lacks `@NullMarked`, so a new one is never skipped. Entities are left out (Hibernate fills them), tests too (they pass
`null` on purpose).

## Consequences

- The first run found 110 places, two of them real failures (a missing argument answered with a
  `NullPointerException`); all are fixed.
- A form field that validation requires is still `@Nullable`: binding leaves out what a request didn't send. The forms
  read it through `required...()` methods, after validation.
- A profile, not the default build, because Error Prone needs the JVM flags in `.mvn/jvm.config`, which IntelliJ's own
  compiler doesn't get. IntelliJ checks `@NullMarked` code in the editor.
