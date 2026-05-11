# Release Gate Failure Runbook

## Trigger

- `.github/workflows/dmos-release-gate.yml` fails any production-blocking job.
- Static guard, contract drift, privacy, bootstrap, E2E, or analytics gate blocks release.

## Immediate Containment

1. Treat the release as blocked; do not bypass required checks.
2. Identify the failed job, step, commit SHA, and artifact/log link.
3. Preserve generated reports, Playwright traces, test XML, and release report.
4. Notify the owning team for the failed gate.

## Investigation

1. Reproduce the failing command locally where possible.
2. Determine whether failure is product behavior, environment setup, flaky infrastructure, or stale generated artifact.
3. Check whether OpenAPI, route manifest, generated TS, or generated Java artifacts drifted.
4. Inspect whether a guard is correctly blocking unsafe production readiness claims.

## Recovery

1. Fix the root cause in code, docs, generated artifacts, or CI setup.
2. Add or update tests if the failure exposed missing coverage.
3. Re-run the failed gate and the adjacent affected gates.
4. Keep the release blocked until the full release summary is green.

## Verification

- Failed job passes on the updated commit.
- Full release gate summary marks every required job successful.
- Any changed readiness claim links to passing evidence.
- No warning-only production gate remains for the failed class.
