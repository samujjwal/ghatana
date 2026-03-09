# 24. Mobile Projects – Project List – Deep-Dive Spec

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 5. Mobile Shell & Views](../APP_CREATOR_PAGE_SPECS.md#5-mobile-shell--views--mobile)

**Code files:**

- `src/routes/mobile/projects.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **touch-first list of projects** on mobile, with search, filters, favorites, offline awareness, and quick actions.

**Primary goals:**

- Let users browse and search projects on mobile.
- Surface key status and actions for each project.
- Handle offline mode gracefully.

**Non-goals:**

- Replace full desktop project lists; this is optimized for mobile usage.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- Developers and leads checking project status away from their desk.

**Key scenarios:**

1. **Finding a project quickly**
   - User searches by name and taps the project card.

2. **Marking favorites**
   - User stars frequently-used projects.

3. **Working offline**
   - User opens app with no network; project list shows offline state and cached data where possible.

---

## 3. Content & Layout Overview

- **Search bar:**
  - Text input with debounce.
- **Filter controls:**
  - Filter chips or dropdowns (e.g., status, favorites).
- **Project cards:**
  - Title, description, status chips, favorite icon, context menu.
- **Offline indicator:**
  - Uses Capacitor network plugin to detect offline mode.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Tap targets:**
  - Cards and actions must be large enough for finger taps.
- **Clear errors:**
  - Use snackbars to show network or other errors.

---

## 5. Completeness and Real-World Coverage

Mobile projects list should support:

1. Dozens of projects.
2. Favorites and quick actions (share, archive) via context menu.

---

## 6. Modern UI/UX Nuances and Features

- **Pull-to-refresh** (if implemented).
- **Haptics** for certain actions via Capacitor plugins.

---

## 7. Coherence and Consistency Across the App

- Project names and statuses must match `/app` desktop projects.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#5-mobile-shell--views--mobile`
- Route implementation: `src/routes/mobile/projects.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Ensure project identities and statuses stay in sync with `/app` projects.
2. Support navigating from a mobile project to relevant desktop routes / deep links.

---

## 10. Mockup / Expected Layout & Content

```text
(Mobile viewport – inside mobile shell, Projects tab active)

Header: "Projects"

[ Search projects… 🔍 ]  [ Filter ⌄ ]

┌───────────────────────────────────────────────┐
│ (optional) Offline banner                    │
│  ⚠️ Offline – showing last synced projects.  │
└───────────────────────────────────────────────┘

Project list (scrollable)
┌───────────────────────────────────────────────┐
│  ★  E‑commerce Platform                      │
│     Status: Healthy • Last deploy: 2h ago    │
│     Open issues: 3 (1 High, 2 Low)           │
│     Updated: 15 min ago                      │
│     Chips: [Prod] [High traffic]             │
│                                              │
│     ⋯  (tap for actions: Share, Archive)     │
└───────────────────────────────────────────────┘

┌───────────────────────────────────────────────┐
│  ☆  Design System Library                     │
│     Status: Warning • Build failing          │
│     Open issues: 5 (2 High, 3 Medium)        │
│     Updated: 3h ago                          │
│     Chips: [Staging] [Design system]         │
└───────────────────────────────────────────────┘

Bottom navigation (from mobile shell)
┌───────────────────────────────────────────────┐
│  Dashboard   ● Projects   Alerts             │
└───────────────────────────────────────────────┘

Interactions:
- Tap a project card → opens mobile project detail or routes into desktop /app URL.
- Tap star ☆/★ → toggle favorite with subtle haptic feedback.
- Pull down to refresh when online.
```
