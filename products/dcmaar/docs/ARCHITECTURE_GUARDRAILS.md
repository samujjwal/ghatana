# DCMAAR Architecture Guardrails

This document captures repository-level guardrails that protect the separation between **frameworks** and **apps** in the DCMAAR area of the monorepo.

It complements the day-by-day implementation plan in `DCMAAR_FRAMEWORK_GUARDIAN_DEVICE_HEALTH_IMPLEMENTATION_PLAN.md`.

## 1. Directory Roles

- `products/dcmaar/framework/*` – DCMAAR platform frameworks (agent, server, desktop, browser-extension, shared libs).
- `products/dcmaar/apps/*` – Product apps (Guardian, Device Health, future apps) built on top of the frameworks.
- `products/dcmaar/contracts/*` – Platform contracts (`dcmaar.v1` protobufs) shared by frameworks and apps.

Frameworks are **product-neutral** and must not depend on app-specific code.

## 2. Dependency Rules

1. `framework/*` → `apps/*`
   - Framework code must not import, reference, or depend on any code under `products/dcmaar/apps/*`.
   - Example of disallowed patterns:
     - Relative imports that traverse into `../apps/...`.
     - Hard-coded references to `apps/guardian` or `apps/device-health` paths.

2. `apps/*` → `framework/*`
   - Apps may depend on frameworks only through:
     - Public contracts (e.g., `dcmaar.v1` protobufs, TS contract packages).
     - Publicly exposed framework libraries (e.g., `@dcmaar/*`, agent plugin SDKs).
   - Apps should not take dependencies on internal/private framework internals that are not part of the published APIs.

These rules are primarily about **directionality**: frameworks are reusable and app-agnostic; apps are thin layers on top.

## 3. Automated Check

A lightweight script enforces the strictest rule (no `framework/*` → `apps/*` imports):

- Script: `products/dcmaar/ci/scripts/check_architecture_guardrails.sh`
- Behavior:
  - Scans `products/dcmaar/framework` for path references into `products/dcmaar/apps`.
  - Fails if it finds patterns such as `apps/guardian`, `apps/device-health`, or `../apps/`.

Run it locally from the repo root:

```bash
bash products/dcmaar/ci/scripts/check_architecture_guardrails.sh
```

CI can invoke this script as part of the DCMAAR build or pre-merge checks.

## 4. Future Extensions

Over time, the guardrails can be extended to:

- Enforce a whitelist of allowed framework imports for each app.
- Validate that services and plugins use the correct protobuf/TS contracts.
- Integrate with language-specific tooling (e.g., Rust, TypeScript analyzers) for deeper enforcement.

The current guardrails are intentionally minimal but are enough to prevent the most problematic coupling (frameworks importing apps).
