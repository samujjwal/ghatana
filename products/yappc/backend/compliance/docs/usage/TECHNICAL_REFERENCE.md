# YAPPC Backend – Compliance Module – Technical Reference

## 1. Overview

This reference summarizes the main technical elements of the Compliance backend module.

## 2. Core Types (Conceptual)

- `PolicyStatus` – Enum of policy lifecycle states (DRAFT, ACTIVE, DEPRECATED, ARCHIVED).
- `PolicyAcknowledgment` – Records who acknowledged a policy, when, and for which version.
- `CompliancePolicy` – Core policy model including metadata, content, status, version, owner, timestamps, review cycle, and acknowledgments.
- `CompliancePolicyManager` – In-memory manager exposing operations on policies and acknowledgments.

## 3. Key Operations (Illustrative)

- `createPolicy(...)` – Create a new policy with timestamps and generated ID.
- `updatePolicy(id, updates)` – Update a policy and bump version.
- `archivePolicy(id)` / `deprecatePolicy(id)` – Lifecycle helpers.
- `recordAcknowledgment(id, email, ipAddress?)` – Track user acknowledgments.
- `hasAcknowledged(id, email, version?)` – Check acknowledgment status.
- `getPoliciesNeedingAcknowledgment(email)` – Determine which active policies a user must acknowledge.
- `getPoliciesDueForReview(daysThreshold?)` – Identify policies that need review.
- `getAcknowledgmentStats(id, allUsers)` – Compute coverage metrics.
- `searchPolicies(query, filters?)` – Search policies by text and filters.
- `exportPolicies(format)` – Export policies as JSON or CSV.
- `cleanup(retentionDays?)` – Remove archived policies beyond retention.

## 4. Integration Points

- Used by YAPPC backend services to implement compliance APIs and dashboards.
- Typically wrapped by persistence and HTTP layers outside this module.

This reference is self-contained and summarizes Compliance module capabilities at a technical level.
