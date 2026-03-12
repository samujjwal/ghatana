EPIC-ID: EPIC-K-01
EPIC NAME: Identity & Access Management (IAM)
LAYER: KERNEL
MODULE: K-01 Identity & Access Management
VERSION: 1.1.1

---

#### Section 1 — Objective

Deliver a multi-tenant Identity and Access Management (IAM) capability for the Project Siddhanta platform. This kernel module provides authentication, SSO (SAML/OIDC), session management, and RBAC/ABAC with attribute-aware policy evaluation. Crucially, it satisfies Principle 14 (National ID as Root of Trust) by providing an extension point for Jurisdiction Plugins to define specific National ID schemes without embedding jurisdiction logic into the core. It ensures tenant-isolated identity namespaces and provides a unified AuthZ client SDK for all domain modules (Principle 11).

---

#### Section 2 — Scope

- **In-Scope:**
  1. Multi-tenant authentication, SSO (SAML/OIDC), MFA, and session management.
  2. RBAC + ABAC with attribute-aware policy evaluation.
  3. Service-to-service identity (mTLS, SPIFFE/SPIRE).
  4. Core AuthZ SDK for domain modules.
  5. Extension point for National ID scheme adapters.
- **Out-of-Scope:**
  1. Implementation of specific National ID adapters (e.g., Nepal National ID or Aadhaar) — these belong in Jurisdiction Plugins (T3 Adapter or T2 Config).
  2. Local domain-level auth logic — all domain modules must delegate to this kernel module.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework)
- **Kernel Readiness Gates:** N/A (Kernel Epic)
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Authentication:** Authenticate users via OIDC/SAML, supporting multi-factor authentication (MFA) governed by K-02 Config Engine.
2. **FR2 Multi-Tenant Isolation:** Enforce strict tenant isolation; users authenticate within a specific `tenant_id` namespace.
3. **FR3 Authorization (RBAC/ABAC):** Evaluate permissions based on roles and attributes, returning allow/deny to the domain module via the AuthZ SDK.
4. **FR4 National ID Extension Point:** Store a generic `national_id_ref` (with `scheme` and `issuing_jurisdiction` attributes) and delegate validation/verification to the active Jurisdiction Plugin.
5. **FR5 Service-to-Service Identity:** Issue and validate mTLS certificates/SPIFFE IDs for all internal service communication.
6. **FR6 Audit Logging:** All auth events (login, logout, permission denied, role assigned) must be logged via K-07 Audit Framework.
7. **FR7 Event Emission:** Emit `UserAuthenticated`, `RoleGranted`, `RoleRevoked` events to the K-05 Event Bus.
8. **FR8 Dual-Calendar Handling:** Timestamps on sessions and lockouts must use dual-calendar (via K-15) for reporting.
9. **FR9 Ledger Impact:** None directly.
10. **FR10 Maker-Checker Applicability:** Assigning high-privilege roles (e.g., system admin, compliance officer) requires maker-checker approval.
11. **FR11 Approval Rate Limiting:** Enforce configurable rate limits on maker-checker approval actions (default: max 10 approvals per checker per hour). Alert on rapid approval patterns. Require MFA step-up for critical approvals (role grants, config changes, rule deployments). [ARB P1-09]
12. **FR12 Approval Velocity Anomaly Detection:** Integrate with K-09 AI Governance to detect unusual approval velocity or pattern anomalies (e.g., same checker approving across multiple modules in rapid succession). Flag for security review.
13. **FR13 Beneficial Ownership Graph:** Maintain a directed acyclic graph (DAG) of beneficial ownership relationships for corporate entities. Track Ultimate Beneficial Owners (UBO) with ownership percentages and control relationships. Support queries: (a) `getUBOs(entity_id)` returns all UBOs with ownership % ≥ threshold (default 25%), (b) `getOwnershipChain(entity_id, person_id)` returns full ownership path. Graph updates require maker-checker approval. Emit `BeneficialOwnershipUpdatedEvent` to K-05. Integration point for AML/KYC compliance checks. [GAP-002]

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The identity model, SSO flows, and RBAC/ABAC evaluation engine are purely Generic Core.
2. **Jurisdiction Plugin:** The verification logic for a National ID (e.g., format validation, API integration with government gateways) is externalized to a T3 Executable Pack (Adapter) or T2 Rule Pack.
3. **Resolution Flow:** Config Engine determines which National ID scheme applies based on the tenant/jurisdiction.
4. **Hot Reload:** Changes to MFA requirement rules via Config Pack take effect without restarting the IAM service.
5. **Backward Compatibility:** Existing active sessions remain valid across config reloads unless explicitly revoked.
6. **Future Jurisdiction:** A new country's National ID system can be integrated simply by adding a new National ID Adapter Plugin without modifying the IAM core.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `User`: `{ user_id: UUID, tenant_id: UUID, email: String, roles: List<String>, attributes: Map<String, String>, status: Enum }`
  - `NationalIdRef`: `{ user_id: UUID, scheme: String, issuing_jurisdiction: String, id_hash: String, verification_status: Enum }`
  - `Session`: `{ session_id: UUID, user_id: UUID, created_at: DualDate, expires_at: DualDate }`
  - `BeneficialOwnershipEdge`: `{ edge_id: UUID, owned_entity_id: UUID, owner_entity_id: UUID, ownership_percentage: Decimal, control_type: Enum(DIRECT, INDIRECT), effective_date: DualDate, verified_at: DualDate, verified_by: String }` [GAP-002]
  - `UltimateBeneficialOwner`: `{ ubo_id: UUID, entity_id: UUID, person_id: UUID, total_ownership_percentage: Decimal, ownership_chain: List<UUID>, last_updated: DualDate }` [GAP-002]
- **Dual-Calendar Fields:** `created_at` and `expires_at` in the `Session` entity use `DualDate`; `effective_date`, `verified_at`, `last_updated` in UBO entities use `DualDate`.
- **Event Schema Changes:** New schemas for auth lifecycle events and `BeneficialOwnershipUpdatedEvent`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                            |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Event Name        | `UserAuthenticated`                                                                                                    |
| Schema Version    | `v1.0.0`                                                                                                               |
| Trigger Condition | User successfully completes authentication and MFA.                                                                    |
| Payload           | `{ "user_id": "...", "tenant_id": "...", "timestamp_gregorian": "...", "timestamp_bs": "...", "auth_method": "OIDC" }` |
| Consumers         | Audit Framework, AI Risk Model (for anomaly detection), User Dashboard                                                 |
| Idempotency Key   | `hash(user_id + session_id + timestamp)`                                                                               |
| Replay Behavior   | Suppress side-effects (e.g., don't re-issue tokens); purely reconstruct read projections.                              |
| Retention Policy  | Configurable per jurisdiction (e.g., 5 years).                                                                         |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `AuthenticateUserCommand`                                            |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Valid credentials, account not locked, MFA token valid (if required) |
| Handler          | `AuthenticationCommandHandler` in K-01 IAM                           |
| Success Event    | `UserAuthenticated`                                                  |
| Failure Event    | `AuthenticationFailed`                                               |
| Idempotency      | Command ID must be unique; duplicate commands return cached result   |

| Field            | Description                                                                   |
| ---------------- | ----------------------------------------------------------------------------- |
| Command Name     | `AssignRoleCommand`                                                           |
| Schema Version   | `v1.0.0`                                                                      |
| Validation Rules | User exists, role exists, requester has permission, maker-checker if required |
| Handler          | `RoleCommandHandler` in K-01 IAM                                              |
| Success Event    | `RoleAssigned`                                                                |
| Failure Event    | `RoleAssignmentFailed`                                                        |
| Idempotency      | Command ID must be unique; duplicate commands return original result          |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `RevokeSessionCommand`                                               |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Session exists, requester authorized (self or admin)                 |
| Handler          | `SessionCommandHandler` in K-01 IAM                                  |
| Success Event    | `SessionRevoked`                                                     |
| Failure Event    | `SessionRevocationFailed`                                            |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Anomaly Detection
- **Workflow Steps Exposed:** Login attempt evaluation.
- **Model Registry Usage:** `auth-anomaly-detector-v1`
- **Explainability Requirement:** If AI forces step-up authentication due to anomalous location/velocity, explanation is stored in K-07 Audit.
- **Human Override Path:** Operator can unlock an account locked by AI via the Admin Portal (generates an auditable event).
- **Drift Monitoring:** False positive rate > 1% triggers re-evaluation alert.
- **Fallback Behavior:** Standard MFA policy applies if AI is unavailable.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                                 |
| ------------------------- | ------------------------------------------------------------------------------------------------ |
| Latency / Throughput      | P99 < 50ms for auth evaluation; 5,000 TPS                                                        |
| Scalability               | Horizontal scale-out based on request volume                                                     |
| Availability              | 99.999% target uptime (critical path for all services)                                           |
| Consistency Model         | Strong consistency for role changes                                                              |
| Security                  | Multi-tenant namespace isolation; secrets encrypted at rest via K-08                             |
| Data Residency            | PII and identity data bounded by Jurisdiction Config Pack                                        |
| Data Retention            | Session logs kept per `[LCA-RET-001]` requirements                                               |
| Auditability              | Every failed login and role change recorded                                                      |
| Observability             | Metrics: `auth.success`, `auth.failure`, `auth.latency`, dimensions: `tenant_id`, `jurisdiction` |
| Extensibility             | New ID scheme via plugin < 1 sprint                                                              |
| Upgrade / Compatibility   | JWT validation forward/backward compatible during rolling upgrades                               |
| On-Prem Constraints       | Support local LDAP/AD integration without external internet                                      |
| Ledger Integrity          | N/A                                                                                              |
| Dual-Calendar Correctness | Session dates correctly reflect BS/Gregorian conversion                                          |

---

#### Section 10 — Acceptance Criteria

1. **Given** valid credentials for Tenant A, **When** the user logs in, **Then** a JWT is issued strictly scoped to Tenant A, and `UserAuthenticated` is emitted.
2. **Given** a domain module evaluating permissions, **When** it calls the AuthZ SDK, **Then** IAM accurately returns true/false based on ABAC policies < 50ms.
3. **Given** a user registers with a Nepal National ID, **When** verification is triggered, **Then** IAM delegates format and API validation to the Nepal ID Adapter Plugin.
4. **Given** an administrator attempts to assign the `Compliance_Officer` role, **When** submitted, **Then** IAM requires a distinct second administrator to approve before the role is active.
5. **Given** a network partition to the cloud config store, **When** a user authenticates in an on-prem hybrid mode, **Then** IAM uses the securely cached local config bundle.

---

#### Section 11 — Failure Modes & Resilience

- **External IdP Outage:** Degraded mode allows pre-configured emergency local admin login; standard users fail with clear message.
- **Cache Partition:** IAM queries the persistent store directly with latency penalty, triggering scale-out if necessary.
- **Idempotency:** Repeated token exchange requests within a small time window return the same token or fail gracefully.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                          |
| ------------------- | ------------------------------------------------------------------------- |
| Metrics             | `iam.login.count`, `iam.authz.latency`, dimensions: `tenant_id`, `status` |
| Logs                | Structured: `trace_id`, `tenant_id`, `user_id`, `operation`, `result`     |
| Traces              | Spans for `login`, `evaluate_policy`                                      |
| Audit Events        | `RoleAssigned`, `RoleRevoked`, `LoginFailed` [LCA-AUDIT-001]              |
| Regulatory Evidence | Access control reports for compliance audits                              |

---

#### Section 13 — Compliance & Regulatory Traceability

- AML/KYC readiness — identity verification hooks [LCA-AMLKYC-001]
- Segregation of duties — maker-checker controls for sensitive role assignments [LCA-SOD-001]
- Audit trails for access [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `AuthZClient` exposing `hasPermission(userId, resource, action)`, `getRoles(userId)`.
- **Jurisdiction Plugin Extension Points:** `NationalIdValidator` interface for T3 Adapters.
- **Events Emitted:** `UserAuthenticated`, `RoleGranted`, `RoleRevoked`, `NationalIdVerified`

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                              | Expected Answer                                                                                                          |
| --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| Can this module support India/Bangladesh via plugin?                  | Yes, via Aadhaar or NID plugins.                                                                                         |
| Can a new regulator be added?                                         | N/A for core IAM; role definitions handled via Config.                                                                   |
| Can tax rules change?                                                 | N/A                                                                                                                      |
| Can this run in an air-gapped deployment?                             | Yes, using local AD/LDAP configuration.                                                                                  |
| Can this module handle digital assets (tokenized securities, crypto)? | Yes. Wallet-bound identity and DID (Decentralized Identifier) claims are supported via pluggable identity providers.     |
| Is the design ready for CBDC integration or T+0 settlement?           | Yes. Token-based authentication flows and real-time session management support instant settlement identity verification. |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Credential Stuffing / Brute Force Attacks**
   - **Threat:** Attacker uses leaked credentials or automated tools to gain unauthorized access.
   - **Mitigation:** Rate limiting on login attempts; account lockout after N failed attempts; CAPTCHA after 3 failures; MFA enforcement; breach detection via K-09 AI anomaly detection; credential breach monitoring.
   - **Residual Risk:** Sophisticated distributed attacks may evade rate limiting.

2. **Session Hijacking**
   - **Threat:** Attacker steals session tokens to impersonate legitimate users.
   - **Mitigation:** Short-lived JWT tokens (15-minute expiry); secure cookie flags (HttpOnly, Secure, SameSite); token binding to client IP/fingerprint; session invalidation on suspicious activity; mTLS for service-to-service.
   - **Residual Risk:** Man-in-the-middle attacks on compromised networks.

3. **Privilege Escalation**
   - **Threat:** User exploits vulnerabilities to gain higher privileges than authorized.
   - **Mitigation:** Strict RBAC enforcement at every API call; role assignment requires maker-checker; regular privilege audits; principle of least privilege; all role changes logged to K-07.
   - **Residual Risk:** Zero-day vulnerabilities in RBAC evaluation logic.

4. **Identity Spoofing / Fake National ID**
   - **Threat:** Attacker creates account using forged or stolen National ID.
   - **Mitigation:** National ID verification via government APIs (T3 adapters); document authenticity checks; biometric verification where available; AI-based fraud detection; cross-reference with sanctions lists.
   - **Residual Risk:** Sophisticated forgeries may pass automated checks.

5. **Insider Threat - Admin Abuse**
   - **Threat:** Malicious administrator abuses elevated privileges to access unauthorized data or grant improper access.
   - **Mitigation:** Maker-checker for all sensitive role assignments; all admin actions logged immutably to K-07; anomaly detection on admin behavior; separation of duties; regular access reviews.
   - **Residual Risk:** Collusion between multiple administrators.

6. **SSO/OIDC Provider Compromise**
   - **Threat:** External identity provider is compromised, allowing unauthorized access.
   - **Mitigation:** Multi-provider support with fallback; token validation includes issuer verification; certificate pinning; regular security assessments of IdP integrations; emergency local admin access.
   - **Residual Risk:** Simultaneous compromise of multiple providers.

7. **MFA Bypass**
   - **Threat:** Attacker bypasses MFA through social engineering, SIM swapping, or implementation flaws.
   - **Mitigation:** Multiple MFA options (TOTP, hardware tokens, biometrics); anti-phishing measures; SIM swap detection; MFA enforcement at API level; fallback verification methods.
   - **Residual Risk:** Sophisticated social engineering attacks.

8. **Account Takeover via Password Reset**
   - **Threat:** Attacker exploits password reset flow to take over accounts.
   - **Mitigation:** Multi-factor verification for password reset; email + SMS verification; security questions; account activity notification; reset link expiry (15 minutes); rate limiting on reset requests.
   - **Residual Risk:** Email account compromise.

**Security Controls:**

- Multi-factor authentication (TOTP, hardware tokens, biometrics)
- Rate limiting and account lockout policies
- Session management with short-lived tokens
- RBAC/ABAC with maker-checker for sensitive roles
- Comprehensive audit logging to K-07
- Anomaly detection via K-09 AI Governance
- National ID verification via T3 adapters
- Encryption in transit (mTLS) and at rest
- Regular security assessments and penetration testing
- Principle of least privilege enforcement
- Emergency break-glass procedures with audit trail

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
