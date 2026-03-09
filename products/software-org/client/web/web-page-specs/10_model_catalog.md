# 10. Model Catalog – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 10. `/models` – Model Catalog](../WEB_PAGE_FEATURE_INVENTORY.md#10-models--model-catalog)

**Code file:**

- `src/features/models/pages/ModelCatalog.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Act as a central registry and control panel for all ML models, showing versions, performance, deployment status, and providing comparison and testing tools.

**Primary goals:**

- List models with **status**, **current version**, and **core metrics**.
- Let users **select multiple models** to compare.
- Provide **details view** for one model and a **test runner** view.

**Non-goals:**

- Editing model training pipelines (covered by ML platform elsewhere).
- Deep feature-level analysis (ML Observatory handles that).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **ML engineer / Data scientist.**
- **MLOps / Platform engineer.**
- **SRE** monitoring model health.

**Scenarios:**

1. **Reviewing available models**
   - GIVEN: Several models are in development/testing/production.
   - WHEN: ML engineer opens the catalog.
   - THEN: They see each model’s status and performance at a glance.

2. **Comparing candidate models**
   - GIVEN: Two candidate models for fraud detection.
   - WHEN: Engineer selects both and opens Comparison view.
   - THEN: They can compare accuracy, precision, recall, F1, latency, throughput.

3. **Running tests**
   - GIVEN: New model version is ready.
   - WHEN: Engineer opens Test Runner.
   - THEN: They run evaluation suites and view results.

---

## 3. Content & Layout Overview

From `ModelCatalog.tsx`:

- **Header:**
  - Title: `Model Catalog`.
  - Buttons: `+ Deploy Model`, `🧪 Run Tests`, `⚖️ Compare (N)`.

- **Views:**
  - **Catalog** (default) – model list.
  - **Details** – `ModelDetails` for one model.
  - **Compare** – `ModelComparison` for 2+ models.
  - **Test** – `TestRunner`.

- **Catalog view content:**
  - Model list from `useModelRegistry()`.
  - Each model row/card includes:
    - Name, status badge, version.
    - Type.
    - Performance metrics (accuracy, precision, recall, F1).
    - Last updated, deployed at.
    - Checkbox for comparison selection.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Status clarity:**
  - Color-coded badges (Active, Testing, Deprecated, Archived).
- **Selection feedback:**
  - Checkboxes should show how many models are selected for comparison.
- **Navigation:**
  - Clicking a model name opens Details view.
  - `Run Tests` opens Test view.
  - `Compare` button is disabled until 2+ models are selected.

---

## 5. Completeness and Real-World Coverage

Model Catalog should answer:

- What models exist? Which are active? Which are being tested or deprecated?
- How good is each model (accuracy, precision, recall, F1)?
- Which model versions are currently deployed?

Over time it can integrate with:

- ML Observatory for deeper operational metrics.
- Automation Engine to see where models are used in workflows.

---

## 6. Modern UI/UX Nuances and Features

- Scrollable list for many models.
- Sticky header section with actions.
- Clear separation between metadata and metrics.
- Hover states and accessible checkbox labels.

---

## 7. Coherence and Consistency Across the App

- Metric terminology (accuracy, precision, recall, F1) consistent with Help Center guides.
- Model status semantics align with ML Observatory.
- Deployment info matches Real-Time Monitor/Automation contexts.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#10-models--model-catalog`
- Implementation: `src/features/models/pages/ModelCatalog.tsx`
- Model registry hook: `src/hooks/useModelRegistry.ts`
- Model API: `src/services/api/modelsApi.ts`

---

## 9. Open Gaps & Enhancement Plan

- Add filters/search (by name, status, type).
- Show per-version history inside Details view.
- Integrate with deployment environments (staging/prod).

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch (Catalog View)

```text
H1: Model Catalog
[ + Deploy Model ]  [ 🧪 Run Tests ]       [ ⚖️ Compare (2) ]

[Model List]
+-----------------------------------------------------------------+
| [ ] FraudDetector-v3   [ACTIVE]   v3      Classification Model   |
|   Accuracy: 98.2%  Precision: 97.5%  Recall: 96.8%  F1: 97.1%    |
|   Updated: 2025-11-18   Deployed: 2025-11-19 14:32 UTC           |
+-----------------------------------------------------------------+
| [x] ChurnPredictor-v2  [TESTING]  v2      Regression Model      |
|   Accuracy: 91.4%  Precision: 89.0%  Recall: 90.2%  F1: 89.6%    |
|   Updated: 2025-11-15   Deployed: -                              |
+-----------------------------------------------------------------+
| [x] AnomalyDetector-v1 [DEPRECATED] v1    Anomaly Detection     |
|   Accuracy: 94.3%  Precision: 93.8%  Recall: 92.1%  F1: 92.9%    |
|   Updated: 2025-09-01   Deployed: 2025-09-10 (retired)          |
+-----------------------------------------------------------------+
```

### 10.2 Example Model Data

1. **FraudDetector-v3**
   - Status: `active`
   - Current Version: `3`
   - Type: `Classification`
   - Accuracy: `0.982`
   - Precision: `0.975`
   - Recall: `0.968`
   - F1 Score: `0.971`
   - Latency: `45ms`
   - Throughput: `1200 req/s`
   - Last Updated: `2025-11-18`
   - Deployed At: `2025-11-19 14:32 UTC`

2. **ChurnPredictor-v2**
   - Status: `testing`
   - Current Version: `2`
   - Type: `Regression`
   - Accuracy: `0.914`
   - Precision: `0.890`
   - Recall: `0.902`
   - F1 Score: `0.896`
   - Latency: `80ms`
   - Throughput: `600 req/s`

3. **AnomalyDetector-v1**
   - Status: `deprecated`
   - Current Version: `1`
   - Type: `Anomaly Detection`
   - Accuracy: `0.943`
   - Precision: `0.938`
   - Recall: `0.921`
   - F1 Score: `0.929`

### 10.3 Example Compare View Summary

When comparing `ChurnPredictor-v2` and `AnomalyDetector-v1`:

```text
Compare Models

Model              Version   Accuracy   Precision   Recall   F1     Latency  Throughput
ChurnPredictor-v2  v2        91.4%      89.0%       90.2%    89.6%  80ms     600 req/s
AnomalyDetector-v1 v1        94.3%      93.8%       92.1%    92.9%  65ms     800 req/s
```

This mockup reflects realistic catalog usage with concrete performance metrics and layout.
