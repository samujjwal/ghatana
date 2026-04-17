# Social Proof Checklist

## Pre-run

- Confirm social routes deployed for target environment.
- Confirm test tenant and test user identities exist.
- Confirm auth headers/correlation ID capture is enabled.

## Run

- Execute collect-social-validation-evidence script.
- Execute verify-social-routes script.
- Save logs under docs/operations.

## Post-run

- Populate environment evidence file.
- Update SOCIAL_PROOF_STATUS_MATRIX_2026-04-16.md.
- Complete SOCIAL_PROOF_SIGNOFF_2026-04-16.md.
