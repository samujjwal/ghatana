# io/activej — Patch Manifest

This document is the authoritative record of all deviations between the vendored
ActiveJ source files in this directory and the upstream ActiveJ releases they
were derived from.

**Baseline upstream version:** `activej-6.0-rc2`
**Java compatibility:** Java 21

---

## Compliance Status

> **⚠️ ACTION REQUIRED — Inline annotations missing**
>
> Per the [vendoring policy](../README.md) and `package-info.java`, every
> deviation from upstream MUST be annotated with `// GHATANA-PATCH: <reason>`
> in the source file. **None** of the four vendored files currently contain these
> annotations. Until inline markers are added, this manifest is the only
> traceability record — do not delete it.

---

## Vendored Files

### 1. `promise/AbstractPromise.java`

| Attribute | Value |
|-----------|-------|
| Upstream class | `io.activej.promise.AbstractPromise` |
| Lines | 1383 |
| Purpose | Ghatana-specific promise instrumentation hooks |

**Documented intent (from io/README.md):** Adds instrumentation hooks for
observability — e.g. span correlation and metrics recording on promise
completion.

**Current state:** No `GHATANA-PATCH:` markers present. The file compiles clean
against `activej-6.0-rc2`. Until the inline diff is identified and marked, treat
this file as an **unverified vendor copy** — do not modify without diffing
against upstream first.

**Action required:** Diff this file against the upstream `6.0-rc2`
`AbstractPromise.java`, identify all deviations, and add
`// GHATANA-PATCH: <reason>` at each change site.

---

### 2. `promise/SettablePromise.java`

| Attribute | Value |
|-----------|-------|
| Upstream class | `io.activej.promise.SettablePromise` |
| Lines | 112 |
| Purpose | Companion patch to `AbstractPromise` |

**Documented intent:** Companion patch required to support the `AbstractPromise`
instrumentation changes without breaking the internal `set()`/`setException()`
contract.

**Current state:** No `GHATANA-PATCH:` markers present. Treat as unverified
vendor copy.

**Action required:** Same diff procedure as `AbstractPromise.java`.

---

### 3. `common/time/Stopwatch.java`

| Attribute | Value |
|-----------|-------|
| Upstream class | `io.activej.common.time.Stopwatch` |
| Lines | 115 |
| Purpose | High-resolution timing for event-loop metrics |

**Documented intent:** Exposes additional timing resolution or hooks needed by
`EbpfEventloopStallTracer` and `QueueMetrics`.

**Current state:** No `GHATANA-PATCH:` markers present. Treat as unverified
vendor copy.

**Action required:** Diff against upstream and annotate.

---

### 4. `eventloop/inspector/EventloopInspector.java`

| Attribute | Value |
|-----------|-------|
| Upstream class | `io.activej.eventloop.inspector.EventloopInspector` |
| Lines | 49 |
| Purpose | Additional metrics hooks on the event-loop inspector interface |

**Documented intent:** Adds platform-specific callback methods to the inspector
interface so `platform/java/observability` can attach metrics probes to
event-loop lifecycle events.

**Current state:** No `GHATANA-PATCH:` markers present. Treat as unverified
vendor copy.

**Action required:** Diff against upstream and annotate.

---

## Upgrade Checklist

When bumping `activej` in `gradle/libs.versions.toml`:

1. For each file in this directory, download the new upstream source.
2. Re-apply the intent documented above (or verify the upstream now includes
   the same behaviour, in which case delete the vendored file).
3. Add/update `// GHATANA-PATCH: <reason>` at each change site.
4. Update the **Baseline upstream version** header in this file.
5. Run `./gradlew :io:build` and `./gradlew test` to confirm clean build.

---

## How to diff against upstream

```bash
# Download upstream source for a specific class
curl -L "https://raw.githubusercontent.com/activej/activej/v6.0-rc2/core-promise/src/main/java/io/activej/promise/AbstractPromise.java" \
  -o /tmp/AbstractPromise.upstream.java

# Diff
diff /tmp/AbstractPromise.upstream.java io/activej/promise/AbstractPromise.java
```

Repeat for each vendored file, adjusting the upstream path as needed.

---

## License

All files in this directory are derivative works of ActiveJ source and are
distributed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0),
consistent with the upstream license. Original copyright notices are preserved
in each file header.
