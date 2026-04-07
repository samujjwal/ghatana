# YAPPC Release Evidence Bundle

The canonical release artifact is the `yappc-release-evidence-bundle` uploaded by `.github/workflows/yappc-ci.yml`.

## What It Includes

- backend unit and integration test artifacts
- frontend unit test and coverage artifacts
- Playwright report and E2E result artifacts
- lifecycle startup diagnostics proving `/health/liveness`, `/health/readiness`, and authenticated `/metrics`
- contract-test report for OpenAPI verification

## Review Rule

Do not approve a release candidate by reading scattered workflow logs manually. Review the generated bundle first, then drill into linked artifacts only where the bundle shows a missing or failed surface.

## Failure Rule

If the evidence bundle is missing, incomplete, or shows a missing artifact for a critical journey, the release candidate is not ready.