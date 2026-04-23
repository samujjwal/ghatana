# AEP UI/UX Implementation Plan — Remaining Work

> **Date:** 2026-04-22
> **Product:** `products/aep`
> **Source:** AEP_UI_UX_AUDIT_2026-04-22.md
> **Guidelines:** .github/copilot-instructions.md

All P0, P1, and P2 tasks have been completed and validated. This document now tracks only the remaining P3 (long-term) tasks that are **NOT_STARTED**.

---

## 0. Guiding Principles (copilot-instructions.md)

- **Reuse before creating** — check `platform/*`, `@ghatana/*`, and existing AEP contracts.
- **No `any` types** — fully typed TypeScript implementation.
- **Tests are part of the change** — every meaningful behavior change or bug fix requires regression tests.
- **No silent failures** — errors must be surfaced, logged, and testable.
- **Zero-warning mindset** — lint, formatting, static checks, and build health must remain clean.
- **Public Java APIs require `@doc.*` tags**.
- **Observability** — logs, metrics, traces for important flows.
- **Package naming** — `@ghatana/*` scope only, kebab-case.

---

## Remaining Long-term (P3) Tasks

### TASK-L1: Move to Secure Platform-Auth Handoff and Safer Session Model

- **Finding:** AEP-F006, AEP-F007, AEP-F024
- **Severity:** High (long-term security hardening)
- **Category:** Product strategy / Trust
- **Status:** **NOT_STARTED**
- **What to do:**
  1. Complete full platform SSO integration with short-lived sessions and silent renewal.
  2. Define whether AEP is internal-operator-only or evolving into a broader product.
  3. Align shell UX accordingly (e.g., admin/operator affordances vs. general-user experience).
  4. Add session expiry warnings and graceful re-auth.
- **Owner type:** identity, security, frontend, platform
- **Effort:** High
- **Acceptance Criteria:**
  - AEP auth model matches declared audience (internal vs. external).
  - Session security posture is enterprise-ready.
- **Test Requirements:**
  - E2E test: session expiry triggers re-auth without data loss.
  - Security audit: penetration test on auth flow.

### TASK-L2: Deliver True Intent-First Pipeline Authoring Flow

- **Finding:** AEP-F023
- **Severity:** High
- **Category:** Workflow simplification / AI strategy
- **Status:** **NOT_STARTED**
- **What to do:**
  1. User starts from intent or template.
  2. System drafts pipeline structure via AI (see TASK-M1).
  3. System prefills configuration with 80% auto-config where possible.
  4. User reviews and edits only uncertain parts.
  5. guided validation and one-click deployment.
- **Owner type:** product, frontend, ML
- **Effort:** High
- **Acceptance Criteria:**
  - New pipeline creation is majority-auto, minority-manual.
  - Users rate the flow as significantly faster than manual builder.
- **Test Requirements:**
  - User acceptance testing (UAT) for intent-first flow.
  - E2E test: full NLQ → draft → edit → deploy flow.

### TASK-L3: Build Trustworthy AI-Native Operational Guidance

- **Finding:** AEP-F016, AEP-F017
- **Severity:** High
- **Category:** AI/ML UX
- **Status:** **NOT_STARTED**
- **What to do:**
  1. Embed AI guidance across monitoring, HITL, and governance as contextual assistance, not separate panels.
  2. Summarize likely root cause and next best action for runs.
  3. Cluster repeated episodes and propose policy candidates in learning/memory.
  4. Predict risky policy drift or failure concentration in governance.
  5. Always show confidence level and allow user override.
- **Owner type:** ML, product, frontend
- **Effort:** High
- **Acceptance Criteria:**
  - AI assistance is invisible-in-the-good-way: fewer manual fields, better defaults, clearer next actions.
- **Test Requirements:**
  - Unit test: AI suggestion generation with mocked ML backend.
  - E2E test: operational triage with AI guidance.

### TASK-L4: Governance Cues in Build, Review, and Marketplace Flows

- **Finding:** 3.11 Governance, 3.14 Workflow Catalog, 3.15 Agent Marketplace
- **Severity:** Medium
- **Category:** Trust / Transparency
- **Status:** **NOT_STARTED**
- **What to do:**
  1. Pull governance signals (policy status, compliance state, audit trail) upstream into builder, review, and marketplace surfaces.
  2. Show policy impact warnings before destructive pipeline edits.
  3. Display compliance badges on marketplace listings.
- **Owner type:** product, frontend
- **Effort:** Medium
- **Acceptance Criteria:**
  - Governance signals appear at action points, not just in the governance page.
- **Test Requirements:**
  - E2E test: policy warning shown before destructive action.

---

## Ideal State Vision

AEP should feel like a serious operator control plane that quietly reduces work instead of asking users to understand more systems than necessary.

### Authentication & Entry

- Secure platform sign-in with explicit tenant scope.
- No token paste, no `localStorage` secrets.

### Shell & Navigation

- Outcome-first navigation: operate / build / learn / govern / catalog.
- No ambient noise: SSE and diagnostics are contextual.
- Tenant context is unmistakable and safe to change.

### Pipeline Authoring

- Intent-first: describe, review, confirm.
- AI drafts structure; user judges uncertain parts.
- Template instantiation is reliable and obvious.

### Monitoring & Operations

- Run triage first, metrics second.
- AI guidance is row-level, not banner-level.
- Degraded states are honest, not broken-looking.

### Learning & Memory

- Question-first: "What did this agent learn?" not taxonomy tabs.
- Summaries above raw data.

### Governance & Trust

- Governance cues appear where risk exists.
- No fake evidence, no overpromised capabilities.
- Every action goes where it says it will.

### AI/ML Experience

- Mostly invisible: fewer fields, better defaults, clearer next actions.
- Confidence shown, override always possible.

---

## 1. Remaining Findings → Task Mapping

| Finding ID | Title                                                 | Priority | Task(s) |
| ---------- | ----------------------------------------------------- | -------- | ------- |
| AEP-F006   | Manual JWT paste is primary login UX                  | Critical | TASK-L1 |
| AEP-F007   | Auth/session tokens stored in localStorage            | Critical | TASK-L1 |
| AEP-F016   | AI/NLQ/voice components not embedded in primary flows | High     | TASK-L3 |
| AEP-F023   | Pipeline creation heavily manual                      | High     | TASK-L2 |
| AEP-F024   | Login and shell assume highly trusted internal users  | High     | TASK-L1 |

---

## 2. Definition of Done (per copilot-instructions.md)

For every task in this plan:

1. Follows existing conventions of the touched Ghatana module.
2. Existing shared platform code was checked before creating new abstractions.
3. The change builds, types, and compiles in the relevant workspace (`pnpm --filter @aep/ui exec tsc --noEmit` passes).
4. All TypeScript code is fully typed — no `any`, no untyped parameters, no missing interfaces.
5. Relevant tests added or updated and pass.
6. Formatting, linting, and static checks remain healthy (`pnpm --filter @aep/ui exec eslint .` and `pnpm --filter @aep/ui exec prettier --check .`).
7. Public Java APIs include required JavaDoc and `@doc.*` tags (if backend work is involved).
8. Errors and important flows are observable (structured logs, metrics).
9. Inputs validated at correct boundaries (Zod schemas for API inputs).
10. No repo drift introduced in architecture, naming, or dependency choices.

---

## 3. Execution Order Recommendation

1. **Week 13-14:** TASK-L1 (secure auth), TASK-L2 (intent-first authoring), TASK-L3 (AI-native guidance)
2. **Week 15-16:** TASK-L4 (governance cues)

---

_End of Implementation Plan_
