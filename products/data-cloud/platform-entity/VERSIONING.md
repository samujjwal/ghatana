# platform-entity — Schema Versioning Policy

This document defines the allowed change operations on entity types in
`products/data-cloud/platform-entity`, the rules for breaking changes, and the
deprecation lifecycle. All contributors and reviewers must follow these rules
before merging any change to this module.

---

## Guiding Principle

`platform-entity` types flow across persistence, API, event bus, and search
index boundaries. A change that is safe in one layer may cause data loss or
deserialization failures in another. **When in doubt, treat a change as
breaking and follow the deprecation path.**

---

## 1. Always-Safe Changes (No Version Bump Required)

You may make the following changes without a version bump and without a
migration plan:

| Change | Allowed? | Notes |
|---|---|---|
| Add a new optional field with a non-null default | ✅ Yes | Existing records read back with the default |
| Add a new interface implementation that does not add abstract methods | ✅ Yes | Additive only |
| Add a new concrete record type (subtype of an existing sealed type hierarchy) | ✅ Yes | Consumers that `switch` on the sealed hierarchy must be updated |
| Fix a typo in a `@doc.*` Javadoc tag | ✅ Yes | Documentation only |
| Refactor internal implementation without changing the public API surface | ✅ Yes | Compile-time check required |
| Add a new `default` method to an interface | ✅ Yes | No impact on existing implementors |

---

## 2. Additive-Only Field Policy

### New fields

- New fields on record types MUST be optional or carry a sensible default that
  does not break deserialization of records written before the field existed.
- Use `@Nullable` / `Optional<T>` for fields that may be absent in older data.
- If using Jackson: annotate new fields with `@JsonInclude(NON_NULL)` so
  existing data that lacks the field does not fail on deserialization.

### Renaming fields

Field renaming is a **breaking change**. Follow the deprecation path below.
Use `@JsonAlias` to bridge the old name during the transition window.

### Removing fields

Field removal is a **breaking change**. Mark the field `@Deprecated` first;
remove only after the retention window has elapsed and all consumers have
migrated.

---

## 3. Deprecation Policy

All breaking changes must go through this lifecycle:

```
Active ──► @Deprecated (kept for 2 sprints / ~1 month) ──► Removal
```

### Steps

1. **Deprecate** — annotate with `@Deprecated` and add a Javadoc comment:
   ```java
   /**
    * @deprecated Use {@link #newField()} instead. Will be removed in Sprint N+2.
    */
   @Deprecated(since = "2026-05-01", forRemoval = true)
   public String oldField() { ... }
   ```
2. **Notify consumers** — file a tracking issue with `platform-entity-deprecation` label;
   link it in the Javadoc `@deprecated` note.
3. **Migration window** — 2 sprints minimum before the field or type may be removed.
4. **Remove** — only after all consumers have migrated and the tracking issue is closed.

---

## 4. Breaking Change Classification

The following changes are **always breaking** and require the full deprecation path
plus a migration strategy before merging:

| Change | Why it breaks |
|---|---|
| Remove a field | Deserialization of stored data fails |
| Rename a field (without `@JsonAlias`) | Same as removal from JSON perspective |
| Change a field's type (e.g. `String` → `UUID`) | Deserialization fails for old data |
| Make an optional field required | Old records without the field cannot be read |
| Remove a `default` method from an interface | All implementing classes must be updated |
| Remove an interface from the sealed hierarchy | Consumers using `switch` statements fail |
| Change the sealed type hierarchy root | All pattern-match consumers must be updated |

---

## 5. Version Bump Criteria

This module does not currently carry a public semantic version number.
When a breaking change cannot be avoided, the following actions are required
instead of a version bump:

1. File a tracking issue labelled `platform-entity-breaking-change`.
2. Coordinate across all consumers (data-cloud API, event store, storage connectors).
3. Execute the migration atomically within a single PR or a gated release gate.
4. Update this document to record the breaking change under the **Change Log** section below.

---

## 6. Key Entity Types and Their Stability Status

| Type | Stability | Notes |
|---|---|---|
| `Record` (interface) | Stable | Root entity contract — no changes without RFC |
| `ImmutableRecord` (interface) | Stable | Read-only view contract |
| `MutableRecord` (interface) | Stable | Write-path contract |
| `MetadataRecord` | Stable | Core metadata fields — additive only |
| `Auditable` (interface) | Stable | Audit trail — additive only |
| `Timestamped` (interface) | Stable | Temporal tracking |
| `Schematized` (interface) | Stable | Schema version carrier |
| `Versioned` (interface) | Stable | Optimistic lock version |
| `HasMetadata` (interface) | Stable | Metadata accessor |
| `AIEnhanced` (interface) | Evolving | AI enrichment fields may grow |
| `FullEntityRecord` | Stable | Canonical entity record |
| `FullDocumentRecord` | Stable | Document record |
| `FullGraphRecord` | Stable | Graph node record |
| `SimpleRecord` | Stable | Lightweight projection record |
| `ImmutableEventRecord` | Stable | Event sourcing record |

---

## 7. Change Log

_Record breaking changes here with date, description, and migration reference._

| Date | Change | Migration Reference |
|---|---|---|
| *(no breaking changes yet)* | — | — |
