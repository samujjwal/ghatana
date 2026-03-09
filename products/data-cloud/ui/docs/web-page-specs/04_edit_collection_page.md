# 4. Edit Collection Page – Deep-Dive Spec

Related routes & files:

- Route: `/collections/:id/edit`
- Page: `src/pages/EditCollectionPage.tsx`
- Form component: `src/features/collection/components/CollectionForm.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **safe editing surface** for updating an existing collection’s metadata and schema.

**Primary goals:**

- Load an existing collection by ID.
- Let users adjust fields (name, description, schema fields) safely.
- On save, update the collection and return user to the collection detail view.

**Non-goals:**

- Bulk migration of entities.
- Editing underlying storage or connectors (belongs to data fabric admin).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Data modeler / architect** maintaining schemas over time.

**Key scenarios:**

1. **Minor schema or description tweak**
   - User opens `/collections/:id/edit`.
   - Adjusts a field label or description.
   - Saves changes and reviews updated collection.

2. **Adding new fields**
   - User adds new schema fields to a collection.

---

## 3. Content & Layout Overview

From `EditCollectionPage.tsx`:

- On mount:
  - Uses `mockApiClient.getCollectionById(id)` to load collection.
  - Shows spinner while loading.
  - On error, shows toast and navigates back to `/collections`.

- Page layout:
  - Header: `Edit Collection` + subtitle.
  - Card with `CollectionForm` pre-populated via `initialData`.

- On submit:
  - Builds an updated collection object:
    - Merges form data.
    - Updates `updatedAt` timestamp.
    - Keeps existing field IDs when names match, or generates new ones.
  - Calls `mockApiClient.updateCollection`.
  - Shows success toast and navigates back to `/collections/:id`.

- On cancel:
  - Navigates back to detail or list as appropriate.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Loading and not-found:**
  - Clear spinner while loading.
  - Friendly not-found message if collection doesn’t exist.
- **Safe edits:**
  - Make it clear that changes affect schema; consider warnings if removing fields.
- **Feedback:**
  - Disabled submit state while saving, plus success/error toasts.

---

## 5. Completeness and Real-World Coverage

For production:

1. Integrate with real CES backend updates.
2. Handle validation errors from the server (e.g., incompatible schema changes).
3. Provide more guidance when making breaking changes.

---

## 6. Modern UI/UX Nuances and Features

- **Diff awareness (future):**
  - Show what changed since last schema version.
- **Versioning:**
  - Track schema versions and allow rollbacks.

---

## 7. Coherence with App Creator / Canvas & Platform

- Editing a collection here should correspond to editing a **data model node** in a canvas.
- Long-term, App Creator could surface this editing experience as a dedicated panel on a canvas node.

---

## 8. Links to More Detail & Working Entry Points

- Collections list/detail: `src/pages/CollectionsPage.tsx`
- Collection form: `src/features/collection/components/CollectionForm.tsx`

---

## 9. Gaps & Enhancement Plan

1. **Schema change safety checks:**
   - Warn users when removing or altering fields that might break existing workflows.

2. **Collaboration & audit:**
   - Add visibility into who last changed the schema and why (tie into Audit Logs).

3. **Inline documentation:**
   - Inline hints/tooltips about field types and best practices.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Edit Collection
"Update the collection details and schema"

[Card]
-------------------------------------------------------------------------------
[CollectionForm] pre-filled with existing data

[ Cancel ]   [ Save Changes ]
-------------------------------------------------------------------------------

Error / not found example:
- "Collection not found" message.
- [ Back to Collections ] button.
```
