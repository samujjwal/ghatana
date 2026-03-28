# Repo-Wide Code Consistency, Naming, Duplication, and Deprecation Audit Prompt

## Purpose

Analyze the entire repository and identify all code-quality, consistency, naming, duplication, and deprecation issues. Then produce a **single detailed Markdown report** that contains:

1. A repo-wide findings summary
2. Detailed issue inventory by module/package/file
3. A normalized classification of issues
4. A prioritized, implementation-ready resolution plan
5. Repo-wide consistency standards to apply going forward
6. Concrete refactoring guidance that is coherent across the entire codebase

The final output must be highly actionable, technically rigorous, and optimized for execution by an engineering team working on a real production codebase.

---

## Core Objective

Perform a **deep repository audit** that detects and explains:

- Coding inconsistencies
- Naming inconsistencies
- Duplicate or near-duplicate logic
- Deprecated, legacy, obsolete, or transitional code
- Pattern drift across modules
- Structural inconsistencies that make the codebase harder to maintain, extend, test, or reason about

Then create a **detailed Markdown resolution plan** that brings the repository toward a single, consistent, production-grade standard.

---

## Audit Scope

Review the repository comprehensively, including but not limited to:

- Applications
- Libraries
- Shared packages
- UI code
- Backend services
- API layers
- Domain models
- DTOs / schemas / contracts
- Infrastructure / DevOps / IaC code
- Tests
- Build/configuration files
- Scripts/tooling
- Documentation that directly affects code usage or conventions

Do not stop at obvious surface-level issues. Inspect both macro-level architecture and micro-level implementation details.

---

## Working Rules

1. **Scan the full repository first** before making recommendations.
2. Do not make isolated file-level recommendations that conflict with broader repo patterns.
3. Identify the **current dominant patterns** in the repo and distinguish them from outliers.
4. Prefer recommendations that improve long-term consistency, maintainability, readability, and correctness.
5. Avoid unnecessary churn, but do not preserve poor patterns merely because they are common.
6. When multiple patterns exist, determine:
   - which one should become the standard,
   - why it should become the standard,
   - what should be migrated,
   - what can remain temporarily,
   - and what must be removed immediately.
7. Be explicit about tradeoffs.
8. Do not produce vague advice. Every issue must include concrete evidence and a concrete remediation path.
9. Treat this as a production-grade audit, not a lightweight review.
10. Favor durable, scalable standards over one-off fixes.

---

## What to Identify

### 1. Coding Issues

Identify inconsistencies or problems such as:

- Inconsistent code style or structure across modules
- Mixed implementation patterns for similar responsibilities
- Weak separation of concerns
- Unclear abstraction boundaries
- Overly large classes/functions/components/modules
- Dead code or unreachable code
- Misplaced business logic
- Inconsistent error handling patterns
- Inconsistent validation patterns
- Inconsistent logging/observability patterns
- Inconsistent async/concurrency handling
- Inconsistent dependency injection or initialization patterns
- Inconsistent state handling patterns
- Inconsistent API/controller/service/repository structure
- Repeated anti-patterns
- Fragile or confusing control flow
- Inconsistent test structure or missing test alignment with production patterns

### 2. Naming Issues

Identify naming problems such as:

- Ambiguous names
- Overloaded names
- Misleading names
- Inconsistent file/folder/package naming
- Inconsistent class/component/type/interface/function/variable naming
- Multiple terms used for the same concept
- Same term used for different concepts
- Abbreviations without clear meaning
- Names not aligned with domain language
- Names that describe implementation instead of responsibility
- Mismatch between name and actual behavior
- Singular/plural inconsistencies
- Inconsistent suffix/prefix conventions
- Inconsistent naming between frontend/backend/domain/API/database layers

### 3. Duplicate Issues

Identify and classify:

- Exact duplicate logic
- Near-duplicate logic
- Duplicate utilities/helpers
- Duplicate UI patterns/components
- Duplicate data transformation/mapping logic
- Duplicate validation logic
- Duplicate constants/configs/enums
- Duplicate contract/schema definitions
- Duplicate business rules implemented in multiple places
- Copy-paste variants with slight drift
- Reinvented abstractions where a shared reusable module should exist

For each duplication finding, determine whether the right fix is:

- deletion,
- consolidation,
- extraction into shared abstraction,
- standardization around one implementation,
- or intentional retention with clear justification.

### 4. Deprecation / Legacy Issues

Identify:

- Explicitly deprecated code
- Implicitly obsolete code
- Transitional code that should have been removed
- Legacy APIs still in use without migration plan
- Backward-compatibility shims that are no longer needed
- Old feature flags that can be removed
- Deprecated libraries/framework APIs
- Deprecated internal abstractions
- Old versions of utilities/helpers still lingering
- Dead migration code
- Shadow implementations during incomplete refactors

For deprecations, determine:

- what is deprecated,
- why it exists,
- whether it is still referenced,
- migration risk,
- safe removal strategy,
- and sequencing for cleanup.

---

## Required Analysis Dimensions

For each finding, analyze through these lenses where relevant:

- Readability
- Consistency
- Maintainability
- Extensibility
- Testability
- Correctness risk
- Regression risk
- Performance implications
- Security implications
- Operational implications
- Developer experience impact
- Architectural alignment
- Domain-model alignment

---

## Normalization Requirement

Do not merely list issues. Normalize them.

For each class of issue, determine the **target standard** for the repo. Examples:

- Standard naming convention for modules, files, classes, hooks, DTOs, services, repositories, handlers, jobs, scripts, tests
- Standard folder/package/module organization pattern
- Standard duplication-reduction strategy
- Standard approach for deprecating and removing old code
- Standard error handling pattern
- Standard mapping/transformation pattern
- Standard testing layout and naming scheme

The output should make it easy for the repo to converge on one consistent model.

---

## Output Requirements

Create a **single Markdown file** as the final output.

The Markdown file must contain the following sections.

# 1. Executive Summary

Provide:

- Overall repository health summary
- Top systemic consistency issues
- Top naming issues
- Top duplication issues
- Top deprecation/legacy issues
- Overall maintainability assessment
- Recommended urgency level

# 2. Audit Methodology

Explain briefly:

- how the repository was evaluated,
- how patterns were compared,
- how duplicates were identified,
- how deprecations were inferred,
- and how target standards were chosen.

# 3. Repo-Wide Standardization Recommendations

Define the desired target state across the repository, including:

- Naming conventions
- Code organization conventions
- Reuse/shared abstraction rules
- Deprecation lifecycle rules
- Removal rules for legacy code
- Preferred implementation patterns for repeated responsibilities

# 4. Detailed Findings Inventory

Organize findings by:

- domain / product / app / package / module / folder / file

For each finding include:

- Finding ID
- Severity: Critical / High / Medium / Low
- Category: Coding / Naming / Duplication / Deprecation / Mixed
- Location(s)
- Issue summary
- Why it is a problem
- Evidence
- Recommended target state
- Recommended action
- Migration notes
- Dependencies / blockers
- Regression risk
- Estimated effort

# 5. Cross-Cutting Pattern Drift Analysis

Call out inconsistent patterns that appear in multiple places, such as:

- multiple service patterns,
- multiple controller patterns,
- multiple DTO/schema conventions,
- multiple state management approaches,
- multiple component composition styles,
- multiple helper utility styles,
- inconsistent test conventions,
- inconsistent package boundaries,
- inconsistent naming vocabularies.

# 6. Duplicate Code Consolidation Plan

Provide:

- duplicate clusters,
- source-of-truth recommendation,
- what to merge/delete/extract,
- shared module candidates,
- refactor sequence,
- and verification steps.

# 7. Deprecation and Legacy Cleanup Plan

Provide:

- deprecated/legacy inventory,
- safe deletion candidates,
- migration-first candidates,
- compatibility risk,
- rollback considerations,
- and phased removal sequencing.

# 8. Prioritized Resolution Roadmap

Create a phased execution plan, for example:

- Phase 0: Safety / visibility / inventory
- Phase 1: Critical naming and structural alignment
- Phase 2: Duplicate consolidation
- Phase 3: Deprecation cleanup
- Phase 4: Standardization enforcement
- Phase 5: Ongoing guardrails

For each phase include:

- objective,
- exact work items,
- sequencing,
- expected impact,
- validation approach,
- and rollout considerations.

# 9. Guardrails to Prevent Reintroduction

Recommend concrete controls such as:

- lint rules
- static analysis
- architecture rules
- naming validation
- code generation/templates
- CI checks
- duplication detection tooling
- deprecation policy and removal SLAs
- PR review checklist updates
- repository conventions document

# 10. Appendix: Canonical Naming and Structure Rules

Provide a concise but explicit reference standard for future contributors.

---

## Required Resolution Plan Quality Bar

The resolution plan must be:

- comprehensive,
- consistent across the entire repository,
- realistic to execute,
- sequenced to reduce risk,
- optimized for production-grade quality,
- and detailed enough that an engineering team can implement it without guesswork.

Do not provide only recommendations. Provide a **plan**.

---

## Additional Instructions

- Be strict.
- Prefer clarity over politeness.
- Flag hidden complexity.
- Call out systemic root causes, not just symptoms.
- Highlight where the repository uses multiple competing patterns for the same concern.
- Where applicable, recommend a canonical vocabulary for the domain.
- Distinguish between quick wins and foundational refactors.
- Identify issues that should be fixed globally with codemods, linting, templates, or shared abstractions rather than by manual edits alone.
- Where standards differ by layer (e.g. backend vs frontend vs infra), state that explicitly, but still keep the repo coherent.
- If a pattern should remain intentionally different in a specific subdomain, explain why.

---

## Preferred Tone of the Output

The generated Markdown report should read like a **principal-engineer-level repo audit and remediation plan**. It should be crisp, technical, structured, and execution-ready.

---

## Final Instruction

Now audit the repository accordingly and produce the final output as a **single Markdown file** with all findings and the full resolution plan.
