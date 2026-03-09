# 8. Not Found Page – 404 – Deep-Dive Spec

Related routes & files:

- Route: `*` (catch-all)
- Page: `src/pages/NotFound/index.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a simple, friendly **404 page** when a user navigates to an unknown route.

**Primary goals:**

- Clearly indicate that the page does not exist.
- Offer a straightforward way to return to the home/dashboard.

---

## 2. Users, Personas, and Real-World Scenarios

- Any user hitting an invalid CES URL (typo, outdated bookmark, or removed route).

---

## 3. Content & Layout Overview

From `NotFound/index.tsx`:

- Centered layout with:
  - Large `404` code.
  - Subtitle `Page Not Found`.
  - Short explanatory message.
  - `Go to Home` button (navigates to `/`).

---

## 4. UX Requirements – User-Friendly and Valuable

- **Plain language:**
  - Explain that the page might not exist or has been moved.
- **Clear recovery path:**
  - Primary action to return to home.

---

## 5. Completeness and Real-World Coverage

Optionally, a richer 404 page could:

1. Show useful links to key CES areas (Dashboard, Collections, Workflows).
2. Provide a search box.

---

## 6. Modern UI/UX Nuances and Features

- Centered, generous whitespace for a calm, non-threatening error.
- Responsive typography so 404 code doesn’t overflow on small screens.

---

## 7. Coherence with App Creator / Canvas & Platform

- Error handling semantics (e.g., 404 vs 500) should be consistent across products.

---

## 8. Links to More Detail & Working Entry Points

- Shell & routing: `00_shell_and_routing.md`

---

## 9. Gaps & Enhancement Plan

1. **Shared error layout:**
   - Align 404 and other error pages across Ghatana products.

2. **Telemetry:**
   - Optionally log 404s for analysis.

---

## 10. Mockup / Expected Layout & Content

```text
[ Centered on screen ]
-------------------------------------------------------------------------------
404
Page Not Found
"The page you're looking for doesn't exist or has been moved."

[ Go to Home ]
-------------------------------------------------------------------------------
```
