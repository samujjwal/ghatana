# Platform Documentation Review Report

**Date**: March 12, 2026  
**Scope**: All documents under `products/app-plaform/`  
**Reviewer**: Architecture Review  
**Focus Areas**: Naming Conventions · Completeness · Correctness · Extensibility · Flexibility

---

## Executive Summary

The documentation portfolio is architecturally sound and has been well-evolved toward a multi-domain platform model. However, a **fundamental identity collision** undermines the generic-platform thesis: "Siddhanta" is used as both the **platform name** and the **capital-markets domain pack name**, at every layer of the docs. Additionally, several kernel-level types and contracts embed domain-specific (Nepal/finance) vocabulary directly, violating the strict Kernel ↔ Domain Pack boundary the architecture itself mandates.

The four dimensions of the review (completeness, correctness, extensibility, flexibility) each surface their own set of specific gaps that are documented below with prioritized action items.

---

## 1. Naming Convention Review

> **Rule**: Generic platform needs its own identity. Domain-specific names belong exclusively inside domain packs. No kernel contract, event envelope field, type name, or ADR should reference any specific domain, jurisdiction, or industry.

---

### 1.1 CRITICAL — Platform Identity Collision

**Problem**: "Siddhanta" (सिद्धान्त) is a Nepal capital-markets brand name. It appears as the product name across every layer — READMEs, ADR headers, LLD titles, TDD specs, domain-pack development guides — even documents that are explicitly about the _generic_ kernel.

| Document                                      | Current Title / Usage                             | Issue                                           |
| --------------------------------------------- | ------------------------------------------------- | ----------------------------------------------- |
| `docs/README.md`                              | "Siddhanta — Capital Markets Operating System"    | Platform name is domain brand                   |
| `docs/KERNEL_PLATFORM_REVIEW.md`              | "Siddhanta Platform — Core Kernel Review"         | Kernel review carries domain brand              |
| `docs/DOMAIN_PACK_DEVELOPMENT_GUIDE.md`       | "for the Siddhanta Multi-Domain Operating System" | Generic guide is branded as domain-specific     |
| `docs/DOMAIN_PACK_INTERFACE_SPECIFICATION.md` | "Siddhanta kernel"                                | Interface spec carries domain brand             |
| `docs/adr/ADR-001` through `ADR-011`          | "Project Siddhanta" in headers                    | Platform-level ADRs carry domain brand          |
| All LLD files                                 | "Siddhanta Platform"                              | Kernel LLDs carry domain brand                  |
| Domain pack guide example                     | `author.organization: "Siddhanta Foundation"`     | Capital-markets org baked into generic template |

**Required Fix**: Assign the generic platform its own name (e.g., **"AppPlatform"** or **"GhatanaPlatform"** or simply **"Platform"** within the `app-plaform` product). Reserve "Siddhanta" exclusively as the name for the **Capital Markets Domain Pack**. Update all platform-level documents accordingly.

**Rename mapping**:
| Current | Corrected |
|---|---|
| "Project Siddhanta" (platform-level) | "[Platform Name]" (e.g., "AppPlatform") |
| "Siddhanta Platform" | "[Platform Name]" |
| "Siddhanta kernel" | "Platform Kernel" |
| "Siddhanta Multi-Domain Operating System" | "[Platform Name] Multi-Domain Operating System" |
| "Siddhanta Foundation" in pack examples | "[Your Org]" |
| "Siddhanta" as domain pack | **Retain** — correct usage |
| `Siddhanta_Platform_Specification.md` | `Capital_Markets_Nepal_Specification.md` (clarify scope) |

---

### 1.2 CRITICAL — K-15 "Dual-Calendar" Name Contradiction

**Problem**: The kernel module K-15 is named "Dual-Calendar Service" and the core data type is named `DualDate`. The ADR itself states the system must support Islamic, Thai, and other calendar systems via T1 plugins — yet "Dual" implies exactly two. This creates a permanent naming mismatch every time a third calendar is added.

Additionally, the `DualDate` interface hardcodes `gregorian` and `bs` (Bikram Sambat) as field names — meaning Nepal's fiscal calendar is embedded into the _generic kernel's_ primary time type.

**Current (problematic)**:

```typescript
interface DualDate {
  gregorian: ISO8601DateTime; // ← hardcoded calendar name
  bs: BSDate; // ← Nepal-specific calendar name baked into kernel type
  timezone: IANATimezone;
  fiscal_year_bs?: string; // ← Nepal-specific
  fiscal_year_ad?: string;
}
```

**Required Fix**:

- Rename K-15 to **"Multi-Calendar Service"** across all docs (ADR-004, LLD_K15, EPIC-K-15, tdd specs, ARCHITECTURE_SPEC docs)
- Rename `DualDate` to **`MultiCalendarDate`** or **`CalendarDate`**
- Redesign the type to use a pluggable map of calendar entries:

```typescript
interface CalendarDate {
  primary: ISO8601DateTime; // canonical UTC instant
  timezone: IANATimezone;
  calendars: Record<CalendarId, CalendarDateTime>; // pluggable: "gregorian", "bs", "hijri", etc.
  fiscalYear?: Record<CalendarId, string>; // per-calendar fiscal year
}
type CalendarId = string; // "gregorian" | "bs" | "hijri" | "thai" — open, not enum
```

- Change K-05 event envelope `FR7` requirement from `timestamp_gregorian` and `timestamp_bs` to use the `CalendarDate` type from K-15

---

### 1.3 HIGH — K-05 Event Envelope Nepal-Specific Fields

**Problem**: The K-05 Event Bus epic (FR7) and LLD define the canonical event envelope with `timestamp_bs` and `timestamp_gregorian` as first-class fields. This bakes Nepal's dual-calendar into every event the generic kernel ever emits, regardless of what domain pack is loaded.

**Affected files**:

- `docs/epics/EPIC-K-05-Event-Bus.md` — FR7, Section 5 Data Model
- `docs/lld/LLD_K05_EVENT_BUS.md`
- `docs/tdd_spec_k05_event_bus_expanded_v2.1.md` — EE_TC_007 "Handle dual-calendar timestamps"
- TDD group EE, EnvelopeField references throughout

**Required Fix**: Replace `timestamp_gregorian` / `timestamp_bs` in the kernel event envelope with a single `timestamp` field of type `CalendarDate` (from K-15). The K-15 service resolves which calendars are active for the current jurisdiction via T1 config packs.

```typescript
// BEFORE (hardcoded to Nepal)
EventEnvelope {
  timestamp_gregorian: Timestamp;
  timestamp_bs: String;
}

// AFTER (generic)
EventEnvelope {
  timestamp: CalendarDate;   // resolved by K-15; includes all configured calendars
}
```

---

### 1.4 HIGH — `DomainCapability` Enum Mixes Generic and Domain-Specific Values

**Problem**: `DOMAIN_PACK_INTERFACE_SPECIFICATION.md` defines a single `DomainCapability` enum that includes:

- Generic capabilities: `ENTITY_MANAGEMENT`, `WORKFLOW_ORCHESTRATION`, `BUSINESS_RULES`
- Finance-specific: `TRADING`, `SETTLEMENT`, `LEDGER`, `ACCOUNT_MANAGEMENT`, `TRANSACTION_PROCESSING`, `PAYMENT_PROCESSING`
- Healthcare-specific: `PATIENT_MANAGEMENT`, `CLINICAL_WORKFLOWS`, `MEDICAL_RECORDS`
- Manufacturing-specific: `PRODUCTION_MANAGEMENT`, `INVENTORY_CONTROL`, `QUALITY_CONTROL`

This means every new domain requires modifying the **core kernel interface** — violating the extension-without-modification principle.

**Required Fix**: The core `DomainPack` interface should only enumerate the _generic capability classes_. Domain-specific extensions belong in each pack's own capability declaration:

```typescript
// Core interface — generic capabilities only
enum CoreDomainCapability {
  ENTITY_MANAGEMENT = "entity_management",
  WORKFLOW_ORCHESTRATION = "workflow_orchestration",
  BUSINESS_RULES = "business_rules",
  INTEGRATION = "integration",
  USER_INTERFACE = "user_interface",
  CALCULATION_ENGINE = "calculation_engine",
  DATA_INGESTION = "data_ingestion",
  MASTER_DATA_MANAGEMENT = "master_data_management",
  POLICY_ENFORCEMENT = "policy_enforcement",
  COMPLIANCE_REPORTING = "compliance_reporting",
  AUDIT_TRAIL_GENERATION = "audit_trail_generation",
  REPORTING = "reporting",
  ANALYTICS = "analytics",
  NOTIFICATIONS = "notifications",
  DOCUMENT_MANAGEMENT = "document_management",
  SCREENING_ENGINE = "screening_engine",
  RECONCILIATION_ENGINE = "reconciliation_engine",
}

// Domain packs declare additional pack-specific capabilities as strings
interface DomainPack {
  capabilities: CoreDomainCapability[];
  extendedCapabilities?: string[]; // domain-specific, opaque to kernel
}
```

---

### 1.5 HIGH — `DomainType` Enum is Closed and Pre-Enumerates Industries

**Problem**: The `DomainType` enum (`FINANCIAL_SERVICES`, `HEALTHCARE`, `MANUFACTURING`, etc.) requires a kernel code change every time a new industry vertical is supported. This is anti-extensible for a platform whose purpose is unlimited domain extensibility.

**Required Fix**: Replace enum with a string taxonomy backed by a configurable domain registry:

```typescript
// BEFORE
enum DomainType {
  FINANCIAL_SERVICES = "financial-services",
  HEALTHCARE = "healthcare",
  ...
}

// AFTER
type DomainType = string;  // open — validated against domain registry at runtime

// Platform ships with well-known constants as optional helpers
namespace WellKnownDomainTypes {
  const FINANCIAL_SERVICES = "financial-services";
  const HEALTHCARE = "healthcare";
  const MANUFACTURING = "manufacturing";
  // Domain packs can introduce new types freely
}
```

---

### 1.6 MEDIUM — `finance-ghatana-integration-plan.md` Location

**Problem**: `products/app-plaform/finance-ghatana-integration-plan.md` is the integration plan for the **Capital Markets domain pack** integrating with Ghatana platform services. It sits at the product root, giving the impression that the whole `app-plaform` product is finance-specific.

**Required Fix**: Move to `domain-packs/capital-markets/docs/finance-ghatana-integration-plan.md` or `docs/archive/`. Update the Document Authority Map and README cross-references to reflect the move.

---

### 1.7 MEDIUM — `docs/Siddhanta_Platform_Specification.md` and `docs/siddhanta.md`

**Problem**: These documents are Nepal-specific capital markets instantiation specs. Their titles and placement in `docs/` make them appear to be platform-level documents rather than domain-pack specifications.

**Required Fix**:

- Keep their content unchanged (it is correct and complete Nepal-specific content)
- Rename to `Capital_Markets_Nepal_Platform_Specification.md` and `capital_markets_nepal.md`
- Add a header banner: `> DOMAIN PACK: Capital Markets (Siddhanta) — Nepal Instantiation. This is NOT a platform-level specification.`
- Move to `domain-packs/capital-markets/docs/` once the domain pack directory structure is created

---

### 1.8 MEDIUM — Product Folder Typo

**Problem**: The folder is named `products/app-plaform` (missing a 't'). This likely propagates across CI/CD scripts, import paths, and documentation links.

**Required Fix**: Rename to `products/app-platform` after confirming all downstream references. This is a mechanical rename but has wide blast radius — coordinate with build scripts.

---

### 1.9 LOW — `K-16 Ledger Framework` Name

**Problem**: "Ledger Framework" and "Double-Entry Accounting" are finance-industry terms. For healthcare (patient balance), logistics (cost ledger), or SaaS billing, the concept exists but the name is unfamiliar. The docs themselves describe it generically as "immutable ledger, balance queries."

**Required Fix**: Keep the implementation generic (it already is), but rename to **"K-16 Immutable Ledger Engine"** or **"K-16 Balance Ledger"** and explicitly document it as applicable to: financial accounts, credit/debit tracking in any domain, cost accounting, healthcare billing, subscription balances. Remove "Double-Entry Accounting" from kernel descriptions (keep in capital-markets pack docs).

---

## 2. Completeness Review

### 2.1 CRITICAL — No Inter-Domain Pack Communication Specification

The current docs specify how a domain pack communicates with the kernel (via K-12 SDK) and how it emits events (via K-05), but there is **no specification** for how two simultaneously loaded domain packs communicate with each other.

**Example gap**: If both "Capital Markets" and "Regulatory Reporting" packs are loaded, how do they share data? Via K-05 events only? Can they call each other's APIs? What isolation is enforced?

**Missing document**: `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md` must define:

- Event-only rule (K-05 pub/sub as the bus between packs, never direct RPC)
- Shared entity reference model (how Pack A refers to an entity owned by Pack B)
- Cross-pack saga coordination pattern
- Data visibility rules (tenant isolation across pack boundaries)

---

### 2.2 CRITICAL — K-05 Event Envelope Missing `domain_pack_id` Field

The canonical event envelope in EPIC-K-05 Section 5 does not include a field identifying which domain pack emitted the event. In a multi-domain deployment, the event store will contain events from multiple packs, and consumers need to filter or route by pack without inspecting the payload.

**Required Fix**: Add `source_domain_pack: string` (optional, filled by K-04 Plugin Runtime at publish time) to the `EventEnvelope` entity. Update FR, data model, and TDD specs accordingly.

---

### 2.3 HIGH — No Domain Pack Hot-Reload / Upgrade Specification

K-04 Plugin Runtime specifies hot-swap for T1/T2/T3 plugins. Domain packs are described as loaded through K-04, but no document specifies what happens during a domain-pack _upgrade_ in production: schema migrations, event schema version coexistence, rollback strategy, active request draining.

**Missing document**: `DOMAIN_PACK_UPGRADE_RUNBOOK.md` should cover:

- Version compatibility matrix (K-04 semver resolution)
- Schema migration protocol (K-05 schema registry enforcement during upgrade)
- Blue/green pack deployment (parallel old and new pack running)
- Rollback triggers and automated rollback conditions
- Data migration responsibility (must be in domain pack, not kernel)

---

### 2.4 HIGH — EPIC-P-01 Pack Certification Has No Detailed Interface

`EPIC-P-01` (Pack Certification & Marketplace) is listed in the epic index and mentioned in UNIFIED_IMPLEMENTATION_PLAN but has no corresponding LLD (`lld/LLD_P01_PACK_CERTIFICATION.md` does not exist) and no TDD test spec.

**Missing**:

- `lld/LLD_P01_PACK_CERTIFICATION.md`
- Certification test suite contract (what tests must every pack pass?)
- Marketplace API specification
- Pack signature and integrity verification spec

---

### 2.5 HIGH — No Multi-Domain Deployment Configuration Spec

KERNEL_PLATFORM_REVIEW.md §2.6 mentions four deployment models (Single-Domain, Multi-Domain, Domain-Specific, Hybrid) but no document specifies **how** to configure a multi-domain deployment: which ConfigMaps/Helm values determine which packs load, ordering constraints, shared vs. isolated database instances per pack.

**Missing document**: Should be either an extension to `LLD_K04_PLUGIN_RUNTIME.md` or a standalone `MULTI_DOMAIN_DEPLOYMENT_SPEC.md`.

---

### 2.6 MEDIUM — No Domain Pack Dependency Graph Resolution Spec

The `DomainPack` interface includes:

```typescript
readonly dependencies: DomainDependency[];
```

But there is no specification for how circular dependencies are detected, how version conflicts are resolved, or how shared sub-packs are deduplicated. K-04 Plugin Runtime needs a dependency graph section.

---

### 2.7 MEDIUM — TDD Specs Reference Capital-Markets Domain Exclusively

The TDD spec suite (660+ test cases) covers kernel modules well, but `tdd_spec_d01_oms_expanded_v2.1.md` is the only domain-layer TDD spec, and it tests capital-markets OMS. The framework for domain pack integration testing is not demonstrated for any other domain (banking, healthcare), leaving the claim that the platform supports multiple domains untested at spec level.

**Required**: At least one additional domain pack integration TDD spec (e.g., banking account-opening flow) to validate that the generic kernel actually supports a second domain without capital-markets assumptions.

---

### 2.8 MEDIUM — Glossary (`epics/GLOSSARY.md`) Still Finance-Centric

The glossary defines D-_ as: _"Domain subsystems implementing core business logic for capital markets operations."_ This should read: _"Domain Packs implementing industry-specific business logic."\* All OMS, EMS, PMS definitions follow, which is correct since they're capital-markets pack terms — but the category description itself is wrong.

Additionally, the glossary does not define: Domain Pack, T1/T2/T3 taxonomy, ContentPack, DomainManifest, CalendarDate (or equivalent), KernelModule, or PackCertification.

---

### 2.9 LOW — No Marketplace Governance Document

GENERIC_PLATFORM_EXPANSION_ANALYSIS.md references a "Domain Pack Marketplace" but there is no governance doc covering: pack submission process, review criteria, revocation of certified packs, versioning policy, SLA commitments by pack authors.

---

## 3. Correctness Review

### 3.1 CRITICAL — ADR-011 References External Archived Paths

ADR-011 (the canonical authority document) references implementation sources at:

```
../ghatana-archive-20260308-114326/.github/copilot-instructions.md
../ghatana/products/aep/platform/build.gradle.kts
../ghatana/platform/java/event-cloud/build.gradle.kts
```

These are relative paths pointing outside the repo to an archived snapshot. If the archive is moved or deleted, the primary authority document loses its most important references. The canonical copilot-instructions.md lives at `/home/samujjwal/Developments/ghatana/.github/copilot-instructions.md` — reference that directly or snapshot it internally.

**Required Fix**: Replace all `../ghatana-archive-*` references in ADR-011 with references to the live workspace paths or embed the relevant content as a quoted excerpt with `[source: ghatana copilot-instructions, version 2026-03-08]`.

---

### 3.2 HIGH — `DomainCapability` Enum in Core Interface Allows T3 (Arbitrary Code) Without Classification

`BusinessRuleReference.language` supports `"java" | "kotlin"` alongside `"rego"`. Java/Kotlin rule execution is effectively T3 (arbitrary executable code), not T2 (declarative rules). The spec does not distinguish this — a pack could declare a Java rule reference and have it treated with T2 isolation guarantees, exposing a security gap.

**Required Fix**:

- Split `BusinessRuleReference.language` into two subtypes: `T2RuleReference` (languages: rego, sql, dsl) and `T3RuleReference` (languages: java, kotlin, python, javascript)
- Require that T3 references go through K-04 Plugin Runtime sandboxing, not K-03 Rules Engine
- Document this in `DOMAIN_PACK_INTERFACE_SPECIFICATION.md` and update `LLD_K03_RULES_ENGINE.md`

---

### 3.3 HIGH — K-16 Ledger in Capital Markets Domain Pack (D epics) vs Generic Kernel

K-16 Ledger Framework is listed as a **Kernel** module (K-16) but EPIC-D-09 (Post-Trade & Settlement) and EPIC-D-16 reference "the ledger" in ways that assume it serves exclusively trading settlement. The LLD for K-16 should explicitly describe two usage modes: (1) generic immutable balance ledger for any domain, (2) the Capital Markets domain pack uses it for trade settlement — with all finance-specific configuration in pack, not kernel.

---

### 3.4 HIGH — K-05 FR10 Hardcodes Priority Lanes for Domain Modules

EPIC-K-05 FR10:

> "maintain priority lanes — critical producers (K-16 Ledger, **D-01 OMS**) are never throttled"

`D-01 OMS` is a capital-markets domain module. Hardcoding a domain module ID into a **kernel** functional requirement violates the Kernel ↔ Domain Pack separation principle. If the banking pack becomes critical, it has no way to declare its own priority lanes.

**Required Fix**: K-05 FR10 should read:

> "maintain priority lanes — producers may declare their criticality tier (CRITICAL / HIGH / NORMAL / BULK) via K-04 Plugin Runtime registration. CRITICAL producers are never throttled. Kernel modules (K-16 Ledger, K-07 Audit) default to CRITICAL. Domain packs declare their tier in their pack manifest."

---

### 3.5 MEDIUM — Technology Stack Inconsistencies Across Documents

Some older documents (C4 diagrams, archive content, some LLDs) still reference superseded technologies. While ADR-011 is the authority, readers encounter contradictions:

| Superseded Reference | Correct Per ADR-011      | Files to Check               |
| -------------------- | ------------------------ | ---------------------------- |
| Elasticsearch        | OpenSearch               | Older LLD files, C4 diagrams |
| MinIO                | Ceph / S3-compatible     | Some architecture specs      |
| GitHub Actions       | Gitea                    | Older pipeline descriptions  |
| Redux Toolkit        | Jotai + TanStack Query   | Some frontend specs          |
| Camunda / Temporal   | Ghatana Workflow runtime | archive/ docs                |

**Required Fix**: Audit all non-archive documents for these references. ADR-011 §5 already lists the exclusions — a grep pass can locate all violating references. Add a CI lint rule that fails on banned technology names outside `archive/`.

---

### 3.6 MEDIUM — `Siddhanta_Platform_Specification.md` Has Dual-Purpose Scope

This document starts with finance-specific Nepal spec content (SEBON, CDSC, NEPSE integrations, Bikram Sambat) but mid-document introduces generic platform concerns (batch processing framework in §8.8 with no Nepal-specific content). The two concerns leak into each other, making both harder to maintain.

**Required Fix**: Split the batch processing framework section into a separate kernel-level document (`LLD_K05_BATCH_PROCESSING.md` or an annex to EPIC-K-05), leaving `Siddhanta_Platform_Specification.md` as a purely Nepal-specific domain pack document.

---

### 3.7 LOW — `DOMAIN_PACK_DEVELOPMENT_GUIDE.md` Banking Example Uses "Siddhanta Team" as Author

The banking domain pack manifest example hardcodes:

```yaml
author:
  name: "[Your Name / Team]"
  organization: "[Your Organization]"
```

The template previously hardcoded `"Siddhanta Foundation"`, which is the Capital Markets domain pack author — not a generic placeholder. The corrected form uses bracketed placeholders to make the authoring intent clear for any domain pack implementor.

---

## 4. Extensibility Review

### 4.1 CRITICAL — `CalendarDate` / `DualDate` Type Is Not Extensible

Covered in §1.2. The `DualDate` type hardcodes `gregorian` and `bs` fields. As soon as a third calendar (e.g., Islamic Hijri for a Gulf market domain pack) is needed, the **core kernel type** must change.

**Priority**: This is the single most extensibility-breaking decision in the current codebase-level contracts.

---

### 4.2 HIGH — `requiredKernels` in Domain Pack Manifest Uses Unversioned String Codes

```yaml
requiredKernels:
  - "K-01"
  - "K-05"
```

These references carry no version constraints. If K-05 releases a breaking change, a domain pack built against K-05 v1.x has no way to declare incompatibility with K-05 v2.x.

**Required Fix**: Use semver constraint syntax:

```yaml
requiredKernels:
  - module: "K-01"
    version: ">=1.0.0 <2.0.0"
  - module: "K-05"
    version: ">=1.2.0"
```

This also makes K-04's dependency resolver actionable.

---

### 4.3 HIGH — No Domain Pack API Versioning Contract

The `APISchema` in `DOMAIN_PACK_INTERFACE_SPECIFICATION.md` defines:

```typescript
interface APISchema {
  readonly apiName: string;
  readonly version: string;
  readonly openapi: OpenAPISpec;
}
```

But there is no specification for what constitutes a "breaking" vs "non-breaking" API change for a domain pack, how consumers of the pack's APIs are notified of deprecations, or how the kernel enforces backward compatibility across pack upgrades. This is the same problem K-05 solves for events (Avro compatibility rules), but nothing equivalent exists for REST/gRPC domain pack APIs.

**Required addition**: A "Domain Pack API Evolution Policy" section in `DOMAIN_PACK_INTERFACE_SPECIFICATION.md` mirroring the event schema evolution rules in K-05 FR9.

---

### 4.4 HIGH — `LifecycleHooks` Interface Is Not Fully Specified

The `DomainPack` interface includes `readonly lifecycleHooks: LifecycleHooks` but this type is never defined in the spec. What hooks exist? `onInstall`, `onActivate`, `onUpgrade`, `onDeactivate`, `onUninstall`? What are their signatures? Can they be async? Do they have timeouts? This is a significant extension point that is declared but not contracted.

---

### 4.5 MEDIUM — K-03 Rules Engine T2 Policy Sandbox Not Extensible for New Languages

The Polyglot Rule Execution Engine (`POLYGLOT_RULE_EXECUTION_ENGINE.md`) defines a `RuleRuntime` interface — this is good. However, the `registerRuntime()` method is on `RuleExecutionManager`, which is a kernel service. The document doesn't clarify whether domain packs can register new language runtimes (e.g., a domain pack shipping a COBOL rule executor) or only the platform vendor can.

**Required clarification**: Document whether runtime registration is kernel-only or pack-extended. If pack-extended, this must go through K-04's T3 sandbox with appropriate resource limits.

---

### 4.6 MEDIUM — `DomainPack.domainType` Is Single-Valued

```typescript
readonly domainType: DomainType;  // single value
```

A pack that bridges Financial Services and Regulatory Reporting (a common real-world product) cannot express that it spans two domain types. Consider allowing:

```typescript
readonly domainTypes: DomainType[];  // one or more
```

---

### 4.7 LOW — K-13 Admin Portal Schema-Driven UI Has No Extension Contract for Domain Pack UI Components

The Admin Portal (K-13) is described as "schema-driven" and "dynamic forms" but `LLD_K13_ADMIN_PORTAL.md` doesn't specify how a domain pack registers its own UI components, dashboards, or admin forms. The `userInterface` field in `DomainPack` references files, but the runtime registration protocol with K-13 is undocumented.

---

## 5. Flexibility Review

### 5.1 CRITICAL — K-15 "Dual-Calendar" Is Named for a Two-Calendar World

Already addressed in §1.2 from a naming perspective. From a flexibility standpoint: the `DualDate` conversion API in `LLD_K15_DUAL_CALENDAR.md` exposes endpoints like `convertGregorianToBS()` and `convertBSToGregorian()`. For any new domain pack operating in a market with a different calendar (Hijri for Islamic banking, Thai solar calendar, Persian calendar), these explicit endpoints don't exist — they'd need new kernel endpoints.

**Required Fix**: The K-15 (renamed to Multi-Calendar Service) API should be:

```
GET /calendar/convert?from={calendarId}&to={calendarId}&date={value}
GET /calendar/business-day?calendar={calendarId}&date={value}&jurisdiction={id}
GET /calendar/fiscal-year?calendar={calendarId}&date={value}&jurisdiction={id}
```

New calendars are registered via T1 config packs, not new kernel endpoints.

---

### 5.2 HIGH — Domain Pack Manifest Has No Feature Flag Support

The `domainPack.yaml` manifest has no mechanism for declaring optional features that can be toggled per-tenant without changing the manifest version. In practice, a banking pack might have `CRYPTO_PAYMENTS` as an experimental feature that only some tenants enable.

**Required addition**:

```yaml
domainPack:
  featureFlags:
    - id: "CRYPTO_PAYMENTS"
      default: false
      description: "Enable cryptocurrency payment processing"
      requiredKernels:
        - module: "K-03"
          version: ">=2.0.0"
```

---

### 5.3 HIGH — K-02 Configuration Engine T1/T2/T3 Pack Taxonomy Has No "T0 Base Config" Layer

The configuration resolution hierarchy (Global → Jurisdiction → Tenant → User) is well-documented. But there is no `T0 / Base` layer representing the domain pack's own default configuration. Currently, a domain pack can provide `config/domain-config.json` but there's no specification for how K-02 picks this up as the base layer BEFORE the global configuration overrides it.

**Required addition**: Explicit "Domain Pack Defaults" as the lowest-priority layer in K-02's resolution chain:

```
Domain Pack Defaults < Global < Jurisdiction < Tenant < User
```

---

### 5.4 MEDIUM — Pack Certification Pipeline (P-01) Does Not Address Continuous Delivery of Pack Updates

The pack certification process implies a one-time gate (validate → certify → publish). Real-world domain packs will need continuous updates (bug fixes, regulatory changes). There is no specification for:

- Expedited certification for security patches
- Incremental certification (only re-test changed components)
- Rollout controls (canary deployment of pack updates to 5% tenants)

---

### 5.5 MEDIUM — `platformMinVersion` / `platformMaxVersion` Uses Strings Without Semver Grammar

```yaml
platformMinVersion: "2.0.0"
platformMaxVersion: "3.0.0"
```

This doesn't support pre-release versions, patch-level constraints (`>=2.1.3`), or exclusions (`!=2.2.0-rc.1`). It should use npm/Maven semver range syntax and be validated by K-04 at pack install time.

---

### 5.6 LOW — No Mechanism for Domain Packs to Extend Kernel Observability Dashboards

K-06 Observability is described as generic, but domain packs have domain-specific metrics (e.g., order fill rate for capital markets, bed occupancy rate for healthcare). There's no documented protocol for a domain pack to register its own Grafana dashboard or Prometheus alert rules through K-06, making K-06 inflexible for multi-domain deployments.

---

## 6. Document Authority Map Gaps

The `DOCUMENT_AUTHORITY_MAP.md` provides a good hierarchy, but is missing:

- `DOMAIN_PACK_INTERFACE_SPECIFICATION.md` — should be **Normative**
- `DOMAIN_PACK_DEVELOPMENT_GUIDE.md` — should be **Normative (Informative for examples)**
- `POLYGLOT_RULE_EXECUTION_ENGINE.md` — should be **Normative for K-03**
- `finance-ghatana-integration-plan.md` — currently at product root, should be classified as **Capital Markets Domain Pack** document
- `PLATFORM_REVIEW_REPORT.md` (this file) — should be **Review / Non-Normative**

---

## 7. Prioritized Action Items

### P0 — Must Fix (Blocks Generic Platform Identity)

| #    | Action                                                                                                                 | Files Affected                                            |
| ---- | ---------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| P0-1 | Assign the generic platform its own name; replace "Siddhanta" in all platform-level docs                               | README, all ADRs, all LLDs, all epics (kernel), TDD specs |
| P0-2 | Rename K-15 "Dual-Calendar" → "Multi-Calendar Service"; rename `DualDate` → `CalendarDate` with pluggable calendar map | ADR-004, LLD_K15, EPIC-K-15, TDD specs, event envelope    |
| P0-3 | Remove `timestamp_bs` / `timestamp_gregorian` from K-05 event envelope; use `CalendarDate` type                        | EPIC-K-05, LLD_K05, TDD specs EE group                    |
| P0-4 | Split `DomainCapability` enum — generic core only; domain-specific capabilities in each pack                           | `DOMAIN_PACK_INTERFACE_SPECIFICATION.md`, pack manifests  |
| P0-5 | Make `DomainType` an open string; provide `WellKnownDomainTypes` constants                                             | `DOMAIN_PACK_INTERFACE_SPECIFICATION.md`                  |

### P1 — Should Fix (Correctness and Security)

| #    | Action                                                                                                       | Files Affected                                                        |
| ---- | ------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------- |
| P1-1 | Split T2/T3 `BusinessRuleReference` to enforce sandbox boundary                                              | `DOMAIN_PACK_INTERFACE_SPECIFICATION.md`, LLD_K03, LLD_K04            |
| P1-2 | Fix K-05 FR10 — remove hardcoded `D-01 OMS` from kernel priority lanes; use per-pack criticality declaration | EPIC-K-05, LLD_K05                                                    |
| P1-3 | Fix ADR-011 external archive path references                                                                 | `adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md` |
| P1-4 | Add `source_domain_pack_id` field to K-05 event envelope                                                     | EPIC-K-05, LLD_K05, TDD specs                                         |
| P1-5 | Move `finance-ghatana-integration-plan.md` to capital-markets domain pack docs                               | File + all cross-references                                           |
| P1-6 | Add CI lint rule for banned technology names (Elasticsearch, MinIO, Camunda, etc.)                           | CI pipeline config                                                    |
| P1-7 | Define `LifecycleHooks` interface fully                                                                      | `DOMAIN_PACK_INTERFACE_SPECIFICATION.md`                              |

### P2 — Should Fix (Extensibility and Completeness)

| #     | Action                                                              | Files Affected                                                               |
| ----- | ------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| P2-1  | Add semver constraints to `requiredKernels` in domain pack manifest | `DOMAIN_PACK_INTERFACE_SPECIFICATION.md`, `DOMAIN_PACK_DEVELOPMENT_GUIDE.md` |
| P2-2  | Document inter-domain pack communication spec                       | New: `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md`                               |
| P2-3  | Document domain pack upgrade / hot-reload / rollback                | New: `DOMAIN_PACK_UPGRADE_RUNBOOK.md`                                        |
| P2-4  | Create `LLD_P01_PACK_CERTIFICATION.md`                              | New file                                                                     |
| P2-5  | Redesign K-15 API as calendar-agnostic endpoints                    | ADR-004, LLD_K15                                                             |
| P2-6  | Add Domain Pack API Evolution Policy to interface spec              | `DOMAIN_PACK_INTERFACE_SPECIFICATION.md`                                     |
| P2-7  | Add feature flags to domain pack manifest format                    | `DOMAIN_PACK_INTERFACE_SPECIFICATION.md`, `DOMAIN_PACK_DEVELOPMENT_GUIDE.md` |
| P2-8  | Add T0 base config layer (Domain Pack Defaults) to K-02             | EPIC-K-02, LLD_K02, K-02 TDD specs                                           |
| P2-9  | Add `domainTypes[]` (plural) to domain pack interface               | `DOMAIN_PACK_INTERFACE_SPECIFICATION.md`                                     |
| P2-10 | Fix Glossary D-\* category description; add missing terms           | `epics/GLOSSARY.md`                                                          |
| P2-11 | Create banking domain pack integration TDD spec                     | New: `tdd_spec_banking_integration_v1.md`                                    |
| P2-12 | Clarify K-13 Admin Portal UI registration protocol for domain packs | LLD_K13, `DOMAIN_PACK_INTERFACE_SPECIFICATION.md`                            |

### P3 — Housekeeping

| #    | Action                                                                                  | Files Affected                                                       |
| ---- | --------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| P3-1 | Rename product folder `app-plaform` → `app-platform`                                    | Folder + all references                                              |
| P3-2 | Rename `Siddhanta_Platform_Specification.md` → `Capital_Markets_Nepal_Specification.md` | File + cross-references                                              |
| P3-3 | Rename `siddhanta.md` → `capital_markets_nepal.md`                                      | File + cross-references                                              |
| P3-4 | Fix banking domain pack author example                                                  | `DOMAIN_PACK_DEVELOPMENT_GUIDE.md`                                   |
| P3-5 | Update Document Authority Map to include new docs                                       | `DOCUMENT_AUTHORITY_MAP.md`                                          |
| P3-6 | Add marketplace governance document                                                     | New: `MARKETPLACE_GOVERNANCE.md`                                     |
| P3-7 | Extract §8.8 batch processing from Siddhanta spec into LLD_K05 annex                    | `Siddhanta_Platform_Specification.md`, `LLD_K05_EVENT_BUS.md`        |
| P3-8 | Add K-06 observability extension contract for domain packs                              | `LLD_K06_OBSERVABILITY.md`, `DOMAIN_PACK_INTERFACE_SPECIFICATION.md` |

---

## 8. What Is Working Well

The following aspects are well-designed and should be preserved:

1. **T1/T2/T3 Plugin Taxonomy** — Clean, well-documented separation of config vs. rules vs. executable extensions.
2. **Multi-Domain Alignment Review** — The MULTI_DOMAIN_ARCHITECTURE_ALIGNMENT_REVIEW.md demonstrates that systematic cross-document consistency work has been done; the process is mature.
3. **K-07 Audit Framework** — Fully domain-agnostic: hash-chained, tamper-evident, configurable retention via domain extension points.
4. **K-02 Config Engine** — Hierarchical resolution (Global → Jurisdiction → Tenant → User) is genuinely generic and well-specified.
5. **ADR Process** — 11 ADRs covering all major decisions, with ADR-011 as a strong canonical authority.
6. **TDD-First Discipline** — 660+ test cases specified before implementation. This is excellent and should continue.
7. **Kernel ↔ Domain Separation** — The architectural principle is correct and consistently applied in LLD files. The violations found are at the naming and type-contract level, not the structural level.
8. **`DOMAIN_PACK_INTERFACE_SPECIFICATION.md`** — The contract structure is comprehensive and covers lifecycle, security, data model, business rules, workflows, and integrations in a single coherent interface.
9. **`POLYGLOT_RULE_EXECUTION_ENGINE.md`** — The `RuleRuntime` interface is genuinely extensible and well-architected.
10. **ARB Finding Traceability** — 20/20 ARB findings addressed and traceable to epics/LLDs. This quality of traceability is rare and valuable.

---

_Reviewed_: March 12, 2026 | _Next Review Suggested_: May 15, 2026 (post Milestone B)
