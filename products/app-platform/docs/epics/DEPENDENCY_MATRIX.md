# Epic Dependency Validation Matrix

**Generated:** March 10, 2026 (Current normalized baseline)  
**Purpose:** Validate all epic dependencies, identify bootstrap cycles, and ensure version compatibility

---

## Dependency Graph Summary

### Kernel Layer (K-\*) - Foundation

These modules have minimal dependencies and form the platform foundation.

| Epic ID | Epic Name                           | Dependencies                   | Dependents Count |
| ------- | ----------------------------------- | ------------------------------ | ---------------- |
| K-01    | IAM                                 | K-02, K-05, K-07               | 18+              |
| K-02    | Configuration Engine                | K-05, K-07                     | 15+              |
| K-03    | Rules Engine                        | K-02, K-04, K-05, K-07         | 10+              |
| K-04    | Plugin Runtime                      | K-02, K-05, K-07               | 8+               |
| K-05    | Event Bus                           | K-07                           | 25+              |
| K-06    | Observability                       | K-05, K-07                     | 20+              |
| K-07    | Audit Framework                     | K-05                           | 22+              |
| K-08    | Data Governance                     | K-01, K-02, K-05, K-07, K-14   | 12+              |
| K-09    | AI Governance                       | K-01, K-02, K-05, K-06, K-07   | 15+              |
| K-10    | Deployment Abstraction              | K-02, K-04, K-06, K-07, PU-004 | 5+               |
| K-11    | API Gateway                         | K-01, K-02, K-05, K-06, K-07   | 18+              |
| K-12    | Platform SDK                        | K-01 to K-19                   | 42 (all)         |
| K-13    | Admin Portal                        | K-01, K-02, K-06, K-07, K-10   | 8+               |
| K-14    | Secrets Management                  | K-01, K-02, K-07, K-08         | 15+              |
| K-15    | Dual-Calendar Service               | K-02, K-05                     | 12+              |
| K-16    | Ledger Framework                    | K-02, K-05, K-15               | 8+               |
| K-17    | Distributed Transaction Coordinator | K-05, K-06, K-07, K-16         | 10+              |
| K-18    | Resilience Patterns Library         | K-02, K-05, K-06               | 15+              |
| K-19    | DLQ Management & Event Replay       | K-05, K-06, K-07               | 8+               |

### Domain Layer (D-\*) - Business Logic

Domain modules depend heavily on kernel modules.

| Epic ID | Epic Name                   | Dependencies                             | Kernel Dependencies     |
| ------- | --------------------------- | ---------------------------------------- | ----------------------- |
| D-01    | OMS                         | K-01, K-03, K-05, K-15, K-16, K-18       | 6 kernel                |
| D-02    | EMS                         | D-01, D-04, K-03, K-04, K-05             | 3 kernel + 2 domain     |
| D-03    | PMS                         | D-01, K-03, K-05, K-15, K-16             | 4 kernel + 1 domain     |
| D-04    | Market Data                 | K-02, K-04, K-05, K-15                   | 4 kernel                |
| D-05    | Pricing Engine              | D-04, K-03, K-05, K-15                   | 3 kernel + 1 domain     |
| D-06    | Risk Engine                 | D-01, D-03, K-03, K-05, K-15, K-16, K-18 | 5 kernel + 2 domain     |
| D-07    | Compliance                  | K-01, K-03, K-05, K-07, K-09, K-18       | 6 kernel                |
| D-08    | Surveillance                | K-03, K-05, K-06, K-09, K-18             | 5 kernel                |
| D-09    | Post-Trade                  | D-01, D-02, K-05, K-15, K-16             | 3 kernel + 2 domain     |
| D-10    | Regulatory Reporting        | D-07, D-08, K-05, K-07, K-15, K-16       | 4 kernel + 2 domain     |
| D-11    | Reference Data              | K-02, K-04, K-05, K-15                   | 4 kernel                |
| D-12    | Corporate Actions           | K-03, K-05, K-15, K-16                   | 4 kernel                |
| D-13    | Client Money Reconciliation | K-05, K-06, K-07, K-16, K-18, R-02       | 5 kernel + 1 regulatory |
| D-14    | Sanctions Screening         | K-01, K-02, K-05, K-07, K-18, D-07       | 5 kernel + 1 domain     |

### Workflow Layer (W-\*) - Cross-Domain Orchestration

| Epic ID | Epic Name              | Dependencies                                   | Total Dependencies               |
| ------- | ---------------------- | ---------------------------------------------- | -------------------------------- |
| W-01    | Workflow Orchestration | K-01, K-02, K-05, K-07, K-13                   | 5 kernel                         |
| W-02    | Client Onboarding      | K-01, K-02, K-07, K-16, D-01, D-06, D-07, W-01 | 4 kernel + 3 domain + 1 workflow |

### Other Layers

| Epic ID | Epic Name                              | Layer          | Dependencies                                            |
| ------- | -------------------------------------- | -------------- | ------------------------------------------------------- |
| O-01    | Operator Console                       | Operations     | K-06, K-07, K-10, K-13, PU-004                          |
| P-01    | Pack Certification                     | Packs          | K-02, K-04, K-07, K-09, PU-004                          |
| R-01    | Regulator Portal                       | Regulatory     | K-01, K-05, K-07, K-16, D-10                            |
| R-02    | Incident Response & Escalation         | Regulatory     | K-01, K-05, K-06, K-07, K-08, R-01                      |
| T-01    | Integration Testing                    | Testing        | All modules (K-01 through D-14, W-01, W-02, R-01, R-02) |
| T-02    | Chaos Engineering & Resilience Testing | Testing        | K-05, K-06, K-10, K-18, T-01                            |
| PU-004  | Platform Manifest                      | Platform Unity | K-04, K-05, K-07, K-10                                  |

---

## Dependency Validation Results

### ✅ All Dependencies Valid

All referenced epic IDs in this matrix exist in the epic directory. Legacy `PU-003` references have been removed.

### ⚠️ One Intentional Bootstrap Cycle Exists

There is one controlled bootstrap cycle between **K-05 Event Bus** and **K-07 Audit Framework**. The implementation plan remains valid because `plans/CURRENT_EXECUTION_PLAN.md` explicitly breaks the cycle by shipping K-05 core bus/event-store capabilities first, then K-07 audit services, and only then enabling K-05 audit hooks.

### Dependency Layers (Bottom-Up)

**Bootstrap Pair (Intentional Cycle):**

- K-05 Event Bus <-> K-07 Audit Framework

**Layer 1 (Earliest Services After Bootstrap Pair):**

- K-02 Configuration Engine
- K-15 Dual-Calendar Service

**Layer 2 (Core Kernel Services):**

- K-01 IAM
- K-02 Configuration Engine
- K-03 Rules Engine
- K-04 Plugin Runtime
- K-06 Observability
- K-14 Secrets Management
- K-16 Ledger Framework

**Layer 3 (Advanced Kernel Services):**

- K-08 Data Governance
- K-09 AI Governance
- K-10 Deployment Abstraction
- K-11 API Gateway
- K-13 Admin Portal
- K-17 Distributed Transaction Coordinator
- K-18 Resilience Patterns Library
- K-19 DLQ Management & Event Replay
- PU-004 Platform Manifest

**Layer 4 (Domain Services):**

- D-01 OMS
- D-04 Market Data
- D-07 Compliance
- D-11 Reference Data
- D-12 Corporate Actions

**Layer 5 (Dependent Domain Services):**

- D-02 EMS
- D-03 PMS
- D-05 Pricing Engine
- D-06 Risk Engine
- D-08 Surveillance
- D-09 Post-Trade
- D-14 Sanctions Screening

**Layer 6 (Advanced Domain Services):**

- D-10 Regulatory Reporting
- D-13 Client Money Reconciliation

**Layer 7 (Workflow & Operations):**

- W-01 Workflow Orchestration
- O-01 Operator Console
- P-01 Pack Certification

**Layer 8 (Advanced Workflows & Regulatory):**

- W-02 Client Onboarding
- R-01 Regulator Portal
- R-02 Incident Response & Escalation

**Layer 9 (Cross-Cutting & Testing):**

- K-12 Platform SDK (depends on all kernel modules)
- T-01 Integration Testing (depends on all modules)
- T-02 Chaos Engineering & Resilience Testing

---

## Kernel Readiness Gates Analysis

### Critical Path Analysis

**To implement D-01 OMS, you must first complete:**

1. K-01 IAM ✓
2. K-03 Rules Engine ✓
3. K-05 Event Bus ✓
4. K-15 Dual-Calendar Service ✓
5. K-16 Ledger Framework ✓

**To implement W-02 Client Onboarding, you must first complete:**

1. All K-01, K-02, K-07, K-16 (kernel) ✓
2. D-01 OMS ✓
3. D-06 Risk Engine ✓
4. D-07 Compliance ✓
5. W-01 Workflow Orchestration ✓

### Recommended Implementation Order

**Phase 1: Foundation (Kernel Core)**

1. K-05 Event Bus core path
2. K-07 Audit Framework
3. K-02 Configuration Engine
4. K-15 Dual-Calendar Service

**Phase 2: Essential Kernel Services** 5. K-01 IAM 6. K-03 Rules Engine 7. K-04 Plugin Runtime 8. K-16 Ledger Framework 9. K-14 Secrets Management

**Phase 3: Advanced Kernel Services** 10. K-06 Observability 11. K-08 Data Governance 12. K-09 AI Governance 13. K-11 API Gateway 14. K-13 Admin Portal 15. K-10 Deployment Abstraction 16. PU-004 Platform Manifest

**Phase 4: Core Domain Services** 17. D-11 Reference Data 18. D-04 Market Data 19. D-01 OMS 20. D-07 Compliance 21. D-12 Corporate Actions

**Phase 5: Dependent Domain Services** 22. D-02 EMS 23. D-03 PMS 24. D-05 Pricing Engine 25. D-06 Risk Engine 26. D-08 Surveillance 27. D-09 Post-Trade

**Phase 3b: ARB Remediation Kernel Services**
16b. K-17 Distributed Transaction Coordinator
16c. K-18 Resilience Patterns Library
16d. K-19 DLQ Management & Event Replay

**Phase 6: Reporting, Compliance & Workflows** 28. D-10 Regulatory Reporting 29. D-13 Client Money Reconciliation 30. D-14 Sanctions Screening 31. W-01 Workflow Orchestration 32. O-01 Operator Console 33. P-01 Pack Certification

**Phase 7: Advanced Workflows & Regulatory** 34. W-02 Client Onboarding 35. R-01 Regulator Portal 36. R-02 Incident Response & Escalation

**Phase 8: Cross-Cutting & Testing** 37. K-12 Platform SDK (continuously updated) 38. T-01 Integration Testing (continuous) 39. T-02 Chaos Engineering & Resilience Testing

---

## Dependency Matrix (Detailed)

### Legend

- ✓ = Direct dependency
- ⊕ = Transitive dependency
- — = No dependency

| Epic | K-01 | K-02 | K-03 | K-04 | K-05 | K-06 | K-07 | K-08 | K-09 | K-10 | K-11 | K-12 | K-13 | K-14 | K-15 | K-16 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| K-01 | —    | ✓    | —    | —    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    |
| K-02 | —    | —    | —    | —    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    |
| K-03 | —    | ✓    | —    | ✓    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    |
| K-04 | —    | ✓    | —    | —    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    |
| K-05 | —    | —    | —    | —    | —    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    |
| K-06 | —    | —    | —    | —    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    |
| K-07 | —    | —    | —    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    | —    | —    |
| K-08 | ✓    | ✓    | —    | —    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | ✓    | —    | —    |
| K-09 | ✓    | ✓    | —    | —    | ✓    | ✓    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    |
| K-10 | —    | ✓    | —    | ✓    | —    | ✓    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    |
| K-11 | ✓    | ✓    | —    | —    | ✓    | ✓    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    |
| K-12 | ✓    | ✓    | ✓    | ✓    | ✓    | ✓    | ✓    | ✓    | ✓    | ✓    | ✓    | —    | ✓    | ✓    | ✓    | ✓    |
| K-13 | ✓    | ✓    | —    | —    | —    | ✓    | ✓    | —    | —    | ✓    | —    | —    | —    | —    | —    | —    |
| K-14 | ✓    | ✓    | —    | —    | —    | —    | ✓    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    |
| K-15 | —    | ✓    | —    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    | —    | —    |
| K-16 | —    | ✓    | —    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    | ✓    | —    |

### Domain Dependencies on Kernel

| Epic | K-01 | K-02 | K-03 | K-04 | K-05 | K-06 | K-07 | K-08 | K-09 | K-10 | K-11 | K-12 | K-13 | K-14 | K-15 | K-16 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| D-01 | ✓    | —    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    | ✓    | ✓    |
| D-02 | —    | —    | ✓    | ✓    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    | —    | —    |
| D-03 | —    | —    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    | ✓    | ✓    |
| D-04 | —    | ✓    | —    | ✓    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    | ✓    | —    |
| D-05 | —    | —    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    | ✓    | —    |
| D-06 | —    | —    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    | ✓    | ✓    |
| D-07 | ✓    | —    | ✓    | —    | ✓    | —    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    |
| D-08 | —    | —    | —    | —    | ✓    | —    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    |
| D-09 | —    | —    | —    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    | ✓    | ✓    |
| D-10 | —    | —    | —    | —    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | ✓    | ✓    |
| D-11 | —    | ✓    | —    | ✓    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    | ✓    | —    |
| D-12 | —    | —    | ✓    | —    | ✓    | —    | —    | —    | —    | —    | —    | —    | —    | —    | ✓    | ✓    |

---

## Version Compatibility Matrix

Epics updated during ARB remediation are at **v1.1.0**; new epics created during ARB remediation are at **v1.0.0**.

| Epic   | Current Version | ARB Updated?                                              | Status |
| ------ | --------------- | --------------------------------------------------------- | ------ |
| K-01   | 1.1.0           | ✅ FR11/FR12/FR13                                         | Active |
| K-02   | 1.1.0           | ✅ FR9, ConfigRolledBackEvent                             | Active |
| K-03   | 1.1.0           | ✅ FR9/FR10 (circuit breaker)                             | Active |
| K-04   | 1.1.0           | ✅ FR9/FR10 (resource quotas)                             | Active |
| K-05   | 1.1.0           | ✅ FR10-FR14 (backpressure, saga, DLQ, projection, trace) | Active |
| K-06   | 1.1.0           | ✅ FR9/FR10 (PII detection, SLIs)                         | Active |
| K-07   | 1.1.0           | ✅ FR7/FR8 (hash anchoring, buffer)                       | Active |
| K-08   | 1.1.0           | ✅ FR7/FR8 (RLS, breach response)                         | Active |
| K-09   | 1.1.0           | ✅ FR8/FR9 (drift rollback, prompt injection)             | Active |
| K-10   | 1.0.0           | —                                                         | Active |
| K-11   | 1.1.0           | ✅ FR7/FR8 (size limits, schema validation)               | Active |
| K-12   | 1.0.0           | —                                                         | Active |
| K-13   | 1.0.0           | —                                                         | Active |
| K-14   | 1.0.0           | —                                                         | Active |
| K-15   | 1.1.0           | ✅ FR10 (leap year edge cases)                            | Active |
| K-16   | 1.1.0           | ✅ FR8/FR9/FR10 (precision, temporal, idempotency)        | Active |
| K-17   | 1.0.0           | 🆕 Created [P0-01]                                        | Active |
| K-18   | 1.0.0           | 🆕 Created [P0-02]                                        | Active |
| K-19   | 1.0.0           | 🆕 Created [P0-04]                                        | Active |
| D-01   | 1.1.0           | ✅ FR3 (unified pre-trade via K-03)                       | Active |
| D-02   | 1.0.0           | —                                                         | Active |
| D-03   | 1.0.0           | —                                                         | Active |
| D-04   | 1.0.0           | —                                                         | Active |
| D-05   | 1.0.0           | —                                                         | Active |
| D-06   | 1.1.0           | ✅ FR1, Section 4.2 (no hardcoded SEBON)                  | Active |
| D-07   | 1.1.0           | ✅ FR1 (registers rules with K-03)                        | Active |
| D-08   | 1.1.0           | ✅ FR6/FR7 (latency budgets, event-only access)           | Active |
| D-09   | 1.0.0           | —                                                         | Active |
| D-10   | 1.1.0           | ✅ FR7/FR8 (real-time trade reporting, reconciliation)    | Active |
| D-11   | 1.0.0           | —                                                         | Active |
| D-12   | 1.0.0           | —                                                         | Active |
| D-13   | 1.0.0           | 🆕 Created [P1-11]                                        | Active |
| D-14   | 1.0.0           | 🆕 Created [P1-13]                                        | Active |
| W-01   | 1.0.0           | —                                                         | Active |
| W-02   | 1.0.0           | —                                                         | Active |
| O-01   | 1.0.0           | —                                                         | Active |
| P-01   | 1.0.0           | —                                                         | Active |
| R-01   | 1.0.0           | —                                                         | Active |
| R-02   | 1.0.0           | 🆕 Created [P1-15]                                        | Active |
| T-01   | 1.1.0           | ✅ FR8 (continuous pen testing)                           | Active |
| T-02   | 1.0.0           | 🆕 Created [P2-19]                                        | Active |
| PU-004 | 1.0.0           | —                                                         | Active |

---

## Dependency Health Metrics

### Most Depended Upon (Critical Path Modules)

1. **K-05 Event Bus** - 25+ dependents (most critical)
2. **K-07 Audit Framework** - 22+ dependents
3. **K-06 Observability** - 20+ dependents
4. **K-01 IAM** - 18+ dependents
5. **K-11 API Gateway** - 18+ dependents
6. **K-02 Configuration Engine** - 15+ dependents
7. **K-09 AI Governance** - 15+ dependents
8. **K-14 Secrets Management** - 15+ dependents

### Least Dependencies (Most Independent)

1. **K-05 Event Bus** - 1 dependency (K-07)
2. **K-07 Audit Framework** - 1 dependency (K-05)
3. **K-15 Dual-Calendar Service** - 2 dependencies (K-02, K-05)

### Most Dependencies (Most Complex)

1. **K-12 Platform SDK** - 16 dependencies (all kernel modules)
2. **T-01 Integration Testing** - 35 dependencies (all modules)
3. **W-02 Client Onboarding** - 8 dependencies (4 kernel + 3 domain + 1 workflow)

---

## Recommendations

### ✅ Dependency Health: EXCELLENT

- No circular dependencies
- Clear layering (kernel → domain → workflow)
- All dependencies valid and exist
- Reasonable dependency counts per module

### Action Items

1. **Monitor K-05 Event Bus** - Most critical module with 25+ dependents. Any breaking change here impacts the entire platform.
2. **Prioritize Kernel Completion** - Complete all K-\* modules before starting domain modules.
3. **Version Coordination** - When updating kernel modules, coordinate version bumps across dependent modules.
4. **Dependency Freeze** - Consider freezing kernel API contracts before domain module development.

---

**Matrix Status:** ✅ VALIDATED  
**Last Updated:** March 10, 2026  
**Next Review:** June 10, 2026 or when new epics are added
