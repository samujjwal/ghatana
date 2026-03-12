# ALL-IN-ONE CAPITAL MARKETS PLATFORM

## Comprehensive Architecture, Operator Packs, Plugin Framework & Control Model

Version: 1.1\
Status: Architecture Baseline\
Date: March 10, 2026

Shared terminology and policy baseline: [Documentation_Glossary_and_Policy_Appendix.md](../archive/Documentation_Glossary_and_Policy_Appendix.md)\
Shared authoritative source register: [Authoritative_Source_Register.md](Authoritative_Source_Register.md)\
Reference style for time-sensitive external facts: `ASR-*` IDs from the shared source register.\
Strategic vision and Nepal instantiation: [siddhanta.md](siddhanta.md)\
Nepal-specific implementation specification: [Siddhanta_Platform_Specification.md](Siddhanta_Platform_Specification.md)\
Epic and kernel architecture reference: [capital_markets_platform_prompt_v2.1.md](capital_markets_platform_prompt_v2.1.md)\
Implementation stack authority: [../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md)

> **Scope note:** This document defines the global, jurisdiction-agnostic platform architecture. It is the normative reference for platform capabilities, operator models, plugin taxonomy, deployment modes, and control layers. Nepal-specific regulatory rules, exchange parameters, and market data belong in jurisdiction plugins and are specified in `Siddhanta_Platform_Specification.md`, not here.

---

# 1. Executive Overview

This document defines the complete, jurisdiction-agnostic architecture for a modular, regulator-grade, all-in-one capital markets platform. It is designed around a single governing principle: **one Generic Core, infinite jurisdictions — no core rewrites, ever.**

The platform is deployed as a pluggable, multi-tenant operating system for capital markets. A single production installation can serve multiple jurisdictions, multiple operator categories, and multiple asset classes simultaneously. Adding a new country means shipping a Jurisdiction Plugin pack — not forking the codebase. Adding a new operator type means composing an Operator Pack — not modifying the core.

**Supported operator categories (via Operator Packs):**

- Brokerage + Depository Participants
- Merchant Banking (Primary Markets)
- Investment Banking (ECM/DCM + M&A)
- Asset / Wealth Management
- Exchange / Clearing / CSD Utilities
- Fintech Retail Operators
- Regulator-Backed Infrastructure Platforms

**The three-layer architecture model:**

```
┌─────────────────────────────────────────────────────────────┐
│  LAYER 2 — Extension Packs (Pluggable, Versioned, Signed)   │
│  Jurisdiction Plugins │ Operator Packs │ AI Packs           │
│  Exchange/Depository Adapters │ Data Connectors             │
├─────────────────────────────────────────────────────────────┤
│  LAYER 1 — Domain Subsystems (First-Party Integrated)       │
│  OMS │ EMS │ PMS │ Market Data │ Pricing │ Risk             │
│  Compliance │ Surveillance │ Post-Trade │ Reg Reporting     │
│  Reference Data │ Corporate Actions                         │
├─────────────────────────────────────────────────────────────┤
│  LAYER 0 — Platform Kernel (Generic Core, Always Present)   │
│  Identity/AuthZ │ Config Engine │ Policy/Rules Engine       │
│  Plugin Runtime │ Event Bus + Event Store │ Observability   │
│  Audit Framework │ Data Governance │ AI Governance          │
│  Deployment Abstraction │ API Gateway │ Platform SDK        │
│  Admin Portal │ Secrets Management │ Dual-Calendar Service  │
│  Ledger Framework │ Distributed Tx Coordinator              │
│  Resilience Patterns Library │ DLQ Management & Replay     │
└─────────────────────────────────────────────────────────────┘
```

The Kernel (Layer 0) is the sole foundation: 19 modules (K-01 through K-19) covering every platform-wide concern. No Domain Subsystem (Layer 1) or Extension Pack (Layer 2) may re-implement any capability owned by the Kernel. Nepal is the first instantiation of this architecture; it is not the architectural boundary.

This document is **architecture-first and jurisdiction-agnostic**. Nepal-specific regulatory data, exchange parameters, and market metrics belong in the Nepal Jurisdiction Plugin and are specified in `Siddhanta_Platform_Specification.md`.

---

# 1.1 Kernel Architecture Reference

The 19 Platform Kernel modules are the non-negotiable foundation. Every module is Generic Core — no jurisdiction-specific logic anywhere in this layer. Domain Subsystems and Extension Packs consume kernel services exclusively through the Platform SDK (K-12).

| Module | Name                                            | Primary Responsibility                                                                                                                  |
| ------ | ----------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| K-01   | Identity & Access Management                    | Multi-tenant auth, RBAC+ABAC, National ID extension point, service-to-service identity (mTLS)                                           |
| K-02   | Configuration Engine                            | Versioned config schema registry, 6-level resolution hierarchy (Global → Jurisdiction → Operator → Tenant → Account → User), hot reload |
| K-03   | Policy / Rules Engine                           | Externalized policy evaluation; declarative DSL (OPA/Rego-compatible); compliance, fee, margin, circuit-breaker, routing rules          |
| K-04   | Plugin Runtime & SDK                            | Plugin registration, capability declaration, tier-aware sandbox isolation, signed plugin verification, compatibility enforcement        |
| K-05   | Event Bus, Event Store & Workflow Orchestration | Durable append-only event store; ordered delivery; event schema registry; saga/compensation; DLQ; replay                                |
| K-06   | Observability                                   | Unified structured logging, distributed tracing, metrics SDK, SLO/SLA tracking, tenant-scoped data residency                            |
| K-07   | Audit Framework                                 | Immutable tamper-evident audit log, cryptographic hash chaining, jurisdiction-configurable retention, regulator-ready export            |
| K-08   | Data Governance                                 | Data lineage, residency enforcement, retention/deletion lifecycle, KMS/encryption abstraction                                           |
| K-09   | AI Governance                                   | Model registry, prompt governance, evaluation framework, HITL override, explainability artifacts, drift detection and rollback          |
| K-10   | Deployment Abstraction                          | SaaS/dedicated/on-prem/hybrid; feature flags; zero-downtime upgrade; air-gapped support                                                 |
| K-11   | Unified API Gateway                             | Single entry point; routing, auth, rate-limiting, observability at gateway; domain modules register at startup                          |
| K-12   | Platform SDK                                    | Composite SDK exposing all kernel services; mandatory for all domain and plugin authors; versioned independently                        |
| K-13   | Admin & Operator Portal                         | Unified console: tenant management, config deployment, plugin lifecycle, health dashboards, audit viewer, dual-calendar tools           |
| K-14   | Secrets Management & Key Vault                  | Unified secrets/key abstraction; Vault/KMS/HSM provider selection; automatic rotation; customer-managed keys                            |
| K-15   | Dual-Calendar Service                           | BS/Gregorian first-class; authoritative conversion; calendar pack extension point; no domain-level calendar arithmetic                  |
| K-16   | Ledger Framework                                | Immutable double-entry ledger; no domain-local ledger; reconciliation tooling; jurisdiction-configurable COA                            |
| K-17   | Distributed Transaction Coordinator             | Saga orchestration; transactional outbox/inbox; idempotent command execution; partial-failure recovery                                  |
| K-18   | Resilience Patterns Library                     | Circuit breaker, retry, timeout, bulkhead, degraded-mode — policy-driven, consistent across all services                                |
| K-19   | DLQ Management & Event Replay                   | Dead-letter classification, RCA workflow, quarantine, safe replay with idempotency enforcement                                          |

**Build order constraint:** The Kernel (Layer 0) reaches Platform Stable before any Layer 1 or Layer 2 component begins implementation. This is enforced via Kernel Readiness Gates on all Domain Subsystem epics.

---

# 1.2 Extension Pack Taxonomy

All extensibility is governed by a strict three-tier taxonomy. Tier determines security review, sandbox requirements, benchmarking gates, and approval workflow. No code in a lower-trust tier may access kernel internals directly.

| Tier | Name             | Execution Model                                                                | Examples                                                                                                                                     | Code Execution?          |
| ---- | ---------------- | ------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------ |
| T1   | Config Packs     | Data-only; schema-validated on load; zero execution                            | Tax tables, market calendars, trading sessions, reporting templates, BS/Gregorian calendar data, margin rates, circuit-breaker thresholds    | No                       |
| T2   | Rule Packs       | Declarative logic in platform policy DSL; sandboxed by K-03 Rules Engine       | Compliance rules, validation rules, fee calculation rules, routing preferences, margin rules, circuit-breaker rules, tax withholding rules   | No                       |
| T3   | Executable Packs | Signed + sandboxed code; process/container isolation; resource limits enforced | Pricing models, risk models, execution strategies, AI model adapters, exchange/depository adapters, data connectors, operator workflow packs | Yes — signed + sandboxed |

**Jurisdiction Plugin composition:** A jurisdiction plugin is typically a bundle of T1 + T2 packs plus T3 exchange/depository adapters. The Generic Core never contains jurisdiction-specific logic. Adding a second jurisdiction (India, Bangladesh, etc.) means shipping a new bundle of packs — zero core modification.

---

# 2. Core Financial Operating System (FOS)

## 2.1 Identity & Relationship Graph

**Owner:** K-01 Identity & Access Management. National ID scheme is a K-01 extension point — the core stores a generic `national_id_ref` with `scheme` and `issuing_jurisdiction`; jurisdiction plugins define the specific identity type.

**Entity types:** Individual, Corporate, Beneficial Owner, Director/Signatory, Intermediary, Issuer, Counterparty, Exchange, Depository, Bank, Regulator.

**Platform capabilities:**

- KYC/KYB lifecycle state machine: `ApplicationReceived → IDVerification → RiskScoring → [AutoApproved | ManualReview | EnhancedDD] → Active → [Suspended | Closed]`
- Beneficial ownership graph with ownership percentage edges, PEP screening, and source-of-funds capture
- Risk rating engine — scoring model configured via T2 Rule Pack; AI-assisted scoring registered in K-09 AI Governance with mandatory HITL override path
- Consent & disclosure registry — consent type, legal basis, capture method, and audit-trailed lifecycle
- RBAC + ABAC governed by K-01; zero domain-level auth logic permitted anywhere
- Maker-checker enforcement — configurable which workflows require four-eyes approval via T2 Rule Pack; both maker and checker actions produce K-07 audit events

---

## 2.2 Reference Data & Master Data Management

**Owner:** D-11 Reference Data (Layer 1), consuming K-02, K-05, K-07, K-15, K-16.

**Mandatory master domains:**

| Domain                        | Notes                                                                                                                |
| ----------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| Instrument Master             | ISIN, symbol, sector classification, settlement cycle (plugin-driven — never hardcode T+n)                           |
| Issuer Master                 | Registration number, authorized/paid-up capital, promoter ratio, sector, primary regulator                           |
| Counterparty Master           | Legal entity, relationship graph, credit tier, exposure limits                                                       |
| Calendar Master               | Holiday lists, settlement exclusion days, auction calendars via T1 Calendar Pack; dual BS/Gregorian storage via K-15 |
| Corporate Actions Master      | Entitlement types, record-date logic, election windows                                                               |
| Tax Rules Master              | All rates and thresholds in T2 Rule Pack — no hardcoded values in core                                               |
| Margin & Haircut Rules Master | Initial margin, maintenance margin, eligible securities — all in T2 Rule Pack                                        |

**Controls:** Versioned reference data with effective-date support; mandatory maker-checker approval for overrides; full K-07 audit trail on every change; dual-calendar date storage on every date-bearing field enforced at K-15.

---

## 2.3 Ledger Architecture

**Owner:** K-16 Ledger Framework. No domain subsystem implements its own ledger.

Separate, reconciled layers:

1. **Operational Sub-Ledgers** — Securities positions, Cash, Margin, Fee/Commission, Corporate Actions, Tax (CGT/TDS/withholding), Settlement Obligations
2. **Accounting General Ledger (GL)** — IFRS / Local GAAP mapping; chart of accounts (jurisdiction-configurable via T1 Config Pack); journal posting engine; trial balance export
3. **Regulatory Ledgers** — Client asset segregation, net capital calculation, capital adequacy tracking, AML/CTF transaction register

**Ledger invariants enforced by K-16:** Immutable (append-only events); double-entry (every debit has a matching credit — ledger always balanced); replayable (full state reconstructible from event log); tamper-evident (cryptographic hash chaining via K-07 Audit Framework).

---

## 2.4 Pricing & Valuation Engine

**Owner:** D-05 Pricing Engine (Layer 1); custom valuation model execution in T3 Executable Packs registered in K-04 Plugin Runtime.

Capabilities: end-of-day pricing; intraday mark-to-market; NAV calculation (mutual funds, ETFs); price source hierarchy and arbitration; corporate action price adjustments; override workflow with maker-checker approval and K-07 audit trail.

**Extension points:** custom valuation models (Black-Scholes, local vol, stochastic vol, credit models) are T3 Executable Packs, independently versioned and signable. AI-driven pricing models are additionally registered in K-09 AI Governance with mandatory explainability artifacts.

---

## 2.5 Margin, Collateral & Credit Engine

**Owner:** D-06 Risk Engine (Layer 1); all margin thresholds externalized to T2 Rule Pack evaluated by K-03 Policy/Rules Engine.

Required features: exposure limits (client/group/instrument/sector) configurable via Rule Pack; concentration limits; collateral eligibility rules; haircut logic; margin call workflow with K-07 audit trail; shortfall handling; forced liquidation procedures with maker-checker gating; settlement liquidity forecasting.

**Plugin variability:** initial margin %, maintenance margin %, eligible securities lists, and forced-liquidation trigger thresholds all live in T2 Rule Pack — no hardcoded thresholds in the core Risk Engine.

---

## 2.6 Client Money & Asset Segregation

**Owner:** D-09 Post-Trade & Settlement (Layer 1), using K-16 Ledger Framework for ledger operations and K-07 Audit Framework for evidence.

Mandatory controls: segregated client bank accounts enforced at the ledger level; daily client money reconciliation against bank statements; withdrawal approval workflow with dual-authorization; prohibition of unauthorized transfers enforced by K-03 Policy Engine at command-validation layer; audit-ready client asset reports exportable on-demand for regulators.

---

## 2.7 Reconciliation Framework

**Owner:** D-09 Post-Trade & Settlement (Layer 1).

| Reconciliation Type          | Description                                                   |
| ---------------------------- | ------------------------------------------------------------- |
| Broker vs Exchange           | Trade confirmations vs exchange floorsheet/clearing statement |
| Broker vs Depository         | Internal position records vs CSD statement                    |
| Broker vs Bank               | Cash ledger vs bank statement                                 |
| Sub-Ledger vs GL             | All operational sub-ledgers balanced against accounting GL    |
| Client Asset vs Bank Balance | Segregated pool validated on daily basis                      |

Features: auto-matching engine with configurable tolerance thresholds (T1 Config Pack); break classification taxonomy; break aging and escalation workflow; resolution evidence retained in K-07 Audit Framework.

---

## 2.8 Workflow & Case Management

**Owner:** K-05 Event Bus + Workflow Orchestration (cross-subsystem coordination); K-07 Audit Framework (evidence); K-13 Admin & Operator Portal (queue management).

- BPM orchestration via K-05 saga patterns with explicit compensation steps on failure
- SLA tracking with configurable thresholds (T1 Config Pack) and K-06 Observability alerting
- Exception queues with priority classification and escalation rules (configurable via T2 Rule Pack)
- Regulator inspection pack export — structured evidence bundles from K-07, correlated by workflow trace ID
- Incident management linkage — correlated trace IDs across all participating subsystems via K-06 distributed tracing

---

# 3. Deployment Modes

The platform supports modular deployment. All deployment modes run the same Generic Core + Kernel stack. The deployment mode determines which Domain Subsystems and Extension Packs are activated, what licensing is required, and what participant governance model applies.

| Mode   | Name                                | Activated Capabilities                                                                                   | Typical Operator                      |
| ------ | ----------------------------------- | -------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| Mode 1 | Exchange Only                       | Matching engine, market data dissemination, market surveillance, trading sessions (T1 Calendar Pack)     | Exchange Utility                      |
| Mode 2 | Clearing House                      | Netting engine, margin calculation (T2 Rule Pack), default management, collateral tracking               | CCP/Clearing Utility                  |
| Mode 3 | Central Securities Depository (CSD) | Securities register, settlement finality, corporate actions master, participant account management       | CSD Utility                           |
| Mode 4 | Integrated Utility                  | Exchange + Clearing + CSD combined; single platform manifest                                             | Exchange/CCP/CSD conglomerate         |
| Mode 5 | Intermediary Platform               | OMS, EMS, PMS, compliance, post-trade — without exchange infrastructure                                  | Broker/Wealth Manager/Merchant Banker |
| Mode 6 | Regulator Portal                    | Supervisory dashboards, filing review, enforcement workflows, inspection pack access — read/observe only | Regulatory Authority                  |

Each mode requires: separate licensing configuration (T1 Config Pack), matching control model (T2 Rule Pack), and participant governance rules (T2 Rule Pack). No mode hardcodes jurisdiction parameters.

---

# 4. Operator Packs

Operator Packs (T3 Executable Packs) are the primary customization mechanism for operator-category-specific workflows, branded reports, custom notification templates, and role-specific dashboards. Each Operator Pack composes on top of the Generic Core; no core logic changes when a pack is installed, upgraded, or removed.

Every Operator Pack must declare: required permissions, consumed events, emitted events, data access scope, PII classes, performance SLO, and compatibility with the core version.

---

## Pack A: Broker + Depository Participant

**Minimum controls:**

- Client money segregation enforced at K-16 Ledger level
- Daily reconciliation (Broker vs Exchange, Broker vs Depository, Broker vs Bank)
- Margin monitoring with configurable thresholds (T2 Rule Pack)
- Trade confirmation controls — contract note timing configurable via T2 Rule Pack
- Restricted list enforcement — restricted securities list managed as T1 Config Pack

**Mandatory reconciliations:** Exchange trades, Depository positions, Bank balances, Client asset segregation pool.

**Required reports:** Daily trade register, Client holdings statement, Margin report, Regulatory capital report, Suspicious transaction log.

---

## Pack B: Merchant Banker

**Minimum controls:**

- Conflict of interest registry with mandatory disclosure workflow
- Due diligence checklist — checklist schema versioned as T1 Config Pack
- Prospectus validation against regulator-mandated schema (T2 Rule Pack)
- Escrow reconciliation — funds tracked via K-16 Ledger Framework
- Allocation audit trail — every allocation decision recorded as immutable K-07 audit event

**Mandatory reconciliations:** Bid vs allocation, Escrow funds vs subscriptions received, Allotment vs depository credits.

**Required reports:** Due diligence certificate, Allotment report, Issue summary report, Regulatory submission pack.

---

## Pack C: Investment Bank

**Minimum controls:**

- Information barrier enforcement — cross-wall request workflow with maker-checker gating
- Insider list management — persons list tracked as time-stamped K-07 audit entries
- Wall-crossing logs — every crossing event produces an immutable audit record
- Conflict committee workflow — committee decisions recorded with evidence artifacts

**Mandatory reconciliations:** Syndicate allocations, Fee split distribution, Deal expense reconciliation.

**Required reports:** Fairness opinion pack, Valuation documentation, Deal audit trail, Regulatory filing bundle.

---

## Pack D: Asset / Wealth Manager

**Minimum controls:**

- Suitability enforcement — suitability rules in T2 Rule Pack; overrides require documented justification captured in K-07
- Performance methodology documentation — methodology version pinned in Config Engine for reproducibility
- Portfolio construction constraints — constraint rules in T2 Rule Pack

**Mandatory reconciliations:** Portfolio valuation vs custodian statement, Fee billing vs ledger.

**Required reports:** Client periodic statement, Performance report, Fee invoice report, Tax summary pack.

---

## Pack E: Exchange / Clearing / CSD Utility

**Minimum controls:**

- Participant onboarding controls — eligibility rules in T2 Rule Pack
- Margin coverage monitoring — real-time exposure vs collateral via K-06 Observability hooks
- Default management plan — default waterfall defined as T2 Rule Pack; execution workflow with maker-checker
- Settlement cut-off enforcement — cut-off schedule in T1 Calendar Pack

**Mandatory reconciliations:** Clearing margin vs collateral held, Settlement obligations vs actual settlement, Participant exposure vs limit.

**Required reports:** Margin exposure report, Settlement completion report, Market surveillance alerts.

---

## Pack F: Fintech Retail Operator

**Minimum controls:**

- Digital onboarding verification — National ID adapter via K-01 extension point
- Fraud detection — AI model registered in K-09 AI Governance with HITL override
- Simplified suitability checks — rules in T2 Rule Pack

**Mandatory reconciliations:** Broker vs Exchange (trade side), Client ledger vs bank settlement.

**Required reports:** Retail portfolio statement, Fee transparency summary, Risk disclosure log.

---

## Pack G: Regulator-Backed Platform

**Minimum controls:**

- Licensing registry — active licenses maintained as reference data with effective-date tracking
- Breach tracking — breach events recorded in K-07; escalation rules in T2 Rule Pack
- Inspection workflow — systematic evidence collection from K-07 Audit Framework

**Mandatory reconciliations:** Filing completeness checks, Cross-entity exposure monitoring.

**Required reports:** Intermediary compliance dashboard, Suspicious activity register, Enforcement action log.

---

# 5. Plugin Framework

All extensibility is delivered through the three-tier plugin taxonomy defined in Section 1.2. The Plugin Runtime (K-04) governs the full plugin lifecycle: registration, capability declaration, tier-aware sandbox enforcement, signed verification, compatibility matrix management, and rollback.

## 5.1 Plugin Contract — Mandatory Manifest Schema

Every plugin (T1, T2, or T3) must declare a conforming manifest. The schema below is the normative contract enforced by K-04 Plugin Runtime on installation:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "CapitalMarketsPlugin",
  "type": "object",
  "required": [
    "name",
    "version",
    "tier",
    "required_permissions",
    "compatibility"
  ],
  "properties": {
    "name": { "type": "string", "description": "Unique plugin identifier" },
    "version": {
      "type": "string",
      "description": "Semantic version (major.minor.patch)"
    },
    "tier": {
      "type": "string",
      "enum": ["T1", "T2", "T3"],
      "description": "Trust tier — determines sandbox and approval requirements"
    },
    "required_permissions": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Permissions requested from K-01 AuthZ"
    },
    "consumed_events": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Event topics this plugin subscribes to via K-05"
    },
    "emitted_events": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Event topics this plugin publishes via K-05"
    },
    "data_access": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Data domains this plugin reads or writes"
    },
    "command_types_allowed": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Commands this plugin may submit; empty for T1/T2"
    },
    "pii_classes": {
      "type": "array",
      "items": { "type": "string" },
      "description": "PII categories handled — drives K-08 data governance policies"
    },
    "performance_slo": {
      "type": "string",
      "description": "Plugin-declared p99 latency SLO — verified at certification"
    },
    "compatibility": {
      "type": "string",
      "description": "Minimum core version (e.g., Core>=1.0.0)"
    },
    "observability": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Metrics this plugin emits to K-06 Observability stack"
    },
    "jurisdiction_scope": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Jurisdictions this pack applies to; empty means all"
    },
    "ai_governance_required": {
      "type": "boolean",
      "description": "If true, AI model used must be registered in K-09 AI Governance"
    }
  }
}
```

---

## 5.2 Example Plugin Declaration — IPO Book-Building Module (T3)

```json
{
  "name": "IPO_BookBuilding_Module",
  "version": "1.0.0",
  "tier": "T3",
  "required_permissions": ["READ_ISSUER", "READ_INVESTOR", "SUBMIT_ALLOCATION"],
  "consumed_events": ["BidPlaced", "IssuePriceFixed", "BidWindowClosed"],
  "emitted_events": [
    "AllocationGenerated",
    "AllotmentConfirmed",
    "RefundInitiated"
  ],
  "data_access": ["Issuer", "Investor", "Bid", "AllocationRecord"],
  "command_types_allowed": [
    "SubmitAllocationCommand",
    "ConfirmAllotmentCommand"
  ],
  "pii_classes": ["InvestorIdentity", "InvestorBankAccount"],
  "performance_slo": "p99 < 500ms per allocation batch of 10,000 investors",
  "compatibility": "Core>=1.0.0",
  "observability": [
    "allocation_latency_ms",
    "error_rate",
    "bid_processing_tps"
  ],
  "jurisdiction_scope": [],
  "ai_governance_required": false
}
```

---

## 5.3 Example Config Pack Declaration — Nepal Trading Calendar (T1)

```json
{
  "name": "nepal_trading_calendar_2083",
  "version": "2083.1.0",
  "tier": "T1",
  "required_permissions": [],
  "consumed_events": [],
  "emitted_events": [],
  "data_access": [],
  "command_types_allowed": [],
  "pii_classes": [],
  "performance_slo": "Loaded within 2s at startup; no runtime execution",
  "compatibility": "Core>=1.0.0",
  "observability": [],
  "jurisdiction_scope": ["NPL"],
  "ai_governance_required": false
}
```

---

# 6. Plugin Conformance & Certification

Every plugin must pass the following gates before production enablement. The K-04 Plugin Runtime enforces gate status — plugins blocked at any gate cannot be installed in a production tenant.

## 6.1 Technical Gates

| Gate                      | Requirement                                                                                        |
| ------------------------- | -------------------------------------------------------------------------------------------------- |
| Contract validation       | Manifest schema validates against Section 5.1; version is semantic; tier is declared               |
| No direct ledger mutation | T3 Executable Packs may not call K-16 Ledger directly — all ledger ops via K-12 SDK `LedgerClient` |
| Idempotency verified      | Every command the plugin emits is idempotent with respect to duplicate delivery by K-05            |
| Load test                 | Plugin tested at 120% of declared performance SLO under sustained load                             |
| Security scan             | Dependency vulnerability scan; no known CVEs above CVSS 7.0 unmitigated; static analysis pass      |
| Signature verification    | T3 packs signed by the authorised publisher key; K-04 verifies on every install and upgrade        |

## 6.2 Compliance Gates

| Gate               | Requirement                                                                                                          |
| ------------------ | -------------------------------------------------------------------------------------------------------------------- |
| Audit logs         | Plugin emits K-07-compatible audit events for every state-changing operation                                         |
| PII classification | All PII categories handled are declared in manifest `pii_classes`; K-08 data-governance policies applied accordingly |
| Data retention     | Plugin-created data is tagged with retention metadata per K-08 policies and jurisdiction Config Pack                 |
| Role-based access  | All permission requests in manifest are reviewed and minimally scoped                                                |
| AI governance      | If `ai_governance_required: true`, every AI model used must be registered in K-09 before certification completes     |

## 6.3 Operational Gates

| Gate                | Requirement                                                                                   |
| ------------------- | --------------------------------------------------------------------------------------------- |
| Rollback strategy   | Rollback procedure documented and tested; K-04 Plugin Runtime can roll back without data loss |
| Monitoring coverage | All declared `observability` metrics are visible in K-06 within 60s of plugin startup         |
| Error handling      | All error paths emit structured events to K-05 DLQ; no silent failures                        |
| Documentation       | API contracts, configuration parameters, and known limitations documented                     |

## 6.4 Certification Sequence

1. Static analysis and dependency scan
2. Security review (T3 packs additionally require threat modelling sign-off)
3. Functional conformance test against the platform's plugin conformance test suite
4. Performance load test at declared SLO + 20%
5. Compliance audit approval (Audit, PII, data retention gates)
6. Production enablement approval — two approvers from platform governance team

---

# 7. Versioning & Compatibility Strategy

The platform uses semantic versioning (`major.minor.patch`) for the Generic Core, all Kernel modules, and all Extension Packs. Versions are tracked in the Platform Manifest (K-PU-004) — the single source of truth for what is installed and at what version.

| Rule                        | Detail                                                                                                                                      |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Backward compatibility      | Within a major version, all API contracts and event schemas are backward-compatible                                                         |
| Breaking changes            | Major version bump required; migration guide mandatory; deprecation notice minimum 90 days                                                  |
| Plugin compatibility matrix | K-04 Plugin Runtime enforces the `compatibility` constraint in every plugin manifest; incompatible packs are rejected at install time       |
| Schema migration tooling    | Core provides migration tooling for every schema change affecting event schemas, API contracts, or Config Pack schemas                      |
| Deprecation policy          | Deprecated APIs/events are marked in the Platform SDK ChangeLog; warnings emitted to K-06 Observability; removed only at next major version |
| Air-gapped delivery         | Plugin bundles and Core upgrade packages are signed, versioned, and deliverable via secure offline channel for on-prem deployments          |

---

# 8. Governance & Control Layers

The platform enforces a layered control model. Every state change flows through each layer in strict sequence:

| Layer | Name               | Owner                 | Responsibility                                                                                          |
| ----- | ------------------ | --------------------- | ------------------------------------------------------------------------------------------------------- |
| 1     | Command Validation | K-01, K-03            | Authentication, authorization (RBAC/ABAC), schema validation, idempotency key check                     |
| 2     | Policy Enforcement | K-03 Rules Engine     | T2 Rule Pack evaluation — compliance pre-checks, fee calculation, eligibility, maker-checker gate       |
| 3     | Ledger Mutation    | K-16 Ledger Framework | Double-entry posting; balance validation; no direct mutations outside this layer                        |
| 4     | Event Publication  | K-05 Event Bus        | Immutable event emitted to Event Store; downstream consumers notified; saga coordination if needed      |
| 5     | Audit Capture      | K-07 Audit Framework  | Immutable audit event written with full input context, actor identity, outcome, dual-calendar timestamp |
| 6     | Regulatory Export  | K-07 + K-10           | Structured evidence bundles produced on demand for regulators; data residency enforced by K-08          |

**Non-negotiable invariants:**

- No state-mutating operation may bypass Layer 1 and Layer 2.
- No financial ledger entry may be created outside Layer 3 (K-16).
- Every Layer 3 mutation produces a Layer 4 event — no silent state changes.
- Every Layer 4 event produces a Layer 5 audit record — audit is not optional.
- Layer 6 exports are cryptographically verified to be derived from the unaltered audit log.

---

# 9. Non-Functional Requirements (Platform-Wide)

These NFRs apply to the Generic Core and all first-party Domain Subsystems. Extension Packs declare their own performance SLOs in their manifests; the platform enforces that pack SLOs do not exceed platform NFRs under combined load.

| NFR Category                | Requirement                                                                                          | Enforcement Mechanism                                              |
| --------------------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| **Order throughput**        | ≥ 5,000 orders/second sustained; ≥ 10,000 orders/second burst (60s)                                  | Load test gate in K-04 certification; K-06 real-time monitoring    |
| **Order latency**           | p99 < 50ms end-to-end (order submission → risk check → EMS routing) under sustained load             | K-06 SLO tracking; alerting if p99 > 40ms                          |
| **Event bus throughput**    | ≥ 100,000 events/second sustained across all topics                                                  | K-05 performance benchmark; K-06 alerting                          |
| **Audit write latency**     | p99 < 10ms for immutable audit event write (K-07)                                                    | K-06 SLO; audit write failures are P0 platform incidents           |
| **API gateway latency**     | p99 < 20ms for authenticated routing at K-11 (excluding upstream domain processing)                  | K-06; gateway-level SLO alert                                      |
| **Reconciliation SLA**      | Daily reconciliation completes within 60 minutes of cut-off for all sub-ledger types                 | K-06 SLO; escalation to K-13 operator portal if exceeded           |
| **Recovery objectives**     | RTO ≤ 4 hours (SaaS); RTO ≤ 8 hours (on-prem)                                                        | K-10 deployment runbook; tested annually                           |
| **Data residency**          | 100% of regulated data tagged and routed to compliant storage per jurisdiction at creation           | K-08 data governance; violation is P0 incident                     |
| **Plugin isolation**        | T3 plugin failure must not cascade to Generic Core or other plugins; circuit breaker trips within 3s | K-18 Resilience Patterns; K-04 sandbox isolation                   |
| **Upgrade target**          | Zero-downtime upgrade for SaaS tenants; ≤ 30-minute maintenance window for on-prem                   | K-10 upgrade strategy; tested on every major release               |
| **Cryptographic integrity** | All audit log entries verifiable via hash-chain within 30s of write                                  | K-07 hash-chain verification tool; periodic automated verification |

---

# 10. Conclusion

Project Siddhanta's global platform architecture delivers on a single promise: **one Generic Core, any jurisdiction, any operator category, any asset class — with no core rewrites.**

The architecture achieves this through strict layering. The 19-module Kernel provides every platform-wide service — identity, configuration, rules evaluation, events, observability, audit, ledger, AI governance, and more. Domain Subsystems (Layer 1) consume these services exclusively via the Platform SDK without reimplementing any kernel concern. Extension Packs (Layer 2) extend and specialize behaviour without touching the core.

For operators, this means: choosing a new jurisdiction is a configuration deployment, not an engineering engagement. For regulators, this means: every action is immutably auditable, every rule is traceable to its declaration, and evidence packs are always on demand. For investors, this means: the same control guarantees — segregated funds, maker-checker approvals, immutable evidence — regardless of which operator they transact through.

**World-class quality commitments this architecture maintains at every release:**

- **Investor protection** — ledger-enforced client asset segregation, risk-gated order flow, and suitability-checked product distribution
- **Market integrity** — AI-native surveillance, deterministic compliance pre-checks, and tamper-evident audit chains
- **Operational resilience** — circuit breakers, bulkheads, saga compensation, DLQ replay, and zero-downtime upgrade targets
- **Strict segregation of duties** — maker-checker on all regulated workflows; dual-identity enforcement at the audit layer
- **Audit-grade evidence** — immutable, hash-chained, regulator-exportable from any point in the event log
- **Controlled extensibility** — signed T3 packs with process isolation; T1/T2 packs with zero code execution; all extensions governed by K-04
- **Multi-jurisdiction readiness** — zero Generic Core modification required to add a new jurisdiction; Nepal is instantiation one, not the ceiling

The platform is designed to be the last capital markets infrastructure investment an operator, regulator, or market infrastructure provider ever needs to make.
