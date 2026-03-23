# Extension Point Catalog

**Document Type**: Normative Reference  
**Authority Level**: 3 — Normative Reference  
**Version**: 1.0.0 | **Status**: Active | **Date**: 2026-01-19  
**Owner**: AppPlatform Architecture Council  
**Canonical Path**: `products/app-platform/docs/EXTENSION_POINT_CATALOG.md`

---

## Purpose

This catalog enumerates every officially supported extension point in the AppPlatform Kernel (K-01 through K-19). For each extension point, it specifies:

- **Interface** — the Java/TypeScript type that domain packs must implement
- **Pack Type** — which Domain Pack tier may register it (T1/T2/T3)
- **Stability** — `STABLE` (no breaking changes without 2-version notice) | `PROVISIONAL` (may change in next minor) | `EXPERIMENTAL` (use at own risk)
- **Consumers** — which kernel modules call/use the extension
- **Sandbox Constraints** — resource limits and capability restrictions

Domain packs **must not** call any non-listed kernel internal API. All permitted kernel interactions happen through registered extension points or the K-02 Query Gateway.

---

## Table of Contents

1. [K-01 IAM Extension Points](#1-k-01-iam)
2. [K-02 Configuration Engine Extension Points](#2-k-02-configuration-engine)
3. [K-03 Rules Engine Extension Points](#3-k-03-rules-engine)
4. [K-04 Plugin Runtime Extension Points](#4-k-04-plugin-runtime)
5. [K-05 Event Bus Extension Points](#5-k-05-event-bus)
6. [K-06 Observability Extension Points](#6-k-06-observability)
7. [K-07 Audit Framework Extension Points](#7-k-07-audit-framework)
8. [K-08 Data Governance Extension Points](#8-k-08-data-governance)
9. [K-09 AI Governance Extension Points](#9-k-09-ai-governance)
10. [K-10 Deployment Abstraction Extension Points](#10-k-10-deployment-abstraction)
11. [K-11 API Gateway Extension Points](#11-k-11-api-gateway)
12. [K-12 Platform SDK Extension Points](#12-k-12-platform-sdk)
13. [K-13 Admin Portal Extension Points](#13-k-13-admin-portal)
14. [K-14 Secrets Management Extension Points](#14-k-14-secrets-management)
15. [K-15 Multi-Calendar Service Extension Points](#15-k-15-multi-calendar-service)
16. [K-16 Ledger Framework Extension Points](#16-k-16-ledger-framework)
17. [K-17 Distributed Transaction Coordinator Extension Points](#17-k-17-distributed-transaction-coordinator)
18. [K-18 Resilience Patterns Extension Points](#18-k-18-resilience-patterns)
19. [K-19 DLQ Management Extension Points](#19-k-19-dlq-management)
20. [Extension Point Stability Summary](#20-stability-summary)

---

## 1. K-01 IAM

### EP-K01-001: Authentication Provider

| Field           | Value                                                                                                                          |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| **Interface**   | `com.ghatana.core.iam.AuthenticationProvider`                                                                                  |
| **Pack Type**   | T1 (Jurisdiction Config Pack)                                                                                                  |
| **Stability**   | STABLE                                                                                                                         |
| **Consumers**   | K-01 IAM kernel                                                                                                                |
| **Description** | Register additional authentication methods (e.g., FIDO2, government eID, biometric) alongside the built-in OIDC/SAML provider. |

```java
public interface AuthenticationProvider {
  String getId();  // unique e.g. "np.gov.eid"
  Promise<AuthResult> authenticate(AuthRequest request, HandlerContext ctx);
  boolean supports(String method);
}
```

### EP-K01-002: Authorization Policy Evaluator

| Field           | Value                                                                                                                                                |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.iam.AuthorizationPolicy`                                                                                                           |
| **Pack Type**   | T2 (Rego Rule Pack)                                                                                                                                  |
| **Stability**   | STABLE                                                                                                                                               |
| **Consumers**   | K-01, K-11 API Gateway                                                                                                                               |
| **Description** | Provide jurisdiction-specific RBAC/ABAC rule extensions evaluated by OPA. The generic RBAC engine calls this after evaluating kernel-built-in roles. |

### EP-K01-003: National ID Scheme Validator

| Field           | Value                                                                                                     |
| --------------- | --------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.iam.NationalIdSchemeValidator`                                                          |
| **Pack Type**   | T1                                                                                                        |
| **Stability**   | STABLE                                                                                                    |
| **Consumers**   | K-01 KYC integration                                                                                      |
| **Description** | Validate jurisdiction-specific national ID formats (e.g., Nepal Citizenship Number checksum, PAN number). |

```java
public interface NationalIdSchemeValidator {
  String getSchemeId();       // e.g. "NP_CITIZENSHIP"
  boolean validate(String id);
  String normalize(String id);
}
```

---

## 2. K-02 Configuration Engine

### EP-K02-001: Config Pack Schema Extension

| Field           | Value                                                                                                                                                   |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | JSON Schema (via T1 pack)                                                                                                                               |
| **Pack Type**   | T1                                                                                                                                                      |
| **Stability**   | STABLE                                                                                                                                                  |
| **Consumers**   | K-02 schema validator                                                                                                                                   |
| **Description** | Domain packs register their own JSON Schema definitions for config payloads. K-02 validates config packs against registered schemas at activation time. |

### EP-K02-002: Config Resolution Interceptor

| Field           | Value                                                                                                    |
| --------------- | -------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.config.ConfigResolutionInterceptor`                                                    |
| **Pack Type**   | T1                                                                                                       |
| **Stability**   | PROVISIONAL                                                                                              |
| **Consumers**   | K-02 resolution pipeline                                                                                 |
| **Description** | Hook into the 5-level config resolution pipeline to apply domain-specific overrides or merge strategies. |

```java
public interface ConfigResolutionInterceptor {
  int getOrder();
  JsonNode intercept(ConfigResolutionContext ctx, JsonNode resolved);
}
```

---

## 3. K-03 Rules Engine

### EP-K03-001: T2 Rule Pack (Rego DSL)

| Field           | Value                                                                                                                                                               |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | OPA Rego Policy Bundle                                                                                                                                              |
| **Pack Type**   | T2 (Signed Rule Pack)                                                                                                                                               |
| **Stability**   | STABLE                                                                                                                                                              |
| **Consumers**   | K-03 evaluation engine                                                                                                                                              |
| **Description** | The primary extension point. Domain packs deliver jurisdiction-specific business rules as Rego policies. Evaluated in a sandboxed OPA runtime with no external I/O. |
| **Sandbox**     | No network/filesystem access. Memory: 256 MB. Timeout: 5s per evaluation.                                                                                           |

### EP-K03-002: Custom Built-in Function

| Field           | Value                                                                                                                                          |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.rules.RulesBuiltIn`                                                                                                          |
| **Pack Type**   | T3 (Signed Executable)                                                                                                                         |
| **Stability**   | PROVISIONAL                                                                                                                                    |
| **Consumers**   | K-03 Rego runtime                                                                                                                              |
| **Description** | Register custom built-in functions callable from Rego (e.g., complex financial math, external lookup). Runs in T3 sandbox with restricted I/O. |

```java
public interface RulesBuiltIn {
  String getName();           // e.g. "ghatana.capital_markets.irr"
  JsonNode evaluate(List<JsonNode> args);
}
```

---

## 4. K-04 Plugin Runtime

### EP-K04-001: Plugin Lifecycle Hook

| Field           | Value                                                                                                                      |
| --------------- | -------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.plugin.PluginLifecycleHook`                                                                              |
| **Pack Type**   | T3                                                                                                                         |
| **Stability**   | STABLE                                                                                                                     |
| **Consumers**   | K-04 plugin lifecycle manager                                                                                              |
| **Description** | Receive callbacks at plugin start, stop, upgrade, and health-check phases for orderly initialization and resource cleanup. |

```java
public interface PluginLifecycleHook {
  Promise<Void> onStart(PluginContext ctx);
  Promise<Void> onStop(PluginContext ctx);
  Promise<HealthStatus> onHealthCheck(PluginContext ctx);
}
```

### EP-K04-002: Permission Scope Declaration

| Field           | Value                                                                                                                                                      |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `DomainManifest.requiredPermissions[]` (declarative)                                                                                                       |
| **Pack Type**   | T3                                                                                                                                                         |
| **Stability**   | STABLE                                                                                                                                                     |
| **Consumers**   | K-04 sandbox provisioner                                                                                                                                   |
| **Description** | Declarative list of capability permissions the plugin requires (e.g., `k16:ledger:read`, `k05:publish:own`). The runtime grants only declared permissions. |

---

## 5. K-05 Event Bus

### EP-K05-001: Event Publish Interceptor

| Field           | Value                                                                                                                                                                                      |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Interface**   | `com.ghatana.core.events.EventPublishInterceptor`                                                                                                                                          |
| **Pack Type**   | T1                                                                                                                                                                                         |
| **Stability**   | PROVISIONAL                                                                                                                                                                                |
| **Consumers**   | K-05 publish pipeline                                                                                                                                                                      |
| **Description** | Hook applied just before an event is committed to the store. Can enrich the event (e.g., add `calendar_date` via K-15) or reject it. The K-15 calendar enricher itself is registered here. |

```java
public interface EventPublishInterceptor {
  int getOrder();
  Promise<EventEnvelope<?>> intercept(EventEnvelope<?> event, HandlerContext ctx);
}
```

### EP-K05-002: Custom Serialization Codec

| Field           | Value                                                                                                                                 |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.events.EventCodec`                                                                                                  |
| **Pack Type**   | T3                                                                                                                                    |
| **Stability**   | EXPERIMENTAL                                                                                                                          |
| **Consumers**   | K-05 serialization layer                                                                                                              |
| **Description** | Replace JSON serialization with Avro/Protobuf/CBOR for specific event types. Must be registered alongside a schema evolution adapter. |

---

## 6. K-06 Observability

### EP-K06-001: Custom Metric Collector

| Field           | Value                                                                                                                   |
| --------------- | ----------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `io.micrometer.core.instrument.MeterBinder`                                                                             |
| **Pack Type**   | T3                                                                                                                      |
| **Stability**   | STABLE                                                                                                                  |
| **Consumers**   | K-06 Micrometer registry                                                                                                |
| **Description** | Register domain-specific metrics (e.g., `orders.pending`, `margin.calls.active`) via standard Micrometer `MeterBinder`. |

### EP-K06-002: PII Detection Pattern

| Field           | Value                                                                                                                                                          |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | T1 Config Pack (`pii_patterns` key)                                                                                                                            |
| **Pack Type**   | T1                                                                                                                                                             |
| **Stability**   | STABLE                                                                                                                                                         |
| **Consumers**   | K-06 log masker                                                                                                                                                |
| **Description** | Register jurisdiction-specific regex patterns for PII detection in log fields (e.g., Nepal Citizenship Number, NRB account masks). Applied before log storage. |

### EP-K06-003: Custom Alert Rule

| Field           | Value                                                                                                                                 |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `AlertRuleDefinition` (YAML, T1 Config Pack)                                                                                          |
| **Pack Type**   | T1                                                                                                                                    |
| **Stability**   | STABLE                                                                                                                                |
| **Consumers**   | K-06 alerting engine                                                                                                                  |
| **Description** | Define domain-specific alert thresholds and routing policies. Alert rule YAML conforms to the `AlertRuleDefinition` schema from K-02. |

### EP-K06-004: SLO Definition

| Field           | Value                                                                                                                    |
| --------------- | ------------------------------------------------------------------------------------------------------------------------ |
| **Interface**   | `SloDefinition` (JSON, T1 Config Pack)                                                                                   |
| **Pack Type**   | T1                                                                                                                       |
| **Stability**   | STABLE                                                                                                                   |
| **Consumers**   | K-06 SLO engine                                                                                                          |
| **Description** | Define domain-level SLO targets (availability, latency, error rate) with error budget and burn-rate alert configuration. |

---

## 7. K-07 Audit Framework

### EP-K07-001: Audit Record Enricher

| Field           | Value                                                                                                                 |
| --------------- | --------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.audit.AuditEnricher`                                                                                |
| **Pack Type**   | T1                                                                                                                    |
| **Stability**   | STABLE                                                                                                                |
| **Consumers**   | K-07 audit pipeline                                                                                                   |
| **Description** | Attach jurisdiction-specific metadata to audit records (e.g., regulatory reference codes, audit classification tags). |

```java
public interface AuditEnricher {
  Map<String, String> enrich(AuditRecord record, AuditContext ctx);
}
```

### EP-K07-002: Audit Event Schema Extension

| Field           | Value                                                                                                                                                |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | JSON Schema (T1 Config Pack, `audit_schemas` key)                                                                                                    |
| **Pack Type**   | T1                                                                                                                                                   |
| **Stability**   | STABLE                                                                                                                                               |
| **Consumers**   | K-07 schema registry                                                                                                                                 |
| **Description** | Register custom audit event types beyond the kernel-standard set. Domain packs mapping compliance regulations to audit events register schemas here. |

---

## 8. K-08 Data Governance

### EP-K08-001: Data Classification Rule

| Field           | Value                                                                                                                                                                               |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | T2 Rule Pack (`data_classification` namespace)                                                                                                                                      |
| **Pack Type**   | T2                                                                                                                                                                                  |
| **Stability**   | STABLE                                                                                                                                                                              |
| **Consumers**   | K-08 classification engine                                                                                                                                                          |
| **Description** | Extend data classification taxonomy with domain-specific tags (e.g., `CAPITAL_MARKETS_TRADE_DATA`, `REGULATORY_EVIDENCE`). Rules determine retention, masking, and access policies. |

### EP-K08-002: Data Lineage Annotator

| Field           | Value                                                                                                                                     |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.governance.LineageAnnotator`                                                                                            |
| **Pack Type**   | T3                                                                                                                                        |
| **Stability**   | PROVISIONAL                                                                                                                               |
| **Consumers**   | K-08 lineage tracker                                                                                                                      |
| **Description** | Attach domain-specific lineage metadata to data flows (e.g., tag a trade record with its source feed and transformation steps for audit). |

---

## 9. K-09 AI Governance

### EP-K09-001: AI Model Registration

| Field           | Value                                                                                                                                                                            |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `ModelCard` (JSON, T1 Config Pack)                                                                                                                                               |
| **Pack Type**   | T1                                                                                                                                                                               |
| **Stability**   | PROVISIONAL                                                                                                                                                                      |
| **Consumers**   | K-09 model registry                                                                                                                                                              |
| **Description** | Register AI models (inferences, embeddings, classifiers) with their capability declarations, bias assessments, and approved use-cases. Models can only be invoked if registered. |

### EP-K09-002: Model Policy Validator

| Field           | Value                                                                                                                                              |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | T2 Rule Pack (`model_policy` namespace)                                                                                                            |
| **Pack Type**   | T2                                                                                                                                                 |
| **Stability**   | EXPERIMENTAL                                                                                                                                       |
| **Consumers**   | K-09 policy gate                                                                                                                                   |
| **Description** | Define OPA policies gating which models may be invoked in which contexts (e.g., "ML models scoring risk require SEBON approval for live trading"). |

### EP-K09-003: Embedding Model Provider

| Field           | Value                                                                                                                                                                                                                       |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.ai.EmbeddingModelProvider`                                                                                                                                                                                |
| **Pack Type**   | T3                                                                                                                                                                                                                          |
| **Stability**   | EXPERIMENTAL                                                                                                                                                                                                                |
| **Consumers**   | K-09 Platform Embedding Service (FR10)                                                                                                                                                                                      |
| **Description** | Register a custom embedding model (e.g., domain-specific financial text encoder) with the K-09 embedding service. The provider receives text inputs and returns fixed-dimension float vectors stored in the pgvector index. |

```java
public interface EmbeddingModelProvider {
  String getModelId();                         // must match a registered ModelCard
  int getDimension();                          // e.g. 768, 1536
  Promise<float[]> embed(String text, HandlerContext ctx);
  Promise<List<float[]>> embedBatch(List<String> texts, HandlerContext ctx);
}
```

### EP-K09-004: Fine-Tuning Dataset Provider

| Field           | Value                                                                                                                                                                                                                 |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.ai.FineTuningDatasetProvider`                                                                                                                                                                       |
| **Pack Type**   | T2 (data declaration) + T3 (data extraction logic)                                                                                                                                                                    |
| **Stability**   | EXPERIMENTAL                                                                                                                                                                                                          |
| **Consumers**   | K-09 HITL Fine-Tuning Pipeline (FR12)                                                                                                                                                                                 |
| **Description** | Register a domain-specific labeled dataset provider for feeding K-09's fine-tuning pipeline. The provider applies PII masking (via K-08) before exporting samples, ensuring compliance with data governance policies. |

```java
public interface FineTuningDatasetProvider {
  String getTargetModelId();          // model this dataset trains
  String getDatasetVersion();         // semantic version
  Promise<List<LabeledSample>> export(
    CalendarDate fromDate, CalendarDate toDate, HandlerContext ctx);
}
```

### EP-K09-005: Custom HITL Workflow

| Field           | Value                                                                                                                                                                                                |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.ai.HitlWorkflowProvider`                                                                                                                                                           |
| **Pack Type**   | T2                                                                                                                                                                                                   |
| **Stability**   | EXPERIMENTAL                                                                                                                                                                                         |
| **Consumers**   | K-09 HITL queue                                                                                                                                                                                      |
| **Description** | Register a domain-specific Human-in-the-Loop review workflow for low-confidence AI decisions. The workflow defines reviewer roles, escalation paths, SLA deadlines, and the feedback payload schema. |

```java
public interface HitlWorkflowProvider {
  String getWorkflowId();
  String getTargetModelId();
  RoleSpec getReviewerRole();            // K-01 RBAC role
  Duration getReviewSla();
  Promise<HitlDecision> onReview(HitlTask task, HandlerContext ctx);
}
```

### EP-K09-006: Model Serving Adapter

| Field           | Value                                                                                                                                                                                                                                                |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.ai.ModelServingAdapter`                                                                                                                                                                                                            |
| **Pack Type**   | T3                                                                                                                                                                                                                                                   |
| **Stability**   | PROVISIONAL                                                                                                                                                                                                                                          |
| **Consumers**   | K-09 Inference Router (FR14)                                                                                                                                                                                                                         |
| **Description** | Register an external or on-prem model serving backend (e.g., Triton Inference Server, ONNX Runtime, vLLM, Ollama) with the K-09 inference router. The router dispatches inference calls based on tenant config, jurisdiction, latency SLO, and cost. |

```java
public interface ModelServingAdapter {
  String getAdapterId();                     // e.g. "triton-gpu-cluster-1"
  List<String> getSupportedModelIds();        // from model registry
  InferenceCapabilities getCapabilities();    // GPU, batch size, latency SLO
  Promise<InferenceResult> infer(
    InferenceRequest request, HandlerContext ctx);
}
```

### EP-K09-007: Evaluation Metric Plugin

| Field           | Value                                                                                                                                                                                                                                                     |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.ai.EvaluationMetricPlugin`                                                                                                                                                                                                              |
| **Pack Type**   | T2                                                                                                                                                                                                                                                        |
| **Stability**   | EXPERIMENTAL                                                                                                                                                                                                                                              |
| **Consumers**   | K-09 model drift monitor, A/B testing engine (FR11)                                                                                                                                                                                                       |
| **Description** | Register a custom evaluation metric for domain-specific model performance tracking. The metric function receives a batch of `(prediction, ground_truth)` pairs and returns a scalar score used in drift detection and A/B statistical significance tests. |

```java
public interface EvaluationMetricPlugin {
  String getMetricId();                       // e.g. "financial-accuracy-weighted"
  String getTargetModelId();
  double compute(List<EvalSample> samples);   // returns scalar metric
  double getDriftThreshold();                 // alert if metric drops below this
}
```

---

## 10. K-10 Deployment Abstraction

### EP-K10-001: Deployment Target Adapter

| Field           | Value                                                                                                                                                           |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.deploy.DeploymentTargetAdapter`                                                                                                               |
| **Pack Type**   | T3                                                                                                                                                              |
| **Stability**   | STABLE                                                                                                                                                          |
| **Consumers**   | K-10 deployment orchestrator                                                                                                                                    |
| **Description** | Register a deployment target backend (e.g., Kubernetes, Nomad, AWS ECS, bare-metal). The kernel delegates pack deployment operations to the registered adapter. |

```java
public interface DeploymentTargetAdapter {
  String getTargetId(); // e.g. "kubernetes-eks-prod"
  Promise<DeploymentResult> deploy(PackDeploymentSpec spec, HandlerContext ctx);
  Promise<HealthStatus> healthCheck(String packId, String tenantId);
}
```

---

## 11. K-11 API Gateway

### EP-K11-001: Request Transformer

| Field           | Value                                                                                                                               |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.gateway.RequestTransformer`                                                                                       |
| **Pack Type**   | T1                                                                                                                                  |
| **Stability**   | STABLE                                                                                                                              |
| **Consumers**   | K-11 request pipeline                                                                                                               |
| **Description** | Apply domain-specific request transformations (e.g., inject jurisdiction context headers, normalize date format per tenant locale). |

### EP-K11-002: Rate Limit Override Rule

| Field           | Value                                                                                                                                                     |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | T1 Config Pack (`rate_limit_overrides` key)                                                                                                               |
| **Pack Type**   | T1                                                                                                                                                        |
| **Stability**   | STABLE                                                                                                                                                    |
| **Consumers**   | K-11 rate limiter                                                                                                                                         |
| **Description** | Define per-endpoint, per-tenant, or per-role rate limit overrides on top of the kernel defaults (e.g., higher throughput for exchange adapter endpoints). |

---

## 12. K-12 Platform SDK

### EP-K12-001: SDK Client Extension

| Field           | Value                                                                                                                                                                                            |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Interface**   | Published as a library artifact via the Pack Registry                                                                                                                                            |
| **Pack Type**   | T3                                                                                                                                                                                               |
| **Stability**   | STABLE                                                                                                                                                                                           |
| **Consumers**   | Domain pack developers                                                                                                                                                                           |
| **Description** | Domain packs may publish SDK extension libraries (e.g., a `capital-markets-sdk` wrapping K-05/K-16 operations) that other co-installed packs in the same tenant can depend on via Pack Registry. |

---

## 13. K-13 Admin Portal

### EP-K13-001: Custom Dashboard Widget

| Field           | Value                                                                                                                                                               |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `WidgetDefinition` (JSON, T1 Config Pack)                                                                                                                           |
| **Pack Type**   | T1                                                                                                                                                                  |
| **Stability**   | PROVISIONAL                                                                                                                                                         |
| **Consumers**   | K-13 portal renderer                                                                                                                                                |
| **Description** | Register domain-specific dashboard widgets (charts, grids, status panels) rendered in the Admin Portal. Widget data sources are read-only K-02 Query Gateway calls. |

### EP-K13-002: Admin Action Plugin

| Field           | Value                                                                                                                                                                              |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `AdminActionDefinition` (JSON + T3 handler)                                                                                                                                        |
| **Pack Type**   | T3                                                                                                                                                                                 |
| **Stability**   | EXPERIMENTAL                                                                                                                                                                       |
| **Consumers**   | K-13 action dispatcher                                                                                                                                                             |
| **Description** | Register operator-facing actions (e.g., "Force Settlement", "Manual Margin Override") that appear in the Admin Portal and execute via a T3 handler with maker-checker enforcement. |

---

## 14. K-14 Secrets Management

### EP-K14-001: Vault Backend Adapter

| Field           | Value                                                                                                                                                            |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.secrets.VaultAdapter`                                                                                                                          |
| **Pack Type**   | T3                                                                                                                                                               |
| **Stability**   | STABLE                                                                                                                                                           |
| **Consumers**   | K-14 secret store                                                                                                                                                |
| **Description** | Register a backend secrets store (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault, GCP Secret Manager). K-14 delegates all CRUD to the registered adapter. |

```java
public interface VaultAdapter {
  String getVaultId();
  Promise<SecretValue> getSecret(String path, String tenantId);
  Promise<Void> rotateSecret(String path, String tenantId);
}
```

### EP-K14-002: Key Derivation Scheme

| Field           | Value                                                                                                       |
| --------------- | ----------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.secrets.KeyDerivationScheme`                                                              |
| **Pack Type**   | T3                                                                                                          |
| **Stability**   | PROVISIONAL                                                                                                 |
| **Consumers**   | K-14 encryption service                                                                                     |
| **Description** | Register an alternative key derivation algorithm for tenant-scoped encryption keys (e.g., HSM-backed HKDF). |

---

## 15. K-15 Multi-Calendar Service

### EP-K15-001: Calendar Converter

| Field           | Value                                                                                                                                                                                                                          |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Interface**   | `com.ghatana.core.calendar.CalendarConverter`                                                                                                                                                                                  |
| **Pack Type**   | T1                                                                                                                                                                                                                             |
| **Stability**   | STABLE                                                                                                                                                                                                                         |
| **Consumers**   | K-15 `CalendarClient`                                                                                                                                                                                                          |
| **Description** | Register a converter for a new calendar system (e.g., Hijri, Indian National, Ethiopian). The converter maps dates to/from Julian Day Numbers. Once registered, the calendar is available as a `CalendarId` in `CalendarDate`. |

```java
public interface CalendarConverter {
  CalendarId getCalendarId();  // e.g. "hijri"
  int toJulianDayNumber(CalendarDateTime date);
  CalendarDateTime fromJulianDayNumber(int jdn);
}
```

### EP-K15-002: Holiday Data Provider

| Field           | Value                                                                                                                                                                                                                   |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | T1 Config Pack (`holiday_calendars` key)                                                                                                                                                                                |
| **Pack Type**   | T1                                                                                                                                                                                                                      |
| **Stability**   | STABLE                                                                                                                                                                                                                  |
| **Consumers**   | K-15 holiday engine                                                                                                                                                                                                     |
| **Description** | Provide jurisdiction-specific public holiday lists and weekend rules used for business day calculations and T+n settlement date computation. Format: iCalendar (RFC 5545) or the platform's native holiday JSON schema. |

---

## 16. K-16 Ledger Framework

### EP-K16-001: Chart of Accounts Schema

| Field           | Value                                                                                                                                                                                       |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | T1 Config Pack (`chart_of_accounts` key)                                                                                                                                                    |
| **Pack Type**   | T1                                                                                                                                                                                          |
| **Stability**   | STABLE                                                                                                                                                                                      |
| **Consumers**   | K-16 account factory                                                                                                                                                                        |
| **Description** | Define the hierarchical account taxonomy (Assets, Liabilities, Equity, Revenue, Expense) with account type codes, currency constraints, and precision rules applicable to the jurisdiction. |

### EP-K16-002: Reconciliation Format Adapter

| Field           | Value                                                                                                                                                                 |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.ledger.ReconciliationAdapter`                                                                                                                       |
| **Pack Type**   | T3                                                                                                                                                                    |
| **Stability**   | STABLE                                                                                                                                                                |
| **Consumers**   | K-16 reconciliation engine                                                                                                                                            |
| **Description** | Parse external account statements from institution-specific formats (CSV, MT940, CDSC XML) into the Platform's `ExternalStatement` type for automated reconciliation. |

```java
public interface ReconciliationAdapter {
  String getFormatId();  // e.g. "cdsc-csv-v2"
  List<ExternalStatementEntry> parse(InputStream data);
}
```

### EP-K16-003: Rounding Rule Override

| Field           | Value                                                                                                                      |
| --------------- | -------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | T1 Config Pack (`currency_precision` key)                                                                                  |
| **Pack Type**   | T1                                                                                                                         |
| **Stability**   | STABLE                                                                                                                     |
| **Consumers**   | K-16 `Money` value object                                                                                                  |
| **Description** | Override default precision and rounding mode per currency code (e.g., NPR: 2dp HALF_EVEN, crypto-token XYZ: 8dp, HALF_UP). |

---

## 17. K-17 Distributed Transaction Coordinator

### EP-K17-001: Saga Compensating Handler

| Field           | Value                                                                                                                                                     |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.saga.CompensatingHandler`                                                                                                               |
| **Pack Type**   | T3                                                                                                                                                        |
| **Stability**   | STABLE                                                                                                                                                    |
| **Consumers**   | K-17 saga orchestrator                                                                                                                                    |
| **Description** | Register domain-specific compensation logic for saga steps. Called when a saga rolls back. Must be idempotent and must emit a compensation event on K-05. |

```java
public interface CompensatingHandler<T> {
  String getSagaStepId();
  Promise<CompensationResult> compensate(T originalPayload, HandlerContext ctx);
}
```

---

## 18. K-18 Resilience Patterns

### EP-K18-001: Circuit Breaker Configuration

| Field           | Value                                                                                                                                                                                 |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | T1 Config Pack (`circuit_breakers` key)                                                                                                                                               |
| **Pack Type**   | T1                                                                                                                                                                                    |
| **Stability**   | STABLE                                                                                                                                                                                |
| **Consumers**   | K-18 resilience engine                                                                                                                                                                |
| **Description** | Configure per-dependency circuit breaker thresholds, half-open probe intervals, degraded fallback behavior, and burn-alert thresholds for domain-specific inter-service dependencies. |

### EP-K18-002: Fallback Handler

| Field           | Value                                                                                                                                                            |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.resilience.FallbackHandler`                                                                                                                    |
| **Pack Type**   | T3                                                                                                                                                               |
| **Stability**   | PROVISIONAL                                                                                                                                                      |
| **Consumers**   | K-18 degraded-mode dispatcher                                                                                                                                    |
| **Description** | Provide domain-specific degraded-mode behavior when a dependency is unavailable. Must mark the result with `degraded: true` and emit a `DegradedOperationEvent`. |

---

## 19. K-19 DLQ Management

### EP-K19-001: DLQ Routing Rule

| Field           | Value                                                                                                                                                                                    |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | T2 Rule Pack (`dlq_routing` namespace)                                                                                                                                                   |
| **Pack Type**   | T2                                                                                                                                                                                       |
| **Stability**   | STABLE                                                                                                                                                                                   |
| **Consumers**   | K-19 DLQ router                                                                                                                                                                          |
| **Description** | Define routing rules that determine which DLQ lane events land in (e.g., `COMPLIANCE_CRITICAL`, `SETTLEMENT_CRITICAL`, `NORMAL`) based on event type and domain. Affects alert priority. |

### EP-K19-002: DLQ Reprocessing Handler

| Field           | Value                                                                                                                                                                               |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Interface**   | `com.ghatana.core.dlq.DlqReprocessingHandler`                                                                                                                                       |
| **Pack Type**   | T3                                                                                                                                                                                  |
| **Stability**   | STABLE                                                                                                                                                                              |
| **Consumers**   | K-19 manual replay pipeline                                                                                                                                                         |
| **Description** | Domain packs may register a handler to inspect and fix events before operator-triggered DLQ replay. Common use: transform a structurally invalid event after a bug fix is deployed. |

```java
public interface DlqReprocessingHandler {
  String getEventType();
  Promise<EventEnvelope<?>> prepare(DeadLetterEntry entry, HandlerContext ctx);
}
```

---

## 20. Stability Summary

| Extension Point ID                       | Module | Stability    |
| ---------------------------------------- | ------ | ------------ |
| EP-K01-001 Authentication Provider       | K-01   | STABLE       |
| EP-K01-002 Authorization Policy          | K-01   | STABLE       |
| EP-K01-003 National ID Validator         | K-01   | STABLE       |
| EP-K02-001 Config Pack Schema            | K-02   | STABLE       |
| EP-K02-002 Config Resolution Interceptor | K-02   | PROVISIONAL  |
| EP-K03-001 T2 Rule Pack                  | K-03   | STABLE       |
| EP-K03-002 Custom Built-in Function      | K-03   | PROVISIONAL  |
| EP-K04-001 Plugin Lifecycle Hook         | K-04   | STABLE       |
| EP-K04-002 Permission Scope Declaration  | K-04   | STABLE       |
| EP-K05-001 Event Publish Interceptor     | K-05   | PROVISIONAL  |
| EP-K05-002 Custom Serialization Codec    | K-05   | EXPERIMENTAL |
| EP-K06-001 Custom Metric Collector       | K-06   | STABLE       |
| EP-K06-002 PII Detection Pattern         | K-06   | STABLE       |
| EP-K06-003 Custom Alert Rule             | K-06   | STABLE       |
| EP-K06-004 SLO Definition                | K-06   | STABLE       |
| EP-K07-001 Audit Record Enricher         | K-07   | STABLE       |
| EP-K07-002 Audit Event Schema Extension  | K-07   | STABLE       |
| EP-K08-001 Data Classification Rule      | K-08   | STABLE       |
| EP-K08-002 Data Lineage Annotator        | K-08   | PROVISIONAL  |
| EP-K09-001 AI Model Registration         | K-09   | PROVISIONAL  |
| EP-K09-002 Model Policy Validator        | K-09   | EXPERIMENTAL |
| EP-K09-003 Embedding Model Provider      | K-09   | EXPERIMENTAL |
| EP-K09-004 Fine-Tuning Dataset Provider  | K-09   | EXPERIMENTAL |
| EP-K09-005 Custom HITL Workflow          | K-09   | EXPERIMENTAL |
| EP-K09-006 Model Serving Adapter         | K-09   | PROVISIONAL  |
| EP-K09-007 Evaluation Metric Plugin      | K-09   | EXPERIMENTAL |
| EP-K10-001 Deployment Target Adapter     | K-10   | STABLE       |
| EP-K11-001 Request Transformer           | K-11   | STABLE       |
| EP-K11-002 Rate Limit Override Rule      | K-11   | STABLE       |
| EP-K12-001 SDK Client Extension          | K-12   | STABLE       |
| EP-K13-001 Custom Dashboard Widget       | K-13   | PROVISIONAL  |
| EP-K13-002 Admin Action Plugin           | K-13   | EXPERIMENTAL |
| EP-K14-001 Vault Backend Adapter         | K-14   | STABLE       |
| EP-K14-002 Key Derivation Scheme         | K-14   | PROVISIONAL  |
| EP-K15-001 Calendar Converter            | K-15   | STABLE       |
| EP-K15-002 Holiday Data Provider         | K-15   | STABLE       |
| EP-K16-001 Chart of Accounts Schema      | K-16   | STABLE       |
| EP-K16-002 Reconciliation Format Adapter | K-16   | STABLE       |
| EP-K16-003 Rounding Rule Override        | K-16   | STABLE       |
| EP-K17-001 Saga Compensating Handler     | K-17   | STABLE       |
| EP-K18-001 Circuit Breaker Configuration | K-18   | STABLE       |
| EP-K18-002 Fallback Handler              | K-18   | PROVISIONAL  |
| EP-K19-001 DLQ Routing Rule              | K-19   | STABLE       |
| EP-K19-002 DLQ Reprocessing Handler      | K-19   | STABLE       |

**Stability Definitions:**

- **STABLE** — No breaking changes in the same major platform version. Breaking changes announced 2 minor versions in advance.
- **PROVISIONAL** — Interface is functional and tested but may see breaking changes in the next minor version. Use in production with awareness.
- **EXPERIMENTAL** — Interface is available for evaluation. May change or be removed without prior notice. Not recommended for production.
