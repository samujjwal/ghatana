# 20. DevSecOps Templates & Playbooks – Deep-Dive Spec

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 4. DevSecOps Area](../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops)

**Code files:**

- `src/routes/devsecops/templates.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide **DevSecOps templates and playbooks** that personas can apply to shape boards, KPIs, and workflows.

**Primary goals:**

- List templates organized by persona or use case.
- Describe what each template configures (phases, KPIs, workflows).
- Allow users to apply a template, navigating to the canvas/boards configured accordingly.

**Non-goals:**

- Serve as a free-form documentation wiki; templates are structured and executable.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- Security leads, SREs, engineering managers.

**Key scenarios:**

1. **New team onboarding**
   - Team picks a "New Product" DevSecOps template.
   - Board phases, KPIs, and workflows are pre-configured.

2. **Persona-based workflows**
   - Security lead uses a security-focused template for specific KPIs.

---

## 3. Content & Layout Overview

- **Accordion of templates:**
  - Each entry shows persona, use case, description, recommended KPIs, and workflow tips.
- **Apply template action:**
  - Button that navigates to `/devsecops/canvas` or a phase board with template applied (via query params or state).

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear explanations:**
  - Each template must explain who it is for and what changes it applies.
- **Non-destructive preview:**
  - Ideally, provide preview/description before applying.

---

## 5. Completeness and Real-World Coverage

Templates should cover:

1. Multiple personas.
2. Common use cases (greenfield, incident-heavy, compliance-focused).

---

## 6. Modern UI/UX Nuances and Features

- **Expandable accordions** with smooth animations.
- **Badges** for persona or difficulty level.

---

## 7. Coherence and Consistency Across the App

- Templates must align with DevSecOps canvas definitions and phase/item models.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops`
- Route implementation: `src/routes/devsecops/templates.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Clarify how applying a template reshapes boards and canvases.
2. Tie templates directly to App Creator projects where relevant.

---

## 10. Mockup / Expected Layout & Content

```text
H1: DevSecOps Templates & Playbooks

[ Template Accordion ]

► Security-Focused Launch   [Persona: Security Lead] [Level: Intermediate]
   Description: Hardens a new product's pipelines with security checks.
   Recommended KPIs: Critical vulns, policy coverage, compliant pipelines.
   Workflow tips: Add SAST, secrets scan, and license checks to Build phase.
   [Preview details]   [Apply Template]

► Reliability Ramp‑Up       [Persona: SRE]           [Level: Advanced]
   Description: Focuses on alerts, SLOs, and incident workflows.
   Recommended KPIs: Alert volume, SLO burn rate, time to mitigate.
   [Preview details]   [Apply Template]
```
