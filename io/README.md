# io/activej — Vendored ActiveJ Patches

## Purpose

This directory contains **vendored patches** for selected ActiveJ framework classes.
These are minimal, targeted modifications to upstream ActiveJ source that cannot be
applied via standard extension points (subclassing, delegation, configuration).

## Why Vendored?

The ActiveJ framework (licensed Apache 2.0) is used throughout the Ghatana platform for
its high-performance event-loop and async-promise model. Occasionally, a bug fix or
performance improvement is needed before it is available in a released ActiveJ version,
or a Ghatana-specific behavioural tweak is required at the framework level.

Rather than forking the entire ActiveJ repository, only the affected source files are
vendored here with the minimal diff applied.

## Contents

| Path | Upstream class | Reason for patch |
|------|---------------|------------------|
| `promise/AbstractPromise.java` | `io.activej.promise.AbstractPromise` | Ghatana-specific promise instrumentation |
| `promise/SettablePromise.java` | `io.activej.promise.SettablePromise` | Companion patch for AbstractPromise |
| `common/time/Stopwatch.java` | `io.activej.common.time.Stopwatch` | High-resolution timing for event-loop metrics |
| `eventloop/inspector/EventloopInspector.java` | `io.activej.eventloop.inspector.EventloopInspector` | Additional metrics hooks |

## Maintenance Rules

1. **Minimise diff** — keep each file as close to upstream as possible.
2. **Mark all changes** — every deviation from upstream MUST be annotated with
   `// GHATANA-PATCH: <reason>` inline comments.
3. **Track upstream version** — record the upstream ActiveJ version the patch was
   applied against in the file-level Javadoc (`@since activej-X.Y.Z`).
4. **Rebase on upgrades** — when the ActiveJ version in `gradle/libs.versions.toml`
   is bumped, verify each vendored file against the new upstream source and either
   incorporate the upstream change or confirm the patch still applies cleanly.
5. **Prefer removal** — if a patched behaviour becomes available upstream, delete the
   vendored file and switch to the upstream class.

## Build Integration

These files are compiled as part of the `io` Gradle sub-project and placed on the
classpath *before* the upstream ActiveJ jars so the JVM class-loader picks the patched
version. See [io/activej build configuration] for details.

## License

These files are derivative works of ActiveJ source code and are distributed under the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0), consistent with the
upstream license. Original copyright notices are preserved in each file.
