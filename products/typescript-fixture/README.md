# TypeScript Fixture Product

A minimal TypeScript Node API service and library used to validate the PnpmNodeApiAdapter can handle TypeScript projects through the full lifecycle.

## Purpose

This fixture product validates:
- PnpmNodeApiAdapter validate phase (tsc typecheck)
- PnpmNodeApiAdapter test phase (vitest)
- PnpmNodeApiAdapter build phase (tsc build)
- PnpmNodeApiAdapter package phase (tsc build)
- Environment strategy detection for pnpm projects

## Structure

- `src/index.ts` - Simple library with Greeter class
- `src/main.ts` - Node API handler
- `package.json` - Package configuration with dependencies and scripts
- `tests/handler.test.ts` - Unit tests for the handler
- `tsconfig.json` - TypeScript configuration

## Lifecycle Phases

```bash
# Validate (runs tsc --noEmit)
pnpm typecheck

# Test (runs vitest)
pnpm test

# Build (runs tsc)
pnpm build

# Package (runs tsc)
pnpm build
```

## Dependencies

- typescript
- vitest
- tsx

## Artifacts

- Library: `dist/index.js`, `dist/index.d.ts`
- Service: Same build output (Node services are packaged as CommonJS/ESM bundles)
