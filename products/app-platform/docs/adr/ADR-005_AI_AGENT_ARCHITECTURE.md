# ADR-005: AI Agent Architecture Framework
## Project Siddhanta - Architectural Decision Record

**Status**: Accepted  
**Date**: 2026-03-08  
**Decision**: Adopt governed AI agent framework with human-in-the-loop controls  
**Impact**: High

---

# CONTEXT

## Problem Statement

Project Siddhanta requires AI/ML capabilities across multiple domain services — from order suggestions and risk prediction to surveillance anomaly detection and settlement failure prediction. The platform must:

- Integrate AI models safely in a financial regulatory environment
- Ensure explainability for all AI-driven decisions
- Provide human-in-the-loop (HITL) override capabilities
- Detect and mitigate model drift, bias, and adversarial inputs
- Support model versioning, A/B testing, and instant rollback
- Comply with emerging AI governance regulations

## Constraints

1. **Regulatory**: AI decisions in capital markets must be explainable and auditable
2. **Risk**: Model failures can cause financial losses and regulatory penalties
3. **Latency**: Trading-path AI must not exceed 500ms P99
4. **Bias**: Models must be monitored for demographic and market-condition bias
5. **Governance**: All model deployments require approval workflows

---

# DECISION

## Architecture Choice

**Adopt a centralized AI Governance service (K-09) that provides model registry, explainability, HITL controls, drift detection, and audit trail for all AI agents across the platform.**

### **AI Agent Categories**

#### **Autonomous Agents** (Low-Risk, High-Volume)
- Reference data quality checking
- Report validation copilot
- Settlement failure prediction
- Operate with monitoring; HITL override available but not mandatory

#### **Advisory Agents** (Medium-Risk)
- Order suggestions
- Portfolio rebalancing recommendations
- Corporate action reconciliation assistance
- Always present recommendations; human makes final decision

#### **Supervised Agents** (High-Risk)
- Surveillance anomaly detection
- Risk model execution
- Compliance violation assessment
- Require human review before any action is taken

### **Core Capabilities**

#### **1. Model Registry**
- Centralized registration of all AI/ML models
- Semantic versioning with dependency tracking
- Model metadata: training data, performance metrics, bias reports
- Deployment status tracking (development, staging, production, deprecated)

#### **2. Explainability Framework**
- SHAP (SHapley Additive exPlanations) for feature importance
- LIME (Local Interpretable Model-agnostic Explanations) for local explanations
- Decision audit trail linking inputs → model version → outputs → explanation
- Natural language explanation generation for compliance officers

#### **3. Human-in-the-Loop (HITL)**
- Override mechanism for any AI decision
- Override audit trail (who, when, why, original decision, override decision)
- Automatic escalation when override rate exceeds threshold
- Feedback loop: overrides inform model retraining

#### **4. Drift Detection**
- Population Stability Index (PSI) for input drift
- Performance drift monitoring (accuracy, precision, recall degradation)
- Automated alerting when drift exceeds configurable thresholds
- Automatic model rollback on critical drift

#### **5. A/B Testing**
- Canary deployment of new model versions
- Statistical significance testing before full rollout
- Automatic rollback on performance regression

---

# CONSEQUENCES

## Positive Consequences

### **Regulatory Compliance**
- **Explainability**: Every AI decision has auditable explanation
- **Audit Trail**: Complete decision → explanation → override chain
- **Governance**: Approval workflows for model deployment
- **Transparency**: Regulators can inspect model behavior

### **Risk Management**
- **HITL Override**: Humans can override any AI decision
- **Drift Detection**: Early warning of model degradation
- **Rollback**: Instant rollback to previous model versions
- **Bias Monitoring**: Continuous fairness metric tracking

### **Operational Excellence**
- **Model Registry**: Single source of truth for all models
- **A/B Testing**: Data-driven model improvement
- **Monitoring**: Comprehensive AI observability
- **Feedback Loop**: Continuous model improvement

## Negative Consequences

### **Complexity**
- **Integration Overhead**: Every AI-using service must integrate with K-09
- **Latency Addition**: Explainability computation adds ~50ms per decision
- **Storage Cost**: Explanation artifacts require significant storage
- **Operational Overhead**: Model governance requires dedicated team

### **Mitigation**
- K-12 Platform SDK provides transparent K-09 integration
- Async explainability compute for non-trading-path decisions
- Tiered storage for explanation artifacts (hot/warm/cold)
- Automated drift detection reduces manual monitoring burden

---

# ALTERNATIVES CONSIDERED

## Option 1: Decentralized AI (Per-Service ML)
- **Rejected**: No centralized governance, audit, or drift detection
- **Risk**: Inconsistent explainability; model sprawl

## Option 2: External AI Platform (AWS SageMaker, Azure ML)
- **Rejected**: Cloud-lock-in; air-gap incompatibility; insufficient auditability
- **Risk**: Data residency concerns; vendor dependency

## Option 3: No AI (Rule-Based Only)
- **Rejected**: Insufficient for surveillance, risk prediction, and quality automation
- **Risk**: Competitive disadvantage; higher false positive rates

---

# IMPLEMENTATION NOTES

Implementation alignment update (2026-03-09): the Siddhanta baseline reuses the Ghatana AI platform components referenced by [ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md). This keeps the AI control plane consistent with the broader Ghatana platform while preserving the production constraints in this ADR.

## Technology
- **Service**: K-09 AI Governance (Java 21 + ActiveJ for control-plane integration, Python/FastAPI for model-specific inference when required)
- **Explainability**: SHAP, LIME libraries (Python)
- **Model Storage**: S3/MinIO for model artifacts
- **Platform Reuse**: Ghatana `ai-registry`, `ai-inference-service`, `feature-store-ingest`, `platform/java/ai-integration`
- **Metrics**: Prometheus for drift/performance metrics, Grafana dashboards

## Model Risk Tiering

All models are assigned a governance tier that determines HITL requirements and override paths:

| Tier | Name | HITL Requirement | Use Cases |
|------|------|-----------------|-----------|
| **TIER_1** | Autonomous — Informational | Monitoring only; override available | Classifiers that route work, surface insights, or annotate data |
| **TIER_2** | Decision Support | Presents recommendation; human decides | Predictions that influence but do not execute decisions |
| **TIER_3** | Automated Decision | Shadow evaluation ≥5 days; HITL before production promo | Models whose output triggers automated execution |
| **ADVISORY_LLM** | RAG/LLM Copilot | All outputs reviewed before use; locally-hosted only | Generative assistants; no external API calls permitted |

## AI-Integrated Services — Complete Model Registry

### Domain Services

| Service | Model ID | Algorithm / Architecture | Tier | Latency Budget | Implementing Stories |
|---------|----------|--------------------------|------|----------------|----------------------|
| D-01 OMS | `order-optimizer-v1` | Gradient-boosted regressor (GBM) | TIER_2 | 500ms | D01-019 |
| D-01 OMS | `execution-strategy-suggester-v1` | Multi-class classifier (HITL feedback) | ADVISORY_LLM | 300ms | D01-020 |
| D-01 OMS | `pre-trade-cost-estimator-v1` | Implementation shortfall model (XGBoost) | TIER_2 | 200ms | D01-021 |
| D-02 EMS | `venue-toxicity-scorer-v1` | LightGBM, features: fill_rate/slippage/adverse_selection | TIER_2 | 5s (cron) | D02-021 |
| D-02 EMS | `rl-execution-optimizer-v1` | PPO/DQN RL agent | TIER_3 | 200ms | D02-022 |
| D-06 Risk | `var-model-v1` | Historical/parametric VaR | TIER_2 | 2ms (pre-trade) | D06-001→D06-006 |
| D-06 Risk | `regime-detector-v1` | Hidden Markov Model (HMM), 3-state | TIER_2 | 500ms | D06-020 |
| D-06 Risk | `stress-scenario-vae-v1` | Variational Autoencoder (VAE) | TIER_2 | 60s (batch) | D06-021 |
| D-07 Compliance | `adverse-media-screener-v1` | Fine-tuned BERT (multilingual) | TIER_2 | 2s | D07-015 |
| D-07 Compliance | `str-risk-scorer-v1` | XGBoost, 7 behavioral features | TIER_3 | 500ms | D07-016 |
| D-07 Compliance | `edd-copilot-v1` | RAG-LLM (locally-hosted Mistral-7B) | ADVISORY_LLM | 10s | D07-017 |
| D-08 Surveillance | `market-abuse-detector-v1` | Isolation Forest + Autoencoder | TIER_3 | 500ms | D08-009 |
| D-08 Surveillance | `collusion-detector-v1` | GraphSAGE GNN, co-trading graph | TIER_3 | 2s | D08-015 |
| D-08 Surveillance | `surveillance-narrative-v1` | RAG-LLM (locally-hosted, K-04 T2 sandbox) | ADVISORY_LLM | 15s | D08-016 |
| D-09 Settlement | `settlement-fail-predictor-v1` | XGBoost, counterparty/liquidity features | TIER_2 | 100ms | D09-017 |
| D-10 Reporting | `report-validator-copilot-v1` | Rule-augmented NLP validator | TIER_1 | 1s | D10-* |
| D-11 Ref Data | `refdata-quality-checker-v1` | Blocking + fine-tuned BERT record linkage | TIER_1 | 100ms | D11-012 |
| D-11 Ref Data | `instrument-classifier-v1` | NLP multi-label classifier (asset_class/sector/risk_tier) | TIER_1 | 200ms | D11-013 |
| D-12 Corp Actions | `ca-reconciliation-copilot-v1` | Gradient-boosted multiclass (6 break classes) | TIER_1 | 1s | D12-013 |
| D-12 Corp Actions | `ca-instruction-narrator-v1` | RAG-LLM (locally-hosted Mistral-7B, K-04 T2) | ADVISORY_LLM | 10s | D12-014 |
| D-13 Recon | `recon-break-classifier-v1` | Gradient-boosted multiclass (CLEAN/TRANSIENT/SYSTEMATIC/CRITICAL) | TIER_1 | 500ms | D13-016 |
| D-13 Recon | `recon-resolution-rag-v1` | RAG over K-07 historical resolutions (pgvector) | ADVISORY_LLM | 3s | D13-017 |
| D-13 Recon | `recon-failure-forecaster-v1` | LSTM + Prophet seasonality, 3-day horizon | TIER_2 | 60s (batch) | D13-018 |
| D-14 Sanctions | `sanctions-embedding-matcher-v1` | Multilingual sentence-transformer + pgvector ANN | TIER_2 | 200ms | D14-015 |
| D-14 Sanctions | `pep-network-scorer-v1` | GraphSAGE GNN, N-hop risk propagation | TIER_2 | 1s | D14-016 |

### Platform Kernel Services

| Service | Model ID | Algorithm / Architecture | Tier | Latency Budget | Implementing Stories |
|---------|----------|--------------------------|------|----------------|----------------------|
| K-06 Observability | `metric-anomaly-detector-v1` | Isolation Forest + LSTM Autoencoder, seasonality-aware | TIER_1 | 2s (streaming) | K06-020 |
| K-06 Observability | `log-intelligence-v1` | sentence-transformer + HDBSCAN clustering, pgvector NLQ | TIER_1 | 5s | K06-021 |
| K-06 Observability | `alert-correlator-v1` | Embedding dedup + K-08 dependency graph traversal, SHAP RCA | TIER_1 | 1s | K06-022 |
| K-19 DLQ | `dlq-failure-classifier-v1` | Gradient-boosted (5 classes) + sentence-transformer error embeddings | TIER_1 | 100ms | K19-014 |
| K-19 DLQ | `dlq-root-cause-rag-v1` | RAG over K-07 historical resolutions + LSTM 2h overflow forecast | ADVISORY_LLM | 3s | K19-015 |

### Operations & Compliance Portal Services

| Service | Model ID | Algorithm / Architecture | Tier | Latency Budget | Implementing Stories |
|---------|----------|--------------------------|------|----------------|----------------------|
| O-01 Operator Console | `nlq-query-engine-v1` | LLM NL→PromQL/ES translator (locally-hosted, K-04 T2) | ADVISORY_LLM | 5s | O01-013 |
| O-01 Operator Console | `capacity-planner-v1` | Prophet time-series, 7-day resource forecasting | TIER_2 | 60s (batch) | O01-014 |
| R-01 Regulator Portal | `regulatory-query-copilot-v1` | RAG-LLM over K-08 catalogs + K-07 audit (locally-hosted) | ADVISORY_LLM | 5s | R01-011 |
| R-02 Incident Response | `incident-clustering-v1` | HDBSCAN on incident embeddings + temporal logistic regression | TIER_2 | 2s | R02-011 |
| R-02 Incident Response | `rca-assistant-v1` | LLM RCA from K-06 alerts + K-07 events + K-08 lineage context | ADVISORY_LLM | 3 min | R02-012 |

## LLM Inference Boundary Policy

All LLM-based models (ADVISORY_LLM tier) **must** comply with the following constraints:
- **Local inference only** — hosted within K-04 Plugin Runtime T2 secure sandbox (Mistral-7B or equivalent)
- **No external API calls** — OpenAI, Anthropic, and all external LLM APIs are prohibited in production
- **PII stripping** — K-08 data lineage service strips/masks PII before prompt construction
- **Prompt audit** — Every prompt + completion pair stored immutably in K-07 with requester identity, timestamp, and session ID
- **Output review gate** — All ADVISORY_LLM outputs presented to a human reviewer before downstream action

## Vector Store Architecture

| Use Case | Store | Index Type | Embedding Model |
|----------|-------|------------|-----------------|
| Sanctions name matching | pgvector | HNSW (cosine) | multilingual-e5-large |
| Log semantic search | pgvector | HNSW (cosine) | all-MiniLM-L6-v2 |
| Recon break resolution RAG | pgvector | HNSW (cosine) | all-MiniLM-L6-v2 |
| DLQ root cause RAG | pgvector | IVFFlat | all-MiniLM-L6-v2 |
| Alert deduplication | pgvector | HNSW (cosine) | all-MiniLM-L6-v2 |
| EDD copilot RAG | pgvector | HNSW (cosine) | multilingual-e5-base |

## Dependencies
- K-02 (model config and thresholds), K-04 (LLM T2 sandbox), K-05 (AI decision events), K-07 (audit trail), K-08 (data lineage + PII masking), K-09 (model registry + SHAP + drift + HITL), K-10 (HPA scale-up on overflow predictions), Ghatana `ai-registry`, `ai-inference-service`, `feature-store-ingest`, and `platform/java/ai-integration`

---

**Decision Makers**: Platform Architecture Team  
**Reviewers**: AI Ethics Committee, Regulatory Compliance Team  
**Approval Date**: 2026-03-08
