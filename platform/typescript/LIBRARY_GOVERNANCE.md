# Library Governance

> **Last Updated:** 2026-04-20 (post-audit Phase 3 remediation)
>
> **Authoritative Entry Point:** For the complete repository-wide engineering standards including package governance, see [`.github/copilot-instructions.md`](../../.github/copilot-instructions.md). This document provides the detailed platform-specific package registry and governance rules.

## When to Create a New Library

Create a new library when:

1. **Clear separation of concerns**: The library has a single, well-defined purpose
2. **Multiple consumers**: At least 2 different packages will use it
3. **No suitable existing library**: No existing library can accommodate the functionality
4. **Framework-agnostic**: The library is not tied to a specific framework unless necessary

## When NOT to Create a New Library

Do NOT create a new library when:

1. **Single consumer**: Only one package will use it
2. **Existing library suffices**: An existing library can be extended
3. **Unclear purpose**: The library's purpose is vague or mixed
4. **Temporary need**: The functionality is experimental or short-lived

## Library Creation Process

1. **Proposal**: Create a proposal document describing:
   - Library name and purpose
   - Intended consumers
   - Dependencies
   - API surface
   - Why existing libraries cannot be used

2. **Review**: Submit proposal for review to platform team

3. **Approval**: Get approval before implementation

4. **Implementation**: Follow platform library patterns:
   - Use `tsc` for building
   - Extend `tsconfig.base.json`
   - Include tests
   - Document public API

5. **Validation**: Ensure all tests pass before merge

## Canonical Platform Library Registry

| Package | Purpose | Owner | Status |
|---------|---------|-------|--------|
| `@ghatana/design-system` | UI component primitives (atoms, molecules, organisms) | Platform | ✅ Active |
| `@ghatana/platform-utils` | Shared utilities (`cn()`, formatters, etc.) | Platform | ✅ Active |
| `@ghatana/canvas` | Canvas/visualization components — canonical entry point | Platform | ✅ Active |
| `@ghatana/code-editor` | Monaco editor + AST/LSP/debugging/refactoring | Platform | ✅ Active |
| `@ghatana/config` | Environment validation, feature flags, config schemas | Platform | ✅ Active |
| `@ghatana/state` | Shared Jotai atoms (auth, notification, tenant) | Platform | ✅ Active |
| `@ghatana/forms` | Form primitives, field hooks, Zod validation | Platform | ✅ Active |
| `@ghatana/data-grid` | Sortable/filterable/paginated data grid | Platform | ✅ Active |
| `@ghatana/wizard` | Multi-step wizard navigation | Platform | ✅ Active |
| `@ghatana/charts` | Chart components | Platform | ✅ Active |
| `@ghatana/tokens` | Design tokens | Platform | ✅ Active |
| `@ghatana/theme` | Theme and styling system (MUI is optional peer dep via `./mui` subpath) | Platform | ✅ Active |
| `@ghatana/i18n` | Internationalization | Platform | ✅ Active |
| `@ghatana/realtime` | Real-time transport (EventEmitter/EventSubscription; event types from `@ghatana/events`) | Platform | ✅ Active |
| `@ghatana/events` | Canonical `PlatformEvent` type and event-related schemas | Platform | ✅ Active |
| `@ghatana/api` | API client utilities | Platform | ✅ Active |
| `@ghatana/sso-client` | SSO authentication client (Fastify plugin via `./security/fastify` subpath only) | Platform | ✅ Active |
| `@ghatana/domain-components` | Canonical domain-specific components (privacy, security, voice, nlp, selection) | Platform | ✅ Active |
| `@ghatana/accessibility` | Canonical accessibility audit + audit log components and hooks | Platform | ✅ Active |
| `@ghatana/product-shell` | Shared product shell layout primitives — sidebar, header, nav, notifications, mode selector, boundary surfaces | Platform | ✅ Active |
| `@ghatana/kernel-product-contracts` | Shared lifecycle, surface, artifact, environment, and deployment contracts for Kernel product orchestration | Platform | ✅ Active |
| `@ghatana/kernel-lifecycle` | Kernel lifecycle planning and execution orchestration | Platform | ✅ Active |
| `@ghatana/kernel-toolchains` | Toolchain adapters used by Kernel lifecycle phases | Platform | ✅ Active |
| `@ghatana/kernel-artifacts` | Artifact validation, manifest generation, and fingerprinting for lifecycle outputs | Platform | ✅ Active |
| `@ghatana/kernel-deployment` | Deployment planning and adapter orchestration for lifecycle deploy/verify phases | Platform | ✅ Active |

### Deprecated Packages — Fix Forward

These packages are thin re-export facades. Migrate consumers directly to the canonical package:

| Deprecated Package | Canonical Replacement | Migration |
|--------------------|-----------------------|-----------|
| `@ghatana/accessibility-audit` | `@ghatana/accessibility` | Replace import |
| `@ghatana/audit-components` | `@ghatana/accessibility` | Replace import |
| `@ghatana/canvas-core` | `@ghatana/canvas` or `@ghatana/canvas/core` | Replace import |
| `@ghatana/canvas-react` | `@ghatana/canvas` or `@ghatana/canvas/react` | Replace import |
| `@ghatana/canvas-plugins` | `@ghatana/canvas` or `@ghatana/canvas/plugins` | Replace import |
| `@ghatana/canvas-tools` | `@ghatana/canvas` or `@ghatana/canvas/tools` | Replace import |
| `@ghatana/canvas-chrome` | `@ghatana/canvas` or `@ghatana/canvas/chrome` | Replace import |

## Library Ownership

- **Platform libraries** (`platform/typescript/*`): Owned by platform team
- **Product libraries** (`products/*/libs/*`): Owned by respective product team
- **Cross-cutting libraries**: Owned by platform team with product input

## Naming Conventions

- All platform packages use the `@ghatana/` scope with kebab-case names
- Product packages use the product's own scope (e.g., `@yappc/`, `@dcmaar/`)
- No deprecated aliases — fix forward

## Build Standards

All platform TypeScript libraries must:

- Extend `tsconfig.base.json` (at repo root or `platform/typescript/tsconfig.base.json`)
- Use `tsc` as the build tool (not `tsup`, `esbuild`, or similar for library output)
- Include `"typecheck": "tsc --noEmit"` script
- Keep `src/` as rootDir, `dist/` as outDir
- Export a barrel `src/index.ts`
- Use ESM-only exports (no dual CJS+ESM `.mjs`/`.js` split) — `type: "module"` in package.json
- Keep `sideEffects: false` unless the package genuinely has side effects

### Server-Side Concerns

Libraries that include both client-safe and server-side code must:

- Separate server-only code behind a subpath export (e.g., `./security/fastify`)
- Not import server frameworks (`fastify`, `express`, `fastify-plugin`) in the main barrel
- Keep server deps in `optionalDependencies` or `devDependencies`, not `dependencies`

### YAPPC Product Libraries

Product libraries in `products/yappc/frontend/libs/` are held to the same build standard:

- All 4 core libs (`@yappc/ai`, `@yappc/core`, `@yappc/state`, `@yappc/ui`) now use `tsc` builds
- Extend `products/yappc/frontend/tsconfig.base.json` with `noEmit: false`
- ESM-only exports (`.js` not `.mjs`)
- CSS builds (`build:styles`) remain as Tailwind CLI steps separate from TypeScript compilation

## Library Deprecation

When deprecating a library:

1. **Announce**: Communicate deprecation to all consumers
2. **Migration path**: Provide clear migration instructions
3. **No aliases**: Do not create compatibility shims — fix forward
4. **Removal**: Delete the package after consumers have migrated
5. **Update tsconfig/vite aliases**: Remove all path aliases pointing to the deleted package

## Architecture Enforcement

The `platform/typescript/` directory includes `.dependency-cruiser.cjs` which enforces:

- No circular dependencies between platform packages
- No platform packages importing from `products/`
- `@ghatana/sso-client` main barrel cannot import `fastify` or `fastify-plugin`
- Deprecated canvas sub-libs cannot cross-import each other

**Run locally:**

```sh
# From platform/typescript/:
pnpm depcruise
# or from repo root:
npx depcruise --config platform/typescript/.dependency-cruiser.cjs --output-type text platform/typescript/*/src
```

**Strict TypeScript flags** (all enabled in `platform/typescript/tsconfig.base.json`):

| Flag | Value | Purpose |
|------|-------|---------|
| `strict` | `true` | Enables all strict type-checking options |
| `noImplicitOverride` | `true` | Require `override` keyword when overriding |
| `noUncheckedIndexedAccess` | `true` | Index signatures return `T \| undefined` |
| `noImplicitReturns` | `true` | All code paths must return a value |
| `noFallthroughCasesInSwitch` | `true` | No fall-through in switch statements |
| `exactOptionalPropertyTypes` | `true` | `?:` properties cannot be set to `undefined` |
