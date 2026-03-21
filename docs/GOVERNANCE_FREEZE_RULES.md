# Governance Freeze Rules — Phase 0

**Status:** ACTIVE  
**Effective:** 2026-03-21  
**Scope:** All products in the Ghatana monorepo

---

## 1. Freeze Announcement

Effective immediately, **no new product-local agent infrastructure** may be introduced.
All new agent-related code must go through the centralized AEP platform.

### Blocked Patterns

The following class/file patterns are **frozen** — no new instances allowed outside
`platform/java/agent-*` and `products/aep/`:

| Pattern | Type | Description |
|---------|------|-------------|
| `*AgentRegistry.java` | Java class | Product-local agent registries |
| `*RegistryHandler.java` | Java class | HTTP handlers serving local registries |
| `*AgentCatalog.java` | Java class | Product-local catalogs |
| `*CatalogLoader.java` | Java class | File-system catalog loaders |
| Direct filesystem scans for agent definitions | Code pattern | e.g., `Files.list()` scanning for `agent-*.yaml` |

### Allowed

- Importing from `platform:java:agent-api` / `platform:java:agent-spi`
- Implementing `AgentLogicProvider` SPI (Phase 3+)
- Using the AEP catalog service APIs (Phase 2+)

---

## 2. Existing Exceptions Registry

The following violations pre-exist the freeze and are **grandfathered** until their
migration phase completes.

| Violation | Location | Owner | Migration Phase | Notes |
|-----------|----------|-------|-----------------|-------|
| `YAPPCAgentRegistry.java` | `products/yappc/libs/java/yappc-domain/` | YAPPC Team | Phase 5 (P5-2) | Full replacement by AEP runtime |
| `YappcAgentCatalog.java` | `products/yappc/libs/java/yappc-domain/` | YAPPC Team | Phase 5 (P5-1) | Migrate to `agent-catalog.yaml` |
| Data Cloud agent-registry module | `products/data-cloud/agent-registry/` | Data Team | Phase 6 (P6-1) | Remove reflective AEP detection |
| `AgenticDataProcessor` bootstrap | `products/data-cloud/agent-service/` | Data Team | Phase 6 (P6-1) | Reflective/hybrid runtime |
| AEP platform-registry local store | `products/aep/platform-registry/` | AEP Team | Phase 4 (P4-1) | Centralize into AEP registry service |

### Accountabilities

- **Product teams** own their exceptions and must migrate by their assigned phase.
- **AEP Team** provides migration guides and central APIs (Phase 4).
- **Platform Team** enforces CI rules and reviews architecture compliance.

---

## 3. CI Enforcement

Architecture rules are enforced by:

| Check | Tool | Scope | Enforcement |
|-------|------|-------|-------------|
| Banned Java class names | `scripts/check-java-architecture.sh` | Java sources | Fails PR |
| Package JSON validity | `scripts/check-architecture-compliance.js` | All packages | Fails PR |
| OpenAPI spec sync | Gradle `verifyOpenApiSync` | AEP server | Fails build |
| Cross-product imports | ESLint `no-cross-product-imports` | TypeScript | Fails lint |
| Banned libraries | ESLint `no-banned-libraries` | TypeScript | Fails lint |

---

## 4. Requesting an Exception

If a product team needs a temporary exception:

1. File an issue with the `governance-exception` label
2. Include: justification, scope, proposed migration timeline
3. AEP Team + Platform Team must approve
4. Exception is added to section 2 with an expiry phase

---

## Change Log

| Date | Change |
|------|--------|
| 2026-03-21 | Initial governance freeze established |
