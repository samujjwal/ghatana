# 2. Module Page – Module Detail & Progress – Deep-Dive Spec

Related routes & files:

- Route: `/modules/:slug`
- Router: `src/router/routes.tsx`
- Page: `src/pages/ModulePage.tsx`
- Hooks & API:
  - `src/hooks/useModuleBySlug.ts`
  - `src/hooks/useProgressUpdate.ts`
  - `src/api/tutorputorClient.ts` (for enroll/progress operations)

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **detailed view of a learning module**, including description, learning objectives, content blocks, and the learner’s progress/actions.

**Primary goals:**

- Show clear module metadata (title, domain, difficulty, time estimate).
- Present learning objectives and content blocks in a structured way.
- Let the learner **enroll**, **continue**, and **update progress**.

**Non-goals:**

- Implement full lesson rendering (e.g., video player, exercises) – contentBlocks are currently abstract.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Learner** focusing on a specific topic.
- **Tutor/content author** previewing the module.

**Key scenarios:**

1. **Starting a module**

   - Learner lands on `/modules/:slug` from Dashboard.
   - Reads module description and objectives.
   - Clicks **Start Module** to enroll.

2. **Continuing a module**

   - Learner returns with an existing enrollment.
   - Sees current progress %, time spent, and objectives.
   - Uses the action button to **Continue Module** and progress through steps.

3. **Marking progress (current stub)**
   - "Mark Step Completed" increases progress by 10% and increments time.
   - This simulates real lesson completion interactions.

---

## 3. Content & Layout Overview

From `ModulePage.tsx` and `useModuleBySlug`:

- Data shape:
  - `module`: metadata, learningObjectives, contentBlocks.
  - `userEnrollment`: progress, status, time spent (or `null` if not enrolled).

**Layout:**

- Back button → Dashboard.
- Main module header card with:
  - Title, domain chip, difficulty, estimated time.
  - Description.
  - Progress summary (if enrolled).
  - Primary action button (Start/Continue/Enrolling...).
- Learning Objectives section.
- Content section with contentBlocks (each a `ContentBlockCard`).

---

## 4. UX Requirements – User-Friendly and Valuable

- **Plain copy:**
  - Explain difficulty and time estimate in simple language.
- **Progress clarity:**
  - Progress bar and text should clearly show where the learner stands.
- **Button labels:**
  - Button must reflect state: `Start Module`, `Continue Module`, `Enrolling...`.
- **Error/loading states:**
  - Centered messages for loading/error.

---

## 5. Completeness and Real-World Coverage

For production use, the Module page should:

1. Support richer content types for contentBlocks (video, code, quizzes).
2. Reflect real progress and lesson completion, not stub increments.
3. Show prerequisites and recommended next modules.

---

## 6. Modern UI/UX Nuances and Features

- **Sectioned layout:**
  - Clear separation between metadata, objectives, and content.
- **Responsive design:**
  - Layout should remain readable on mobile and desktop.
- **Accessible buttons & lists:**
  - Objectives and content blocks should be screen-reader friendly.

---

## 7. Coherence with App Creator / Canvas & Platform

- A module is analogous to a **learning pipeline**:
  - Objectives and contentBlocks could map to steps or nodes in a learning canvas.
- Over time, the module page could link to a **visual lesson designer** used by tutors, similar to how pipelines/patterns are managed in other products.

---

## 8. Links to More Detail & Working Entry Points

- Code:
  - Module page: `src/pages/ModulePage.tsx`
  - Module hook: `src/hooks/useModuleBySlug.ts`
  - Progress update hook: `src/hooks/useProgressUpdate.ts`

---

## 9. Gaps & Enhancement Plan

1. **Real lesson content:**

   - Replace stub contentBlocks with a structured lesson renderer.

2. **Fine-grained progress tracking:**

   - Track per-block or per-lesson completion instead of fixed 10% increments.

3. **Assessment & reporting:**
   - Add links to assessments and performance summaries per module.

---

## 10. Mockup / Expected Layout & Content

```text
← Back to Dashboard

H1: Event-Driven Architecture Basics
[Backend]   •   Intermediate   •   45 minutes

Description
"Learn how event-driven systems are structured, how events flow between services,
and how to reason about eventual consistency."

Your Progress (if enrolled)
-------------------------------------------------------------------------------
Progress: 30.0%  [████────]
Status: In Progress   •   Time spent: 18 minutes

[ Continue Module ]
(or [ Start Module ] if not yet enrolled; [ Enrolling... ] while in-flight)
-------------------------------------------------------------------------------

Section: Learning Objectives
-------------------------------------------------------------------------------
✓ Understand the difference between commands and events                      (Understand)
✓ Describe at least three benefits of event-driven architectures             (Remember)
✓ Draw a simple event-driven system with producers and consumers             (Apply)
-------------------------------------------------------------------------------

Section: Content
-------------------------------------------------------------------------------
[Content block cards – e.g.,]
- Video: "What is Event-Driven Architecture?"  (10 min)
- Article: "Events vs Commands"                (15 min)
- Exercise: "Model your own event flow"        (20 min)
-------------------------------------------------------------------------------

For enrolled learners: button "Mark Step Completed" (stub today, real lesson actions future).
```
