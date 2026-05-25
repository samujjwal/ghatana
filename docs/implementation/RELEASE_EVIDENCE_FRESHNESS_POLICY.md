# Release Evidence Freshness Policy

## Purpose

Define one enforceable contract for release evidence freshness, provenance, and revalidation so release decisions are always tied to current code and explicit execution context.

This policy is implemented by:

- `config/evidence-freshness-policy.json`
- `scripts/check-evidence-freshness.mjs`
- `scripts/check-evidence-current-commit.mjs`
- `scripts/validate-release-evidence.mjs`

## Canonical Evidence Metadata

Release evidence that participates in release verdicts should include:

- `generatedAt`: UTC ISO timestamp for evidence generation.
- `sourceCommitSha`: source commit that generated the evidence.
- `targetCommitSha`: commit the evidence is intended to validate.
- `targetEnvironment`: release environment (`local`, `dev`, `staging`, `prod`).
- `validationStatus`: `validated` or `failed`.
- `reviewDueAt` or `expiresAt`: freshness/renewal boundary.

## Freshness Classes

Freshness and failure behavior is driven by `config/evidence-freshness-policy.json`.

- `runtime-production`:
  - Critical runtime evidence.
  - Tight freshness threshold.
  - Fails closed when stale.
- `runtime-executed`:
  - Evidence generated from executable tests/checks.
  - Fails closed when stale.
- `generated-on-demand`:
  - Regenerable evidence derived from source/config.
  - Can be stale without immediate release failure when policy marks `failOnStale=false`.
- `static-configuration`:
  - Low-churn configuration-derived evidence.
  - Relaxed freshness threshold.
- `release-summary`:
  - Aggregated release scorecards and gate snapshots.
  - Must remain fresh for release decisions.

## Source-Backing Rule

For evidence types with `requireSourceBacking=true`, the validator enforces source-backing metadata when the evidence follows migrated release metadata schema (`validationStatus`, commit fields, or `evidenceRun`).

Accepted source-backing fields include:

- `source`
- `sourceCommand`
- `generator`
- `provenance.source`
- `metadata.source`
- `summary.source`
- `evidenceRun.command`
- `evidenceRun.script`

This phased rule prevents false failures for legacy evidence while strictly validating migrated release evidence.

## Commit Binding and Revalidation

Revalidation is required when any of the following changes:

- `targetCommitSha`
- `targetEnvironment`
- lifecycle blocker/gate status
- critical runtime dependencies used by product release gates

In release mode, target commit mismatch is a release-blocking error.

Evidence files that include `evidenceRun.commit` are additionally bound to the current checkout by `pnpm check:evidence-current-commit`.

## Operational Guidance

When evidence fails freshness or source-backing checks:

1. Re-run the producing check/script for the affected evidence file.
2. Verify commit and environment metadata are updated.
3. Re-run `pnpm check:validate-release-evidence`.
4. Re-run `pnpm check:product-release-readiness` for affected products.
5. Run `pnpm check:release-gate` for final release validation.

## Scope and Non-Goals

- This policy governs release evidence and release-decision artifacts.
- Historical archives (`history`, `archived`, backups) are outside strict freshness enforcement unless explicitly reintroduced into release decision paths.
