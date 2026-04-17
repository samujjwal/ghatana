# TutorPutor — Deep Product Re-Audit & Strategic Findings

**Date:** 2026-04-16
**Scope:** `products/tutorputor/`
**Method:** Evidence-based code inspection, spec vs. implementation tracing, fake-completeness grep, correctness/hardening review, test-proof inventory, market/whitespace analysis.
**Status:** Initial deep audit (no prior TutorPutor-specific audit baseline was found in the repo).

---

## 1. Executive Verdict

| Dimension | Verdict |
|---|---|
| Production readiness | **Not Ready** (MVP-feasible; critical animation export + auth data-flow blockers) |
| Feature completeness | **Partial → Misleadingly Complete** in animation/export, mobile, offline |
| Correctness confidence | **Medium** (strong auth/tenant; weak boundary validation, mock user resolution in prod code) |
| Hardening | **Moderate** (Sentry + audit + Docker hardening; correlation IDs unverified; `@ts-nocheck` files) |
| UI/UX quality | **Moderate** (web + admin feature-complete; no verified E2E coverage of full journeys) |
| Competitive position | **Moderate** (simulation-first + CBM + LTI 1.3 is differentiated; execution gaps erode the story) |
| Problem–solution fit | **Strong intent, Moderate execution** (right problems targeted; core loop real; edge features staged) |
| Innovation / disruption potential | **Strong** (CBM + Viva overconfidence detection + NL→simulation + kernel marketplace is a defensible moat — if hardened) |

### What improved (vs. general ghatana baseline)
- Real Java content-generation service with multi-agent pipeline (GAA framework).
- Strong JWT + SSO + device-fraud + session-revocation coverage (146 passing auth tests across Phase 2A–3).
- Strict tenant isolation at request boundary (no silent `"default"` fallback).
- Dockerfile follows multi-stage best practices (non-root, health probe, no hardcoded secrets).
- CI enforces contract drift, type-check, Prisma migration validity, and 60% coverage floor.

### What is still open
- **Animation export returns fake Blob data** (video/GIF encoding is stub).
- **Mock user lookups persist inside production auth code** (6 sites in `auth/index.ts`).
- **E2E Playwright coverage is a fragment**, not a suite.
- **Input validation is not uniformly Zod-backed** at request boundaries.
- **Correlation ID propagation** not evident; distributed tracing is partial.
- **Mobile app** = placeholder; **offline mode** = spec-only.
- **One admin test suite skipped** (`DomainEditorPage`).
- **`@ts-nocheck` in 30+ frontend files** violates the repo's §7/§26 typing mandate.

### What regressed
- N/A — no prior baseline. However, multiple "mock" markers in production auth path suggest **surface fixes over root-cause fixes** during the Phase 2–3 hardening push.

### Top 15 Critical Findings

| # | Finding | Severity |
|---|---|---|
| 1 | Animation video/GIF export returns synthetic bytes — users cannot download animations | **P0** |
| 2 | `auth/index.ts` contains 6 "return a mock user" sites in production code path | **P0** |
| 3 | E2E test file is incomplete fragment; no verified end-to-end journey coverage | **P1** |
| 4 | Input validation not uniformly Zod-guarded at Fastify handlers (violates §5 / §27) | **P1** |
| 5 | `@ts-nocheck` in 30+ files violates §7 (type safety at implementation time) + §26 | **P1** |
| 6 | Correlation ID propagation not verified across service boundaries (§19) | **P1** |
| 7 | Email alert system is explicit placeholder (`monitoring.ts#L578`) | **P2** |
| 8 | `describe.skip` on `DomainEditorPage.test.tsx` — entire admin suite disabled | **P2** |
| 9 | Mobile app is a placeholder while README + spec advertise it as a deployment mode | **P1** |
| 10 | Offline mode is spec-only (IndexedDB + ServiceWorker) but surfaced as a product capability | **P2** |
| 11 | Real-time collaboration claimed (Redis streams) but not verified in code | **P2** |
| 12 | Coverage threshold of 60% is below industry norm (70–80%) for a learning-outcome product | **P2** |
| 13 | VR/AR runtime is scaffold only (`services/tutorputor-vr`), not production-grade | **P2** |
| 14 | No verified at-rest encryption pathway for learner PII / assessment evidence | **P2** |
| 15 | Competitor whitespace (correctness + evidence-of-learning moat) under-executed vs. potential | **Strategic** |

---

## 2. Problem / Use-Case Validation Matrix

| # | User/Persona | Problem | Why it matters | Expected workflow | Implementation evidence | Missing pieces | Correctness | Production credibility | Verdict |
|---|---|---|---|---|---|---|---|---|---|
| 1 | K-12 / Higher-Ed student | Passive content doesn't build real understanding | Learning outcomes, retention | Log in → assessment → adaptive path → simulation → CBM feedback | `apps/tutorputor-web` + `modules/learning/` + `ClaimMasteryCalculator` | Full E2E Playwright coverage, offline path | Correct | Credible for pilot | **Validated & supported** |
| 2 | Student (evidence-seeking) | Overconfidence / illusion of mastery | Mis-calibrated learners fail downstream | Confidence-marked answers → Viva oral probes when suspicious | `learning-engine/analytics/ClaimMastery.ts` + `VivaEngine` | Real LLM integration vs. fallback heuristics not demonstrated | Correct design; runtime effectiveness unverified | Fragile | **Supported but fragile** |
| 3 | Student (experimentation) | Text-only science learning lacks intuition | Retention + transfer | NL prompt → simulation manifest → interactive physics/chem/discrete kernel | `tutorputor-sim-author`, `tutorputor-sim-runtime`, `tutorputor-sim-sdk`, `tutorputor-kernel-registry` | End-to-end perf + safety gates, kernel marketplace governance | Correct | Differentiated; credible for MVP | **Validated & supported** |
| 4 | Educator / content author | AI content generation is cheap but unreliable | Trust is the whole product | Describe topic → 9-gate publish validation → signed artifact | `tutorputor-content-generation` (Java, ActiveJ) + `LearningUnitValidator` pillars | Animation export broken; publish gating tests exist but end-to-end audit trail incomplete | Correct | **Compromised by animation stub** | **Partially supported** |
| 5 | Educator (LMS-native institution) | Another silo is a blocker | Adoption | LTI 1.3 launch → deep-link → grade passback | `modules/integration/lti/` + Phase 2D tests | Field-tested AGS against real LMS unknown | Correct | Credible | **Validated & supported** |
| 6 | Teacher (classroom ops) | Need progress, risk, engagement signals | Early intervention | Dashboards + risk scoring + xAPI | `modules/learning/analytics/` + `XAPIIngestor` | Real-time collaboration claim unverified | Correct (batch) | Credible (batch); fragile (real-time) | **Supported but fragile** |
| 7 | Institution admin | Multi-tenant SaaS with SSO | Compliance/procurement | SAML/OIDC login → per-tenant config | `auth/` Phase 3 SSO + `modules/tenant/` | At-rest encryption posture not verified | Correct | Credible | **Validated & supported** |
| 8 | Institution | GDPR/student data rights | Legal / trust | Export + delete pipelines | `modules/audit/` + export routes | End-to-end deletion test of cascading records not audited | Plausible | Moderate | **Partially supported** |
| 9 | Learner (mobile-first) | Study on phone | Reach / adoption | Native mobile app | `apps/tutorputor-mobile/` **placeholder** | Entire app | N/A | **Not credible** | **Claimed but not implemented** |
| 10 | Learner (low-connectivity) | Offline study | Access equity, emerging markets | Sync down → local study → sync up | `docs/offline-mode-spec.md` only | Entire runtime | N/A | **Not credible** | **Claimed but not implemented** |
| 11 | Learner (immersive) | VR/AR labs | Hands-on STEM | VR launch → simulation | `services/tutorputor-vr/` scaffold | Actual runtime | Partial | **Not production-grade** | **Weakly justified** |
| 12 | Creator / reseller | Monetize kernels & content | Ecosystem moat | Marketplace + Stripe | `modules/integration/marketplace/` + Stripe tests | Seller onboarding, tax, payouts depth unknown | Partial | Moderate | **Supported but fragile** |

---

## 3. Previous Audit Closure Matrix

**Finding:** No prior TutorPutor-specific audit report exists. Root `PRODUCT_REVIEW_FINDINGS_2026-04-16.md` covers YAPPC / AEP / Data-Cloud / Audio-Video only. This audit establishes the baseline.

Recommendation: treat §10 of this report (Release Blockers) as the frozen baseline for future closure tracking.

---

## 4. Product Claim vs. Reality Matrix

| Capability | Claimed in | Implementation evidence | Missing pieces | Correctness | Hardening | Tests | Verdict |
|---|---|---|---|---|---|---|---|
| Adaptive learning paths | README, PRODUCT_SPEC §1 | `modules/learning/`, preferences in Prisma schema | — | OK | OK | Unit + integration | **Complete** |
| Evidence-based CBM + Viva overconfidence | PRODUCT_SPEC | `learning-engine/analytics/ClaimMastery.ts`, `VivaEngine` | LLM-vs-heuristic efficacy proof | OK (logic) | OK | Unit | **Complete but unvalidated at outcome level** |
| NL → Simulation authoring | PRODUCT_SPEC | `tutorputor-sim-author`, `tutorputor-sim-nl`, `tutorputor-sim-runtime`, SDK, kernel registry | Kernel supply-chain signing/review | OK | Moderate | Partial | **Complete but fragile** |
| AI content generation (9-gate validator) | PRODUCT_SPEC | `tutorputor-content-generation` (Java/ActiveJ), `LearningUnitValidator`, `PlatformContentGenerator` | — | OK | OK | Java test suites | **Complete** |
| Animation export (MP4/GIF) | Implicit in Content Studio UX | `animation-runtime/export-service.ts` L284–L320 | Actual encoder | **Incorrect (fake bytes)** | **Broken** | None | **Misleadingly complete** |
| LTI 1.3 (launch, deep-link, AGS) | PRODUCT_SPEC §1, CURRENT_STATE §3.2 | `modules/integration/lti/` | Real LMS field test | OK | OK | Phase 2D | **Complete** |
| Multi-tenant SaaS + SSO | README, PRODUCT_SPEC | `auth/`, `modules/tenant/`, SAML+OIDC Phase 3 | At-rest encryption evidence | OK | Strong | Phase 3 | **Complete** |
| Device fraud + session revocation | Phase 3 report | `auth/` Phase 3 tests (8 device, 6 session, 4 refresh rotation) | — | OK | Strong | Yes | **Complete** |
| Marketplace + Stripe | PRODUCT_SPEC | `modules/integration/marketplace/` | Payouts, tax, dispute flow | OK (core) | Moderate | Partial | **Complete but fragile** |
| Real-time collaboration (Redis streams) | Docs | Not verified in search | Live path verification | Unknown | Unknown | None | **Claimed, unverified** |
| Mobile app | README deployment map | `apps/tutorputor-mobile` placeholder | Entire app | N/A | N/A | None | **Claimed, not implemented** |
| Offline mode | `docs/offline-mode-spec.md` | Spec only | Runtime | N/A | N/A | None | **Claimed, not implemented** |
| VR/AR labs | `services/tutorputor-vr` | 8 DB models + scaffold | Real runtime, headset integration | Partial | Weak | Minimal | **Partial** |
| Gamification (points, badges, groups) | PRODUCT_SPEC | `modules/engagement/` | — | OK | OK | Yes | **Complete** |
| xAPI / Caliper / GDPR export | CURRENT_STATE | Export routes + `learning-kernel/plugins/XAPIIngestor.ts` | Cascading delete audit | OK | Moderate | Partial | **Complete but fragile** |

---

## 5. Gaps We Must Fill

### 5.1 Product / Use-Case
- **Animation download is broken** → Educators produce artifacts they cannot deliver.
- **Mobile + offline = narrative without execution** → Remove from marketing until implemented, or prioritize delivery.
- **Outcome-proof pipeline is missing** → We log xAPI + CBM but do not publish an outcome-evidence artifact per learner per unit (see §11 innovation moves).

### 5.2 Correctness
- Mock user lookups in `auth/index.ts` (6 sites) — must hit real user repository.
- Input validation inconsistent — enforce Zod at every Fastify route (see §27 repo standard).
- `@ts-nocheck` present in 30+ frontend files — violates §7 and §26.

### 5.3 Architecture
- Correlation ID / distributed trace propagation missing at boundary (§19 requirement).
- Kernel marketplace lacks supply-chain signing / sandbox policy for third-party kernels.
- Real-time pathway (claimed Redis streams) not traceable in code — architecture debt or undelivered feature.

### 5.4 UX
- Full student + educator journey not proven by E2E (Playwright file is a fragment).
- Skipped `DomainEditorPage` suite leaves the domain authoring flow unguarded.
- Animation "create → export" journey has a hard wall at export.

### 5.5 Hardening
- At-rest encryption posture not verified for learner PII and assessment evidence.
- Email alert path is a placeholder (operator would miss critical incidents).
- Coverage threshold 60% is too soft for a product whose value proposition is correctness/trust.

### 5.6 Data / Integrations
- LTI AGS grade-passback field-test against a real LMS (Canvas/Moodle/Blackboard) unverified.
- GDPR delete cascade over 60+ Prisma models not audited.
- Marketplace payouts/tax/disputes not fully wired (Stripe hooks present; depth unknown).

### 5.7 Testing / Proof
- E2E suite incomplete.
- Mobile tests absent (app absent, but still a claim).
- Animation export zero tests (because stubbed).
- Real-time features zero tests.

### 5.8 Operations
- No evidence of readiness/liveness split beyond Docker `HEALTHCHECK`.
- Alerting is placeholder — operational blind spot.
- Sentry traces at 10% sample is reasonable, but no SLO/burn-rate alerts are defined.

### 5.9 Strategy / Positioning
- Simulation-first + CBM + overconfidence detection + kernel marketplace is a **defensible moat** undersold by the current narrative. The public story still reads as "AI tutor" (commoditized) rather than "evidence-of-learning platform" (differentiated).

---

## 6. Fake Completeness / Shallow Fix Findings

| # | Location | Evidence | Why unacceptable | Risk | Required replacement |
|---|---|---|---|---|---|
| F1 | `services/tutorputor-platform/src/modules/animation-runtime/export-service.ts` L284–L320 | `// For now, return a mock blob` + `new Uint8Array(frames.length * 100)` | Users download **fake video files** branded as course animations | P0 — product-integrity failure, educator trust loss | Integrate FFmpeg.wasm or server-side `ffmpeg` binary behind a worker; render frames; encode MP4/GIF; verify checksum on write |
| F2 | `services/tutorputor-platform/src/auth/index.ts` L185, L189, L471, L475, L549, L557 | `// For now, return a mock user` in 3 distinct auth entry points | Production auth returns stubbed identities — either dead code masquerading as real, or real requests getting mock data | P0 — silent correctness/authz breakage | Route through `tutorputor-core` Prisma `User` repository; delete the mock branches; add regression tests that fail on mock returns |
| F3 | `services/tutorputor-platform/src/monitoring/monitoring.ts` L578 | `"Email alert sent (placeholder implementation)"` | Operator alerting does not fire | P1 — blind in production | Wire Resend/SendGrid adapter; load-test the alert pipeline |
| F4 | `apps/tutorputor-admin/src/pages/__tests__/DomainEditorPage.test.tsx` L45 | `describe.skip(...)` on the entire suite | Core admin flow is unguarded; regressions land silently | P1 | Un-skip or replace; gate merge on green |
| F5 | `apps/tutorputor-web/src/utils/logger.ts` L38 | `// TODO: Integrate with Sentry or other error tracking for production` | Client-side errors never reach observability | P2 | Wire Sentry browser SDK already used server-side |
| F6 | ~30+ frontend files using `@ts-nocheck` | Violates §7 / §26 | Implementation-time typing silently disabled; strictness is cosmetic | P1 | File-by-file removal; add CI rule that bans new `@ts-nocheck` |
| F7 | `services/tutorputor-vr/` | 8 DB models + scaffold without runtime | Feature is advertised; not production-grade | P2 | Either mark Experimental in docs or fund a real delivery |
| F8 | `apps/tutorputor-mobile/` | Placeholder | README lists it as a deployment mode | P1 | Remove from marketing or deliver |
| F9 | `docs/offline-mode-spec.md` | Spec with no runtime | Implies capability | P2 | Implement or mark Roadmap |
| F10 | Real-time collaboration (Redis streams) claim | Not traceable in search | Capability sold but not verifiable | P2 | Verify code path or remove claim |

---

## 7. End-to-End Correctness Findings

| # | Workflow | Expected | Actual | Affected layers/files | Proof status | Severity | Required correction |
|---|---|---|---|---|---|---|---|
| C1 | Educator creates animation → downloads MP4 | Real MP4 bytes | `Blob` of synthetic `Uint8Array` | `animation-runtime/export-service.ts` | **No tests** | **P0** | Real encoder + integrity test |
| C2 | User authenticates, identity resolves to DB row | Real `User` from Prisma | Mock user returned in several sites | `auth/index.ts` | Phase 2A–3 pass despite this (tests may not exercise the mock branch) | **P0** | Remove mock branches; integration test against Postgres |
| C3 | Publish gating blocks a failing unit | Error thrown, no artifact created | OK — validator runs 9 gates | `LearningUnitValidator` | Java tests | OK | — |
| C4 | Tenant A cannot read tenant B data | 403 / empty | OK — enforced at request helpers and query scope | `core/context/request-helpers.ts` | Phase 2C tests | OK | — |
| C5 | LTI grade passback posts to LMS | AGS POST with score | OK (unit); real LMS unverified | `modules/integration/lti/` | Phase 2D tests | Medium | Field test Canvas / Moodle |
| C6 | GDPR delete removes all PII across 60+ Prisma models | All references cascaded | Unverified | `modules/audit/`, export/delete routes | None | High | Cascade audit + integration test |
| C7 | Offline session syncs answers on reconnect | Queued answers delivered, conflicts resolved | Not implemented | `apps/tutorputor-web` (no SW/IndexedDB logic) | None | P1 (if kept as claim) | Implement or retract |
| C8 | Refresh token rotation invalidates old token | Old rejected | OK — Phase 3 tests | `auth/` | Yes | OK | — |
| C9 | Device-fraud risk ≥70 blocks login | Blocked + audit | OK | `auth/` device fraud | Phase 3 | OK | — |
| C10 | Real-time progress update | Students see live board | Claimed; not traceable | Unknown | None | Medium | Verify or retract |

---

## 8. Hardening Findings

| # | Location | Issue | Failure / risk mode | Severity | Required fix |
|---|---|---|---|---|---|
| H1 | Fastify request handlers | Non-uniform boundary validation (not always Zod) | Type-only validation erased at runtime; injection / malformed input | P1 | Schema-at-every-route (§27) |
| H2 | Platform-wide | Correlation-ID middleware not evident | Cross-service traces unlinkable | P1 | OpenTelemetry propagator + middleware per §19 |
| H3 | `monitoring.ts` | Alert system stub | Silent outages | P1 | Real SMTP/Resend adapter + test |
| H4 | `logger.ts` (web) | No Sentry browser SDK | Frontend exceptions invisible | P2 | Wire SDK, env-gated DSN |
| H5 | Frontend | `@ts-nocheck` prevalence | Type-safety regressions | P1 | Ban via ESLint; fix files |
| H6 | CI | 60% coverage floor | Soft floor for a correctness product | P2 | Raise to 75%; critical paths 90% |
| H7 | Data | At-rest encryption posture unverified | PII / evidence exposure | P2 | Validate Postgres TDE / KMS / `pgcrypto` for sensitive columns |
| H8 | Kernel marketplace | Third-party kernel trust model absent | Supply-chain attack vector | P2 | Signed manifests + sandboxed execution + review gate |
| H9 | Animation export | Zero tests | Silent corruption re-lands | P0 | Integration tests that decode output |
| H10 | Skipped test suite | Regressions on `DomainEditorPage` | UX regression risk | P2 | Un-skip |

---

## 9. UI / UX Findings

- **Broken journey**: Create animation → export. UI appears to succeed; download artifact is junk. Must be fixed or hidden.
- **Missing E2E coverage** for: signup → first simulation, teacher creates class → student joins, CBM attempt → Viva trigger, LTI launch round-trip.
- **Skipped test** hides domain-editor UX breakage.
- **Admin + Web** are feature-dense; no indication of empty / error / loading states being systematically tested.
- **Client observability blind**: browser errors do not reach Sentry — operators learn from user complaints.
- **Mobile/offline claims** inflate scope and confuse buyers; either deliver or label Roadmap.

---

## 10. Competitor / Market Analysis

### Direct + indirect landscape (as of April 2026)
- **Khan Academy (+ Khanmigo)** — broad, free, strong brand, weak enterprise LTI depth, generic LLM tutor.
- **Duolingo Max** — gamified; narrow to language; no STEM simulation depth.
- **Brilliant** — interactive STEM/problem-solving; limited institutional/LTI story, no adaptive CBM evidence model.
- **Quizlet / Magic Notes** — study tools; shallow learning science.
- **Century Tech / Squirrel AI / Knewton-heritage platforms** — adaptive engines; closed, heavyweight integration; weak modern UX.
- **Canvas / Moodle / Blackboard + AI plugins** — distribution incumbents; weak native AI tutoring; **our LTI 1.3 story is the Trojan horse**.
- **Open-source**: Open edX, H5P, PhET, ILIAS — strong content primitives, weak AI + adaptive loops.
- **AI-native newcomers**: Rogo, Magic School, Eureka Labs (Karpathy), Synthesis — promising but mostly thin UI over LLM.

### Where they are stronger
- Brand, content library size, LMS distribution (incumbents).
- Raw tutoring UX polish (Khanmigo, Magic School).
- Content volume and SEO (Khan, Quizlet).

### Where they are weaker
- **Evidence of learning** is shallow: most rely on correctness only (no confidence-based marking, no overconfidence detection, no Viva oral probe).
- **Simulation + NL authoring** is either absent or locked to proprietary content (PhET is static).
- **Kernel marketplace / plug-in ecosystem** for simulations is effectively empty.
- **Trust/governance for AI-generated content** is usually post-hoc moderation, not pre-publish multi-gate validation.
- **Multi-tenant + LTI + SSO + device-fraud** stack in a single product is rare in AI-native entrants.

### Whitespace
1. **Outcome-provable learning artifacts** per learner per unit (signed CBM + Viva + evidence bundle).
2. **Pre-publish multi-gate AI content validation** as a visible trust surface.
3. **NL → simulation** where the runtime + SDK + marketplace form a kernel ecosystem.
4. **"Institution-grade AI tutor"** combining LTI AGS + SSO + device fraud + audit — commodity in each piece, rare as a bundle.
5. **Overconfidence remediation loop** (Viva) is genuinely novel in K-12/Higher-Ed AI tutors.

### Where TutorPutor is still commodity
- Flashcards, lesson rendering, leaderboards, generic "AI Q&A".

### Where TutorPutor can credibly lead
- **Evidence-of-learning platform** (CBM + Viva + signed artifacts).
- **Simulation-native adaptive tutor** with kernel marketplace.
- **Institution-grade AI content trust** (multi-gate validator).

---

## 11. Innovation / Disruption Positioning

| # | Proposed differentiator | Customer pain solved | Why incumbents don't | Feasibility | Defensibility | Horizon | Impact |
|---|---|---|---|---|---|---|---|
| I1 | **Signed Evidence-of-Learning Bundles** (CBM score + Viva transcript + simulation traces + xAPI, cryptographically signed per learner per unit) | Employers + institutions distrust AI-era credentials | Incumbents log completion, not evidence | Medium (we already have CBM + Viva + xAPI + audit) | High (trust moat, portable, verifiable) | 1–2 quarters | Acquire (institutions) + retain (learners) + expand (employer-facing) |
| I2 | **Overconfidence Remediation as first-class product surface** | Students "feel" they learned but didn't | No major competitor exposes this | High (engine exists) | Medium-High | 1 quarter | Retention + outcome story for enterprise |
| I3 | **NL→Simulation Kernel Marketplace** with signed kernels + sandboxed runtime + revenue share | Creators cannot monetize STEM content | PhET closed; Brilliant proprietary | Medium (sim-author, sim-runtime, kernel-registry exist) | High (two-sided network effect) | 2 quarters | Ecosystem moat |
| I4 | **Pre-publish 9-Gate AI Content Trust Seal** shown in UI (educational, experiential, technical, safety, a11y pillars visible) | Educators fear AI hallucinations | Competitors post-moderate | High (validator exists) | Medium (brand-level trust) | 1 quarter | Enterprise acquisition |
| I5 | **Institutional Fabric**: LTI 1.3 + SSO + device fraud + GDPR cascade + audit — sold as one | Procurement friction for AI tutors | AI-native startups lack enterprise stack | High (exists; needs hardening) | Medium | 1 quarter | Bigger deal size |
| I6 | **Offline-first low-connectivity mode** (signed bundles + delta sync) | Emerging-market access + field/lab use | Most competitors are cloud-only | Medium | Medium (geographic moat) | 2 quarters | TAM expansion |
| I7 | **Teacher Co-Pilot with Evidence Query** (natural-language queries over signed evidence to produce defensible interventions) | Teachers drown in dashboards | Incumbents give dashboards, not answers | Medium | Medium | 1 quarter | Retention |
| I8 | **Outcome-linked Marketplace** (kernels + content ranked by measured learning gain, not by vendor claim) | Buyers cannot compare | No one does this | Hard (requires N-scale data) | Very High (winner-take-most) | 3–4 quarters | Long-term moat |

**Distinction**:
- Commodity: leaderboards, flashcards, generic AI tutor chat.
- Meaningful: I2, I4, I5, I7.
- Disruptive: **I1, I3, I8**.
- Long-term moat: **I1 (trust) + I3 (ecosystem) + I8 (outcome data)**.

---

## 12. Testing / Proof Gaps

| Capability / use case | Expected proof | Current proof | Missing proof | Confidence | Tests needed |
|---|---|---|---|---|---|
| Student full journey (signup → assessment → sim → CBM → Viva) | Playwright E2E happy + sad paths | Fragment only | Complete suite | Low | 10–15 Playwright specs |
| Animation export | Integration test that decodes MP4/GIF | **None** | Entire layer | **Zero** | Encoder tests + checksum + codec validation |
| Auth identity resolution | Integration test against Prisma/Postgres confirming real user | Unit mocks | Real-DB test | Medium | Container-based integration tests |
| LTI round-trip | Live LMS (Canvas/Moodle) test | Unit (Phase 2D) | Real LMS | Medium | Contract tests via conformance suite |
| GDPR delete cascade | Integration test across 60+ models | None | Entire | Low | Seeded delete test |
| Real-time collaboration | Redis stream tests + browser tests | **None** | Entire | Zero | Verify or retract |
| Offline sync | Conflict-resolution unit + E2E | None | Entire | Zero | Build or retract |
| Kernel marketplace integrity | Signature + sandbox escape test | None | Entire | Zero | Security tests |
| Domain editor UI | Full suite | `describe.skip` | Entire | Zero | Un-skip |
| Admin flows | E2E | Partial | E2E | Low | Playwright |
| Frontend error tracking | Sentry capture test | None | Entire | Zero | Browser smoke |

---

## 13. Current Release Blockers

Only blockers for a credible production release:

1. **Animation export returning fake bytes** (`export-service.ts` L284–L320).
2. **Mock user lookups in production auth path** (`auth/index.ts` 6 sites).
3. **No end-to-end E2E proof** of the core student + educator journeys.
4. **`@ts-nocheck` in 30+ files** — type-safety mandate violation (§7/§26).
5. **Correlation-ID propagation / distributed tracing** absent.
6. **Frontend Sentry wiring** missing (operators blind to client errors).
7. **Skipped `DomainEditorPage` test suite** — uncontrolled regression path.
8. **Email alerting stub** — operational visibility gap.
9. **Mobile + offline claims** without implementation — must be retracted from public narrative or implemented.
10. **GDPR delete cascade** unverified across 60+ models.

---

## 14. Prioritized Remediation & Advancement Plan

### Phase 0 — Release Blockers + Fake Completeness + Correctness (fix-forward)
| Item | Old/New | Why | Fix direction | Expected impact | Priority |
|---|---|---|---|---|---|
| Replace animation export stub with real encoder | New | Product-integrity failure | FFmpeg.wasm or worker-side `ffmpeg`; integration test decoding output | Core educator artifact delivered | P0 |
| Remove mock user lookups from `auth/index.ts` | New | Silent authz correctness risk | Route through Prisma `User` repo; container integration test | Auth trustworthy | P0 |
| Complete E2E Playwright suite for 6 critical journeys | New | No end-to-end proof | Student signup → sim → CBM → Viva; Educator create → validate → publish → export; LTI round-trip | Release confidence | P0 |
| Ban `@ts-nocheck`, fix files incrementally | New | Violates §7/§26 | ESLint rule + per-file cleanup budget | Type-safety restored | P1 |
| Un-skip or delete `DomainEditorPage` suite | New | Regression shield off | Fix or replace | Admin reliability | P1 |

### Phase 1 — Hardening + Proof
| Item | Why | Fix direction | Priority |
|---|---|---|---|
| Zod at every Fastify route | Runtime boundary validation (§27) | Schema codegen from `contracts/v1/` | P1 |
| Correlation-ID middleware + OpenTelemetry propagation (§19) | Distributed tracing | Fastify + worker plumbing + Sentry trace link | P1 |
| Client Sentry SDK | Frontend visibility | Wire in `logger.ts` | P1 |
| Real email alert path | Ops visibility | Resend/SendGrid adapter + synthetic alert drill | P2 |
| Coverage floor 75% (90% critical paths) | Correctness product | CI threshold lift | P2 |
| GDPR cascade integration test | Legal + trust | Seeded delete test | P2 |
| At-rest encryption posture audit | PII safety | Confirm KMS/TDE/`pgcrypto` | P2 |
| Kernel signing + sandbox | Marketplace trust | Signed manifest + isolated VM / Deno compartment | P2 |

### Phase 2 — Use-Case Completion + UX + Ops
| Item | Why | Fix direction | Priority |
|---|---|---|---|
| Mobile app: deliver MVP or retract claim | Narrative credibility | Decide + roadmap; RN app with shared contracts | P1 (decide), P2 (deliver) |
| Offline mode: deliver or retract | Narrative credibility | SW + IndexedDB + delta sync; or mark Roadmap | P2 |
| Real-time collaboration: verify or retract | Narrative credibility | Prove Redis streams path or remove | P2 |
| Field-test LTI AGS against Canvas + Moodle | Enterprise credibility | Conformance suite + partner pilots | P2 |
| Marketplace payout/tax/dispute depth | Commercial credibility | Stripe Connect completion | P2 |
| SLO + burn-rate alerts | Operational maturity | Monitoring module extension | P2 |

### Phase 3 — Innovation / Disruption / Frontrunner
| Item | Why | Fix direction | Priority |
|---|---|---|---|
| Signed Evidence-of-Learning bundles (I1) | Trust moat for AI-era credentials | CBM + Viva + sim traces + xAPI → signed portable artifact | P1 (strategic) |
| Public Trust Seal for AI content (I4) | Educator trust differentiation | Surface 9-gate validator pillars in UI + external verifier | P2 |
| NL→Simulation Kernel Marketplace (I3) | Ecosystem moat | Creator onboarding + signed kernels + revenue share | P2 |
| Overconfidence Remediation as a product surface (I2) | Differentiation vs. generic AI tutors | Promote Viva to first-class learner surface | P2 |
| Teacher Co-Pilot w/ evidence query (I7) | Retention | NL query over signed evidence → intervention suggestions | P3 |
| Outcome-linked marketplace ranking (I8) | Long-term data moat | Learning-gain telemetry → ranking | P3 |

---

## Appendix A — Evidence File Index

| Area | Key files |
|---|---|
| Architecture | [docs/architecture/specs/PRODUCT_SPEC.md](architecture/specs/PRODUCT_SPEC.md), [docs/architecture/CURRENT_STATE.md](architecture/CURRENT_STATE.md), [docs/architecture/DESIGN_ARCHITECTURE.md](architecture/DESIGN_ARCHITECTURE.md) |
| Auth/Security | [services/tutorputor-platform/src/auth/index.ts](../services/tutorputor-platform/src/auth/index.ts), Phase 2–3 tests under `services/tutorputor-platform/src/__tests__/` |
| Animation blocker | [services/tutorputor-platform/src/modules/animation-runtime/export-service.ts](../services/tutorputor-platform/src/modules/animation-runtime/export-service.ts#L284) |
| Monitoring stub | [services/tutorputor-platform/src/monitoring/monitoring.ts](../services/tutorputor-platform/src/monitoring/monitoring.ts#L578) |
| Client logger TODO | [apps/tutorputor-web/src/utils/logger.ts](../apps/tutorputor-web/src/utils/logger.ts#L38) |
| Content generation (Java) | [services/tutorputor-content-generation/](../services/tutorputor-content-generation/) |
| Sim stack | [services/tutorputor-sim-author/](../services/tutorputor-sim-author/), [sim-nl](../services/tutorputor-sim-nl/), [sim-runtime](../services/tutorputor-sim-runtime/), [sim-sdk](../services/tutorputor-sim-sdk/), [kernel-registry](../services/tutorputor-kernel-registry/) |
| Data model | [libs/tutorputor-core/prisma/schema.prisma](../libs/tutorputor-core/prisma/schema.prisma) |
| CI/CD | [.github/workflows/tutorputor-ci.yml](../.github/workflows/tutorputor-ci.yml), [Dockerfile](../services/tutorputor-platform/Dockerfile) |
| Phase 3 report | [services/tutorputor-platform/PHASE_3_COMPLETION_REPORT.md](../services/tutorputor-platform/PHASE_3_COMPLETION_REPORT.md) |
| Skipped test | [apps/tutorputor-admin/src/pages/__tests__/DomainEditorPage.test.tsx](../apps/tutorputor-admin/src/pages/__tests__/DomainEditorPage.test.tsx#L45) |
| E2E fragment | [e2e/critical-flows.spec.ts](../e2e/critical-flows.spec.ts) |

---

**End of report.**
