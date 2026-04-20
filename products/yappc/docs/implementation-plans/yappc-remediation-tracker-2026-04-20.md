# Yappc Remediation Tracker

Date: 2026-04-20
Purpose: Task-by-task implementation and validation tracker derived from the Yappc product audit.
Usage: Update status, owner, links, and notes as work progresses. The order below is intentional and follows dependency order.

## Status Legend

- `TODO`: not started
- `IN PROGRESS`: currently being worked on
- `BLOCKED`: cannot proceed yet
- `DONE`: implemented and validated
- `DEFERRED`: intentionally postponed

## Program Goals

- Make Yappc truthful before making it broader.
- Make the core flow simple before expanding advanced surfaces.
- Eliminate broken mounted routes and contract mismatches.
- Establish one canonical lifecycle model.
- Replace silent fallback behavior with visible, trustworthy state.
- Add enough integration coverage to prove the product actually works end to end.

## Track 0: Program Setup

### Task 0.1

- Title: Appoint owners and working group
- Status: TODO
- Priority: P0
- Outcome: Clear ownership across product, web, API, DB, and platform.
- Suggested owners: Product architect, web lead, API lead, platform lead, QA lead
- Validation:
  - Named owners for each track
  - Review cadence defined

### Task 0.2

- Title: Freeze net-new surface area during core remediation
- Status: TODO
- Priority: P0
- Outcome: Team stops expanding broken or partial routes while repairing the core.
- Validation:
  - Team agreement documented
  - New UI features require audit sign-off until P0 and P1 are closed

## Track 1: Canonical Product Model

### Task 1.1

- Title: Choose one canonical lifecycle model
- Status: TODO
- Priority: P0
- Outcome: One lifecycle vocabulary and phase sequence for the whole product.
- Scope:
  - README
  - config pipelines
  - agent transition config
  - Prisma enum
  - API DTOs
  - web labels and state handling
- Validation:
  - One lifecycle source of truth committed
  - All references updated
  - No conflicting phase names remain

### Task 1.2

- Title: Create shared lifecycle constants and helpers
- Status: TODO
- Priority: P0
- Outcome: Lifecycle logic is imported rather than duplicated.
- Scope:
  - Shared package or domain module
  - Phase ordering
  - Transition rules
  - Human-readable labels
  - Guard and readiness metadata
- Validation:
  - Web and API compile against the shared lifecycle module
  - Unit tests cover phase ordering and transitions

## Track 2: Core Entity Correctness

### Task 2.1

- Title: Fix project creation response bug
- Status: TODO
- Priority: P0
- Outcome: Project creation no longer throws because of undefined `aiHealthScore`.
- Exact fix:
  - Update `products/yappc/frontend/apps/api/src/routes/projects.ts`
  - Return `healthResult.score` or remove the field
- Validation:
  - `projects.audit.test.ts` passes
  - Manual create-project smoke check passes

### Task 2.2

- Title: Change default project lifecycle phase to canonical first phase
- Status: TODO
- Priority: P0
- Outcome: Newly created projects start in the true first lifecycle phase.
- Scope:
  - Prisma schema default
  - Any explicit create-path defaults
  - Data migration for existing records if needed
- Validation:
  - Schema migration applied
  - New project creation tests assert the correct initial phase

### Task 2.3

- Title: Audit create and update DTOs for project and workspace entities
- Status: TODO
- Priority: P1
- Outcome: Core entity contracts are minimal, aligned, and complete.
- Validation:
  - No unsupported fields remain in mounted forms
  - DTO tests or schema assertions added

## Track 3: Onboarding Truthfulness

### Task 3.1

- Title: Decide whether onboarding is rich or minimal
- Status: TODO
- Priority: P0
- Outcome: Onboarding only asks for what will actually be saved and used.
- Decision options:
  - Rich path: persist project name, type, personas, and defaults
  - Minimal path: trim the form to just workspace and starter project creation
- Validation:
  - Final onboarding specification documented

### Task 3.2

- Title: Persist onboarding project metadata
- Status: TODO
- Priority: P0
- Outcome: Project name and type from onboarding become the actual created project.
- Files likely involved:
  - `products/yappc/frontend/web/src/components/workspace/OnboardingFlow.tsx`
  - `products/yappc/frontend/apps/api/src/routes/workspaces.ts`
  - `products/yappc/frontend/apps/api/src/routes/projects.ts`
- Validation:
  - Onboarding integration test proves persistence

### Task 3.3

- Title: Persist onboarding persona selections durably
- Status: TODO
- Priority: P1
- Outcome: Persona selections are saved to a project or workspace model instead of local-only storage.
- Validation:
  - DB model decided
  - API contract added
  - Web reads and writes through the API

### Task 3.4

- Title: Fix onboarding completion semantics
- Status: TODO
- Priority: P0
- Outcome: Onboarding is only marked complete after successful durable setup.
- Validation:
  - Failure-path tests added
  - No false-complete state on failed setup

## Track 4: Route Inventory and Product Surface Cleanup

### Task 4.1

- Title: Create canonical mounted-route inventory
- Status: TODO
- Priority: P0
- Outcome: Team has one source of truth for what is actually shipped.
- Validation:
  - Route inventory document committed
  - Every route classified as complete, gated, or hidden

### Task 4.2

- Title: Hide or remove broken mounted routes
- Status: TODO
- Priority: P0
- Outcome: Users no longer reach unsupported flows.
- Routes to review first:
  - `/profile`
  - workspace settings
  - project settings
  - preview
  - deploy
- Validation:
  - Navigation smoke test passes
  - Broken links eliminated

### Task 4.3

- Title: Remove or archive unreachable page artifacts from product-critical areas
- Status: TODO
- Priority: P2
- Outcome: Repo surface better matches the real product surface.
- Validation:
  - Reachability audit complete
  - Archive or document latent surfaces clearly

## Track 5: API Contract Alignment

### Task 5.1

- Title: Standardize response envelopes for workspace and project reads
- Status: TODO
- Priority: P0
- Outcome: Frontend and API agree on one read-shape convention.
- Validation:
  - Workspace settings and project shell both load correctly
  - Contract tests added

### Task 5.2

- Title: Standardize update contracts for project and workspace writes
- Status: TODO
- Priority: P1
- Outcome: Write routes require only the right fields and are called consistently.
- Validation:
  - `workspaceId` and similar required data flows are coherent
  - Save-route tests added

### Task 5.3

- Title: Add missing endpoints or remove unsupported UI dependencies
- Status: TODO
- Priority: P0
- Outcome: Mounted UIs call only supported routes.
- Candidate endpoints:
  - `/api/users/me`
  - project members
  - project tokens
  - project audit feed
- Validation:
  - No mounted screen calls a missing route

### Task 5.4

- Title: Generate or centralize shared client types
- Status: TODO
- Priority: P1
- Outcome: Contract drift becomes harder to introduce.
- Validation:
  - Shared types used in web and API
  - Type-level contract breakages caught in CI

## Track 6: Settings and Admin Simplification

### Task 6.1

- Title: Reduce workspace settings to only supported fields
- Status: TODO
- Priority: P0
- Outcome: Workspace settings become truthful and usable.
- Validation:
  - Unsupported fields removed or fully backed
  - GET and PATCH paths pass integration checks

### Task 6.2

- Title: Rebuild project settings around real capabilities only
- Status: TODO
- Priority: P0
- Outcome: Project settings no longer ship as a stubbed or broken admin panel.
- Validation:
  - Stub markers removed
  - Only backed sections remain visible

### Task 6.3

- Title: Reintroduce advanced settings behind progressive disclosure
- Status: TODO
- Priority: P2
- Outcome: Core flows stay calm while advanced controls remain available when real.
- Validation:
  - UX review sign-off

## Track 7: Preview and Deploy Truthfulness

### Task 7.1

- Title: Decide product truth for preview
- Status: TODO
- Priority: P0
- Outcome: Preview is either implemented, clearly externalized, or hidden.
- Decision options:
  - Implement a real preview service contract
  - Integrate with an external preview host formally
  - Hide preview until supported
- Validation:
  - Decision documented
  - UI behavior matches reality

### Task 7.2

- Title: Decide product truth for deploy
- Status: TODO
- Priority: P0
- Outcome: Deploy screen becomes real or becomes an explicitly scoped planning surface.
- Validation:
  - No hardcoded fake operational numbers remain
  - CI/CD adapter path matches UI claims

### Task 7.3

- Title: Add deployment and preview status models
- Status: TODO
- Priority: P1
- Outcome: User can see pending, running, succeeded, failed, blocked, and approval-needed states.
- Validation:
  - Status model documented
  - API and UI states aligned

## Track 8: Save, Sync, and Visibility Truth

### Task 8.1

- Title: Inventory all local fallback repositories and optimistic save paths
- Status: TODO
- Priority: P0
- Outcome: Team knows every place where remote failure can be hidden.
- Validation:
  - Inventory committed
  - Each path classified by risk

### Task 8.2

- Title: Introduce explicit sync states in the UI
- Status: TODO
- Priority: P0
- Outcome: User sees whether state is local-only, syncing, saved remotely, or failed.
- Validation:
  - Save-state component available and reused
  - Failure UX tested

### Task 8.3

- Title: Add event and audit visibility for important automation actions
- Status: TODO
- Priority: P1
- Outcome: Important automated actions become inspectable without clutter.
- Validation:
  - Project timeline or activity panel exists
  - Automation events render with reason and result

## Track 9: AI and Automation Realignment

### Task 9.1

- Title: Rewrite AI product language to match actual capability
- Status: TODO
- Priority: P1
- Outcome: Product claims are honest.
- Validation:
  - Copy review complete
  - No screen implies deeper AI than exists

### Task 9.2

- Title: Expand only implicit, practical automation
- Status: TODO
- Priority: P2
- Outcome: AI reduces effort rather than advertising itself.
- Candidate interventions:
  - Autofill
  - Summaries
  - Next-best actions
  - Duplicate detection
  - Persona recommendation
- Validation:
  - UX outcomes measured by fewer inputs and fewer clicks

### Task 9.3

- Title: Add confidence and review thresholds for automation
- Status: TODO
- Priority: P2
- Outcome: Low-risk automation stays hidden; high-risk automation becomes reviewable.
- Validation:
  - Confidence thresholds documented
  - Review triggers tested

## Track 10: Observability and Governance

### Task 10.1

- Title: Surface project-level status and next action in the UI
- Status: TODO
- Priority: P1
- Outcome: Users and operators can immediately tell what stage a project is in and what should happen next.
- Validation:
  - Project cockpit or shell shows status and owner of next action

### Task 10.2

- Title: Build a lightweight approval and blocked-reason UX
- Status: TODO
- Priority: P1
- Outcome: Governance is understandable instead of hidden in config.
- Validation:
  - Approval-needed and blocked states have human-readable reasons

### Task 10.3

- Title: Map audit events to user-visible timeline entries
- Status: TODO
- Priority: P2
- Outcome: Auditability becomes useful in product workflows.
- Validation:
  - Timeline entries link back to real audit events or action records

## Track 11: Testing and Release Confidence

### Task 11.1

- Title: Fix Prisma generation and integration test bootstrap
- Status: TODO
- Priority: P0
- Outcome: Integration tests can run locally and in CI.
- Validation:
  - `lifecycle-gates.integration.test.ts` runs
  - CI setup documented

### Task 11.2

- Title: Add full-stack tests for mounted critical flows
- Status: TODO
- Priority: P1
- Critical flows:
  - onboarding
  - workspace list and settings
  - project create and shell load
  - lifecycle transition
  - preview and deploy state behavior
- Validation:
  - Each mounted critical route has at least one full-stack happy path and one failure-path test

### Task 11.3

- Title: Add contract tests for shared API shapes
- Status: TODO
- Priority: P1
- Outcome: Envelope drift and required-parameter drift are caught automatically.
- Validation:
  - Contract tests exist for workspaces and projects

### Task 11.4

- Title: Add failure and recovery tests for sync-state behavior
- Status: TODO
- Priority: P1
- Outcome: Local fallback no longer hides critical truth from users.
- Validation:
  - Failure injection coverage added

## Track 12: Product Simplification Delivery

### Task 12.1

- Title: Design one-project-cockpit information architecture
- Status: TODO
- Priority: P2
- Outcome: Core workflow is calmer and simpler.
- Validation:
  - UX spec approved
  - Redundant screens identified for merge or removal

### Task 12.2

- Title: Implement cockpit and progressive disclosure model
- Status: TODO
- Priority: P2
- Outcome: Lifecycle, preview, deploy, and status become one coherent flow.
- Validation:
  - Usability review
  - Navigation regression tests

### Task 12.3

- Title: Remove dashboard and settings clutter
- Status: TODO
- Priority: P2
- Outcome: Primary actions become obvious and calm.
- Validation:
  - Screen-by-screen copy and control count review

## Reporting Template

Use this flat template to update each task:

```md
### Task X.Y

- Title:
- Status:
- Priority:
- Owner:
- Started:
- Completed:
- Dependencies:
- Deliverables:
- Validation:
- Notes:
```

## Suggested Milestones

### Milestone 1: Core Truth

- Includes:
  - Track 1
  - Task 2.1
  - Task 2.2
  - Track 3
  - Track 4.2
  - Task 5.1
  - Task 6.1
  - Task 11.1

### Milestone 2: Usable Core Flow

- Includes:
  - Remaining Track 5
  - Track 6.2
  - Track 7 decisions
  - Track 8
  - Task 10.1
  - Task 11.2
  - Task 11.3

### Milestone 3: Trust and Simplification

- Includes:
  - Track 9
  - Track 10.2
  - Track 10.3
  - Track 12

## Exit Criteria

Yappc should not be considered production-ready until the following are true:

- One canonical lifecycle model exists everywhere.
- New projects start in the correct first phase.
- No mounted route is knowingly broken or unbacked.
- Onboarding is truthful and durably persisted.
- Save and sync state is explicit and trustworthy.
- Preview and deploy accurately reflect reality.
- Critical flows have real full-stack test coverage.
- Product claims about AI and automation are honest and practical.
