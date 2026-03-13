# LLD: P-01 Pack Certification & Marketplace

**Document Type**: Low-Level Design (LLD)  
**Authority Level**: 3 — Normative Reference  
**Version**: 1.0.0 | **Status**: Active | **Date**: 2026-01-19  
**Module**: P-01 Pack Certification & Marketplace  
**Owner**: AppPlatform Architecture Council  
**Canonical Path**: `products/app-platform/docs/lld/LLD_P01_PACK_CERTIFICATION.md`

---

## Table of Contents

1. [Module Overview](#1-module-overview)
2. [Invariants](#2-invariants)
3. [Certification Workflow](#3-certification-workflow)
4. [Automated Gate Definitions](#4-automated-gate-definitions)
5. [REST API](#5-rest-api)
6. [gRPC Contract](#6-grpc-contract)
7. [Data Model](#7-data-model)
8. [Marketplace Publication](#8-marketplace-publication)
9. [Security Model](#9-security-model)
10. [SDK](#10-sdk)
11. [Observability](#11-observability)

---

## 1. Module Overview

**P-01 Pack Certification & Marketplace** is the Platform service responsible for:

- Accepting domain pack submissions from pack authors
- Running automated quality, security, and performance gates
- Issuing signed `PackCertification` certificates to packs that pass all gates
- Publishing certified packs to the AppPlatform Marketplace
- Serving pack discovery, installation, and update APIs to tenant administrators

P-01 operates as a Platform-layer service, not a Kernel module (K-\*). It is referenced by `products/app-platform/docs/MARKETPLACE_GOVERNANCE.md`.

---

## 2. Invariants

| #     | Invariant                                                                                                                                        |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| INV-1 | A domain pack MUST NOT be installable in any tenant without a valid `PackCertification` signed by the AppPlatform Certification Authority (ACA). |
| INV-2 | `PackCertification` MUST reference the exact SHA-256 digest of the pack artifact. Any artifact modification invalidates the certificate.         |
| INV-3 | All automated gates MUST pass before a certificate is issued. No manual override of gate results.                                                |
| INV-4 | Coverage gate threshold is ≥ 90% for all non-generated code.                                                                                     |
| INV-5 | Zero critical or high CVEs allowed at certification time. Medium CVEs require documented suppression with expiry.                                |
| INV-6 | P-01 MUST be multi-tenant: submissions from different organizations are fully isolated.                                                          |
| INV-7 | Certificates are valid for 90 days. Renewal requires re-running all gates against the same artifact.                                             |

---

## 3. Certification Workflow

```
Pack Author
    │
    │ POST /api/v1/certifications/submit
    ▼
P-01 Submission Service
    │
    ├── 1. Manifest Validation (sync, <1s)
    │        Schema check, required fields, semver format, KernelModuleConstraint syntax
    │
    ├── 2. Artifact Integrity (async, <30s)
    │        SHA-256 digest computed & stored; signature envelope verified
    │
    ├── 3. Automated Gate Runner (async, <20 min)
    │        ├── Code Quality Gate    (see §4.1)
    │        ├── Security Scan Gate   (see §4.2)
    │        ├── Coverage Gate        (see §4.3)
    │        └── Performance Gate     (see §4.4)
    │
    ├── 4. Human Review (optional, async)
    │        Triggered if any gate emits a WARNING (non-blocking)
    │        Required if pack declares T3 rules with NETWORK or FILESYSTEM permissions
    │
    └── 5. Certificate Issuance (sync, on all gates PASS)
             PackCertification signed with ACA private key
             Stored in P-01 Certificate Registry
             Author notified via webhook
```

### 3.1 Submission States

```
SUBMITTED → VALIDATING → GATES_RUNNING → REVIEW_PENDING (optional) → CERTIFIED
                                        └──────────────────────────→ REJECTED
```

A submission in `REJECTED` state carries a `gateResults[]` array with full detail for each failed gate.

---

## 4. Automated Gate Definitions

### 4.1 Code Quality Gate

**Tool**: Checkstyle + PMD (Java packs); ESLint (TypeScript packs)  
**Rules file**: `config/checkstyle/checkstyle.xml`; `config/pmd/ruleset.xml`  
**Pass condition**: Zero violations at severity ERROR or WARN.

Additionally:

- All public classes/interfaces must have JavaDoc with `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern` tags (enforced by `gradle/doc-tag-check.gradle` logic).
- No duplicate implementations detected by `gradle/duplicate-check.gradle` logic.

### 4.2 Security Scan Gate

**Tool**: OWASP Dependency-Check  
**Suppression file**: `config/owasp-suppressions.xml` (ACA-maintained)

| CVE Severity        | Policy                                                     |
| ------------------- | ---------------------------------------------------------- |
| Critical            | Gate FAILS — certificate blocked                           |
| High                | Gate FAILS — certificate blocked                           |
| Medium              | Gate WARNING — suppression with documented expiry required |
| Low / Informational | Logged only                                                |

Additionally, the P-01 Security Scanner performs static analysis for:

- Hardcoded credentials (secrets scan)
- SSRF-susceptible URL construction patterns
- SQL injection patterns in T2/T3 rule code
- Insecure deserialization

### 4.3 Coverage Gate

**Tool**: JaCoCo (Java); Istanbul/c8 (TypeScript)  
**Threshold**: Line coverage ≥ 90% across all non-generated source files.  
**Exclusions**: Auto-generated protobuf stubs, configuration-only classes annotated `@CoverageExclude`.

### 4.4 Performance Gate

P-01 spins up an isolated sandbox environment and runs the pack's declared benchmark suite:

| Benchmark                          | Default Threshold | Override                                  |
| ---------------------------------- | ----------------- | ----------------------------------------- |
| Pack activate latency              | ≤ 2s              | `manifest.performanceSLO.activateMs`      |
| Core capability p99 latency (warm) | ≤ 100ms           | `manifest.performanceSLO.p99Ms`           |
| Event publish throughput           | ≥ 500 events/s    | `manifest.performanceSLO.eventsPerSecond` |
| Memory footprint (idle)            | ≤ 512 MiB         | `manifest.performanceSLO.idleMemoryMiB`   |

If no benchmark suite is declared by the pack, P-01 runs a synthetic load test using the pack's manifest-declared capabilities.

---

## 5. REST API

### Submit Pack for Certification

```http
POST /api/v1/certifications/submit
Content-Type: multipart/form-data

manifest: <DomainManifest JSON file>
artifact: <Pack JAR / Docker image tarball>
```

**Response** `202 Accepted`:

```json
{
  "submission_id": "sub_01J...",
  "pack_id": "com.ghatana.capital-markets",
  "version": "2.0.0",
  "status": "SUBMITTED",
  "estimated_gate_completion_minutes": 20,
  "webhook_url": "https://your-server/certification-webhook"
}
```

---

### Get Submission Status

```http
GET /api/v1/certifications/{submission_id}
```

**Response** `200 OK`:

```json
{
  "submission_id": "sub_01J...",
  "pack_id": "com.ghatana.capital-markets",
  "version": "2.0.0",
  "status": "GATES_RUNNING",
  "gate_results": [
    { "gate": "CODE_QUALITY", "status": "PASSED", "warnings": 0, "errors": 0 },
    {
      "gate": "SECURITY_SCAN",
      "status": "PASSED",
      "critical_cves": 0,
      "high_cves": 0
    },
    { "gate": "COVERAGE", "status": "RUNNING", "coverage_percent": null },
    { "gate": "PERFORMANCE", "status": "PENDING" }
  ],
  "certificate": null
}
```

---

### Get Certificate

```http
GET /api/v1/certifications/{submission_id}/certificate
```

**Response** `200 OK` (after `CERTIFIED` status):

```json
{
  "certificate_id": "cert_01J...",
  "pack_id": "com.ghatana.capital-markets",
  "version": "2.0.0",
  "certified_at": "2026-01-19T10:00:00Z",
  "expires_at": "2026-04-19T10:00:00Z",
  "artifact_digest": "sha256:abc123...",
  "signed_by": "AppPlatform Certification Authority",
  "signature": "<ACA Ed25519 signature>"
}
```

---

### Marketplace: List Packs

```http
GET /api/v1/marketplace/packs?category={category}&page={n}&size={m}
```

**Response** `200 OK`:

```json
{
  "total": 42,
  "packs": [
    {
      "pack_id": "com.ghatana.capital-markets",
      "name": "Capital Markets (Siddhanta)",
      "description": "...",
      "latest_version": "2.0.0",
      "domain_types": ["capital-markets", "financial-services"],
      "capabilities": ["ENTITY_MANAGEMENT", "WORKFLOW_ORCHESTRATION"],
      "rating": 4.7,
      "install_count": 12,
      "certified_at": "2026-01-19T10:00:00Z"
    }
  ]
}
```

---

### Install Pack into Tenant

```http
POST /api/v1/marketplace/packs/{pack_id}/install
Authorization: Bearer <tenant-admin-token>
Content-Type: application/json

{
  "version": "2.0.0",
  "tenant_id": "tenant_xyz"
}
```

Triggers the same installation workflow as the `LifecycleHooks.onInstall` sequence described in `DOMAIN_PACK_UPGRADE_RUNBOOK.md §8`.

---

## 6. gRPC Contract

```protobuf
syntax = "proto3";
package platform.certification.v1;

service CertificationService {
  rpc SubmitPack(SubmitPackRequest) returns (SubmitPackResponse);
  rpc GetSubmissionStatus(GetSubmissionStatusRequest) returns (SubmissionStatus);
  rpc GetCertificate(GetCertificateRequest) returns (PackCertification);
  rpc RenewCertificate(RenewCertificateRequest) returns (PackCertification);
}

message SubmitPackRequest {
  bytes manifest_json = 1;
  bytes artifact_bytes = 2;
  string webhook_url = 3;
}

message SubmitPackResponse {
  string submission_id = 1;
  string status = 2;
}

message GateResult {
  string gate_name = 1;
  string status = 2;        // PENDING | RUNNING | PASSED | WARNED | FAILED
  repeated string messages = 3;
}

message SubmissionStatus {
  string submission_id = 1;
  string pack_id = 2;
  string version = 3;
  string status = 4;
  repeated GateResult gate_results = 5;
}

message PackCertification {
  string certificate_id = 1;
  string pack_id = 2;
  string version = 3;
  string certified_at = 4;  // ISO 8601
  string expires_at = 5;    // ISO 8601
  string artifact_digest = 6;
  string signed_by = 7;
  bytes signature = 8;      // Ed25519
}
```

---

## 7. Data Model

### 7.1 SQL DDL

```sql
-- Certification submissions
CREATE TABLE p01_submissions (
  submission_id      TEXT        PRIMARY KEY,
  pack_id            TEXT        NOT NULL,
  version            TEXT        NOT NULL,
  status             TEXT        NOT NULL DEFAULT 'SUBMITTED',
  artifact_digest    TEXT,
  manifest_json      JSONB,
  submitted_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at       TIMESTAMPTZ,
  submitter_identity TEXT        NOT NULL,  -- JWT subject of submitter
  UNIQUE (pack_id, version)
);

-- Individual gate results
CREATE TABLE p01_gate_results (
  id             BIGSERIAL   PRIMARY KEY,
  submission_id  TEXT        NOT NULL REFERENCES p01_submissions(submission_id),
  gate_name      TEXT        NOT NULL,
  status         TEXT        NOT NULL,
  messages       JSONB,
  started_at     TIMESTAMPTZ,
  completed_at   TIMESTAMPTZ
);

-- Issued certificates
CREATE TABLE p01_certificates (
  certificate_id  TEXT        PRIMARY KEY,
  submission_id   TEXT        NOT NULL REFERENCES p01_submissions(submission_id),
  pack_id         TEXT        NOT NULL,
  version         TEXT        NOT NULL,
  artifact_digest TEXT        NOT NULL,
  certified_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at      TIMESTAMPTZ NOT NULL,
  signature       BYTEA       NOT NULL,
  revoked         BOOLEAN     NOT NULL DEFAULT FALSE,
  revoked_at      TIMESTAMPTZ,
  revocation_reason TEXT
);

-- Marketplace metadata
CREATE TABLE p01_marketplace_listings (
  pack_id          TEXT        PRIMARY KEY,
  display_name     TEXT        NOT NULL,
  description      TEXT,
  latest_version   TEXT        NOT NULL,
  domain_types     JSONB,
  capabilities     JSONB,
  total_installs   INTEGER     NOT NULL DEFAULT 0,
  avg_rating       NUMERIC(3,1),
  listed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_p01_submissions_pack ON p01_submissions(pack_id, status);
CREATE INDEX idx_p01_certs_pack ON p01_certificates(pack_id, expires_at) WHERE NOT revoked;
```

---

## 8. Marketplace Publication

After `CERTIFIED`, P-01 automatically:

1. Creates or updates the `p01_marketplace_listings` entry for the pack.
2. Publishes a `io.appplatform.marketplace.pack.published.v1` event to K-05, allowing marketplace UI and tenant notification systems to react.
3. The pack appears as `LATEST` in marketplace search for its `domain_types`.

### 8.1 Listing Metadata (from DomainManifest)

| Marketplace Field   | Source in DomainManifest                    |
| ------------------- | ------------------------------------------- |
| `display_name`      | `name`                                      |
| `description`       | `description`                               |
| `domain_types`      | `domainTypes[]`                             |
| `capabilities`      | `capabilities[]` + `extendedCapabilities[]` |
| `support_url`       | `support.url`                               |
| `documentation_url` | `documentation.url`                         |

---

## 9. Security Model

### 9.1 Certificate Signing

Certificates are signed with an Ed25519 key managed by the ACA HSM (Hardware Security Module). The public key is distributed to all Platform Kernel instances at startup. A pack cannot be installed without a valid signature verification.

### 9.2 Certificate Revocation

Revocation is implemented via a CRL (Certificate Revocation List) endpoint polled by all Kernel instances every 5 minutes:

```http
GET /api/v1/certifications/crl
```

Response is a JSON array of `{ certificate_id, pack_id, version, revoked_at, reason }`. Kernel instances cache this list and reject installations of revoked packs.

### 9.3 Submission Isolation

Each pack submission runs in an isolated sandbox container with:

- No network egress (except to P-01 internal artifact registry)
- Read-only filesystem (except a defined temp mount)
- CPU / memory quotas enforced by container runtime

### 9.4 Authentication

- Pack authors authenticate via OpenID Connect (same IdP as the AppPlatform admin portal).
- Marketplace install endpoint requires a `tenant-admin` role JWT.
- P-01 internal service-to-service calls use mTLS.

---

## 10. SDK

```typescript
import { CertificationClient } from "@appplatform/certification-sdk";

const client = new CertificationClient({
  baseUrl: process.env.APPPLATFORM_CERT_URL,
});

// Submit
const submission = await client.submitPack({
  manifestPath: "./domain-manifest.json",
  artifactPath: "./build/my-pack-2.0.0.jar",
  webhookUrl: "https://your-server/hook",
});

// Poll until done
let status = await client.getStatus(submission.submissionId);
while (status.status === "GATES_RUNNING" || status.status === "SUBMITTED") {
  await sleep(10_000);
  status = await client.getStatus(submission.submissionId);
}

if (status.status === "CERTIFIED") {
  const cert = await client.getCertificate(submission.submissionId);
  console.log("Certificate ID:", cert.certificateId);
  console.log("Expires:", cert.expiresAt);
} else {
  console.error("Certification failed:", status.gateResults);
}
```

---

## 11. Observability

| Metric                             | Labels                                  |
| ---------------------------------- | --------------------------------------- |
| `p01_submissions_total`            | `status` (CERTIFIED, REJECTED, RUNNING) |
| `p01_gate_duration_seconds`        | `gate_name`, `status`                   |
| `p01_certification_issuance_total` | —                                       |
| `p01_certificates_expiring_soon`   | `days_until_expiry` bucket              |
| `p01_marketplace_install_total`    | `pack_id`, `version`, `tenant_id`       |

Alerts:

- `p01_certificates_expiring_soon{days_until_expiry="7"} > 0` → Notify pack author
- `p01_gate_duration_seconds{gate_name="SECURITY_SCAN"} > 900` → Gate runner health alert

---

_This LLD is maintained by the AppPlatform Architecture Council. Changes require an ADR._
