# FlashIt Product Scorecard

- Current classification: planned mobile + API shape validator.
- Target classification for phase: mobile lifecycle shape validator without unsafe execution enablement.
- Surfaces: mobile/API surfaces as declared in registry.
- Adapters: Android/iOS artifact adapters are not ready for executable lifecycle enablement.
- Gates: preview, privacy, personal-data, and mobile artifact gates remain required.
- Manifests: mobile IPA/AAB artifact manifests are blockers before execution.
- Lifecycle execution allowed: false.
- Validation commands: `pnpm check:flashit-lifecycle-readiness`, `pnpm check:product-shape-capability-matrix`.
- Failing gaps: lifecycle execution blocked until mobile artifact contracts and adapters are real.
- Boundary impact: product remains a validator and does not own generic lifecycle runtime.
- Duplication impact: no duplicate mobile lifecycle runner added.
- Security/privacy/o11y readiness: privacy and personal-data gates remain declared blockers.
- Next required work: add real mobile artifact contracts and adapter execution evidence.
