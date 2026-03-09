# Library Spec – @ghatana/charts

Shared chart primitives built on top of Recharts with Ghatana theming.

---

## 1. Purpose & Scope

- Provide **reusable chart components** (line, bar, area, pie, etc.) that:
  - Use `recharts` under the hood.
  - Integrate with `@ghatana/theme` and `@ghatana/ui` for consistent styling and behavior.

From `package.json`:

- Name: `@ghatana/charts`.
- Description: "Shared chart primitives built on top of Recharts with Ghatana theming".
- Peer deps: `@ghatana/theme`, `@ghatana/ui`, React, `recharts`.

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- Wrap `recharts` with opinionated defaults (colors, typography, tooltips) based on the theme.
- Expose **composable chart components** (e.g., `TimeSeriesChart`, `KpiSparkline`).

**Non-responsibilities:**

- No domain-specific metrics or aggregation logic.
- No data fetching; charts receive data via props.

---

## 3. Consumers & Typical Usage

- AEP UI, App Creator dashboards, Software Org control towers.
- Any app needing a consistent representation of metrics (errors, throughput, latency, etc.).

Conceptual example:

```tsx
import { TimeSeriesChart } from "@ghatana/charts";

<TimeSeriesChart data={points} xKey="timestamp" yKey="value" />;
```

---

## 4. Dependencies & Relationships

- Depends on `@ghatana/theme` and `@ghatana/ui` for theming and base UI primitives.
- Should remain focused on visualization, not layout.

---

## 5. Gaps, Duplicates, Reuse Misses

- **Ad-hoc charts in apps:**

  - Apps might directly use `recharts` without `@ghatana/charts`.  
    → Identify and migrate common patterns here.

- **Theming consistency:**
  - Ensure color palettes align with tokens/themes used across the platform (not hard-coded hex).

---

## 6. Enhancement Opportunities

1. **Chart templates:**

   - Provide preconfigured charts for common observability metrics (success/error rate, latency histograms) without encoding backend logic.

2. **Accessibility:**
   - Integrate with `@ghatana/utils/accessibility` and `@yappc/accessibility-audit` for keyboard/ARIA patterns and fallbacks.

---

## 7. Usage Guidelines

- Prefer `@ghatana/charts` over direct `recharts` imports when building shared dashboards.
- Keep chart components data-agnostic; domain naming should come from calling code.
