# 12. Dataset Detail & Insights Page – Deep-Dive Spec

> **Status:** Planned page – no concrete implementation in CES UI yet. This spec refines the Dataset Detail view described in `frontend_todo (1).md`.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **deep view into a single dataset**, including schema, sample data, quality, cost, access patterns, and optimization history.

**Primary goals:**

- Show rich metadata for a chosen dataset.
- Let users quickly assess whether the dataset is suitable for their use case.
- Surface issues (quality, freshness, cost anomalies) and recent optimizations.
- Provide entry points to lineage explorer and SQL workspace.

**Non-goals:**

- Editing dataset schemas directly (handled by dedicated schema tools where applicable).
- Full workflow orchestration configuration (handled by Workflow Builder).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Analyst/Scientist** evaluating dataset fitness for analysis.
- **Data engineer** confirming that pipelines populate and optimize the dataset correctly.
- **Governance user** checking compliance, PII handling, and lineage.

**Key scenarios:**

1. **Fitness check**
   - User opens dataset detail from Dataset Explorer.
   - Reviews schema, sample rows, quality metrics, and last refresh.

2. **Cost and performance investigation**
   - User sees high query costs or slow performance.
   - Reviews cost metrics, access heatmaps, and optimizer history to understand why.

3. **Governance and compliance review**
   - User checks PII tags, policies, and lineage to downstream reports.

---

## 3. Content & Layout Overview

Proposed main sections for the detail page:

1. **Overview header**
   - Dataset name, description.
   - Type, domain/system, owner/team.
   - Tags and tier (Bronze/Silver/Gold/Cold).
   - High-level health score.

2. **Schema viewer**
   - Table of columns: name, type, description, PII tag, nullable, stats (distinct count, null %).
   - Grouping by category (dimensions, measures, technical columns).

3. **Sample data viewer**
   - Paginated, virtualized grid of sample rows.
   - Controls for sample mode (head, random sample) and row limit.

4. **Data quality metrics**
   - Cards/graphs for completeness, accuracy, consistency, anomaly flags.
   - Basic history of quality scores over time.

5. **Cost & performance metrics**
   - Storage size, partition count, file count.
   - Query cost and latency distributions.
   - Top queries or consumers by cost.

6. **Access heatmaps**
   - Time-based view of how often the dataset is accessed.
   - Breakdowns by team/user or workload type.

7. **Optimization history timeline**
   - List/timeline of optimizer actions (format conversion, partitioning, tiering, MV creation).
   - Each entry links to more detailed optimizer logs if available.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Readable at a glance:**
  - Overview section summarizes key facts and health so users can decide quickly.
- **Progressive disclosure:**
  - Advanced metrics (cost, optimizer internals) can be in collapsible panels.
- **Explainable metrics:**
  - Health and quality scores should include explanations and links to docs.
- **Safe sample viewing:**
  - Respect row/column-level security and data masking.

---

## 5. Completeness and Real-World Coverage

A robust Dataset Detail page should:

1. Pull schema and stats from the **metadata catalog** and profiling/quality services.
2. Reflect latest **storage/compute state** (size, format, index usage).
3. Tie in signals from the **AI Optimizer/Data Brain** about past and recommended actions.
4. Show governance metadata (policies, PII flags, approvals) as first-class concepts.
5. Work across table formats (Iceberg, Delta, etc.) and storage tiers.

---

## 6. Modern UI/UX Nuances and Features

- **Sticky summary header:**
  - Keep core info and health score visible while scrolling.
- **Linked sections:**
  - Quick links or tabs for Schema, Sample, Quality, Cost, Lineage.
- **Inline query link:**
  - Button to "Open in SQL Workspace" with dataset pre-selected.

---

## 7. Coherence with App Creator / Canvas & Platform

- This page describes the **dataset node** that appears in workflows and canvases.
- Workflows using this dataset should be accessible from the detail view.
- App Creator may reference this dataset as a source for applications; this view is the authoritative reference for its structure and quality.

---

## 8. Links to More Detail & Working Entry Points

- Lineage Explorer (planned): `13_lineage_explorer_page.md`.
- SQL Workspace (planned): `14_sql_workspace_page.md`.
- Governance UI (future): role/policy/PII viewers.
- Backend: profiling, quality, and optimizer services as defined in `backend_todo (1).md`.

---

## 9. Gaps & Enhancement Plan

1. **Concrete routing & integration:**
   - Decide URL patterns (e.g., `/datasets/:id`) and how to map from catalog IDs.

2. **Data sampling strategy:**
   - Define safe, performant mechanisms to fetch sample rows.

3. **Metric sources:**
   - Specify which backend services provide quality, cost, and access signals.

4. **Security and masking:**
   - Ensure column masking/row filters are enforced in sample and schema views.

---

## 10. Mockup / Expected Layout & Content

```text
H1: orders_dataset                         [ Open in SQL Workspace ]
"Order events from ecommerce platform"
Owner: Data Platform Team   Type: Collection   Tier: Gold   Health: 92/100
Tags: [pii] [critical]

Tabs: [Overview] [Schema] [Sample Data] [Quality] [Cost & Usage] [Lineage]

[Schema]
-----------------------------------------------------------------------------
column_name   type      description                 pii   null%   distinct
-----------------------------------------------------------------------------
order_id      string    Unique order identifier     yes    0%      1.2M
customer_id   string    Customer identifier         yes    0%      420K
status        string    Order status enum           no     0.1%    6
...
```
