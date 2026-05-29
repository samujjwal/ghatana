# Ghatana Monorepo

Ghatana is a multi-product monorepo with shared platform contracts and product-owned implementations.

## Start Here

- Monorepo docs index: `docs/README.md`
- Architecture: `docs/kernel/MONOREPO_ARCHITECTURE.md`
- Governance rules: `docs/GOVERNANCE.md`
- Build and testing standards: `docs/kernel/BUILD.md`, `docs/TESTING.md`
- Contributor guide: `CONTRIBUTING.md`

## Repository Shape

- `platform/contracts`: shared contracts and schemas
- `platform/java/*`: shared Java platform modules
- `platform/typescript/*`: shared TypeScript platform modules
- `products/*`: product-specific systems
- `shared-services/*`: shared service implementations
- `scripts/*`: CI, release, and quality automation

## Quick Commands

```bash
pnpm check:required
pnpm check:docs
pnpm check:scripts
pnpm check:phase0
```

## Notes

- Canonical documentation surfaces are registered in `config/documentation-surface-registry.json`.
- Canonical script surfaces are registered in `config/script-surface-registry.json`.
- Session reports, temporary implementation trackers, and one-off cleanup scripts are not canonical repo truth.
