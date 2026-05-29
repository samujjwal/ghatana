# Module Ownership Matrix

**Status:** Accepted  
**Date:** 2026-05-10  
**Authors:** Platform Team  
**Phase:** BDY-5 (Boundary Enforcement)  
**Related:** `docs/GOVERNANCE.md`, `docs/adr/ADR-013-shared-services-ownership.md`

---

## Overview

This document provides a comprehensive module ownership matrix for the Ghatana repository, establishing clear boundaries between platform modules, shared services, and product-specific code. Each module is assigned an owning team with clear responsibilities for maintenance, evolution, and deprecation.

**Purpose:** 
- Prevent silent ownership drift
- Enable clear escalation paths for bugs and feature requests
- Enforce dependency directionality (platform → product, not reverse)
- Guide new contributors on who to contact for module changes

---

## Platform Modules (Owned by Platform Team)

### `platform/contracts/`
- **Owner:** Platform Team - API Governance Sub-team
- **Purpose:** Shared API contracts, schemas, and protocol definitions
- **Dependencies:** None (bottom of dependency graph)
- **Consumers:** All products, platform modules
- **Change Process:** Requires API Governance review for breaking changes

### `platform/java/core/`
- **Owner:** Platform Team - Core Runtime Sub-team
- **Purpose:** Core runtime utilities, common abstractions
- **Dependencies:** `platform/contracts/`
- **Consumers:** All platform modules, most products
- **Change Process:** Requires Platform Core review

### `platform/java/database/`
- **Owner:** Platform Team - Data Sub-team
- **Purpose:** Database abstraction, connection pooling, migrations
- **Dependencies:** `platform/java/core/`
- **Consumers:** Products requiring database access
- **Change Process:** Requires Data Sub-team review

### `platform/java/http/`
- **Owner:** Platform Team - Networking Sub-team
- **Purpose:** HTTP client/server abstractions, ActiveJ integration
- **Dependencies:** `platform/java/core/`
- **Consumers:** All products with HTTP endpoints
- **Change Process:** Requires Networking Sub-team review

### `platform/java/security/`
- **Owner:** Platform Team - Security Sub-team
- **Purpose:** Authentication, authorization, cryptography
- **Dependencies:** `platform/java/core/`, `platform/contracts/`
- **Consumers:** All products requiring security
- **Change Process:** Requires Security Sub-team review

### `platform/java/observability/`
- **Owner:** Platform Team - Observability Sub-team
- **Purpose:** Metrics, tracing, logging, alerting
- **Dependencies:** `platform/java/core/`
- **Consumers:** All products
- **Change Process:** Requires Observability Sub-team review

### `platform/java/testing/`
- **Owner:** Platform Team - Quality Sub-team
- **Purpose:** Test utilities, fixtures, test harness
- **Dependencies:** `platform/java/core/`, `platform/java/database/`
- **Consumers:** All products
- **Change Process:** Requires Quality Sub-team review

### `platform/java/workflow/`
- **Owner:** Platform Team - Workflow Sub-team
- **Purpose:** Workflow orchestration, DAG execution
- **Dependencies:** `platform/java/core/`, `platform/java/database/`
- **Consumers:** Products requiring workflow capabilities
- **Change Process:** Requires Workflow Sub-team review

### `platform/java/agent-core/`
- **Owner:** Platform Team - Agent Sub-team
- **Purpose:** Agent framework, tool integration, execution
- **Dependencies:** `platform/java/core/`, `platform/java/workflow/`
- **Consumers:** Products with agent capabilities
- **Change Process:** Requires Agent Sub-team review

### `platform/java/ai-integration/`
- **Owner:** Platform Team - AI Sub-team
- **Purpose:** AI model integration, completion services, embeddings
- **Dependencies:** `platform/java/core/`, `platform/java/http/`
- **Consumers:** Products with AI features
- **Change Process:** Requires AI Sub-team review

### `platform/java/plugin/`
- **Owner:** Platform Team - Extensibility Sub-team
- **Purpose:** Plugin registry, dynamic loading
- **Dependencies:** `platform/java/core/`
- **Consumers:** All products (7+ verified consumers)
- **Change Process:** Requires Extensibility Sub-team review

### `platform/typescript/design-system/`
- **Owner:** Platform Team - UX Sub-team
- **Purpose:** UI components, design tokens, theming
- **Dependencies:** None (UI layer)
- **Consumers:** All TypeScript-based products
- **Change Process:** Requires UX Sub-team review

### `platform/typescript/canvas/`
- **Owner:** Platform Team - UX Sub-team
- **Purpose:** Canvas/flow diagram components
- **Dependencies:** `platform/typescript/design-system/`
- **Consumers:** Products with workflow/flow UI
- **Change Process:** Requires UX Sub-team review

### `platform/typescript/realtime/`
- **Owner:** Platform Team - Networking Sub-team
- **Purpose:** WebSocket, event streaming, real-time updates
- **Dependencies:** None
- **Consumers:** Products with real-time features
- **Change Process:** Requires Networking Sub-team review

---

## Shared Services (Cross-Product)

### `shared-services/auth-gateway/`
- **Owner:** Platform Team - Security Sub-team
- **Purpose:** Authentication/authorization gateway, JWT validation
- **Dependencies:** `platform/java/security/`, `platform/java/http/`
- **Consumers:** All 7 products
- **Change Process:** Requires Security Sub-team review
- **Status:** KEEP + DEVELOP (per ADR-013)

### `shared-services/user-profile-service/`
- **Owner:** Platform Team - Identity Sub-team
- **Purpose:** Centralized user profile storage
- **Dependencies:** `platform/java/database/`, `platform/contracts/`
- **Consumers:** YAPPC, Finance, DCMAAR
- **Change Process:** Requires Identity Sub-team review
- **Status:** KEEP as cross-product service (per ADR-013)

### `shared-services/ai-inference-service/`
- **Owner:** Platform Team - AI Sub-team
- **Purpose:** AI inference routing/proxy
- **Dependencies:** `platform/java/ai-integration/`
- **Consumers:** TBD
- **Change Process:** Requires AI Sub-team review
- **Status:** STABILISE or REMOVE by 2026-04-30 (per ADR-013)

---

## Product Modules (Product-Owned)

### `products/data-cloud/`
- **Owner:** Data Cloud Team
- **Purpose:** Data Cloud platform, connectors, analytics
- **Dependencies:** Platform modules, shared services
- **Consumers:** None (leaf product)
- **Change Process:** Data Cloud Team owns all changes

### `products/aep/`
- **Owner:** AEP Team
- **Purpose:** AEP-specific features, orchestrator
- **Dependencies:** Platform modules, shared services
- **Consumers:** None (leaf product)
- **Change Process:** AEP Team owns all changes

### `products/yappc/`
- **Owner:** YAPPC Team
- **Purpose:** YAPPC orchestration, lifecycle management
- **Dependencies:** Platform modules, shared services
- **Consumers:** None (leaf product)
- **Change Process:** YAPPC Team owns all changes

### `products/finance/`
- **Owner:** Finance Team
- **Purpose:** Finance-specific features, PHR integration
- **Dependencies:** Platform modules, shared services
- **Consumers:** None (leaf product)
- **Change Process:** Finance Team owns all changes

### `products/dcmaar/`
- **Owner:** DCMAAR Team
- **Purpose:** DCMAAR-specific features
- **Dependencies:** Platform modules, shared services
- **Consumers:** None (leaf product)
- **Change Process:** DCMAAR Team owns all changes

### `products/audio-video/`
- **Owner:** Audio-Video Team
- **Purpose:** Audio/video processing, STT integration
- **Dependencies:** Platform modules, shared services
- **Consumers:** None (leaf product)
- **Change Process:** Audio-Video Team owns all changes

### `products/aura/`
- **Owner:** Aura Team
- **Purpose:** Aura-specific agent orchestration
- **Dependencies:** Platform modules, shared services
- **Consumers:** None (leaf product)
- **Change Process:** Aura Team owns all changes

---

## Platform Kernel (Special Ownership)

### `platform-kernel/`
- **Owner:** Platform Team - Kernel Sub-team
- **Purpose:** Platform kernel abstraction, plugin system
- **Dependencies:** None (kernel-level)
- **Consumers:** Products built on kernel
- **Change Process:** Requires Platform Architecture Board review
- **Status:** Emerging kernel pattern - see `KERNEL_IMPLEMENTATION_PLAN.md`

---

## Deprecated/Moved Modules

### `shared-services/auth-service/`
- **Status:** DELETED (consolidated into auth-gateway per ADR-013)
- **Reason:** Duplicate of auth-gateway functionality

### `shared-services/ai-registry/`
- **Status:** DELETED (consolidated into platform/java/ai-integration per ADR-013)
- **Reason:** AI model registry belongs in ai-integration

### `shared-services/feature-store-ingest/`
- **Status:** MOVED to `products/data-cloud/services/feature-store-ingest/` (per ADR-013)
- **Reason:** Feature store is data-cloud domain

### `platform/java/ai-experimental/`
- **Status:** MERGED into `platform/java/ai-integration` (per ADR-014)
- **Reason:** Premature generalization, no direct consumers

### `platform/java/ingestion/`
- **Status:** INVESTIGATING (per ADR-014)
- **Reason:** No product consumers, likely belongs in products/data-cloud

---

## Dependency Directionality Rules

1. **Platform → Product**: Platform modules can be imported by products
2. **Product → Platform**: Products MUST NOT import from other products
3. **Product → Shared Service**: Products can import from shared services
4. **Shared Service → Platform**: Shared services can import from platform modules
5. **Shared Service → Product**: Shared services MUST NOT import from products
6. **Platform Kernel → Product**: Products built on kernel can import kernel modules

---

## Change Request Process

### Platform Module Changes
1. Open issue in platform repo
2. Assign to owning sub-team
3. Sub-team reviews impact on all consumers
4. Breaking changes require deprecation notice (minimum 2 releases)
5. Update ADR if architectural change

### Shared Service Changes
1. Open issue in shared-services repo
2. Assign to owning team
3. Review impact on all consuming products
4. Coordinate with product teams for breaking changes
5. Update ADR-013 if service ownership changes

### Product Module Changes
1. Product team owns all changes
2. No external approval required for internal changes
3. Breaking changes to product APIs require product team approval
4. Product can request platform changes via issues

---

## Ownership Transfer Process

1. **Proposal:** Document reason for ownership transfer in issue
2. **Review:** Current owner and prospective owner review
3. **Approval:** Platform Architecture Board approves
4. **Update:** Update this matrix and relevant ADRs
5. **Handoff:** Code transfer, documentation update, notification

---

## Enforcement

- **CI Gate**: Gradle task validates dependency directionality
- **Review Gate**: PR reviewers verify ownership matrix compliance
- **Audit**: Quarterly audit of module ownership and dependencies
- **Escalation**: Ownership disputes resolved by Platform Architecture Board

---

## Related Documents

- `docs/adr/ADR-013-shared-services-ownership.md` - Shared services ownership decisions
- `docs/adr/ADR-014-platform-module-consumer-audit.md` - Platform module audit
- `docs/architecture/ARCHITECTURE_RULES.md` - Architecture rules
- `config/documentation-surface-registry.json` - Canonical documentation surface registry
