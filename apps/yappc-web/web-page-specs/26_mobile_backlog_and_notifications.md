# 26. Mobile Backlog & Notifications (Placeholders) – Deep-Dive Spec

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 5. Mobile Shell & Views](../APP_CREATOR_PAGE_SPECS.md#5-mobile-shell--views--mobile)

**Code files:**

- `src/routes/mobile/backlog.tsx`
- `src/routes/mobile/notifications.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Reserve **mobile-optimized backlog and notifications views** for future implementation.

Currently implemented as **PlaceholderRoute** pages indicating that full features are pending.

---

## 2. Users, Personas, and Real-World Scenarios (Future)

**Personas:**

- Developers and on-call engineers who need to see tasks and alerts on the go.

**Key future scenarios:**

1. Viewing project tasks on mobile.
2. Triage notifications and alerts, with quick actions.

---

## 3. Content & Layout Overview (Current)

- Both routes render `PlaceholderRoute` with appropriate icons and explanatory text.

---

## 4. UX Requirements – Future Behavior

Mobile backlog should:

- Show tasks grouped by status in a mobile-friendly way.
- Allow quick editing or status changes.

Mobile notifications should:

- Show alerts and messages.
- Provide tap-through into relevant mobile or desktop views.

---

## 5. Coherence and Consistency Across the App

- Mobile backlog must align with desktop Backlog column semantics.
- Mobile notifications should mirror alert semantics from Monitor and DevSecOps.

---

## 6. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#5-mobile-shell--views--mobile`
- Route implementations (placeholders): `src/routes/mobile/backlog.tsx`, `src/routes/mobile/notifications.tsx`

---

## 7. Open Gaps & Enhancement Plan

1. Implement real backlog and notifications logic on mobile.
2. Reuse patterns from desktop Backlog and Monitor while respecting mobile UX.

---

## 8. Mockup / Expected Layout & Content (Future)

```text
Mobile Backlog (conceptual)
-------------------------------------------------------------------------------
Header: "Backlog"

[ Filter: Status ▼ ]  [ Search tasks… ]

Task cards (scrollable list)
- [ ] BK‑210 Fix mobile checkout crash
  Status: IN PROGRESS • Assignee: alice
- [ ] BK‑211 Add offline banner for Projects
  Status: TODO • Assignee: unassigned

Mobile Notifications (conceptual)
-------------------------------------------------------------------------------
Header: "Alerts & Notifications"

- [🟥 P0] New critical alert in Prod: "500s on /api/orders"   [View]
- [🟧 P1] Build #1238 failed on main                         [View]
- [ℹ]   New DevSecOps report available: "Weekly Summary"     [Open]
```
