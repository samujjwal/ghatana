# Ghatana Architecture

> **Owner:** Platform Team | **Status:** Active | **Last Updated:** 2026-04-14

---

## 1. Repository Structure

```
ghatana/
├── docs/                    # Monorepo-level documentation (this directory)
├── platform/                # Shared, product-agnostic platform libraries
│   ├── java/               # Java platform modules
│   ├── typescript/         # TypeScript platform modules
│   └── contracts/          # Protobuf / OpenAPI / JSON schema contracts
├── platform-kernel/        # ActiveJ-based kernel platform (lifecycle, plugins, DI)
├── platform-plugins/       # Platform-level plugins (billing, audit, consent, etc.)
├── products/               # Product implementations
│   ├── aep/               # Agentic Event Processor
│   ├── data-cloud/        # Data Cloud Platform
│   ├── dcmaar/            # Data Center Management / AR
│   ├── flashit/           # Flashit mobile
│   ├── phr/               # Personal Health Records (Nepal)
│   ├── software-org/      # Software Organization product
│   ├── tutorputor/        # AI Tutoring Platform
│   ├── virtual-org/       # Virtual Organization
│   └── yappc/             # YAPPC (14 domain modules + refactorer)
├── shared-services/        # Deployable cross-product runtime services
│   ├── auth-gateway/      # Authentication + authorization gateway
│   └── user-profile-service/
├── integration-tests/      # Cross-product integration test suites
├── apps/web/               # Root-level web application (platform shell)
├── scripts/               # Build, CI, and developer automation scripts
├── config/                # OWASP suppression, Checkstyle, PMD, SpotBugs
└── gradle/                # Version catalog, convention utilities, templates
```

---

## 2. Platform Java Modules (`platform/java/`)

| Module | Purpose |
|--------|---------|
| `core` | Value types, health, validation, resilience, service model |
| `agent-core` | Nine-type agent framework (`TypedAgent<I,O>`, registry, lifecycle) |
| `agent-memory` | Agent memory abstractions (episodic, semantic, procedural) |
| `ai-integration` | LLM gateway, embeddings, vector store, feature store |
| `audio-video` | STT, TTS, vision, media config |
| `audit` | Immutable platform audit trail |
| `config` | Runtime configuration loading |
| `data-governance` | Data classification and governance contracts |
| `database` | ActiveJ database, JPA, Flyway migrations, Redis pub/sub cache |
| `distributed-cache` | Distributed cache abstractions |
| `domain` | Platform domain objects and schema types |
| `governance` | Policy evaluation and governance enforcement |
| `http` | ActiveJ HTTP request lifecycle, middleware, filters |
| `identity` | Identity SPI, tenant-scoped identity |
| `incident-response` | Incident management |
| `messaging` | Kafka, event connectors, messaging abstractions |
| `observability` | Structured logging, metrics, tracing (OpenTelemetry) |
| `policy-as-code` | OPA-style policy evaluation |
| `runtime` | ActiveJ event loop abstractions |
| `security` | JWT, auth filters, RBAC, tenant isolation |
| `security-analytics` | Injection detection, egress security |
| `workflow` | Workflow orchestration |

**New integration abstractions should be added inside existing modules first.** Promote to a new module only after consumer demand and module-governance checks (`scripts/check-new-platform-module.sh`) justify it.

---

## 3. Platform TypeScript Libraries (`platform/typescript/`)

See [platform-libraries/LIBRARY_INDEX.md](./platform-libraries/LIBRARY_INDEX.md) for the full catalogue with dependency direction and quick-start patterns.

**Dependency direction:** `tokens` / `theme` → `platform-utils` → `design-system` / `canvas` / `realtime` / `events` → domain packages.

---

## 4. Platform Kernel (`platform-kernel/`)

The kernel provides the foundational ActiveJ-based lifecycle, dependency injection, capability registration, and plugin architecture for the platform.

- `kernel-core` — `KernelModule`, `KernelContext`, `KernelRegistry`, lifecycle (initialize/start/stop/health)
- `kernel-plugin` — Plugin interface, governance, platform types

**Key characteristics:**
- Topological dependency resolution (O(V+E)); circular dependencies are detected and rejected
- All initialization chains are Promise-based (never blocking)
- Capability/dependency model is composable and extensible

---

## 5. Product Layer

Each product under `products/<name>/` contains:
- `docs/` — Product documentation
- `apps/` or `web/` — UI applications
- `services/` or `libs/java/` — Backend services
- `core/` — Domain modules (YAPPC uses 14 Gradle submodules in 5 clusters)

### Products at a Glance

| Product | Stack | Domain |
|---------|-------|--------|
| `aep` | Java (ActiveJ) | Agentic Event Processor — central agent runtime |
| `data-cloud` | Java + TypeScript | Data collection, querying, agent-backed analytics |
| `dcmaar` | TypeScript (React) + Java | Data center management, browser extension, AR visualization |
| `flashit` | React Native | Mobile flashcard / learning app |
| `phr` | Java (ActiveJ, FHIR R4) | Personal Health Records for Nepal market |
| `software-org` | Java | Software organization tooling |
| `tutorputor` | TypeScript (Next.js) + Java | AI tutoring platform |
| `yappc` | Java + TypeScript | Primary YAPPC platform |

---

## 6. Shared Services

Shared services are **deployable cross-product runtime services** — not in-process libraries.

A capability belongs in `shared-services/` only when:
1. It runs as a service (not just a library)
2. Multiple products consume it in production over the network
3. No single product can own the runtime lifecycle

**Current shared services:**
- `auth-gateway` — Authentication gateway (absorbs `auth-service`)
- `user-profile-service`
- `ai-inference-service` — Status: stabilize as genuine cross-product service or remove

> If a capability is reused by import → it belongs in `platform/java/` or `platform/typescript/`.  
> If it is product-specific business logic → it belongs in `products/<name>/`.

---

## 7. Cross-Product Integration

**Products must not communicate with each other directly.** All cross-product flows route through the Kernel plugin layer.

### Pattern: PHR ↔ Finance via Kernel

```
┌──────────────────────────────────────────────────────────┐
│ PRODUCTS                                                  │
│  PHR (BillingService) ────────────────► uses plugin API  │
│  Finance (LedgerService) ──────────────► implements SPI  │
└─────────────────────────┬────────────────────────────────┘
                          │ routes through
                          ▼
┌──────────────────────────────────────────────────────────┐
│ KERNEL PLATFORM                                          │
│  BillingLedgerPlugin  (postToLedger, queryByCorrelation) │
│  Event Bus  (billing.ledger.posted, .retry_scheduled)    │
│  Audit Plugin                                            │
└──────────────────────────────────────────────────────────┘
```

```java
// Kernel-level integration seam (platform layer)
public interface BillingLedgerPlugin {
    Promise<LedgerResponse> postToLedger(BillingLedgerRequest request);
    Promise<List<LedgerEntry>> queryByCorrelation(String correlationId);
}

public record BillingLedgerRequest(
    String correlationId,
    String tenantId,
    String productId,
    BigDecimal amount,
    String currency,
    String description,
    Instant timestamp
) {}
```

---

## 8. Observability Stack

| Component | URL (local) | Purpose |
|-----------|-------------|---------|
| Grafana | http://localhost:3001 (admin/admin) | Dashboards |
| Prometheus | http://localhost:9090 | Metrics scrape |
| Jaeger | http://localhost:16686 | Distributed tracing |
| Loki | http://localhost:3100 | Log aggregation |

All services expose `/metrics` in Prometheus format. Correlation IDs (`X-Correlation-ID`) are propagated across service boundaries.

---

## 9. Key Architecture Decisions

See [docs/adr/](./adr/) for the full set. Key decisions:

| ADR | Decision |
|-----|---------|
| ADR-001 | Nine-type typed agent framework (`TypedAgent<I,O>`) |
| ADR-004 | ActiveJ as the async framework (Promise-based, no blocking) |
| ADR-005 | Multi-tenant isolation via `TenantContext` |
| ADR-007 | OpenTelemetry observability stack |
| ADR-012 | Keep AEP as the product execution gateway |
| ADR-013 | Shared-services ownership and fate |
| ADR-019 | Auth-gateway / security-gateway boundary |
| ADR-020 | Five-layer agent system architecture |

---

## Related Documents

- [VISION.md](./MONOREPO_VISION.md) — Strategic direction and roadmap
- [GOVERNANCE.md](./GOVERNANCE.md) — Decision-making, naming conventions, deprecation
- [ONBOARDING.md](./ONBOARDING.md) — Developer setup
- [BUILD.md](./BUILD.md) — Build system guide
- [agent-system/ARCHITECTURE.md](./agent-system/ARCHITECTURE.md) — Agent framework architecture
- [platform-libraries/LIBRARY_INDEX.md](./platform-libraries/LIBRARY_INDEX.md) — TypeScript platform library catalogue

- [MONOREPO_VISION.md](./MONOREPO_VISION.md) - Strategic vision
- [GOVERNANCE.md](./GOVERNANCE.md) - Governance model
- [INTEGRATION_PLATFORM_ARCHITECTURE.md](./INTEGRATION_PLATFORM_ARCHITECTURE.md) - Shared integration architecture
- [platform-libraries/](./platform-libraries/) - Platform library docs
