# DCMAAR – Post-Alignment Backlog

This backlog captures follow-up work after the **DCMAAR Framework + Guardian + Device Health** architectural alignment.

It is grouped by area and assumes that:

- Frameworks are product-neutral and own `dcmaar.v1` contracts.
- Guardian and Device Health are thin apps on top, with their own contracts.
- The Go server is deprecated in favor of Java + Node.js.

---

## 1. Go Server Decommissioning (Code-Level)

- [ ] Remove legacy Go server implementation once Java + Node.js server covers required functionality.
  - Delete `products/dcmaar/framework/server` Go code and related Dockerfiles.
  - Remove Go server targets from `products/dcmaar/Makefile` (build, run, test, docker targets).
  - Remove Go server references from any remaining docs and samples.
- [ ] Ensure any remaining consumers are migrated to the Java + Node.js stack or marked obsolete.

---

## 2. Guardian Backend – Proto & Service Adoption

- [ ] Wire `apps/guardian/proto/guardian.proto` into backend builds.
  - Add Buf generation step (Go/Java/TS) for Guardian contracts as needed.
  - Ensure generated code is committed or generated in CI consistently.
- [ ] Refactor Guardian backend services to use generated `guardian.*` types.
  - Replace ad-hoc DTOs for `UsageEvent`, `PolicyAction`, `DevicePolicy`, etc.
  - Align gRPC/HTTP APIs with the new proto contracts.
- [ ] Align Guardian database migrations with the new contracts where appropriate.
  - Ensure schema names and fields match canonical message definitions.

---

## 3. Device Health – Implementation Over Contracts

- [ ] Implement or refine Device Health extension and plugins to follow `DEVICE_HEALTH_CONTRACTS.md`.
  - Ensure `DeviceHealthMetric`, `HealthConfig`, and `HealthAlert` types are used consistently.
  - Map these types into `dcmaar.v1` metrics/events via connectors.
- [ ] Build out Device Health dashboards using the desktop config model.
  - Create concrete dashboard configurations for key health views (CPU, memory, disk, network).
  - Validate layouts and queries in desktop and web.

---

## 4. Desktop Configuration & Plugins (Implementation)

- [ ] Implement the dashboard configuration loader in the desktop app.
  - Support routes, panels, and bindings to DCMAAR query APIs.
- [ ] Define a minimal plugin/registry mechanism for product-specific React modules.
  - Ensure no direct imports from `apps/*` into desktop core.
- [ ] Provide a small set of example configs for internal teams (including Device Health).

---

## 5. Dependency Guardrails – CI Integration

- [ ] Integrate `products/dcmaar/ci/scripts/check_architecture_guardrails.sh` into CI.
  - Run on DCMAAR-related PRs or as part of the main pipeline.
  - Fail builds on framework → apps dependency violations.
- [ ] Consider extending guardrails beyond simple path checks.
  - Language-aware checks for Rust and TypeScript imports.
  - Optional allowlists for framework libraries that apps may import.

---

## 6. Tests & Build Pipelines (Verification)

- [ ] Run full builds and tests for DCMAAR frameworks.
  - Rust agent/desktop crates under `products/dcmaar/framework/agent-daemon` and `framework/desktop`.
  - Server-side Java/Node services.
- [ ] Run full builds and tests for Guardian and Device Health apps.
  - Backend services (Guardian) and extension/apps (Device Health).
- [ ] Add or refine automated test suites around:
  - Agent–server–dashboard telemetry flows.
  - Guardian policy evaluation and enforcement flows.
  - Device Health metric collection and visualization.

---

## 7. Documentation & Examples

- [ ] Keep `GUARDIAN_ARCHITECTURE_AND_CONTRACTS.md` and `DEVICE_HEALTH_CONTRACTS.md` in sync with real code.
- [ ] Add end-to-end example flows:
  - Guardian: device → DCMAAR → Guardian backend → dashboard.
  - Device Health: agent/extension → DCMAAR → desktop dashboards.
- [ ] Periodically review central docs (`DCMAAR_*` docs) to ensure the three-product model remains accurate.

---

## 8. Future Enhancements

- [ ] Explore additional products built on DCMAAR frameworks using the same plugin/contract patterns.
- [ ] Expand AIOps and auto-remediation capabilities and expose them in Guardian/Device Health where relevant.
- [ ] Consider formal ADRs for any major changes to contracts, guardrails, or framework boundaries.
