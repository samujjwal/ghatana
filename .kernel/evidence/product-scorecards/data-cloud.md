# Data Cloud Product Scorecard

- Current classification: platform-provider product.
- Target classification for phase: governed runtime truth provider with optional Kernel bridge mode.
- Surfaces: Data Cloud provider bridge, event, governance, memory, provenance, telemetry, and runtime truth planes.
- Adapters: provider-mode contracts; business-product lifecycle execution remains disabled.
- Gates: bootstrap vs platform provider mode, runtime truth provider health, event/provenance/memory/telemetry conformance.
- Manifests: provider evidence uses public Kernel provider contracts and Data Cloud bridge readiness checks.
- Lifecycle execution allowed: false.
- Validation commands: `pnpm check:data-cloud-platform-providers`, `pnpm check:data-cloud-platform-provider-readiness`, `pnpm check:kernel-provider-mode`.
- Failing gaps: none in current provider-readiness checks.
- Boundary impact: Kernel does not import Data Cloud internals; Data Cloud implements bridge contracts product-side.
- Duplication impact: no product-specific business behavior is added to shared platform packages.
- Security/privacy/o11y readiness: provider mode remains explicit and degraded states are represented without fake success.
- Next required work: keep Data Cloud-backed provider implementations queryable through public provider contracts.
