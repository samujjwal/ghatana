# Yappc Core Remediation Working Agreement

Date: 2026-04-20
Purpose: Define the working group, ownership model, review cadence, and surface freeze used to close the Yappc core remediation program.
Scope: Applies to the mounted Yappc product surface until all P0 and P1 remediation work in the tracker is complete.

## Working Group

| Area | Role Owner | Scope |
| --- | --- | --- |
| Product truth and acceptance | Product architect | Mounted route truthfulness, scope decisions, copy claims, sign-off on surfaced capabilities |
| Web delivery | Web lead | Mounted React routes, navigation, state truth, save-state UX, route-level integration coverage |
| API delivery | API lead | Fastify route contracts, envelope consistency, transition semantics, activity and persistence endpoints |
| Data model and migrations | Database lead | Prisma schema, lifecycle defaults, durable onboarding persistence, migration safety |
| Shared platform dependencies | Platform lead | Shared contracts, package reuse, environment wiring, observability guardrails |
| Release confidence | QA lead | Critical-flow happy/failure coverage, regression matrix, release evidence |

## Track Ownership

| Track | Primary Owner | Supporting Owners |
| --- | --- | --- |
| Track 0: Program Setup | Product architect | QA lead, platform lead |
| Track 1: Canonical Product Model | API lead | Database lead, web lead |
| Track 2: Core Entity Correctness | API lead | Database lead |
| Track 3: Onboarding Truthfulness | Web lead | API lead, database lead |
| Track 4: Route Inventory and Surface Cleanup | Web lead | Product architect |
| Track 5: API Contract Alignment | API lead | Web lead |
| Track 6: Settings and Admin Simplification | Web lead | API lead |
| Track 7: Preview and Deploy Truthfulness | Product architect | Web lead, API lead, platform lead |
| Track 8: Save, Sync, and Visibility Truth | Web lead | API lead, platform lead |
| Track 9: AI and Automation Realignment | Product architect | Web lead, API lead |
| Track 10: Observability and Governance | Platform lead | API lead, QA lead |
| Track 11: Testing and Release Confidence | QA lead | Web lead, API lead |
| Track 12: Product Simplification Delivery | Product architect | Web lead |

## Review Cadence

- Daily remediation stand-up: 15 minutes, blocker review across product, web, API, DB, and QA.
- Twice-weekly design and contract review: product architect, web lead, API lead, database lead.
- Twice-weekly verification review: QA lead with web/API owners to review new critical-flow evidence.
- Weekly sign-off checkpoint: product architect approves tracker state transitions from `IN PROGRESS` to `DONE` for user-visible work.

## Surface Freeze Rules

- No net-new mounted routes, navigation entries, settings sections, or product claims may be added while any tracker P0 or P1 item remains open.
- Any exception requires written audit sign-off from the product architect and explicit validation notes in the remediation tracker.
- UI work during the freeze is limited to truthfulness fixes, contract alignment, reliability, visibility, or regression coverage for the existing mounted surface.
- API additions are allowed only when they close an existing mounted dependency or remove a documented contract mismatch.
- Shared-platform changes must reuse existing contracts or packages unless the platform lead approves a new shared abstraction as necessary to close a tracker item.

## Exit Condition

The freeze lifts only after all tracker P0 and P1 tasks are marked `DONE` with linked validation evidence and the QA lead confirms the mounted critical-flow suite is green.