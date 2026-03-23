# AppPlatform Marketplace Governance

**Document Type**: Governance Policy  
**Authority Level**: 3 — Normative Reference  
**Version**: 1.0.0 | **Status**: Active | **Date**: 2026-01-19  
**Owner**: AppPlatform Architecture Council  
**Canonical Path**: `products/app-platform/docs/MARKETPLACE_GOVERNANCE.md`

---

## Table of Contents

1. [Purpose & Scope](#1-purpose--scope)
2. [Governance Bodies](#2-governance-bodies)
3. [Pack Submission Process](#3-pack-submission-process)
4. [Review Criteria](#4-review-criteria)
5. [Versioning Policy](#5-versioning-policy)
6. [Deprecation & Removal Policy](#6-deprecation--removal-policy)
7. [Certification Authority](#7-certification-authority)
8. [Licensing Model](#8-licensing-model)
9. [Community Ratings & Feedback](#9-community-ratings--feedback)
10. [Violation & Enforcement](#10-violation--enforcement)

---

## 1. Purpose & Scope

The **AppPlatform Marketplace** is the official channel through which domain packs are distributed to tenants. This governance document establishes the rules for pack publication, maintenance, and removal.

**In scope:**

- All domain packs listed in the AppPlatform Marketplace
- Responsibilities of pack authors and the AppPlatform Certification Authority (ACA)
- Versioning, deprecation, and removal policies

**Out of scope:**

- Kernel module (K-\*) releases — governed by the AppPlatform Release Process
- Private pack deployments (tenants deploying un-certified packs in isolated environments) — governed by tenant agreements

---

## 2. Governance Bodies

### 2.1 AppPlatform Architecture Council (AAC)

- Sets platform-wide policies (this document, ADRs, `DOMAIN_PACK_INTERFACE_SPECIFICATION.md`)
- Has final authority on breaking compatibility decisions
- Meets quarterly; emergency sessions for P0 issues

### 2.2 AppPlatform Certification Authority (ACA)

- Operates the P-01 Pack Certification service
- Issues and revokes `PackCertification` certificates
- Maintains the OWASP suppression registry
- Manages the ACA Ed25519 signing key (HSM-backed)
- Contacts: `aca@appplatform.io`

### 2.3 Domain Pack Authors

- Organizations or individuals who build, maintain, and publish domain packs
- Agree to the AppPlatform Pack Author Agreement (PAA) before first submission
- Responsible for security patches within 14 days of CVE disclosure
- Responsible for maintaining compatibility during their declared support window

---

## 3. Pack Submission Process

### 3.1 Eligibility

To submit a pack to the Marketplace, an author must:

1. Accept the Pack Author Agreement (PAA)
2. Register an organization account at `${APPPLATFORM_MARKETPLACE_URL}/register`
3. Choose a globally unique `pack_id` in reverse-DNS format (e.g., `com.acme.banking`)

### 3.2 First-Time Submission

```
1. Author registers at marketplace portal
2. Author submits pack via P-01 API (see LLD_P01_PACK_CERTIFICATION.md §5)
3. P-01 runs all automated gates (code quality, security, coverage ≥90%, performance)
4. ACA reviews submission if T3 rules with NETWORK or FILESYSTEM permissions are declared
5. ACA issues PackCertification (valid 90 days)
6. Pack appears in Marketplace with status: CERTIFIED
```

### 3.3 Update Submission

Identical flow to first-time. The new version's certification is independent of the prior version. Both versions remain listed; the new version becomes the `LATEST` tag.

### 3.4 Timeline SLAs

| Step                                      | SLA               |
| ----------------------------------------- | ----------------- |
| Automated gates                           | ≤ 30 minutes      |
| ACA human review (T3 NETWORK/FILESYSTEM)  | ≤ 3 business days |
| Certificate issuance after all gates pass | ≤ 1 hour          |

---

## 4. Review Criteria

### 4.1 Automated Gate Criteria (P-01)

All must pass — no exceptions, no manual overrides:

| Gate                                   | Threshold                         |
| -------------------------------------- | --------------------------------- |
| Code Quality (Checkstyle / ESLint)     | Zero ERRORs or WARNs              |
| JavaDoc / `@doc.*` tag completeness    | 100% on public classes            |
| Security — Critical CVEs               | Zero                              |
| Security — High CVEs                   | Zero                              |
| Security — Medium CVEs                 | Suppressed with documented expiry |
| Test Coverage                          | ≥ 90% line coverage               |
| Performance — core capability p99      | ≤ 100ms (or declared SLO)         |
| Performance — event publish throughput | ≥ 500 events/s (or declared SLO)  |

### 4.2 Editorial Review Criteria (ACA — T3 packs only)

The ACA human review checks:

- **Necessity**: Does the T3 rule legitimately require NETWORK/FILESYSTEM permission?
- **Scope**: Is the declared `allowedHosts` list minimal and justified?
- **No data exfiltration**: Static analysis confirms no credential/PII extraction
- **Description accuracy**: Marketplace description accurately represents pack capabilities

### 4.3 Domain Pack Identity Criteria

- `pack_id` must be in a DNS namespace controlled by the author's registered organization
- `name` and `description` must not impersonate AppPlatform kernel modules or other certified packs
- `domainTypes` must use established values from `WellKnownDomainTypes` where applicable, or be clearly defined in the description

---

## 5. Versioning Policy

All domain packs MUST follow [Semantic Versioning 2.0.0](https://semver.org).

| Change Type                                            | Version Increment              | Backward Compatible?   |
| ------------------------------------------------------ | ------------------------------ | ---------------------- |
| Bug fix, no API change                                 | PATCH (`0.0.x`)                | Yes                    |
| New capability, new optional event field               | MINOR (`0.x.0`)                | Yes                    |
| New required event field, removed field, renamed topic | MAJOR (`x.0.0`)                | No                     |
| New T3 permission                                      | MINOR (requires ACA re-review) | Yes (opt-in by tenant) |

### 5.1 Pre-release Versions

Packs may publish `BETA` (e.g., `1.0.0-beta.1`) and `RC` (e.g., `1.0.0-rc.1`) versions. These:

- Are clearly labeled in Marketplace as `PRE-RELEASE`
- Cannot be installed without explicit tenant opt-in
- Must complete all automated gates but ACA human review is expedited (24h SLA)

### 5.2 Long-Term Support (LTS)

Pack authors may designate a version as `LTS`. LTS versions:

- Receive security patches for a minimum of 18 months
- Are prioritized in Marketplace discovery
- Require a support commitment declaration in the `DomainManifest`

---

## 6. Deprecation & Removal Policy

### 6.1 Version Deprecation

When a pack version is deprecated:

1. Author marks the version `DEPRECATED` in the Marketplace portal
2. Affected tenants receive a notification with the `sunset_date` (minimum 90 days from deprecation)
3. Newer versions of the pack remain available
4. After `sunset_date`, the deprecated version is `UNLISTED` (still installable by tenants who have it, but not discoverable by new tenants)

### 6.2 Pack Retirement (Complete Removal)

A pack may be retired (fully removed from Marketplace) only if:

- The author submits a retirement request with ≥ 180 days notice, AND
- Zero tenants have it currently installed, OR the author provides a migration path to an alternative pack

Emergency retirement (security incident) is possible without notice, with ACA approval.

### 6.3 Event Topic Deprecation (Cross-Pack Impact)

When a published event topic is deprecated (see `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md §12`):

- The old topic bridges to the new topic for ≥ 90 days
- Subscriber packs are notified via `io.appplatform.marketplace.topic.deprecated.v1` event
- ACA tracks dependent packs and may block pack retirement until all subscribers have migrated

---

## 7. Certification Authority

### 7.1 Certificate Validity

- `PackCertification` certificates are valid for **90 days**
- Authors are reminded 14 days before expiry
- Expired certificates block installation of new instances (existing tenant installations continue to run but cannot be reinstalled after expiry)

### 7.2 Certificate Renewal

Renewal re-runs all automated gates against the **same artifact** (same SHA-256 digest). Renewal does not require a version bump.

### 7.3 Revocation

ACA may revoke a certificate immediately if:

- A critical CVE is disclosed in a pack's dependency
- Security policy violation is discovered post-certification
- Author requests revocation (e.g., compromised signing key)

Revoked packs are added to the CRL (Certificate Revocation List) polled by all Platform Kernel instances every 5 minutes. Installations of revoked packs are blocked immediately.

### 7.4 Appeals

Authors may appeal a rejection or revocation via `appeal@appplatform.io`. The AAC reviews appeals within 5 business days.

---

## 8. Licensing Model

> **Note**: The commercial licensing model is placeholder and subject to change by the AppPlatform business team.

### 8.1 License Types

| License Type  | Description                                           |
| ------------- | ----------------------------------------------------- |
| `OPEN_SOURCE` | Pack source is public; free to install                |
| `COMMERCIAL`  | Paid license required; billing handled by Marketplace |
| `FREEMIUM`    | Free tier with usage quotas; paid for higher limits   |
| `ENTERPRISE`  | Negotiated agreement; contact pack author             |

The license type is declared in `DomainManifest.license`.

### 8.2 Revenue Sharing (Commercial Packs)

For commercially licensed packs distributed via the Marketplace:

- AppPlatform takes a platform fee (percentage — TBD by business)
- Revenue is disbursed monthly to the author's registered payment account
- Usage metering is provided by K-17 Observability (install counts, active tenants)

### 8.3 Open-Source Packs

Open-source packs must declare a valid SPDX license identifier in `DomainManifest.license.spdxId`. The ACA verifies that the declared license is compatible with the AppPlatform PAA.

---

## 9. Community Ratings & Feedback

### 9.1 Rating System

Tenant administrators can rate installed packs (1–5 stars) after a minimum of 30 days of use. Ratings are visible on the Marketplace listing.

| Visible Metric   | Description                                           |
| ---------------- | ----------------------------------------------------- |
| `avg_rating`     | Weighted average (recent ratings weighted higher)     |
| `install_count`  | Total unique tenant installations                     |
| `active_tenants` | Currently active installations                        |
| `p99_latency`    | Aggregated performance metric (from K-17, anonymized) |

### 9.2 Reviews

Text reviews are:

- Optional — tenant admins may or may not provide text
- Moderated by ACA for content policy compliance
- Not editable by pack authors (to prevent manipulation)

### 9.3 Abuse Reporting

Tenants may flag a pack for security concerns or misrepresentation via the Marketplace portal. Flagged packs are reviewed by ACA within 48 hours.

---

## 10. Violation & Enforcement

### 10.1 Author Violations

| Violation                                        | Consequence                                                              |
| ------------------------------------------------ | ------------------------------------------------------------------------ |
| Submitting malicious code                        | Permanent ban; ACA notifies affected tenants; security incident declared |
| Failing to patch Critical CVE within 14 days     | Pack suspended from Marketplace until patched                            |
| Impersonating another pack or AppPlatform Kernel | Immediate delisting; PAA termination                                     |
| Misrepresenting pack capabilities in description | Warning (1st offense); delisting (2nd offense)                           |
| Breaking PAA terms                               | PAA termination; all packs delisted                                      |

### 10.2 Tenant Violations

Tenants deploying packs in ways that violate the tenant agreement are subject to the AppPlatform Terms of Service enforcement, not governed by this document.

### 10.3 Enforcement Contacts

- Security incidents: `security@appplatform.io` (24/7)
- Policy violations: `compliance@appplatform.io`
- Appeals: `appeal@appplatform.io`
- General governance: `governance@appplatform.io`

---

_This governance document is maintained by the AppPlatform Architecture Council. Review cycle: annually or after any material marketplace policy change._
