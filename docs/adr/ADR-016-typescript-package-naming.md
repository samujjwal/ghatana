# ADR-016: TypeScript Platform Package Naming Convention

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-03-25 |
| **Author** | Ghatana Platform Team |
| **Resolves** | Audit finding LOW-002 |
| **Supersedes** | — |

---

## Context

The `@ghatana` npm scope contains ~14 platform packages that have grown
organically. Two distinct naming sub-patterns exist:

| Pattern | Packages |
|---------|---------|
| `@ghatana/<name>` | `theme`, `tokens`, `design-system`, `canvas`, `charts`, `i18n`, `realtime`, `api`, `sso-client`, `accessibility-audit`, `ui-integration`, `utils` (deprecated) |
| `@ghatana/platform-<name>` | `platform-shell`, `platform-utils` |

There is no documented rule for when the `platform-` prefix is used, creating
confusion about which packages are "core infrastructure" vs. "application-level
UI utilities".

---

## Decision

**Adopt a two-tier naming model within `@ghatana/`:**

### Tier 1 — `@ghatana/platform-*`
Reserved for **cross-cutting infrastructure** packages that:
- Are consumed by multiple products and platform packages
- Have no product-domain semantics
- Are maintained by the Platform team and subject to platform governance

**Current packages:** `platform-utils`, `platform-shell`

**Future additions** must meet all three criteria above to get the `platform-`
prefix.

### Tier 2 — `@ghatana/<name>` (unprefixed)
Everything else — UI component libraries, domain-specific packages, and
deprecated packages. These may be consumed within a product or shared
cross-product, but they carry semantic meaning tied to a feature area.

**Current packages:** `design-system`, `theme`, `tokens`, `charts`, `canvas`,
`i18n`, `realtime`, `api`, `sso-client`, `accessibility-audit`,
`ui-integration`

---

## Why NOT rename everything to `@ghatana/platform-*`

1. **Package renaming is a breaking change** — all `package.json` in all
   products must be updated; CI pipelines and external consumers must be
   coordinated.
2. **The `platform-` prefix signals "infrastructure", not "UI"** — applying it
   to `design-system` or `tokens` would be misleading.
3. **Scope isolation already exists** — the `@ghatana/` npm scope provides
   namespace uniqueness without the prefix.

---

## Consequences

### Positive
- New packages have a clear rule to follow (criteria-based prefix assignment).
- No breaking rename required; existing consumers are unaffected.
- The distinction "infrastructure vs. UI/domain" is now documented and
  discoverable.

### Negative
- The existing inconsistency between `@ghatana/theme` (no prefix) and
  `@ghatana/platform-shell` (prefix) remains. These are both platform-maintained
  packages. If `theme` ever needs a v2.0 breaking rename for other reasons,
  migrating to `@ghatana/platform-theme` at that point is acceptable.

### Neutral
- `@ghatana/utils` is deprecated and will be removed. Its canonical replacement
  `@ghatana/platform-utils` already follows Tier 1 naming.

---

## Enforcement

New packages must be reviewed against this ADR in the Module Admission Checklist
([docs/MODULE_ADMISSION_CHECKLIST.md](../MODULE_ADMISSION_CHECKLIST.md)).

---

## Alternatives Considered

| Alternative | Rejected Because |
|-------------|-----------------|
| Rename all to `@ghatana/platform-*` | Breaking change; misleading for UI packages |
| Keep status quo with no documented rule | Leaves naming ambiguity for future packages |
| Switch to separate `@ghatana-platform/` org scope | Would require npm org creation and even more renames |
