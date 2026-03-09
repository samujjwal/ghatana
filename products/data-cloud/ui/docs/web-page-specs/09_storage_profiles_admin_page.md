# 9. Storage Profiles Admin Page – Data Fabric – Deep-Dive Spec

Related routes & files:

- Feature: `src/features/data-fabric/*`
- Page component: `src/features/data-fabric/components/StorageProfilesPage.tsx`
- Public API: `src/features/data-fabric/index.ts` (re-exports `StorageProfilesPage`)
- Integration guide: `src/features/data-fabric/INTEGRATION_GUIDE.md`

_Note: This page is part of a **modular data-fabric admin feature**, intended to be plugged into an admin shell (e.g., `/admin/data-fabric/profiles`)._

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide an **admin interface for storage profiles**, where operators can create, edit, delete, and set defaults for storage backends.

**Primary goals:**

- List storage profiles with key metadata.
- Allow creating new profiles (via modal form).
- Allow editing existing profiles.
- Allow deleting profiles (with confirmation).
- Allow setting a default profile.

**Non-goals:**

- Low-level storage configuration UI beyond the profile abstraction.
- Data connector management (covered by Data Connectors page).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Platform engineer** configuring storage backends (cloud buckets, databases, etc.).
- **SRE / operator** updating defaults and cleaning up unused profiles.

**Key scenarios:**

1. **Initial setup**
   - Operator creates first storage profile (e.g., S3 bucket) and sets it as default.

2. **Changing defaults**
   - Operator updates default profile when moving to a new bucket or environment.

3. **Cleanup**
   - Operator deletes unused profiles that are no longer referenced.

---

## 3. Content & Layout Overview

From `StorageProfilesPage.tsx`:

- Props:
  - `onCreateClick`, `onEditClick` – callbacks to open modal forms.

- Data flow:
  - Uses Jotai atoms (`loadStorageProfilesAtom`, `allStorageProfilesAtom`, `deleteStorageProfileAtom`, `setDefaultStorageProfileAtom`).
  - Calls `storageProfileApi.getAll()` to load data.
  - Uses toasts for success/error messaging.

- Layout:
  - Header:
    - H1 `Storage Profiles`.
    - Subtitle: "Manage storage backends and data repository configurations".
    - `New Profile` button.
  - Content:
    - Loading spinner while fetching profiles.
    - `StorageProfilesList` when data is loaded, with handlers for edit/delete/setDefault.
    - Empty state block when there are no profiles, with CTA `Create First Profile`.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear destructive actions:**
  - Confirm dialog for deletes with clear warning copy.
- **Default selection:**
  - Visual indication of which profile is default.
- **Smooth loading:**
  - Spinners and toasts must be easy to understand.

---

## 5. Completeness and Real-World Coverage

In a mature deployment:

1. Storage profiles should show **type** (e.g., S3, GCS, local, database) and key metadata (region, bucket, etc.).
2. There should be a clear indication of which components use each profile.
3. Some profiles may be **locked** if in use by critical workflows.

---

## 6. Modern UI/UX Nuances and Features

- **List vs details:**
  - `StorageProfilesList` likely presents each profile with badges and quick actions.
- **Error handling:**
  - Use `toast.error` with plain-language messages.

---

## 7. Coherence with App Creator / Canvas & Platform

- Storage profiles map to **data sources/targets** that workflows and collections depend on.
- In a broader design-to-code context, these profiles could be selected from a palette when wiring connectors and outputs in a canvas.

---

## 8. Links to More Detail & Working Entry Points

- Feature index: `src/features/data-fabric/FEATURE_INDEX.md`
- Integration guide: `src/features/data-fabric/INTEGRATION_GUIDE.md`
- Components: `StorageProfilesList`, `StorageProfileForm` (defined in feature docs).

---

## 9. Gaps & Enhancement Plan

1. **Admin shell integration:**
   - Wire this page into an admin layout (tabs for storage profiles vs connectors) as shown in INTEGRATION_GUIDE.

2. **Usage awareness:**
   - Show where a profile is used (which connectors/workflows) before deleting.

3. **Validation:**
   - Validate profile configuration against backend (e.g., test connection).

---

## 10. Mockup / Expected Layout & Content

```text
H1: Storage Profiles                      [ New Profile ]
"Manage storage backends and data repository configurations"

[Table or list of profiles]
-------------------------------------------------------------------------------
Name            Type        Default   Last Updated   Actions
-------------------------------------------------------------------------------
"S3 Prod"      S3 Bucket   Yes       2025-11-18     [ Edit ] [ Delete ] [ Set Default ]
"Local Dev"    Local FS    No        2025-11-10     [ Edit ] [ Delete ] [ Set Default ]
-------------------------------------------------------------------------------

Empty state example
- "No storage profiles yet"
- "Create your first storage profile to connect to cloud or local storage systems."
- [ Create First Profile ]
```
