# TypeScript Stub Package vs Implementation Decision Criteria

## Overview

This document defines the decision criteria for when to create a stub package versus a full implementation in `platform/typescript`. This ensures consistency across the platform and prevents accumulation of empty or placeholder packages.

## Background

During the platform folder audit (April 2025), 4 empty TypeScript stub packages were removed (ui-integration, platform-shell, capabilities, utils) because they provided no value. The `platform-utils` package was retained because it contained 8 actual utility files. Going forward, any new platform/typescript packages must follow these guidelines.

## Decision Criteria

### When to Create a Stub Package

A stub package is appropriate **only** when:

1. **Clear Implementation Plan Exists**
   - There is a documented roadmap or ADR (Architectural Decision Record)
   - The implementation timeline is defined (within 1-2 sprints)
   - The package has a designated owner

2. **Placeholder for Type Definitions**
   - The package defines shared types, interfaces, or schemas
   - These types are needed by other packages before full implementation
   - Example: `@ghatana/agent-types` defining agent contracts before runtime

3. **Dependency Constraints**
   - The package depends on external libraries not yet available
   - Waiting for infrastructure (e.g., design system components, canvas library)
   - The stub provides type safety and compilation stability

4. **Minimum Viable Content**
   - At minimum, the stub must contain:
     - `package.json` with proper dependencies and exports
     - At least one `.ts` file with types or utilities
     - JSDoc/TSDoc explaining the purpose and implementation plan
     - Basic unit tests for the types or utilities
     - TypeScript configuration (`tsconfig.json`)

### When to Create a Full Implementation

A full implementation is required when:

1. **Immediate Business Value**
   - The package is needed for a product feature in the current sprint
   - There is no dependency blocking implementation

2. **Shared Infrastructure**
   - The package provides reusable functionality for multiple products
   - Examples: state management (@ghatana/state), theming (@ghatana/theme), tokens (@ghatana/tokens)

3. **Types Alone Are Insufficient**
   - Just defining types without behavior provides no value
   - Other packages cannot meaningfully use the stub

## Package Content Requirements

### Stub Package Minimum Content

```json
{
  "name": "@ghatana/feature-stub",
  "version": "0.1.0",
  "type": "module",
  "exports": {
    "./package.json": "./package.json",
    ".": "./src/index.ts"
  },
  "types": "./dist/index.d.ts"
}
```

```typescript
// src/index.ts
/**
 * @ghatana/feature-stub
 *
 * Placeholder for [feature description]
 * 
 * IMPLEMENTATION PLAN:
 * - Phase 1 (Sprint X): [specific features]
 * - Phase 2 (Sprint Y): [specific features]
 * 
 * OWNER: [team/person]
 */

/**
 * Placeholder type defining the contract.
 * Full implementation planned for Sprint X.
 */
export interface FeatureContract {
  id: string;
  // Define minimal contract needed by dependents
}
```

### Full Implementation Requirements

- Complete implementation of all exported functions and components
- Comprehensive unit tests (>80% coverage)
- Integration tests where applicable
- Full TSDoc/JSDoc documentation
- Error handling with proper error types
- TypeScript strict mode enabled
- ESLint configuration with zero warnings
- Prettier formatting
- Build script and proper package exports

## Examples

### Acceptable Stub

```typescript
// @ghatana/agent-contracts (acceptable stub)
// Purpose: Define agent type contracts before runtime implementation

/**
 * Agent execution context type
 * Full runtime implementation in @ghatana/agent-core
 */
export interface AgentContext {
  agentId: string;
  input: unknown;
  metadata: Record<string, unknown>;
}

/**
 * Agent result type
 */
export interface AgentResult {
  success: boolean;
  output: unknown;
  error?: Error;
}
```

### Unacceptable Stub

```typescript
// Empty package with no exports
// Should be removed or not created
export {}; // REMOVE THIS PACKAGE
```

## Package Structure Standards

### Minimum Structure

```
platform/typescript/package-name/
├── package.json
├── tsconfig.json
├── src/
│   ├── index.ts (must have at least one export)
│   └── types.ts (optional, for type definitions)
├── __tests__/
│   └── index.test.ts (basic tests)
└── README.md (explains purpose and plan)
```

### Full Implementation Structure

```
platform/typescript/package-name/
├── package.json
├── tsconfig.json
├── vitest.config.ts (or jest.config.js)
├── src/
│   ├── index.ts
│   ├── components/
│   ├── hooks/
│   ├── utils/
│   └── types.ts
├── __tests__/
│   ├── unit/
│   └── integration/
├── .eslintrc.js
├── .prettierrc
└── README.md
```

## Governance

### Package Creation Checklist

Before creating a new `platform/typescript` package:

- [ ] Document the business requirement
- [ ] Create ADR explaining the package's purpose
- [ ] Define implementation timeline (if stub)
- [ ] Identify package owner
- [ ] Ensure minimum content requirements are met
- [ ] Add to platform/typescript README
- [ ] Verify no existing package provides similar functionality

### Package Review

- Platform team reviews all new package proposals
- Stubs are reviewed every 2 sprints for implementation progress
- Stubs without progress for >4 sprints are removed
- Full implementations require code review and test coverage validation
- All packages must pass TypeScript strict mode and ESLint checks

## Removed Packages (April 2025)

The following empty stub packages were removed during the audit:
- `ui-integration` (0 files)
- `platform-shell` (0 files)
- `capabilities` (0 files)
- `utils` (0 files)

**Retained:**
- `platform-utils` (8 files - actual utility functions)

These packages can be recreated only if they meet the criteria in this document.

## TypeScript-Specific Considerations

### Type Safety
- All packages must use `strict: true` in tsconfig.json
- No `any` types except in tightly justified boundary adapters
- Use `unknown` for untyped data with type guards
- Explicit null checks with `strictNullChecks`

### Dependency Management
- Use pnpm with lockfile validation
- Prefer peer dependencies for React, Jotai, etc.
- Avoid overlapping libraries without clear need
- Document why each dependency is required

### Export Patterns
- Use `package.json` `exports` field for path mapping
- Provide both ESM and CJS builds if needed by consumers
- Export types with `typesVersions` if needed
- Document public API surface in README

## References

- Platform folder audit: `docs/platform-folder-audit-4-21.md`
- Copilot instructions: `.github/copilot-instructions.md`
- Coding instructions: `MEMORY[coding-instructions.md]`
- ADR template: `docs/architecture/adr-template.md` (if exists)
