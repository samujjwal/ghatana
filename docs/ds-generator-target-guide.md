# DS Generator Target Guide

> **Package**: `@ghatana/ds-generator`
> **Status**: Production
> **Last Updated**: 2026-05

## Overview

The DS Generator converts a `DesignSystemDocument` into platform-specific output:

| Target | Function | Output |
|--------|----------|--------|
| CSS Custom Properties | `emitCss()` | `.css` file |
| JSON Token File | `emitJson()` | `.json` file |
| Tailwind Config | `emitTailwind()` | `tailwind.config.js` string |
| React Theme Module | `emitReactTheme()` | `.ts` module |

---

## DesignSystemDocument

```ts
import { createDesignSystemDocument, DS_DOCUMENT_SCHEMA_VERSION } from "@ghatana/ds-generator";

const doc = createDesignSystemDocument({
  id: "doc-uuid",
  name: "My Design System",
  primitiveTokens: {
    color: { primary: "#3b82f6", "primary-dark": "#1d4ed8" },
    spacing: { sm: "0.5rem", md: "1rem", lg: "1.5rem" },
  },
  semanticAliases: {
    "color-brand": "color.primary",
    "color-brand-hover": "color.primary-dark",
  },
});
```

Schema version: `DS_DOCUMENT_SCHEMA_VERSION = "1.0.0"`

---

## CSS Target

```ts
import { emitCss } from "@ghatana/ds-generator";

const css = emitCss(doc, {
  prefix: "ghatana",    // Default: "ghatana"
  selector: ":root",    // Default: ":root"
});
// Produces:
// :root {
//   --ghatana-color-primary: #3b82f6;
//   --ghatana-color-brand: var(--ghatana-color-primary);
//   ...
// }
```

**Important**: CSS custom properties always have the `--` prefix. The `prefix` option adds the vendor prefix _after_ the double dash: `--{prefix}-{token}`.

---

## JSON Target

```ts
import { emitJson } from "@ghatana/ds-generator";

const { tokens } = emitJson(doc, { prefix: "ghatana" });
// tokens: Record<string, string>  (flat key-value pairs)
```

---

## Tailwind Target

```ts
import { emitTailwind } from "@ghatana/ds-generator";

const config = emitTailwind(doc, { prefix: "ghatana" });
// Returns a complete Tailwind config string referencing CSS variables
```

---

## React Theme Target

```ts
import { emitReactTheme } from "@ghatana/ds-generator";

const module = emitReactTheme(doc, { prefix: "ghatana" });
// Returns a TypeScript module exporting a typed theme object
```

---

## Token Graph

The token graph resolves alias chains and detects cycles:

```ts
import { buildTokenGraph, graphToRecord, flattenTokenRecord } from "@ghatana/ds-generator";

const result = buildTokenGraph(doc.primitiveTokens, doc.semanticAliases ?? {});

if (!result.ok) {
  for (const error of result.errors) {
    if (error.kind === "cycle") {
      console.error("Circular alias:", error.path.join(" → "));
    }
    if (error.kind === "missing-token") {
      console.error("Missing token referenced by alias:", error.alias);
    }
  }
} else {
  const resolved = graphToRecord(result.graph);
}
```

---

## Contrast Audit

```ts
import { auditContrastPairs, deriveColorPairs } from "@ghatana/ds-generator";

const pairs = deriveColorPairs(doc);
const audit = auditContrastPairs(pairs);
// audit.entries: Array<{ foreground, background, ratio, passesAA, passesAAA }>
// audit.failCount: number
```

WCAG thresholds: AA ≥ 4.5, AAA ≥ 7.0 (normal text).

---

## Brand CSS

The brand emitter produces a complete CSS token set from a brand preset:

```ts
import { renderBrandToCss } from "@ghatana/ds-generator";

const css = renderBrandToCss(brandPreset, { prefix: "ghatana" });
// Includes: color, spacing, typography, shadow, motion, zIndex tokens
```

---

## CI Gate

```bash
pnpm check:ds-generator-golden
```
