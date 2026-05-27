# YAPPC Release Evidence Bundle

The canonical release artifact is the `yappc-release-evidence-bundle` uploaded by `.github/workflows/yappc-ci.yml`.

## What It Includes

- backend unit and integration test artifacts
- frontend unit test and coverage artifacts
- Playwright report and E2E result artifacts
- lifecycle startup diagnostics proving `/health/liveness`, `/health/readiness`, and authenticated `/metrics`
- contract-test report for OpenAPI verification
- `yappc-scorecard-evidence.json`, which maps release-readiness scorecard dimensions to validation commands, evidence references, required artifacts, and present/missing status
- `yappc-ci-execution-proof.json`, which summarizes CI artifact presence by major YAPPC check family

## Review Rule

Do not approve a release candidate by reading scattered workflow logs manually. Review the generated bundle first, then drill into linked artifacts only where the bundle shows a missing or failed surface.

## Failure Rule

If the evidence bundle is missing, incomplete, or shows a missing artifact for a critical journey, the release candidate is not ready.

## Local Generation

```bash
bash products/yappc/scripts/generate-release-evidence.sh products/yappc/build/release-evidence artifacts
node products/yappc/scripts/generate-ci-execution-proof.mjs products/yappc/build/release-evidence artifacts
node products/yappc/scripts/generate-yappc-scorecard-evidence.mjs products/yappc/build/release-evidence artifacts
node products/yappc/scripts/check-yappc-scorecard-evidence.mjs products/yappc/build/release-evidence/yappc-scorecard-evidence.json
```
