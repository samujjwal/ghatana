# Document Authority Map

Status: Repository authority and document-role map for AppPlatform documentation  
Last updated: 2026-01-19 (updated: Level 2 authority updated to UNIFIED_IMPLEMENTATION_PLAN)

---

## Purpose

This document classifies the active repository documents by authority role so edits do not accidentally treat supporting material as normative baseline.

Use this together with `README.md`, `UNIFIED_IMPLEMENTATION_PLAN.md`, `WEEK_BY_WEEK_IMPLEMENTATION_PLAN.md`, `stories/STORY_INDEX.md`, `epics/DEPENDENCY_MATRIX.md`, and `adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md` when reconciling conflicts.

---

## Normative Baseline

These documents control active implementation choices when conflicts appear elsewhere.

| Authority Level | Document / Set                                                        | Role                                                                                                    |
| --------------- | --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| 1               | `adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md` | Canonical stack and implementation-technology authority                                                 |
| 2               | `UNIFIED_IMPLEMENTATION_PLAN.md`                                      | Canonical sequencing, milestone order, story counts, and execution baseline (supersedes archived plans) |
| 3               | `WEEK_BY_WEEK_IMPLEMENTATION_PLAN.md`                                 | Canonical sprint/week execution cadence, readiness gates, and delivery checkpoints                       |
| 3               | `DOMAIN_PACK_INTERFACE_SPECIFICATION.md`                              | Canonical contract between domain packs and the Platform Kernel                                         |
| 3               | `DOMAIN_PACK_DEVELOPMENT_GUIDE.md`                                    | Normative guide for domain pack authors; informative for kernel developers                              |
| 3               | `POLYGLOT_RULE_EXECUTION_ENGINE.md`                                   | Canonical T2/T3 rule execution contract for K-03 Rules Engine                                           |
| 3               | `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md`                             | Canonical inter-pack communication and event routing contract                                           |
| 3               | `epics/`                                                              | Canonical scope, functional requirements, dependencies, and acceptance boundaries                       |
| 3               | `lld/`                                                                | Canonical low-level design, contracts, data models, and implementation detail                           |
| 4               | `stories/STORY_INDEX.md`                                              | Canonical normalized backlog counts and sprint-level execution index                                    |
| 4               | `stories/MILESTONE_*_STORIES.md`                                      | Canonical story-pack execution detail when aligned to the story index                                   |

---

## Controlled Reference

These documents are authoritative for specific fact families, but they do not override the normative implementation baseline above.

| Document                                | Role                                                                                                |
| --------------------------------------- | --------------------------------------------------------------------------------------------------- |
| `docs/Authoritative_Source_Register.md` | Canonical source register for time-sensitive external facts and operating assumptions               |
| `docs/Legal_Claim_Citation_Appendix.md` | Canonical traceability appendix for legal and regulatory claims                                     |
| `docs/Claim_Traceability_Matrix.md`     | Navigation aid linking active claims to ASR/LCA references                                          |
| `epics/DEPENDENCY_MATRIX.md`            | Canonical explanation of dependency ordering, including the intentional K-05 / K-07 bootstrap cycle |
| `adr/ADR-004_DUAL_CALENDAR_SYSTEM.md`   | Canonical decision record for K-15 Multi-Calendar Service architecture and `CalendarDate` type      |

---

## Supporting Design And Strategy

These documents provide context, synthesis, or narrative packaging. They should be aligned to the baseline, but they do not override it.

| Document / Set                                              | Role                                                                                            |
| ----------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `README.md`                                                 | Top-level orientation and authority summary                                                     |
| `architecture/`                                             | Architecture synthesis and sectional design narrative                                           |
| `c4/`                                                       | Structural visualization and model context                                                      |
| `docs/siddhanta.md`                                         | **[Capital Markets Domain Pack]** Strategic Nepal-first narrative and product positioning       |
| `docs/Siddhanta_Platform_Specification.md`                  | **[Capital Markets Domain Pack]** Nepal-specific implementation-oriented supporting spec        |
| `../finance-ghatana-integration-plan.md`                    | **[Capital Markets Domain Pack]** Ghatana infra integration plan for the Siddhanta reference pack |
| `docs/All_In_One_Capital_Markets_Platform_Specification.md` | Cross-jurisdiction architecture-first supporting specification                                  |
| `archive/plans/DELIVERY_PROGRAM_PLAN.md`                    | Historical long-form delivery decomposition and staffing model                                  |
| `stories/STORY_BACKLOG.md`                                  | Long-form story template catalog and representative detail                                      |
| `regulatory/`                                               | Regulator-facing or compliance-supporting documentation                                         |
| `MARKETPLACE_GOVERNANCE.md`                                 | Domain pack marketplace governance model (non-normative until P-01 epic is implemented)         |
| `DOMAIN_PACK_UPGRADE_RUNBOOK.md`                            | Operational runbook for domain pack upgrades (non-normative; superseded by P-01 LLD once ready) |

---

## Prompt And Generation Artifacts

These documents guide content generation or TDD expansion. They are procedural aids, not implementation authority.

| Document / Set                                 | Role                                                                                     |
| ---------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `docs/capital_markets_platform_prompt_v2.1.md` | Epic/spec generation prompt standard                                                     |
| `docs/tdd_*`                                   | Test-spec generation, expansion, and review artifacts                                    |
| `docs/tdd_spec_banking_integration_v1.md`      | Banking domain pack integration test spec (draft)                                        |
| `archive/reviews/2026-03/`                    | Historical review, audit, and update artifacts retained for traceability only             |
| `archive/`                                     | Historical prompts, snapshots, and superseded review material                            |

---

## Conflict Rules

1. If a supporting document conflicts with ADR-011 on stack choice, ADR-011 wins.
2. If a supporting document conflicts with the current milestone order, `UNIFIED_IMPLEMENTATION_PLAN.md` wins.
3. If sprint dates, week-level sequencing, or execution checkpoints conflict with higher-level summaries, `WEEK_BY_WEEK_IMPLEMENTATION_PLAN.md` wins unless ADR-011 says otherwise.
4. If a backlog total or story-point total conflicts with a long-form plan or story catalog, `stories/STORY_INDEX.md` wins.
5. If a time-sensitive market fact conflicts with narrative text, `docs/Authoritative_Source_Register.md` wins.
6. If a legal claim conflicts with narrative text, `docs/Legal_Claim_Citation_Appendix.md` wins.
7. If a dependency statement conflicts with an older strict-DAG assumption, `epics/DEPENDENCY_MATRIX.md` wins.
8. If a top-level summary or index label conflicts with an epic, milestone summary, or current story index, the canonical epic/milestone/story-index naming wins and the summary document must be refreshed.

---

## Editing Guidance

1. Update the controlling document first, then propagate the change to supporting documents.
2. Do not convert a provisional tax or market fact into a hard requirement unless it is pinned in the ASR or legal appendix.
3. Keep supporting documents explicit about whether a statement is clause-verified, document-verified, project policy, or a revalidation assumption.
4. Treat process templates, task schemas, routing policies, and value catalogs as normative implementation detail only when they are defined in current epic/LLD baseline documents; supporting docs may describe them but should not override their contracts or compatibility rules.
5. When updating epic/story counts or labels, refresh any affected top-level summaries in `README.md`, milestone headers, and index tables in the same pass.
