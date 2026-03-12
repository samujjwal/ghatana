# LOW-LEVEL DESIGN: K-09 AI GOVERNANCE

**Module**: K-09 AI Governance  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Core Team

> **Implementation alignment**: Per [../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md), Siddhanta reuses Ghatana `ai-registry`, `ai-inference-service`, `feature-store-ingest`, and `platform/java/ai-integration` as the baseline AI platform components.

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The AI Governance module provides **centralized model registry, prompt versioning, explainability, and human-in-the-loop (HITL) controls** for all AI/ML capabilities across Project Siddhanta.

**Core Responsibilities**:

- Model registry with versioning and metadata
- Prompt template versioning and management
- Explainability tracking for AI decisions
- Human-in-the-loop (HITL) override mechanism
- Model drift detection and alerting
- Instant rollback to previous model versions
- Dual-calendar timestamping for AI decisions
- A/B testing framework for model comparison
- Bias detection and fairness metrics

**Invariants**:

1. All AI models MUST be registered before use
2. All AI decisions MUST be explainable and auditable
3. HITL overrides MUST take precedence over AI decisions
4. Model versions MUST be immutable
5. Drift detection MUST trigger alerts

### 1.2 Explicit Non-Goals

- ❌ Model training orchestration design (handled by Ghatana AI platform services and domain-specific training pipelines)
- ❌ Feature engineering pipelines (domain-specific)
- ❌ Real-time model serving optimization (use dedicated inference servers)

### 1.3 Dependencies

| Dependency           | Purpose                                               | Readiness Gate        |
| -------------------- | ----------------------------------------------------- | --------------------- |
| K-02 Config Engine   | Model configuration storage                           | K-02 stable           |
| K-05 Event Bus       | AI decision events                                    | K-05 stable           |
| K-07 Audit Framework | AI decision audit trail                               | K-07 stable           |
| Ghatana AI Platform  | Registry, inference, and feature integration baseline | AI platform available |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/ai/models/register
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "model_id": "fraud_detection_v2",
  "model_type": "CLASSIFICATION",
  "framework": "SKLEARN",
  "version": "2.1.0",
  "artifact_url": "s3://models/fraud_detection_v2.pkl",
  "metadata": {
    "accuracy": 0.95,
    "precision": 0.93,
    "recall": 0.94,
    "training_date": "2025-03-01T00:00:00Z",
    "training_dataset_size": 1000000
  },
  "explainability_method": "SHAP",
  "registered_by": "ml_engineer_1"
}

Response 201:
{
  "model_id": "fraud_detection_v2",
  "version": "2.1.0",
  "status": "REGISTERED",
  "registered_at": "2025-03-02T10:30:00Z"
}
```

```yaml
POST /api/v1/ai/predict
Authorization: Bearer {service_token}

Request:
{
  "model_id": "fraud_detection_v2",
  "input": {
    "transaction_amount": 50000,
    "merchant_category": "ELECTRONICS",
    "user_history_score": 0.8
  },
  "require_explanation": true,
  "trace_id": "abc-123"
}

Response 200:
{
  "prediction": {
    "class": "FRAUD",
    "confidence": 0.87
  },
  "explanation": {
    "method": "SHAP",
    "feature_importance": {
      "transaction_amount": 0.45,
      "user_history_score": 0.35,
      "merchant_category": 0.20
    }
  },
  "model_version": "2.1.0",
  "decision_id": "dec_7a8b9c0d",
  "timestamp": "2025-03-02T10:30:00Z"
}
```

```yaml
POST /api/v1/ai/decisions/{decision_id}/override
Authorization: Bearer {admin_token}

Request:
{
  "override_value": "NOT_FRAUD",
  "reason": "Customer verified via phone call",
  "overridden_by": "user_456",
  "evidence": {
    "call_recording_id": "rec_123",
    "verification_method": "PHONE_OTP"
  }
}

Response 200:
{
  "decision_id": "dec_7a8b9c0d",
  "original_prediction": "FRAUD",
  "override_value": "NOT_FRAUD",
  "overridden_at": "2025-03-02T10:35:00Z"
}
```

```yaml
POST /api/v1/ai/prompts/register
Authorization: Bearer {admin_token}

Request:
{
  "prompt_id": "order_summary_generator",
  "version": "1.2.0",
  "template": "Generate a summary for order {{order_id}} with {{quantity}} shares of {{instrument_id}} at price {{price}}",
  "model_id": "local-mistral-7b",
  "parameters": {
    "temperature": 0.7,
    "max_tokens": 150
  },
  "registered_by": "product_manager_1"
}

Response 201:
{
  "prompt_id": "order_summary_generator",
  "version": "1.2.0",
  "status": "REGISTERED"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";

package siddhanta.ai.v1;

service AIGovernanceService {
  rpc RegisterModel(RegisterModelRequest) returns (RegisterModelResponse);
  rpc Predict(PredictRequest) returns (PredictResponse);
  rpc OverrideDecision(OverrideDecisionRequest) returns (OverrideDecisionResponse);
  rpc DetectDrift(DetectDriftRequest) returns (DetectDriftResponse);
  rpc RollbackModel(RollbackModelRequest) returns (RollbackModelResponse);
}

message RegisterModelRequest {
  string model_id = 1;
  ModelType model_type = 2;
  string framework = 3;
  string version = 4;
  string artifact_url = 5;
  google.protobuf.Struct metadata = 6;
  string explainability_method = 7;
  string registered_by = 8;
}

enum ModelType {
  CLASSIFICATION = 0;
  REGRESSION = 1;
  CLUSTERING = 2;
  NLP = 3;
  RECOMMENDATION = 4;
}

message PredictRequest {
  string model_id = 1;
  google.protobuf.Struct input = 2;
  bool require_explanation = 3;
  string trace_id = 4;
}

message PredictResponse {
  google.protobuf.Struct prediction = 1;
  Explanation explanation = 2;
  string model_version = 3;
  string decision_id = 4;
  google.protobuf.Timestamp timestamp = 5;
}

message Explanation {
  string method = 1;
  google.protobuf.Struct feature_importance = 2;
  repeated string reasoning_steps = 3;
}

message OverrideDecisionRequest {
  string decision_id = 1;
  google.protobuf.Value override_value = 2;
  string reason = 3;
  string overridden_by = 4;
  google.protobuf.Struct evidence = 5;
}
```

### 2.3 SDK Method Signatures

```typescript
interface AIGovernanceClient {
  /**
   * Register AI model
   */
  registerModel(model: ModelRegistration): Promise<ModelInfo>;

  /**
   * Make prediction with model
   */
  predict<I, O>(
    modelId: string,
    input: I,
    options?: PredictOptions,
  ): Promise<PredictionResult<O>>;

  /**
   * Override AI decision with human judgment
   */
  overrideDecision(
    decisionId: string,
    override: DecisionOverride,
  ): Promise<void>;

  /**
   * Detect model drift
   */
  detectDrift(modelId: string, referenceData: unknown[]): Promise<DriftReport>;

  /**
   * Rollback to previous model version
   */
  rollbackModel(modelId: string, targetVersion: string): Promise<void>;

  /**
   * Register prompt template
   */
  registerPrompt(prompt: PromptTemplate): Promise<PromptInfo>;

  /**
   * Execute prompt with variables
   */
  executePrompt<T>(
    promptId: string,
    variables: Record<string, unknown>,
  ): Promise<PromptResult<T>>;
}

interface ModelRegistration {
  modelId: string;
  modelType:
    | "CLASSIFICATION"
    | "REGRESSION"
    | "CLUSTERING"
    | "NLP"
    | "RECOMMENDATION";
  framework: string;
  version: string;
  artifactUrl: string;
  metadata: ModelMetadata;
  explainabilityMethod: "SHAP" | "LIME" | "ATTENTION" | "RULE_BASED";
  registeredBy: string;
}

interface PredictOptions {
  requireExplanation?: boolean;
  traceId?: string;
  timeout?: number;
}

interface PredictionResult<T> {
  prediction: T;
  explanation?: Explanation;
  modelVersion: string;
  decisionId: string;
  timestamp: Date;
}

interface DecisionOverride {
  overrideValue: unknown;
  reason: string;
  overriddenBy: string;
  evidence?: Record<string, unknown>;
}

interface DriftReport {
  modelId: string;
  driftDetected: boolean;
  driftScore: number;
  driftedFeatures: string[];
  recommendation: "RETRAIN" | "MONITOR" | "OK";
}
```

### 2.4 Error Model

| Error Code | HTTP Status | Retryable | Description                  |
| ---------- | ----------- | --------- | ---------------------------- |
| AI_E001    | 404         | No        | Model not found              |
| AI_E002    | 400         | No        | Invalid input schema         |
| AI_E003    | 500         | Yes       | Model inference timeout      |
| AI_E004    | 500         | Yes       | Model inference failed       |
| AI_E005    | 409         | No        | Model version conflict       |
| AI_E006    | 400         | No        | Explainability not available |
| AI_E007    | 403         | No        | HITL override not authorized |
| AI_E008    | 500         | No        | Drift detection failed       |

---

## 3. DATA MODEL

### 3.1 Event Schemas

#### AIDecisionMadeEvent v1.0.0

> **K-05 Envelope Compliant** — all events use the standard envelope from LLD_K05_EVENT_BUS §3.1.

```json
{
  "event_id": "uuid",
  "event_type": "AIDecisionMade",
  "event_version": "1.0.0",
  "aggregate_id": "dec_7a8b9c0d",
  "aggregate_type": "AIDecision",
  "sequence_number": 1,
  "timestamp_bs": "2081-11-17T10:30:00",
  "timestamp_gregorian": "2025-03-02T10:30:00Z",
  "metadata": {
    "trace_id": "abc-123",
    "causation_id": "predict-cmd-uuid",
    "correlation_id": "corr-uuid",
    "tenant_id": "tenant_np_1"
  },
  "data": {
    "decision_id": "dec_7a8b9c0d",
    "model_id": "fraud_detection_v2",
    "model_version": "2.1.0",
    "input": {
      "transaction_amount": 50000,
      "merchant_category": "ELECTRONICS"
    },
    "prediction": {
      "class": "FRAUD",
      "confidence": 0.87
    },
    "explanation": {
      "method": "SHAP",
      "feature_importance": {...}
    }
  }
}
```

#### AIDecisionOverriddenEvent v1.0.0

```json
{
  "event_id": "uuid",
  "event_type": "AIDecisionOverridden",
  "event_version": "1.0.0",
  "aggregate_id": "dec_7a8b9c0d",
  "aggregate_type": "AIDecision",
  "sequence_number": 2,
  "timestamp_bs": "2081-11-17T10:35:00",
  "timestamp_gregorian": "2025-03-02T10:35:00Z",
  "metadata": {
    "trace_id": "trace-uuid",
    "causation_id": "override-cmd-uuid",
    "correlation_id": "corr-uuid",
    "tenant_id": "tenant_np_1"
  },
  "data": {
    "decision_id": "dec_7a8b9c0d",
    "original_prediction": "FRAUD",
    "override_value": "NOT_FRAUD",
    "reason": "Customer verified via phone call",
    "overridden_by": "user_456",
    "evidence": {...}
  }
}
```

### 3.2 Storage Tables

#### ai_models

```sql
CREATE TABLE ai_models (
  model_id VARCHAR(255) NOT NULL,
  version VARCHAR(50) NOT NULL,
  model_type VARCHAR(50) NOT NULL CHECK (model_type IN ('CLASSIFICATION', 'REGRESSION', 'CLUSTERING', 'NLP', 'RECOMMENDATION')),
  framework VARCHAR(100) NOT NULL,
  artifact_url VARCHAR(500) NOT NULL,
  metadata JSONB NOT NULL,
  explainability_method VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('REGISTERED', 'ACTIVE', 'DEPRECATED', 'ARCHIVED')),
  registered_by VARCHAR(255) NOT NULL,
  registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  activated_at TIMESTAMPTZ,
  PRIMARY KEY (model_id, version)
);

CREATE INDEX idx_ai_models_status ON ai_models(model_id, status);
```

#### ai_decisions

```sql
CREATE TABLE ai_decisions (
  decision_id VARCHAR(255) PRIMARY KEY,
  model_id VARCHAR(255) NOT NULL,
  model_version VARCHAR(50) NOT NULL,
  input JSONB NOT NULL,
  prediction JSONB NOT NULL,
  explanation JSONB,
  confidence FLOAT,
  tenant_id VARCHAR(255) NOT NULL,
  trace_id VARCHAR(255),
  overridden BOOLEAN DEFAULT FALSE,
  override_value JSONB,
  override_reason TEXT,
  overridden_by VARCHAR(255),
  overridden_at TIMESTAMPTZ,
  timestamp_bs VARCHAR(10) NOT NULL,
  timestamp_gregorian TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_decisions_model ON ai_decisions(model_id, timestamp_gregorian);
CREATE INDEX idx_ai_decisions_tenant ON ai_decisions(tenant_id, timestamp_gregorian);
CREATE INDEX idx_ai_decisions_overridden ON ai_decisions(overridden, timestamp_gregorian);
```

#### ai_prompts

```sql
CREATE TABLE ai_prompts (
  prompt_id VARCHAR(255) NOT NULL,
  version VARCHAR(50) NOT NULL,
  template TEXT NOT NULL,
  model_id VARCHAR(255) NOT NULL,
  parameters JSONB,
  status VARCHAR(20) NOT NULL CHECK (status IN ('REGISTERED', 'ACTIVE', 'DEPRECATED')),
  registered_by VARCHAR(255) NOT NULL,
  registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (prompt_id, version)
);

CREATE INDEX idx_ai_prompts_status ON ai_prompts(prompt_id, status);
```

#### model_drift_metrics

```sql
CREATE TABLE model_drift_metrics (
  metric_id UUID PRIMARY KEY,
  model_id VARCHAR(255) NOT NULL,
  model_version VARCHAR(50) NOT NULL,
  drift_score FLOAT NOT NULL,
  drifted_features JSONB,
  reference_period_start TIMESTAMPTZ NOT NULL,
  reference_period_end TIMESTAMPTZ NOT NULL,
  measured_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_drift_metrics_model ON model_drift_metrics(model_id, measured_at);
```

---

## 4. CONTROL FLOW

### 4.1 Model Registration Flow

```
ML Engineer → AIGovernanceClient.registerModel(model)
  ↓
AIGovernanceClient → Validate model metadata
  ↓ [Valid]
AIGovernanceClient → Upload model artifact to storage
  ↓
AIGovernanceClient → Insert into ai_models (status=REGISTERED)
  ↓
AIGovernanceClient → Publish ModelRegisteredEvent
  ↓
AIGovernanceClient → Return ModelInfo
```

### 4.2 Prediction Flow with Explanation

```
Service → AIGovernanceClient.predict(model_id, input, {requireExplanation: true})
  ↓
AIGovernanceClient → Resolve active model version
  ↓
AIGovernanceClient → Load model from artifact storage
  ↓
AIGovernanceClient → Validate input schema
  ↓ [Valid]
AIGovernanceClient → Execute model inference
  ↓
Model → Return prediction
  ↓
AIGovernanceClient → Generate explanation (SHAP/LIME)
  ↓
Explainer → Calculate feature importance
  ↓
AIGovernanceClient → Generate decision_id
  ↓
AIGovernanceClient → Insert into ai_decisions
  ↓
AIGovernanceClient → Publish AIDecisionMadeEvent
  ↓
AIGovernanceClient → Audit to K-07
  ↓
AIGovernanceClient → Return PredictionResult
```

### 4.3 HITL Override Flow

```
Human Operator → AIGovernanceClient.overrideDecision(decision_id, override)
  ↓
AIGovernanceClient → Verify operator has HITL permission
  ↓ [Authorized]
AIGovernanceClient → Load original decision
  ↓
AIGovernanceClient → Update ai_decisions (overridden=true, override_value, override_reason)
  ↓
AIGovernanceClient → Publish AIDecisionOverriddenEvent
  ↓
AIGovernanceClient → Audit to K-07
  ↓
AIGovernanceClient → Trigger retraining signal (if configured)
  ↓
AIGovernanceClient → Return success
```

### 4.4 Drift Detection Flow

```
Scheduler (hourly) → Run drift detection job
  ↓
DriftDetector → Query recent predictions (last 24h)
  ↓
DriftDetector → Load reference distribution (training data)
  ↓
DriftDetector → Calculate drift score (KL divergence, PSI)
  ↓
DriftDetector → Identify drifted features
  ↓
DriftDetector → Insert into model_drift_metrics
  ↓
If drift_score > threshold:
  ↓
  DriftDetector → Publish ModelDriftDetectedEvent
  ↓
  DriftDetector → Send alert to ML team
  ↓
  DriftDetector → Recommend action (RETRAIN/MONITOR)
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 SHAP Explainability

```python
import shap

class SHAPExplainer:
    """
    Generate SHAP explanations for model predictions.
    """

    def __init__(self, model, background_data):
        self.model = model
        self.explainer = shap.Explainer(model, background_data)

    def explain(self, input_data: dict) -> dict:
        """
        Generate SHAP explanation for prediction.
        """
        # Convert input to array
        input_array = self.dict_to_array(input_data)

        # Calculate SHAP values
        shap_values = self.explainer(input_array)

        # Extract feature importance
        feature_importance = {}
        for feature, shap_value in zip(input_data.keys(), shap_values.values[0]):
            feature_importance[feature] = abs(float(shap_value))

        # Normalize to sum to 1
        total = sum(feature_importance.values())
        feature_importance = {
            k: v / total for k, v in feature_importance.items()
        }

        return {
            'method': 'SHAP',
            'feature_importance': feature_importance
        }
```

### 5.2 Drift Detection (PSI)

```python
import numpy as np

class DriftDetector:
    """
    Detect model drift using Population Stability Index (PSI).
    """

    @staticmethod
    def calculate_psi(
        reference_dist: np.ndarray,
        current_dist: np.ndarray,
        bins: int = 10
    ) -> float:
        """
        Calculate PSI between reference and current distributions.

        PSI < 0.1: No significant drift
        0.1 <= PSI < 0.2: Moderate drift
        PSI >= 0.2: Significant drift
        """
        # Create bins
        min_val = min(reference_dist.min(), current_dist.min())
        max_val = max(reference_dist.max(), current_dist.max())
        bin_edges = np.linspace(min_val, max_val, bins + 1)

        # Calculate distributions
        ref_hist, _ = np.histogram(reference_dist, bins=bin_edges)
        cur_hist, _ = np.histogram(current_dist, bins=bin_edges)

        # Normalize
        ref_pct = ref_hist / len(reference_dist)
        cur_pct = cur_hist / len(current_dist)

        # Avoid division by zero
        ref_pct = np.where(ref_pct == 0, 0.0001, ref_pct)
        cur_pct = np.where(cur_pct == 0, 0.0001, cur_pct)

        # Calculate PSI
        psi = np.sum((cur_pct - ref_pct) * np.log(cur_pct / ref_pct))

        return float(psi)

    def detect_drift(
        self,
        model_id: str,
        reference_data: list[dict],
        current_data: list[dict]
    ) -> DriftReport:
        """
        Detect drift across all features.
        """
        drifted_features = []
        feature_drift_scores = {}

        # Get feature names
        features = reference_data[0].keys()

        for feature in features:
            # Extract feature values
            ref_values = np.array([d[feature] for d in reference_data])
            cur_values = np.array([d[feature] for d in current_data])

            # Calculate PSI
            psi = self.calculate_psi(ref_values, cur_values)
            feature_drift_scores[feature] = psi

            # Check threshold
            if psi >= 0.2:
                drifted_features.append(feature)

        # Overall drift score (max PSI)
        drift_score = max(feature_drift_scores.values())

        # Recommendation
        if drift_score >= 0.2:
            recommendation = 'RETRAIN'
        elif drift_score >= 0.1:
            recommendation = 'MONITOR'
        else:
            recommendation = 'OK'

        return DriftReport(
            model_id=model_id,
            drift_detected=len(drifted_features) > 0,
            drift_score=drift_score,
            drifted_features=drifted_features,
            recommendation=recommendation
        )
```

### 5.3 Model Rollback

```python
class ModelRollback:
    """
    Rollback model to previous version.
    """

    async def rollback(self, model_id: str, target_version: str):
        """
        Rollback model to target version.
        """
        # Verify target version exists
        target_model = await self.get_model(model_id, target_version)
        if not target_model:
            raise ModelNotFoundError(f"Model {model_id} v{target_version} not found")

        # Get current active version
        current_model = await self.get_active_model(model_id)

        # Deactivate current version
        await self.update_model_status(
            model_id,
            current_model.version,
            'DEPRECATED'
        )

        # Activate target version
        await self.update_model_status(
            model_id,
            target_version,
            'ACTIVE'
        )

        # Publish event
        await self.publish_event(ModelRolledBackEvent(
            model_id=model_id,
            from_version=current_model.version,
            to_version=target_version,
            rolled_back_at=datetime.now()
        ))

        logger.info(f"Rolled back {model_id} from v{current_model.version} to v{target_version}")
```

### 5.4 Prompt Template Rendering

```python
from jinja2 import Template

class PromptRenderer:
    """
    Render prompt templates with variables.
    """

    def render(self, template: str, variables: dict) -> str:
        """
        Render Jinja2 template with variables.
        """
        jinja_template = Template(template)
        rendered = jinja_template.render(**variables)
        return rendered

    async def execute_prompt(
        self,
        prompt_id: str,
        variables: dict
    ) -> str:
        """
        Execute prompt template.
        """
        # Load active prompt version
        prompt = await self.get_active_prompt(prompt_id)

        # Render template
        rendered = self.render(prompt.template, variables)

        # Call LLM
        response = await self.call_llm(
            model_id=prompt.model_id,
            prompt=rendered,
            parameters=prompt.parameters
        )

        # Audit
        await self.audit_prompt_execution(
            prompt_id=prompt_id,
            prompt_version=prompt.version,
            variables=variables,
            response=response
        )

        return response
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation                       | P50   | P95    | P99    | Timeout |
| ------------------------------- | ----- | ------ | ------ | ------- |
| predict() - without explanation | 50ms  | 200ms  | 500ms  | 5000ms  |
| predict() - with explanation    | 200ms | 1000ms | 2000ms | 10000ms |
| overrideDecision()              | 10ms  | 50ms   | 100ms  | 1000ms  |
| detectDrift()                   | 1s    | 5s     | 10s    | 60000ms |

### 6.2 Throughput Targets

| Operation          | Target TPS | Peak TPS |
| ------------------ | ---------- | -------- |
| predict()          | 1,000      | 5,000    |
| overrideDecision() | 100        | 500      |

### 6.3 Model Performance Thresholds

**Classification Models**:

- Minimum accuracy: 0.85
- Minimum precision: 0.80
- Minimum recall: 0.80

**Drift Thresholds**:

- PSI < 0.1: No action
- 0.1 <= PSI < 0.2: Monitor
- PSI >= 0.2: Retrain

---

## 7. SECURITY DESIGN

### 7.1 Model Artifact Security

```python
from cryptography.fernet import Fernet

class ModelArtifactEncryption:
    """
    Encrypt model artifacts at rest.
    """

    def __init__(self, encryption_key: bytes):
        self.cipher = Fernet(encryption_key)

    def encrypt_artifact(self, artifact: bytes) -> bytes:
        """
        Encrypt model artifact.
        """
        return self.cipher.encrypt(artifact)

    def decrypt_artifact(self, encrypted_artifact: bytes) -> bytes:
        """
        Decrypt model artifact.
        """
        return self.cipher.decrypt(encrypted_artifact)
```

### 7.2 HITL Authorization

```typescript
interface HITLPermissions {
  canOverride: (userId: string, modelId: string, tenantId: string) => boolean;
}

class HITLAccessControl implements HITLPermissions {
  canOverride(userId: string, modelId: string, tenantId: string): boolean {
    // Only specific roles can override AI decisions
    const allowedRoles = [
      "COMPLIANCE_OFFICER",
      "SENIOR_TRADER",
      "RISK_MANAGER",
    ];

    const userRoles = this.getUserRoles(userId, tenantId);

    return userRoles.some((role) => allowedRoles.includes(role));
  }

  private getUserRoles(userId: string, tenantId: string): string[] {
    // Implementation
    return [];
  }
}
```

### 7.3 Model Poisoning Detection

```python
class ModelPoisoningDetector:
    """
    Detect potential model poisoning attacks.
    """

    def detect_anomalous_predictions(
        self,
        model_id: str,
        predictions: list[dict],
        threshold: float = 3.0
    ) -> bool:
        """
        Detect anomalous prediction patterns using z-score.
        """
        confidences = [p['confidence'] for p in predictions]

        mean_confidence = np.mean(confidences)
        std_confidence = np.std(confidences)

        # Calculate z-scores
        z_scores = [(c - mean_confidence) / std_confidence for c in confidences]

        # Check for outliers
        anomalies = [z for z in z_scores if abs(z) > threshold]

        if len(anomalies) > len(predictions) * 0.1:  # > 10% anomalies
            logger.warning(f"Potential model poisoning detected for {model_id}")
            return True

        return False
```

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics

```yaml
metrics:
  - name: ai_predictions_total
    type: counter
    labels: [model_id, model_version, prediction_class]

  - name: ai_prediction_latency_seconds
    type: histogram
    labels: [model_id, with_explanation]
    buckets: [0.05, 0.1, 0.5, 1.0, 2.0, 5.0]

  - name: ai_prediction_confidence
    type: histogram
    labels: [model_id, prediction_class]
    buckets: [0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 0.99]

  - name: ai_decisions_overridden_total
    type: counter
    labels: [model_id, original_prediction, override_value]

  - name: ai_model_drift_score
    type: gauge
    labels: [model_id, model_version]

  - name: ai_model_accuracy
    type: gauge
    labels: [model_id, model_version]
```

### 8.2 Structured Logs

```json
{
  "timestamp": "2025-03-02T10:30:00.123Z",
  "level": "INFO",
  "service": "ai-governance",
  "trace_id": "abc-123",
  "action": "AI_PREDICTION_MADE",
  "decision_id": "dec_7a8b9c0d",
  "model_id": "fraud_detection_v2",
  "model_version": "2.1.0",
  "prediction": "FRAUD",
  "confidence": 0.87,
  "explanation_method": "SHAP"
}
```

### 8.3 Alerting

```yaml
alerts:
  - name: ModelDriftDetected
    condition: ai_model_drift_score > 0.2
    severity: HIGH
    description: Model drift detected, retraining recommended

  - name: LowPredictionConfidence
    condition: histogram_quantile(0.5, ai_prediction_confidence) < 0.7
    severity: MEDIUM
    description: Median prediction confidence below 70%

  - name: HighOverrideRate
    condition: rate(ai_decisions_overridden_total[1h]) / rate(ai_predictions_total[1h]) > 0.1
    severity: HIGH
    description: More than 10% of AI decisions being overridden
```

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 Custom Explainability Methods

```typescript
interface ExplainabilityProvider {
  name: string;
  explain(model: Model, input: unknown): Promise<Explanation>;
}

class AttentionExplainer implements ExplainabilityProvider {
  name = "ATTENTION";

  async explain(model: Model, input: unknown): Promise<Explanation> {
    // Extract attention weights from transformer model
    const attentionWeights = await model.getAttentionWeights(input);

    return {
      method: "ATTENTION",
      feature_importance: this.normalizeAttention(attentionWeights),
      reasoning_steps: this.generateReasoningSteps(attentionWeights),
    };
  }

  private normalizeAttention(weights: number[][]): Record<string, number> {
    // Implementation
    return {};
  }

  private generateReasoningSteps(weights: number[][]): string[] {
    // Implementation
    return [];
  }
}

// Register custom explainer
aiGovernance.registerExplainer(new AttentionExplainer());
```

### 9.2 A/B Testing Framework

```typescript
interface ABTest {
  testId: string;
  modelA: string;
  modelB: string;
  trafficSplit: number; // 0.0 to 1.0
  metrics: string[];
  startDate: Date;
  endDate: Date;
}

class ABTestRunner {
  async runTest(test: ABTest, input: unknown): Promise<PredictionResult> {
    // Determine which model to use
    const useModelA = Math.random() < test.trafficSplit;
    const modelId = useModelA ? test.modelA : test.modelB;

    // Make prediction
    const result = await aiClient.predict(modelId, input);

    // Track metrics
    await this.trackMetrics(test.testId, modelId, result);

    return result;
  }

  async analyzeTest(testId: string): Promise<ABTestResults> {
    // Compare metrics between models
    const metricsA = await this.getMetrics(testId, "modelA");
    const metricsB = await this.getMetrics(testId, "modelB");

    return {
      winner: this.determineWinner(metricsA, metricsB),
      confidence: this.calculateConfidence(metricsA, metricsB),
      metrics: { modelA: metricsA, modelB: metricsB },
    };
  }
}
```

### 9.3 Fairness Metrics

```python
class FairnessMetrics:
    """
    Calculate fairness metrics for model predictions.
    """

    @staticmethod
    def demographic_parity(
        predictions: list[dict],
        protected_attribute: str
    ) -> float:
        """
        Calculate demographic parity difference.

        Measures difference in positive prediction rates
        between protected and unprotected groups.
        """
        protected_group = [p for p in predictions if p[protected_attribute]]
        unprotected_group = [p for p in predictions if not p[protected_attribute]]

        protected_positive_rate = sum(
            1 for p in protected_group if p['prediction'] == 'POSITIVE'
        ) / len(protected_group)

        unprotected_positive_rate = sum(
            1 for p in unprotected_group if p['prediction'] == 'POSITIVE'
        ) / len(unprotected_group)

        return abs(protected_positive_rate - unprotected_positive_rate)

    @staticmethod
    def equal_opportunity(
        predictions: list[dict],
        protected_attribute: str
    ) -> float:
        """
        Calculate equal opportunity difference.

        Measures difference in true positive rates
        between protected and unprotected groups.
        """
        # Implementation
        pass
```

---

## 10. TEST PLAN

### 10.1 Unit Tests

```typescript
describe("AIGovernance", () => {
  it("should register model successfully", async () => {
    const model: ModelRegistration = {
      modelId: "test_model",
      modelType: "CLASSIFICATION",
      framework: "SKLEARN",
      version: "1.0.0",
      artifactUrl: "s3://models/test_model.pkl",
      metadata: { accuracy: 0.95 },
      explainabilityMethod: "SHAP",
      registeredBy: "ml_engineer_1",
    };

    const info = await aiClient.registerModel(model);

    expect(info.modelId).toBe("test_model");
    expect(info.version).toBe("1.0.0");
    expect(info.status).toBe("REGISTERED");
  });

  it("should make prediction with explanation", async () => {
    const result = await aiClient.predict(
      "fraud_detection_v2",
      {
        transaction_amount: 50000,
        merchant_category: "ELECTRONICS",
      },
      { requireExplanation: true },
    );

    expect(result.prediction).toBeDefined();
    expect(result.explanation).toBeDefined();
    expect(result.explanation.method).toBe("SHAP");
    expect(result.explanation.feature_importance).toBeDefined();
  });

  it("should override AI decision", async () => {
    const decisionId = "dec_test_1";

    await aiClient.overrideDecision(decisionId, {
      overrideValue: "NOT_FRAUD",
      reason: "Customer verified",
      overriddenBy: "user_456",
    });

    const decision = await getDecision(decisionId);
    expect(decision.overridden).toBe(true);
    expect(decision.override_value).toBe("NOT_FRAUD");
  });
});
```

### 10.2 Integration Tests

```typescript
describe("Drift Detection", () => {
  it("should detect significant drift", async () => {
    // Create reference data
    const referenceData = generateNormalData(1000, { mean: 100, std: 10 });

    // Create drifted data
    const currentData = generateNormalData(1000, { mean: 150, std: 10 });

    const report = await aiClient.detectDrift("test_model", referenceData);

    expect(report.driftDetected).toBe(true);
    expect(report.driftScore).toBeGreaterThan(0.2);
    expect(report.recommendation).toBe("RETRAIN");
  });

  it("should not detect drift for similar distributions", async () => {
    const referenceData = generateNormalData(1000, { mean: 100, std: 10 });
    const currentData = generateNormalData(1000, { mean: 101, std: 10 });

    const report = await aiClient.detectDrift("test_model", referenceData);

    expect(report.driftDetected).toBe(false);
    expect(report.driftScore).toBeLessThan(0.1);
    expect(report.recommendation).toBe("OK");
  });
});
```

### 10.3 Security Tests

```typescript
describe("AI Security", () => {
  it("should prevent unauthorized HITL override", async () => {
    const regularUser = { userId: "user_1", role: "TRADER" };

    await expect(
      aiClient.overrideDecision("dec_test_1", {
        overrideValue: "NOT_FRAUD",
        reason: "Test",
        overriddenBy: regularUser.userId,
      }),
    ).rejects.toThrow(UnauthorizedError);
  });

  it("should encrypt model artifacts", async () => {
    const artifact = Buffer.from("model data");

    const encrypted = encryptArtifact(artifact);
    expect(encrypted).not.toEqual(artifact);

    const decrypted = decryptArtifact(encrypted);
    expect(decrypted).toEqual(artifact);
  });
});
```

### 10.4 Explainability Tests

```typescript
describe("Model Explainability", () => {
  it("should generate SHAP explanation", async () => {
    const result = await aiClient.predict(
      "fraud_detection_v2",
      {
        transaction_amount: 50000,
        user_history_score: 0.8,
        merchant_category: "ELECTRONICS",
      },
      { requireExplanation: true },
    );

    expect(result.explanation.method).toBe("SHAP");

    const importance = result.explanation.feature_importance;
    expect(Object.keys(importance)).toContain("transaction_amount");
    expect(Object.keys(importance)).toContain("user_history_score");

    // Sum of importance should be ~1.0
    const sum = Object.values(importance).reduce((a, b) => a + b, 0);
    expect(sum).toBeCloseTo(1.0, 2);
  });
});
```

---

## 11. VALIDATION QUESTIONS & ASSUMPTIONS

### Assumptions

1. **[ASSUMPTION]** Models are stateless (no session state)
   - **Validation**: Are there stateful models (e.g., RNNs with hidden state)?
   - **Impact**: May need session management

2. **[ASSUMPTION]** Explainability is post-hoc (not intrinsic)
   - **Validation**: Are there intrinsically interpretable models?
   - **Impact**: May need different explanation strategies

3. **[ASSUMPTION]** Drift detection runs periodically (hourly)
   - **Validation**: Is real-time drift detection needed?
   - **Impact**: May need streaming drift detection

4. **[ASSUMPTION]** HITL overrides are rare (< 1% of decisions)
   - **Validation**: What is expected override rate?
   - **Impact**: May need dedicated override workflow

5. **[ASSUMPTION]** Model artifacts are < 1GB
   - **Validation**: Are there large models (e.g., LLMs)?
   - **Impact**: May need chunked loading or model serving infrastructure

---

**END OF LLD: K-09 AI GOVERNANCE**
