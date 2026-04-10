# Library Governance Process

Rules and processes for proposing, changing, deprecating, and owning libraries in the Ghatana monorepo.

**Principle**: Prevent fragmentation by requiring explicit approval for new libraries and significant changes.

---

## 1. New Library Proposal (RFC Process)

### When an RFC is required

- Creating a new `platform/typescript/*` package
- Extracting a new shared library from a product
- Promoting a product-local library to shared-services

### RFC Steps

1. **Open a GitHub Discussion** in the `Library Proposals` category
2. Use the [RFC template](#rfc-template) below
3. Tag `@platform-team` for review
4. Wait for 2 platform-team approvals and no blocking objections (7-day minimum)
5. Upon approval: create the library following platform conventions
6. Update `docs/PLATFORM_LIBRARY_OVERVIEW.md` and `docs/LIBRARY_OWNERSHIP.md`

### RFC Template

```markdown
# RFC: @ghatana/<library-name>

## Purpose
What problem does this library solve?

## Alternatives Considered
Why can't existing platform libraries cover this? What alternatives were evaluated?

## Proposed API Surface
Outline the key exports with TypeScript signatures.

## Affected Products
Which products will consume this library immediately?

## Impact on Existing Libraries
Does this change the responsibility of any existing library?

## Migration Plan
If extracting from an existing package, how will imports be updated?

## Quality Commitment
- Test coverage target: __%
- Documentation: README + JSDoc
- Owner: @github-username
```

---

## 2. Library Change Process

### Minor changes (no approval needed)

- Bug fixes that don't change the public API
- Documentation improvements
- Dependency patch version bumps
- Internal refactors with no observable behaviour change

### Major changes (require 1 platform-team approval)

- New exports added to a library
- Dependency minor/major version bumps
- Performance-sensitive changes
- New required peer dependencies

### Breaking changes (require 2 platform-team approvals)

- Removing or renaming public exports
- Changing function signatures in an incompatible way
- Changing default behaviour
- Removing a peer dependency

**For breaking changes**: include a migration guide in the PR, update `docs/MIGRATION_GUIDES.md`, and bump the major version.

---

## 3. Deprecation Process

### Criteria for deprecation

A library should be deprecated when:
- Its functionality has been absorbed by another library
- No products depend on it for 2+ months
- It violates architectural boundaries (e.g., a product lib that should be platform)

### Deprecation timeline

| Stage | Duration | Action |
|-------|----------|--------|
| Announce | Week 1 | Add `@deprecated` JSDoc, update README, open tracking issue |
| Warning phase | 4 weeks | ESLint warning on import, migration guide published |
| Removal | After 5 weeks | Delete library, add to `no-deleted-v41-packages` ESLint rule |

### Removal checklist

- [ ] All consumers migrated (verified by search + build)
- [ ] ESLint `no-deleted-v41-packages` rule updated with the removed package
- [ ] Package directory deleted
- [ ] `pnpm-workspace.yaml` entry removed
- [ ] `docs/LIBRARY_OWNERSHIP.md` entry archived

---

## 4. Library Ownership

See [LIBRARY_OWNERSHIP.md](./LIBRARY_OWNERSHIP.md) for the current registry.

### Owner responsibilities

- Respond to issues and PRs within 5 business days
- Keep dependencies up-to-date (monthly)
- Maintain ≥80% test coverage
- Document breaking changes in the CHANGELOG
- Review RFC proposals that affect the library

### Ownership transfer

1. Open a GitHub issue: `[Ownership Transfer] @ghatana/<library-name>`
2. Agree on a new owner
3. Update CODEOWNERS and `docs/LIBRARY_OWNERSHIP.md`
4. New owner acknowledges the responsibility in the issue

### Escalation

If an owner is unresponsive, escalate to platform-team by pinging `@ghatana/platform-team` in GitHub.

---

## 5. Quality Gates (enforced in CI)

Every platform library must pass:

| Gate | Tool | Threshold |
|------|------|-----------|
| Type check | `tsc --noEmit` | 0 errors |
| Linting | ESLint + `@ghatana/eslint-plugin` | 0 errors |
| Tests | Vitest | All pass |
| Coverage | Vitest coverage | ≥80% overall, ≥90% for critical paths |
| Build | `tsc` / `tsup` | Successful output in `dist/` |

---

## 6. Platform Library Standards

New libraries must follow:

1. **Package name**: `@ghatana/<kebab-case-name>` (see `docs/PLATFORM_LIBRARY_OVERVIEW.md`)
2. **TypeScript**: strict mode, no `any`, all exports typed
3. **Exports**: barrel `src/index.ts` + named subpath exports in `package.json`
4. **Tests**: Vitest, co-located `__tests__/` directories
5. **README**: purpose, installation, API table, usage examples
6. **No product-specific logic**: platform libraries must stay product-agnostic
7. **No circular dependencies**: verified by `@ghatana/eslint-plugin` boundary rules
