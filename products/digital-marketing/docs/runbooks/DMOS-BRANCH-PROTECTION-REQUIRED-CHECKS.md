# DMOS Branch Protection Required Checks

## Purpose
This runbook defines the exact status checks that must be marked as required in GitHub branch protection so DMOS quality gates become merge blockers.

This is the operations step that completes P0 testing enforcement after workflow wiring is already in-repo.

## Required Status Checks
Use the workflow job name values exactly as listed below:

- Test Gate
- Contract Drift Check
- P0 Release Gate

Source workflows:

- .github/workflows/dmos-test-matrix.yml (job name: Test Gate)
- .github/workflows/dmos-openapi-client-gen.yml (job name: Contract Drift Check)
- .github/workflows/dmos-release-gate.yml (job name: P0 Release Gate)

## Branches
Apply to:

- main
- release/*

## GitHub UI Steps
1. Open repository Settings.
2. Open Branches (or Rulesets if your org uses repository rulesets).
3. Edit protection for main.
4. Enable Require status checks to pass before merging.
5. Add required checks:
   - Test Gate
   - Contract Drift Check
   - P0 Release Gate
6. Enable Require branches to be up to date before merging.
7. Save the rule.
8. Repeat for release/*.

## Verification Procedure
1. Open a DMOS pull request that changes files under products/digital-marketing.
2. Confirm all three checks appear in the PR checks list.
3. Push a commit that intentionally fails one DMOS gate (for example, break a route contract assertion).
4. Confirm merge is blocked while the failed check is red.
5. Revert the failing change and confirm merge unblocks only after all required checks pass.

## Expected Outcome
A pull request cannot merge when any DMOS gate fails:

- UI type/lint/route contract gate (via Test Gate and P0 Release Gate dependencies)
- API contract and OpenAPI/client drift gate (via Contract Drift Check)
- Backend and release blocker validation suites (via P0 Release Gate)

## Ownership
- DMOS engineering lead: keeps required check names aligned with workflow job names.
- Repository administrators: maintain branch protection settings in GitHub.
