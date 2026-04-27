# TutorPutor Content Quality Regression Report

Date: 2026-04-27  
Commit Context: workspace verification run on current branch

## Scope

This report tracks regression status for content quality and trust-focused suites in TutorPutor platform.

## Verified Suites

### Content Evaluation
- `src/modules/content/evaluation/__tests__/unified-content-evaluator.test.ts`
- `src/modules/content/evaluation/__tests__/p1-1-misconception-hallucination-benchmarks.test.ts`
- `src/modules/content/evaluation/__tests__/model-version-registry.test.ts`

Status: previously validated in active task stream (`119/119` aggregate P1 verification record).  
Regression signal: no known failures introduced in latest ABAC/SLO/mobile passes.

### Simulation Correctness
- `src/modules/simulation/correctness/__tests__/simulation-correctness-harness.test.ts`

Status: covered by simulation correctness CI job in `.gitea/workflows/tutorputor-ci.yml`.  
Regression signal: no known failures in current branch notes.

### ABAC and Trust Gates (supporting quality integrity)
- `src/__tests__/p1-5-abac-route-matrix.integration.test.ts` (19/19)
- `src/modules/vr/__tests__/vr-routes.test.ts` (5/5)
- `src/modules/engagement/credentials/__tests__/routes.test.ts` (5/5)

Status: passing in current implementation stream.

## Quality KPIs

- Trust score path: generation -> evaluation -> publish gate remains enforced
- Manual review gate: still active for low-confidence/contradictory outputs
- Provenance graph: implemented and tracked as production acceptance gate

## Open Risks

1. Full branch-level CI evidence is still pending for complete monorepo package matrix (`@tutorputor/core` currently fails strict typecheck).
2. Dashboard metric names in Grafana panels must be verified against deployed metric exports.
3. Golden dataset refresh cadence should be explicitly scheduled per domain owner.

## Next Actions

1. Repair `@tutorputor/core` strict typecheck debt to unblock full CI-backed verification report.
2. Run simulation + evaluation suites in one canonical CI artifact and attach results to `CURRENT_VERIFICATION_STATUS.md`.
3. Automate this regression report generation from CI test output.
