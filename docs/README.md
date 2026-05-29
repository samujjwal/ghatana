# Ghatana Monorepo Documentation

> Owner: Platform Team

## Canonical Core Documents

| Document | Purpose |
|----------|---------|
| [MONOREPO_VISION.md](./MONOREPO_VISION.md) | Monorepo-level vision and principles |
| [kernel/MONOREPO_ARCHITECTURE.md](./kernel/MONOREPO_ARCHITECTURE.md) | Canonical architecture and boundaries |
| [GOVERNANCE.md](./GOVERNANCE.md) | Naming, policy, and authority model |
| [TESTING.md](./TESTING.md) | Test standards and quality gates |
| [kernel/BUILD.md](./kernel/BUILD.md) | Build conventions and toolchain practices |
| [ONBOARDING.md](./ONBOARDING.md) | Developer onboarding and environment setup |

## Canonical Supporting Areas

| Area | Location |
|------|----------|
| ADRs | [adr/](./adr/) |
| Operations and runbooks | [runbooks/](./runbooks/) |
| Process docs | [process/](./process/) |
| Platform library index | [platform-libraries/LIBRARY_INDEX.md](./platform-libraries/LIBRARY_INDEX.md) |

## Product Documentation Roots

| Product | Root |
|---------|------|
| Data Cloud | `products/data-cloud/docs/README.md` |
| Digital Marketing | `products/digital-marketing/docs/` |
| Flashit | `products/flashit/docs/` |
| PHR | `products/phr/docs/` |
| YAPPC | `products/yappc/docs/README.md` |

## Surface Governance

- Documentation registry: `config/documentation-surface-registry.json`
- Script registry: `config/script-surface-registry.json`
- Validation commands: `pnpm check:docs` and `pnpm check:scripts`

## Policy

Non-authoritative historical implementation notes, session logs, completion reports, and ad hoc verification trackers are not canonical repo truth surfaces and must not be maintained as active core documentation.

