# 1. Dashboard Page – Learner Home – Deep-Dive Spec

Related routes & files:

- Routes: `/` (index) and `/dashboard`
- Router: `src/router/routes.tsx`
- Page: `src/pages/DashboardPage.tsx`
- Hooks: `src/hooks/useDashboard.ts`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Give the learner a **home base** showing who they are, what they’re currently enrolled in, and what modules are recommended next.

**Primary goals:**

- Welcome the learner by name and email.
- Show **current enrollments** with progress.
- Surface **recommended modules** to explore next.

**Non-goals:**

- Deep per-module content (that’s the Module page).
- Detailed reporting or history (future views).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Primary learner** (developer or student) taking modules.
- **Secondary**: Tutor or coach viewing as the learner for testing (internal).

**Key scenarios:**

1. **Starting a learning session**

   - Learner lands on Dashboard.
   - Sees their name/email and current enrollments.
   - Clicks a current enrollment or recommended module to continue/start.

2. **Resuming ongoing modules**

   - Learner checks current enrollments and progress %.
   - Picks a module in progress to continue.

3. **Exploring new topics**
   - Learner reads recommended modules by domain, difficulty, tags, and estimated time.

---

## 3. Content & Layout Overview

From `DashboardPage.tsx` and `useDashboard`:

- `useDashboard` fetches a `DashboardSummary` with:
  - `user` (displayName, email).
  - `currentEnrollments`: array of `Enrollment`.
  - `recommendedModules`: array of `ModuleSummary`.

**Layout:**

- Full-page container with:

  - Header: learner greeting and email.
  - Section: Current Enrollments.
  - Section: Recommended Modules.

- **Enrollment cards:**

  - Show module title (if known), progress bar, enrollment status, and time spent.

- **Module cards:**
  - Show title, domain badge, tags, estimated time, difficulty, and optional progress bar.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Plain labels:**
  - Explain what "Current Enrollments" vs "Recommended Modules" mean.
- **Readable progress:**
  - Progress displayed as a percentage with a clear bar.
- **Empty state:**
  - Friendly message when there are no current enrollments.
- **Error/loading states:**
  - Centered loading and error messages for initial load.

---

## 5. Completeness and Real-World Coverage

A robust dashboard should:

1. Reflect **real modules and enrollments**, not placeholders.
2. Support many modules gracefully (grid layout, paging if needed).
3. Eventually incorporate **learning paths** or **tracks** grouping modules.

---

## 6. Modern UI/UX Nuances and Features

- **Responsive grid:**
  - Enrollment and module cards adjust to screen size.
- **Hover/focus states:**
  - Card shadows and clickable areas should be obvious.
- **Accessible structure:**
  - Headings (`h1`, `h2`) and landmarks for screen readers.

---

## 7. Coherence with App Creator / Canvas & Platform

- Modules here are analogous to **projects/pages** in App Creator:
  - Each module could map to a canvas or plan that describes its lesson flow.
- Over time, dashboards across products (TutorPutor, App Creator, AEP) can share a similar **"overview → detail"** pattern.

---

## 8. Links to More Detail & Working Entry Points

- Code:
  - Dashboard page: `src/pages/DashboardPage.tsx`
  - Dashboard hook: `src/hooks/useDashboard.ts`

---

## 9. Gaps & Enhancement Plan

1. **Navigation affordances:**

   - Consider explicit "Continue"/"Open" buttons on Enrollment cards.

2. **More learner context:**

   - Add last activity time or learning streaks.

3. **Reporting hooks:**
   - Provide links to future progress reports and analytics pages.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Welcome, Jane Doe
jane.doe@example.com

Section: Current Enrollments
-------------------------------------------------------------------------------
[Card] "Intro to Event-Driven Systems"
- Progress: 45.0%  [█████────]
- Status: In Progress
- Time spent: 32 minutes

[Card] "Advanced TypeScript Patterns"
- Progress: 10.0%  [█────────]
- Status: Not Started
- Time spent: 3 minutes
-------------------------------------------------------------------------------

Empty state example
- If no enrollments: "No active enrollments. Start learning by exploring modules below!"

Section: Recommended Modules
-------------------------------------------------------------------------------
[Card] "Event-Driven Architecture Basics"     [Backend]
- Tags: fundamentals, events, messaging
- 45 min   •   Beginner

[Card] "Streaming Data with Kafka"            [Data]
- Tags: kafka, streaming, pipelines
- 60 min   •   Intermediate
-------------------------------------------------------------------------------

Clicking a module card opens `/modules/:slug`.
```
