EPIC-ID: EPIC-PU-004
EPIC NAME: Platform Manifest
LAYER: PLATFORM-UNITY
MODULE: PU-004 Platform Manifest
VERSION: 1.0.1

---

#### Section 1 — Objective

Deliver the Platform Manifest (PU-004), providing a single, immutable source of truth for the entire platform's installed state. It records which domain subsystems, extension packs (T1/T2/T3), and config packs are currently active, along with their exact versions. This satisfies Principle 12 (Single Pane of Glass) and is critical for ensuring safe upgrades, maintaining the compatibility matrix, and proving regulatory compliance regarding system state at any point in time.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Immutable append-only platform manifest store.
  2. Compatibility matrix validation during deployments/upgrades.
  3. Diff/compare tooling for evaluating changes before activation.
  4. Integration with K-10 Deployment Abstraction and K-04 Plugin Runtime.
- **Out-of-Scope:**
  1. The actual downloading/execution of the plugins (handled by K-04).
- **Dependencies:** EPIC-K-04 (Plugin Runtime), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-10 (Deployment Abstraction)
- **Kernel Readiness Gates:** N/A
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Source of Truth:** The manifest must immutably record the desired and actual state of all platform components (Core modules, Domain modules, Config Packs, Plugins).
2. **FR2 Compatibility Validation:** Before any state change is applied, the manifest engine must validate the new state against the registered compatibility matrix (e.g., ensuring Plugin X v2.0 supports Platform v3.1).
3. **FR3 Append-Only History:** Every change to the platform state creates a new manifest version. Previous versions are retained indefinitely.
4. **FR4 Diff Tooling:** Expose an API to compare the current manifest with a proposed manifest to highlight additions, removals, and version bumps.
5. **FR5 Rollback Orchestration:** Provide a mechanism to revert the active manifest to a previous version, triggering K-04 and K-02 to align with the reverted state.
6. **FR6 Dual-Calendar Tracking:** Manifest activation times must be recorded using `DualDate`.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The manifest framework is jurisdiction-agnostic.
2. **Jurisdiction Plugin:** The manifest records which Jurisdiction Plugins are active, but does not contain their logic.
3. **Resolution Flow:** N/A
4. **Hot Reload:** Changes to the manifest trigger hot reloads in downstream components (e.g., K-02, K-04).
5. **Backward Compatibility:** Guarantees platform state can be reconstructed for historical audits.
6. **Future Jurisdiction:** A new jurisdiction's rollout is simply a manifest update adding new packs.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `PlatformManifest`: `{ manifest_version: Int, active_components: List<ComponentRef>, activated_at: DualDate, signature: String }`
  - `ComponentRef`: `{ component_id: String, type: Enum, version: String, hash: String }`
- **Dual-Calendar Fields:** `activated_at` uses `DualDate`.
- **Event Schema Changes:** `ManifestUpdatedEvent`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                      |
| ----------------- | ------------------------------------------------------------------------------------------------ |
| Event Name        | `ManifestUpdatedEvent`                                                                           |
| Schema Version    | `v1.0.0`                                                                                         |
| Trigger Condition | A new platform manifest version is approved and activated.                                       |
| Payload           | `{ "new_version": 42, "previous_version": 41, "activated_at_bs": "...", "diff_summary": {...} }` |
| Consumers         | Plugin Runtime, Config Engine, Audit Framework                                                   |
| Idempotency Key   | `hash(new_version)`                                                                              |
| Replay Behavior   | Ignored.                                                                                         |
| Retention Policy  | Permanent.                                                                                       |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                            |
| ---------------- | ---------------------------------------------------------------------- |
| Command Name     | `UpdateManifestCommand`                                                |
| Schema Version   | `v1.0.0`                                                               |
| Validation Rules | Manifest version incremented, compatibility validated, signature valid |
| Handler          | `ManifestCommandHandler` in PU-004 Platform Manifest                   |
| Success Event    | `ManifestUpdated`                                                      |
| Failure Event    | `ManifestUpdateFailed`                                                 |
| Idempotency      | Command ID must be unique; duplicate commands return original result   |

| Field            | Description                                          |
| ---------------- | ---------------------------------------------------- |
| Command Name     | `ValidateCompatibilityCommand`                       |
| Schema Version   | `v1.0.0`                                             |
| Validation Rules | Proposed manifest provided, current manifest exists  |
| Handler          | `CompatibilityValidator` in PU-004 Platform Manifest |
| Success Event    | `CompatibilityValidated`                             |
| Failure Event    | `CompatibilityValidationFailed`                      |
| Idempotency      | Same input returns cached validation result          |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `RollbackManifestCommand`                                            |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Target version exists, requester authorized, rollback safe           |
| Handler          | `ManifestCommandHandler` in PU-004 Platform Manifest                 |
| Success Event    | `ManifestRolledBack`                                                 |
| Failure Event    | `ManifestRollbackFailed`                                             |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Copilot Assist
- **Workflow Steps Exposed:** Manifest diff review before deployment.
- **Model Registry Usage:** `deployment-risk-analyzer-v1`
- **Explainability Requirement:** AI analyzes the diff and historical incident data to flag high-risk upgrades (e.g., "This combination of OMS v2 and Risk v1.5 previously caused latency spikes").
- **Human Override Path:** Operator can acknowledge and proceed.
- **Drift Monitoring:** N/A
- **Fallback Behavior:** Standard static compatibility matrix check.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                 |
| ------------------------- | ---------------------------------------------------------------- |
| Latency / Throughput      | Validation of new manifest < 100ms                               |
| Scalability               | N/A (low volume control plane operation)                         |
| Availability              | 99.999% uptime                                                   |
| Consistency Model         | Strong consistency                                               |
| Security                  | Manifest updates require Maker-Checker and cryptographic signing |
| Data Residency            | Global control plane data                                        |
| Data Retention            | Permanent                                                        |
| Auditability              | Every manifest change is an audited event [LCA-AUDIT-001]        |
| Observability             | Metrics: `manifest.version`, `manifest.update.latency`           |
| Extensibility             | N/A                                                              |
| Upgrade / Compatibility   | Core enabler of platform upgrades                                |
| On-Prem Constraints       | Manifest updates bundled in offline sync files                   |
| Ledger Integrity          | N/A                                                              |
| Dual-Calendar Correctness | Correct `DualDate` activation logging                            |

---

#### Section 10 — Acceptance Criteria

1. **Given** a proposed manifest updating the SEBON Rule Pack from v1 to v2, **When** the diff API is called, **Then** it accurately highlights the single component change.
2. **Given** a proposed manifest including a Plugin version that violates the compatibility matrix, **When** submitted for activation, **Then** it is synchronously rejected with a compatibility error.
3. **Given** a critical issue in production post-upgrade, **When** an operator initiates a rollback to the previous manifest version, **Then** the system reverts all configurable state to match the old manifest without restarting core services.

---

#### Section 11 — Failure Modes & Resilience

- **Validation Failure:** Manifest update aborted safely.
- **Downstream Sync Failure:** If Plugin Runtime fails to apply the new manifest, the manifest engine initiates an automatic rollback and alerts operators.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                                               |
| ------------------- | ---------------------------------------------------------------------------------------------- |
| Metrics             | `manifest.updates.count`, `manifest.rollback.count`                                            |
| Logs                | Structured: `manifest_version`, `status`                                                       |
| Traces              | N/A                                                                                            |
| Audit Events        | `ManifestUpdatedEvent`                                                                         |
| Regulatory Evidence | Proof of exact system state (code versions) at the time of a historical trade [LCA-AUDIT-001]. |

---

#### Section 13 — Compliance & Regulatory Traceability

- Software change control and integrity [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `ManifestClient.getCurrent()`, `ManifestClient.propose(newManifest)`.
- **Jurisdiction Plugin Extension Points:** N/A

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                       |
| ---------------------------------------------------- | ------------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes.                                  |
| Can this run in an air-gapped deployment?            | Yes, manifest tracks offline bundles. |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Manifest Forgery**

- **Threat:** An attacker proposes or activates a forged manifest to deploy unauthorized components.
- **Mitigation:** Cryptographic signing, maker-checker approval, version monotonicity checks, and audit logging of manifest proposals and activations.
- **Residual Risk:** Compromise of signing credentials.

2. **Rollback Abuse**

- **Threat:** A rollback is triggered to an insecure or incompatible historical state.
- **Mitigation:** Safe-rollback validation, authorization controls, compatibility checks against target state, and operator alerts for rollback actions.
- **Residual Risk:** Approved rollback to a historically vulnerable but operationally necessary version.

3. **Compatibility Bypass**

- **Threat:** Components are activated despite violating compatibility rules, causing unsafe runtime behavior.
- **Mitigation:** Mandatory compatibility validation before activation, automated rejection of invalid diffs, and downstream sync verification with rollback on failure.
- **Residual Risk:** Logic defects in the compatibility engine.

4. **Historical State Tampering**

- **Threat:** Past manifests are altered to conceal what was active during an audit window.
- **Mitigation:** Append-only history, signed manifests, immutable audit trails, and reproducible diff tooling for historical comparisons.
- **Residual Risk:** Storage-layer compromise bypassing immutability controls.

5. **Control-Plane Availability Loss**

- **Threat:** Manifest services are unavailable during critical deployment or rollback operations.
- **Mitigation:** Low-volume hardened control plane, offline sync bundles for on-prem, downstream cache coordination, and automatic rollback handling for partial sync failures.
- **Residual Risk:** Coordinated outage across manifest and dependent control-plane services.

**Security Controls:**

- Cryptographic signing and maker-checker for manifest changes
- Mandatory compatibility validation
- Append-only manifest history
- Audit logging for proposals, activations, and rollbacks
- Safe-rollback checks and downstream sync verification
- Offline bundle support for resilient control-plane operations

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added a threat-model section for manifest integrity and rollback risks.
- Added changelog metadata for future epic maintenance.
