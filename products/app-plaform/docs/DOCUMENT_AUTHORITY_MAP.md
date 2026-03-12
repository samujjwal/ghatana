# Document Authority Map

Status: Repository authority and document-role map for active Project Siddhanta documentation  
Last updated: March 10, 2026

---

## Purpose

This document classifies the active repository documents by authority role so edits do not accidentally treat supporting material as normative baseline.

Use this together with `README.md`, `plans/CURRENT_EXECUTION_PLAN.md`, `stories/STORY_INDEX.md`, `epics/DEPENDENCY_MATRIX.md`, and `adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md` when reconciling conflicts.

---

## Normative Baseline

These documents control active implementation choices when conflicts appear elsewhere.

| Authority Level | Document / Set                                                        | Role                                                                              |
| --------------- | --------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| 1               | `adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md` | Canonical stack and implementation-technology authority                           |
| 2               | `plans/CURRENT_EXECUTION_PLAN.md`                                     | Canonical sequencing, milestone order, and execution baseline                     |
| 3               | `epics/`                                                              | Canonical scope, functional requirements, dependencies, and acceptance boundaries |
| 3               | `lld/`                                                                | Canonical low-level design, contracts, data models, and implementation detail     |
| 4               | `stories/STORY_INDEX.md`                                              | Canonical normalized backlog counts and sprint-level execution index              |
| 4               | `stories/MILESTONE_*_STORIES.md`                                      | Canonical story-pack execution detail when aligned to the story index             |

---

## Controlled Reference

These documents are authoritative for specific fact families, but they do not override the normative implementation baseline above.

| Document                                | Role                                                                                                |
| --------------------------------------- | --------------------------------------------------------------------------------------------------- |
| `docs/Authoritative_Source_Register.md` | Canonical source register for time-sensitive external facts and operating assumptions               |
| `docs/Legal_Claim_Citation_Appendix.md` | Canonical traceability appendix for legal and regulatory claims                                     |
| `docs/Claim_Traceability_Matrix.md`     | Navigation aid linking active claims to ASR/LCA references                                          |
| `epics/DEPENDENCY_MATRIX.md`            | Canonical explanation of dependency ordering, including the intentional K-05 / K-07 bootstrap cycle |

---

## Supporting Design And Strategy

These documents provide context, synthesis, or narrative packaging. They should be aligned to the baseline, but they do not override it.

| Document / Set                                              | Role                                                            |
| ----------------------------------------------------------- | --------------------------------------------------------------- |
| `README.md`                                                 | Top-level orientation and authority summary                     |
| `architecture/`                                             | Architecture synthesis and sectional design narrative           |
| `c4/`                                                       | Structural visualization and model context                      |
| `docs/siddhanta.md`                                         | Strategic Nepal-first narrative and product positioning         |
| `docs/Siddhanta_Platform_Specification.md`                  | Nepal-specific implementation-oriented supporting specification |
| `docs/All_In_One_Capital_Markets_Platform_Specification.md` | Cross-jurisdiction architecture-first supporting specification  |
| `plans/DELIVERY_PROGRAM_PLAN.md`                            | Long-form delivery decomposition and staffing model             |
| `stories/STORY_BACKLOG.md`                                  | Long-form story template catalog and representative detail      |
| `regulatory/`                                               | Regulator-facing or compliance-supporting documentation         |

---

## Prompt And Generation Artifacts

These documents guide content generation or TDD expansion. They are procedural aids, not implementation authority.

| Document / Set                                 | Role                                                          |
| ---------------------------------------------- | ------------------------------------------------------------- |
| `docs/capital_markets_platform_prompt_v2.1.md` | Epic/spec generation prompt standard                          |
| `docs/tdd_*`                                   | Test-spec generation, expansion, and review artifacts         |
| `archive/`                                     | Historical prompts, snapshots, and superseded review material |

---

## Conflict Rules

1. If a supporting document conflicts with ADR-011 on stack choice, ADR-011 wins.
2. If a supporting document conflicts with the current milestone order, `plans/CURRENT_EXECUTION_PLAN.md` wins.
3. If a backlog total or story-point total conflicts with a long-form plan or story catalog, `stories/STORY_INDEX.md` wins.
4. If a time-sensitive market fact conflicts with narrative text, `docs/Authoritative_Source_Register.md` wins.
5. If a legal claim conflicts with narrative text, `docs/Legal_Claim_Citation_Appendix.md` wins.
6. If a dependency statement conflicts with an older strict-DAG assumption, `epics/DEPENDENCY_MATRIX.md` wins.
7. If a top-level summary or index label conflicts with an epic, milestone summary, or current story index, the canonical epic/milestone/story-index naming wins and the summary document must be refreshed.

---

## Editing Guidance

1. Update the controlling document first, then propagate the change to supporting documents.
2. Do not convert a provisional tax or market fact into a hard requirement unless it is pinned in the ASR or legal appendix.
3. Keep supporting documents explicit about whether a statement is clause-verified, document-verified, project policy, or a revalidation assumption.
4. Treat process templates, task schemas, routing policies, and value catalogs as normative implementation detail only when they are defined in current epic/LLD baseline documents; supporting docs may describe them but should not override their contracts or compatibility rules.
5. When updating epic/story counts or labels, refresh any affected top-level summaries in `README.md`, milestone headers, and index tables in the same pass.
