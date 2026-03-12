EPIC-ID: EPIC-K-14
EPIC NAME: Secrets Management & Key Vault
LAYER: KERNEL
MODULE: K-14 Secrets Management
VERSION: 1.0.1

---

#### Section 1 — Objective

Deliver the K-14 Secrets Management & Key Vault module, providing a unified, secure abstraction layer for all platform secrets (database credentials, API keys, certificates, encryption keys). This epic addresses the critical P0 gap identified in the platform review by centralizing secrets lifecycle management (creation, rotation, access control, audit, emergency access) and integrating with external vault providers (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault, GCP Secret Manager). It ensures zero secrets in configuration files or environment variables and provides customer-managed key (CMK) support for hybrid deployments.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Unified Secrets API for all platform modules.
  2. Integration with external vault providers (HashiCorp, AWS, Azure, GCP).
  3. Automatic secret rotation with zero-downtime.
  4. Certificate lifecycle management (issuance, renewal, revocation).
  5. Emergency break-glass procedures with audit trail.
  6. Customer-managed keys (CMK) support for tenant isolation.
  7. Secret versioning and rollback.
- **Out-of-Scope:**
  1. The actual vault infrastructure provisioning (handled by deployment automation).
  2. Application-level encryption logic (handled by K-08 Data Governance).
- **Dependencies:** EPIC-K-01 (IAM), EPIC-K-02 (Config Engine), EPIC-K-07 (Audit Framework), EPIC-K-08 (Data Governance)
- **Kernel Readiness Gates:** N/A (Kernel Epic)
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Unified Secrets API:** All platform modules must retrieve secrets exclusively via the K-14 SDK. Direct access to vault providers or environment variables is prohibited.
2. **FR2 Multi-Provider Support:** The module must support pluggable vault backends (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault, GCP Secret Manager) with runtime provider selection via K-02 Config Engine.
3. **FR3 Automatic Rotation:** The module must support automatic secret rotation on a configurable schedule (e.g., every 90 days) with zero-downtime handoff to consuming services.
4. **FR4 Certificate Management:** The module must manage TLS certificate lifecycle (CSR generation, issuance via ACME/internal CA, renewal 30 days before expiry, revocation).
5. **FR5 Secret Versioning:** The module must maintain version history for all secrets, allowing rollback to previous versions within a retention window.
6. **FR6 Access Control:** The module must enforce RBAC via K-01 IAM, ensuring only authorized services/users can access specific secrets.
7. **FR7 Break-Glass Access:** The module must provide emergency access procedures for critical secrets when the primary vault is unavailable, with mandatory audit logging.
8. **FR8 Customer-Managed Keys (CMK):** For dedicated/on-prem deployments, the module must support tenant-provided encryption keys stored in their own HSM/KMS.
9. **FR9 Audit Trail:** Every secret access, rotation, and modification must be logged to K-07 Audit Framework with actor identity and timestamp.
10. **FR10 Dual-Calendar Stamping:** Secret expiry dates and rotation schedules must support dual-calendar (Gregorian and BS) for operational planning.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The secrets management framework and provider abstraction are jurisdiction-agnostic.
2. **Jurisdiction Plugin:** Specific compliance requirements (e.g., key residency, rotation frequency) are defined in Jurisdiction Config Packs (T1).
3. **Resolution Flow:** Config Engine determines which vault provider and rotation policies apply per tenant/jurisdiction.
4. **Hot Reload:** Rotation policies can be updated dynamically via Config Engine.
5. **Backward Compatibility:** Existing secret versions remain accessible during rotation.
6. **Future Jurisdiction:** A new country's key management requirements are handled via T1 Config Packs.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `SecretMetadata`: `{ secret_id: String, path: String, version: Int, created_at: DualDate, expires_at: DualDate, rotation_policy: String, owner_service: String }`
  - `SecretAccessLog`: `{ access_id: UUID, secret_id: String, accessor: String, timestamp: DualDate, operation: Enum }`
  - `CertificateMetadata`: `{ cert_id: String, subject_dn: String, issuer: String, valid_from: DualDate, valid_until: DualDate, status: Enum }`
- **Dual-Calendar Fields:** `created_at`, `expires_at`, `valid_from`, `valid_until` use `DualDate`.
- **Event Schema Changes:** `SecretRotated`, `SecretAccessed`, `CertificateRenewed`, `BreakGlassActivated`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                       |
| ----------------- | ----------------------------------------------------------------------------------------------------------------- |
| Event Name        | `SecretRotated`                                                                                                   |
| Schema Version    | `v1.0.0`                                                                                                          |
| Trigger Condition | A secret is automatically rotated or manually updated.                                                            |
| Payload           | `{ "secret_id": "...", "old_version": 5, "new_version": 6, "rotation_type": "AUTOMATIC", "timestamp_bs": "..." }` |
| Consumers         | All services consuming the secret (via SDK notification), Audit Framework, Observability                          |
| Idempotency Key   | `hash(secret_id + new_version)`                                                                                   |
| Replay Behavior   | Updates the materialized view of active secret versions.                                                          |
| Retention Policy  | Permanent.                                                                                                        |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `RotateSecretCommand`                                                |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Secret exists, new value generated, requester authorized             |
| Handler          | `SecretCommandHandler` in K-14 Secrets Management                    |
| Success Event    | `SecretRotated`                                                      |
| Failure Event    | `SecretRotationFailed`                                               |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `RevokeSecretCommand`                                                |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Secret exists, no active consumers, requester authorized             |
| Handler          | `SecretCommandHandler` in K-14 Secrets Management                    |
| Success Event    | `SecretRevoked`                                                      |
| Failure Event    | `SecretRevocationFailed`                                             |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `RenewCertificateCommand`                                            |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Certificate exists, renewal window valid, CA accessible              |
| Handler          | `CertificateCommandHandler` in K-14 Secrets Management               |
| Success Event    | `CertificateRenewed`                                                 |
| Failure Event    | `CertificateRenewalFailed`                                           |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Anomaly Detection
- **Workflow Steps Exposed:** Secret access pattern monitoring.
- **Model Registry Usage:** `secret-access-anomaly-v1`
- **Explainability Requirement:** AI detects unusual secret access patterns (e.g., service accessing secrets outside normal hours, excessive access frequency) and generates alerts.
- **Human Override Path:** Security operator can acknowledge the alert or trigger break-glass investigation.
- **Drift Monitoring:** False positive rate tracked.
- **Fallback Behavior:** Standard access control via K-01 RBAC.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                      |
| ------------------------- | ------------------------------------------------------------------------------------- |
| Latency / Throughput      | Secret retrieval P99 < 10ms (cached), < 50ms (vault fetch); 10,000 TPS                |
| Scalability               | Horizontally scalable with local caching per service instance                         |
| Availability              | 99.999% uptime (critical path for all services)                                       |
| Consistency Model         | Strong consistency for secret writes; eventual consistency for rotation notifications |
| Security                  | Secrets never logged in plaintext; encrypted in transit (mTLS) and at rest            |
| Data Residency            | Vault location determined by jurisdiction config                                      |
| Data Retention            | Secret metadata retained permanently; secret values purged per retention policy       |
| Auditability              | Every access logged [LCA-AUDIT-001]                                                   |
| Observability             | Metrics: `secret.access.latency`, `secret.rotation.count`, `cert.expiry.days`         |
| Extensibility             | New vault providers via adapter pattern < 1 sprint                                    |
| Upgrade / Compatibility   | SDK backward compatible for 2 major versions                                          |
| On-Prem Constraints       | Support local HashiCorp Vault or file-based sealed storage                            |
| Ledger Integrity          | N/A                                                                                   |
| Dual-Calendar Correctness | Expiry calculations accurate                                                          |

---

#### Section 10 — Acceptance Criteria

1. **Given** a service requires a database password, **When** it calls `SecretsClient.getSecret("db/postgres/password")`, **Then** the SDK retrieves it from the configured vault in < 50ms and caches it locally.
2. **Given** a secret with a 90-day rotation policy, **When** 90 days elapse, **Then** K-14 automatically generates a new version, updates the vault, and emits `SecretRotated` event.
3. **Given** a service receives a `SecretRotated` event, **When** processed, **Then** it fetches the new version within 5 seconds and updates its connection pool without downtime.
4. **Given** a TLS certificate expiring in 25 days, **When** the daily renewal job runs, **Then** K-14 requests a new certificate via ACME, installs it, and emits `CertificateRenewed`.
5. **Given** the primary vault is unreachable, **When** an operator activates break-glass, **Then** K-14 uses the sealed emergency backup, logs the access to K-07, and alerts security operations.
6. **Given** a tenant with customer-managed keys, **When** they revoke access to their HSM, **Then** all subsequent secret access attempts for that tenant fail immediately with clear error messages.
7. **Given** an unauthorized service attempts to access a secret, **When** evaluated by K-01 RBAC, **Then** access is denied and logged as a security event.

---

#### Section 11 — Failure Modes & Resilience

- **Vault Unreachable:** SDK serves from local cache (with staleness warning); alerts operations. If cache expires, fail closed (deny access) to prevent unencrypted fallback.
- **Rotation Failure:** Retry with exponential backoff up to 3 times; if all fail, alert operations and keep old version active.
- **Certificate Renewal Failure:** Retry daily; if certificate expires, alert critical and trigger manual intervention.
- **Break-Glass Abuse:** All break-glass access logged immutably; security review triggered automatically.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                                                                               |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Metrics             | `secret.cache.hit_rate`, `secret.rotation.success_rate`, `cert.days_until_expiry`, dimensions: `secret_path`, `vault_provider` |
| Logs                | Structured: `trace_id`, `secret_id`, `operation`, `result`, `latency_ms`                                                       |
| Traces              | Span `SecretsClient.getSecret`                                                                                                 |
| Audit Events        | Action: `AccessSecret`, `RotateSecret`, `BreakGlassActivated` [LCA-AUDIT-001]                                                  |
| Regulatory Evidence | Access logs for compliance audits; proof of rotation frequency                                                                 |

---

#### Section 13 — Compliance & Regulatory Traceability

- Access control and segregation of duties [LCA-SOD-001]
- Audit trails for secret access [LCA-AUDIT-001]
- Key management compliance (PCI-DSS, SOC 2) [ASR-SEC-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `SecretsClient.getSecret(path)`, `SecretsClient.watchSecret(path, callback)`, `SecretsClient.rotateSecret(path)`.
- **Vault Provider Interface:** `VaultProvider` with methods `read(path)`, `write(path, value)`, `delete(path)`, `list(prefix)`.
- **Jurisdiction Plugin Extension Points:** Rotation policies and key residency rules via T1 Config Packs.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                              | Expected Answer                                                                                                |
| --------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?                  | Yes, via vault provider selection and rotation policies.                                                       |
| Can a new vault provider be added?                                    | Yes, via VaultProvider interface implementation.                                                               |
| Can rotation policies change without redeploy?                        | Yes, via K-02 Config Engine hot reload.                                                                        |
| Can this run in an air-gapped deployment?                             | Yes, using local HashiCorp Vault or sealed file storage.                                                       |
| Can this module handle digital assets (tokenized securities, crypto)? | Yes. Private key management for blockchain wallets and HSM-backed signing keys use the same vault abstraction. |
| Is the design ready for CBDC integration or T+0 settlement?           | Yes. Instant key rotation and real-time secret injection support T+0 settlement signing requirements.          |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Secret Exfiltration via Compromised Service**
   - **Threat:** Attacker compromises a service to extract secrets from memory or logs.
   - **Mitigation:** Secrets never logged in plaintext; short-lived secret caching; memory encryption where possible; service authentication via K-01 with least privilege; audit all secret access; anomaly detection on access patterns.
   - **Residual Risk:** Memory dump from compromised host.

2. **Vault Compromise / Unauthorized Access**
   - **Threat:** Attacker gains direct access to the underlying vault (HashiCorp, AWS Secrets Manager).
   - **Mitigation:** Vault access restricted to K-14 service only; mTLS for vault communication; vault audit logging; regular vault security assessments; vault sealed at rest; multi-factor authentication for vault admin access.
   - **Residual Risk:** Vault provider infrastructure breach.

3. **Secret Rotation Failure Leading to Outage**
   - **Threat:** Failed secret rotation causes service outages or leaves old credentials active.
   - **Mitigation:** Dual-credential overlap period during rotation; automated rollback on rotation failure; health checks before finalizing rotation; notification to consuming services; retry logic with exponential backoff.
   - **Residual Risk:** Cascading failures across multiple services.

4. **Break-Glass Abuse**
   - **Threat:** Operator abuses emergency break-glass access to extract secrets.
   - **Mitigation:** All break-glass access logged immutably to K-07; automatic security review triggered; multi-person approval for break-glass activation; time-limited break-glass sessions; post-incident audit required.
   - **Residual Risk:** Insider threat with emergency access.

5. **Man-in-the-Middle on Secret Retrieval**
   - **Threat:** Attacker intercepts secrets in transit between service and K-14.
   - **Mitigation:** mTLS for all secret retrieval; certificate pinning; no plaintext transmission; encrypted channels only; network segmentation; regular certificate rotation.
   - **Residual Risk:** Compromised network infrastructure.

6. **Customer-Managed Key (CMK) Revocation**
   - **Threat:** Customer revokes CMK access, causing data unavailability or loss.
   - **Mitigation:** CMK revocation detection with immediate alerts; graceful degradation mode; backup encryption with platform-managed keys; customer notification before revocation impact; documented recovery procedures.
   - **Residual Risk:** Intentional or accidental CMK deletion.

7. **Secret Sprawl / Untracked Secrets**
   - **Threat:** Secrets created outside K-14 system, bypassing controls.
   - **Mitigation:** Enforce all secrets via K-14 SDK; automated scanning for hardcoded secrets in code; CI/CD pipeline checks; regular secret inventory audits; no environment variable secrets allowed.
   - **Residual Risk:** Legacy systems with hardcoded credentials.

8. **Insufficient Secret Rotation**
   - **Threat:** Secrets remain active too long, increasing compromise window.
   - **Mitigation:** Automated rotation policies (90-day default); alerts for secrets nearing expiry; forced rotation for high-risk secrets; rotation tracking and compliance reporting; no manual rotation allowed.
   - **Residual Risk:** Rotation policy exceptions for legacy systems.

**Security Controls:**

- Secrets never logged or stored in plaintext
- mTLS for all vault communication
- Service authentication via K-01 IAM
- Least privilege access (RBAC)
- Comprehensive audit logging to K-07
- Anomaly detection on access patterns (K-09)
- Automatic secret rotation with overlap periods
- Break-glass procedures with immutable audit trail
- Customer-managed key (CMK) support
- Secret versioning and rollback capability
- Encrypted at rest and in transit

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
- Regular security assessments
- No hardcoded secrets (enforced via CI/CD)
- Certificate lifecycle management
