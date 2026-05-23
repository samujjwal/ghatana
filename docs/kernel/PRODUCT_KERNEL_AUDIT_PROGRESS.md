# Product/Kernel Audit Progress

> Generated from executable contract metadata. Do not hand-edit status counts.

## Snapshot

- Products in product shape: `audio-video`, `aura`, `data-cloud`, `dcmaar`, `digital-marketing`, `finance`, `flashit`, `phr`, `polyglot-fixture`, `python-fixture`, `rust-fixture`, `security-gateway`, `software-org`, `tutorputor`, `typescript-fixture`, `virtual-org`, `yappc`
- Kernel capabilities in registry: 7
- Platform plugins in registry: 7
- Domain packs in registry: 9

## Executable Checks

| Check | Status | Command | Proof |
| --- | --- | --- | --- |
| `product-manifest-contracts` | covered | `pnpm check:product-manifest-contracts` | Product manifests parse through schema-backed validation, registry references, policy vocabulary, plugin ownership, dependency scope, and product-shape surface alignment. |
| `route-entitlement-contracts` | covered | `pnpm check:route-entitlement-contracts` | PHR, DMOS, and FlashIt route entitlement APIs have behavioral coverage and FlashIt frontend/backend route parity is enforced. |
| `shared-product-shells` | covered | `pnpm check:shared-product-shells` | Audited product shells use shared shell composition, stable config helpers, product-owned routing content, and product-neutral platform docs. |
| `data-access-contract` | covered | `pnpm check:data-access-contract` | Data-access metadata carries tenant/principal/correlation/idempotency/audit fields and FlashIt tenant resolution fails closed. |
| `observability-conformance` | covered | `pnpm check:observability-conformance` | Product observability flow evidence remains registered for trace, metric, log, redaction, and audit fields. |

## Evidence Files

### product-manifest-contracts

- `scripts/check-product-manifest-contracts.mjs`
- `platform/typescript/product-manifest-contracts/index.mjs`
- `config/kernel-product-capability-registry.json`

### route-entitlement-contracts

- `scripts/check-route-entitlement-contracts.mjs`
- `products/phr/src/test/java/com/ghatana/phr/api/PhrHttpServerTest.java`
- `products/digital-marketing/dm-api/src/test/java/com/ghatana/digitalmarketing/api/DmosRouteEntitlementServletTest.java`
- `products/flashit/backend/gateway/src/routes/__tests__/entitlements.test.ts`

### shared-product-shells

- `scripts/check-shared-product-shells.mjs`
- `platform/typescript/product-shell/src/access.ts`
- `platform/typescript/product-shell/src/useProductEntitlements.ts`
- `platform/typescript/product-shell/src/components/ProductShell.tsx`

### data-access-contract

- `scripts/check-data-access-contract.mjs`
- `products/flashit/backend/gateway/src/lib/data-access-context.ts`
- `products/flashit/backend/gateway/src/lib/__tests__/data-access-context.test.ts`

### observability-conformance

- `scripts/check-observability-conformance.mjs`
- `config/observability/product-observability-flows.json`

## Policy

Compliance status is derived from contract scripts, registry metadata, product-shape declarations, and behavioral tests. Historical audit task prose belongs in issue trackers or changelogs, not this generated status file.
