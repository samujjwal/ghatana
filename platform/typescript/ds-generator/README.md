# @ghatana/ds-generator

## Purpose

`@ghatana/ds-generator` provides design-system preset materialization and brand customization utilities for generating branded token outputs and CSS artifacts from shared schema-driven design inputs.

## Dependencies

- `@ghatana/ds-schema` for validated schema contracts
- `@ghatana/tokens` for token definitions and shared token primitives
- `zod` for runtime validation during generator workflows

## Usage

Import preset or branding helpers from the root package or subpath exports:

```ts
import { materializePreset, renderPresetToCss } from '@ghatana/ds-generator';
import { applyBrand } from '@ghatana/ds-generator/brand';
```

Build and validate locally:

```bash
pnpm --filter @ghatana/ds-generator build
pnpm --filter @ghatana/ds-generator test
```

## Public API Surface

- Preset materialization helpers and preset schemas under `./presets`
- Brand customization helpers under `./brand`
- Root exports from `src/index.ts` for preset and brand workflows