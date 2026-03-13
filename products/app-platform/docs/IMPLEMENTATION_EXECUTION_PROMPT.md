# Gold-Standard Implementation Execution Prompt — AppPlatform

**Version**: 2.0.0  
**Date**: March 13, 2026  
**Status**: Active execution prompt  
**Purpose**: Concrete, repo-grounded implementation instructions for AppPlatform work

---

## 1. Mission

Implement AppPlatform work without inventing missing artifacts, bypassing platform contracts, or relying on stale planning assumptions.

Use this prompt for coding, design refinement, test creation, sprint execution, and documentation updates across the active AppPlatform repository.

---

## 2. Source Order

Read and obey sources in this order:

1. `adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md`
2. `DOCUMENT_AUTHORITY_MAP.md`
3. `UNIFIED_IMPLEMENTATION_PLAN.md`
4. `WEEK_BY_WEEK_IMPLEMENTATION_PLAN.md`
5. `stories/STORY_INDEX.md` and the relevant milestone story file it points to
6. `epics/README.md`, `epics/DEPENDENCY_MATRIX.md`, and the relevant epic file(s)
7. `lld/LLD_INDEX.md` and the relevant active LLD file(s), where they exist
8. `tdd_spec_master_index_v2.1.md` plus the relevant detailed or phase-level TDD file(s)
9. `../finance-ghatana-integration-plan.md` when Ghatana reuse is in scope
10. `Authoritative_Source_Register.md`, `Legal_Claim_Citation_Appendix.md`, and `Claim_Traceability_Matrix.md` when facts, regulatory language, or customer-facing claims are touched
11. `architecture/` and `c4/` only as supporting context

If sources disagree, follow the authority order above and update the lower-authority document in the same pass when appropriate.

---

## 3. Required Preflight

Before writing code or editing docs, produce a concrete working set:

- Story IDs or epic IDs being implemented
- Milestone and sprint from `stories/STORY_INDEX.md` when the task is backlog-driven; otherwise identify the explicit authority document authorizing the work
- Exact epic sections and active LLD sections that govern the change, or an explicit note that no active LLD exists for that scope
- Exact test specs that prove the change, or the exact validation mechanism when the task is documentation-only or governance-only
- Exact repo files that will change
- Exact Ghatana modules to reuse, extend, or intentionally avoid
- Exact cross-cutting responsibilities involved: K-02 config, K-05 events, K-06 observability, K-07 audit, K-15 calendar, K-09 AI governance where applicable

Do not proceed if the task still depends on placeholder files, imaginary modules, or generic "we will create later" assumptions.

---

## 4. Definition of Ready

The task is ready only if all of the following are true:

- The work is authorized by an active story, epic, or explicit baseline document.
- Dependencies listed in `epics/DEPENDENCY_MATRIX.md` are satisfied or intentionally handled.
- Input/output contracts are known: API, event, schema, workflow, config, and audit shape.
- Test cases or validation checks are identified before implementation starts.
- Reuse is explicit: which Ghatana library or platform component is used, and what remains net new.
- Any legal or factual wording changes have an ASR/LCA update path.

If any item is missing, stop and resolve the definition gap first.

---

## 5. Gold-Standard Execution Rules

### 5.1 Contract First

- Define or confirm events, schemas, API shapes, workflow metadata, and configuration keys before filling in service logic.
- Keep variable behavior in K-02-managed configuration, T1/T2/T3 packs, or governed metadata catalogs.
- Do not hardcode jurisdiction timelines, thresholds, calendars, tax behavior, or regulator-specific wording into kernel code.

### 5.2 Reuse Before Net New

- Consult `../finance-ghatana-integration-plan.md` for every kernel or platform service that may map to AEP, Data Cloud, workflow, AI integration, audit, resilience, HTTP, security, or SDK components.
- Reuse proven Ghatana components when the mapping exists.
- Extend an existing module, contract, schema, workflow, utility, or document before creating a new abstraction layer, top-level file, or service boundary.
- Reject copy-paste duplication unless it is a short-lived migration step with a removal plan.
- If net new code is required, explain why reuse is insufficient.

### 5.3 Right-Size the Solution

- Implement the smallest change that fully satisfies the story, epic contract, and required NFRs.
- Do not add speculative extensibility, new frameworks, or generic helper layers without a current second use case or an explicit epic/LLD requirement.
- Do not under-engineer by bypassing existing platform contracts, omitting audit/observability/config hooks, or leaving known edge cases to follow-up without recording them.
- Prefer modifying an existing file, service, schema, or workflow definition over introducing parallel structures that will immediately drift.

### 5.4 Test First

- Start from the relevant TDD spec, phase-level test spec, or documented validation checklist and the milestone story acceptance criteria.
- Add missing integration, performance, or failure-mode tests when the story or epic demands them.
- Do not mark work complete because code exists; mark it complete only when the defined tests and acceptance gates pass.

### 5.5 Cross-Cutting Requirements Always Apply

- K-07 audit for governed state changes
- K-06 metrics, logs, and traces for production behavior
- K-02 for runtime variability
- K-05 for event-driven state transitions and replayable history
- K-15 for date/calendar-sensitive behavior
- K-09 for any AI/ML inference, model access, or prompt execution

### 5.6 Documentation Is Part of the Change

- When a documentation change is required, update the controlling doc first, then refresh derived summaries, indexes, or prompts.
- Fix stale links, moved paths, and authority references in the same change.
- Archive, merge, or remove superseded active guidance instead of leaving duplicate instructions behind.
- Never leave a changed behavior documented only in code or only in a downstream summary.

### 5.7 No Speculation

- Do not reference files that are not in the repo.
- Do not claim a process, integration, SLA, or regulatory rule is live unless the active source set supports it.
- Do not use wildcard doc references where a real index document exists.

---

## 6. Required Output for Every Implementation Unit

At the end of the task, record:

- Stories and epics completed
- Source documents consulted
- Reuse applied from Ghatana
- Any net-new abstraction, file, or workflow and why reuse or consolidation was insufficient
- Tests added or executed
- Validation used when no code-level tests applied
- Docs updated
- Remaining risks, blocked dependencies, or follow-up stories

Use exact repo paths, not generic labels.

---

## 7. Definition of Done

Work is done only when:

- The relevant story acceptance criteria are satisfied
- Epic contracts are preserved, and active LLD contracts are preserved where they exist
- Tests or documented validation checks pass at the appropriate level
- Cross-cutting concerns are wired correctly
- Reuse and duplicate-avoidance checks are complete, and any net-new abstraction has a documented justification
- Documentation and traceability are updated
- No stale links or contradictory authority references remain in touched files
- Any open gap is explicitly documented with owner and next step

---

## 8. Prompt Body

Use the instruction block below when executing a concrete AppPlatform task:

> Implement the requested AppPlatform change by following the active repository baseline.  
> First, identify the exact story IDs, milestone file, epics, active LLDs if they exist, TDD specs, and reuse mappings that govern the work.  
> Follow this source order: `ADR-011` -> `DOCUMENT_AUTHORITY_MAP.md` -> `UNIFIED_IMPLEMENTATION_PLAN.md` -> `WEEK_BY_WEEK_IMPLEMENTATION_PLAN.md` -> `stories/STORY_INDEX.md` and the relevant milestone story file -> `epics/README.md`, `epics/DEPENDENCY_MATRIX.md`, and relevant epic files -> `lld/LLD_INDEX.md` and relevant active LLDs -> `tdd_spec_master_index_v2.1.md` and relevant detailed or phase-level TDD specs -> `../finance-ghatana-integration-plan.md` when reuse is in scope -> ASR/LCA trace docs when wording or claims change.  
> Do not invent missing files, services, environments, or commands. Use only repo paths that exist.  
> Implement contract-first, reuse-before-net-new, right-sized design, and test-first. Prefer extending an existing module or document over creating parallel abstractions, remove or archive superseded guidance when you replace it, and avoid both speculative frameworks and story-local hacks.  
> Apply K-02, K-05, K-06, K-07, K-15, and K-09 cross-cutting requirements whenever they are relevant.  
> Update the controlling documentation and any affected summaries or indexes in the same change.  
> Finish by reporting: stories completed, sources consulted, reuse decisions, any justified net-new artifacts, tests or validation executed, docs updated, and residual risks.

---

## 9. When Updating Documentation Instead of Code

If the task is documentation-only, the same rules still apply:

- Reconcile against the authority chain first
- Remove stale or duplicate guidance instead of layering on new text
- Archive or merge superseded active docs instead of leaving multiple active versions with overlapping instructions
- Prefer concrete repo references over narrative placeholders
- Refresh adjacent docs that now conflict
- Validate links after editing
