# @yappc/config-schema

Declarative configuration schemas for data-driven product development in YAPPC.

## Purpose

Provides Zod-based validation schemas for:
- **IntentConfig**: Natural language intent definitions with AI lineage
- **RequirementConfig**: Structured requirements with acceptance criteria
- **PageConfig**: Declarative page/layout configurations
- **InterfaceDefinition**: Input/output contract definitions
- **ConnectionDefinition**: Event/data/navigation wiring definitions

## Installation

```bash
pnpm add @yappc/config-schema
```

## Usage

```typescript
import { PageConfig, IntentConfig, RequirementConfig } from '@yappc/config-schema';
import { PageConfigValidator } from '@yappc/config-schema/validation';
```

## Architecture

- **Schemas** (`src/schemas/`): Zod schema definitions
- **Types** (`src/types/`): TypeScript type definitions
- **Validation** (`src/validation/`): Validation utilities
- **Migration** (`src/migration/`): Schema migration utilities

## Dependencies

- `zod`: Runtime validation
- `@yappc/core`: Core types and utilities

## Development

```bash
# Build
pnpm build

# Watch mode
pnpm dev

# Test
pnpm test

# Type check
pnpm type-check

# Lint
pnpm lint
```

## Guidelines

- **Strict TypeScript**: All code is fully typed with `strict: true`
- **No `any` types**: All types are explicit
- **Reuse before creating**: Reuses existing SchemaRegistry pattern from YAPPC
- **Tests included**: All features have corresponding tests

## Related

- [@yappc/config-compiler](../config-compiler/): Compiler layer for artifact generation
- [@yappc/core](../yappc-core/): Core types and utilities
