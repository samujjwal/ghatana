# ADR-023: Data Cloud Sovereign / Air-Gapped Profile — Sovereign RAG, Model Isolation, and Tenant Consent

**Status:** Accepted  
**Date:** 2026-04-28  
**Decision Makers:** Data Cloud Platform Team  
**Phase:** 1 — Data Fabric Foundation  

## Context

Data Cloud tenants operate under diverse regulatory regimes (GDPR, HIPAA, SOC2, CCPA). A "sovereign profile" ensures that RAG retrieval paths, model/provider exposure, and data residency are governed by tenant-level policy rather than platform defaults. Without this, cross-border data flows, uncontrolled external LLM calls, and un-audited RAG retrieval paths risk compliance violations.

## Decision

### 1. Sovereign Profile Declaration

Every tenant may declare a `sovereignProfile` at provisioning time or via `PUT /api/v1/settings/tenant-config`.

```json
{
  "tenantId": "acme-eu",
  "sovereignProfile": {
    "dataResidency": "eu-west-1",
    "externalModelAllowed": false,
    "allowedModels": ["local-llama3", "local-mistral"],
    "allowedProviders": [],
    "ragPolicy": {
      "piiRedactionRequired": true,
      "retentionPolicyEnforced": true,
      "crossCollectionRetrieval": false
    },
    "complianceFrameworks": ["GDPR"]
  }
}
```

### 2. Policy Enforcement Points

| Enforcement Point | Rule | Endpoint |
|---|---|---|
| **RAG Retrieval** | Before semantic search or context retrieval, `POST /api/v1/context/:collection/rag-policy-check` validates PII, retention, and sovereignty policies. | `POST /api/v1/context/{collection}/rag-policy-check` |
| **Model/Provider Exposure** | Every external LLM call is gated by `externalModelAllowed` and `allowedProviders`. A consent record (`tenantId`, `model`, `purpose`, `timestamp`) is logged to `dc_model_consent_log`. | Internal gate in `AiAssistHandler` and `VoiceGatewayHandler` |
| **Cross-Border Flow** | Embeddings, context snapshots, and query results may not leave the declared `dataResidency` region unless `crossBorderAllowed: true`. | Enforced at storage layer via `RegionLock` |
| **PII Redaction** | Collections flagged with PII fields (detected by `CompliancePlugin`) require redaction before RAG retrieval. | `CollectionContextHandler.handleRagPolicyCheck()` |

### 3. Consent and Audit Model

All external model/provider invocations require **explicit tenant consent** recorded in the `dc_model_consent_log` collection:

- `tenantId`, `model`, `provider`, `purpose`, `grantedAt`, `revokedAt`, `grantedBy`
- Consent is checked at invocation time; if revoked or never granted, the call is blocked with `SOVEREIGN_POLICY_VIOLATION`.
- Consent may be revoked via `DELETE /api/v1/settings/model-consent/:consentId`.

### 4. Air-Gapped Deployment

In fully air-gapped deployments:

- `externalModelAllowed` is hardcoded to `false` at platform initialization.
- Only local models (via `platform:java:ai-integration` local inference bridge) are available.
- No outbound network calls for model inference, embedding generation, or telemetry.
- All audit logs are written to local append-only storage; no external log shipping.

## Consequences

- **Positive**: Tenants in regulated industries (healthcare, finance, EU entities) can adopt Data Cloud with confidence that data residency and model exposure are governed.
- **Positive**: RAG retrieval paths are gated by policy, preventing accidental PII leakage into embeddings or model prompts.
- **Negative**: External model features (e.g., GPT-4 for NLQ, voice classification) are unavailable for tenants with `externalModelAllowed: false`.
- **Negative**: Additional latency on every RAG retrieval path due to policy check.

## Related

- `CollectionContextHandler.handleRagPolicyCheck()` — P1.6 RAG policy enforcement
- `AiAssistHandler` — model consent gate before every external LLM call
- `CompliancePlugin` — PII detection and retention policy enforcement
- `docs/adr/ADR-021-data-cloud-autonomy.md` — autonomy levels for human-in-the-loop on sensitive model calls
