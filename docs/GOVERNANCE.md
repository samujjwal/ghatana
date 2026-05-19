# Ghatana Governance

> **Owner:** Platform Team | **Status:** Active | **Effective:** 2026-03-22

## Authority Table

| Governance area | Authoritative source | Validation |
| --- | --- | --- |
| Domain workstream map | [`docs/architecture/DOMAIN_WORKSTREAM_MAP.md`](architecture/DOMAIN_WORKSTREAM_MAP.md) | `pnpm check:domain-registry` |
| Machine-readable domain registry | [`config/domain-registry.json`](../config/domain-registry.json) | `pnpm check:domain-registry` |
| Machine-readable product registry | [`config/canonical-product-registry.json`](../config/canonical-product-registry.json) | `pnpm check:product-registry` |
| Repository-wide implementation rules | [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) | `node scripts/check-doc-authority.mjs` |
| Product architecture direction | [`ghatana_unified_product_development_blueprint_hardened.md`](../ghatana_unified_product_development_blueprint_hardened.md) | `pnpm check:doc-truth` |
| Platform library governance | [`platform/typescript/LIBRARY_GOVERNANCE.md`](../platform/typescript/LIBRARY_GOVERNANCE.md) | `pnpm check:platform-package-governance` |

Lower-level docs may summarize these rules for local context, but they must reference the authoritative source instead of redefining registry, domain, product, package, or implementation governance.

### Stale-Doc Exceptions

Use a stale-doc exception only when a lower-level document cannot be updated in the same change. The exception must record the doc path, owner, classification, expiry date, replacement document, and validation command. Expired exceptions must fail the relevant doc-governance check rather than silently carrying stale guidance.

---

## 1. Governance Model

The Ghatana monorepo uses a tiered governance model:

1. **Monorepo Level** — Cross-cutting decisions affecting multiple products
2. **Product Level** — Product-specific architectural and design decisions
3. **Package Level** — Implementation details within clear boundaries

Product-facing readiness and status language must follow [docs/process/PRODUCT_TRUTHFULNESS_POLICY.md](/Users/samujjwal/Development/ghatana/docs/process/PRODUCT_TRUTHFULNESS_POLICY.md).

### Decision-Making Authority

| Scope | Decision Maker | Documentation |
|-------|---------------|---------------|
| Build system changes | Platform Team | ADR in `docs/adr/` |
| New product admission | Architecture Board | `scripts/create-module.sh` |
| Product boundaries | Product Owner + Platform Team | Product `ARCHITECTURE.md` |
| Package API changes | Package Owner | Package `README.md` |
| New platform libraries | Platform Team (RFC process) | See §5 below |

### CI-Enforced Standards

| Check | Script | Blocks |
|-------|--------|--------|
| Architecture score gate | `scripts/architecture-score-gate.sh` | PR merge |
| Java architecture freeze | `scripts/check-java-architecture.sh` | PR merge |
| Cross-product dependencies | `scripts/check-cross-product-deps.sh` | PR merge |
| Deprecated UI package use | `scripts/check-deprecated-ui.sh` | PR merge |
| Duplicate package names | `scripts/check-duplicate-package-names.sh` | PR merge |
| Platform package layering | `scripts/check-platform-package-governance.js` | PR merge |
| JWT dependency policy | `scripts/check-jwt-dependency-policy.sh` | PR merge |
| License compliance | `scripts/check-license-policy.sh` | PR merge |
| Gradle version catalog | `scripts/validate-gradle-build.sh` | PR merge |
| Java doc tags | `scripts/validate-doc-tags.sh` | PR merge |
| Agent conformance | `scripts/check-agent-conformance.sh` | PR merge |

---

## 2. Naming Conventions

### Java Platform Packages

**Canonical public namespace:** `com.ghatana.platform.*`

- New consumer-facing platform Java APIs must live under `com.ghatana.platform.*`.
- `com.ghatana.core.*` is transitional/legacy unless explicitly marked canonical in a migration doc.
- Consumers must depend on contract packages (`..port..`, `..api..`, `..spi..`) rather than concrete implementation packages.

```java
// Preferred: depend on the platform port
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;

// Avoid: binding to the concrete implementation package
// import com.ghatana.platform.security.jwt.JwtTokenProviderImpl;
```

### TypeScript Platform Packages

All TypeScript platform libraries use the `@ghatana/` scope with canonical names:

| Package | Location | Purpose |
|---------|----------|---------|
| `@ghatana/tokens` | `platform/typescript/tokens` | Design tokens (color, spacing, typography) |
| `@ghatana/theme` | `platform/typescript/theme` | Theme provider, CSS variable injection |
| `@ghatana/design-system` | `platform/typescript/design-system` | WCAG AA UI component library |
| `@ghatana/platform-utils` | `platform/typescript/foundation/platform-utils` | Cross-cutting utility functions |
| `@ghatana/api` | `platform/typescript/api` | HTTP client with middleware and retries |
| `@ghatana/realtime` | `platform/typescript/realtime` | WebSocket / SSE helpers |
| `@ghatana/events` | `platform/typescript/events` | Typed event bus |
| `@ghatana/browser-events` | `platform/typescript/browser-events` | Mouse, keyboard, clipboard event handlers |
| `@ghatana/state` | `platform/typescript/state` | State management atoms + React hooks |
| `@ghatana/config` | `platform/typescript/config` | Configuration + feature flags |
| `@ghatana/canvas` | `platform/typescript/canvas` | Hybrid canvas renderer (custom + ReactFlow) |
| `@ghatana/charts` | `platform/typescript/charts` | Chart primitives built on Recharts |
| `@ghatana/i18n` | `platform/typescript/i18n` | Internationalization framework |
| `@ghatana/eslint-plugin` | `platform/typescript/eslint-plugin` | Architecture lint rules |
| `@ghatana/code-editor` | `platform/typescript/code-editor` | Lazily-loaded Monaco editor component |
| `@ghatana/platform-shell` | `platform/typescript/platform-shell` | NavBar, TenantSelector, NotificationCenter |
| `@ghatana/sso-client` | `platform/typescript/sso-client` | SSO / JWT authentication client |
| `@ghatana/platform-testing` | `platform/typescript/testing` | Shared test helpers (a11y, WCAG, perf) |
| `@ghatana/ui-integration` | `platform/typescript/ui-integration` | AI features + collaboration integration |
| `@ghatana/accessibility-audit` | `platform/typescript/accessibility-audit` | Automated WCAG audit helpers |

**Rules:**
- Use kebab-case: `design-system`, `sso-client`
- No product-specific names in `@ghatana/` scope
- `@ghatana/dcmaar` and `@ghatana/dcmaar-backend-shim` are private workspace-only entries, not library packages

### TypeScript Product Packages

Product libraries use a product-specific scope:

```
✅ @yappc/core        ✅ @yappc/ui        ✅ @yappc/ai
✅ @data-cloud/ui-components
✅ @dcmaar/types      ✅ @dcmaar/ui
✅ @audio-video/ui    ✅ @flashit/shared
```

Products must not use `@ghatana/` scope for product-specific libraries.

### Product-to-Product Dependencies

Products must not depend on each other directly. Share through `@ghatana/*` platform abstractions only.

---

## 3. Architecture Freeze — Agent Infrastructure

**Effective:** 2026-03-21 | **Scope:** All products

No new product-local agent infrastructure may be introduced. All new agent-related code must go through the centralized AEP platform.

### Blocked Patterns

Outside `platform/java/agent-*` and `products/data-cloud/planes/action/`, these patterns are frozen:

| Pattern | Type |
|---------|------|
| `*AgentRegistry.java` | Product-local agent registries |
| `*RegistryHandler.java` | HTTP handlers serving local registries |
| `*AgentCatalog.java` | Product-local catalogs |
| `*CatalogLoader.java` | File-system catalog loaders |
| `Files.list()` scanning `agent-*.yaml` | Filesystem scan for agent definitions |

### Allowed

- Implementing `AgentLogicProvider` SPI
- Using the AEP catalog service APIs
- Importing from `platform:java:agent-api` / `platform:java:agent-spi`

### Grandfathered Exceptions

| Violation | Location | Migration Phase |
|-----------|----------|-----------------|
| `YAPPCAgentRegistry.java` | `products/yappc/libs/java/yappc-domain/` | Phase 5 |
| `YappcAgentCatalog.java` | `products/yappc/libs/java/yappc-domain/` | Phase 5 |
| Data Cloud agent-registry module | `products/data-cloud/extensions/agent-registry/` | Phase 6 |
| `AgenticDataProcessor` bootstrap | `products/data-cloud/planes/action/agent-runtime/` | Phase 6 |
| AEP platform-registry local store | `products/data-cloud/planes/action/platform-registry/` | Phase 4 |

---

## 4. Module Deprecation Policy

### Lifecycle Stages

```
ACTIVE → DEPRECATED → SUNSET → RETIRED
```

| Stage | Description | Duration |
|-------|-------------|----------|
| **ACTIVE** | In active use and maintenance | — |
| **DEPRECATED** | Scheduled for removal; no new consumers; existing must migrate | Minimum 90 days |
| **SUNSET** | Build disabled; code still present but excluded from CI | 30 days |
| **RETIRED** | Code deleted; entry removed from `settings.gradle.kts` / `pnpm-workspace.yaml` | Final |

### Moving ACTIVE → DEPRECATED

1. Create an ADR in `docs/adr/` explaining why and what replaces it
2. Add `MIGRATION.md` inside the module with step-by-step migration instructions
3. Add `@deprecated` JavaDoc/JSDoc to all public APIs
4. Add a `// DEPRECATED(YYYY-MM): migrating to :replacement — ADR-NNN` comment in `settings.gradle.kts`
5. Architecture Board approval required

### Moving DEPRECATED → SUNSET

- Zero active consumers (validated by build without the module)
- 90-day minimum has elapsed
- Platform Team sign-off

### Moving SUNSET → RETIRED

- Remove `include()` from `settings.gradle.kts`
- Wait 30 days
- Tag the last commit `deprecated/<module-name>`
- Delete the source directory

### Fast-Track (Security / Critical Bug)

Minimum deprecation window drops to **2 weeks** when both Architecture Board and Security Team approve.

### Currently Deprecated Modules

| Module | Deprecated Since | Replacement | Target Retirement |
|--------|-----------------|-------------|-------------------|
| _(none)_ | — | — | — |

---

## 5. Library Governance (RFC Process)

### When an RFC is Required

- Creating a new `platform/typescript/*` package
- Extracting a shared library from a product
- Promoting a product-local library to shared-services

### RFC Steps

1. Open a GitHub Discussion in the `Library Proposals` category
2. Use the template below
3. Tag `@platform-team` for review
4. Wait for 2 platform-team approvals and no blocking objections (7-day minimum)
5. On approval: create the library following platform conventions

### RFC Template

```markdown
# RFC: @ghatana/<library-name>

## Purpose
## Alternatives Considered
## Proposed API Surface
## Affected Products
## Impact on Existing Libraries
## Migration Plan
## Quality Commitment (coverage target, owner)
```

### Change Approval Thresholds

| Change Type | Approvals Required |
|-------------|-------------------|
| Bug fix, docs, patch version bump | None |
| New exports, minor/major dep bump, new peer dep | 1 platform-team |
| Removing/renaming exports, changing signatures, breaking defaults | 2 platform-team |

---

## 6. Library Ownership

### Platform Libraries

| Package | Owner |
|---------|-------|
| `@ghatana/tokens`, `@ghatana/theme` | Platform Team |
| `@ghatana/design-system` | Platform Team |
| `@ghatana/platform-utils`, `@ghatana/api` | Platform Team |
| `@ghatana/realtime`, `@ghatana/events`, `@ghatana/browser-events` | Platform Team |
| `@ghatana/state`, `@ghatana/config` | Platform Team |
| `@ghatana/canvas`, `@ghatana/charts`, `@ghatana/i18n` | Platform Team |
| `@ghatana/eslint-plugin` | Platform Team |

### Product Libraries

| Package | Owner |
|---------|-------|
| `@yappc/core`, `@yappc/api`, `@yappc/state`, `@yappc/ui`, `@yappc/ai` | YAPPC Team |
| `@dcmaar/types`, `@dcmaar/ui`, `@dcmaar/browser-extension-core` | DCMAAR Team |
| `@dcmaar/bridge-protocol`, `@dcmaar/connectors`, `@dcmaar/plugin-*` | DCMAAR Team |
| `@data-cloud/ui-components` | Data-Cloud Team |
| `@audio-video/ui` | Audio-Video Team |
| `@flashit/shared` | Flashit Team |

### Owner Responsibilities

1. **Responsiveness** — Reply to issues and PRs within 5 business days
2. **Dependency hygiene** — Monthly dependency reviews; security patches within 48h
3. **Test health** — Maintain ≥80% test coverage; don't merge failing tests
4. **Changelog discipline** — Document breaking changes in PRs

### Archived / Removed Libraries

| Package | Removed In | Replaced By |
|---------|-----------|-------------|
| `@ghatana/ui` | V4.1 | `@ghatana/design-system` |
| `@ghatana/audit-ui`, `@ghatana/privacy-ui`, `@ghatana/security-ui` | V4.1 | `@ghatana/design-system` |
| `@ghatana/voice-ui`, `@ghatana/nlp-ui`, `@ghatana/selection-ui` | V4.1 | `@ghatana/design-system` |
| `@yappc/canvas` | Sprint 4 | `@ghatana/canvas` |
| `@yappc/auth`, `@yappc/chat`, `@yappc/security`, `@yappc/testing` | Library Restructuring | `@yappc/core` |

---

## 7. CODEOWNERS

```
platform/typescript/              @ghatana/platform-team
platform/typescript/eslint-plugin/ @ghatana/platform-team
products/yappc/                   @ghatana/yappc-team
products/dcmaar/                  @ghatana/dcmaar-team
products/data-cloud/              @ghatana/data-cloud-team
products/audio-video/             @ghatana/audio-video-team
products/flashit/                 @ghatana/flashit-team
eslint-rules/                     @ghatana/platform-team
docs/                             @ghatana/platform-team
```

---

## 8. Quarterly Boundary Audit

Run every quarter using `scripts/run-quarterly-audit.sh`. The audit covers:

- Module count trend and growth
- Cross-product dependency violations
- Platform → product layering violations
- Deprecated module staleness (flag if > 180 days in DEPRECATED)
- TypeScript boundary health (`scripts/check-platform-package-governance.js`)
- Architecture score (`scripts/architecture-score-gate.sh`)

Output is written to `docs/audits/quarterly-YYYY-QN.md`.

---

## Related Documents

- [VISION.md](./VISION.md) — Strategic direction and roadmap
- [ARCHITECTURE.md](./ARCHITECTURE.md) — Technical structure and integration patterns
- [TESTING.md](./TESTING.md) — Test standards and taxonomy
- [BUILD.md](./BUILD.md) — Build system and conventions
- [docs/adr/](./adr/) — Architecture Decision Records
