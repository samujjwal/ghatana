# 2. Collections Page – List & Detail – Deep-Dive Spec

Related routes & files:

- Routes:
  - `/collections` – list view
  - `/collections/:id` – detail view
- Page: `src/pages/CollectionsPage.tsx`
- Mock API: `src/lib/mock-api-client.ts`, `src/lib/mock-data.ts`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Let users **browse all collections** and **inspect individual collection details** (metadata, entity counts, schema fields) in a single page component.

**Primary goals:**

- Show a **list of collections** with brief metadata.
- Allow navigation into a **detail view** for a specific collection.
- Provide clear actions to **create new collections**.
- Act as an **entry-level dataset explorer** for Data Cloud by exposing key collection-level signals (counts, schema, status) before users deep-dive into richer dataset explorer and lineage views.

**Non-goals:**

- Editing collections (handled by Create/Edit pages).
- Entity-level browsing; focus is on collection-level metadata.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Data modeler / architect** defining collections and schemas.
- **Operator** verifying collection health and sizes.

**Key scenarios:**

1. **Scanning available collections**
   - User lands on `/collections` and sees all collections with name, description, entity count, active status, and last updated.

2. **Inspecting a specific collection**
   - User clicks a collection card → `/collections/:id`.
   - Sees ID, entity count, schema fields, created/updated timestamps.

3. **Starting a new collection**
   - From an empty state or top-right button, user clicks `New Collection` → `/collections/new`.

---

## 3. Content & Layout Overview

From `CollectionsPage.tsx`:

- List view (`/collections`):
  - Header: `Collections` + `New Collection` button.
  - `CollectionsList` component:
    - Loading state (spinner + text).
    - Error state (red alert with message).
    - Empty state: "No collections" + CTA to create.
    - List of cards, each showing:
      - Name, description.
      - Active/Inactive badge.
      - Entity count.
      - Last updated date.

- Detail view (`/collections/:id`):
  - Back button to `Collections`.
  - Card with:
    - Name, description, Active/Inactive badge.
    - Properties: ID, entity count, schema fields (pill badges), created at, last updated.
  - Edit button → `/collections/:id/edit`.
  - **Planned expansion (Dataset Explorer alignment):** below the main card, reserve space for dataset insight panels such as schema viewer, sample data preview, data quality metrics, cost and usage summaries, and an optimization-history timeline (driven by backend metrics from the Data Brain).

---

## 4. UX Requirements – User-Friendly and Valuable

- **Readable counts and dates:**
  - Format entity counts with thousands separators and dates with locale formatting.
- **Status clarity:**
  - Use color-coded badges for Active vs Inactive.
- **Error and empty handling:**
  - Clear, friendly messages when collections fail to load or when none exist.

---

## 5. Completeness and Real-World Coverage

A more complete collections page should:

1. Support **filtering and search** (by name, status, tags, tenant), including basic sort options (e.g., by last updated, entity count).
2. Display additional metadata (owners, tags, usage stats) sourced from the metadata catalog.
3. Show links to **related workflows** or data-fabric configurations (connectors, storage profiles) that read from or write to this collection.
4. Surface **dataset-level insights** in the detail view, such as data quality scores, storage/compute cost estimates, access heatmaps, and summaries of recent optimizer actions (promotions, compactions, tiering moves).
5. Provide entry points into dedicated **Dataset Explorer** and **Lineage Explorer** pages where users can see sample data, detailed lineage graphs, and performance dashboards for this collection.

---

## 6. Modern UI/UX Nuances and Features

- **Clickable cards:**
  - Entire collection card is clickable to go to details.
- **Responsive layout:**
  - Cards and detail view should be readable on smaller screens.
- **Hierarchy:**
  - Collection name prominent; supporting data in subdued text.

---

## 7. Coherence with App Creator / Canvas & Platform

- Collections in CES map to **entities/collections** that might appear in App Creator data canvases.
- Detail view’s schema field badges should conceptually align with schema views in other tools (e.g., AEP event types, data models).
- In the broader Data Cloud product, this page forms the **collection-centric slice of the Dataset Explorer**: it is where users first see which datasets exist before pivoting into deeper views (sample data, lineage, performance, cost).

---

## 8. Links to More Detail & Working Entry Points

- Create Collection page: `src/pages/CreateCollectionPage.tsx`
- Edit Collection page: `src/pages/EditCollectionPage.tsx`
- Collection form: `src/features/collection/components/CollectionForm.tsx`

---

## 9. Gaps & Enhancement Plan

1. **Search/filter UI:**
   - Add search bar and filters for status/owner, with server- or client-side support for sorting by common fields (name, entity count, last updated).

2. **Schema visualization:**
   - Provide richer schema previews (grouped by type, required fields) and link into schema editor.

3. **Integration with data fabric:**
   - Indicate which collections are backed by which storage profiles or connectors.

4. **Dataset insight panels:**
   - Introduce dedicated sections in the detail view for data quality, cost, and usage (heatmaps, recent query volume) powered by backend metrics.

5. **Optimization history timeline:**
   - Show a timeline of optimizer actions affecting this collection (format changes, tiering adjustments, compactions) with links back to the AI Optimizer/"Data Brain" views.

6. **Deep links into Dataset & Lineage Explorer:**
   - Add clear CTAs from the collection detail view to open the full Dataset Explorer and Lineage Explorer pages pre-filtered to this collection.

---

## 10. Mockup / Expected Layout & Content

```text
List view (/collections)
-------------------------------------------------------------------------------
H1: Collections                           [ New Collection ]

[Card] "Orders"   [Active]
- 12,345 entities • Updated 2025-11-18
- "Order events from ecommerce system"

[Card] "Customers" [Active]
- 542 entities • Updated 2025-11-17
- "Customer master records"

Empty state example
- Icon
- "No collections"
- "Get started by creating a new collection." [ New Collection ]

Detail view (/collections/:id)
-------------------------------------------------------------------------------
← Back to Collections         [ Edit ]

H2: Orders           [Active]
"Order events from ecommerce system"

ID: col-orders
Entity Count: 12,345
Schema Fields: [order_id (string)] [status (enum)] [total_amount (number)] ...
Created At: 2025-09-01 12:34
Last Updated: 2025-11-18 08:03
```
