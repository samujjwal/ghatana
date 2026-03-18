# 5. Design – Design System & Components – Deep-Dive Spec

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 2.4 Design](../APP_CREATOR_PAGE_SPECS.md#24-design----designtsx)

**Code files:**

- `src/routes/app/project/design.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Expose the **design system, component library, and API contracts** for a project so the canvas and code stay aligned with a single source of truth.

**Primary goals:**

- Show design tokens (colors, typography, spacing) used by the project.
- Present available UI components and patterns.
- Surface relevant API documentation and contracts.

**Non-goals:**

- Act as a full documentation portal; instead it links out to deeper docs.
- Edit canvas elements directly (that’s the Implement/Canvas tab).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Developers:** Need to know which components and tokens to use.
- **Designers:** Verify that implementation matches design system.
- **Tech Leads / Architects:** Ensure consistency across projects.

**Key scenarios:**

1. **Choosing a component**
   - Developer opens Design tab.
   - Scans component cards and picks the right pattern to use on the canvas.

2. **Checking token usage**
   - Designer validates that colors and typography align with brand spec.

3. **Ensuring API contract alignment**
   - Engineer checks API docs to confirm fields and shapes before wiring data to components.

---

## 3. Content & Layout Overview

- **GraphQL-backed project context:**
  - Fetches project information to tailor tokens/components.
- **Design tokens section:**
  - Cards or tables showing color palette, typography scale, spacing.
- **Components section:**
  - List/grid of components with brief descriptions and usage hints.
- **API docs section:**
  - Links or embedded summaries of key APIs used by this project.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Plain-language descriptions:**
  - Each token group and component should have a short explanation.
- **Visual examples:**
  - Show swatches or typography samples, not just names.
- **Linking to deeper docs:**
  - Provide clear links to full design system and API documentation.

---

## 5. Completeness and Real-World Coverage

The Design tab should support:

1. **Multiple token sets** (e.g., brand vs semantic colors).
2. **Different component families** (navigation, forms, feedback, layout).
3. **Project-specific overrides** (future), clearly marked as such.

---

## 6. Modern UI/UX Nuances and Features

- **Responsive layout:**
  - Token and component cards should wrap neatly on smaller screens.
- **Search/filter:**
  - Future: filter components by category or usage.
- **Copyable references:**
  - Easy copy of token names or component import paths.

---

## 7. Coherence and Consistency Across the App

- Tokens and components here must match those used in **Canvas**, **Build**, and **Monitor** views.
- API docs referenced here should be consistent with DevSecOps reports and backend contracts.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#24-design----designtsx`
- Route implementation: `src/routes/app/project/design.tsx`
- Global design system docs: refer to `@ghatana/ui` and shared tokens documentation.

---

## 9. Open Gaps & Enhancement Plan

1. Show which canvas nodes use which components/tokens.
2. Flag mismatches between design system versions and project usage.
3. Add search/filter for large component sets.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Design System
Subtitle: Tokens, components, and APIs used by this project.

Layout (desktop)
-------------------------------------------------------------------------------
| Tokens & Theme (left)                    | Components & APIs (right)        |
-------------------------------------------------------------------------------

[ Tokens ]
- Brand colors
  • Primary / brand:    #2563EB (Blue 600)
  • Accent / success:   #16A34A (Green 600)
  • Danger / error:     #DC2626 (Red 600)
- Semantic colors
  • Background: #0B1120 (dark), #F9FAFB (light)
  • Border:     #1F2937
- Typography
  • Heading 1:  32px / 40px, weight 600
  • Body:       14px / 20px, weight 400
  • Code:       13px / 18px, mono

[ Components ]
- Primary Button
  • Usage: Main call-to-action on forms and dialogs.
  • Variants: primary, secondary, subtle.
  • Props: `size`, `variant`, `icon`, `isLoading`.

- Page Card
  • Usage: Surface for page-level summaries (used in Overview & Backlog).
  • Contains: title, subtitle, status chip(s), primary action.

- Metric Tile
  • Usage: Small KPI blocks in Overview, Build, and Monitor.
  • Examples: "Build Success Rate", "Error Rate", "Deploys per Week".

[ APIs ]
- Orders API
  • Path: `GET /api/orders`  (paginated list)
  • Key fields: `id`, `status`, `totalAmount`, `createdAt`, `customerId`.
  • Used by: "Recent Orders" widget on Monitor and DevSecOps reports.

- Metrics API
  • Path: `GET /api/metrics/projects/:projectId`
  • Key metrics: `buildSuccessRate`, `deployFrequency`, `errorRateP95`.
  • Used by: Overview KPIs, Build/Deploy summary chips.
```
