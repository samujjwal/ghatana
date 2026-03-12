# C4 DIAGRAM PACK — ARCHITECTURE REVIEW REPORT
## Project Siddhanta: All-In-One Capital Markets Platform

**Reviewer:** Architecture Review (Automated)  
**Date:** March 6, 2026  
**Status:** Review Complete — Action Required  
**Scope:** All 5 C4 diagram documents  
**Reference NFRs:** ≤2 ms internal / ≤12 ms e2e P99 order latency, 50K sustained / 100K burst TPS, 99.999% availability, 10-year data retention

**Historical Note (March 8, 2026):** This report captures a review snapshot before subsequent cleanup. Some issues referenced here were later resolved in the diagram and index set.

---

## EXECUTIVE SUMMARY

The C4 diagram pack is **structurally sound at the top two levels (C1–C2)** and demonstrates strong coverage of cross-cutting architecture primitives (T1/T2/T3 Content Pack taxonomy, dual-calendar, hardened NFR targets). However, **C3 and C4 levels are critically incomplete** — only 3 of 28 containers have component diagrams and only 2 have code diagrams. Of the platform's 42 epics, roughly **19 are explicitly represented** in diagram artifacts and **23 have no direct visual representation** at any C4 level. Kernel modules K-08 through K-14 are almost entirely absent from all diagrams. There are also **internal NFR inconsistencies** across sections within the same document.

| Verdict | Count |
|---------|-------|
| 🔴 Critical (Blocks Architecture Baseline) | 4 |
| 🟠 High (Must fix before Sprint 1) | 8 |
| 🟡 Medium (Should fix in next revision) | 9 |
| 🟢 Low (Informational / cosmetic) | 5 |
| **Total Findings** | **26** |

---

## 1. FILE-BY-FILE SUMMARY

### 1.1 C4_C1_CONTEXT_SIDDHANTA.md (838 lines)

| Attribute | Detail |
|-----------|--------|
| **C4 Level** | C1 — System Context |
| **Sections** | 15 (Legend → Approval) |
| **Mermaid Diagrams** | 1 |
| **Actors** | 7 human, 3 organizational |
| **External Systems** | 13 |
| **NFR Coverage** | ✓ 99.999%, ✓ ≤2 ms/≤12 ms, ✓ 50K/100K TPS, ✓ 10-year retention |
| **T1/T2/T3** | ✓ Section 8.4 |
| **Dual-Calendar** | ✓ Section 8.4 |

**Strengths:**
- Comprehensive external system landscape with 13 integration points
- Detailed "What Breaks This" section (6 major categories, 15+ scenarios)
- Trust boundary model (Internet / Partner / Regulatory / Core)
- T1/T2/T3 and dual-calendar explicitly documented in Section 8.4
- Quality attributes (Section 11) align with hardened NFR targets

**Issues Found:** 5 (see §3 for details)

---

### 1.2 C4_C2_CONTAINER_SIDDHANTA.md (1,102 lines)

| Attribute | Detail |
|-----------|--------|
| **C4 Level** | C2 — Container |
| **Sections** | 10 (Legend → Revision History) |
| **Mermaid Diagrams** | 1 |
| **Containers** | 28 across 6 layers |
| **NFR Coverage** | ✓ ≤2 ms/≤12 ms, ✓ 50K/100K TPS, ✓ 10-year retention (6 instances) |
| **T1/T2/T3** | ✓ Section 3.8 (table) |
| **Dual-Calendar** | ✓ Section 3.8 (K-15 container description) |

**Strengths:**
- Six-layer decomposition is clear and well-organized
- 10-year retention mentioned 6 times across data-layer containers (Operational DB, Ledger DB, Analytics DB, Object Storage, Search Engine hot/cold, Logging hot/cold)
- T1/T2/T3 table with kernel container mapping (K-02, K-03, K-04)
- K-15 Dual-Calendar explicitly described as kernel container
- Comprehensive failure-mode catalog (7 categories, 20+ scenarios)

**Issues Found:** 10 (see §3 for details)

---

### 1.3 C4_C3_COMPONENT_SIDDHANTA.md (1,081 lines)

| Attribute | Detail |
|-----------|--------|
| **C4 Level** | C3 — Component |
| **Sections** | 14 (Legend → Revision History) |
| **Mermaid Diagrams** | 3 (OMS, PMS, CMS) |
| **Services Covered** | 3 of 28 containers |
| **NFR Coverage** | Partial (mentioned in invariants §9.0) |
| **T1/T2/T3** | ✓ Section 9.0 invariant |
| **Dual-Calendar** | ✓ Section 9.0 invariant |

**Strengths:**
- Three well-detailed component diagrams (OMS: 17 components, PMS: 12, CMS: 14)
- CQRS / event-sourcing / hexagonal patterns are clearly applied
- Cross-cutting §9.0 formally requires T1/T2/T3 and dual-calendar
- K-05 event envelope invariant with `timestamp_bs`, `timestamp_gregorian`, `tenant_id`

**Issues Found:** 6 (see §3 for details)

---

### 1.4 C4_C4_CODE_SIDDHANTA.md (1,280 lines)

| Attribute | Detail |
|-----------|--------|
| **C4 Level** | C4 — Code/Class |
| **Sections** | 11 (Legend → Revision History) |
| **Mermaid Diagrams** | 2 (Order class diagram, Position class diagram) |
| **Domains Covered** | 2 of 28 containers |
| **Code Examples** | 20+ Java snippets |
| **NFR Coverage** | Minimal (no latency/TPS in code) |
| **T1/T2/T3** | ✓ Section 7.1 invariant |
| **Dual-Calendar** | ✓ Section 7.1 invariant (createdAtBs / updatedAtBs) |

**Strengths:**
- Full Java code examples with proper BigDecimal handling
- Optimistic locking (`@Version`), pessimistic locking (`lockForUpdate`)
- Event sourcing pattern with transactional outbox
- Dual-calendar required in entity invariants (§7.1, item 2)
- T1/T2/T3 required in entity invariants (§7.1, item 6)
- K-05 event envelope required in entity invariants (§7.1, item 7)

**Issues Found:** 5 (see §3 for details)

---

### 1.5 C4_DIAGRAM_PACK_INDEX.md (524 lines)

| Attribute | Detail |
|-----------|--------|
| **C4 Level** | Index / Master Navigation |
| **Sections** | 12+ (Overview → Quick Start) |
| **NFR Coverage** | ✓ Header statement |
| **T1/T2/T3** | ✓ Header statement |
| **Dual-Calendar** | ✓ Header statement |
| **Epic Count** | References 42 epics |

**Strengths:**
- Audience-based navigation (Executive → Developer → DevOps)
- Task-based navigation (Feature, Troubleshooting, Onboarding, Security, Performance)
- Statistics table with line counts per file
- Cross-references to ARB review, regulatory doc, LLDs, epics
- Cross-cutting concerns section summarizing security, observability, resilience, data integrity
- K-17/K-18/K-19 explicitly referenced in resilience section

**Issues Found:** 2 (see §3 for details)

---

## 2. COMPLETENESS ANALYSIS

### 2.1 Epic Coverage (42 Epics → C4 Diagrams)

#### Kernel Modules (K-01 to K-19)

| Epic | Name | C1 | C2 | C3 | C4 | Status |
|------|------|----|----|----|----|--------|
| K-01 | IAM | ✓ (actor auth) | ✓ (Identity Service) | ✗ | ✗ | 🟠 Missing C3/C4 |
| K-02 | Configuration Engine | ✓ (ref in §4.3) | ✓ (ref in T1) | ✗ | ✗ | 🟠 No container box in Mermaid |
| K-03 | Rules Engine | ✗ | ✓ (ref in T2) | ✗ | ✗ | 🟠 No container box in Mermaid |
| K-04 | Plugin Runtime | ✗ | ✓ (ref in T3) | ✗ | ✗ | 🟠 No container box in Mermaid |
| K-05 | Event Bus | ✗ | ✓ (Event Bus/Kafka) | ✗ | ✗ | 🟡 Container exists but K-05 abstraction above Kafka not shown |
| K-06 | Observability | ✗ | ✓ (3 containers) | ✗ | ✗ | 🟢 Adequate at C2 |
| K-07 | Audit Framework | ✗ | ✗ | ✗ | ✗ | 🔴 **Not represented at any level** |
| K-08 | Data Governance | ✗ | ✗ | ✗ | ✗ | 🔴 **Not represented at any level** |
| K-09 | AI Governance | ✗ | ✓ (ref in ext AI Services) | ✗ | ✗ | 🟠 Only as external system |
| K-10 | Deployment Abstraction | ✗ | ✗ | ✗ | ✗ | 🔴 **Not represented at any level** |
| K-11 | API Gateway | ✗ | ✓ (API Gateway) | ✗ | ✗ | 🟡 Container exists, K-11 label missing |
| K-12 | Platform SDK | ✗ | ✗ | ✗ | ✗ | 🔴 **Not represented at any level** |
| K-13 | Admin Portal | ✗ | ✗ | ✗ | ✗ | 🟠 Missing entirely |
| K-14 | Secrets Management | ✗ | ✓ (text ref only) | ✗ | ✗ | 🟠 Mentioned in cross-cutting, no container |
| K-15 | Dual-Calendar | ✓ (§8.4) | ✓ (§3.8) | ✓ (§9.0) | ✓ (§7.1) | 🟢 Well documented textually, no container box |
| K-16 | Ledger Framework | ✗ | ✓ (Ledger Service) | ✗ | ✗ | 🟠 Missing C3/C4 |
| K-17 | Distributed Transaction Coordinator | ✗ | ✓ (ref in Index) | ✗ | ✗ | 🟠 No container box |
| K-18 | Resilience Patterns | ✗ | ✓ (ref in Index) | ✗ | ✗ | 🟡 Library, not a container |
| K-19 | DLQ Management | ✗ | ✓ (ref in Index) | ✗ | ✗ | 🟡 Operational concern |

#### Domain Modules (D-01 to D-14)

| Epic | Name | C1 | C2 | C3 | C4 | Status |
|------|------|----|----|----|----|--------|
| D-01 | OMS | ✓ | ✓ | ✓ | ✓ | 🟢 **Fully covered** |
| D-02 | EMS | ✓ (FIX Gateway) | ✓ (FIX Gateway) | Partial (FIX Adapter in OMS) | ✗ | 🟠 Merged into OMS, no standalone |
| D-03 | PMS | ✓ | ✓ | ✓ | ✓ | 🟢 **Fully covered** |
| D-04 | Market Data | ✓ (ext system) | ✓ (Integration Hub ref) | ✗ | ✗ | 🟠 No dedicated container or C3 |
| D-05 | Pricing Engine | ✗ | ✓ (Pricing Service) | ✗ | ✗ | 🟠 Missing C3/C4 |
| D-06 | Risk Engine | ✗ | ✓ (RMS) | ✗ | ✗ | 🟠 Missing C3/C4 |
| D-07 | Compliance | ✗ | ✓ (Compliance Engine) | ✗ | ✗ | 🟠 Missing C3/C4 |
| D-08 | Surveillance | ✗ | ✗ | ✗ | ✗ | 🔴 **Not represented at any level** |
| D-09 | Post-Trade | ✗ | ✓ (Reconciliation) | ✗ | ✗ | 🟡 Partially via Reconciliation |
| D-10 | Regulatory Reporting | ✗ | ✗ | ✗ | ✗ | 🟠 Merged into Compliance, not isolated |
| D-11 | Reference Data | ✗ | ✗ | ✗ | ✗ | 🟠 Missing entirely |
| D-12 | Corporate Actions | ✗ | ✗ | ✓ (within PMS) | ✓ (within PMS) | 🟡 Embedded in PMS, not standalone |
| D-13 | Client Money Recon | ✗ | ✓ (CMS) | ✓ (CMS) | ✗ | 🟡 Partially via CMS |
| D-14 | Sanctions Screening | ✗ | ✗ | ✗ | ✗ | 🟠 Missing entirely |

#### Other Epics

| Epic | Name | Represented? | Status |
|------|------|-------------|--------|
| PU-004 | Platform Manifest | ✗ | 🟡 Operational concern |
| W-01 | Workflow Orchestration | ✓ (Camunda) | 🟢 At C2 |
| W-02 | Client Onboarding | ✗ | 🟠 Missing |
| P-01 | Pack Certification | ✗ | 🟡 Operational concern |
| T-01 | Integration Testing | ✓ (C3 §12) | 🟢 Testing strategy section |
| T-02 | Chaos Engineering | ✓ (Index ref) | 🟡 Reference only |
| O-01 | Operator Workflows | ✓ (Operator Portal) | 🟢 At C2 |
| R-01 | Regulator Portal | ✓ (Regulator Portal) | 🟢 At C2 |
| R-02 | Incident Notification | ✗ | 🟠 Missing |

### 2.2 Coverage Summary

| Category | Total | Represented (≥1 C4 level) | Fully Covered (C1→C4) | Missing Entirely |
|----------|-------|---------------------------|----------------------|-----------------|
| Kernel (K-01–K-19) | 19 | 12 | 0 | **4** (K-07, K-08, K-10, K-12) |
| Domain (D-01–D-14) | 14 | 10 | **2** (D-01, D-03) | **2** (D-08, D-11) |
| Other | 9 | 5 | 0 | 2 |
| **Total** | **42** | **27** (64%) | **2** (5%) | **8** (19%) |

---

## 3. DETAILED FINDINGS

### Finding 1 — 🔴 CRITICAL: C3/C4 Coverage Gap

**Files:** C4_C3_COMPONENT_SIDDHANTA.md, C4_C4_CODE_SIDDHANTA.md  
**Category:** Completeness  
**Section:** Global

**Description:**  
C3 diagrams exist for only **3 of 28 containers** (OMS, PMS, CMS). C4 diagrams exist for only **2 domains** (Order, Position). Zero kernel modules have C3 or C4 decomposition. This means 89% of the container landscape has no component-level visibility, which directly contradicts the C4 methodology requirement for drill-down from every container.

**Impact:** Developers implementing K-01 (IAM), K-16 (Ledger), D-05 (Pricing), D-06 (Risk), D-07 (Compliance), D-08 (Surveillance), and 20+ other modules have no architecture guidance below the container level.

**Recommendation:**  
Prioritize C3 diagrams for the following high-risk containers (minimum viable):
1. Ledger Service (K-16) — immutable ledger is a regulatory requirement
2. Risk Management Service (D-06) — margin calculations directly affect client money safety
3. Compliance Engine (D-07) — AML/surveillance is a regulatory mandate
4. Identity Service (K-01) — authentication/authorization is security-critical
5. Configuration Engine (K-02) — drives T1/T2/T3 content pack model

---

### Finding 2 — 🔴 CRITICAL: Kernel Modules K-07, K-08, K-10, K-12 Absent from All Diagrams

**Files:** All 5 C4 documents  
**Category:** Completeness  
**Section:** N/A (absent)

**Description:**  
Four kernel modules are **not represented at any C4 level**:
- **K-07 Audit Framework** — critical for 10-year retention and regulatory compliance
- **K-08 Data Governance** — critical for data residency, PII handling, GDPR
- **K-10 Deployment Abstraction** — critical for multi-cloud, multi-region operations
- **K-12 Platform SDK** — critical for third-party plugin development

These are not mentioned in any diagram, any container boundary, or any cross-cutting section.

**Impact:** K-07 (Audit) and K-08 (Data Governance) are directly tied to regulatory compliance requirements. Their absence from the architecture diagrams creates a traceability gap.

**Recommendation:** Add these as containers in C2, even if some are cross-cutting libraries. K-07 and K-08 warrant dedicated containers; K-10 and K-12 can be shown as supporting infrastructure/SDK components.

---

### Finding 3 — 🔴 CRITICAL: D-08 (Surveillance) and D-11 (Reference Data) Missing Entirely

**Files:** All 5 C4 documents  
**Category:** Completeness  
**Section:** N/A (absent)

**Description:**  
- **D-08 Surveillance** is a separate SEBON-mandated capability (pattern detection, alert generation, market manipulation detection). It is not shown as a container, component, or relationship.
- **D-11 Reference Data** (instruments, issuers, market calendars, ISINs) is fundamental to every domain service but has no container or component representation. The OMS validator references "Reference data service" informally (C3 §3.2) but no container exists.

**Impact:** Surveillance is explicitly required by SEBON. Without it in the diagram, there is a regulatory traceability gap. Reference Data is an implicit dependency of OMS, PMS, Pricing, and Compliance — its absence means data ownership and data flow are undefined.

**Recommendation:**  
- Add **"Surveillance Service"** as a new container in C2, consuming events from Event Bus.
- Add **"Reference Data Service"** as a new container in C2, with cache layer and master-data management.

---

### Finding 4 — 🔴 CRITICAL: NFR Inconsistency Within C1 Document

**File:** C4_C1_CONTEXT_SIDDHANTA.md  
**Category:** Consistency  
**Sections:** §5.6 (Performance Invariants) vs. §11.2 (Quality Attributes — Performance)

**Description:**  
Section 5.6 states:
> "Order Latency: Order routing to exchange < 100ms (99th percentile)"
> "API Response Time: REST API responses < 200ms (95th percentile)"

Section 11.2 states:
> "Order Latency: ≤ 2ms internal / ≤ 12ms end-to-end (P99)"
> "API Response: < 200ms (95th percentile)"

The 100ms figure in §5.6 is **50× worse** than the 2ms internal target in §11.2. While "order routing to exchange" and "internal order placement" may be different metrics, using the same "Order Latency" label for both creates dangerous ambiguity. A developer reading §5.6 would assume 100ms is acceptable for the order path.

**Impact:** Misalignment may lead to implementations that meet 100ms but fail the ≤2ms/≤12ms hardened target.

**Recommendation:** Reconcile §5.6 with §11.2. Either:
- Remove the legacy 100ms figure from §5.6, or
- Explicitly label §5.6 as "Order routing network latency (exchange round-trip)" distinct from "internal order placement latency."

---

### Finding 5 — 🟠 HIGH: NFR Inconsistency Within C2 Document

**File:** C4_C2_CONTAINER_SIDDHANTA.md  
**Category:** Consistency  
**Section:** §5.5 (Performance Invariants), items 1 and 2

**Description:**  
Item 1 states:
> "API Latency: P95 < 200ms for REST APIs, **P99 ≤ 12ms for order placement (internal)**"

Item 2 states:
> "Order Latency: Order placement **≤ 2ms internal** / ≤ 12ms end-to-end (P99)"

Item 1 claims P99 ≤ 12ms **internal**, while item 2 claims ≤ 2ms **internal** / ≤ 12ms **e2e**. The 12ms vs 2ms conflict for "internal order placement" is a direct contradiction.

**Impact:** Same as Finding 4 — implementation teams may target the wrong latency number.

**Recommendation:** Correct item 1 to read: "P99 ≤ 2ms for internal order placement, ≤ 12ms end-to-end."

---

### Finding 6 — 🟠 HIGH: K-02, K-03, K-04 Described in Text But Missing from Mermaid Diagram

**File:** C4_C2_CONTAINER_SIDDHANTA.md  
**Category:** Completeness / C4 Methodology  
**Sections:** §2 (Mermaid diagram) vs. §3.8 (cross-cutting)

**Description:**  
K-02 (Configuration Engine), K-03 (Rules Engine), and K-04 (Plugin Runtime) are described in §3.8 as kernel containers that manage T1/T2/T3 content packs. However, **none of them appear as `Container()` nodes in the Mermaid C4Container diagram** in §2. This means the visual diagram — which is the primary artifact stakeholders consume — omits three architecturally critical containers.

**Impact:** Stakeholders viewing only the diagram will miss the T1/T2/T3 infrastructure. The text-diagram disconnect undermines trust in the C2 artifact.

**Recommendation:** Add `Container(config_engine, "Configuration Engine (K-02)", "K-02", "T1 content packs, jurisdiction config")`, `Container(rules_engine, "Rules Engine (K-03)", "K-03/OPA", "T2 OPA/Rego policies")`, and `Container(plugin_runtime, "Plugin Runtime (K-04)", "K-04", "T3 signed executable plugins")` to the Mermaid diagram, inside a new `Boundary(b7, "Kernel Infrastructure Layer")`.

---

### Finding 7 — 🟠 HIGH: K-15 Dual-Calendar Not a Container in Mermaid Diagram

**File:** C4_C2_CONTAINER_SIDDHANTA.md  
**Category:** Completeness / C4 Methodology  
**Sections:** §2 (Mermaid) vs. §3.8

**Description:**  
K-15 Dual-Calendar is described textually in §3.8 as "A dedicated kernel container" but does not appear as a `Container()` node in the C2 Mermaid diagram. Given that **every domain event** must carry `timestamp_bs` (per C3 §9.0 and C4 §7.1), this is a universally-consumed service that should be visually prominent.

**Recommendation:** Add `Container(dual_calendar, "Dual-Calendar Service (K-15)", "Java/Spring", "BS ↔ Gregorian conversion, date arithmetic")` to the Mermaid diagram.

---

### Finding 8 — 🟠 HIGH: D-14 (Sanctions Screening) Missing from All Diagrams

**File:** All 5 C4 documents  
**Category:** Completeness  
**Section:** N/A

**Description:**  
EPIC-D-14-Sanctions-Screening is an ARB P1-13 finding, meaning it was explicitly flagged by the Architecture Review Board as a priority gap. Despite this, no C4 diagram shows a sanctions screening container, component, or class. The C1 diagram shows "KYC/AML Provider" as an external system, and C2 shows an "Identity Service," but real-time sanctions screening (against OFAC, UN, and local lists) is a distinct capability requiring its own processing pipeline.

**Recommendation:** Add a "Sanctions Screening Service (D-14)" container in C2, consuming events from Event Bus and calling external sanctions list providers. Alternatively, show it as a component within the Compliance Engine at C3.

---

### Finding 9 — 🟠 HIGH: 99.999% Availability Target Not Stated in C2

**File:** C4_C2_CONTAINER_SIDDHANTA.md  
**Category:** Consistency  
**Section:** §5 (Invariants)

**Description:**  
The 99.999% availability target is clearly stated in C1 §4.2 and §11.1, and in the Index header. However, C2 §5 (Invariants) does not mention the availability target at all. C2 is the primary technical architecture document where HA deployment patterns (multi-replica, auto-failover, circuit breakers) are defined — the availability SLA should be anchored here.

**Recommendation:** Add to C2 §5.4 (Operational Invariants): "**Availability**: 99.999% uptime during trading hours (≤ 5.26 min/year downtime)."

---

### Finding 10 — 🟠 HIGH: No Dedicated D-13 Client Money Reconciliation Component

**File:** C4_C3_COMPONENT_SIDDHANTA.md  
**Category:** Completeness  
**Section:** §6–7 (CMS)

**Description:**  
EPIC-D-13 (Client Money Reconciliation) is an ARB P1-11 finding. C3's CMS diagram includes a `SegregationValidator` component, but this only validates segregation — it does not model the full **reconciliation workflow** (bank statement ingestion, multi-way matching, break detection, resolution tracking, regulatory reporting). D-13 has its own LLD (`LLD_D13_CLIENT_MONEY_RECONCILIATION.md`) suggesting it's a substantial capability.

**Recommendation:** Either add dedicated components (BankStatementParser, ClientMoneyReconciler, BreakResolver) to the CMS C3 diagram, or elevate D-13 to its own container at C2.

---

### Finding 11 — 🟠 HIGH: No K-17/K-18/K-19 Containers in Mermaid Diagram

**File:** C4_C2_CONTAINER_SIDDHANTA.md  
**Category:** Completeness  
**Section:** §2 (Mermaid diagram)

**Description:**  
K-17 (Distributed Transaction Coordinator), K-18 (Resilience Patterns), and K-19 (DLQ Management) are **ARB P0-level findings** (highest priority) per the Index's epic references. They are mentioned in the Index's cross-cutting resilience section but are not shown in the C2 Mermaid diagram. K-17 (outbox/saga) and K-19 (DLQ) are infrastructure services that warrant at least library-level representation at C2.

**Recommendation:** Add K-17, K-18, K-19 as components within the Event & Messaging layer boundary in the C2 Mermaid diagram: e.g., `Container(dtc, "Transaction Coordinator (K-17)", "Java", "Outbox pattern, saga compensation")`.

---

### Finding 12 — 🟡 MEDIUM: C1 Does Not Map Actors to Epics

**File:** C4_C1_CONTEXT_SIDDHANTA.md  
**Category:** Traceability  
**Section:** §3.2

**Description:**  
Seven human actors are defined but there is no traceability matrix mapping actors → epics. For example, "Merchant Banker" → EPIC-D-12 (Corporate Actions), EPIC-W-02 (Client Onboarding); "Compliance Officer" → EPIC-D-07, D-08, D-14, R-02. Without this mapping, it's unclear which actor interactions exercise which features.

**Recommendation:** Add an Actor-Epic traceability table in §3.2 or §7.

---

### Finding 13 — 🟡 MEDIUM: C2 Container Diagram Does Not Show Kernel Module IDs

**File:** C4_C2_CONTAINER_SIDDHANTA.md  
**Category:** Traceability  
**Section:** §2 (Mermaid diagram)

**Description:**  
Containers are labeled with technology descriptors (e.g., "Identity Service [Node.js]") but not with their kernel/domain module IDs (K-01, D-01, etc.). This forces readers to mentally map "Identity Service" → K-01, "Ledger Service" → K-16, etc. Since the 42-epic taxonomy is central to the project, container labels should include the module ID.

**Recommendation:** Update container descriptions in Mermaid to include module ID, e.g., `Container(identity, "Identity Service (K-01)", "Node.js", "KYC, authentication, RBAC")`.

---

### Finding 14 — 🟡 MEDIUM: C3 Cross-Cutting Primitives Are Invariants, Not Components

**File:** C4_C3_COMPONENT_SIDDHANTA.md  
**Category:** C4 Methodology  
**Section:** §9.0

**Description:**  
T1/T2/T3 and dual-calendar appear as invariants (§9.0) rather than as components in the C3 Mermaid diagrams. In a C3 diagram, cross-cutting concerns should be modeled as shared components or service adapters (e.g., `Component(calendar_adapter, "Calendar Adapter", "Adapter", "K-15 Dual-Calendar integration")`). The current approach documents the *requirement* but not the *realization*.

**Recommendation:** In each C3 Mermaid diagram, add a `CalendarAdapter` component (calling K-15) and a `ConfigClient` component (loading T1 data from K-02). Show dependencies from domain services to these adapters.

---

### Finding 15 — 🟡 MEDIUM: C4 Code Diagrams Do Not Show Dual-Calendar Fields in Mermaid

**File:** C4_C4_CODE_SIDDHANTA.md  
**Category:** Consistency  
**Section:** §2 (Order class diagram), §4 (Position class diagram)

**Description:**  
Entity invariant §7.1 item 2 requires:
> "All entities have `createdAt`, `updatedAt` (Gregorian) **and** `createdAtBs`, `updatedAtBs` (Bikram Sambat)"

However, neither the `Order` class diagram nor the `Position` class diagram includes `createdAtBs` or `updatedAtBs` fields. The Java code examples also omit these fields entirely.

**Impact:** Developers implementing from the class diagram will omit BS timestamp fields, violating the invariant.

**Recommendation:** Add `String createdAtBs`, `String updatedAtBs` fields to both `Order` and `Position` class diagrams and code examples.

---

### Finding 16 — 🟡 MEDIUM: C4 Code Event Classes Do Not Show K-05 Envelope Fields

**File:** C4_C4_CODE_SIDDHANTA.md  
**Category:** Consistency  
**Section:** §2 (Order class diagram), §3.7 (Event Layer)

**Description:**  
Entity invariant §7.1 item 7 requires:
> "All domain events include `timestampBs`, `timestampGregorian`, `tenantId`, `traceId`, `causationId`, `correlationId`"

The `OrderEvent` abstract class in the Mermaid diagram and Java code only contains `eventId`, `aggregateId`, `timestamp`. The envelope fields (`timestampBs`, `tenantId`, `traceId`, `causationId`, `correlationId`) are absent.

**Impact:** Same as Finding 15 — developers will build events without the K-05 standard envelope.

**Recommendation:** Add the 6 envelope fields to `OrderEvent` abstract class in both the Mermaid diagram and Java code.

---

### Finding 17 — 🟡 MEDIUM: C4 `Order` Entity Missing `tenantId` Field

**File:** C4_C4_CODE_SIDDHANTA.md  
**Category:** Consistency  
**Section:** §3.5 (Order Entity code), §7.1 (invariant item 3)

**Description:**  
Entity invariant §7.1 item 3 states:
> "All entities carry a non-null `tenantId` UUID for multi-tenant RLS isolation"

The `Order` entity JPA code does not include a `tenantId` field. Similarly, `Position` entity is missing `tenantId`.

**Recommendation:** Add `@Column(nullable = false) private UUID tenantId;` to both `Order` and `Position` entities.

---

### Finding 18 — 🟡 MEDIUM: C2 Missing "Execution Management" as Distinct Capability

**File:** C4_C2_CONTAINER_SIDDHANTA.md  
**Category:** Completeness  
**Section:** §2 (Mermaid diagram)

**Description:**  
D-02 (EMS - Execution Management System) has its own epic (EPIC-D-02-EMS.md) implying it is a first-class capability. However, at C2 it is absorbed into OMS and the FIX Gateway. If EMS is intended to be a separate deployable service (e.g., smart order routing, algo execution, multi-exchange support), it should have its own container.

**Recommendation:** Clarify whether D-02 is architecturally separate from D-01. If yes, add a dedicated "Execution Management Service (D-02)" container. If not, explicitly document the merge decision and update the epic's scope.

---

### Finding 19 — 🟡 MEDIUM: C1 Mermaid Diagram Uses Non-Standard C4 Syntax

**File:** C4_C1_CONTEXT_SIDDHANTA.md  
**Category:** C4 Methodology  
**Section:** §2 (Mermaid diagram)

**Description:**  
The C1 Mermaid diagram places the Siddhanta system inside an `Enterprise_Boundary(b0, "Internet Zone")`. In C4 methodology, the system under design should be at the center, not inside a trust boundary. Trust boundaries in C4Context diagrams should use `Boundary()` to group external actors/systems by zone, not to contain the central system.

**Recommendation:** Move Siddhanta out of the Internet Zone boundary. Use `Boundary()` to group external systems (e.g., `Boundary(reg_zone, "Regulatory Zone") { ... SEBON, Tax Authority ... }`).

---

### Finding 20 — 🟡 MEDIUM: No Deployment Diagram (C4 Supplementary)

**Files:** All 5 documents  
**Category:** C4 Methodology  
**Section:** N/A (absent)

**Description:**  
The C4 methodology recommends a **deployment diagram** as a supplementary view showing how containers map to infrastructure (Kubernetes pods, nodes, cloud regions, DR topology). C2 §8 describes deployment textually, but there is no Mermaid `C4Deployment` diagram. Given the 99.999% availability and multi-region DR requirements, a deployment diagram is essential.

**Recommendation:** Create a supplementary `C4_DEPLOYMENT_SIDDHANTA.md` with a `C4Deployment` Mermaid diagram showing production, DR, and CDN topology.

---

### Finding 21 — 🟢 LOW: Index Statistics Table May Be Stale

**File:** C4_DIAGRAM_PACK_INDEX.md  
**Category:** Maintenance  
**Section:** Diagram Statistics table

**Description:**  
The statistics table claims file sizes (`~50 KB`, `~70 KB`, etc.) that are approximate and undated. These likely originated from v1.0. As diagrams evolve, these will become stale.

**Recommendation:** Either auto-generate this table from actual file sizes, or remove it.

---

### Finding 22 — 🟢 LOW: Revision History Date Inconsistency in Index

**File:** C4_DIAGRAM_PACK_INDEX.md  
**Category:** Consistency  
**Section:** Revision History

**Description:**  
Version 2.0 is dated `2026-03-03` but version 2.1 is dated `2025-06`. A version after 2.0 should have a later date. This appears to be a chronological error (2.1 was likely the v2.0 hardening pass done in June 2025, before the March 2026 ARB update).

**Recommendation:** Renumber or redate: v2.0 = June 2025 (hardening), v2.1 = March 2026 (ARB remediation).

---

### Finding 23 — 🟢 LOW: C3 Does Not Cross-Reference LLD Documents

**File:** C4_C3_COMPONENT_SIDDHANTA.md  
**Category:** Traceability  
**Section:** §13 (Cross-References)

**Description:**  
C3 §13 references C1, C2, C4 and two epics (D-01, D-03) but does not reference the corresponding LLD documents (LLD_D01_OMS.md, etc.) which contain the most detailed design specifications. Given that C3 is the bridge between architecture and implementation, bidirectional LLD links are important.

**Recommendation:** Add LLD cross-references to C3 §13.

---

### Finding 24 — 🟢 LOW: C2 API Gateway Rate Limits Are Very Low

**File:** C4_C2_CONTAINER_SIDDHANTA.md  
**Category:** Consistency  
**Section:** §3.2 (API Gateway)

**Description:**  
API Gateway rate limit is "100 req/sec per client (trading)." At 50K TPS sustained throughput, this implies at least 500 concurrent trading clients at full rate, which is reasonable. However, the 100 req/sec figure feels incongruous with the 100K burst TPS target. If burst comes from a smaller set of institutional clients, 100 req/sec per client would cap total throughput at much less than 100K.

**Recommendation:** Clarify whether institutional/FIX clients bypass the per-client rate limit. Define separate rate tiers (Retail: 10 req/s, Institutional: 1,000 req/s, FIX: unlimited).

---

### Finding 25 — 🟢 LOW: C4 Java Code Uses Deprecated KafkaTemplate Callback

**File:** C4_C4_CODE_SIDDHANTA.md  
**Category:** Code Quality  
**Section:** §3.7 (OrderEventPublisher)

**Description:**  
```java
kafkaTemplate.send(ordersTopic, key, event)
    .addCallback(result -> ..., ex -> ...);
```
The `addCallback()` method on `ListenableFuture` is deprecated in Spring Kafka 3.x (which uses `CompletableFuture`). The modern API uses `whenComplete()` or `thenAccept()`.

**Recommendation:** Update to:
```java
kafkaTemplate.send(ordersTopic, key, event)
    .whenComplete((result, ex) -> {
        if (ex != null) log.error("Failed", ex);
        else log.info("Published: {}", event.getEventType());
    });
```

---

### Finding 26 — 🟡 MEDIUM: W-02 (Client Onboarding) and R-02 (Incident Notification) Missing

**Files:** All 5 C4 documents  
**Category:** Completeness  
**Section:** N/A

**Description:**  
- **W-02 Client Onboarding** has its own epic but is not shown as a workflow or component at any C4 level. The C2 Workflow Engine mentions "Long-running workflows (KYC, onboarding)" generically but doesn't specifically model the onboarding flow.
- **R-02 Incident Notification** is an ARB P1-15 finding for notifying regulators of security incidents. It is not modeled anywhere.

**Recommendation:** Show W-02 as a named workflow in the C3 Workflow Engine decomposition. Show R-02 as a component within the Compliance Engine or as a dedicated notification workflow.

---

## 4. T1/T2/T3 CONTENT PACK MODEL — CROSS-LEVEL ASSESSMENT

| C4 Level | T1/T2/T3 Representation | Verdict |
|----------|--------------------------|---------|
| **C1** | ✓ §8.4: Described as cross-cutting primitive with T1/T2/T3 definitions | 🟢 Good |
| **C2** | ✓ §3.8: Table mapping T1→K-02, T2→K-03, T3→K-04 | 🟡 Described in text but K-02/K-03/K-04 **not in Mermaid diagram** |
| **C3** | ✓ §9.0: Invariant requiring no hard-coded jurisdiction logic | 🟡 Invariant only, **no component realization** (no ConfigClient, RulesClient, PluginLoader) |
| **C4** | ✓ §7.1: Invariant item 6 | 🟡 Invariant only, **no Java interface/class showing content pack loading** |

**Overall:** The T1/T2/T3 model is consistently **documented as a requirement** but inconsistently **realized in diagrams**. At C2, the three kernel containers (K-02, K-03, K-04) must appear in the Mermaid diagram. At C3 and C4, adapter components/classes that interact with these kernel services must be shown.

---

## 5. DUAL-CALENDAR — CROSS-LEVEL ASSESSMENT

| C4 Level | Dual-Calendar Representation | Verdict |
|----------|------------------------------|---------|
| **C1** | ✓ §8.4: Description of K-15 and BS+Gregorian event fields | 🟢 Good |
| **C2** | ✓ §3.8: K-15 as kernel container, DB companions, event envelope | 🟢 Good (text), 🟡 **Not in Mermaid** |
| **C3** | ✓ §9.0: Invariant requiring BS timestamps in all components | 🟢 Good |
| **C4** | ✓ §7.1: Invariant requiring `createdAtBs`/`updatedAtBs` | 🟡 **Not shown in class diagrams or Java code** |

**Overall:** Same pattern as T1/T2/T3 — well-documented as a requirement, but the actual fields/components are missing from the visual artifacts.

---

## 6. C4 METHODOLOGY COMPLIANCE

| C4 Principle | Compliance | Notes |
|--------------|------------|-------|
| **Hierarchical drill-down (C1→C2→C3→C4)** | 🟠 Partial | Only OMS and PMS drill from C1 all the way to C4. 25+ containers stop at C2. |
| **System Context shows actors + external systems** | 🟢 Good | 7 actors, 13 external systems, trust boundaries |
| **Container shows deployable units** | 🟡 Partial | 28 containers shown but at least 8 missing (K-02, K-03, K-04, K-07, K-08, K-15, D-08, D-11) |
| **Component shows internal structure** | 🔴 Poor | Only 3/28 containers decomposed |
| **Code shows classes/interfaces** | 🔴 Poor | Only 2/28 containers have code diagrams |
| **Supplementary deployment diagram** | 🔴 Missing | Described textually, no Mermaid diagram |
| **Each diagram is self-contained** | 🟢 Good | Each file has legend, assumptions, invariants, failure modes |
| **Consistent notation** | 🟢 Good | Mermaid C4 syntax used consistently |
| **"What Breaks This" at each level** | 🟢 Excellent | Every diagram has extensive failure analysis |

---

## 7. PRIORITIZED ACTION ITEMS

### Immediate (Before Architecture Baseline Sign-Off)

| # | Action | Severity | Effort |
|---|--------|----------|--------|
| 1 | Reconcile NFR inconsistencies in C1 §5.6 vs §11.2, and C2 §5.5 items 1 vs 2 | 🔴 Critical | Small |
| 2 | Add K-02, K-03, K-04, K-15 as containers in C2 Mermaid diagram | 🟠 High | Small |
| 3 | Add D-08 (Surveillance) and D-11 (Reference Data) as containers in C2 | 🔴 Critical | Medium |
| 4 | Add K-07 (Audit Framework) and K-08 (Data Governance) as containers in C2 | 🔴 Critical | Medium |
| 5 | Add 99.999% availability invariant to C2 §5.4 | 🟠 High | Small |

### Sprint 1 (C3 Expansion)

| # | Action | Severity | Effort |
|---|--------|----------|--------|
| 6 | Create C3 for Ledger Service (K-16) | 🟠 High | Large |
| 7 | Create C3 for Risk Management (D-06) | 🟠 High | Large |
| 8 | Create C3 for Compliance Engine (D-07) | 🟠 High | Large |
| 9 | Create C3 for Identity Service (K-01) | 🟠 High | Large |
| 10 | Add T1/T2/T3 adapter components to existing C3 diagrams | 🟡 Medium | Medium |
| 11 | Add K-15 CalendarAdapter component to existing C3 diagrams | 🟡 Medium | Small |

### Sprint 2 (C4 Expansion & Supplementary)

| # | Action | Severity | Effort |
|---|--------|----------|--------|
| 12 | Add dual-calendar fields to C4 class diagrams and code | 🟡 Medium | Small |
| 13 | Add K-05 envelope fields to event class diagrams and code | 🟡 Medium | Small |
| 14 | Add `tenantId` to all entity class diagrams and code | 🟡 Medium | Small |
| 15 | Create C4_DEPLOYMENT_SIDDHANTA.md supplementary diagram | 🟡 Medium | Medium |
| 16 | Create C4 code diagrams for Ledger and Risk domains | 🟠 High | Large |

### Backlog

| # | Action | Severity | Effort |
|---|--------|----------|--------|
| 17 | Add module IDs (K-xx, D-xx) to all C2 container labels | 🟡 Medium | Small |
| 18 | Add Actor→Epic traceability table to C1 | 🟡 Medium | Small |
| 19 | Add LLD cross-references to C3 | 🟢 Low | Small |
| 20 | Fix Index revision history date inconsistency | 🟢 Low | Small |
| 21 | Update deprecated KafkaTemplate callback code | 🟢 Low | Small |
| 22 | Define API Gateway rate-limit tiers per client type | 🟢 Low | Small |
| 23 | Fix C1 Mermaid boundary placement (system not inside Internet Zone) | 🟡 Medium | Small |

---

## 8. CONCLUSION

The C4 diagram pack demonstrates **strong architectural thinking** at the system context (C1) and container (C2) levels, with excellent cross-cutting documentation of T1/T2/T3 content packs, dual-calendar, and failure-mode analysis. The **hardened NFR targets are mostly consistent** across documents, with two notable exceptions that must be fixed.

The critical gap is **depth**: only 2 of 42 epics are fully traced from C1 through C4, and 8 epics are completely absent. The architecture baseline cannot be signed off until at least the 5 immediate action items (§7) are addressed. The C3/C4 expansion plan should target the 5 highest-risk containers in Sprint 1.

**Recommended verdict:** ⚠️ **Conditional Approval** — approve C1 and C2 with corrections; defer C3/C4 approval until expansion is complete.

---

**END OF C4 DIAGRAM REVIEW REPORT**
