# ARCHIVED: app-platform

**Archived:** 2026-03-22  
**Reason:** Ghost product — documentation-only node with zero code implementation  
**Action taken:** Documentation moved to `docs/archive/app-platform/` per 2026-03-22 boundary audit (P0 cleanup)

## What this was

`products/app-platform` was the architectural home for the **Domain Pack system** concept — a marketplace/plugin framework for deploying domain-specific behaviour on top of the Ghatana kernel. It accumulated 271 items of docs including:
- PLUGIN_SANDBOX_SPECIFICATION.md
- POLYGLOT_RULE_EXECUTION_ENGINE.md
- DOMAIN_PACK_DEVELOPMENT_GUIDE.md
- C4 diagrams, ADRs, epics, and regulatory docs

## What happened

The implementation concepts were absorbed into:
- `products/finance` — Reference implementation of domain packs
- `platform/java/kernel` and `platform/java/kernel-capabilities` — Core abstractions
- Various ADRs in `docs/adr/`

## Where to find the content

All documentation has been preserved in `docs/archive/app-platform/`.

If this product is ever revived (to build the domain-pack marketplace), start from `docs/archive/app-platform/` and create a new product entry through the MODULE_ADMISSION_CHECKLIST process.
