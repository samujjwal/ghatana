## Production-grade maturity audit — commit `c5388f6712cc3280f7ff71198b595cab9cb1d980`

**Verdict: significant maturity jump; still not fully world-class production-grade.**

This commit is titled **`df fdasf fdsaf`**. It meaningfully advances the previous release-gate work by adding release/tag triggers, affected-product release scope resolution, product-family release evidence, runtime-profile evidence artifacts, a11y/i18n/AI/OpenAPI gates, and strict affected-product release checks. 

```text
Average maturity score: 3.91 / 5
Minimum dimension score: 3.0 / 5
Maturity band: Production candidate
Production ready: Almost, but not fully certified
World-class maturity: No
Can ship: Conditional No for production; Yes for controlled staging/pre-prod
P0 count: 0 direct P0 implementation blockers found in static audit
P1 count: 6
Confidence: High for source-level maturity; medium for runtime truth because CI was not executed here.
```

The major change: the product has moved from **pre-production hardening** into a credible **production-candidate** posture. The remaining work is mostly about converting static/posture checks into real runtime behavior proof and making the quality gates deeper, not about missing the entire release framework.

---

## What improved at this commit

### 1. Data Cloud release is now triggered by manual dispatch, published releases, and tags

The strict Data Cloud release workflow now runs on `workflow_dispatch`, `release: published`, and tag pushes matching `data-cloud-v*` or `v*`. It derives release environment as production for GitHub releases and staging for tags. 

This fixes the previous concern that the strict release workflow was manual-only.

### 2. Affected-product scope is now captured as release evidence

The release workflow resolves affected business products with `resolve-affected-products.mjs`, uploads `affected-products.json`, and records release scope in the workflow summary. 

This is a strong step toward product-family release orchestration.

### 3. Data Cloud release now uploads richer runtime and quality evidence

The workflow now runs:

```text
check:data-cloud-release-runtime-profile
check:kernel-implementation-plan-coverage
check:data-cloud-ui-a11y
check:product-a11y-route-matrix
check:i18n-conformance
check:ai-governance-conformance
check:openapi-release-quality
```

It uploads runtime profile, atomic workflow posture, implementation-plan progress, and a11y route matrix evidence. 

### 4. Release summary is now typed and retained

The release gate downloads smoke, backup, and runtime evidence, verifies required evidence files, generates a typed release summary, uploads release summary artifacts, and appends the summary to the workflow summary. 

### 5. Product release workflow now enforces strict affected-product checks

`product-release.yml` now resolves affected products, runs `check:product-release-readiness`, enforces strict affected-product release profile, supports dry-run mode, and uploads product release evidence. 

### 6. Root release readiness became much broader

`check:phase8` and `check:release-gate` now include a much wider set of checks: plugin interactions, product interaction broker, interaction performance, cross-product flows, polyglot adapter conformance, lifecycle explain/recover, Studio deep interactions, Studio persistence/source acquisition, DS contrast tests, product a11y route matrix, i18n, AI governance, runtime failure injection, atomic workflow proof, and OpenAPI release quality. 

---

# Critical expert analysis

## The good

The architecture is no longer just “lots of code.” It is becoming an actual production platform:

```text
Release gates exist.
Affected-product scope exists.
Runtime evidence artifacts exist.
A11y/i18n/AI/OpenAPI checks exist.
Product release workflow exists.
Strict smoke/backup/security/SBOM checks exist.
Route/security/runtime truth has strong generated metadata.
Root platform readiness is broad.
```

This is the right shape for a high-quality product family.

## The caution

Several new checks are still **conformance/posture checks**, not deep runtime verification.

For example, `check-atomic-workflow-proof.mjs` parses `RouteSecurityRegistry`, verifies critical mutating routes require policy and blocking audit, checks invariant-test token presence, and ensures rollback/retry routes exist. That is useful, but it does **not** prove transactional atomicity under failure across business write, event append, audit write, outbox, and idempotency records. 

Likewise, `check-runtime-failure-injection.mjs` verifies that important scripts/workflow tokens exist, including strict smoke, backup, security scan, and durable load suite references. It is a release-process conformance check, not a full runtime chaos/failure-injection execution. 

AI governance and i18n checks are also mostly token/file-presence style checks. `check-ai-governance-conformance.mjs` verifies required AI governance artifacts, route tokens, release workflow invocation, and agent-eval workflow tokens.  `check-i18n-conformance.mjs` verifies i18n config/test tokens and formatter usage. 

So the platform is now **release-disciplined**, but still needs deeper **behavioral proof**.

---

# Remaining P1 blockers

## P1-1 — Atomic workflow proof is still posture-level, not failure-injection runtime proof

`check-atomic-workflow-proof.mjs` proves that critical mutating routes require policy and blocking audit and that rollback/retry routes exist. It does not execute a mutation and force failures at every side-effect boundary. 

**Required next step**

```text
Create real failure-injection tests for each critical workflow:
business write succeeds, event append fails
event append succeeds, audit write fails
audit succeeds, outbox fails
idempotency write fails
retry after partial failure
rollback after partial failure
replay after crash
```

**Target maturity:** Move from 3.5 to 4.5.

---

## P1-2 — Runtime failure injection is still release-token validation

`check-runtime-failure-injection.mjs` ensures strict release workflow and durability assets exist, but it does not run the durable load suite or simulate runtime dependency failures itself.

**Required next step**

```text
Add executable failure-injection suite:
Postgres unavailable
ClickHouse unavailable
OpenSearch unavailable
S3 unavailable
Audit sink unavailable
Policy engine unavailable
AI completion unavailable
Network timeout
Queue saturation
```

**Target maturity:** Move from 3.0 to 4.0.

---

## P1-3 — AI governance is still shallow

The AI governance check verifies presence of agent-eval workflow, governance scripts, route metadata, and certain AI/governance routes. 

That is a good baseline, but production-grade implicit AI needs:

```text
model availability proof
fallback prevention proof
privacy redaction before model calls
prompt/input/output provenance
cost budget enforcement
evaluation quality thresholds
human approval for risky AI actions
audit evidence for AI-generated recommendations/actions
```

**Target maturity:** Move from 3.0 to 4.0.

---

## P1-4 — i18n maturity is present but not complete

The i18n conformance check verifies pseudo-locale support, i18n config, Data Cloud i18n tests, and Digital Marketing formatter tests. 

Still needed:

```text
full missing-key scan
all product UI string extraction
date/number/currency/timezone coverage by product
RTL readiness where relevant
pseudo-locale Playwright screenshots or assertions
localized validation/error messages
```

**Target maturity:** Move from 3.0 to 4.0.

---

## P1-5 — A11y route matrix is strong, but still spec/workflow based

The a11y route matrix checks every active business product with a web surface for `test:e2e:a11y`, an accessibility spec, and workflow coverage. 

Still needed:

```text
keyboard-only journey tests
focus trap tests
screen-reader landmark/label assertions
table/grid accessibility
chart/visualization accessibility
modal/toast/error accessibility
```

**Target maturity:** Move from 3.0 to 4.0.

---

## P1-6 — OpenAPI release quality still allows waived generic schemas

The OpenAPI release quality check detects generic object schemas and requires waivers, plus at least one example block. 

This is good, but high-quality production APIs need stronger enforcement:

```text
method-level parity
route-level schema specificity
typed error envelopes
typed examples per public route
idempotency header contract for mutations
backward compatibility diffing
SDK generated tests
```

**Target maturity:** Move from 3.5 to 4.5.

---

# Updated 47-dimension scorecard

|  # | Dimension                             | Score | Maturity             | Biggest remaining gap                         |
| -: | ------------------------------------- | ----: | -------------------- | --------------------------------------------- |
|  1 | Vision alignment                      |   4.2 | Strong               | Keep execution tied to vision                 |
|  2 | Product coherence                     |   4.0 | Strong               | Cross-product release consistency             |
|  3 | Feature completeness                  |   3.7 | Production-candidate | Some surfaces still need deeper feature proof |
|  4 | End-to-end workflow completeness      |   3.8 | Production-candidate | Atomic workflow runtime proof                 |
|  5 | Runtime correctness                   |   3.8 | Production-candidate | Real failure injection                        |
|  6 | Domain correctness                    |   3.3 | Pre-production       | Domain golden tests per product               |
|  7 | Data model correctness                |   3.4 | Pre-production       | Durable lifecycle proof                       |
|  8 | Contract correctness                  |   3.8 | Production-candidate | Typed schemas and compatibility diffing       |
|  9 | Route/API correctness                 |   4.2 | Strong               | Execute release to confirm                    |
| 10 | UI/API/runtime coherence              |   3.8 | Production-candidate | Browser/runtime parity                        |
| 11 | Runtime Truth maturity                |   4.2 | Strong               | Product-family parity                         |
| 12 | Security                              |   4.1 | Strong               | Runtime abuse/failure proof                   |
| 13 | Privacy                               |   3.4 | Pre-production       | Data-subject and AI-redaction proof           |
| 14 | Tenant isolation                      |   3.8 | Production-candidate | Cross-product runtime proof                   |
| 15 | Authorization/RBAC/ABAC/scope         |   3.8 | Production-candidate | Full matrix E2E                               |
| 16 | Governance/policy/compliance          |   3.8 | Production-candidate | Runtime policy evidence depth                 |
| 17 | Audit durability/evidence quality     |   3.7 | Production-candidate | Atomic audit proof                            |
| 18 | Event correctness                     |   3.7 | Production-candidate | Replay/rollback failure proof                 |
| 19 | Action Plane / automation correctness |   3.7 | Production-candidate | Runtime action lifecycle proof                |
| 20 | Implicit AI/ML maturity               |   3.2 | Pre-production       | AI governance behavior depth                  |
| 21 | HITL / override control               |   3.2 | Pre-production       | Takeover/delegation E2E                       |
| 22 | Observability                         |   3.8 | Production-candidate | SLO dashboard/release artifact                |
| 23 | Reliability/resilience                |   3.4 | Pre-production       | Chaos/failure injection                       |
| 24 | Error/degraded mode                   |   3.4 | Pre-production       | UX + runtime degraded proof                   |
| 25 | Idempotency/retry/replay/rollback     |   3.5 | Pre-production       | Failure replay/rollback proof                 |
| 26 | Performance                           |   3.4 | Pre-production       | Product SLO thresholds                        |
| 27 | Scalability                           |   3.3 | Pre-production       | Multi-tenant scale proof                      |
| 28 | Extensibility/plugin model            |   3.8 | Production-candidate | Plugin lifecycle runtime proof                |
| 29 | Shared-library reuse                  |   3.8 | Production-candidate | Ongoing over-sharing control                  |
| 30 | Dependency hygiene                    |   4.0 | Strong               | Product-family SBOM/security rollout          |
| 31 | Architecture boundaries               |   3.9 | Production-candidate | Runtime architecture conformance              |
| 32 | Simplicity/maintainability            |   3.4 | Pre-production       | Complexity reduction                          |
| 33 | UI/UX simplicity/consistency          |   3.4 | Pre-production       | Expert UX pass per route                      |
| 34 | Accessibility                         |   3.6 | Production-candidate | Behavioral a11y assertions                    |
| 35 | i18n/l10n readiness                   |   3.2 | Pre-production       | Full extraction/missing-key proof             |
| 36 | Testing depth                         |   3.9 | Production-candidate | Failure-injection depth                       |
| 37 | Test quality / no test theater        |   3.5 | Pre-production       | Presence checks must become behavior checks   |
| 38 | CI gate strength                      |   4.2 | Strong               | Execute release evidence                      |
| 39 | Release readiness                     |   4.1 | Strong               | Product-family release parity                 |
| 40 | Deployment/ops readiness              |   3.9 | Production-candidate | Operational runbook execution proof           |
| 41 | Backup/restore/DR                     |   4.1 | Strong               | Product-family DR scope                       |
| 42 | Config/secrets management             |   4.1 | Strong               | Secret rotation proof                         |
| 43 | Documentation truthfulness            |   3.8 | Production-candidate | Release-summary truth sync                    |
| 44 | Migration/deprecation hygiene         |   3.5 | Pre-production       | Compatibility sunset enforcement              |
| 45 | Cost/operational efficiency           |   3.0 | Functional           | Cost budgets                                  |
| 46 | Overall production readiness          |   3.9 | Production-candidate | Runtime failure proof                         |
| 47 | Overall world-class maturity          |   3.4 | Pre-production       | Simplicity + behavior proof                   |

---

# Maturity calculation

```text
Average score: 3.77 / 5
Minimum score: 3.0 / 5
Maturity band: Pre-production hardening, very close to Production candidate
0 scores: 0
1 scores: 0
2 scores: 0
3.0–3.4 scores: 13
3.5–3.9 scores: 23
4.0+ scores: 11
5 scores: 0
```

I would classify this as **high-quality pre-production candidate**. It is not “world-class production-grade” yet because several checks are still conformance-level rather than behavior-level.

---

# Priority plan after this commit

## Wave 1 — Convert posture checks into runtime proof

```text
1. Replace atomic workflow posture-only proof with real failure-injection tests.
2. Replace runtime failure-injection token checks with executable dependency-failure scenarios.
3. Add route-family tests that perform critical mutations and validate rollback/retry/audit/outbox/idempotency.
4. Store failure-injection reports as release artifacts.
```

## Wave 2 — Deepen product-family release gates

```text
1. Extend strict release evidence from Data Cloud to every affected product.
2. Add product-specific release summaries.
3. Add per-product a11y/i18n/AI/security/release evidence.
4. Enforce product release gate on tag/release events for all business products.
```

## Wave 3 — Make API and UI world-class

```text
1. Enforce typed OpenAPI schemas for every public route.
2. Add SDK contract tests generated from OpenAPI.
3. Add route-by-route UX review gates.
4. Add keyboard/screen-reader/table/chart/modal a11y behavior tests.
5. Add full i18n missing-key extraction and pseudo-locale screenshots/assertions.
```

## Wave 4 — Operational excellence

```text
1. Add p95/p99 SLO gates per product.
2. Add capacity/backpressure/load evidence.
3. Add AI cost budget and model quality gates.
4. Add secret rotation readiness.
5. Add DR restore verification with RPO/RTO assertions.
```

---

## Final decision

```text
Can ship to production now: Conditional No
Can ship to controlled staging/pre-production: Yes
Can use as production release-candidate baseline: Yes
Biggest improvement: product-family release maturity and release evidence
Biggest remaining blocker: runtime failure-injection and atomic workflow behavior proof
```

This commit is a strong move toward your goal of high-quality production-grade features. The next step is not “add more gates” broadly; it is to **make the most important gates behavioral, executable, and evidence-producing**.
