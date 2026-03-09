# 13. Data Export Utility – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 13. `/export` – Data Export Utility](../WEB_PAGE_FEATURE_INVENTORY.md#13-export--data-export-utility)

**Code file:**

- `src/features/export/pages/DataExportUtil.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Allow users to export incidents, metrics, audit logs, models, and alerts into common formats, with control over time range, columns, and schedule.

**Primary goals:**

- Provide a **form** to define export jobs (format, data type, date range, columns).
- Allow **one-time exports** and **scheduled exports**.
- Show **recent export history** with status and download links.

**Non-goals:**

- Full ETL/BI pipeline management.
- Interactive analytics (dashboards handle that).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Engineering managers** exporting incident data.
- **Security/compliance** exporting audit logs.
- **Data analysts** exporting metrics for BI tools.

**Scenarios:**

1. **Quarterly incident review**
   - GIVEN: EM needs a list of P1 incidents for last quarter.
   - WHEN: They choose Data Type `Incidents`, Date Range `Last 90 days`, Format `CSV`, and relevant columns.
   - THEN: They run export and download a CSV.

2. **Compliance audit**
   - GIVEN: Auditor requests an audit log for a specific period.
   - WHEN: Compliance officer selects Data Type `Audit Log`, Date Range `Custom`, Format `JSON` or `CSV`.
   - THEN: They export logs and attach them to evidence.

3. **Feeding BI dashboards**
   - GIVEN: Data team wants daily metrics into their warehouse.
   - WHEN: They set a scheduled export for `Metrics`, `Daily`, to `CSV`.
   - THEN: Files are generated nightly (once wired) and can be pulled by ETL.

---

## 3. Content & Layout Overview

From `DataExportUtil.tsx`:

- **Header:**
  - Title: `Data Export`.
  - Subtitle: `Export incidents, metrics, logs and more`.

- **Layout:**
  - Left (2/3): Export configuration form.
  - Right (1/3): Presets, history, and tips.

- **Export form sections:**
  - **Format:** `PDF`, `CSV`, `JSON`, `Excel`.
  - **Data Type:** `Incidents`, `Metrics`, `Audit Log`, `Models`, `Alerts`.
  - **Date Range:** `Today`, `Last 7 days`, `Last 30 days`, `Custom` (with from/to).
  - **Columns:** checkboxes per data type.
  - **Schedule:** `One-time`, `Daily`, `Weekly`, `Monthly`.

- **Actions:**
  - `Run Export` primary button.
  - `Reset` button.

- **Sidebar:**
  - Quick Export presets (e.g., `Last 7 days incidents (CSV)`).
  - Recent Exports history list.
  - Tips card.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Guided flow:**
  - Sections grouped logically with headings.
- **Validation:**
  - Prevent running export if required fields are missing.
- **Feedback:**
  - After running export, show status (Pending, Completed, Failed).
- **Clarity of impact:**
  - Short description of what each Data Type contains.

---

## 5. Completeness and Real-World Coverage

Supported data types must enable:

- **Incidents:** on-call and reliability review.
- **Metrics:** performance analytics.
- **Audit Log:** compliance.
- **Models:** ML governance.
- **Alerts:** SRE/security analysis.

---

## 6. Modern UI/UX Nuances and Features

- Multi-column layout for form on desktop; stacked on mobile.
- Progress indicator/spinner while exports run (future).
- Copy-to-clipboard for generated download links.
- Tooltips explaining certain fields (e.g., scheduled exports cadence).

---

## 7. Coherence and Consistency Across the App

- Data type names match the pages where they originate (Incidents from Dashboard/Monitor, Metrics from Dashboard, etc.).
- Date range semantics match Reports and Dashboard.
- Audit Log export matches Security → Audit Log.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#13-export--data-export-utility`
- Implementation: `src/features/export/pages/DataExportUtil.tsx`

---

## 9. Open Gaps & Enhancement Plan

- Add status tracking for exports and progress.
- Wire to real backend endpoints per data type.
- Allow direct push to external storage (e.g., S3, GCS) as a next phase.

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
H1: Data Export
Subtitle: Export incidents, metrics, logs and more

[Export Configuration (left, 2/3)]      [Presets & History (right, 1/3)]
```

### 10.2 Sample Export Form Values

- **Format:** `CSV`
- **Data Type:** `Incidents`
- **Date Range:** `Last 30 days`
- **Columns:**
  - [x] Incident ID
  - [x] Severity
  - [x] Service
  - [x] Started At
  - [x] Resolved At
  - [ ] Root Cause

- **Schedule:** `One-time`

Buttons:

- `[ Run Export ]` `[ Reset ]`

### 10.3 Sample Quick Export Presets

- `Last 7 days incidents (CSV)`
- `Last 30 days audit log (JSON)`
- `Monthly metrics summary (Excel)`

### 10.4 Sample Export History

```text
Recent Exports

2025-11-20 10:15   Incidents   Last 30 days   CSV     Completed   [ Download ]
2025-11-19 09:00   Audit Log   Last 7 days    JSON    Completed   [ Download ]
2025-11-18 18:30   Metrics     Last 30 days   Excel   Failed      [ View Error ]
```

### 10.5 Sample Tips Card

```text
Tips

• Use CSV for spreadsheets and BI tools.
• Use JSON for programmatic analysis.
• For large ranges, prefer scheduled exports off-hours.
```

This mockup aligns the export UI with realistic usage and concrete sample data.
