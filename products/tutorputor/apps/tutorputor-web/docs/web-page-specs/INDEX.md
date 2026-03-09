# TutorPutor Web – Page Specs Index

These specs document the **TutorPutor web app** pages and shells at the level of:

- **Intention** – why the page exists.
- **Content & layout** – what is shown on the page.
- **User actions & workflows** – what learners can do.
- **Gaps & enhancements** – how to evolve toward richer, canvas-like learning flows that align with the broader App Creator/design-to-code vision.

Each spec follows the same 10-section structure used for YAPPC App Creator, software-org, and AEP UI specs.

---

## 0. Shell & Layout

- `00_shell_and_routing.md`  
  Global React Query + Router shell and `AppLayout` navigation.

## 1. Core Learning Pages

- `01_dashboard_page.md`  
  Learner dashboard summarizing user, current enrollments, and recommended modules.

- `02_module_page.md`  
  Module detail and progression page for a given module slug.

---

## Notes

- These specs are intentionally **page-focused**, not component-level. Component and hook details remain in code and any shared component docs.
- As TutorPutor grows (e.g., lesson/canvas views, assessments, reporting), new page specs should be added here and wired into this index.
