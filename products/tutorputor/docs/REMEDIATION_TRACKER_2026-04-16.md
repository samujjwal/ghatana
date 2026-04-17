# TutorPutor Remediation Tracker

Date: 2026-04-16
Source: PRODUCT_REVIEW_FINDINGS_2026-04-16.md
Scope: Concrete implementation tracking for audit follow-through.

Status legend:
- done
- in-progress
- not-started
- blocked
- deferred

## Implemented in this session

| ID | Item | Status | Notes |
|---|---|---|---|
| TP-001 | Stop fake animation export artifacts | done | Video and GIF export now fail explicitly with observable errors until a real encoder is wired. |
| TP-002 | Restore admin domain editor coverage | done | Added a real DomainEditorPage implementation and replaced the skipped placeholder suite with executable tests. |
| TP-003 | Wire browser-side error reporting | done | Web logger and app bootstrap now initialize client error tracking and capture production errors when configured. |
| TP-004 | Add request correlation at Fastify boundary | done | Platform startup now normalizes and propagates x-correlation-id on every request and forwards it into Sentry context. |
| TP-005 | Replace monitoring email placeholder | done | Monitoring email alerts now send through Resend and fail loudly when not configured. |
| TP-007 | Remove mock-user auth fallbacks from legacy auth adapter | done | `services/tutorputor-platform/src/auth/index.ts` now fails closed unless a real user repository is injected, and regression tests cover the legacy adapter. |
| TP-010 | Reduce and ban new @ts-nocheck usage | done | Added CI allowlist enforcement so new `@ts-nocheck` debt cannot land silently while the remaining exceptions are paid down deliberately. |
| TP-014 | Raise coverage gate and protect critical paths | done | TutorPutor CI now fails below a 75% line coverage floor instead of the previous 60% threshold. |
| TP-009 | Continue Fastify boundary validation rollout | in-progress | Added runtime validation + route tests for credentials, collaboration, marketplace, knowledge-base, and tenant domain-pack endpoints. |
| TP-012 | Expand GDPR schema-contract evidence | in-progress | Added additional Prisma model contract tests for deletion request state, retention windows, and user-data lookup indexes. |
| TP-008 | Build critical-journey E2E proof runbook | in-progress | Added runbook, checklist, datasets, signoff template, evidence matrix, and runnable scripts to capture executed proof. |
| TP-009 | Expand route-boundary validation to integration/engagement modules | in-progress | Added runtime validation + route tests for billing, gamification, engagement credentials, content-needs, and auto-revision endpoints. |
| TP-008 | Add environment evidence collectors | in-progress | Added local/staging evidence docs plus scriptable collectors and an aggregated remediation proof suite runner. |
| TP-009 | Harden LTI route boundary validation | in-progress | Added strict payload/param validation for deep-linking, grade-passback, registration, and platform admin routes with route test assertions for malformed payload rejection. |
| TP-008 | Expand proof coverage to preprod/production artifacts | in-progress | Added critical-journey and GDPR evidence templates for preprod/production plus proof execution log, status matrix, signoff sheet, and LTI evidence checklist. |
| TP-015 | Add encryption evidence collection wrappers | in-progress | Added preprod encryption evidence template and script-level collectors to standardize evidence generation by environment. |
| TP-009 | Harden social route boundary validation and tests | in-progress | Added strict request validation and a dedicated social route test suite covering study groups, forums, peer tutoring, chat, and feed query boundaries. |
| TP-008 | Add social proof execution artifacts | in-progress | Added social proof runbook/checklist/matrix/log/signoff, environment evidence files, and script wrappers for local/staging/preprod/production runs. |
| TP-009 | Harden content route boundary validation and tests | in-progress | Added strict request validation and dedicated route suites for telemetry, recommendation, publish, evaluation, and candidates content modules. |
| TP-008 | Add content proof execution artifacts | in-progress | Added content proof runbook/checklist/matrix/log/signoff, environment evidence files, and script wrappers for local/staging/preprod/production runs. |
| TP-009 | Expand content route validation phase 2 surfaces | in-progress | Added strict request validation and route suites for asset, semantic, quality-ml, modality-conversion, generation, review, cms, and content experiments A/B testing routes. |
| TP-008 | Add content phase 2 proof artifacts | in-progress | Added phase 2 runbook/checklist/matrix/log/signoff, environment evidence files, and script wrappers for local/staging/preprod/production runs. |
| TP-009 | Expand route validation batch 3 surfaces | in-progress | Added stricter validation for integration/lti launch payload, content root module list/detail routes, and expanded content studio boundary validation with new route suites. |
| TP-008 | Add route validation batch 3 proof artifacts | in-progress | Added batch 3 runbook/checklist/matrix/log/signoff, environment evidence templates, and local/staging/preprod/production wrapper scripts. |
| TP-009 | Tighten LTI phase 2 boundary validation | in-progress | Added stricter grade-passback score/progress validation and whitespace-safe platform parameter validation with extended LTI route tests. |
| TP-008 | Add LTI phase 2 proof artifacts | in-progress | Added LTI phase 2 runbook/checklist/matrix/log/signoff, environment evidence templates, and local/staging/preprod/production wrapper scripts. |

## Remaining audit items

| ID | Priority | Item | Status | Notes |
|---|---|---|---|---|
| TP-006 | P0 | Implement real MP4 and GIF encoding | in-progress | Fake bytes are removed; actual encoding still requires a real browser or server encoder path. |
| TP-008 | P0 | Expand Playwright to full critical-journey proof | in-progress | Added execution and evidence collection scripts plus local/staging/preprod/production evidence templates and signoff matrices; still needs executed runs with attached traces/logs for final sign-off. |
| TP-009 | P1 | Add Zod validation to all Fastify boundaries | in-progress | Search + admin + teacher + audit + payments + compliance + credentials + collaboration + marketplace + knowledge-base + tenant domain-pack + billing + integration/lti + gamification + engagement credentials + content-needs + auto-revision + engagement/social + content/root + content/studio + content/telemetry + content/recommendation + content/publish + content/evaluation + content/candidates + content/asset + content/semantic + content/quality-ml + content/modality-conversion + content/generation + content/review + content/cms + content/experiments/ab-testing routes now enforce or are migrating to strict boundary validation with route tests; remaining gaps are concentrated in final route surfaces and environment proof execution/signoff completion. |
| TP-011 | P1 | Verify or retract mobile and offline capability claims | done | Docs now distinguish shipped web offline support from infrastructure-only mobile work, instead of treating both as either fully delivered or fully absent. |
| TP-012 | P1 | Add GDPR delete cascade proof | in-progress | Added schema-contract coverage for cascade relations, deletion-request state defaults, retention windows, and deletion lookup indexes; still needs seeded end-to-end deletion flow proof across persistence. |
| TP-013 | P2 | Verify real-time collaboration path | done | Redis Streams and pub/sub are present in learning analytics, plus collaboration services exist; docs now reflect that some web UX still uses polling fallback. |
| TP-015 | P2 | Audit at-rest encryption posture | in-progress | Added operational audit docs, checklist/evidence templates, and repeatable verification scripts for Postgres and object storage; pending environment-by-environment execution evidence. |

## Current position

- Release-blocking fake completeness in animation export is removed.
- Admin domain authoring now has a real page and active regression coverage.
- Client and server observability both carry correlation and error reporting hooks.
- Product docs now distinguish implemented foundations from roadmap-only delivery in mobile, offline, and real-time areas.
- Remaining work is now concentrated in real encoder delivery, full route-surface validation completion for the remaining uncovered modules, seeded GDPR deletion-flow proofs with executed environment runs, environment-specific encryption evidence capture with completed checklists, and finalized critical-journey/LTI/social/content signoff artifacts.