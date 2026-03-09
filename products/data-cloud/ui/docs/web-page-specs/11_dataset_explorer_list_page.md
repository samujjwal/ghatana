# 11. Dataset Explorer – List View – Deep-Dive Spec

> **Status:** Planned page – no concrete implementation in CES UI yet. This spec translates the Dataset Explorer list requirements from `frontend_todo (1).md` into a concrete future page.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **global list of datasets** (not just CES collections) with search, filters, and sorting, as the main entry to exploring data in the Data Cloud.

**Primary goals:**

- Show all relevant datasets across the Data Cloud (collections, external tables, virtual datasets).
- Allow users to **search, filter, and sort** datasets by multiple criteria.
- Expose key dataset signals at a glance: type, owner, tags, usage, health.
- Provide clear navigation into dataset detail, lineage explorer, and SQL workspace.

**Non-goals:**

- Deep per-dataset analysis (covered by Dataset Detail spec).
- Lineage graph visualization (covered by Lineage Explorer spec).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Data analyst / scientist** discovering datasets for analysis.
- **Data engineer** checking availability and status of pipelines’ outputs.
- **Governance user** scanning catalog for PII-bearing or sensitive datasets.

**Key scenarios:**

1. **Finding a dataset for a new analysis**
   - User opens Dataset Explorer.
   - Searches for "orders"; filters to production, compliant datasets.
   - Opens the chosen dataset’s detail page.

2. **Monitoring a curated set of critical datasets**
   - User filters by tag (e.g., `critical`) and sorts by last updated/health.
   - Quickly identifies any at-risk datasets.

3. **Onboarding into an unfamiliar domain**
   - User browses by domain/system (e.g., CRM, billing).
   - Reads descriptions and ownership info before drilling into details.

---

## 3. Content & Layout Overview

High-level layout for the list page:

- **Header**
  - Title: `Datasets`.
  - Subtitle explaining this is the main catalog view for Data Cloud datasets.

- **Search & Filters bar**
  - Text search box (name, description, column names).
  - Filters:
    - Status (active/deprecated/experimental).
    - Domain or system (CRM, billing, product, etc.).
    - Tags (e.g., `pii`, `critical`, `gold`).
    - Tier or layer (Bronze/Silver/Gold/Cold).
  - Sort dropdown (last updated, usage, health, cost).

- **Dataset list/table**
  - Columns (or card fields):
    - Name & short description.
    - Type (collection, external table, virtual dataset).
    - Owner / team.
    - Tags.
    - Usage (queries/day or relative ranking).
    - Health score badge.
    - Last updated timestamp.

- **Empty & error states**
  - Friendly copy when no datasets match filters.
  - Error banner when the catalog fails to load.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Fast discovery:**
  - Search should be responsive and support partial matches.
- **Clear filters:**
  - Show active filters as chips; allow quick removal/reset.
- **Accessible table/list:**
  - Keyboard navigable, screen-reader friendly.
- **Link density without clutter:**
  - Dataset name links to detail; icons/quick actions for lineage, SQL, and documentation.

---

## 5. Completeness and Real-World Coverage

A production-ready Dataset Explorer list should:

1. Pull from the **central metadata catalog**, not only CES collections.
2. Represent datasets from multiple storage backends and table formats.
3. Integrate with governance signals (PII, policies) and optimizer signals (hotness, tiering).
4. Scale to thousands of datasets with pagination or infinite scroll.
5. Respect security (only show datasets user is authorized to see).

---

## 6. Modern UI/UX Nuances and Features

- **Column customization:**
  - Allow users to choose which columns to show/hide.
- **Saved views:**
  - Users can save filter/sort combinations as named views.
- **Inline indicators:**
  - Icons/badges for PII, experimental, deprecated, or highly used datasets.

---

## 7. Coherence with App Creator / Canvas & Platform

- Datasets selected here are the same entities referenced by:
  - SQL Workspace (schema browser and autocomplete).
  - Workflow Builder (nodes operating on these datasets).
  - App Creator data sources.
- This list acts as the **canonical registry** UI, consistent with backend catalog services.

---

## 8. Links to More Detail & Working Entry Points

- Dataset Detail page (planned): `12_dataset_detail_insights_page.md`.
- Lineage Explorer (planned): `13_lineage_explorer_page.md`.
- SQL Workspace (planned): `14_sql_workspace_page.md`.
- Backend catalog services: see `backend_todo (1).md` – Metadata, Governance & Security.

---

## 9. Gaps & Enhancement Plan

1. **Implementation & routing:**
   - Define concrete routes (e.g., `/datasets`) and integrate with shell routing.

2. **Backend integration:**
   - Implement catalog APIs and connect them to this page.

3. **Performance & scale:**
   - Add server-side pagination and caching for large catalogs.

4. **Governance overlays:**
   - Highlight policy violations or pending approvals.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Datasets
"Discover and explore all datasets in your Data Cloud"

[ Search box                     ] [ Filters ▾ ] [ Sort: Last Updated ▾ ]

-----------------------------------------------------------------------------
Name          Type        Owner      Tags            Usage   Health  Updated
-----------------------------------------------------------------------------
Orders        Collection  Data Eng   [pii] [gold]    High    92/100  2025-11-18
Customers     Table       Analytics  [critical]      Medium  88/100  2025-11-17
Events_raw    External    Platform   [bronze]        Low     75/100  2025-11-16
-----------------------------------------------------------------------------

Empty state example
- "No datasets match your filters"
- Button: [ Clear Filters ]
```
