# Architecture & Design Documentation Suite for Project Siddhanta

## Part 2: Sections 6-8

**Document Version:** 2.1  
**Date:** March 5, 2026  
**Status:** Implementation-Ready (Post-ARB Remediation)  
**Change Log:** v2.1 adds AI governance guardrails, drift detection SLAs, multi-cloud deployment abstraction (K-10), and DLQ management integration (K-19)

> **Stack authority**: [../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md) defines the canonical Siddhanta stack. This document aligns AI/ML, event, workflow, and data-management execution to the Ghatana platform products referenced in ADR-011.
>
> **Platform Reuse**: See [Finance-Ghatana Integration Plan](../../finance-ghatana-integration-plan.md) for detailed implementation guidance on reusing Ghatana platform components.

---

## Table of Contents - Part 2 (Sections 6-8)

6. [AI Governance & Integration Architecture](#6-ai-governance--integration-architecture)
7. [Data Architecture](#7-data-architecture)
8. [Deployment Architecture](#8-deployment-architecture)

---

## 6. AI Governance & Integration Architecture

### 6.1 Overview

The AI Governance & Integration Architecture provides a **comprehensive framework for responsible AI deployment** in capital markets operations, ensuring:

- Regulatory compliance (SEBI AI guidelines, EU AI Act)
- Model transparency and explainability
- Bias detection and mitigation
- Model versioning and lifecycle management
- Performance monitoring and drift detection
- Ethical AI practices

Execution baseline:

- Reuse Ghatana `shared-services/ai-registry` for model registry
- Reuse Ghatana `shared-services/ai-inference-service` for inference gateway patterns
- Reuse Ghatana `shared-services/feature-store-ingest` for event-to-feature ingestion
- Reuse Ghatana `platform/java/ai-integration` for shared Java AI integration
- Keep Siddhanta production policy constraints from [../adr/ADR-005_AI_AGENT_ARCHITECTURE.md](../adr/ADR-005_AI_AGENT_ARCHITECTURE.md), especially local-only production LLM inference

### 6.2 AI Use Cases in Project Siddhanta

| Use Case                  | AI Technique                             | Business Value                       |
| ------------------------- | ---------------------------------------- | ------------------------------------ |
| **Risk Assessment**       | Ensemble models, Deep Learning           | Real-time portfolio risk scoring     |
| **Fraud Detection**       | Anomaly detection, Graph Neural Networks | Identify suspicious trading patterns |
| **Market Prediction**     | Time-series forecasting, LSTM            | Price movement prediction            |
| **Smart Order Routing**   | Reinforcement Learning                   | Optimal execution strategies         |
| **Compliance Monitoring** | NLP, Rule-based AI                       | Automated regulatory compliance      |
| **Client Segmentation**   | Clustering, Classification               | Personalized service offerings       |
| **Document Processing**   | OCR, NLP                                 | Automated KYC/corporate actions      |
| **Chatbot Support**       | LLM, RAG                                 | 24/7 client support                  |

### 6.3 AI Architecture Components

```
┌─────────────────────────────────────────────────────────────┐
│                    AI Governance Layer                       │
│  (Model Registry, Approval Workflow, Audit Trail)           │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Model Serving Layer                       │
│  (Inference API, A/B Testing, Canary Deployment)            │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Feature Store                             │
│  (Feature Engineering, Storage, Serving)                    │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Model Training Pipeline                   │
│  (Data Prep, Training, Validation, Registration)            │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Monitoring & Observability                │
│  (Performance Metrics, Drift Detection, Explainability)     │
└─────────────────────────────────────────────────────────────┘
```

### 6.4 Model Registry & Versioning

**MLflow Model Registry**:

```python
import mlflow
from mlflow.tracking import MlflowClient

class ModelRegistry:
    def __init__(self, tracking_uri: str):
        mlflow.set_tracking_uri(tracking_uri)
        self.client = MlflowClient()

    def register_model(
        self,
        model_name: str,
        model_uri: str,
        metadata: dict
    ) -> str:
        """Register a new model version"""

        # Create or get registered model
        try:
            self.client.create_registered_model(
                name=model_name,
                description=metadata.get('description', '')
            )
        except Exception:
            pass  # Model already exists

        # Create new version
        model_version = self.client.create_model_version(
            name=model_name,
            source=model_uri,
            run_id=metadata.get('run_id'),
            tags=metadata.get('tags', {}),
            description=metadata.get('version_description', '')
        )

        # Add metadata
        self.client.set_model_version_tag(
            name=model_name,
            version=model_version.version,
            key='training_date',
            value=metadata.get('training_date')
        )

        self.client.set_model_version_tag(
            name=model_name,
            version=model_version.version,
            key='dataset_version',
            value=metadata.get('dataset_version')
        )

        return model_version.version

    def transition_model_stage(
        self,
        model_name: str,
        version: str,
        stage: str,
        archive_existing: bool = True
    ):
        """Transition model to different stage (Staging/Production/Archived)"""

        self.client.transition_model_version_stage(
            name=model_name,
            version=version,
            stage=stage,
            archive_existing_versions=archive_existing
        )

        logger.info(f"Model {model_name} v{version} transitioned to {stage}")

    def get_model_version(
        self,
        model_name: str,
        stage: str = 'Production'
    ) -> str:
        """Get model version for a specific stage"""

        versions = self.client.get_latest_versions(
            name=model_name,
            stages=[stage]
        )

        if not versions:
            raise ModelNotFoundError(f"No {stage} version found for {model_name}")

        return versions[0].version

    def load_model(self, model_name: str, version: str = None):
        """Load model for inference"""

        if version:
            model_uri = f"models:/{model_name}/{version}"
        else:
            model_uri = f"models:/{model_name}/Production"

        return mlflow.pyfunc.load_model(model_uri)
```

### 6.5 Model Training Pipeline

**Training Workflow**:

```python
from dataclasses import dataclass
from typing import Dict, Any
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, precision_score, recall_score

@dataclass
class TrainingConfig:
    model_name: str
    model_type: str
    hyperparameters: Dict[str, Any]
    dataset_version: str
    validation_split: float = 0.2
    test_split: float = 0.1

class ModelTrainingPipeline:
    def __init__(
        self,
        config: TrainingConfig,
        feature_store: FeatureStore,
        model_registry: ModelRegistry
    ):
        self.config = config
        self.feature_store = feature_store
        self.model_registry = model_registry

    def run(self) -> str:
        """Execute complete training pipeline"""

        with mlflow.start_run(run_name=f"{self.config.model_name}_training") as run:
            # Step 1: Load data from feature store
            logger.info("Loading training data from feature store")
            X, y = self.feature_store.get_training_data(
                dataset_version=self.config.dataset_version
            )

            # Step 2: Split data
            X_train, X_temp, y_train, y_temp = train_test_split(
                X, y, test_size=(self.config.validation_split + self.config.test_split)
            )

            val_size = self.config.validation_split / (
                self.config.validation_split + self.config.test_split
            )
            X_val, X_test, y_val, y_test = train_test_split(
                X_temp, y_temp, test_size=(1 - val_size)
            )

            # Step 3: Log parameters
            mlflow.log_params(self.config.hyperparameters)
            mlflow.log_param("dataset_version", self.config.dataset_version)
            mlflow.log_param("train_size", len(X_train))
            mlflow.log_param("val_size", len(X_val))
            mlflow.log_param("test_size", len(X_test))

            # Step 4: Train model
            logger.info("Training model")
            model = self.train_model(X_train, y_train, X_val, y_val)

            # Step 5: Evaluate model
            logger.info("Evaluating model")
            metrics = self.evaluate_model(model, X_test, y_test)

            # Log metrics
            for metric_name, metric_value in metrics.items():
                mlflow.log_metric(metric_name, metric_value)

            # Step 6: Check if model meets quality threshold
            if not self.meets_quality_threshold(metrics):
                logger.warning("Model does not meet quality threshold")
                return None

            # Step 7: Log model
            logger.info("Logging model to MLflow")
            mlflow.sklearn.log_model(
                model,
                "model",
                registered_model_name=self.config.model_name
            )

            # Step 8: Register model
            version = self.model_registry.register_model(
                model_name=self.config.model_name,
                model_uri=f"runs:/{run.info.run_id}/model",
                metadata={
                    'run_id': run.info.run_id,
                    'training_date': datetime.now().isoformat(),
                    'dataset_version': self.config.dataset_version,
                    'metrics': metrics
                }
            )

            logger.info(f"Model registered: {self.config.model_name} v{version}")

            return version

    def train_model(self, X_train, y_train, X_val, y_val):
        """Train model based on model type"""

        if self.config.model_type == 'random_forest':
            from sklearn.ensemble import RandomForestClassifier
            model = RandomForestClassifier(**self.config.hyperparameters)
        elif self.config.model_type == 'xgboost':
            import xgboost as xgb
            model = xgb.XGBClassifier(**self.config.hyperparameters)
        elif self.config.model_type == 'neural_network':
            model = self.build_neural_network()
        else:
            raise ValueError(f"Unsupported model type: {self.config.model_type}")

        model.fit(X_train, y_train)
        return model

    def evaluate_model(self, model, X_test, y_test) -> Dict[str, float]:
        """Evaluate model performance"""

        y_pred = model.predict(X_test)

        metrics = {
            'accuracy': accuracy_score(y_test, y_pred),
            'precision': precision_score(y_test, y_pred, average='weighted'),
            'recall': recall_score(y_test, y_pred, average='weighted')
        }

        return metrics

    def meets_quality_threshold(self, metrics: Dict[str, float]) -> bool:
        """Check if model meets minimum quality requirements"""

        thresholds = {
            'accuracy': 0.85,
            'precision': 0.80,
            'recall': 0.80
        }

        for metric_name, threshold in thresholds.items():
            if metrics.get(metric_name, 0) < threshold:
                logger.warning(
                    f"Metric {metric_name} ({metrics[metric_name]:.3f}) "
                    f"below threshold ({threshold})"
                )
                return False

        return True
```

### 6.6 Feature Store

**Feature Engineering & Storage**:

```python
from feast import FeatureStore, Entity, FeatureView, Field
from feast.types import Float32, Int64, String
from datetime import timedelta

class FeatureStoreManager:
    def __init__(self, repo_path: str):
        self.store = FeatureStore(repo_path=repo_path)

    def define_entities(self):
        """Define entities for feature store"""

        # Client entity
        client = Entity(
            name="client",
            join_keys=["client_id"],
            description="Client entity"
        )

        # Instrument entity
        instrument = Entity(
            name="instrument",
            join_keys=["instrument_id"],
            description="Financial instrument entity"
        )

        return [client, instrument]

    def define_feature_views(self):
        """Define feature views"""

        # Client features
        client_features = FeatureView(
            name="client_features",
            entities=["client"],
            ttl=timedelta(days=365),
            schema=[
                Field(name="total_portfolio_value", dtype=Float32),
                Field(name="avg_trade_size", dtype=Float32),
                Field(name="trading_frequency", dtype=Int64),
                Field(name="risk_score", dtype=Float32),
                Field(name="account_age_days", dtype=Int64),
                Field(name="kyc_status", dtype=String)
            ],
            source="client_features_source"
        )

        # Instrument features
        instrument_features = FeatureView(
            name="instrument_features",
            entities=["instrument"],
            ttl=timedelta(days=1),
            schema=[
                Field(name="volatility_30d", dtype=Float32),
                Field(name="avg_volume_30d", dtype=Float32),
                Field(name="price_change_1d", dtype=Float32),
                Field(name="price_change_7d", dtype=Float32),
                Field(name="market_cap", dtype=Float32),
                Field(name="sector", dtype=String)
            ],
            source="instrument_features_source"
        )

        return [client_features, instrument_features]

    def get_online_features(
        self,
        entity_rows: list,
        features: list
    ) -> pd.DataFrame:
        """Get features for online inference"""

        feature_vector = self.store.get_online_features(
            features=features,
            entity_rows=entity_rows
        )

        return feature_vector.to_df()

    def get_historical_features(
        self,
        entity_df: pd.DataFrame,
        features: list
    ) -> pd.DataFrame:
        """Get historical features for training"""

        training_df = self.store.get_historical_features(
            entity_df=entity_df,
            features=features
        ).to_df()

        return training_df
```

### 6.7 Model Serving & Inference

**Inference Service**:

```python
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Dict, Any
import numpy as np

app = FastAPI(title="AI Model Serving API")

class PredictionRequest(BaseModel):
    model_name: str
    features: Dict[str, Any]
    explain: bool = False

class PredictionResponse(BaseModel):
    prediction: Any
    probability: float = None
    explanation: Dict[str, Any] = None
    model_version: str
    inference_time_ms: float

class ModelInferenceService:
    def __init__(
        self,
        model_registry: ModelRegistry,
        feature_store: FeatureStoreManager
    ):
        self.model_registry = model_registry
        self.feature_store = feature_store
        self.loaded_models = {}

    def load_model(self, model_name: str, version: str = None):
        """Load model into memory"""

        cache_key = f"{model_name}:{version or 'production'}"

        if cache_key not in self.loaded_models:
            model = self.model_registry.load_model(model_name, version)
            self.loaded_models[cache_key] = model
            logger.info(f"Model loaded: {cache_key}")

        return self.loaded_models[cache_key]

    async def predict(
        self,
        model_name: str,
        features: Dict[str, Any],
        explain: bool = False
    ) -> PredictionResponse:
        """Make prediction"""

        start_time = time.time()

        # Load model
        model = self.load_model(model_name)

        # Get model version
        version = self.model_registry.get_model_version(model_name)

        # Prepare features
        feature_vector = self.prepare_features(features)

        # Make prediction
        prediction = model.predict(feature_vector)[0]

        # Get probability if available
        probability = None
        if hasattr(model, 'predict_proba'):
            probabilities = model.predict_proba(feature_vector)[0]
            probability = float(max(probabilities))

        # Generate explanation if requested
        explanation = None
        if explain:
            explanation = self.explain_prediction(
                model, feature_vector, prediction
            )

        inference_time = (time.time() - start_time) * 1000

        # Log prediction
        self.log_prediction(
            model_name, version, features, prediction, inference_time
        )

        return PredictionResponse(
            prediction=prediction,
            probability=probability,
            explanation=explanation,
            model_version=version,
            inference_time_ms=inference_time
        )

    def prepare_features(self, features: Dict[str, Any]) -> np.ndarray:
        """Prepare features for model input"""

        # Feature engineering and transformation
        # This is simplified; real implementation would use feature store
        feature_vector = np.array([list(features.values())])
        return feature_vector

    def explain_prediction(
        self,
        model,
        features: np.ndarray,
        prediction: Any
    ) -> Dict[str, Any]:
        """Generate explanation for prediction using SHAP"""

        import shap

        # Create explainer
        explainer = shap.TreeExplainer(model)

        # Calculate SHAP values
        shap_values = explainer.shap_values(features)

        # Get feature importance
        feature_importance = {
            f"feature_{i}": float(shap_values[0][i])
            for i in range(len(shap_values[0]))
        }

        return {
            'method': 'SHAP',
            'feature_importance': feature_importance,
            'base_value': float(explainer.expected_value)
        }

    def log_prediction(
        self,
        model_name: str,
        version: str,
        features: Dict[str, Any],
        prediction: Any,
        inference_time: float
    ):
        """Log prediction for monitoring"""

        logger.info("Prediction made", {
            'model_name': model_name,
            'model_version': version,
            'prediction': prediction,
            'inference_time_ms': inference_time
        })

@app.post("/predict", response_model=PredictionResponse)
async def predict(request: PredictionRequest):
    """Prediction endpoint"""

    try:
        response = await inference_service.predict(
            model_name=request.model_name,
            features=request.features,
            explain=request.explain
        )
        return response
    except Exception as e:
        logger.error(f"Prediction failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))
```

### 6.8 Model Monitoring & Drift Detection

**Monitoring System**:

```python
from scipy.stats import ks_2samp
import pandas as pd

class ModelMonitor:
    def __init__(self, model_name: str, reference_data: pd.DataFrame):
        self.model_name = model_name
        self.reference_data = reference_data
        self.drift_threshold = 0.05

    def detect_data_drift(self, current_data: pd.DataFrame) -> Dict[str, Any]:
        """Detect data drift using Kolmogorov-Smirnov test"""

        drift_detected = False
        drift_features = []

        for column in self.reference_data.columns:
            if column not in current_data.columns:
                continue

            # Perform KS test
            statistic, p_value = ks_2samp(
                self.reference_data[column],
                current_data[column]
            )

            if p_value < self.drift_threshold:
                drift_detected = True
                drift_features.append({
                    'feature': column,
                    'ks_statistic': statistic,
                    'p_value': p_value
                })

        return {
            'drift_detected': drift_detected,
            'drift_features': drift_features,
            'timestamp': datetime.now().isoformat(),
            'timestamp_bs': self.calendar_service.to_bs(datetime.now())  # K-15 Dual-Calendar
        }

    def detect_prediction_drift(
        self,
        predictions: pd.Series,
        window_size: int = 1000
    ) -> Dict[str, Any]:
        """Detect drift in prediction distribution"""

        # Calculate prediction statistics
        current_mean = predictions.tail(window_size).mean()
        historical_mean = predictions.head(window_size).mean()

        drift_percentage = abs(current_mean - historical_mean) / historical_mean * 100

        return {
            'current_mean': current_mean,
            'historical_mean': historical_mean,
            'drift_percentage': drift_percentage,
            'drift_detected': drift_percentage > 10,
            'timestamp': datetime.now().isoformat(),
            'timestamp_bs': self.calendar_service.to_bs(datetime.now())  # K-15 Dual-Calendar
        }

    def monitor_performance(
        self,
        predictions: pd.Series,
        actuals: pd.Series
    ) -> Dict[str, float]:
        """Monitor model performance metrics"""

        from sklearn.metrics import accuracy_score, precision_score, recall_score

        metrics = {
            'accuracy': accuracy_score(actuals, predictions),
            'precision': precision_score(actuals, predictions, average='weighted'),
            'recall': recall_score(actuals, predictions, average='weighted')
        }

        # Check for performance degradation
        for metric_name, metric_value in metrics.items():
            if metric_value < 0.75:  # Threshold
                logger.warning(
                    f"Performance degradation detected: {metric_name} = {metric_value:.3f}"
                )

        return metrics
```

### 6.9 AI Governance Framework

**Governance Policies**:

```python
from enum import Enum
from dataclasses import dataclass

class ModelRiskLevel(Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"

@dataclass
class GovernancePolicy:
    model_name: str
    risk_level: ModelRiskLevel
    requires_approval: bool
    approval_roles: List[str]
    monitoring_frequency: str
    retraining_frequency: str
    explainability_required: bool
    bias_testing_required: bool

class AIGovernanceManager:
    def __init__(self):
        self.policies = {}

    def register_policy(self, policy: GovernancePolicy):
        """Register governance policy for a model"""
        self.policies[policy.model_name] = policy

    def check_deployment_approval(
        self,
        model_name: str,
        version: str,
        approver: str
    ) -> bool:
        """Check if model deployment is approved"""

        policy = self.policies.get(model_name)
        if not policy:
            raise PolicyNotFoundError(f"No policy found for {model_name}")

        if not policy.requires_approval:
            return True

        # Check if approver has required role
        # This is simplified; real implementation would check against user roles
        return True

    def audit_model_usage(
        self,
        model_name: str,
        start_date: datetime,
        end_date: datetime
    ) -> Dict[str, Any]:
        """Generate audit report for model usage"""

        # Query prediction logs
        predictions = self.get_predictions(model_name, start_date, end_date)

        return {
            'model_name': model_name,
            'period': {
                'start': start_date.isoformat(),
                'end': end_date.isoformat()
            },
            'total_predictions': len(predictions),
            'avg_inference_time_ms': predictions['inference_time_ms'].mean(),
            'unique_users': predictions['user_id'].nunique(),
            'error_rate': (predictions['status'] == 'error').mean()
        }
```

---

## 7. Data Architecture

### 7.1 Overview

The Data Architecture provides a **comprehensive data management strategy** supporting:

- Multi-database polyglot persistence
- Real-time and batch data processing
- Data lake for analytics
- Data governance and lineage
- GDPR and data privacy compliance
- High availability and disaster recovery

### 7.2 Data Storage Strategy

| Data Type                      | Database           | Rationale                                              |
| ------------------------------ | ------------------ | ------------------------------------------------------ |
| **Transactional**              | PostgreSQL         | ACID compliance, complex queries                       |
| **Time-Series**                | TimescaleDB        | Optimized for time-series data                         |
| **Cache**                      | Redis              | Sub-millisecond latency                                |
| **Search**                     | Elasticsearch      | Full-text search, analytics                            |
| **Object Storage / Data Lake** | S3 + MinIO         | Historical analysis, exports, evidence bundles         |
| **Vector Search**              | pgvector           | Semantic retrieval and RAG support                     |
| **Shared Abstractions**        | Ghatana Data Cloud | Governance, lineage, storage routing, schema lifecycle |

### 7.3 Database Schema Design

> **Dual-Calendar Invariant (Architecture Principle §1.1)**: Every table that stores a user-facing or regulatory timestamp MUST include both a Gregorian `TIMESTAMPTZ` column and a Bikram Sambat `VARCHAR(30)` companion column (suffixed `_bs`). The K-15 Dual-Calendar Service provides conversion at write time. The schemas below show Gregorian columns; implementors MUST add `created_at_bs`, `updated_at_bs`, `order_date_bs`, `trade_time_bs`, etc. alongside each `TIMESTAMP` column. See LLD K-15 for the canonical conversion API.

> **Tenant Isolation Invariant**: All domain tables MUST include a `tenant_id UUID NOT NULL` column with Row-Level Security (RLS) policies enforced at the database layer. Schemas below omit `tenant_id` for brevity; it is mandatory.

**PostgreSQL - Core Transactional Data**:

```sql
-- Clients table
CREATE TABLE clients (
    client_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,                          -- RLS-enforced tenant isolation
    client_code VARCHAR(20) NOT NULL,
    client_name VARCHAR(200) NOT NULL,
    client_type VARCHAR(20) NOT NULL CHECK (client_type IN ('INDIVIDUAL', 'CORPORATE', 'INSTITUTIONAL')),
    pan VARCHAR(10),
    email VARCHAR(100),
    phone VARCHAR(20),
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    risk_category VARCHAR(20),
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at_bs VARCHAR(30) NOT NULL,               -- Bikram Sambat via K-15
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at_bs VARCHAR(30) NOT NULL,               -- Bikram Sambat via K-15
    created_by UUID,
    updated_by UUID,
    UNIQUE(tenant_id, client_code),
    UNIQUE(tenant_id, pan)
);

-- Row-Level Security
ALTER TABLE clients ENABLE ROW LEVEL SECURITY;
CREATE POLICY clients_tenant_isolation ON clients
    USING (tenant_id = current_setting('app.current_tenant')::UUID);

CREATE INDEX idx_clients_code ON clients(client_code);
CREATE INDEX idx_clients_pan ON clients(pan);
CREATE INDEX idx_clients_status ON clients(account_status);

-- Instruments table (shared across tenants — reference data)
CREATE TABLE instruments (
    instrument_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol VARCHAR(50) NOT NULL,
    isin VARCHAR(12) UNIQUE,
    exchange VARCHAR(20) NOT NULL,
    instrument_type VARCHAR(20) NOT NULL,
    sector VARCHAR(50),
    industry VARCHAR(50),
    market_cap DECIMAL(20,2),
    lot_size INTEGER,
    tick_size DECIMAL(10,4),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at_bs VARCHAR(30) NOT NULL,               -- Bikram Sambat via K-15
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at_bs VARCHAR(30) NOT NULL,               -- Bikram Sambat via K-15
    UNIQUE(symbol, exchange)
);

CREATE INDEX idx_instruments_symbol ON instruments(symbol);
CREATE INDEX idx_instruments_isin ON instruments(isin);
CREATE INDEX idx_instruments_exchange ON instruments(exchange);

-- Orders table (partitioned by date)
CREATE TABLE orders (
    order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,                          -- RLS-enforced
    client_id UUID NOT NULL REFERENCES clients(client_id),
    instrument_id UUID NOT NULL REFERENCES instruments(instrument_id),
    order_date DATE NOT NULL,
    order_date_bs VARCHAR(12) NOT NULL,               -- Bikram Sambat date via K-15
    order_type VARCHAR(20) NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity DECIMAL(18,4) NOT NULL,
    price DECIMAL(18,4),
    disclosed_quantity DECIMAL(18,4),
    trigger_price DECIMAL(18,4),
    time_in_force VARCHAR(10) NOT NULL DEFAULT 'DAY',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    filled_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    average_price DECIMAL(18,4),
    exchange_order_id VARCHAR(50),
    parent_order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at_bs VARCHAR(30) NOT NULL,               -- Bikram Sambat via K-15
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at_bs VARCHAR(30) NOT NULL,               -- Bikram Sambat via K-15
    created_by UUID,
    CONSTRAINT fk_parent_order FOREIGN KEY (parent_order_id) REFERENCES orders(order_id)
) PARTITION BY RANGE (order_date);

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY orders_tenant_isolation ON orders
    USING (tenant_id = current_setting('app.current_tenant')::UUID);

-- Create partitions for orders
CREATE TABLE orders_2024_q1 PARTITION OF orders
    FOR VALUES FROM ('2024-01-01') TO ('2024-04-01');

CREATE TABLE orders_2024_q2 PARTITION OF orders
    FOR VALUES FROM ('2024-04-01') TO ('2024-07-01');

CREATE TABLE orders_2024_q3 PARTITION OF orders
    FOR VALUES FROM ('2024-07-01') TO ('2024-10-01');

CREATE TABLE orders_2024_q4 PARTITION OF orders
    FOR VALUES FROM ('2024-10-01') TO ('2025-01-01');

-- Indexes on orders
CREATE INDEX idx_orders_client ON orders(client_id, order_date DESC);
CREATE INDEX idx_orders_instrument ON orders(instrument_id, order_date DESC);
CREATE INDEX idx_orders_status ON orders(status) WHERE status IN ('PENDING', 'SUBMITTED');
CREATE INDEX idx_orders_exchange ON orders(exchange_order_id) WHERE exchange_order_id IS NOT NULL;

-- Trades table (partitioned by date)
CREATE TABLE trades (
    trade_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,                          -- RLS-enforced
    order_id UUID NOT NULL REFERENCES orders(order_id),
    client_id UUID NOT NULL REFERENCES clients(client_id),
    instrument_id UUID NOT NULL REFERENCES instruments(instrument_id),
    trade_date DATE NOT NULL,
    trade_date_bs VARCHAR(12) NOT NULL,               -- Bikram Sambat date via K-15
    trade_time TIMESTAMPTZ NOT NULL,
    trade_time_bs VARCHAR(30) NOT NULL,               -- Bikram Sambat timestamp via K-15
    side VARCHAR(4) NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    price DECIMAL(18,4) NOT NULL,
    trade_value DECIMAL(20,2) NOT NULL,
    exchange_trade_id VARCHAR(50) NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    settlement_date DATE,
    settlement_date_bs VARCHAR(12),                   -- Bikram Sambat settlement date
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at_bs VARCHAR(30) NOT NULL,               -- Bikram Sambat via K-15
    UNIQUE(tenant_id, exchange_trade_id)
) PARTITION BY RANGE (trade_date);

ALTER TABLE trades ENABLE ROW LEVEL SECURITY;
CREATE POLICY trades_tenant_isolation ON trades
    USING (tenant_id = current_setting('app.current_tenant')::UUID);

-- Positions table
CREATE TABLE positions (
    position_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,                          -- RLS-enforced
    client_id UUID NOT NULL REFERENCES clients(client_id),
    instrument_id UUID NOT NULL REFERENCES instruments(instrument_id),
    position_date DATE NOT NULL,
    position_date_bs VARCHAR(12) NOT NULL,            -- Bikram Sambat date via K-15
    quantity DECIMAL(18,4) NOT NULL,
    average_price DECIMAL(18,4) NOT NULL,
    market_value DECIMAL(20,2),
    unrealized_pnl DECIMAL(20,2),
    realized_pnl DECIMAL(20,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at_bs VARCHAR(30) NOT NULL,               -- Bikram Sambat via K-15
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at_bs VARCHAR(30) NOT NULL,               -- Bikram Sambat via K-15
    UNIQUE(tenant_id, client_id, instrument_id, position_date)
);

CREATE INDEX idx_positions_client ON positions(client_id, position_date DESC);
CREATE INDEX idx_positions_instrument ON positions(instrument_id, position_date DESC);
```

**TimescaleDB - Time-Series Data**:

```sql
-- Market data ticks (hypertable)
-- Note: Market ticks use TIMESTAMPTZ only (no BS companion) because:
-- (1) Sub-second tick data is machine-generated, not user-facing
-- (2) BS conversion is applied at query/display time via K-15
CREATE TABLE market_data_ticks (
    time TIMESTAMPTZ NOT NULL,
    instrument_id UUID NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    last_price DECIMAL(18,4),
    bid_price DECIMAL(18,4),
    ask_price DECIMAL(18,4),
    bid_quantity DECIMAL(18,4),
    ask_quantity DECIMAL(18,4),
    volume DECIMAL(18,4),
    open_interest DECIMAL(18,4),
    high_price DECIMAL(18,4),
    low_price DECIMAL(18,4),
    open_price DECIMAL(18,4),
    close_price DECIMAL(18,4)
);

-- Convert to hypertable
SELECT create_hypertable('market_data_ticks', 'time');

-- Create indexes
CREATE INDEX idx_market_data_instrument_time ON market_data_ticks (instrument_id, time DESC);
CREATE INDEX idx_market_data_exchange ON market_data_ticks (exchange, time DESC);

-- Continuous aggregates for OHLCV data
CREATE MATERIALIZED VIEW market_data_1min
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 minute', time) AS bucket,
    instrument_id,
    exchange,
    FIRST(last_price, time) AS open,
    MAX(last_price) AS high,
    MIN(last_price) AS low,
    LAST(last_price, time) AS close,
    SUM(volume) AS volume
FROM market_data_ticks
GROUP BY bucket, instrument_id, exchange;

-- Retention policy: Keep raw ticks for 7 days (operational)
-- Note: This is operational raw-tick retention, NOT regulatory retention (10 years).
-- Aggregated OHLCV data in market_data_1min is retained for 10+ years per K-02 config.
SELECT add_retention_policy('market_data_ticks', INTERVAL '7 days');

-- Compression policy: Compress data older than 1 day
ALTER TABLE market_data_ticks SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'instrument_id,exchange'
);

SELECT add_compression_policy('market_data_ticks', INTERVAL '1 day');
```

### 7.4 Ghatana Data Cloud Abstractions

Siddhanta does not standardize on MongoDB as a required document store. For semi-structured and governance-heavy datasets, the platform uses Ghatana Data Cloud abstractions on top of the canonical stores chosen in [../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md).

**Logical to Physical Mapping**:

| Logical Dataset                    | Physical Baseline                             | Notes                                                                             |
| ---------------------------------- | --------------------------------------------- | --------------------------------------------------------------------------------- |
| Corporate actions payloads         | PostgreSQL JSONB + object storage attachments | Structured query path stays in PostgreSQL; large artifacts move to object storage |
| Regulatory filings                 | PostgreSQL JSONB + object storage             | Filing metadata remains queryable; original documents stored immutably            |
| AI retrieval corpora               | pgvector + object storage                     | Embeddings and source documents governed through K-08/K-09                        |
| Metadata, lineage, retention state | Ghatana Data Cloud platform                   | Shared governance and lifecycle hooks                                             |

**Reference Schema Pattern**:

```sql
CREATE TABLE corporate_actions_documents (
    ca_id UUID PRIMARY KEY,
    instrument_id UUID NOT NULL,
    ca_type VARCHAR(50) NOT NULL,
    record_date DATE NOT NULL,
    record_date_bs VARCHAR(12) NOT NULL,
    status VARCHAR(50) NOT NULL,
    details JSONB NOT NULL,
    attachment_manifest JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at_bs VARCHAR(30) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at_bs VARCHAR(30) NOT NULL
);

CREATE INDEX idx_ca_documents_type_status
    ON corporate_actions_documents (ca_type, status);

CREATE INDEX idx_ca_documents_details_gin
    ON corporate_actions_documents
    USING GIN (details);
```

**Implementation Rule**:

- Use Ghatana `products:data-cloud:platform` and `products:data-cloud:spi` for shared storage abstractions, lineage hooks, and lifecycle policy enforcement.
- Prefer PostgreSQL JSONB, object storage, Elasticsearch, and pgvector before introducing a new specialized datastore.

### 7.5 Redis - Caching Strategy

**Cache Patterns**:

```typescript
class CacheManager {
  private redis: Redis;

  constructor(redisClient: Redis) {
    this.redis = redisClient;
  }

  // Cache-aside pattern
  async get<T>(
    key: string,
    fetchFn: () => Promise<T>,
    ttl: number = 300,
  ): Promise<T> {
    // Try to get from cache
    const cached = await this.redis.get(key);

    if (cached) {
      return JSON.parse(cached);
    }

    // Fetch from source
    const data = await fetchFn();

    // Store in cache
    await this.redis.setex(key, ttl, JSON.stringify(data));

    return data;
  }

  // Write-through pattern
  async set<T>(key: string, value: T, ttl: number = 300): Promise<void> {
    await this.redis.setex(key, ttl, JSON.stringify(value));
  }

  // Invalidate cache
  async invalidate(pattern: string): Promise<void> {
    const keys = await this.redis.keys(pattern);

    if (keys.length > 0) {
      await this.redis.del(...keys);
    }
  }

  // Real-time position cache
  async updatePosition(
    clientId: string,
    instrumentId: string,
    position: Position,
  ): Promise<void> {
    const key = `position:${clientId}:${instrumentId}`;
    await this.redis.hset(key, {
      quantity: position.quantity,
      averagePrice: position.averagePrice,
      marketValue: position.marketValue,
      unrealizedPnl: position.unrealizedPnl,
      updatedAt: new Date().toISOString(),
    });

    // Set expiry
    await this.redis.expire(key, 3600);
  }

  async getPosition(
    clientId: string,
    instrumentId: string,
  ): Promise<Position | null> {
    const key = `position:${clientId}:${instrumentId}`;
    const data = await this.redis.hgetall(key);

    if (!data || Object.keys(data).length === 0) {
      return null;
    }

    return {
      quantity: parseFloat(data.quantity),
      averagePrice: parseFloat(data.averagePrice),
      marketValue: parseFloat(data.marketValue),
      unrealizedPnl: parseFloat(data.unrealizedPnl),
      updatedAt: new Date(data.updatedAt),
    };
  }
}
```

### 7.6 Data Lake Architecture

**S3/MinIO-based Data Lake via Ghatana Data Cloud**:

```
s3://siddhanta-data-lake/
├── raw/                          # Raw data ingestion
│   ├── market-data/
│   │   ├── year=2024/
│   │   │   ├── month=01/
│   │   │   │   ├── day=15/
│   │   │   │   │   └── ticks.parquet
│   ├── orders/
│   ├── trades/
│   └── corporate-actions/
├── processed/                    # Cleaned and transformed data
│   ├── market-data/
│   ├── analytics/
│   └── reports/
└── curated/                      # Business-ready datasets
    ├── client-analytics/
    ├── risk-metrics/
    └── performance-reports/
```

**Data Pipeline Pattern**:

```text
TimescaleDB / PostgreSQL / Kafka
    ↓
Ghatana Event Cloud / AEP ingestion
    ↓
Ghatana Data Cloud lifecycle + schema hooks
    ↓
S3 / MinIO raw and processed zones
    ↓
Curated analytics datasets and regulatory exports
```

**Implementation Rule**:

- Use Ghatana event and data products for ingestion, transformation coordination, and lifecycle enforcement.
- Keep data-lake storage on S3/MinIO-compatible object storage.
- Publish curated datasets through governed schemas rather than ad hoc ETL pipelines.

---

## 8. Deployment Architecture

### 8.1 Overview

The Deployment Architecture provides a **cloud-native, highly available, and scalable infrastructure** supporting:

- Multi-cloud deployment (AWS, Azure, GCP)
- Kubernetes-based container orchestration
- Auto-scaling based on load
- Blue-green and canary deployments
- Disaster recovery and business continuity
- Multi-region deployment

### 8.2 Infrastructure Components

```
┌─────────────────────────────────────────────────────────────┐
│                    CDN & WAF Layer                           │
│  (CloudFront, Cloudflare, DDoS Protection)                  │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Load Balancer Layer                       │
│  (ALB, NLB, Global Load Balancing)                          │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                        │
│  (EKS, AKS, GKE - Multi-AZ Deployment)                      │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Service Mesh Layer                        │
│  (Istio - Traffic Management, Security, Observability)      │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Application Services                      │
│  (Microservices in Pods)                                    │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Data Layer                                │
│  (PostgreSQL, TimescaleDB, Redis, Elasticsearch, S3/MinIO) │
└─────────────────────────────────────────────────────────────┘
```

### 8.3 Kubernetes Cluster Architecture

**EKS Cluster Configuration**:

```yaml
apiVersion: eksctl.io/v1alpha5
kind: ClusterConfig

metadata:
  name: siddhanta-prod
  region: ap-south-1
  version: "1.28"

vpc:
  cidr: 10.0.0.0/16
  nat:
    gateway: HighlyAvailable

availabilityZones:
  - ap-south-1a
  - ap-south-1b
  - ap-south-1c

managedNodeGroups:
  - name: trading-services
    instanceType: c5.2xlarge
    desiredCapacity: 6
    minSize: 3
    maxSize: 12
    volumeSize: 100
    volumeType: gp3
    labels:
      workload: trading
    tags:
      Environment: production
      Team: trading
    iam:
      withAddonPolicies:
        autoScaler: true
        cloudWatch: true
        ebs: true

  - name: data-services
    instanceType: r5.xlarge
    desiredCapacity: 4
    minSize: 2
    maxSize: 8
    volumeSize: 200
    volumeType: gp3
    labels:
      workload: data
    tags:
      Environment: production
      Team: data-engineering

addons:
  - name: vpc-cni
  - name: coredns
  - name: kube-proxy
  - name: aws-ebs-csi-driver

cloudWatch:
  clusterLogging:
    enableTypes:
      - api
      - audit
      - authenticator
      - controllerManager
      - scheduler
```

### 8.4 Service Deployment Configuration

**Order Service Deployment**:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: trading
  labels:
    app: order-service
    version: v1.0.0
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
        version: v1.0.0
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
        prometheus.io/path: "/metrics"
    spec:
      serviceAccountName: order-service
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000

      containers:
        - name: order-service
          image: 123456789.dkr.ecr.ap-south-1.amazonaws.com/order-service:1.0.0
          imagePullPolicy: IfNotPresent

          ports:
            - name: http
              containerPort: 8080
              protocol: TCP
            - name: metrics
              containerPort: 9090
              protocol: TCP

          env:
            - name: NODE_ENV
              value: "production"
            - name: LOG_LEVEL
              value: "info"
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: order-service-secrets
                  key: database-url
            - name: KAFKA_BROKERS
              value: "kafka-0.kafka-headless:9092,kafka-1.kafka-headless:9092,kafka-2.kafka-headless:9092"
            - name: REDIS_URL
              valueFrom:
                secretKeyRef:
                  name: order-service-secrets
                  key: redis-url

          resources:
            requests:
              memory: "512Mi"
              cpu: "500m"
            limits:
              memory: "1Gi"
              cpu: "1000m"

          livenessProbe:
            httpGet:
              path: /health/live
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3

          readinessProbe:
            httpGet:
              path: /health/ready
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 3

          volumeMounts:
            - name: config
              mountPath: /app/config
              readOnly: true
            - name: tmp
              mountPath: /tmp

      volumes:
        - name: config
          configMap:
            name: order-service-config
        - name: tmp
          emptyDir: {}

      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchExpressions:
                    - key: app
                      operator: In
                      values:
                        - order-service
                topologyKey: kubernetes.io/hostname
---
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: trading
  labels:
    app: order-service
spec:
  type: ClusterIP
  selector:
    app: order-service
  ports:
    - name: http
      port: 80
      targetPort: 8080
      protocol: TCP
    - name: metrics
      port: 9090
      targetPort: 9090
      protocol: TCP
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
  namespace: trading
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 3
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Percent
          value: 50
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 10
          periodSeconds: 60
```

### 8.5 Istio Service Mesh Configuration

**Virtual Service & Destination Rule**:

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: order-service
  namespace: trading
spec:
  hosts:
    - order-service
  http:
    - match:
        - headers:
            version:
              exact: v2
      route:
        - destination:
            host: order-service
            subset: v2
          weight: 100
    - route:
        - destination:
            host: order-service
            subset: v1
          weight: 90
        - destination:
            host: order-service
            subset: v2
          weight: 10
      timeout: 30s
      retries:
        attempts: 3
        perTryTimeout: 10s
        retryOn: 5xx,reset,connect-failure,refused-stream
---
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: order-service
  namespace: trading
spec:
  host: order-service
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 100
      http:
        http1MaxPendingRequests: 50
        http2MaxRequests: 100
        maxRequestsPerConnection: 2
    loadBalancer:
      simple: LEAST_REQUEST
    outlierDetection:
      consecutiveErrors: 5
      interval: 30s
      baseEjectionTime: 30s
      maxEjectionPercent: 50
      minHealthPercent: 40
  subsets:
    - name: v1
      labels:
        version: v1.0.0
    - name: v2
      labels:
        version: v2.0.0
```

### 8.6 CI/CD Pipeline

**GitHub Actions + ArgoCD Baseline**:

```yaml
name: build-test-deploy

on:
  push:
    branches: [main]
  pull_request:

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
      - uses: pnpm/action-setup@v4
        with:
          version: 9
      - run: ./gradlew test
      - run: pnpm install --frozen-lockfile
      - run: pnpm test
      - run: docker build -t ghcr.io/siddhanta/order-service:${{ github.sha }} .
      - run: trivy image --severity HIGH,CRITICAL ghcr.io/siddhanta/order-service:${{ github.sha }}

  sync-staging:
    needs: validate
    runs-on: ubuntu-latest
    steps:
      - run: argocd app sync siddhanta-staging
```

---

## Summary

This document (Part 2, Sections 6-8) covers:

6. **AI Governance & Integration Architecture**: Model registry, training pipelines, feature store, model serving, monitoring, drift detection, governance framework, and Ghatana AI platform reuse.

7. **Data Architecture**: Polyglot persistence strategy, database schemas (PostgreSQL, TimescaleDB, Redis, Elasticsearch, object storage, pgvector), caching with Redis, data lake architecture, and Ghatana Data Cloud alignment.

8. **Deployment Architecture**: Kubernetes cluster setup, service deployment configurations, Istio service mesh, auto-scaling, and GitHub Actions + ArgoCD delivery pipelines.

**Next**: Part 2, Sections 9-10 (Security Architecture & Observability Architecture).
