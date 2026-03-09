# 9. AI Intelligence – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 9. `/ai` – AI Intelligence](../WEB_PAGE_FEATURE_INVENTORY.md#9-ai--ai-intelligence)

**Code file:**

- `src/features/ai/AiIntelligence.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a centralized feed of AI-generated insights and recommendations about quality, performance, security, and anomalies, along with clear approval workflows.

**Primary goals:**

- Show **insights list** with category, confidence, and short reasoning.
- Let users **filter by status** (All, Pending, Approved).
- Provide a **detail panel** for the selected insight with full reasoning and recommended actions.
- Allow **Approve/Defer/Reject** for pending insights.

**Non-goals:**

- Tuning model internals.
- Low-level data analysis (the insights summarize that work).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **SRE / Ops engineer.**
- **QA lead.**
- **Security engineer.**
- **Platform engineer.**

**Scenarios:**

1. **Handling test flakiness**
   - GIVEN: Tests have been flaky.
   - WHEN: QA lead sees an insight `Test Flakiness Pattern Detected`.
   - THEN: They read reasoning and recommended changes, then Approve or Defer.

2. **Responding to a performance spike**
   - GIVEN: Latency increased in peak hours.
   - WHEN: SRE sees `Payment Latency Anomaly` insight.
   - THEN: They use recommendation to adjust auto-scaling.

3. **Security posture confirmation**
   - GIVEN: Security wants to confirm there are no new critical CVEs.
   - WHEN: They see `No Critical CVEs Detected` marked as Approved.
   - THEN: They are reassured that scans are up to date.

---

## 3. Content & Layout Overview

From `AiIntelligence.tsx`:

- **Header:**
  - Title: `AI Intelligence`.
  - Subtitle: `AI-generated insights and recommendations for system optimization`.

- **Stats bar (4 cards):**
  - Total Insights.
  - Pending Review.
  - Approved.
  - Avg Confidence.

- **Filters row:**
  - Buttons: `All`, `Pending`, `Approved`.

- **Main grid:**
  - Left (2/3): Insights list.
  - Right (1/3): Insight details panel.

- **Insights list cards:**
  - Title.
  - Short reasoning preview.
  - Confidence badge.
  - Category badge (Security, Quality, Performance, Anomaly).

- **Detail panel:**
  - Full title.
  - Confidence badge.
  - Full reasoning.
  - Recommendation.
  - Estimated benefit.
  - Status indicator or buttons (Approve/Defer/Reject) depending on status.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Readable language:**
  - Reasoning and recommendations should be plain language with minimal jargon.
- **Confidence communication:**
  - Color-coded confidence badge (e.g., High/Medium/Lower) with percentage.
- **Decision clarity:**
  - Buttons clearly labeled, with optional short explanation on what happens on Approve/Defer/Reject.
- **Filter behavior:**
  - Filtering should update the list immediately and keep selected insight if still visible.

---

## 5. Completeness and Real-World Coverage

Insights cover categories:

- `Quality` (test flakiness, coverage issues).
- `Performance` (deployment time spikes, latency anomalies).
- `Security` (CVE posture).
- `Anomaly` (unusual metrics).

Together they give a multi-dimensional view of system health and improvement opportunities.

---

## 6. Modern UI/UX Nuances and Features

- Visually distinct category badges.
- Hover state for cards; selected card border or background.
- Detail panel with clearly separated sections (Reasoning, Recommendation, Estimated Benefit).
- Success/confirmation messaging when decisions are made (future enhancement).

---

## 7. Coherence and Consistency Across the App

- Insight types map to real metrics and events from Dashboard, Real-Time Monitor, etc.
- Pending/Approved concepts align with HITL Console and Automation Engine.
- Security insights should align with Security Dashboard findings.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#9-ai--ai-intelligence`
- Implementation: `src/features/ai/AiIntelligence.tsx`

Future navigation:

- From an insight → open related page (e.g., Real-Time Monitor for performance anomalies, Security for CVEs, Automation Engine for rollout recommendations).

---

## 9. Open Gaps & Enhancement Plan

- Wire to real `/api/v1/ai/insights` API.
- Add links from each insight to affected services/departments/models.
- Persist decision history with timestamps and actors.

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
H1: AI Intelligence
Subtitle: AI-generated insights and recommendations for system optimization

[Stats Bar]
Total Insights: 4   Pending Review: 3   Approved: 1   Avg Confidence: 93.1%

[Filters]
[ All ]  [ Pending ]  [ Approved ]

[Main Grid]
+--------------------------------------+-----------------------------+
| Insights List (left, 2/3)           | Insight Details (right, 1/3)|
+--------------------------------------+-----------------------------+
```

### 10.2 Sample Insights (From Mock Data)

1. **i-001 – Test Flakiness Pattern Detected**
   - Category: `Quality`
   - Confidence: `92%`
   - Status: `Pending`
   - Reasoning (short): `Detected 12% increase in test flakiness in the last 48 hours...`
   - Recommendation: `Increase test timeout and add retry logic for DB connection timeouts.`
   - Estimated Savings: `~8 hours/week in debugging time`.

2. **i-002 – Deployment Time Spike**
   - Category: `Performance`
   - Confidence: `87%`
   - Status: `Pending`
   - Reasoning: `Average deployment time increased 34%... Root cause: dependency caching issues.`
   - Recommendation: `Optimize container build steps and layer caching.`
   - Estimated Savings: `~15 minutes per deploy`.

3. **i-003 – No Critical CVEs Detected**
   - Category: `Security`
   - Confidence: `99%`
   - Status: `Approved`
   - Reasoning: `Comprehensive scan across all dependencies. No critical/high issues found.`
   - Recommendation: `Patch medium issues within 30 days; continue weekly scans.`
   - Estimated Benefit: `Security compliance maintained`.

4. **i-004 – Payment Latency Anomaly**
   - Category: `Anomaly`
   - Confidence: `94%`
   - Status: `Pending`
   - Reasoning: `P95 latency increased from 120ms to 285ms during peak hours...`
   - Recommendation: `Auto-scale payment service replicas and increase connection pool.`
   - Estimated Benefit: `~2% improvement in user experience`.

### 10.3 Example Insight Card Rendering (List)

```text
[Test Flakiness Pattern Detected]         [92% confidence] [Quality]
Detected 12% increase in test flakiness in the last 48 hours...
Status: Pending
```

### 10.4 Example Detail Panel for i-004

```text
Payment Latency Anomaly        [94% confidence]
Category: Anomaly

Reasoning
P95 latency increased from 120ms to 285ms during peak hours (8-10pm). Correlates
with increased transaction volume and insufficient auto-scaling.

Recommendation
Auto-scale payment service replicas during peak hours (8-11pm). Increase
connection pool from 100 to 150.

Estimated Benefit
~2% improvement in user experience (fewer timeouts and retries).

[ Approve ]  [ Defer ]  [ Reject ]
```

This mockup ensures AI Intelligence is concrete, actionable, and aligned with the rest of the system.
