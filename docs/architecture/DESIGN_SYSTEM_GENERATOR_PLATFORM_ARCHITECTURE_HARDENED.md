# Design System Generator Platform Architecture

Reviewed and expanded on 2026-04-10 from:
- `/Users/samujjwal/Downloads/design-system-platform-architecture-spec.md`
- Ghatana repo platform-library and package scan
- Current external standards and ecosystem references

---

## 1. Purpose

This document defines a production-grade architecture for a **Design System Generator Platform** whose goal is not only to ship one design system, but to let any team:

1. adopt a high-quality preset design system as-is,
2. customize that preset to fit a brand or product family,
3. generate a brand-new design system from the same engine and governance model,
4. and publish the result as a reusable Ghatana platform library rather than a one-off product UI.

The platform is intended to be:

- **maintainable**: strong separation of concerns, strict contracts, minimal duplication,
- **highly customizable**: tokens, themes, recipes, component anatomy, density, motion, accessibility, AI UX,
- **fully templatized**: presets, pattern packs, starter templates, multi-brand output,
- **AI/ML native**: AI-assisted authoring, auditing, governance, and AI-specific UX patterns,
- **portable**: web-first, but capable of emitting assets for multiple runtimes and toolchains,
- **reusable in-repo**: aligned to Ghatana’s `platform/typescript/*` workspace model and top-level facade pattern.
- **observable by default**: user-visible state, decision transparency, telemetry, tracing, and auditability are part of the product contract, not afterthoughts.

The architecture follows modern design-system practice where **tokens, foundations, components, patterns, tooling, documentation, and governance** operate together as one coherent system.

---

## 2. Design Goals

### 2.1 Primary goals

- Provide a **single canonical model** for tokens, themes, components, patterns, templates, and governance.
- Support both **opinionated presets** and **fully custom systems**.
- Ensure **accessibility, consistency, testability, and migration safety** are first-class.
- Make customization safe by separating:
  - semantics,
  - behavior,
  - anatomy,
  - recipe/styling,
  - tokens,
  - documentation,
  - governance.
- Make the platform **AI-native** in two ways:
  - as a platform for AI-assisted creation and maintenance of a design system,
  - and as a platform containing first-class components and rules for AI-powered user experiences.
- Make the platform **autonomous but user-respectful**:
  - involve the user only when policy, confidence, permissions, or impact thresholds require it,
  - otherwise act proactively while preserving clear visibility into what the system is doing and why.
- Fit naturally into Ghatana’s current reusable-library stack:
  - `@ghatana/tokens`
  - `@ghatana/theme`
  - `@ghatana/platform-utils`
  - `@ghatana/accessibility`
  - `@ghatana/accessibility-audit`
  - `@ghatana/design-system`

### 2.2 Non-goals

- Not a thin wrapper around a single third-party visual language.
- Not a set of ungoverned CSS utilities with no schema or compatibility guarantees.
- Not a one-off internal component library with hardcoded brand values.
- Not a generator that prioritizes speed over correctness, accessibility, or maintainability.
- Not a repo-wide rewrite that breaks existing product UIs before compatibility shims and codemods exist.

---

## 3. Product Model

The platform should be conceived as a **Design System Engine** with the following hierarchy:

1. Foundations
2. Token model
3. Theme model
4. Primitive runtime
5. Component recipe engine
6. Component implementations
7. Pattern library
8. Template library
9. Docs/testing/governance toolchain
10. AI/ML assistance and auditing layer

This aligns with current ecosystem practice:

- the Design Tokens Community Group formalizes portable token exchange and alias/reference mechanics,
- Atlassian treats foundations and design tokens as a shared source of truth for color, spacing, typography, elevation, and more,
- Carbon emphasizes tokens, theming, layering, components, and open governance,
- Open UI standardizes component names, anatomies, states, and behaviors across design systems,
- W3C WAI APG and WCAG 2.2 make keyboard behavior, focus visibility, and alternative interaction paths explicit requirements,
- Storybook now treats stories as a shared surface for docs, interaction tests, accessibility checks, and visual testing.

---

## 4. Core Principles

### 4.1 Canonical source of truth

Every design decision must resolve back to a canonical source:

- foundations define design intent,
- tokens define abstract values,
- themes map tokens to brands/modes,
- recipes map component slots to tokens,
- components implement semantics + behavior + styling hooks,
- patterns compose components into workflows,
- templates package patterns into reusable product shells.

### 4.2 Primitive-first architecture

All higher-level components should be built from a small set of stable primitives. This reduces duplication, keeps customization centralized, and improves long-term maintainability.

### 4.3 Token-driven rendering

No visual value should be hardcoded in feature components unless explicitly allowed by policy. Component recipes should resolve from design tokens and semantic aliases.

### 4.4 Accessibility by contract

Each component and pattern must have an explicit accessibility contract, keyboard model, focus behavior, screen-reader expectations, and test coverage.

### 4.5 Multi-axis customization

Customization should be supported across:

- brand,
- color,
- typography,
- density,
- radius,
- motion,
- platform,
- locale,
- accessibility mode,
- AI trust/disclosure policy.

### 4.6 Composable, not fork-heavy

Consumers should extend through configuration, recipes, slots, and patterns before resorting to forks.

### 4.7 AI-native governance

The platform should proactively detect duplication, drift, inaccessible states, broken contracts, and excessive customization entropy.

### 4.8 Compatibility-first evolution

Because Ghatana already has packages in production-like use, the generator must evolve the stack with:

- semver,
- migration manifests,
- codemods,
- facade compatibility,
- deprecation windows,
- product-by-product adoption plans.

### 4.9 Autonomous by default, interruptible by policy

AI/ML capabilities should act proactively when the system has sufficient confidence and permission to proceed safely.

Human involvement should be required only when:

- confidence is below policy threshold,
- the action is high-impact or irreversible,
- approval is legally or operationally required,
- the user has requested manual control,
- the system detects conflicting goals, ambiguous intent, or elevated risk.

### 4.10 Visibility before opacity

If the system acts implicitly, it must still make the action legible.

Users should be able to see:

- what the system is doing,
- why it is doing it,
- what inputs informed the action,
- what changed,
- what remains uncertain,
- how to intervene, override, or roll back where supported.

### 4.11 Observability as a product contract

Telemetry and operational visibility are first-class requirements for AI-native experiences.

Every major AI-assisted or autonomous interaction should be instrumented for:

- user-visible status,
- structured events,
- metrics,
- traces,
- audit records,
- privacy-aware logs,
- success/failure outcomes,
- human override and approval events.

---

## 5. User Modes

The platform must support three user modes.

### Mode A: Preset adoption

A team chooses a preset and uses it with minimal or no customization.

Examples:
- enterprise dense,
- minimal SaaS,
- editorial,
- AI copilot,
- public-sector accessibility-first,
- mobile-first consumer.

### Mode B: Preset + brand customization

A team starts from a preset and customizes:
- palette,
- typography,
- density,
- radius,
- motion,
- icon family,
- logo,
- surface treatments,
- AI disclosure style.

### Mode C: Fully custom system

A team defines:
- token scales,
- theme mappings,
- component recipes,
- slot anatomy variations,
- pattern rules,
- template rules,
- governance policies.

All three modes must still pass through the same canonical schema and validation pipeline.

---

## 6. Reference Architecture

### 6.1 High-level logical architecture

```text
+--------------------------------------------------------------------------------+
|                   Design System Generator Platform                             |
+--------------------------------------------------------------------------------+
| Authoring UI / CLI / APIs / Figma Sync / AI Copilot                            |
+--------------------------------------------------------------------------------+
| Schema Registry | Token Engine | Theme Engine | Recipe Engine                  |
+--------------------------------------------------------------------------------+
| Primitive Runtime | Component Library | Pattern Library | Visibility Runtime   |
+--------------------------------------------------------------------------------+
| Template Generator | Docs | Storybook | Tests | Codemods                       |
+--------------------------------------------------------------------------------+
| Governance | A11y | AI Audits | Telemetry | Tracing | Audit | Policy Gates     |
+--------------------------------------------------------------------------------+
| Outputs: TS, CSS vars, JSON, docs, stories, templates, metadata, telemetry     |
+--------------------------------------------------------------------------------+
```

### 6.2 Main domains

#### A. Schema and registry domain

Stores and validates:
- tokens,
- themes,
- primitives,
- component anatomy,
- recipe definitions,
- pattern definitions,
- template definitions,
- accessibility contracts,
- documentation metadata,
- version and compatibility metadata.

#### B. Token and theme domain

Responsible for:
- token declarations,
- semantic aliases,
- brand packs,
- modes,
- platform overrides,
- density variants,
- token transforms,
- build outputs.

#### C. Component domain

Responsible for:
- primitives,
- behavior contracts,
- anatomy,
- slots,
- variants,
- states,
- composition rules,
- component testing.

#### D. Pattern and template domain

Responsible for:
- workflow-level composition,
- page structures,
- domain templates,
- empty/loading/error/success flows,
- AI-assisted interaction templates.

#### E. Tooling domain

Responsible for:
- Storybook generation,
- docs generation,
- type generation,
- migration tooling,
- lint rules,
- codemods,
- changelog automation.

#### F. AI/ML domain

Responsible for:
- brand brief to token suggestions,
- design drift analysis,
- duplicate detection,
- accessibility risk detection,
- AI component recommendations,
- design-to-code assistance,
- runtime AI UX compliance checks.

#### G. Visibility and observability domain

Responsible for:
- structured telemetry events,
- OpenTelemetry-compatible traces and spans,
- decision and action logs,
- user-visible operation status,
- approval and override telemetry,
- provenance capture,
- auditability of AI-assisted changes,
- dashboards and alert-friendly metadata.

#### H. Repo-integration domain

Responsible for:
- mapping generated artifacts into `platform/typescript/*`,
- preserving package boundaries,
- protecting against cyclic dependencies,
- generating compatibility shims for existing consumers,
- publishing through Ghatana’s workspace and release process.

---

## 7. Canonical Data Model

### 7.1 Token model

The token model should be **DTCG-aligned** so it can interoperate cleanly with external tooling.

#### Token categories

- color
- typography
- spacing
- sizing
- radius
- border
- elevation
- opacity
- shadow
- motion duration
- motion easing
- z-index
- breakpoint
- icon size
- stroke width
- grid/layout values

#### Token layers

1. **Global tokens**
   - raw, foundational values
   - examples: `color.blue.500`, `space.4`, `radius.md`

2. **Semantic tokens**
   - intent-oriented aliases
   - examples: `text.primary`, `surface.default`, `border.subtle`, `action.primary.bg`

3. **Component tokens**
   - component-scoped aliases
   - examples: `button.primary.bg`, `dialog.shadow`, `input.focus.ring`

4. **Mode tokens**
   - light, dark, high-contrast, reduced-motion, etc.

5. **Brand tokens**
   - brand A vs brand B mappings

6. **Density/platform tokens**
   - compact/cozy/comfortable
   - web/mobile/desktop

#### Suggested schema shape

```json
{
  "id": "token.action.primary.bg",
  "$type": "color",
  "$value": "{color.blue.600}",
  "description": "Primary action background",
  "$extensions": {
    "ghatana": {
      "layer": "semantic",
      "mode": "light",
      "brand": "default",
      "platform": ["web"],
      "deprecated": false
    }
  }
}
```

#### Hardening requirements

- Use DTCG-style `$type`, `$value`, aliases, and extension blocks instead of an ad hoc schema.
- Support token provenance metadata:
  - source preset,
  - author,
  - generator version,
  - migration lineage.
- Add token lifecycle fields:
  - `status`,
  - `introduced`,
  - `deprecated`,
  - `removedAfter`,
  - `replacement`.
- Encode constraints where possible:
  - min/max ranges for opacity,
  - allowed scales for spacing,
  - semantic category restrictions.
- Support contextual layering tokens for reusable surfaces, following the kind of layer/context split used by Carbon.

### 7.2 Theme model

A theme is a validated mapping of semantic intent to token values under a named configuration.

#### Theme dimensions

- brand
- mode
- density
- platform
- motion preference
- contrast level
- locale/typographic profile

#### Theme responsibilities

- resolve semantic aliases,
- guarantee minimum contrast rules,
- express visual identity,
- expose CSS variables and code artifacts,
- remain backward-compatible within declared contracts.

#### Hardening requirements

- Themes must define completeness requirements for all public semantic tokens.
- Themes must validate accessibility invariants before publish:
  - text/background contrast,
  - focus indicator visibility,
  - disabled-state discernibility,
  - non-color affordance coverage where required.
- Theme overrides must be isolated from token authoring; consumers override mappings, not raw package internals.
- Themes must support both compile-time export and runtime switching.

### 7.3 Primitive model

Each primitive should declare:
- name,
- purpose,
- supported props,
- supported style hooks,
- token usage boundaries,
- accessibility considerations,
- compositional rules.

### 7.4 Component model

Each component must declare:
- stable identity,
- category,
- anatomy/slots,
- supported variants,
- supported states,
- supported sizes,
- supported densities,
- interaction model,
- keyboard behavior,
- a11y contract,
- theming hooks,
- component tokens,
- documentation contract,
- test contract,
- migration metadata.

#### Example component schema shape

```json
{
  "name": "Button",
  "category": "input",
  "anatomy": ["root", "label", "iconLeading", "iconTrailing", "spinner"],
  "variants": ["primary", "secondary", "tertiary", "danger", "link"],
  "sizes": ["xs", "sm", "md", "lg"],
  "states": ["default", "hover", "pressed", "focusVisible", "disabled", "loading"],
  "a11y": {
    "role": "button",
    "keyboard": ["Enter", "Space"],
    "focus": "required-visible"
  },
  "tokens": [
    "button.primary.bg",
    "button.primary.text",
    "button.focus.ring"
  ]
}
```

#### Hardening requirements

- Components must model slot anatomy and behavior explicitly, borrowing from Open UI’s anatomy/state/behavior mindset.
- Public components must distinguish:
  - semantic props,
  - behavioral props,
  - visual variant props,
  - escape hatches.
- Every interactive component must define:
  - pointer interactions,
  - keyboard interactions,
  - touch behavior,
  - focus restoration rules where relevant,
  - live region behavior if dynamic.

### 7.5 Pattern model

A pattern is a validated composition of components for a recurring user goal.

Examples:
- auth flow,
- search + filter + sort,
- CRUD data table,
- approval flow,
- dashboard,
- content editor,
- AI assistant interaction loop,
- onboarding,
- settings,
- empty/loading/error/success states.

### 7.6 Template model

A template is a packaged composition of one or more patterns, navigation structure, page layouts, and starter content.

Examples:
- SaaS dashboard template,
- admin console,
- documentation site,
- marketing site,
- AI copilot app,
- data-heavy enterprise portal,
- commerce shell.

---

## 8. Ghatana Repo Reality and Reuse Strategy

The original draft assumed a greenfield `packages/` monorepo. Ghatana already has a partially formed shared frontend platform under `platform/typescript/*`, so the generator architecture must map onto that structure.

### 8.1 Current reusable assets found in repo

Existing platform libraries:

- `platform/typescript/tokens` → `@ghatana/tokens`
- `platform/typescript/theme` → `@ghatana/theme`
- `platform/typescript/design-system` → `@ghatana/design-system`
- `platform/typescript/accessibility-audit` → `@ghatana/accessibility-audit`
- `platform/typescript/accessibility` → `@ghatana/accessibility`
- `platform/typescript/testing` → `@ghatana/platform-testing`
- `platform/typescript/foundation/platform-utils` → `@ghatana/platform-utils`

Existing signals from repo docs:

- platform-library documents already describe `tokens`, `theme`, `ui`, `storybook`, `test-utils`, and `design-system`,
- `products/data-cloud/planes/action/ui/DESIGN_SYSTEM_ADOPTION.md` shows downstream adoption pressure already exists,
- the repo uses workspace packages and shared governance scripts rather than isolated app tooling.

### 8.2 Important repo findings

1. `@ghatana/tokens` and `@ghatana/theme` already provide the right foundation for generator output.
2. `@ghatana/design-system` currently behaves as both component library and high-level facade.
3. Repo documentation references `@ghatana/ui`, but the concrete platform package currently present is `@ghatana/design-system`; this is a naming and boundary mismatch.
4. The current design-system package exports many unrelated concerns together:
   - atoms/molecules/organisms,
   - tokens/theme helpers,
   - audit/privacy/security/voice/nlp namespaces.
5. The current docs are lighter than the ambition of the proposed generator; metadata, schema discipline, and lifecycle rules are still under-specified.

### 8.3 Recommended target package map for Ghatana

Rather than introduce an entirely separate parallel tree, evolve the existing stack toward:

```text
platform/typescript/
  schema/                  -> @ghatana/ds-schema
  registry/                -> @ghatana/ds-registry
  tokens/                  -> @ghatana/tokens
  theme/                   -> @ghatana/theme
  primitives/              -> @ghatana/primitives
  ui/                      -> @ghatana/ui
  patterns/                -> @ghatana/patterns
  templates/               -> @ghatana/templates
  storybook/               -> @ghatana/storybook
  testing/                 -> @ghatana/platform-testing
  accessibility-audit/     -> @ghatana/accessibility-audit
  foundation/platform-utils -> @ghatana/platform-utils
  design-system/           -> @ghatana/design-system
  cli/                     -> @ghatana/ds-cli
  generator/               -> @ghatana/ds-generator
  codemods/                -> @ghatana/ds-codemods
  governance/              -> @ghatana/ds-governance
```

### 8.4 Package responsibility model

- `@ghatana/tokens`
  - source of truth for token values and transforms
- `@ghatana/theme`
  - semantic mapping, theme runtime, brand presets
- `@ghatana/primitives`
  - low-level layout and interaction primitives
- `@ghatana/ui`
  - reusable components built from primitives
- `@ghatana/patterns`
  - higher-order recurring workflows
- `@ghatana/templates`
  - app starter shells
- `@ghatana/platform-testing`
  - internal shared testing support exposed to product consumers only through `@ghatana/design-system/testing`
- `@ghatana/design-system`
  - facade for apps and docs only
- `@ghatana/ds-*`
  - schema/registry/generator/codemod/governance toolchain

### 8.5 Boundary decisions

- Do not let `@ghatana/design-system` remain the implementation home for every layer.
- Do not let product apps depend directly on generator internals.
- Do not let low-level packages import the facade package.
- Treat the facade as the top of the dependency graph.

### 8.6 Shared-library inclusion criteria

Only content that is **truly cross-product and stable** belongs in the globally shared design-system library.

A module is eligible for the shared design system only if all of the following are true:

- it is expected to be used by multiple product families, not just one roadmap,
- it is domain-neutral and avoids product vocabulary,
- it expresses a stable UX primitive, generic component, or reusable pattern,
- it can be documented without referencing one product’s workflow,
- it can be versioned semantically with a believable compatibility promise,
- its dependencies are also globally shareable.

A module should **not** live in the shared design system if any of the following are true:

- it encodes product-specific business workflow,
- it depends on product APIs, product stores, or product routing assumptions,
- it is experimental and likely to churn before a second consumer exists,
- it is an internal authoring, generator, migration, or governance implementation detail,
- it is a convenience wrapper that exists only to support one product shell.

### 8.7 What other products are realistically expected to consume

The public shared design system should optimize for these common cross-product needs:

- foundational tokens and themes,
- layout primitives,
- generic form controls,
- generic navigation and overlay components,
- generic feedback and status components,
- generic data-display components,
- accessibility-safe interaction hooks,
- generic page-shell and dashboard scaffolding,
- AI-UX primitives that are policy-oriented rather than product-oriented,
- user-visible operation state and transparent system-status primitives,
- observability-ready interaction surfaces that can emit standardized telemetry without product-specific wiring.

It should **not** assume that all products will share:

- product-specific admin workflows,
- domain-specific metrics panels,
- vertical-specific approval flows,
- product-specific content studios,
- product-specific compliance dashboards,
- product-specific data visualizations unless they mature into generic patterns.

### 8.8 Four-layer organization model

To keep organization clean and control bloat, the design-system stack should be managed in four layers:

1. **Public facade**
   - what product teams import
   - example: `@ghatana/design-system`
2. **Internal shared implementation**
   - reusable but not public-by-default workspace modules
   - examples: primitives, UI internals, pattern internals, schema, registry
3. **Shared apps and maintainer tools**
   - docs site, theme studio, playground, admin, generator UI
4. **Product-local code**
   - anything specific to one product, one domain, or one roadmap

Rule:

- move code upward only when reuse and stability are proven,
- never move something to a higher layer just because multiple maintainers touch it,
- prefer the lowest layer that honestly fits the use case.

---

## 9. Recommended Package and Module Structure

### 9.1 Conceptual structure

```text
platform/typescript/
  ds-schema/
  ds-registry/
  tokens/
  theme/
  foundations/
  primitives/
  accessibility/
  motion/
  icons/
  ui/
  patterns/
  templates/
  storybook/
  testing/
  ds-lint-rules/
  ds-codemods/
  ds-figma-sync/
  ds-cli/
  ds-generator/
  ds-governance/
  ds-ai-assistant/
apps/
  theme-studio/
  docs-site/
  component-playground/
  design-system-admin/
```

### 9.2 Key package responsibilities

#### `@ghatana/ds-schema`

- canonical type definitions,
- JSON schemas,
- validation helpers,
- compatibility rules.

#### `@ghatana/ds-registry`

- centralized registry of components, recipes, tokens, themes, patterns,
- dependency graph resolution,
- deprecation lookup,
- capability discovery,
- compatibility checks.

#### `@ghatana/tokens`

- token authoring,
- transforms,
- CSS variable generation,
- JSON and TS export,
- validation.

#### `@ghatana/theme`

- theme packs,
- theme composition,
- brand/mode/density layering,
- runtime providers,
- compile-time outputs.

#### `@ghatana/primitives`

- stable low-level runtime layer:
  - `Box`
  - `Stack`
  - `Inline`
  - `Grid`
  - `Flex`
  - `Container`
  - `Surface`
  - `Text`
  - `Heading`
  - `Anchor`
  - `Pressable`
  - `Icon`
  - `FocusRing`
  - `VisuallyHidden`
  - `Portal`
  - `ScrollArea`
  - `Separator`
  - `AspectRatio`
  - `Slot`

#### `@ghatana/ui`

- domain-neutral reusable components organized by taxonomy but implemented over primitives and recipes.

#### `@ghatana/patterns`

- recurring compositions:
  - auth,
  - dashboard,
  - forms,
  - search/filter,
  - table workflows,
  - settings,
  - review and approval,
  - AI assistant flows.

#### `@ghatana/templates`

- installable starter packs for product types.

#### `@ghatana/storybook`

- story generation,
- shared preview/decorators,
- interaction tests,
- accessibility checks,
- visual regression integration.

#### `@ghatana/platform-testing`

- internal render helpers,
- a11y assertions,
- theme matrix fixtures,
- contract tests,
- visual-regression hooks,
- not a default product-consumer import surface.

#### `@ghatana/ds-cli`

Commands:
- `init`
- `create-theme`
- `create-component`
- `validate`
- `build-tokens`
- `build-docs`
- `generate-template`
- `audit`
- `migrate`
- `release`

#### `@ghatana/ds-generator`

- materializes a complete design system or starter app from presets plus overrides.

#### `@ghatana/ds-governance`

- policy engine for contribution, naming, compatibility, deprecation, quality gates.

#### `@ghatana/ds-ai-assistant`

- AI-backed authoring, auditing, and recommendations.

### 9.3 Public API policy: expose one product-facing library

The shared design-system experience for product teams should be centered on a **single public package**:

- `@ghatana/design-system`

Optional secondary public entry points are allowed only when they serve real cross-product needs and remain conceptually simple:

- `@ghatana/design-system/theme`
- `@ghatana/design-system/tokens`
- `@ghatana/design-system/testing`

Everything else should be treated as implementation detail, even if internally split across many workspace packages.

### 9.4 Public API surface

`@ghatana/design-system` should expose only these stable categories:

- `DesignSystemProvider`
- `ThemeProvider`, `useTheme`, brand preset selection
- primitives that product teams compose directly:
  - `Box`
  - `Stack`
  - `Inline`
  - `Grid`
  - `Container`
  - `Surface`
  - `Text`
  - `Heading`
  - `Icon`
- generic components:
  - buttons
  - inputs
  - selects
  - checkboxes/radios/switches
  - fields and validation messaging
  - dialogs/drawers/popovers/tooltips
  - tabs/breadcrumbs/pagination/menus
  - cards/badges/avatars/tables/lists
  - alerts/toasts/banners/spinners/skeletons
- a very small curated set of generic workflow helpers only when they are demonstrably reusable:
  - form layout
  - empty/loading/error/success states
  - basic page shell
  - search/filter/sort shell
  - generic AI disclosure/provenance primitives
  - user-visible operation status and review-state primitives

Root-package rule:

- the root package should optimize for **everyday product usage**,
- specialized token consumption belongs in `@ghatana/design-system/tokens`,
- specialized test utilities belong in `@ghatana/design-system/testing`.

### 9.4.1 Consumer-facing entry points

The public contract should be intentionally small and explicit.

Allowed entry points:

- `@ghatana/design-system`
- `@ghatana/design-system/theme`
- `@ghatana/design-system/tokens`
- `@ghatana/design-system/testing`

No other entry points should be considered public unless they are formally added to the contract and documented here.

### 9.4.2 Root package contract

`@ghatana/design-system` should be the default import for nearly all product use.

Simple product usage should look like:

```tsx
import {
  DesignSystemProvider,
  Button,
  Input,
  Card,
  PageShell,
} from "@ghatana/design-system";
```

It should export:

- providers:
  - `DesignSystemProvider`
  - `ThemeProvider`
- hooks:
  - `useTheme`
  - `useColorMode`
  - `useReducedMotion`
- primitives:
  - `Box`
  - `Stack`
  - `Inline`
  - `Grid`
  - `Container`
  - `Surface`
  - `Text`
  - `Heading`
  - `Icon`
- generic components:
  - `Button`
  - `IconButton`
  - `Input`
  - `TextArea`
  - `Select`
  - `Checkbox`
  - `Radio`
  - `Switch`
  - `Field`
  - `FormMessage`
  - `Dialog`
  - `Drawer`
  - `Popover`
  - `Tooltip`
  - `Tabs`
  - `Breadcrumbs`
  - `Pagination`
  - `Menu`
  - `Card`
  - `Badge`
  - `Avatar`
  - `Table`
  - `List`
  - `Alert`
  - `Banner`
  - `Toast`
  - `Spinner`
  - `Skeleton`
- generic state and shell helpers:
  - `EmptyState`
  - `ErrorState`
  - `LoadingState`
  - `PageShell`
  - `Section`
  - `SearchFilterBar`
- generic AI trust UX:
  - `AILabel`
  - `CitationBlock`
  - `ConfidenceIndicator`
  - `DataUseNotice`
  - `ToolUseDisclosure`
  - `OperationStatus`
  - `ReviewRequiredBanner`
  - `ChangeSummary`
  - `ActivityTimeline`
- public types only at the contract boundary:
  - component prop types
  - layout and provider contract types

### 9.4.3 Theme entry-point contract

`@ghatana/design-system/theme` should expose only:

- `ThemeProvider`
- `DesignSystemProvider`
- `useTheme`
- `useColorMode`
- `brandPresets`
- `createTheme`
- `resolveTheme`
- public theme types

It should not expose:

- schema validators,
- migration helpers,
- raw internal theme transforms,
- framework-specific bridge internals unless they are intentionally made public.

### 9.4.4 Tokens entry-point contract

`@ghatana/design-system/tokens` should expose only readonly consumption surfaces:

- semantic token objects,
- token lookup helpers safe for runtime consumption,
- CSS variable names/maps,
- public token types.

It should not expose:

- token authoring DSL,
- token transform pipeline,
- token registry internals,
- validation internals,
- source-file layout details.

### 9.4.5 Testing entry-point contract

`@ghatana/design-system/testing` should expose only:

- `renderWithDesignSystem`
- theme matrix test helpers,
- accessibility assertion helpers intended for consumers,
- contract-test utilities for verifying compliant composition,
- stable fixtures/mocks for consumer tests.

It should not expose:

- internal test harness wiring,
- Storybook implementation internals,
- private snapshots,
- generator verification internals.

### 9.4.6 What is intentionally not public

The following should not be part of the product-consumer API:

- atoms/molecules/organisms taxonomies,
- internal layering packages such as `@ghatana/primitives` or `@ghatana/ui`,
- recipe definitions,
- slot metadata registries,
- schema loaders,
- migration graphs,
- build scripts,
- design-tool synchronization clients,
- AI authoring copilots for maintainers.

Products should consume capabilities, not internal taxonomy.

### 9.4.7 Allowed and forbidden import examples

Allowed:

```ts
import { DesignSystemProvider, Button, Card } from "@ghatana/design-system";
import { useTheme } from "@ghatana/design-system/theme";
import { tokens } from "@ghatana/design-system/tokens";
import { renderWithDesignSystem } from "@ghatana/design-system/testing";
```

Forbidden:

```ts
import { Button } from "@ghatana/ui";
import { Stack } from "@ghatana/primitives";
import { tokenRegistry } from "@ghatana/ds-registry";
import { compileRecipe } from "@ghatana/design-system/internal/recipes";
import { colors } from "@ghatana/tokens/src/colors";
```

### 9.4.8 Proposed `exports` map

The public package should enforce the contract with an explicit `exports` map similar to:

```json
{
  "name": "@ghatana/design-system",
  "exports": {
    ".": {
      "types": "./dist/public/index.d.ts",
      "import": "./dist/public/index.js"
    },
    "./theme": {
      "types": "./dist/public/theme/index.d.ts",
      "import": "./dist/public/theme/index.js"
    },
    "./tokens": {
      "types": "./dist/public/tokens/index.d.ts",
      "import": "./dist/public/tokens/index.js"
    },
    "./testing": {
      "types": "./dist/public/testing/index.d.ts",
      "import": "./dist/public/testing/index.js"
    },
    "./package.json": "./package.json"
  }
}
```

Notably absent:

- no `./internal/*`
- no `./atoms/*`
- no `./molecules/*`
- no `./organisms/*`
- no deep paths into implementation folders

### 9.4.9 Recommended source layout for enforcement

The package source tree should mirror the contract:

```text
src/
  public/
    index.ts
    theme/
      index.ts
    tokens/
      index.ts
    testing/
      index.ts
  internal/
    primitives/
    components/
    patterns/
    templates/
    recipes/
    registry/
    schema/
    generator/
    governance/
```

This keeps the distinction enforceable both structurally and mentally.

### 9.4.10 API review checklist

A symbol should be exported publicly only if the answer to all of these is yes:

- Will more than one product realistically use it?
- Is it domain-neutral?
- Can we support it semantically for at least one deprecation window?
- Can we document it without exposing internal architecture?
- Would we be comfortable seeing this import in any product app?

If not, keep it internal.

### 9.4.11 Anti-bloat API design rules

To keep the shared library clean, extensible, comprehensive, and minimal at the same time:

- export by **capability**, not by implementation taxonomy,
- prefer one stable component per job instead of multiple near-duplicates,
- prefer configuration and composition over adding specialized wrappers,
- keep product vocabulary out of shared names,
- keep experimental APIs internal until they survive reuse in at least two products,
- add a new public export only when removing consumer complexity, not just exposing internals.

Concrete rules:

1. Do not publish `atoms`, `molecules`, `organisms`, or similar architecture labels as consumer API.
2. Do not publish separate public namespaces for every internal concern.
3. Do not publish convenience wrappers that save only a few lines for one product.
4. Do not publish product-specific AI, security, privacy, or domain helpers from the design-system root.
5. Prefer a single `Button` with clear variants over multiple public button families unless semantics truly differ.
6. Prefer `PageShell` and `Section` over publishing a long list of app-shell variants.
7. If two exports differ mostly by preset props, keep one public API and move presets internal.

### 9.4.12 Public API lifecycle model

Every candidate shared API should move through four stages:

1. **Product-local**
   - stays inside one product until reuse is proven.
2. **Incubating internal**
   - may move into an internal workspace package, but remains non-public.
3. **Shared public**
   - added to `@ghatana/design-system` only after satisfying inclusion criteria.
4. **Deprecated**
   - kept behind semver and migration policy until removal.

Promotion gate from internal to public:

- two or more credible consumers,
- stable name and semantics,
- docs ready,
- accessibility behavior defined,
- tests reusable across consumers,
- no product-specific dependencies.

### 9.4.13 Current-package cleanup direction

The current `@ghatana/design-system` package shape indicates likely shared-library bloat risk because it publicly exposes:

- `./atoms/*`
- `./molecules/*`
- `./organisms/*`
- `./hooks`
- `./layout`
- `./utils`
- `./audit`
- `./privacy`
- `./security`
- `./voice`
- `./nlp`
- `./selection`

Recommended cleanup direction:

- keep `.` as the main consumer surface,
- keep only `./theme`, `./tokens`, and `./testing` as secondary public entry points,
- fold broadly reusable hooks into the root only if they are part of the stable contract,
- move `audit`, `privacy`, `security`, `voice`, `nlp`, and `selection` behind internal packages unless they become independently justified public libraries,
- retire consumer-facing taxonomy exports such as `./atoms/*`, `./molecules/*`, and `./organisms/*`.

### 9.4.14 Split between shared libraries and shared apps

Not everything shared should become a library. Use this split:

Shared libraries:

- tokens,
- themes,
- primitives,
- generic components,
- a very small curated set of generic workflow helpers,
- testing helpers,
- design-system facade.

Shared apps:

- theme studio,
- docs site,
- component playground,
- design-system admin,
- internal governance dashboard,
- generator authoring UI.

Rule of thumb:

- if consumers import it into product code, it is a library concern,
- if users visit it in a browser or maintainers operate it as a tool, it is an app concern,
- do not convert internal tools into public libraries just because they are reused by maintainers.

### 9.4.15 Minimal extensibility model

Extensibility should come from a few controlled mechanisms:

- theme overrides,
- sanctioned component variants,
- slot composition,
- pattern composition,
- provider configuration.

Implicit AI/ML behavior should be extensible only through governed policy and provider configuration, not through ad hoc hidden side effects.

Extensibility should **not** come from:

- unrestricted deep imports,
- exposing internal recipe compilers,
- leaking registry or schema internals,
- allowing each product to depend on hidden internal modules directly.

This gives products room to extend while keeping the public contract small.

### 9.5 Explicitly hidden internals

The following should remain internal and structurally hidden from product consumers:

- schema packages,
- registry packages,
- token build/transformation internals,
- recipe compiler/runtime internals,
- codemods,
- generator implementation,
- governance engine,
- Figma sync internals,
- story generation internals,
- migration manifests,
- AI authoring and audit pipelines.

These may exist as workspace packages, but they should be:

- private packages where possible,
- unexported from the public facade,
- stored under internal-only source trees such as `src/internal/*`,
- omitted from public documentation except for contributor docs.

### 9.6 Structural hiding rules

To hide package and internal details both logically and physically:

1. Product apps import only from `@ghatana/design-system` and its approved secondary entry points.
2. Product apps do not import from `platform/typescript/*` paths directly.
3. Internal workspace packages should be marked private unless external publication is intentional.
4. Public packages must use explicit `exports` maps so deep internal paths cannot be imported accidentally.
5. Public package folders should separate public and private code:
   - `src/public/*`
   - `src/internal/*`
6. Root barrel exports should expose only contract-level types and components.
7. Internal helper names should not appear in public docs or examples.
8. Facade APIs should prefer stable nouns and verbs over implementation-oriented names such as `registry`, `compiler`, `recipe-engine`, or `schema-loader`.

### 9.7 Logical hiding rules

Even when an internal concept exists, product consumers should not need to understand it.

Consumer mental model:

- choose a theme,
- wrap the app in the provider,
- use shared primitives and components,
- optionally use a small set of reusable patterns,
- rely on testing helpers to stay compliant.

Internal mental model, hidden from consumers:

- schema validation,
- token resolution,
- recipe compilation,
- registry loading,
- governance enforcement,
- migration graph management,
- generator orchestration.

### 9.8 Revised package strategy

The internal architecture can stay modular, but the external contract should look like this:

```text
Public:
  @ghatana/design-system
  @ghatana/design-system/theme
  @ghatana/design-system/tokens
  @ghatana/design-system/testing

Internal-only workspace modules:
  @ghatana/ds-schema
  @ghatana/ds-registry
  @ghatana/ds-generator
  @ghatana/ds-codemods
  @ghatana/ds-governance
  @ghatana/primitives
  @ghatana/ui
  @ghatana/patterns
  @ghatana/templates
```

`@ghatana/primitives`, `@ghatana/ui`, `@ghatana/patterns`, and `@ghatana/templates` may still exist to keep implementation ownership clean, but they should generally be considered **internal composition layers**, not default product-consumer APIs.

### 9.9 Pattern publication policy

Patterns are the easiest place for accidental over-sharing, so publication should be conservative.

Publish a pattern to the shared design system only if it is:

- generic,
- domain-neutral,
- likely to be adopted across products,
- configurable without product conditionals,
- testable against a stable contract.

Otherwise:

- keep it in the product,
- or place it in an incubator/internal package until a second real consumer proves it should graduate.

### 9.10 Publication matrix

Use the following default publication policy for design-system-related modules:

```text
Category                                  Default visibility
----------------------------------------  -------------------
tokens/theme consumption                  Public
primitives                                Public via facade only
generic UI components                     Public via facade only
generic state/shell helpers               Public via facade only
consumer testing helpers                  Public via dedicated testing entry point
pattern implementations                   Internal by default
template implementations                  Internal by default
schema/registry/generator/governance      Internal
authoring and migration tooling           Internal or maintainer-only
storybook/docs/playground/admin UIs       Shared apps, not libraries
product-specific wrappers                 Product-local
```

This should be the default unless there is a strong reason to widen exposure.

---

## 10. Primitive Layer Specification

The primitive layer should remain small, stable, and heavily governed.

### Required primitives

- `Box`: generic token-aware container
- `Stack`: vertical layout
- `Inline`: horizontal layout with wrapping/gaps/alignment
- `Grid`: token-aware grid abstraction
- `Flex`: low-level flex layout where needed
- `Container`: responsive width boundaries
- `Surface`: themed background/elevation container
- `Text`: body and semantic text rendering
- `Heading`: heading semantics + type scale
- `Anchor`: link primitive
- `Pressable`: generic interactive trigger primitive
- `Icon`: icon renderer with sizing + accessibility hooks
- `Focusable` / `FocusRing`: standardized focus treatment
- `VisuallyHidden`: screen-reader-only content
- `Portal`: overlay rendering root
- `ScrollArea`: styled and accessible scroll container
- `Separator`: semantic divider
- `AspectRatio`: media framing helper
- `Slot`: anatomy slot composition helper

### Primitive design rules

- Accept only sanctioned style props or recipe hooks.
- Enforce tokens-first styling.
- Expose semantic HTML as defaults.
- Preserve escape hatches only where explicitly needed.
- Do not let primitive APIs become a second CSS system.
- Keep responsive and density behavior declarative, not ad hoc.

---

## 11. Component Recipe System

The recipe system is the key to flexibility.

### 11.1 Recipe responsibilities

A recipe should define:
- anatomy slots,
- base styles,
- variants,
- sizes,
- state mappings,
- token references,
- density mappings,
- motion mappings,
- compound variants,
- platform exceptions.

### 11.2 Recipe example

```ts
const buttonRecipe = {
  slots: ["root", "label", "iconLeading", "iconTrailing", "spinner"],
  base: {
    root: {
      borderRadius: "{radius.md}",
      minHeight: "{size.control.md}",
      transitionDuration: "{motion.fast}"
    }
  },
  variants: {
    tone: {
      primary: { root: { background: "{button.primary.bg}", color: "{button.primary.fg}" } },
      secondary: { root: { background: "{button.secondary.bg}", color: "{button.secondary.fg}" } }
    },
    size: {
      sm: { root: { paddingInline: "{space.3}", height: "{size.control.sm}" } },
      md: { root: { paddingInline: "{space.4}", height: "{size.control.md}" } }
    }
  },
  states: {
    hover: { root: { background: "{button.primary.bg.hover}" } },
    disabled: { root: { opacity: "{opacity.disabled}" } }
  }
};
```

### 11.3 Recipe constraints

- Recipes may not introduce arbitrary unsanctioned values unless policy allows.
- Recipes should be diffable and versioned.
- Recipes should be overrideable by composition, not mutation where possible.
- Slot/state coverage must be complete for all public variants.
- Recipe overrides must preserve semantic token usage whenever possible.

---

## 12. Theming and Customization Model

### 12.1 Supported customization axes

- Brand identity
- Color model
- Typography model
- Density profile
- Radius profile
- Motion profile
- Accessibility profile
- Platform profile
- Localization profile
- AI trust/disclosure profile

### 12.2 Levels of override

1. Preset selection
2. Theme override
3. Recipe override
4. Component slot override
5. Pattern override
6. Template override

### 12.3 Guardrails

- Prevent unsafe overrides that break contrast or semantics.
- Prevent state incompleteness.
- Require tests/docs updates when public contracts change.
- Warn on theme divergence beyond configured thresholds.
- Disallow direct hardcoded visual values in generated downstream code unless explicitly marked as local exceptions.

### 12.4 Output artifacts

The platform should emit:
- CSS variables,
- JSON tokens,
- TypeScript theme contracts,
- documentation metadata,
- visual previews,
- Storybook stories,
- Figma export/sync artifacts,
- migration manifests.

---

## 13. Complete Inventory Baseline

The original spec’s broad inventory is retained as the platform target. The generator should model support for:

- foundations,
- primitives,
- forms and input,
- buttons and actions,
- navigation,
- overlays,
- feedback and status,
- data display,
- media,
- layout and page structure,
- utility and advanced interaction,
- AI-native components,
- patterns,
- templates.

Implementation does not need to be complete on day one, but the schema, generator, and roadmap must treat these as first-class categories rather than ad hoc future ideas.

---

## 14. AI/ML-Native Design System Capabilities

### 14.1 Authoring-time AI

The platform should support:
- brand brief to token proposals,
- screenshot/style reference to theme suggestions,
- token naming suggestions,
- duplicate token detection,
- duplicate component detection,
- recipe normalization suggestions,
- docs generation,
- story generation,
- edge-state generation,
- migration plan generation.

### 14.2 Governance-time AI

The platform should detect:
- one-off hardcoded values,
- inaccessible contrast combinations,
- missing AI transparency patterns,
- theme drift,
- recipe sprawl,
- component overlap,
- missing states,
- missing test/docs coverage.

### 14.3 Runtime AI UX support

The platform should offer reusable patterns for:
- labeling AI-generated content,
- showing provenance/sources,
- clarifying scope and consent,
- allowing review/approval,
- exposing uncertainty,
- collecting user feedback,
- avoiding misleading anthropomorphism where policy requires restraint.

### 14.3.1 Native and implicit AI/ML interaction model

AI/ML should be a first-class citizen in the platform in two runtime modes:

1. **Explicit AI mode**
   - the user invokes an assistant, suggestion flow, analysis panel, or AI-authored action directly.
2. **Implicit AI mode**
   - the system uses AI/ML in the background to rank, recommend, classify, summarize, detect anomalies, prefill, validate, or prepare next actions without forcing the user into a separate assistant flow.

Both modes must follow the same contracts for:

- visibility,
- telemetry,
- auditability,
- overrideability,
- privacy,
- policy enforcement.

### 14.3.2 User-involvement policy

The prime goal is to involve the user only when needed.

The system should proceed autonomously when all of the following are true:

- the action is within its declared permission scope,
- confidence is above threshold,
- the action is reversible or low-impact,
- there is no legal, financial, privacy, or compliance approval requirement,
- the user has not opted into stricter manual control.

The system should request user involvement when any of the following are true:

- confidence is low or conflicting,
- the action is destructive, externally visible, or costly,
- sensitive data handling requires consent or acknowledgement,
- approval is required by policy,
- the user needs to choose among materially different outcomes,
- the system cannot explain the basis of its recommendation well enough.

### 14.3.3 Visibility contract for implicit AI

If AI/ML is used implicitly, the system must still provide visibility through reusable UX patterns.

Users should be able to inspect:

- active operation status,
- suggested vs applied actions,
- rationale or explanation summary,
- provenance or evidence references,
- uncertainty/confidence indicators,
- approval and override state,
- recent autonomous actions and their outcomes.

The goal is:

- low interruption,
- high visibility,
- predictable control.

### 14.3.4 Review and override model

Every autonomous or semi-autonomous flow should define:

- whether it is advisory, assistive, or acting,
- whether it requires pre-approval, post-hoc review, or no review,
- how the user can override or undo outcomes,
- which actions are blocked behind explicit approval,
- which actions are merely surfaced for awareness.

### 14.3.5 Telemetry and o11y model for AI-native UX

Telemetry and observability are first-class citizens for AI-native flows.

Every meaningful AI-assisted interaction should emit structured signals for:

- `ai.operation.started`
- `ai.operation.completed`
- `ai.operation.failed`
- `ai.suggestion.shown`
- `ai.suggestion.accepted`
- `ai.suggestion.rejected`
- `ai.action.applied`
- `ai.review.requested`
- `ai.review.approved`
- `ai.review.rejected`
- `ai.override.invoked`
- `ai.feedback.submitted`

Each event should include policy-safe metadata such as:

- operation type,
- component/pattern/template id,
- model/provider family if policy allows,
- confidence band,
- latency,
- token/cost band where appropriate,
- user-visible outcome,
- approval requirement and result,
- trace/span correlation ids,
- tenant/product/surface metadata,
- privacy classification.

### 14.3.6 Observability outputs

The platform should support:

- dashboards for AI-assisted interaction health,
- audit logs for autonomous and reviewed actions,
- traces for long-running or multi-step AI flows,
- metrics for acceptance, rejection, override, fallback, and latency,
- alerting hooks for failure spikes, anomalous model behavior, and high override rates.

### 14.3.7 AI-first-class component and pattern requirements

AI-related components and patterns should not be decorative wrappers. They must model:

- lifecycle state,
- provenance,
- review status,
- visibility state,
- approval state,
- feedback state,
- failure and fallback behavior,
- telemetry hooks,
- audit hooks.

### 14.4 AI product safety alignment

AI-facing components and templates should support:
- transparency,
- controllability,
- reviewability,
- safety messaging,
- context-aware disclosure,
- auditability.

### 14.5 Hardening requirements

- Treat AI-specific UX as a governed component domain, not just docs guidance.
- Capture policy variations as config:
  - disclosure requirements,
  - source citation requirements,
  - human-review requirements,
  - retention/logging notices,
  - unsafe-autonomy prohibitions.
- Separate AI capability metadata from marketing language.
- Standardize action-risk tiers so the user-involvement policy is enforceable across products.
- Require every AI-enabled pattern to declare:
  - autonomy level,
  - approval mode,
  - visibility mode,
  - rollback support,
  - telemetry coverage,
  - audit record expectations.

---

## 15. Accessibility and Quality Architecture

### 15.1 Accessibility baseline

The platform should target **WCAG 2.2 AA** as the default compliance baseline for web output, while still documenting stricter internal expectations where needed.

Additional must-haves:

- visible focus treatment,
- pointer alternatives for drag-only interactions,
- touch target guidance,
- reduced motion support,
- high-contrast theme support,
- keyboard-equivalent behavior for component interactions,
- screen-reader announcement guidance for dynamic content.

### 15.2 Quality gates per component

Every component must ship with:
- schema definition,
- anatomy definition,
- design token usage,
- docs,
- stories,
- accessibility notes,
- unit tests,
- interaction tests,
- visual regression coverage,
- theme coverage,
- telemetry semantics for meaningful interactions,
- visibility-state coverage for long-running or autonomous actions where applicable,
- migration metadata if public API changes.

### 15.3 Quality gates per pattern

Every pattern must ship with:
- happy path,
- loading state,
- empty state,
- validation state,
- permission-denied state where applicable,
- failure state,
- success state,
- responsive behavior,
- accessibility notes,
- AI disclosure states if AI is involved,
- review/approval states if AI is involved,
- observability notes covering emitted events, traces, and audit expectations.

### 15.4 Release gates

A release should fail if:
- tokens are invalid,
- theme mappings are incomplete,
- component states are undocumented,
- accessibility tests fail,
- required telemetry contracts are missing for AI-enabled or long-running interactions,
- audit or review-state coverage is missing for autonomous flows,
- visual diffs exceed policy without approval,
- public API changes lack migration notes,
- design-system lint rules fail.

### 15.5 Tooling alignment

The repo already has:
- shared testing packages,
- an accessibility-audit package,
- Storybook library specs,
- platform governance scripts.

The generator should use those rather than invent a parallel QA toolchain.

---

## 16. Documentation Strategy

The docs system should be generated from source metadata wherever possible.

### Required docs for each component

- overview,
- when to use,
- when not to use,
- anatomy,
- variants,
- sizes,
- states,
- accessibility,
- content guidance,
- AI guidance if applicable,
- examples,
- edge cases,
- migration notes,
- implementation notes,
- visibility behavior,
- telemetry emitted,
- review/approval behavior where applicable.

### Required docs for tokens/themes

- token taxonomy,
- naming conventions,
- semantic mapping rules,
- theme dimensions,
- customization boundaries,
- dark/high contrast rules,
- migration guidance.

### Required docs for patterns/templates

- purpose,
- composition map,
- UX rationale,
- states and transitions,
- accessibility,
- AI behavior/disclosure,
- autonomy and approval policy,
- visibility and observability behavior,
- examples,
- extension points.

### Hardening requirements

- Docs must be generated from canonical metadata, not maintained as drifting prose alone.
- Stories should be docs inputs, not parallel truth.
- Every public package should expose API reference plus decision guidance.

---

## 17. Versioning and Governance

### 17.1 Versioning model

Use semantic versioning across public packages, while also tracking compatibility across:
- schema version,
- token format version,
- theme contract version,
- component API version,
- recipe version.

### 17.2 Deprecation model

A deprecated token/component/pattern must include:
- status,
- deprecation version,
- removal target,
- replacement,
- codemod availability,
- migration guidance.

### 17.3 Contribution model

Require:
- schema validation,
- naming review,
- accessibility review,
- documentation completeness,
- tests,
- visual review,
- design review,
- migration plan where needed.

### 17.4 Governance hardening

- Enforce ownership metadata per package/component domain.
- Track exception records for policy overrides.
- Add drift reporting:
  - token drift,
  - theme drift,
  - pattern duplication,
  - local override hotspots.

---

## 18. Build and Delivery Pipeline

### 18.1 Pipeline stages

1. Validate schemas
2. Validate tokens/themes
3. Build token artifacts
4. Build component packages
5. Generate stories/docs
6. Run unit tests
7. Run interaction tests
8. Run accessibility checks
9. Validate telemetry and audit contracts
10. Run visual regression tests
11. Run lint/governance checks
12. Generate migration manifests
13. Publish packages and docs

### 18.2 Output channels

- npm/workspace packages,
- static documentation site,
- token bundles,
- design-tool sync artifacts,
- starter templates,
- changelog/release notes.

### 18.3 Repo-specific delivery notes

- Build outputs must fit the existing pnpm workspace and Turbo pipeline.
- Generated packages should not bypass current package governance or naming checks.
- Adoption should happen incrementally across products, starting with the platform layer and then selected product UIs.

---

## 19. Hardened Gap Analysis

### 19.1 Gaps in the original draft

1. **Greenfield package structure assumption**
   - The original draft assumed a fresh `packages/` tree and did not account for Ghatana’s real `platform/typescript/*` topology.

2. **Facade vs implementation boundaries**
   - The draft did not explicitly guard against collapsing primitives, components, AI helpers, and governance utilities into one package.

3. **Schema rigor**
   - DTCG alignment was mentioned “in spirit” but not defined strongly enough for portability.

4. **Open UI-style component specs**
   - Anatomy, states, and behavior were described, but not formalized as first-class component-spec obligations.

5. **Accessibility baseline currency**
   - The draft treated accessibility as central but did not explicitly set WCAG 2.2 AA as default for modern web output.

6. **Migration and adoption**
   - The draft lacked a repo migration plan for existing package consumers.

7. **Operational governance**
   - Missing explicit ownership, exception handling, and drift reporting models.

8. **AI policy modeling**
   - AI components were listed, but policy variability and audit metadata were not specified enough.

9. **Telemetry and user-visibility contract**
   - The draft did not make telemetry, tracing, user-visible operation state, and audit coverage first-class requirements for implicit AI behavior.

### 19.2 Gaps in the current repo stack

1. `@ghatana/design-system` is overloaded and should gradually become a curated facade.
2. Repo docs mention `@ghatana/ui`, but package reality is inconsistent.
3. Token/theme architecture exists, but its docs and schema depth are lighter than the desired platform.
4. There is no clear dedicated schema/registry/generator package set yet.
5. Shared governance exists at the repo level, but not yet as a first-class design-system governance engine.

---

## 20. Recommended Default Presets

Ship with a curated but small set of presets:

- **Enterprise Default**: balanced, dense-capable, neutral, accessible
- **Minimal SaaS**: clean, light, low visual noise
- **AI Copilot**: assistant-oriented, explicit disclosure/provenance patterns
- **Editorial**: typography-forward, spacious, content-led
- **Commerce**: merchandising-friendly, action-forward
- **Accessibility First**: high contrast, reduced motion defaults, explicit state clarity

Presets should differ mostly at the theme/recipe/pattern layer, not by duplicating the primitive runtime.

---

## 21. Implementation Roadmap

### 21.0 Execution principles

Execute the roadmap with these constraints:

- no new public entry points without explicit API review,
- no direct product adoption of internal workspace packages,
- no broad export additions during migration,
- prefer compatibility shims over breaking changes,
- move one concern at a time: contract first, internals second, product migrations third.
- treat AI autonomy, visibility, telemetry, and auditability as mandatory platform capabilities, not optional later enhancements.

### 21.1 Concrete minimal plan

The simplest workable plan is:

1. **Freeze the public contract**
   - declare `@ghatana/design-system`, `./theme`, `./tokens`, and `./testing` as the only supported public entry points,
   - stop adding new public deep imports.

2. **Split internals without changing product imports**
   - move primitives, UI internals, pattern internals, registry, and generator code behind internal boundaries,
   - keep the facade stable.

3. **Shrink the root surface**
   - remove taxonomy exports like `./atoms/*`, `./molecules/*`, and `./organisms/*`,
   - move specialized internal namespaces out of public exposure.

4. **Adopt in products through the facade only**
   - migrate apps to root and approved secondary entry points,
   - add lint rules to block forbidden imports.

5. **Promote only proven APIs**
   - when a product-local abstraction proves stable across multiple consumers, graduate it intentionally.

Definition of done for the migration:

- every product imports only approved entry points,
- no product imports internal workspace implementation packages,
- the root package is understandable without internal architecture knowledge,
- the public API list fits on one reference page,
- new additions require API review instead of ad hoc export growth,
- AI-enabled flows expose visible status, review state where needed, and telemetry coverage by default.

### Phase 1: Platform core

Build first:
- schema,
- registry,
- tokens,
- themes,
- foundations,
- primitives,
- accessibility utilities,
- visibility/runtime status primitives,
- telemetry, tracing, and audit contracts,
- docs/storybook baseline.

### Phase 2: Core component baseline

Build first-wave components:
- Button
- Input
- Textarea
- Select
- Checkbox
- Radio
- Switch
- Field
- Tabs
- Menu
- Tooltip
- Popover
- Dialog
- Drawer
- Badge
- Alert
- Card
- Avatar
- Table
- Pagination
- Breadcrumbs
- Spinner
- Skeleton
- OperationStatus
- ReviewRequiredBanner
- ChangeSummary
- ActivityTimeline

### Phase 3: Pattern library

Build:
- form pattern,
- auth pattern,
- dashboard pattern,
- CRUD table pattern,
- onboarding pattern,
- settings pattern,
- approval pattern.

### Phase 4: Generator and templates

Build:
- preset packs,
- theme studio,
- CLI generator,
- starter templates,
- docs generation,
- codemods.

### Phase 5: AI-native extensions

Build:
- AI component package,
- AI audit engine,
- brand brief/theme suggestion pipeline,
- screenshot-to-theme suggestions,
- duplicate detection,
- transparency/compliance checks,
- user-visibility patterns for implicit AI actions,
- standardized AI telemetry and trace schemas,
- approval/override policy enforcement for autonomous flows,
- dashboards and audit views for AI-assisted interactions.

### Phase 6: Repo migration

Execute:
- freeze the four approved public entry points and block new public deep imports,
- split overloaded responsibilities out of `@ghatana/design-system`,
- introduce `@ghatana/primitives` and `@ghatana/ui` if they do not already exist as real packages,
- keep `@ghatana/design-system` as a compatibility and adoption facade,
- move `atoms`, `molecules`, `organisms`, and specialized namespaces out of the supported public contract,
- migrate one product UI at a time using codemods and adoption guides,
- enforce policy in CI only after migration tooling is available,
- add lint and package-boundary checks so the minimal API surface does not regress.

---

## 22. Architectural Summary

The most durable architecture for this product is:

- one canonical schema,
- one registry,
- one token engine,
- one theme engine,
- one primitive runtime,
- many recipe packs,
- many component packages,
- many pattern/template packs,
- one governance and quality model,
- one AI-native assistance layer,
- one repo-fit migration path,
- one intentionally small public API surface.

This keeps the platform:
- maintainable,
- customizable,
- scalable across brands and products,
- compatible with modern tooling,
- compatible with Ghatana’s current monorepo,
- safe to consume because products see only the stable facade,
- and capable of generating both preset and fully custom design systems without fragmenting into multiple incompatible libraries.

---

## 23. Suggested Next Artifacts

1. **Schema Spec**
   - exact JSON schema definitions for tokens, themes, components, patterns, templates.

2. **Package-by-Package Technical Design**
   - responsibilities, public APIs, dependencies, test strategy, acceptance criteria.

3. **Token Taxonomy Spec**
   - naming, inheritance, modes, density, brand strategy, output targets.

4. **Primitive and Component API Spec**
   - detailed props, slots, states, accessibility contracts.

5. **Governance and Release Spec**
   - versioning, deprecation, review workflow, quality gates, migration rules.

6. **Repo Migration Plan**
   - how current `@ghatana/design-system`, `@ghatana/tokens`, `@ghatana/theme`, and downstream apps evolve without churn.

7. **Public Contract Spec**
   - exact allowed entry points, exports map, approved symbol list, forbidden import patterns, and review gate for public API additions.

8. **Live UI Builder And Execution Platform Architecture**
   - how the design-system platform feeds a canonical builder capability for page/component authoring, live execution, code visibility, and governed configurators without bloating `@ghatana/design-system`.

---

## 24. External References

These sources informed the hardening recommendations:

- Design Tokens Community Group, Format Module 2025.10:
  - https://www.designtokens.org/TR/2025.10/format/
- Atlassian Design System foundations and design tokens:
  - https://atlassian.design/foundations/
  - https://atlassian.design/foundations/tokens/design-tokens/
  - https://atlassian.design/foundations/color
  - https://atlassian.design/foundations/spacing
- Carbon Design System theming and layering:
  - https://carbondesignsystem.com/elements/color/usage/
- Open UI working model and component anatomy/state references:
  - https://open-ui.org/working-mode/
  - https://open-ui.org/design-system-analysis-guide/
  - https://open-ui.org/components/button/
  - https://open-ui.org/components/switch/
  - https://open-ui.org/components/tabs/
- W3C WAI APG and WCAG 2.2 guidance:
  - https://www.w3.org/WAI/ARIA/apg/patterns/button/examples/button/
  - https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements.html
- Storybook testing guidance:
  - https://storybook.js.org/docs/writing-tests/accessibility-testing
  - https://storybook.js.org/docs/9/writing-tests/interaction-testing
  - https://storybook.js.org/docs/writing-tests/visual-testing/
