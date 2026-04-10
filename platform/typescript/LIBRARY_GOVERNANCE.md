# Library Governance

> **Last Updated:** 2026-04-10

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

| Package | Purpose | Owner |
|---------|---------|-------|
| `@ghatana/design-system` | UI component primitives (atoms, molecules, organisms) | Platform |
| `@ghatana/platform-utils` | Shared utilities (`cn()`, formatters, etc.) | Platform |
| `@ghatana/canvas` | Canvas/visualization components (canonical coords only) | Platform |
| `@ghatana/code-editor` | Monaco editor + AST/LSP/debugging/refactoring | Platform |
| `@ghatana/config` | Environment validation, feature flags, config schemas | Platform |
| `@ghatana/state` | Shared Jotai atoms (auth, notification, tenant) | Platform |
| `@ghatana/forms` | Form primitives, field hooks, Zod validation | Platform |
| `@ghatana/data-grid` | Sortable/filterable/paginated data grid | Platform |
| `@ghatana/wizard` | Multi-step wizard navigation | Platform |
| `@ghatana/charts` | Chart components | Platform |
| `@ghatana/tokens` | Design tokens | Platform |
| `@ghatana/theme` | Theme and styling system | Platform |
| `@ghatana/i18n` | Internationalization | Platform |
| `@ghatana/realtime` | Real-time communication | Platform |
| `@ghatana/api` | API client utilities | Platform |
| `@ghatana/sso-client` | SSO authentication | Platform |
| `@ghatana/domain-components` | Domain-specific components (privacy, security, voice, nlp, selection) | Platform |
| `@ghatana/audit-components` | Accessibility audit + audit log components | Platform |

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

## Library Deprecation

When deprecating a library:

1. **Announce**: Communicate deprecation to all consumers
2. **Migration path**: Provide clear migration instructions
3. **No aliases**: Do not create compatibility shims — fix forward
4. **Removal**: Delete the package after consumers have migrated
5. **Update tsconfig/vite aliases**: Remove all path aliases pointing to the deleted package
