EPIC-ID: EPIC-K-04
EPIC NAME: Plugin Runtime & SDK
LAYER: KERNEL
MODULE: K-04 Plugin Runtime & SDK
VERSION: 1.1.1

---

#### Section 1 — Objective

Deliver the K-04 Plugin Runtime, responsible for the registration, lifecycle management, and secure execution of all platform extension packs (T1, T2, and T3). This epic directly implements Principle 20 (Plugin Taxonomy Enforcement) and ensures strict tier-aware sandbox isolation. It prevents unsigned or incompatible plugins from loading and enforces the principle that all jurisdiction-specific, operator-specific, and exchange-specific code runs safely outside the Generic Core.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Plugin registration and capability declaration via `plugin_manifest.yaml`.
  2. Lifecycle management: install, upgrade, rollback, disable.
  3. Tier-aware sandbox isolation (T1: validation, T2: K-03 Rules Engine, T3: isolated process/container).
  4. Cryptographic signature verification for all plugins.
  5. Compatibility matrix enforcement (platform vs. plugin version).
- **Out-of-Scope:**
  1. Policy evaluation (handled by K-03).
  2. The actual content of the plugins.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-03 (Rules Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-14 (Secrets Management — for plugin signature key storage), EPIC-PU-004 (Platform Manifest)
- **Kernel Readiness Gates:** N/A
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Registration & Verification:** The runtime must verify the cryptographic signature of any plugin bundle before loading. Unsigned plugins must be permanently rejected.
2. **FR2 Capability Declaration:** The runtime must parse the plugin manifest to register its exposed capabilities and hook into the appropriate core extension points.
3. **FR3 Tier Isolation (T3):** Executable packs (T3) must be launched in an isolated process/container with strict resource limits (CPU/Memory).
4. **FR4 Compatibility Enforcement:** The runtime must reject plugins if the platform version falls outside the `platform_min_version` and `platform_max_version` declared in the manifest.
5. **FR5 Lifecycle Events:** Emit events (`PluginInstalled`, `PluginUpgraded`, `PluginDisabled`) on all state changes.
6. **FR6 Graceful Degradation:** If a T3 plugin crashes, the runtime must capture the crash, restart the plugin (up to a limit), and notify observability.
7. **FR7 Hot Swap:** T1 and T2 plugins must support zero-downtime hot swapping.
8. **FR8 Dual-Calendar Support:** Plugin validity periods must handle `DualDate` formats.
9. **FR9 Resource Quotas & Enforcement:** The runtime must enforce configurable resource quotas per plugin instance: (a) CPU: max percentage per plugin (default T3: 10% of host), (b) Memory: max allocation (default T3: 256MB), (c) Network: bandwidth cap and egress allowlist (default T3: no external network), (d) Disk I/O: max IOPS. Quotas are enforced via cgroups/namespaces for T3 plugins. Plugin instances exceeding quotas are terminated with `PluginQuotaExceededEvent` emitted. Quota definitions are configurable via K-02 Config Engine per plugin tier. [ARB P1-10]
10. **FR10 Exfiltration Prevention:** T3 plugins must be prevented from: (a) accessing host filesystem outside designated sandbox directory, (b) making outbound network calls unless explicitly allowlisted in the capability manifest, (c) accessing other plugin sandboxes, (d) reading environment variables of the host process. Violations are logged as security events and trigger immediate plugin termination.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The runtime itself contains zero jurisdiction logic.
2. **Jurisdiction Plugin:** All jurisdiction-specific code runs _inside_ the sandbox managed by this runtime.
3. **Resolution Flow:** Config Engine dictates which version of a plugin is active for a given tenant/jurisdiction; the runtime enforces that state.
4. **Hot Reload:** Supported for T1/T2.
5. **Backward Compatibility:** Deprecated APIs are supported for a minimum of 2 major platform versions.
6. **Future Jurisdiction:** No core changes needed to load a new jurisdiction's plugin.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `PluginRegistration`: `{ plugin_id: String, version: String, tier: Int, manifest: JSON, signature_hash: String, status: Enum }`
- **Dual-Calendar Fields:** `valid_from` and `valid_until` in registration metadata.
- **Event Schema Changes:** `PluginStateChangedEvent`

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                |
| ----------------- | ------------------------------------------------------------------------------------------ |
| Event Name        | `PluginStateChangedEvent`                                                                  |
| Schema Version    | `v1.0.0`                                                                                   |
| Trigger Condition | A plugin is installed, upgraded, or disabled.                                              |
| Payload           | `{ "plugin_id": "...", "version": "...", "previous_version": "...", "action": "UPGRADE" }` |
| Consumers         | API Gateway (routing updates), Admin Portal                                                |
| Idempotency Key   | `hash(plugin_id + version + action)`                                                       |
| Replay Behavior   | Updates the materialized view of active plugins.                                           |
| Retention Policy  | Permanent.                                                                                 |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                                     |
| ---------------- | ------------------------------------------------------------------------------- |
| Command Name     | `RegisterPluginCommand`                                                         |
| Schema Version   | `v1.0.0`                                                                        |
| Validation Rules | Plugin signed, signature valid, compatibility matrix satisfied, benchmarks pass |
| Handler          | `PluginCommandHandler` in K-04 Plugin Runtime                                   |
| Success Event    | `PluginRegistered`                                                              |
| Failure Event    | `PluginRegistrationFailed`                                                      |
| Idempotency      | Command ID must be unique; duplicate commands return original result            |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `UnregisterPluginCommand`                                            |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Plugin exists, no active dependencies, requester authorized          |
| Handler          | `PluginCommandHandler` in K-04 Plugin Runtime                        |
| Success Event    | `PluginUnregistered`                                                 |
| Failure Event    | `PluginUnregistrationFailed`                                         |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                                        |
| ---------------- | ---------------------------------------------------------------------------------- |
| Command Name     | `InvokePluginCommand`                                                              |
| Schema Version   | `v1.0.0`                                                                           |
| Validation Rules | Plugin registered and active, input parameters valid, resource quotas not exceeded |
| Handler          | `PluginInvocationHandler` in K-04 Plugin Runtime                                   |
| Success Event    | `PluginInvoked`                                                                    |
| Failure Event    | `PluginInvocationFailed`                                                           |
| Idempotency      | Command ID must be unique; duplicate commands return cached result                 |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Anomaly Detection
- **Workflow Steps Exposed:** T3 plugin execution monitoring.
- **Model Registry Usage:** `plugin-behavior-monitor-v1`
- **Explainability Requirement:** If AI detects abnormal resource consumption or unauthorized API call attempts from a sandbox, it flags the plugin.
- **Human Override Path:** Operator can suppress the alert or force-disable the plugin.
- **Drift Monitoring:** N/A
- **Fallback Behavior:** Standard strict resource limits (cgroups/namespaces).

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                              |
| ------------------------- | --------------------------------------------------------------------------------------------- |
| Latency / Throughput      | Sandbox IPC overhead < 2ms                                                                    |
| Scalability               | N/A (runs alongside platform nodes)                                                           |
| Availability              | 99.999% uptime for the runtime host                                                           |
| Consistency Model         | Strong consistency on plugin state                                                            |
| Security                  | T3 execution fully isolated; no host filesystem access; cgroup-based resource limits enforced |
| Data Residency            | N/A (code binaries)                                                                           |
| Data Retention            | Retain manifest history indefinitely                                                          |
| Auditability              | All plugin state changes logged [LCA-AUDIT-001]                                               |
| Observability             | Metrics: `plugin.crash.count`, `plugin.ipc.latency`                                           |
| Extensibility             | N/A                                                                                           |
| Upgrade / Compatibility   | Strict enforcement of `platform_min_version`                                                  |
| On-Prem Constraints       | Able to load plugins from offline signed bundle                                               |
| Ledger Integrity          | N/A                                                                                           |
| Dual-Calendar Correctness | Correct processing of plugin validity dates                                                   |

---

#### Section 10 — Acceptance Criteria

1. **Given** an unsigned T3 plugin bundle, **When** deployment is attempted, **Then** the runtime rejects it synchronously and logs a security event.
2. **Given** a valid plugin requiring platform version 3.0, **When** deployed on platform version 2.5, **Then** it is rejected due to compatibility matrix violation.
3. **Given** a T3 Executable Pack that exceeds its memory limit, **When** executed, **Then** the sandbox OOM-kills the process, logs the crash, and restarts it (up to the retry limit).
4. **Given** an upgrade to a T2 Rule Pack, **When** deployed, **Then** the runtime hot-loads it into K-03 without dropping in-flight requests.

---

#### Section 11 — Failure Modes & Resilience

- **T3 Sandbox Crash:** Auto-restart with exponential backoff.
- **Signature Verification Failure:** Permanent rejection.
- **API Gateway Sync Failure:** Retry sync; plugin remains pending until routing is confirmed.
- **Resource Quota Breach:** Plugin terminated immediately; `PluginQuotaExceededEvent` emitted; plugin disabled after 3 quota breaches within 1 hour. [ARB P1-10]
- **Exfiltration Attempt:** Plugin terminated; security event logged to K-07; plugin permanently disabled pending manual review.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                |
| ------------------- | --------------------------------------------------------------- |
| Metrics             | `plugin.load.time`, `sandbox.memory.usage`, `sandbox.cpu.usage` |
| Logs                | Structured: `plugin_id`, `action`, `status`                     |
| Traces              | Span `PluginRuntime.invokeHook`                                 |
| Audit Events        | Action: `InstallPlugin`, `DisablePlugin`                        |
| Regulatory Evidence | Verified cryptographic hash of running code for audits.         |

---

#### Section 13 — Compliance & Regulatory Traceability

- System integrity and change control [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:**
  - `PluginHost.register(RegisterPluginCommand)` → `PluginRegistered`
  - `PluginHost.unregister(UnregisterPluginCommand)` → `PluginUnregistered`
  - `PluginHost.invoke(pluginId, hookName, payload)` → `PluginInvocationResult`
  - `PluginHost.list(filter?)` → `PluginRegistration[]`
  - `PluginHost.getHealth(pluginId)` → `PluginHealthStatus`
  - `PluginHost.getDependencyGraph()` → `DependencyGraph` (for dependency cycle detection)
- **REST API:** Exposed via K-11 API Gateway at `/api/v1/plugins/*`
- **Jurisdiction Plugin Extension Points:** N/A (This module hosts plugins)
- **Events Emitted:** `PluginRegistered`, `PluginUnregistered`, `PluginStateChangedEvent`, `PluginInvoked`, `PluginQuotaExceededEvent`, `PluginCrashEvent`, `PluginSecurityViolationEvent` — all conform to K-05 standard envelope
- **Events Consumed:** `ConfigUpdated` (from K-02), `SecretRotated` (from K-14), `PlatformManifestUpdated` (from PU-004)
- **Webhook Extension Points:** `POST /webhooks/plugin-events` for external CI/CD integration (e.g., notify on plugin deploy/rollback)

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                              | Expected Answer                                                                                          |
| ----------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?  | Yes — natively designed to host jurisdiction packs; new market = new T1/T2/T3 packs, zero core changes   |
| Can a new exchange be connected?                      | Yes — new T3 Adapter Pack with FIX/proprietary protocol support                                          |
| Can this run in an air-gapped deployment?             | Yes — offline signed bundles verified against embedded public keys from K-14                             |
| Can WebAssembly (WASM) be used for plugin sandboxing? | Yes — T3 isolation model is abstracted; WASM runtime can replace cgroups in future without API changes   |
| Can a plugin marketplace be added?                    | Yes — P-01 Pack Certification provides certification; runtime supports versioned package registry        |
| Can plugin dependencies be managed across packs?      | Yes — `getDependencyGraph()` provides cycle detection; compatibility matrix enforces version constraints |

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
