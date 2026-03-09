# 23. Mobile Shell – Capacitor Layout – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Updated with implementation details

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 5. Mobile Shell & Views](../APP_CREATOR_PAGE_SPECS.md#5-mobile-shell--views--mobile)

**Code files:**

| File | Purpose |
|------|---------|
| `src/routes/mobile/_shell.tsx` | Mobile shell layout |
| `src/routes/mobile/index.tsx` | Mobile home/overview |
| `src/routes/mobile/projects.tsx` | Mobile projects list |
| `src/routes/mobile/notifications.tsx` | Notifications/alerts |

**Routes:**

| Route | Purpose |
|-------|---------|
| `/mobile` | Mobile home (index) |
| `/mobile/projects` | Projects list |
| `/mobile/overview` | Quick overview |
| `/mobile/notifications` | Alerts & notifications |

**Platform:**

- Built with Capacitor for iOS/Android
- Uses native plugins for push notifications, haptics
- Responsive design optimized for touch

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **mobile-optimized shell** for the Capacitor app with a bottom navigation bar, header, and drawer, wrapping all `/mobile/*` routes.

**Primary goals:**

- Give mobile users a navigation model suited to phones (bottom nav, compact header).
- Host nested `/mobile/*` routes (overview, projects, backlog, notifications).

**Non-goals:**

- Replace the desktop RootLayout; this is mobile-only.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- Developers and leads checking project status on the go.
- On-call engineers responding from a mobile device.

**Key scenarios:**

1. **Quick health check from phone**
   - User opens mobile app.
   - Lands on mobile overview with KPIs.
   - Can switch tabs to projects or alerts.

2. **Triage from notifications**
   - User receives a push/local notification.
   - Opens app and uses bottom nav to go to Alerts.

---

## 3. Content & Layout Overview

- **Header:**
  - Simple app title and optional actions.
- **Bottom navigation bar:**
  - Tabs for Dashboard/Overview, Projects, Alerts/Notifications.
- **Drawer:**
  - Simple drawer for additional links.
- **Outlet:**
  - Renders nested `/mobile/*` routes inside this shell.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Thumb-friendly navigation:**
  - Bottom nav buttons large enough for comfortable tapping.
- **Clear selection state:**
  - Active tab should be visually distinct.

---

## 5. Completeness and Real-World Coverage

Mobile shell should support:

1. Multiple main tabs.
2. Graceful orientation changes (portrait/landscape).

---

## 6. Modern UI/UX Nuances and Features

- **Safe area handling** on iOS/Android.
- **Smooth transitions** between tabs.

---

## 7. Coherence and Consistency Across the App

- Tab labels and icons must align with desktop terminology (Dashboard vs Overview, Projects, Alerts).

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#5-mobile-shell--views--mobile`
- Route implementation: `src/routes/mobile/_shell.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Align terminology and routes with desktop equivalents.
2. Add explicit indication of which project/workspace context is active, if any.

---

## 10. Mockup / Expected Layout & Content

```text
(Mobile viewport – Capacitor app frame)

┌───────────────────────────────────────────────┐
│  YAPPC App Creator                           │  ← Header
│  [☰]                                         │
└───────────────────────────────────────────────┘

┌───────────────────────────────────────────────┐
│  Drawer (when ☰ tapped)                      │
│  - Settings                                  │
│  - About                                     │
└───────────────────────────────────────────────┘

┌───────────────────────────────────────────────┐
│  [ Outlet: /mobile/* route content ]         │
│  e.g., Overview KPIs or Projects list        │
└───────────────────────────────────────────────┘

┌───────────────────────────────────────────────┐
│  Dashboard     ● Projects      Alerts         │  ← Bottom Nav
└───────────────────────────────────────────────┘
```
