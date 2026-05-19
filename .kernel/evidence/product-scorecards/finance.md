# Finance Product Scorecard

- Current classification: planned business-product shape validator.
- Target classification for phase: backend-heavy compliance validator without executable lifecycle enablement.
- Surfaces: finance domain/API/operator surfaces as declared in registry.
- Adapters: not execution-ready for operator, portal, and SDK lifecycle proof.
- Gates: compliance, risk, promotion, and approval readiness gates remain required before enablement.
- Manifests: readiness manifests tracked through registry and lifecycle readiness evidence.
- Lifecycle execution allowed: false.
- Validation commands: `pnpm check:finance-lifecycle-readiness`, `pnpm check:product-shape-capability-matrix`.
- Failing gaps: lifecycle execution blocked until required adapters and compliance gates are implemented.
- Boundary impact: remains product-owned; no platform lifecycle behavior is implemented locally.
- Duplication impact: no generic lifecycle runner added.
- Security/privacy/o11y readiness: compliance posture is declared as a blocker, not represented as executable truth.
- Next required work: implement required adapters and gate evidence before registry enablement.
