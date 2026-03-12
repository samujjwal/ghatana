# SIDDHANTA PLATFORM — COMPLETE STORY INDEX

## Project Siddhanta | Capital Markets OS | Full Backlog Reference

> **Total Stories**: 654 | **Total Story Points**: ~1,930 | **Sprints**: 1–30 | **Teams**: 6
>
> This index provides a single-entry reference for all user stories across 8 milestone files. Use the Epic Index, ID ranges, and team allocation tables to navigate quickly.

> Execution authority: use this index together with `plans/CURRENT_EXECUTION_PLAN.md`, `epics/DEPENDENCY_MATRIX.md`, and `lld/LLD_INDEX.md` for implementation sequencing and design traceability.
>
> Scope note: dynamic process definitions, human-task schemas, and value catalogs are covered within existing K-02, K-13, W-01, and O-01 story scope; the normalized counts and story-point totals remain unchanged.

---

## 1. MILESTONE FILES

| File                                               | Title                                      | Sprints  | Stories | SP         | Status |
| -------------------------------------------------- | ------------------------------------------ | -------- | ------- | ---------- | ------ |
| [MILESTONE_1A_STORIES.md](MILESTONE_1A_STORIES.md) | Platform Foundation Kernel                 | 1–2      | 78      | ~220       | ✅     |
| [MILESTONE_1B_STORIES.md](MILESTONE_1B_STORIES.md) | Security, Transactions & Observability     | 3–4      | 147     | ~443       | ✅     |
| [MILESTONE_2A_STORIES.md](MILESTONE_2A_STORIES.md) | Trading & Compliance Core                  | 5–9      | 125     | 372        | ✅     |
| [MILESTONE_2B_STORIES.md](MILESTONE_2B_STORIES.md) | Post-Trade, Risk & Reporting               | 8–10     | 104     | 316        | ✅     |
| [MILESTONE_3A_STORIES.md](MILESTONE_3A_STORIES.md) | Data Governance, AI & Platform Ops         | 11–12    | 56      | 164        | ✅     |
| [MILESTONE_3B_STORIES.md](MILESTONE_3B_STORIES.md) | Admin Portal, SDK & Workflows              | 13–14    | 58      | 154        | ✅     |
| [MILESTONE_3C_STORIES.md](MILESTONE_3C_STORIES.md) | Operations, Compliance Portals & Packaging | 15–16    | 56      | 167        | ✅     |
| [MILESTONE_4_STORIES.md](MILESTONE_4_STORIES.md)   | Integration Testing, Chaos & GA            | 17–30    | 30      | 94         | ✅     |
| **TOTAL**                                          |                                            | **1–30** | **654** | **~1,930** |        |

---

## 2. EPIC INDEX — 42 EPICS PLUS GA WORKSTREAM

| Epic Code | Epic Name                           | Milestone File | Stories | ID Range              | Team          |
| --------- | ----------------------------------- | -------------- | ------- | --------------------- | ------------- |
| K-05      | Event Bus & Messaging               | M1A            | 32      | K05-001 → K05-032     | Alpha         |
| K-07      | Audit Framework                     | M1A            | 16      | K07-001 → K07-016     | Alpha         |
| K-02      | Configuration Engine                | M1A            | 17      | K02-001 → K02-017     | Alpha         |
| K-15      | Dual-Calendar (BS/Gregorian)        | M1A            | 13      | K15-001 → K15-013     | Alpha         |
| K-01      | Identity & Access Management (IAM)  | M1B            | 23      | K01-001 → K01-023     | Alpha         |
| K-14      | Secrets Management                  | M1B            | 14      | K14-001 → K14-014     | Alpha         |
| K-16      | Ledger Framework                    | M1B            | 19      | K16-001 → K16-019     | Alpha         |
| K-17      | Distributed Transaction Coordinator | M1B            | 14      | K17-001 → K17-014     | Alpha         |
| K-18      | Resilience Patterns                 | M1B            | 13      | K18-001 → K18-013     | Alpha         |
| K-03      | Rules Engine                        | M1B            | 14      | K03-001 → K03-014     | Alpha         |
| K-04      | Plugin Runtime                      | M1B            | 15      | K04-001 → K04-015     | Alpha         |
| K-06      | Observability                       | M1B            | 22      | K06-001 → K06-022     | Zeta          |
| K-11      | API Gateway                         | M1B            | 13      | K11-001 → K11-013     | Alpha         |
| D-11      | Reference Data                      | M2A            | 13      | D11-001 → D11-013     | Gamma         |
| D-04      | Market Data                         | M2A            | 15      | D04-001 → D04-015     | Gamma         |
| D-01      | Order Management (OMS)              | M2A            | 21      | D01-001 → D01-021     | Beta          |
| D-07      | Compliance Engine                   | M2A            | 17      | D07-001 → D07-017     | Gamma         |
| D-14      | Sanctions Screening                 | M2A            | 16      | D14-001 → D14-016     | Gamma         |
| D-06      | Risk Engine                         | M2A            | 21      | D06-001 → D06-021     | Beta          |
| D-02      | Execution Management (EMS)          | M2A            | 22      | D02-001 → D02-022     | Beta          |
| D-09      | Post-Trade Settlement               | M2B            | 18      | D09-001 → D09-018     | Delta         |
| D-13      | Client Money Reconciliation         | M2B            | 18      | D13-001 → D13-018     | Delta         |
| D-03      | Portfolio Management (PMS)          | M2B            | 13      | D03-001 → D03-013     | Delta         |
| D-05      | Pricing Engine                      | M2B            | 12      | D05-001 → D05-012     | Beta          |
| D-08      | Market Surveillance                 | M2B            | 16      | D08-001 → D08-016     | Gamma         |
| D-10      | Regulatory Reporting                | M2B            | 13      | D10-001 → D10-013     | Gamma         |
| D-12      | Corporate Actions                   | M2B            | 14      | D12-001 → D12-014     | Delta         |
| K-08      | Data Governance                     | M3A            | 14      | K08-001 → K08-014     | Gamma         |
| K-09      | AI Governance                       | M3A            | 15      | K09-001 → K09-015     | Gamma         |
| K-19      | DLQ Management                      | M3A            | 15      | K19-001 → K19-015     | Alpha         |
| K-10      | Deployment Abstraction              | M3A            | 12      | K10-001 → K10-012     | Zeta          |
| K-13      | Admin Portal                        | M3B            | 14      | K13-001 → K13-014     | Epsilon       |
| K-12      | Platform SDK                        | M3B            | 15      | K12-001 → K12-015     | Alpha         |
| W-01      | Workflow Orchestration              | M3B            | 16      | W01-001 → W01-016     | Alpha         |
| W-02      | Client Onboarding                   | M3B            | 13      | W02-001 → W02-013     | Epsilon       |
| O-01      | Operator Console                    | M3C            | 14      | O01-001 → O01-014     | Alpha/Epsilon |
| P-01      | Pack Certification                  | M3C            | 11      | P01-001 → P01-011     | Alpha/Zeta    |
| R-01      | Regulator Portal                    | M3C            | 11      | R01-001 → R01-011     | Epsilon/Alpha |
| R-02      | Incident Response                   | M3C            | 12      | R02-001 → R02-012     | Alpha/Zeta    |
| PU-004    | Platform Manifest                   | M3C            | 8       | PU004-001 → PU004-008 | Zeta          |
| T-01      | Integration Testing                 | M4             | 14      | T01-001 → T01-014     | Zeta          |
| T-02      | Chaos Engineering                   | M4             | 10      | T02-001 → T02-010     | Zeta          |
| GA        | GA Hardening & Sign-Off             | M4             | 6       | GA-001 → GA-006       | Alpha/Zeta    |

---

## 3. TEAM ALLOCATION SUMMARY

| Team                             | Specialization                                                   | Primary Epics                                                                                                      | Stories Owned |
| -------------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ | ------------- |
| **Alpha** — Platform Kernel      | Platform services, SDK, workflow, operations                     | K-02, K-03, K-04, K-05, K-07, K-12, K-13, K-14, K-15, K-16, K-17, K-18, K-19, W-01, O-01, P-01, R-01, R-02, PU-004 | ~280          |
| **Beta** — Trading Backend       | OMS, EMS, risk, pricing                                          | D-01, D-02, D-05, D-06                                                                                             | ~67           |
| **Gamma** — Data & Compliance    | Market data, reference data, compliance, surveillance, reporting | D-04, D-07, D-08, D-10, D-11, D-14, K-08, K-09                                                                     | ~108          |
| **Delta** — Post-Trade & Finance | Settlement, reconciliation, PMS, corporate actions               | D-03, D-09, D-12, D-13                                                                                             | ~56           |
| **Epsilon** — Frontend & Portals | Admin UI, regulator portal, client onboarding UI, marketplace    | K-13, W-02, O-01 (UI), R-01 (UI), P-01 (UI)                                                                        | ~55           |
| **Zeta** — DevOps & Infra        | K8s, CI/CD, observability infra, deployment, testing             | K-06, K-10, T-01, T-02, GA, P-01-F02, R-02 (DevOps)                                                                | ~75           |
| _Multi-team_                     | Stories shared across teams                                      | Various                                                                                                            | ~30           |

---

## 4. SPRINT PLAN OVERVIEW

| Sprint | Milestone | Primary Focus                                                                                          | Completing Teams     |
| ------ | --------- | ------------------------------------------------------------------------------------------------------ | -------------------- |
| 1      | M1A       | K-05 Event Bus core + K-07 Audit bootstrap                                                             | Alpha                |
| 2      | M1A       | K-02 Config Engine + K-15 Dual-Calendar                                                                | Alpha                |
| 3      | M1B       | K-01 IAM + K-14 Secrets                                                                                | Alpha                |
| 4      | M1B       | K-16 Ledger + K-17 DTC + K-18 Resilience + K-03 Rules + K-04 Plugin + K-06 Observability + K-11 API GW | Alpha, Zeta          |
| 5      | M2A       | D-11 Ref Data + D-04 Market Data + D-01 OMS start                                                      | Gamma, Beta          |
| 6      | M2A       | D-07 Compliance + D-14 Sanctions + D-06 Risk + D-01 OMS cont.                                          | Gamma, Beta          |
| 7      | M2A       | D-02 EMS                                                                                               | Beta                 |
| 8      | M2A/M2B   | D-02 EMS finish + D-09 Settlement start                                                                | Beta, Delta          |
| 9      | M2B       | D-13 Reconciliation + D-03 PMS + D-05 Pricing                                                          | Delta, Beta          |
| 10     | M2B       | D-08 Surveillance + D-10 Reg Reporting + D-12 Corporate Actions                                        | Gamma, Delta         |
| 11     | M3A       | K-08 Data Governance + K-09 AI Governance                                                              | Gamma                |
| 12     | M3A       | K-19 DLQ + K-10 Deployment Abstraction                                                                 | Alpha, Zeta          |
| 13     | M3B       | K-13 Admin Portal + K-12 Platform SDK + W-01 Workflow Orchestration                                    | Epsilon, Alpha       |
| 14     | M3B       | W-02 Client Onboarding + W-01 complete                                                                 | Epsilon, Alpha       |
| 15     | M3C       | O-01 Operator Console + P-01 F01-F03 + R-01 F01-F02 + R-02 F01-F02 + PU-004 F01-F02                    | Alpha, Epsilon, Zeta |
| 16     | M3C       | O-01 complete + P-01 F04-F05 + R-01 F03-F05 + R-02 F03-F05 + PU-004 F03-F04                            | Alpha, Epsilon, Zeta |
| 17     | M4        | T-01: E2E order-to-settlement + compliance + data integrity                                            | Gamma, Zeta          |
| 18     | M4        | T-01: E2E plugin + platform upgrade tests                                                              | Zeta                 |
| 19     | M4        | T-01: Performance baseline (OMS, event bus, API gateway)                                               | Zeta                 |
| 20     | M4        | T-01: DB performance + service contract tests                                                          | Zeta                 |
| 21     | M4        | T-01: plugin contracts + T-02: pod failure + network partition chaos                                   | Zeta                 |
| 22     | M4        | T-02: Resource exhaustion + dependency failure + latency injection                                     | Zeta                 |
| 23     | M4        | T-02: Poison pill + DR drill + backup restore                                                          | Zeta                 |
| 24     | M4        | T-02: Resilience scorecard + continuous chaos schedule                                                 | Zeta                 |
| 25     | M4/GA     | GA: Security pen test + CIS benchmark                                                                  | Zeta                 |
| 26     | M4/GA     | GA: SLA sign-off + runbook library                                                                     | Alpha, Zeta          |
| 27     | M4/GA     | GA: Buffer / remediation sprint                                                                        | All                  |
| 28     | M4/GA     | GA: Readiness checklist gate                                                                           | Alpha                |
| 29     | M4/GA     | GA: Final hardening + data migration rehearsal                                                         | Zeta                 |
| 30     | M4/GA     | GA: Launch + hypercare period tracking                                                                 | All                  |

---

## 5. STORY COUNT BY MILESTONE

```
M1A  ████████████████████████  78 stories  (11.9%)
M1B  ███████████████████████████████████████████  147 stories  (22.5%)
M2A  █████████████████████████████████████  125 stories  (19.1%)
M2B  ████████████████████████████████  104 stories  (15.9%)
M3A  █████████████████  56 stories  (8.6%)
M3B  █████████████████  58 stories  (8.9%)
M3C  █████████████████  56 stories  (8.6%)
M4   █████████  30 stories  (4.6%)
                                          Total: 654 stories
```

---

## 6. STORY POINTS BY MILESTONE

| Milestone | Story Points | % of Total |
| --------- | ------------ | ---------- |
| M1A       | ~220         | 11.4%      |
| M1B       | ~443         | 23.0%      |
| M2A       | 372          | 19.3%      |
| M2B       | 316          | 16.4%      |
| M3A       | 164          | 8.5%       |
| M3B       | 154          | 8.0%       |
| M3C       | 167          | 8.7%       |
| M4        | 94           | 4.9%       |
| **TOTAL** | **~1,930**   | **100%**   |

> Assumes **2-week sprints** at average team velocity of **~120 SP/sprint** across all 6 teams combined.

---

## 7. DEPENDENCY CHAIN (Critical Path)

```
K-05 (Event Bus) ──────────┐
K-07 (Audit)    ───────────┤
K-02 (Config)   ───────────┤──→ K-01 (IAM) ──→ D-01 (OMS) ──→ D-02 (EMS) ──→ D-09 (Settlement) ──→ GA
K-15 (Calendar) ───────────┤         │              │
K-16 (Ledger)   ───────────┤         │              └──→ D-06 (Risk) ──→ D-08 (Surveillance)
K-17 (DTC)      ───────────┘         └──→ D-07 (Compliance) ──→ D-10 (Reg Reporting) ──→ R-01 (Regulator Portal)
                                                                 D-14 (Sanctions)
K-04 (Plugin)  ──→ K-12 (SDK) ──→ P-01 (Certification) ──→ Marketplace
K-10 (Deploy)  ──→ T-01/T-02 (Testing) ──→ GA Hardening
```

---

## 8. CROSS-REFERENCE: EPIC CODE → MODULE CODE

For complete module coverage, use `../lld/LLD_INDEX.md` as the master LLD catalog.

| Module Code | Epic Code                           | LLD Document                                                                                            |
| ----------- | ----------------------------------- | ------------------------------------------------------------------------------------------------------- |
| K-01        | IAM                                 | [LLD_K01_IAM.md](../lld/LLD_K01_IAM.md)                                                                 |
| K-02        | Configuration Engine                | [LLD_K02_CONFIGURATION_ENGINE.md](../lld/LLD_K02_CONFIGURATION_ENGINE.md)                               |
| K-03        | Rules Engine                        | [LLD_K03_RULES_ENGINE.md](../lld/LLD_K03_RULES_ENGINE.md)                                               |
| K-04        | Plugin Runtime                      | [LLD_K04_PLUGIN_RUNTIME.md](../lld/LLD_K04_PLUGIN_RUNTIME.md)                                           |
| K-05        | Event Bus                           | [LLD_K05_EVENT_BUS.md](../lld/LLD_K05_EVENT_BUS.md)                                                     |
| K-06        | Observability                       | [LLD_K06_OBSERVABILITY.md](../lld/LLD_K06_OBSERVABILITY.md)                                             |
| K-07        | Audit Framework                     | [LLD_K07_AUDIT_FRAMEWORK.md](../lld/LLD_K07_AUDIT_FRAMEWORK.md)                                         |
| K-08        | Data Governance                     | [LLD_K08_DATA_GOVERNANCE.md](../lld/LLD_K08_DATA_GOVERNANCE.md)                                         |
| K-09        | AI Governance                       | [LLD_K09_AI_GOVERNANCE.md](../lld/LLD_K09_AI_GOVERNANCE.md)                                             |
| K-10        | Deployment Abstraction              | [LLD_K10_DEPLOYMENT_ABSTRACTION.md](../lld/LLD_K10_DEPLOYMENT_ABSTRACTION.md)                           |
| K-11        | API Gateway                         | [LLD_K11_API_GATEWAY.md](../lld/LLD_K11_API_GATEWAY.md)                                                 |
| K-12        | Platform SDK                        | [LLD_K12_PLATFORM_SDK.md](../lld/LLD_K12_PLATFORM_SDK.md)                                               |
| K-13        | Admin Portal                        | [LLD_K13_ADMIN_PORTAL.md](../lld/LLD_K13_ADMIN_PORTAL.md)                                               |
| K-14        | Secrets                             | [LLD_K14_SECRETS_MANAGEMENT.md](../lld/LLD_K14_SECRETS_MANAGEMENT.md)                                   |
| K-15        | Dual-Calendar                       | [LLD_K15_DUAL_CALENDAR.md](../lld/LLD_K15_DUAL_CALENDAR.md)                                             |
| K-16        | Ledger Framework                    | [LLD_K16_LEDGER_FRAMEWORK.md](../lld/LLD_K16_LEDGER_FRAMEWORK.md)                                       |
| K-17        | Distributed Transaction Coordinator | [LLD_K17_DISTRIBUTED_TRANSACTION_COORDINATOR.md](../lld/LLD_K17_DISTRIBUTED_TRANSACTION_COORDINATOR.md) |
| K-18        | Resilience Patterns                 | [LLD_K18_RESILIENCE_PATTERNS.md](../lld/LLD_K18_RESILIENCE_PATTERNS.md)                                 |
| K-19        | DLQ Management                      | [LLD_K19_DLQ_MANAGEMENT.md](../lld/LLD_K19_DLQ_MANAGEMENT.md)                                           |
| D-01        | OMS                                 | [LLD_D01_OMS.md](../lld/LLD_D01_OMS.md)                                                                 |
| D-13        | Client Money Reconciliation         | [LLD_D13_CLIENT_MONEY_RECONCILIATION.md](../lld/LLD_D13_CLIENT_MONEY_RECONCILIATION.md)                 |
| D-14        | Sanctions Screening                 | [LLD_D14_SANCTIONS_SCREENING.md](../lld/LLD_D14_SANCTIONS_SCREENING.md)                                 |

---

## 9. DEFINITION OF DONE (All Stories)

All stories across all milestones share the following DoD:

- [ ] Code reviewed (≥2 reviewers), merged to main
- [ ] Unit tests ≥ 80% coverage
- [ ] Integration tests passing in CI
- [ ] K-07 audit events emitted for all state changes
- [ ] K-06 metrics/traces instrumented (Prometheus + OpenTelemetry)
- [ ] API spec updated (OpenAPI 3.1) or event schema registered in K-08
- [ ] No critical lint/security violations
- [ ] Deployed to staging environment
- [ ] Demo accepted by Product Owner
- [ ] Documentation updated (LLD or updated README)

---

## 10. GLOSSARY OF STORY ID PREFIXES

| Prefix | Module     | Description                                          |
| ------ | ---------- | ---------------------------------------------------- |
| K      | Kernel     | Platform infrastructure services (K-01 through K-19) |
| D      | Domain     | Financial domain services (D-01 through D-14)        |
| W      | Workflow   | Workflow and process automation (W-01, W-02)         |
| O      | Operator   | Operator-facing features (O-01)                      |
| P      | Packs      | Pack certification program (P-01)                    |
| R      | Regulatory | Regulatory and incident (R-01, R-02)                 |
| PU     | Packaging  | Release manifest and packaging (PU-004)              |
| T      | Testing    | Test framework epics (T-01, T-02)                    |
| GA     | GA         | General availability hardening and sign-off          |

---

_Generated by: AI Delivery Planning Agent_
_Project: Siddhanta — Jurisdiction-Neutral Capital Markets OS_
_Program: Enterprise Agile Program v3_
