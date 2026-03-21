# Kernel Platform Dev Document Set

**Last consolidated**: 2026-03-19  
**Status**: Canonical document-set index  
**Purpose**: Define the active, authoritative document set for kernel/AppPlatform convergence and point to preserved legacy material without leaving active planning ambiguous

---

## 1. What This Folder Now Contains

This folder has been cleaned up into:

- a small **active canonical set** at the top level
- a **legacy archive** under `archive/legacy-2026-03-19/`

The top-level set is the only planning set that should be treated as current and authoritative.

The archive keeps earlier material for traceability and idea preservation, but it is not the source of truth for current architecture, readiness, or completion claims.

---

## 2. Authority Order

When two documents disagree, use this authority order:

1. actual code under `platform/java/kernel/**`, `products/app-platform/**`, `products/phr/**`, `products/finance/**`, and `products/aura/**`
2. [KERNEL_APP_PLATFORM_CONVERGENCE_ADR.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_APP_PLATFORM_CONVERGENCE_ADR.md)
3. [KERNEL_APP_PLATFORM_CONVERGENCE_EXPLORATION_AND_PLAN.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_APP_PLATFORM_CONVERGENCE_EXPLORATION_AND_PLAN.md)
4. [KERNEL_CANONICALIZATION_DECISIONS.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_CANONICALIZATION_DECISIONS.md)
5. [DEVELOPER_PLATFORM_CONTRACT_MODEL.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/DEVELOPER_PLATFORM_CONTRACT_MODEL.md)
6. [KERNEL_TO_APP_PLATFORM_MODULE_MAPPING.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_TO_APP_PLATFORM_MODULE_MAPPING.md)
7. [KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md)
8. [KERNEL_NEXT_PHASE_ARCHITECTURE_PROGRAM_BOARD.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_NEXT_PHASE_ARCHITECTURE_PROGRAM_BOARD.md)
9. [PHR_AppPlatform_Integration_Analysis_Report.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/PHR_AppPlatform_Integration_Analysis_Report.md) for the PHR-specific platform integration track

Anything in `archive/legacy-2026-03-19/` is lower authority than the active set above.

---

## 3. Active Canonical Set

The active set is designed to do two things at once:

- preserve the **grand vision** of both the kernel and AppPlatform approaches
- provide a **detailed and granular merge plan** that can be executed without ambiguity

The grand vision that remains active in this folder is:

- a kernel that is AI-native, policy-driven, full-lifecycle, compositional, and easy for product teams to build on
- an AppPlatform that is multi-domain, operationally deep, governance-aware, deployable, and domain-pack-first

The active documents should therefore be read as a combined strategy, not as a stripped-down cleanup set.

### A. Core architecture

- [KERNEL_APP_PLATFORM_CONVERGENCE_ADR.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_APP_PLATFORM_CONVERGENCE_ADR.md)
  - the core architectural decision
  - `platform/java/kernel` is the canonical kernel model
  - `products/app-platform` is the operational platform built on it

- [KERNEL_APP_PLATFORM_CONVERGENCE_EXPLORATION_AND_PLAN.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_APP_PLATFORM_CONVERGENCE_EXPLORATION_AND_PLAN.md)
  - the main detailed architecture and convergence plan
  - includes cross-product constraints from Finance, PHR, and Aura

### B. Canonical kernel cleanup

- [KERNEL_CANONICALIZATION_DECISIONS.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_CANONICALIZATION_DECISIONS.md)
  - resolves duplicate abstractions
  - defines the target generic vocabulary
  - removes product-aware logic from canonical kernel space

- [KERNEL_TO_APP_PLATFORM_MODULE_MAPPING.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_TO_APP_PLATFORM_MODULE_MAPPING.md)
  - maps kernel concepts to AppPlatform modules
  - clarifies what stays generic vs what moves to packs/products

- [KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md)
  - converts architecture gaps into concrete workstreams and backlog items

### C. Developer platform and next phase

- [DEVELOPER_PLATFORM_CONTRACT_MODEL.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/DEVELOPER_PLATFORM_CONTRACT_MODEL.md)
  - defines the missing contract model for UI, API, schema, analytics, autonomy, and deployment

- [KERNEL_NEXT_PHASE_ARCHITECTURE_PROGRAM_BOARD.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_NEXT_PHASE_ARCHITECTURE_PROGRAM_BOARD.md)
  - sequences the next architecture program into epics, work packages, gates, and risks

### D. Product-specific supporting analysis

- [PHR_AppPlatform_Integration_Analysis_Report.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/PHR_AppPlatform_Integration_Analysis_Report.md)
  - detailed PHR-on-platform planning
  - includes deployment-topology guidance and pack/runtime boundary rules

### E. Implementation execution & code alignment

- [VISION_REALIZATION_GAP_ANALYSIS.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/VISION_REALIZATION_GAP_ANALYSIS.md)
  - comprehensive gap analysis between vision and current code
  - identifies all architectural, implementation, and future-proofing gaps
  - provides success metrics and risk mitigation strategies

- [IMPLEMENTATION_EXECUTION_ROADMAP.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/IMPLEMENTATION_EXECUTION_ROADMAP.md)
  - detailed week-by-week implementation plan with concrete actions
  - specific files to add/update/merge/remove with code snippets
  - validation criteria and rollback procedures for each phase

- [CODE_ALIGNMENT_SPECIFICATION.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/CODE_ALIGNMENT_SPECIFICATION.md)
  - exact specification of code changes needed for vision alignment
  - detailed implementation guidance for all components
  - complete testing and validation requirements

---

## 4. Recommended Reading Order

For a new reader:

1. [README.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/README.md)
2. [KERNEL_APP_PLATFORM_CONVERGENCE_ADR.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_APP_PLATFORM_CONVERGENCE_ADR.md)
3. [KERNEL_APP_PLATFORM_CONVERGENCE_EXPLORATION_AND_PLAN.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_APP_PLATFORM_CONVERGENCE_EXPLORATION_AND_PLAN.md)
4. [KERNEL_CANONICALIZATION_DECISIONS.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_CANONICALIZATION_DECISIONS.md)
5. [DEVELOPER_PLATFORM_CONTRACT_MODEL.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/DEVELOPER_PLATFORM_CONTRACT_MODEL.md)
6. [KERNEL_TO_APP_PLATFORM_MODULE_MAPPING.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_TO_APP_PLATFORM_MODULE_MAPPING.md)
7. [KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md)
8. [KERNEL_NEXT_PHASE_ARCHITECTURE_PROGRAM_BOARD.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_NEXT_PHASE_ARCHITECTURE_PROGRAM_BOARD.md)
9. [PHR_AppPlatform_Integration_Analysis_Report.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/PHR_AppPlatform_Integration_Analysis_Report.md) if working the healthcare track
10. [VISION_REALIZATION_GAP_ANALYSIS.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/VISION_REALIZATION_GAP_ANALYSIS.md) for implementation planning
11. [IMPLEMENTATION_EXECUTION_ROADMAP.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/IMPLEMENTATION_EXECUTION_ROADMAP.md) for week-by-week execution
12. [CODE_ALIGNMENT_SPECIFICATION.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/CODE_ALIGNMENT_SPECIFICATION.md) for detailed code changes

If the goal is specifically to understand how the grand visions were preserved while making the plan executable, focus especially on:

1. [KERNEL_APP_PLATFORM_CONVERGENCE_ADR.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_APP_PLATFORM_CONVERGENCE_ADR.md)
2. [KERNEL_APP_PLATFORM_CONVERGENCE_EXPLORATION_AND_PLAN.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_APP_PLATFORM_CONVERGENCE_EXPLORATION_AND_PLAN.md)
3. [DEVELOPER_PLATFORM_CONTRACT_MODEL.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/DEVELOPER_PLATFORM_CONTRACT_MODEL.md)
4. [KERNEL_NEXT_PHASE_ARCHITECTURE_PROGRAM_BOARD.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_NEXT_PHASE_ARCHITECTURE_PROGRAM_BOARD.md)

If the goal is to execute the implementation and ensure excellent product suite realization, focus especially on:

1. [VISION_REALIZATION_GAP_ANALYSIS.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/VISION_REALIZATION_GAP_ANALYSIS.md) - complete gap analysis
2. [IMPLEMENTATION_EXECUTION_ROADMAP.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/IMPLEMENTATION_EXECUTION_ROADMAP.md) - week-by-week execution plan
3. [CODE_ALIGNMENT_SPECIFICATION.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/CODE_ALIGNMENT_SPECIFICATION.md) - detailed code changes
4. [KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md) - concrete workstreams

---

## 5. Why The Older Documents Were Archived

The legacy set contained useful material, but it also had serious issues:

- duplicated architecture narratives
- multiple “final” or “complete” summaries saying different things
- implementation claims stronger than the current code supports
- heavy overlap between brainstorms, phase specs, migration summaries, and validation docs
- older examples and contracts that no longer reflect the current canonical kernel/AppPlatform direction

Archiving them preserves information without forcing active readers to sort through contradictory guidance.

---

## 6. What Lives In The Archive

The archive at [archive/legacy-2026-03-19](/Users/samujjwal/Development/ghatana/docs/kernel-platform-dev/archive/legacy-2026-03-19) preserves:

- early brainstorm material
- older kernel architecture/API/plugin documents
- older detailed and granular implementation plans
- app-platform migration plans and summaries
- legacy validation/readiness reports

Use those documents for:

- historical traceability
- recovering raw examples or ideas
- understanding prior migration thinking

Do not use them as the active source of truth.

---

## 7. Folder Invariants Going Forward

To keep this folder clean:

- only the active canonical set stays at the top level
- superseded or exploratory material moves into `archive/`
- no file may claim `complete`, `production ready`, or equivalent status without executable evidence
- new documents must state purpose, status, and relationship to the rest of the set
- product-specific deep dives belong at the top level only if they are actively used in the platform program

---

## 8. Bottom Line

The folder is now intentionally split between:

- a coherent, current planning spine
- a preserved historical archive

That keeps the active set clear and unambiguous without losing any useful information from the prior documents, and without losing the larger ambition of either the kernel or AppPlatform vision.
