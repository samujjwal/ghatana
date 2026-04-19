# Composition Architecture

## Goals

The design system needs one composition model that supports:

- Very small primitives with near-zero ceremony
- Complex multi-slot components with layered behavior
- First-class accessibility, observability, privacy, and security metadata
- Data-driven authoring for builder/codegen workflows
- An optimization path from runtime composition to precomputed render plans

## Research Signals

This model intentionally borrows proven ideas from the ecosystem:

- React / React Compiler:
  Optimize hot paths by keeping render inputs explicit and compiler-friendly instead of hiding everything in ad hoc closures.
- Radix UI:
  Slot and `asChild`-style composition work well when prop handoff and event merging are deterministic.
- Ark UI / React Aria:
  Explicit part/state metadata (`data-part`, `data-state`, `data-scope`) makes styling, testing, and accessibility integration more durable.
- Zag.js:
  Complex behavior is easier to scale when it is modeled as composable behavior units instead of inline event logic in every component.
- MUI:
  `slots` / `slotProps` patterns are effective, but should be backed by stronger metadata and cleaner ownership rules.
- DTCG / schema-driven systems:
  Data contracts matter if components need to flow through builders, validation, codegen, and brand generation safely.

## Ghatana Model

The current composition stack is intentionally layered:

1. `core/primitives.ts`
   Primitive metadata, privacy-safe attributes, prop merging, and sanitized telemetry.

2. `core/composition.ts`
   Runtime composition hook that merges metadata, state, features, slot props, and behaviors.

3. `core/behaviors.ts`
   Reusable behaviors such as pressable interaction. This is where richer behaviors should accumulate over time.

4. `core/recipes.ts`
   Data-driven recipe/compiler layer that turns declarative component definitions into render plans and then into runtime composition.

5. `core/platform.ts`
   Platform-neutral emission layer that converts recipe output into serializable target plans for React, HTML, Web Components, React Native, SwiftUI, Jetpack Compose, and future generators.

## Why This Stands Out

The model is stronger than a typical slot API because it treats cross-cutting concerns as first-class composition data:

- Accessibility metadata flows through the same slot and state pipeline as styles.
- Observability is structured and privacy-aware, not bolted on as arbitrary analytics callbacks.
- Privacy labels exist at the component and slot layer and can align with schema/builder contracts.
- Recipes produce serializable render plans, which means runtime rendering and codegen can converge on the same source of truth.
- Platform render plans separate serializable semantics, attributes, styles, and capability hints from React-specific runtime props, which keeps future generators from being boxed into React internals.

## Optimization Strategy

Use two levels of composition:

- Runtime composition:
  Best for app-authored components and dynamic state-heavy UIs.

- Compiled recipe plans:
  Best for builder output, repeated patterns, and components that benefit from precomputed metadata, slot topology, and feature/state signatures.

- Platform render plans:
  Best for generator handoff, cross-platform code emission, preview tooling, and optimization passes that should not depend on React prop semantics.

This keeps simple components simple, while giving complex or generated components a path to become more static and more optimized.

## Next Steps

- Add more behaviors: focus-visible, disclosure, selection, async/pending, collection/navigation.
- Extend recipes with collection semantics and virtualization hints.
- Bridge recipe and platform render plans into `@ghatana/ui-builder` codegen and preview rendering.
- Align privacy metadata more directly with `@ghatana/ds-schema` data classification.
- Move more core components onto recipes once the behavior library is broader.
