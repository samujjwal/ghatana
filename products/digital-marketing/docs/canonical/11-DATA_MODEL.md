# DMOS Data Model

## Purpose

This document defines the self-contained DMOS data model: entities, relationships, invariants, persistence rules, privacy treatment, retention expectations, and reporting semantics.

## Data Modeling Principles

1. Every business record is scoped by tenant and workspace unless explicitly global.
2. Production state must be durable.
3. In-memory stores are local/test only.
4. IDs should be typed at domain boundaries.
5. Mutating workflows must preserve auditability.
6. Approval decisions and audit events are immutable.
7. AI actions require provenance.
8. External connector state must be reconciled and idempotent.
9. Reporting formulas must be reproducible from stored or imported data.
10. PII must be classified, redacted, retained, and deleted according to policy.

## Core Identity and Scope

| Entity | Purpose |
|---|---|
| Tenant | Top-level isolation boundary |
| Workspace | Product operating context within tenant |
| Principal | Authenticated user or service actor |
| Session | Request/session continuity |
| Role | Named authority group |
| Permission | Specific capability/action grant |
| ClientAccount | Optional agency/client scope |
| ConnectorAccount | External platform account linked to workspace/client |

Every table that stores product business state should include:

- `tenant_id`
- `workspace_id`
- `created_at`
- `updated_at`
- `created_by` where applicable
- status where lifecycle-driven

## Main Entities

## Campaign

Represents a marketing campaign.

Recommended fields:

- `id`
- `tenant_id`
- `workspace_id`
- `client_account_id`
- `name`
- `objective`
- `audience_summary`
- `status`
- `start_date`
- `end_date`
- `budget_id`
- `strategy_id`
- `risk_level`
- `launch_readiness_status`
- `created_by`
- `created_at`
- `updated_at`

Invariants:

- Campaign cannot launch without required readiness checks.
- Invalid lifecycle transitions are rejected.
- Campaign queries are tenant/workspace scoped.
- Archive does not delete audit history.
- Rollback state must reference supported external/connector action where relevant.

## CampaignLifecycleEvent

Captures lifecycle changes.

Fields:

- `id`
- `tenant_id`
- `workspace_id`
- `campaign_id`
- `from_status`
- `to_status`
- `reason`
- `actor_id`
- `correlation_id`
- `created_at`

Invariant: lifecycle events are append-only.

## Strategy

Represents marketing strategy generated or edited for a campaign/workspace.

Fields:

- `id`
- `tenant_id`
- `workspace_id`
- `campaign_id`
- `objective`
- `target_segments`
- `positioning`
- `channel_mix`
- `messaging`
- `assumptions`
- `risks`
- `success_metrics`
- `status`
- `ai_action_id`
- `version`
- `created_at`
- `updated_at`

Invariants:

- Approved strategy versions are immutable.
- AI-generated strategy must link to AI action provenance.
- Strategy approval state must be consistent with approval records.

## BudgetPlan

Represents budget recommendation and approved budget.

Fields:

- `id`
- `tenant_id`
- `workspace_id`
- `campaign_id`
- `currency`
- `total_budget`
- `channel_allocations`
- `assumptions`
- `recommendation_source`
- `approval_status`
- `ai_action_id`
- `created_at`
- `updated_at`

Invariants:

- Currency must be explicit.
- Budget changes above thresholds require approval.
- Approved budget records should preserve historical values.

## ContentAsset

Represents generated or manually authored marketing content.

Fields:

- `id`
- `tenant_id`
- `workspace_id`
- `campaign_id`
- `type`
- `channel`
- `title`
- `body`
- `status`
- `version`
- `ai_action_id`
- `validation_status`
- `created_by`
- `created_at`
- `updated_at`

Invariants:

- Approved/published versions are immutable.
- AI-generated content links to AI action.
- High-risk content requires approval before public use.

## ContentValidationResult

Represents validation findings.

Fields:

- `id`
- `tenant_id`
- `workspace_id`
- `content_asset_id`
- `rule_pack`
- `risk_level`
- `findings`
- `status`
- `created_at`

Invariants:

- Validation result must be traceable to rule pack version.
- Findings must not expose unredacted sensitive data.

## ApprovalRequest

Represents a required human decision.

Fields:

- `id`
- `tenant_id`
- `workspace_id`
- `target_type`
- `target_id`
- `action`
- `requested_by`
- `requested_at`
- `status`
- `risk_level`
- `snapshot`
- `metadata`

Invariants:

- Snapshot is immutable.
- Request is visible only to authorized reviewers.
- Target references must remain resolvable or snapshot must be sufficient.

## ApprovalDecision

Represents reviewer action.

Fields:

- `id`
- `tenant_id`
- `workspace_id`
- `approval_request_id`
- `decision`
- `notes`
- `decided_by`
- `decided_at`
- `correlation_id`

Invariants:

- Append-only or immutable once written.
- Duplicate conflicting decisions rejected.
- Unauthorized reviewer cannot create decision.

## AiActionLog

Represents AI-assisted action.

Fields:

- `id`
- `tenant_id`
- `workspace_id`
- `entity_type`
- `entity_id`
- `action_type`
- `provider`
- `model`
- `model_version`
- `prompt_summary`
- `input_hash`
- `output_summary`
- `rationale`
- `confidence`
- `risk_level`
- `status`
- `redaction_status`
- `created_by`
- `created_at`

Invariants:

- Sensitive content must be redacted or summarized.
- Model/provider metadata required.
- AI action must be linked to affected entity when applicable.

## ConnectorAccount

Represents external channel account.

Fields:

- `id`
- `tenant_id`
- `workspace_id`
- `client_account_id`
- `provider`
- `external_account_id`
- `status`
- `token_reference`
- `last_validated_at`
- `last_sync_at`
- `created_at`
- `updated_at`

Invariants:

- Tokens are never stored raw in normal business tables.
- Connector account is workspace/client scoped.
- Disabled connector blocks external execution.

## ConnectorExecution

Represents external execution command/outcome.

Fields:

- `id`
- `tenant_id`
- `workspace_id`
- `connector_account_id`
- `campaign_id`
- `operation`
- `idempotency_key`
- `status`
- `external_id`
- `attempt_count`
- `last_error_code`
- `last_error_message`
- `next_retry_at`
- `created_at`
- `updated_at`

Invariants:

- Idempotency key uniqueness prevents duplicate external execution.
- Retryable and non-retryable errors are distinguished.
- DLQ state is explicit.
- Kill switch prevents new execution.

## PerformanceMetric

Represents imported or computed performance data.

Fields:

- `id`
- `tenant_id`
- `workspace_id`
- `campaign_id`
- `connector_account_id`
- `source`
- `metric_date`
- `impressions`
- `clicks`
- `spend`
- `conversions`
- `revenue`
- `currency`
- `freshness_at`
- `created_at`

Invariants:

- Source and freshness are required.
- Currency is explicit for financial metrics.
- Queries are tenant/workspace/campaign scoped.
- Derived reports must define formulas.

## AuditEvent

Represents important user/system action.

Fields:

- `id`
- `tenant_id`
- `workspace_id`
- `actor_id`
- `action`
- `entity_type`
- `entity_id`
- `correlation_id`
- `metadata`
- `created_at`

Invariants:

- Append-only.
- Redacted.
- Sufficient for forensic analysis.
- Does not store secrets or raw PII unless explicitly allowed and protected.

## Reporting Semantics

Reports must be computed from durable business records, connector imports, or explicitly documented external sources.

Required reporting metadata:

- Source table/provider.
- Freshness timestamp.
- Formula version.
- Tenant/workspace filters.
- Currency/date handling.
- Partial data warning where applicable.

## Privacy Classification

| Data | Classification | Handling |
|---|---|---|
| Access/refresh tokens | Secret | Store in secure secret/token store; never log |
| Email/name/contact fields | PII | Hash/encrypt/redact according to policy |
| Prompt/input text | Potential PII | Summarize/redact before logging |
| AI output | Potential PII/regulated claims | Validate, redact, retain with policy |
| Campaign metadata | Business confidential | Tenant scoped, audited |
| Performance metrics | Business confidential | Tenant scoped, aggregate carefully |
| Audit events | Sensitive operational data | Append-only, redacted, retained |

## Retention and DSAR

DMOS must support:

- Listing personal data associated with a principal/contact.
- Exporting relevant personal data.
- Deleting or anonymizing personal data where policy allows.
- Preserving required audit/legal records where deletion is restricted.
- Recording DSAR request and outcome.
- Removing connector tokens during deactivation.

## Indexing Requirements

Minimum index families:

- Tenant/workspace on all scoped records.
- Campaign by workspace/status.
- Approval by workspace/status/risk/requested_at.
- AI action by workspace/entity/created_at.
- Connector execution by status/next_retry_at/idempotency.
- Performance metric by campaign/date/source.
- Audit by workspace/entity/correlation_id/created_at.

## Data Model Acceptance Criteria

The data model is production-ready when:

- Every stateful feature has durable storage.
- Every query enforces tenant/workspace scope.
- Every critical invariant is enforced by domain logic, database constraint, or both.
- Every mutating action is auditable.
- Every AI action has provenance.
- Every connector execution is idempotent and reconcilable.
- PII and secrets are protected.
- Reports are reproducible from known sources.

## Recovered Detailed Entity Additions

## Contact and Identity Context

| Entity | Key Attributes | Notes |
|---|---|---|
| Contact | id, firstName, lastName, piiClassification, consentStatus | PII-safe contact root |
| ContactPoint | id, contactId, type, value, verified, optedOut | Email, phone, address, social |
| Identity | id, contactId, externalId, externalSystem, identityHash | External identity mapping |
| ContactPreference | id, contactId, channel, preference, effectiveAt | Communication preferences |
| ContactMergeRecord | id, sourceContactIds, targetContactId, reason, approvedBy | Identity resolution audit |

Lead records must reference contacts/contact points rather than store raw PII blobs.

## Sales Context

| Entity | Key Attributes | Notes |
|---|---|---|
| Lead | id, campaignId, contactId, source, score, status | Captured from campaign or self-marketing |
| Opportunity | id, leadId, value, stage, probability, closeDate | Pipeline tracking |
| Proposal | id, opportunityId, scope, pricing, terms, status | Commercial proposal |
| Contract | id, proposalId, documentUrl, signedDate, renewalDate | Service agreement |
| Invoice | id, contractId, amount, dueDate, status, paidDate | Billing linkage |
| RenewalRecommendation | id, contractId, resultSummary, nextScope, status | Expansion loop |

## Governance Context

| Entity | Key Attributes | Notes |
|---|---|---|
| Consent | id, contactId, type, channel, lawfulBasis, source, proof, region, policyVersion, grantedAt, revokedAt | Consent record |
| ConsentBasis | id, consentId, basisType, sourceDocument, timestamp | Legal/policy basis |
| SuppressionList | id, workspaceId, type, scope, reason, entries | Do-not-contact/audience exclusion |
| DoNotContactRule | id, workspaceId, criteria, scope, reason | Policy-level suppression |
| Policy | id, workspaceId, type, region, rules, version, active | Product policy |
| Claim | id, contentId, text, evidenceId, approvalStatus, riskLevel | Marketing claim |
| Evidence | id, claimId, sourceUrl, sourceFile, reviewer, expiration | Claim support |
| Disclosure | id, contentId, type, required, addedBy | Endorsement/AI/sponsor disclosure |
| AuditLog | id, action, actor, timestamp, changes, correlationId, policySnapshotId, consentSnapshotId | Audit trail |

## Integration Context

| Entity | Key Attributes | Notes |
|---|---|---|
| ExternalConnection | id, workspaceId, platform, accountName, oauthScope, secretId, status, health | External system connection |
| ExternalObjectMapping | id, connectionId, localType, localId, externalType, externalId, mappingType | Local/external identity map |
| ConnectorAccount | id, workspaceId, platform, accountId, quota, rateLimit, lastSync | Connector account/runtime state |

## Learning Context

| Entity | Key Attributes | Notes |
|---|---|---|
| Playbook | id, workspaceId, industry, steps, successRate, versionId | Reusable growth pattern |
| PlaybookVersion | id, playbookId, version, changes, promotedBy | Versioned playbook |
| ExperimentLearning | id, experimentId, finding, confidence, actionability | Learning artifact |
| PromotionDecision | id, learningId, promotedToPlaybookId, promotedBy | Governance for playbook promotion |
| ModelFeedback | id, agentId, prediction, actual, timestamp | Model/agent improvement |

## Event Schema

All events should include:

| Field | Purpose |
|---|---|
| eventId | Unique event identity |
| eventType | Domain event type |
| schemaVersion | Event schema evolution |
| tenantId | Tenant isolation |
| workspaceId | Workspace isolation |
| actor | User/service/agent identity |
| actorType | Human, system, agent, connector |
| correlationId | Trace linkage |
| causationId | Causal linkage |
| idempotencyKey | Duplicate prevention |
| occurredAt | Event time |
| sourceService | Producing service |
| externalRefs | External provider/object IDs |
| policySnapshotId | Policy context |
| consentSnapshotId | Consent context |
| piiClassification | Data sensitivity |
| payloadSchema | Payload schema reference |
| payload | Event-specific data |

## Consent Proof Storage

Consent proof must include:

- Source form or URL.
- Timestamp.
- Consent text shown.
- Policy version.
- Region.
- IP/user-agent where allowed and useful.
- Consent method.
- Purpose.
- Channel.
- Revocation timestamp and reason where revoked.

## Suppression Enforcement

Suppression must be checked before:

- Email send.
- SMS send.
- Ad audience sync.
- CRM export.
- Retargeting audience creation.
- External enrichment.
- Any channel-specific contact action.
