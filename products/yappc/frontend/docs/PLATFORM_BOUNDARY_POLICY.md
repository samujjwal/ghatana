# YAPPC Platform Boundary Policy

**Owner**: YAPPC Frontend Team  
**Status**: Active  
**Last Updated**: 2026-04-13

---

## Purpose

This document defines the canonical ownership rules for every UI package and capability in the YAPPC frontend workspace. It answers the question: **"Where does this go — shared platform or YAPPC product?"**

All new capability and changes **must** follow these rules before merging. When in doubt, consult this document and make the boundary explicit in your PR description.

---

## Three-Layer Architecture

```
Layer 1: Shared Platform  (@ghatana/*)
         Generic engines, contracts, tokens, themes, canvas runtime, builder

Layer 2: YAPPC Product Foundation  (@yappc/product-theme, @yappc/core, @yappc/ui)
         Product presets, lifecycle navigation, product-specific UI extensions

Layer 3: YAPPC App Experiences  (@yappc/web-app routes and components)
         Routes, workspace flows, idea/design/build/deploy/observe surfaces
```

No capability should skip a layer or place product concerns inside shared platform.

---

## Canonical Package Ownership Table

### Shared Platform — stays in `@ghatana/*`

| Package | What belongs here | What does NOT belong here |
|---------|-------------------|---------------------------|
| `@ghatana/design-system` | Generic atoms, molecules, layout primitives, accessibility behaviors, common interaction patterns | YAPPC-specific workflow components, lifecycle-branded UI, YAPPC personas |
| `@ghatana/tokens` | Token schema, token runtime, standard scale primitives, token validation | YAPPC brand colors, lifecycle semantic tokens, product-specific aliases |
| `@ghatana/theme` | Theme runtime, hooks/providers, brand/schema infrastructure, CSS variable contract | YAPPC MUI bridge logic, product-level color overrides |
| `@ghatana/canvas` | Canvas engine, chrome, collaboration abstractions, telemetry/testing primitives | YAPPC node types, lifecycle zone semantics, phase/role actions |
| `@ghatana/ui-builder` | Builder document model, import/export, scene projection, preview protocol | YAPPC-specific page schemas, lifecycle-aware builder behaviors |
| `@ghatana/ds-generator` | Generic preset materialization, brand application, CSS rendering from token structures | YAPPC brand preset packages, lifecycle MUI adapter outputs |
| `@ghatana/code-editor` | Editor runtime, Monaco/LSP integration, generic editing surfaces | YAPPC-specific code generation panels |

### YAPPC Product Foundation — stays in `@yappc/*`

| Package | What belongs here | What does NOT belong here |
|---------|-------------------|---------------------------|
| `@yappc/product-theme` | YAPPC brand presets, lifecycle semantic tokens, MUI bridge to platform theme, product-specific token aliases | Generic theme primitives (keep those in `@ghatana/theme`) |
| `@yappc/ui` | Product-level composition components, YAPPC workflow specialty components | Generic base components (keep those in `@ghatana/design-system`), development-ui/initialization-ui/navigation-ui (these are separate packages) |
| `@yappc/development-ui` | Development phase-specific UI surfaces | Generic development tools — route-level orchestration |
| `@yappc/initialization-ui` | Project initialization and onboarding flows | Generic form components (keep in `@ghatana/design-system`) |
| `@yappc/core` | YAPPC domain models, product lifecycle types, business rules | Platform infrastructure |
| `@yappc/state` | YAPPC app state atoms, jotai stores | Generic state utilities (keep in `@ghatana/platform-utils`) |
| `@yappc/ai` | YAPPC AI workflow orchestration, lifecycle prompt templates | Generic LLM client wiring (keep in `platform:java:ai-integration`) |

### YAPPC App Layer — stays in route/component code

| Area | What belongs here | What does NOT belong here |
|------|-------------------|---------------------------|
| Routes (`routes/`) | Route orchestration, page-level composition, navigation guards | Business logic, reusable components (extract to libs) |
| Components (`components/`) | Page-specific composition, layout assembly | Route navigation decisions, global state mutations |
| Hooks (`hooks/`) | Route-scoped state, page-specific effects | Cross-route shared state (keep in `@yappc/state`) |

---

## Freeze Rules

### Rule 1: No new generic capability in `@yappc/ui`
Any capability that could serve multiple products or is not YAPPC-workflow-specific **must** go into a `@ghatana/*` package.

To add generic capability to `@yappc/ui`, open a justification issue with:
- Why `@ghatana/design-system` cannot serve this need
- Why this is genuinely shared across YAPPC features

### Rule 2: No product-specific logic in `@ghatana/*` packages
Any capability that encodes YAPPC lifecycle, personas, phase semantics, or YAPPC brand **must not** be added to a `@ghatana/*` package.

### Rule 3: `@yappc/ui` must not re-export standalone `@yappc/*` packages
`@yappc/ui` must not expose `./development-ui`, `./initialization-ui`, or `./navigation-ui` subpaths when standalone packages (`@yappc/development-ui`, `@yappc/initialization-ui`) already exist for those surfaces.

### Rule 4: No `@ts-nocheck` in new code or core integration layers
New files must never use `@ts-nocheck`. Integration layer files (`app-theme.tsx`, key providers, route files) require full TypeScript typing.

---

## Decision Guide

**"Should this component go into `@ghatana/design-system` or `@yappc/ui`?"**
- Could another Ghatana product (tutorputor, data-cloud, AEP) use it without knowing about YAPPC? → `@ghatana/design-system`
- Does it encode YAPPC lifecycle phases, personas, or product workflow meaning? → `@yappc/ui`

**"Should this token go into `@ghatana/tokens` or `@yappc/product-theme`?"**
- Is it a primitive scale (space, size, color ramp, typography)? → `@ghatana/tokens`
- Does it represent a YAPPC lifecycle phase color, product brand preset, or MUI bridge alias? → `@yappc/product-theme`

**"Should this canvas node type go into `@ghatana/canvas` or product code?"**
- Is it a generic authoring primitive (text, frame, connector)? → `@ghatana/canvas`
- Does it represent a YAPPC concept (lifecycle zone, sprint card, devsecops node)? → YAPPC product code

---

## ESLint Enforcement

The `eslint-rules/ghatana-architecture-rules.js` workspace ESLint config enforces:
- `@ghatana/*` packages must not import from `@yappc/*`
- Route files must not import directly from `@ghatana/tokens` (use `@yappc/product-theme` or `@yappc/ui`)

See `eslint-rules/dependency-policy.json` for the full rule set.

---

## Change Process

1. Identify the layer (platform / product foundation / app)
2. Confirm the package ownership using this document
3. If the boundary is ambiguous, discuss in the PR with explicit reference to this policy
4. Do not merge boundary violations — fix forward, no shims
