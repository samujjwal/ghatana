# Ghatana Design System — Complete Specification

**Version:** 1.0  
**Date:** 2026-03-16

This specification defines the Ghatana Design System for web and mobile. It is organized around reusable primitives, standardized tokens, accessibility, and a token exchange model aligned with the Design Tokens Community Group format work. citeturn934637search23turn934637search15

Accessibility requirements should target WCAG 2.2 AA by default. WCAG 2.2 is organized around four principles: perceivable, operable, understandable, and robust. citeturn934637search2turn934637search6

## 1. Goals

Ghatana should provide:

- one visual language across web and mobile
- low cognitive load, high clarity
- token-first theming
- accessibility by default
- composable primitives
- domain components for healthcare workflows
- controlled forms/charts/visualization patterns
- support for Tailwind and React Native

## 2. System layers

```text
Tokens
  -> Primitives
    -> Controls
      -> Composite Components
        -> Domain Components
          -> Page Patterns
```

## 3. Package structure

```text
packages/
  ghatana-tokens/
  ghatana-primitives/
  ghatana-icons/
  ghatana-components/
  ghatana-forms/
  ghatana-charts/
  ghatana-domain-health/
  ghatana-mobile/
  ghatana-tailwind-preset/
  ghatana-docs/
```

### 3.1 Platform Library Reuse

The Ghatana Design System builds upon and extends existing platform libraries. **Reuse these libraries before creating new implementations:**

| Library                  | Location                             | Purpose                                    | PHR Usage                                        |
| ------------------------ | ------------------------------------ | ------------------------------------------ | ------------------------------------------------ |
| `@ghatana/tokens`        | `platform/typescript/tokens/`        | Design tokens (color, spacing, typography) | Import token values; extend for healthcare       |
| `@ghatana/theme`         | `platform/typescript/theme/`         | Theme provider, CSS variables, dark mode   | Wrap with healthcare-specific theme overrides    |
| `@ghatana/design-system` | `platform/typescript/design-system/` | Base components, patterns, layouts         | Extend for healthcare domain components          |
| `@ghatana/charts`        | `platform/typescript/charts/`        | Chart primitives, visualization patterns   | Use for vitals trends, lab results, analytics    |
| `@ghatana/i18n`          | `platform/typescript/i18n/`          | Internationalization, RTL support          | Add medical terminology localization             |
| `@ghatana/utils`         | `platform/typescript/utils/`         | Shared utilities, type helpers             | Use for form validation helpers, date formatting |

**Reuse Rules:**

- Import from `@ghatana/*` packages before creating local implementations
- Extend base components rather than duplicating
- Healthcare-specific overrides belong in `ghatana-domain-health/`
- Follow existing token naming conventions from `@ghatana/tokens`

**Example Import Pattern:**

```typescript
// Correct: Reuse platform library
import { ThemeProvider } from "@ghatana/theme";
import { tokens } from "@ghatana/tokens";
import { Button, Card, DataTable } from "@ghatana/design-system";

// Incorrect: Creating duplicate local implementation
// import { Button } from './components/Button'; // Don't do this
```

### 3.2 Documentation Standards

All public components, functions, and types must include JSDoc annotations with `@doc.*` tags for consistency and IDE support.

**Required Tags:**

| Tag            | Purpose                      | Example                                             |
| -------------- | ---------------------------- | --------------------------------------------------- |
| `@doc.type`    | Classification of the entity | `component`, `hook`, `utility`, `type`              |
| `@doc.purpose` | One-line description         | `Renders a patient summary card`                    |
| `@doc.layer`   | Architecture layer           | `primitive`, `control`, `composite`, `domain`       |
| `@doc.pattern` | Design pattern used          | `compound-component`, `render-prop`, `hook-pattern` |
| `@doc.domain`  | Healthcare domain            | `patient`, `provider`, `billing`, `admin`           |

**Example:**

````typescript
/**
 * Patient summary card for dashboard views
 *
 * @doc.type component
 * @doc.purpose Renders a patient summary card with key demographics and alerts
 * @doc.layer domain
 * @doc.pattern compound-component
 * @doc.domain patient
 * @example
 * ```tsx
 * <PatientSummaryCard patientId="patient-123" showAlerts />
 * ```
 */
export function PatientSummaryCard(props: PatientSummaryCardProps) {
  // implementation
}
````

## 4. Token architecture

### Token categories

- color
- typography
- spacing
- sizing
- radius
- border
- shadow
- opacity
- motion
- z-index
- breakpoint
- focus
- iconography
- semantic status

### Token naming model

```text
<category>.<role>.<variant>
```

Examples:

- `color.surface.default`
- `color.text.muted`
- `color.action.primary`
- `space.4`
- `radius.md`
- `shadow.overlay.md`

### Token tiers

- global tokens
- semantic tokens
- component tokens

## 5. Foundation tokens

### Brand color example

```json
{
  "color": {
    "brand": {
      "50": "#eef7ff",
      "100": "#d9ecff",
      "200": "#baddff",
      "300": "#8fc8ff",
      "400": "#5ca9ff",
      "500": "#2f86f6",
      "600": "#1f6ad6",
      "700": "#1954ad",
      "800": "#194a8b",
      "900": "#1a3f72"
    }
  }
}
```

### Typography

- `font.family.sans`
- `font.family.mono`
- type sizes: xs -> 4xl
- weights: 400, 500, 600, 700

### Spacing

Use a 4px scale:

- 0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64

### Radius

- none
- sm
- md
- lg
- xl
- 2xl
- round

### Motion

- fast 120ms
- normal 180ms
- slow 240ms

## 6. Theming

Required themes:

- light
- dark
- high-contrast light
- high-contrast dark

Rules:

- semantic tokens are the consumption layer
- components do not hardcode raw palette values
- web uses CSS variables
- mobile uses token maps + theme provider

## 7. Primitive components

- Box
- Flex
- Stack
- Grid
- Text
- Heading
- Icon
- Divider
- VisuallyHidden
- FocusRing
- Portal

## 8. Controls

- Button
- IconButton
- Input
- Textarea
- Select
- MultiSelect
- Checkbox
- Radio
- Switch
- Slider
- DateInput
- DateRangePicker
- SearchInput
- Combobox
- FileUpload
- OTPInput

## 9. Composite components

- Card
- Banner
- Alert
- Toast
- Modal
- Drawer
- Tabs
- Accordion
- Breadcrumbs
- Pagination
- DataTable
- DataGrid
- EmptyState
- Skeleton
- CommandPalette
- Stepper
- Timeline
- FilterBar

## 10. Domain-health components

> **Note:** These healthcare domain components are PHR-product-specific and should be created in the PHR product codebase (`~/features/healthcare/components`) rather than in the shared design system initially. Promote to `@ghatana/design-system` only if reused across multiple products.

- PatientCard
- PatientHeader
- EncounterTimeline
- ObservationTrendChart
- LabResultTable
- MedicationTable
- MedicationAdherenceBar
- ImmunizationSchedule
- InsuranceClaimStatus
- ConsentAccessPanel
- AppointmentCalendar
- EmergencySummaryCard
- ShareAccessDrawer
- FhirResourceViewer
- AuditTrailList

## 11. Forms

Use React Hook Form + Ghatana wrappers.

Form primitives:

- Form
- FormSection
- FormField
- FormLabel
- FormControl
- FormDescription
- FormError
- FieldArrayGroup

Validation:

- use Zod schemas
- align client/server validation
- standardized error/help patterns

## 12. Charts & visualization

Expose charts through Ghatana wrappers backed by **Recharts** (per `@ghatana/charts` platform library). Use D3 or Visx only for custom visualizations not covered by Recharts.

Chart primitives:

- Axis
- GridLines
- Tooltip
- Legend
- Marker
- ThresholdBand
- TrendLine

Domain charts:

- VitalsTrendChart
- LabTrendChart
- MedicationAdherenceChart
- AppointmentUtilizationChart
- ClaimStatusFunnel
- PopulationHealthMap
- HealthRiskGauge

Rules:

- no color-only meaning
- textual summaries for critical charts
- explicit thresholds
- print/export friendly defaults

## 13. Accessibility

WCAG 2.2 AA target is mandatory baseline. citeturn934637search2turn934637search18

### Perceivable

- text contrast meets AA
- non-text contrast meets AA
- status not conveyed by color alone

### Operable

- full keyboard support
- visible focus indicators
- touch targets appropriate for mobile

### Understandable

- clear labels
- consistent validation
- plain language for health concepts

### Robust

- semantic HTML on web
- screen-reader support
- ARIA only where needed

### Healthcare-specific additions

- high contrast mode
- large text mode compatibility
- reduced motion support
- caption/transcript support for telemedicine UI

## 14. Tailwind integration

Create `ghatana-tailwind-preset` that maps tokens into:

- colors
- spacing
- radii
- shadows
- fonts
- breakpoints

## 15. Testing

### Visual

- Storybook
- screenshot regression tests
- light/dark/high-contrast snapshots

### Functional

- interaction tests
- keyboard navigation tests
- focus trap tests

### Accessibility

- automated checks
- manual audits on critical patterns
- chart usability review

## 16. Governance

Roles:

- design system owner
- accessibility reviewer
- frontend platform reviewer
- domain UX reviewer

Versioning:

- semver
- migration notes
- codemods for major API transitions when possible

## 17. Starter token JSON example

```json
{
  "color": {
    "surface": {
      "default": { "$value": "{color.neutral.0}" },
      "subtle": { "$value": "{color.neutral.50}" }
    },
    "text": {
      "default": { "$value": "{color.neutral.900}" },
      "muted": { "$value": "{color.neutral.600}" }
    },
    "action": {
      "primary": {
        "bg": { "$value": "{color.brand.600}" },
        "fg": { "$value": "{color.neutral.0}" }
      }
    }
  },
  "space": {
    "1": { "$value": "4px" },
    "2": { "$value": "8px" },
    "4": { "$value": "16px" }
  },
  "radius": {
    "sm": { "$value": "4px" },
    "md": { "$value": "8px" },
    "lg": { "$value": "12px" }
  }
}
```

## 18. Final recommendation

Build Ghatana as:

- token-first
- accessibility-first
- healthcare-aware
- Tailwind-powered
- cross-platform
- wrappers-first for forms and charts
- governed like a product
