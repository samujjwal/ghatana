# 3. Create Collection Page – Deep-Dive Spec

Related routes & files:

- Route: `/collections/new`
- Page: `src/pages/CreateCollectionPage.tsx`
- Form component: `src/features/collection/components/CollectionForm.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **guided form** for defining a new collection and its schema in CES.

**Primary goals:**

- Let users create a new collection with:
  - Basic metadata (name, description).
  - Schema definition (fields and constraints).
- After creation, send the user back to the Collections list.

**Non-goals:**

- Editing existing collections (handled by Edit page).
- Low-level entity operations.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Data modeler / architect** adding new collections.

**Key scenarios:**

1. **Creating a new collection from scratch**
   - User clicks `New Collection` from list or dashboard.
   - Fills the form (collection name, schema fields).
   - Submits and sees confirmation.

---

## 3. Content & Layout Overview

From `CreateCollectionPage.tsx`:

- Page structure:
  - Header: `Create New Collection` + subtitle.
  - Card containing `<CollectionForm>`.

- On submit:
  - Uses `mockApiClient` to create a new collection with:
    - Generated IDs (`col-*`, `schema-*`, `field-*`).
    - Initialized `entityCount = 0` and timestamps.
  - Shows success toast and navigates back to `/collections`.

- On cancel:
  - Navigates back to `/collections`.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear purpose text:**
  - Subtitle explains that the user is defining a new collection and schema.
- **Feedback:**
  - Show `isSubmitting` disabled state and a toast message on success or error.
- **Safe cancel:**
  - Prominent `Cancel` allows backing out without changes.

---

## 5. Completeness and Real-World Coverage

For a real CES deployment, Create Collection should:

1. Integrate with backend API (not mock client).
2. Validate schema (unique field names, types, constraints, etc.).
3. Potentially support templates or cloning of existing collections.

---

## 6. Modern UI/UX Nuances and Features

- **Schema builder UX:**
  - The underlying `CollectionForm` should feel like a light schema builder rather than a raw JSON editor.
- **Validation messages:**
  - Inline, plain-language error messages when required fields are missing or invalid.

---

## 7. Coherence with App Creator / Canvas & Platform

- The Create Collection experience is analogous to **creating a new data model** or entity in a canvas-based tool.
- In future, the schema portion could be a **visual designer** that also feeds into App Creator data modeling.

---

## 8. Links to More Detail & Working Entry Points

- Collection form: `src/features/collection/components/CollectionForm.tsx`
- Mock data helpers: `src/lib/mock-data.ts`

---

## 9. Gaps & Enhancement Plan

1. **Schema templating:**
   - Add presets for common collection types (orders, customers, events).

2. **Backend validation:**
   - Round-trip validation with CES backend for schema correctness before final save.

3. **Canvas integration (future):**
   - Launch a visual schema designer from this page or integrate it inline.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Create New Collection
"Define a new collection and its schema"

[Card]
-------------------------------------------------------------------------------
Form fields (conceptual):
- Collection Name: [__________________]
- Description:     [__________________]

Schema
- Add Field: [ + Add Field ]
  Field list:
  - [ name ] [ type: string ⌄ ] [ required ☐ ] [ … ]
  - [ status ] [ type: enum ⌄ ] [ required ☑ ] [ values: active, inactive, pending ]

[ Cancel ]   [ Create Collection ]
-------------------------------------------------------------------------------

On success:
- Toast: "Collection created successfully".
- Redirect to `/collections`.
```
