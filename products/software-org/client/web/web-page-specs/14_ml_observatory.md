# 14. ML Observatory – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 14. `/ml-observatory` – ML Observatory](../WEB_PAGE_FEATURE_INVENTORY.md#14-ml-observatory--ml-observatory)

**Code file:**

- `src/pages/MLObservatory.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a unified monitoring dashboard for ML models in production, covering performance, drift, feature importance, training jobs, and experiments.

**Primary goals:**

- Show **model health** at a glance (active vs healthy vs drifted).
- Visualize **drift indicators** for key models.
- Expose **feature importance** for interpretation.
- Summarize **training jobs** and **A/B tests**.

**Non-goals:**

- Managing training pipelines themselves.
- Detailed data science notebooks (those live elsewhere).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **ML engineers / Data scientists.**
- **MLOps engineers.**
- **SREs** responsible for model reliability.

**Scenarios:**

1. **Monitoring drift**
   - GIVEN: Fraud model might be seeing new traffic patterns.
   - WHEN: ML engineer checks ML Observatory and sees drift score rising.
   - THEN: They schedule retraining or adjust thresholds.

2. **Explaining model behavior**
   - GIVEN: Stakeholders ask which features most affect predictions.
   - WHEN: Data scientist opens Feature Importance chart.
   - THEN: They show which features contribute most.

3. **Tracking training jobs and A/B tests**
   - GIVEN: New model version is in experiment.
   - WHEN: MLOps checks Training Jobs monitor and A/B dashboard.
   - THEN: They confirm training completed and experiment results are positive.

---

## 3. Content & Layout Overview

From `MLObservatory.tsx`:

- **Header:**
  - Title: `ML Observatory`.
  - Subtitle: `Unified view of model health, drift and experiments`.

- **Stats row:**
  - Active Models.
  - Healthy Models.
  - Drift Alerts.
  - Training Jobs.

- **Main layout:**
  - Grid of `MLModelCard` components.
  - Drift Detection section: `DriftIndicator` for selected/highlighted model(s).
  - Feature Importance section: `FeatureImportanceChart`.
  - Side/secondary panels:
    - `ModelComparisonPanel`.
    - `TrainingJobsMonitor`.
    - `AbTestDashboard`.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Plain-language explanations:**
  - Drift and feature importance must have tooltips and short help text.
- **Visual cues:**
  - Drift severity shown via color and icons.
- **Comparison ease:**
  - Selecting multiple models should be obvious to compare in the panel.
- **Drill-down:**
  - Clicking a model card opens more detail (e.g., link to Model Catalog entry).

---

## 5. Completeness and Real-World Coverage

Observatory should help answer:

- Are our production models still performing well?
- Are they seeing different data than they were trained on?
- Which features drive predictions?
- Are experiments and training jobs healthy and current?

---

## 6. Modern UI/UX Nuances and Features

- Card layout for models with quick health indicators.
- Line/bar charts for drift and feature importance.
- Filters for model type or business domain (future enhancement).
- Responsive design to show cards in multiple columns on desktop.

---

## 7. Coherence and Consistency Across the App

- Links to `Model Catalog` for more metadata.
- Drift alerts should tie into AI insights and Real-Time Monitor.
- Training jobs and A/B tests should align with Automation/ML pipelines.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#14-ml-observatory--ml-observatory`
- Implementation: `src/pages/MLObservatory.tsx`
- Related hooks/components: `src/features/models/hooks/useMLOrchestration.ts` and observability helpers.

---

## 9. Open Gaps & Enhancement Plan

- Wire drift and metrics to real monitoring backend.
- Add per-model history charts for key metrics.
- Highlight models that are both high-impact and high-drift.

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
H1: ML Observatory
Subtitle: Unified view of model health, drift and experiments

[Stats]
Active Models: 5   Healthy: 4   Drift Alerts: 1   Training Jobs: 3 running

[Model Cards Grid]
[ FraudDetector-v3  | ChurnPredictor-v2 | AnomalyDetector-v1 | ... ]

[Drift Detection]   [Feature Importance]
[Model Comparison | Training Jobs | A/B Tests]
```

### 10.2 Sample Model Cards

1. **FraudDetector-v3**
   - Status: `Healthy`
   - Drift Score: `0.12` (low)
   - AUC: `0.982`
   - Requests/min: `1,200`

2. **ChurnPredictor-v2**
   - Status: `At Risk`
   - Drift Score: `0.37` (medium)
   - R²: `0.84`
   - Requests/min: `350`

3. **AnomalyDetector-v1**
   - Status: `Drifted`
   - Drift Score: `0.61` (high – red)
   - Precision@k: `0.91`
   - Requests/min: `700`

### 10.3 Sample Drift Indicator for AnomalyDetector-v1

```text
Data Drift – AnomalyDetector-v1

Overall Drift Score: 0.61 (HIGH)

Top drifting features:
- request_path_pattern    0.78
- country_code            0.64
- device_type             0.58

Recommendation:
Retrain model with last 30 days of traffic and review thresholds.
```

### 10.4 Sample Feature Importance for FraudDetector-v3

Top features:

- `transaction_amount` – 0.32
- `merchant_risk_score` – 0.24
- `user_country` – 0.18
- `device_fingerprint_risk` – 0.11
- `time_since_last_transaction` – 0.07

Accompanying copy:

> "Higher values indicate features that contribute more to the model’s decision."

### 10.5 Sample Training Jobs & A/B Tests

**Training Jobs Monitor:**

```text
Jobs (last 7 days)
- train-fraud-v3.1   Status: Running   Started: 2025-11-20 09:15
- train-churn-v2.1   Status: Succeeded Started: 2025-11-19 21:00   Duration: 2h 10m
- train-anom-v1.2    Status: Failed    Started: 2025-11-18 18:30   Duration: 45m
```

**A/B Test Dashboard:**

```text
Experiment: fraud_model_v3_vs_v2

Variant    Win Rate   Conversion Lift
v3         54%        +1.8%
v2         46%        baseline

Status: Running (7 days remaining)
```

These examples concretely describe the ML Observatory layout and the kinds of values users should see.
