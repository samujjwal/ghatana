# 10. Data Connectors Admin Page – Data Fabric – Deep-Dive Spec

Related routes & files:

- Feature: `src/features/data-fabric/*`
- Page component: `src/features/data-fabric/components/DataConnectorsPage.tsx`
- Public API: `src/features/data-fabric/index.ts` (re-exports `DataConnectorsPage`)
- Integration guide: `src/features/data-fabric/INTEGRATION_GUIDE.md`

_Note: This page is part of a **modular data-fabric admin feature**, intended to be plugged into an admin shell (e.g., `/admin/data-fabric/connectors`)._

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide an **admin interface for data connectors**, where operators can create, edit, delete, and trigger syncs for connectors that move data between sources and storage profiles.

**Primary goals:**

- List all configured data connectors with key metadata and sync status.
- Allow creating new connectors (via modal or side-panel form triggered by `onCreateClick`).
- Allow editing existing connectors (`onEditClick`).
- Allow deleting connectors (with confirmation and error handling).
- Allow triggering data syncs and seeing basic sync statistics.

**Non-goals:**

- Low-level ETL job design (detailed transformations and mapping live elsewhere).
- Storage profile administration (covered by the Storage Profiles page).
- Full observability of sync pipelines (logs/metrics dashboards are assumed external for now).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Data engineer / integration engineer** configuring how external systems feed into the Data Cloud.
- **Platform engineer** standardizing connector templates and ensuring environments are wired consistently.
- **Ops / SRE** monitoring sync health and re-triggering failed syncs.

**Key scenarios:**

1. **Initial source onboarding**
   - Data engineer creates a new connector (e.g., "Salesforce -> Customer Profiles"), ties it to a storage profile, and performs an initial sync.

2. **Ongoing sync management**
   - Operator checks the connectors list daily, sees last sync time and status, and manually re-triggers syncs after upstream incidents.

3. **Decommissioning a source**
   - Platform engineer deletes or disables a connector when a source is retired, ensuring no further sync jobs are triggered.

---

## 3. Content & Layout Overview

From `DataConnectorsPage.tsx`:

- Props:
  - `onCreateClick: () => void` – open "create connector" UI.
  - `onEditClick: (connector: DataConnector) => void` – open "edit connector" UI for a selected connector.

- Data flow:
  - Uses Jotai atoms:
    - `loadDataConnectorsAtom` – load connectors from API into state.
    - `allDataConnectorsAtom` – read list of connectors.
    - `deleteDataConnectorAtom` – update state after deletion.
    - `updateSyncStatisticsAtom` – update state after sync runs.
  - Uses `dataConnectorApi` services:
    - `getAll()` – fetch all connectors.
    - `delete(connectorId)` – delete connector.
    - `triggerSync(connectorId)` – start a sync job; returns `jobId`.
    - `getSyncStatistics(connectorId)` – fetch sync stats after a run.
  - Uses `toast` for success/error notifications.

- Layout:
  - **Header**
    - H1: `Data Connectors`.
    - Subtitle: "Connect data sources to storage backends and manage synchronization".
    - Primary button: `New Connector` (calls `onCreateClick`).
  - **Content area**
    - Loading state: centered spinner while `isLoading` is true.
    - Loaded state: `DataConnectorsList` receives handlers:
      - `onEdit` – calls `onEditClick`.
      - `onDelete` – bound to `handleDelete` (confirmation + API + state update).
      - `onSync` – bound to `handleSync` (trigger + statistics update).
  - **Empty state**
    - When `connectors.length === 0 && !isLoading`:
      - Title: "No data connectors yet".
      - Description: guidance on creating the first connector.
      - CTA button: `Create First Connector` (calls `onCreateClick`).

---

## 4. UX Requirements – User-Friendly and Valuable

- **Destructive actions must be explicit and reversible where possible:**
  - `handleDelete` prompts `confirm("Are you sure you want to delete this data connector? ...")`.
  - Copy should emphasize that deletion stops future syncs.
- **Sync triggering should be clear and reassuring:**
  - On success, toast shows `jobId`: `"Sync triggered successfully (Job ID: X)"`.
  - Surface at least last sync time and status (via `DataConnectorsList`).
- **Loading and error states:**
  - Show spinner while loading and avoid flicker.
  - Use `toast.error` with plain-language messages for failures (load, delete, sync, stats).
- **Primary actions should be obvious:**
  - `New Connector` is the main CTA in header.
  - `Sync`, `Edit`, `Delete` actions exposed per-row.

---

## 5. Completeness and Real-World Coverage

In a realistic deployment, a connector typically has:

1. **Source type** (e.g., Salesforce, Postgres, Kafka, S3, custom API).
2. **Target storage profile** (selected from Storage Profiles).
3. **Schedule / trigger policy** (on-demand vs scheduled; the current UI focuses on manual `triggerSync`).
4. **Sync mode**: full load vs incremental, with cursors or watermark fields.
5. **Status & health**: last successful sync, last failure, error message, record counts.

The current `DataConnectorsPage` wires up basic CRUD + manual sync, which is enough to:

- Represent **connectors as managed objects** in the system.
- Support a **first wave** of integrations where ops manually monitor and kick syncs.

To be fully production-grade, downstream components (list, statistics) should:

- Show at least: name, type, target profile, last sync time, last sync status, next scheduled run (if any).
- Allow filtering by status (healthy, warning, failing) and by source type.

---

## 6. Modern UI/UX Nuances and Features

- **List interactions:**
  - `DataConnectorsList` likely presents connectors in a table or card list with quick actions for `Edit`, `Delete`, and `Sync`.
- **Feedback loops:**
  - Toasts give immediate confirmation or failure feedback, which is important for long-running or async syncs.
- **Progressive disclosure:**
  - High-level stats (e.g., last sync time) on the main list.
  - Detailed stats (record counts, error logs) could be shown in a drawer or details panel, not on this page by default.

---

## 7. Coherence with App Creator / Canvas & Platform

- In the broader **App Creator / workflow canvas** view:
  - Each data connector corresponds to a **node or edge** representing movement from an external system to a storage profile or collection.
  - The canvas might show connectors as **ingress nodes** feeding collections/entities that workflows then process.
- This page acts as the **registry** for those nodes:
  - When a user drags a "Salesforce connector" node into a workflow canvas, it should be selectable from the connectors defined here.
  - The connector’s configuration (source credentials, filters, schedule) remains owned by the admin UI here, while workflows reference it by ID.

---

## 8. Links to More Detail & Working Entry Points

- Feature index: `src/features/data-fabric/FEATURE_INDEX.md` (describes data-fabric feature surface).
- Integration guide: `src/features/data-fabric/INTEGRATION_GUIDE.md` (example routes, admin layout integration).
- Components:
  - `DataConnectorsList` – renders the list/table and per-row actions.
  - `DataConnectorForm` or equivalent (defined in feature components) – create/edit UI.
- Services:
  - `dataConnectorApi` – actual CRUD + sync + statistics functions.

---

## 9. Gaps & Enhancement Plan

1. **Richer sync observability**
   - Add per-connector details page or drawer with:
     - Recent runs (timestamp, status, duration, record counts).
     - Error messages and links to logs.
   - Align with platform-wide monitoring patterns.

2. **Scheduling and automation**
   - Extend beyond manual `triggerSync` to:
     - Configure schedules (cron, interval, event-based triggers).
     - Show next scheduled run and allow pausing.

3. **Usage awareness & safety**
   - Before deletion, show which collections/workflows depend on a connector.
   - Optionally prevent deletion if still referenced by active workflows.

4. **Canvas integration hooks**
   - Ensure connectors have stable IDs and metadata so the workflow canvas can:
     - List them in palettes.
     - Show health state (e.g., color-coded node/symbol based on last sync result).

---

## 10. Mockup / Expected Layout & Content

```text
H1: Data Connectors                         [ New Connector ]
"Connect data sources to storage backends and manage synchronization"

[Table or list of connectors]
--------------------------------------------------------------------------------
Name                 Source Type      Target Profile    Last Sync      Actions
--------------------------------------------------------------------------------
"Salesforce Prod"   Salesforce       S3 Prod           2025-11-18     [ Sync ]
                                                                      [ Edit ] [ Delete ]
"Postgres Analytics" Postgres       Local Dev         2025-11-17     [ Sync ]
                                                                      [ Edit ] [ Delete ]
--------------------------------------------------------------------------------

Empty state example
- "No data connectors yet"
- "Create your first data connector to connect data sources to storage backends."
- [ Create First Connector ]
```
