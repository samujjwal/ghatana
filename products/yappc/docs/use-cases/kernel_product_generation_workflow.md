# YAPPC Kernel Product Generation Workflow

**Document type:** Use Case  
**Layer:** Product  
**Last updated:** 2026-05-30  
**Audience:** Product engineers, Kernel developers  

## 1. Overview

YAPPC accelerates Kernel-native product development by consuming generic Kernel product contracts. Product-specific routes, personas, capabilities, policies, screens, APIs, checks, and deployment facts stay in the product-owned contract files; YAPPC reads those contracts without embedding product domain knowledge.

## 2. Inputs

- A Kernel route contract JSON file with product id, lifecycle state, policy id, route metadata, and API bindings.
- A Kernel use-case baseline JSON file with IA routes, web routes, mobile screens, backend APIs, and implementation status.
- Optional Kernel plugin manifests for policy, i18n, accessibility, observability, privacy, and deployment requirements.

## 3. Workflow

1. Update the product-owned Kernel contract files in the product workspace.
2. Run product contract validation through the Kernel contract checks.
3. Run YAPPC generation in check mode first to detect drift.
4. Generate or update artifacts through generic Kernel templates only.
5. Review generated artifacts for contract fidelity, protected-region preservation, i18n keys, accessibility metadata, policy binding, and API/test parity.
6. Run focused product checks and the generic YAPPC product-contract roundtrip check.

```bash
pnpm check:yappc-product-contract-roundtrip \
  products/<product-id>/config/<product-id>-route-contract.json \
  products/<product-id>/config/<product-id>-usecase-baseline.json
```

## 4. Generated Artifact Classes

| Artifact | Source of truth | Required review |
| --- | --- | --- |
| Route bindings | Kernel route contract | Lifecycle state, policy id, i18n keys, accessibility metadata |
| Web pages | Kernel screen/route contracts | Product shell integration, protected regions, no raw user-facing text |
| API clients | Kernel API contracts | Typed request/response schemas, safe error handling, policy context |
| Backend route adapters | Kernel route/API contracts | Fail-closed policy checks, correlation id, safe errors |
| Mobile screens | Kernel route/screen contracts | session binding, offline/cache policy, accessibility metadata |
| Tests | Kernel validation contracts | Route/API/policy parity and meaningful user-workflow coverage |

## 5. Boundary Rules

- YAPPC must not contain product-specific generators, visualizers, templates, docs, checks, or agents.
- Product domain language belongs in product-owned Kernel contracts and product code.
- YAPPC must generate through Kernel schemas and plugin contracts, not product-local assumptions.
- Incomplete generated behavior must fail closed or remain behind product-owned feature flags.
- Regeneration must preserve protected regions and never overwrite hardened product code blindly.

## 6. Troubleshooting

| Symptom | Likely cause | Action |
| --- | --- | --- |
| Generated routes differ from product navigation | Route contract drift | Re-run Kernel route contract validation and compare lifecycle states |
| Missing API clients | Route lacks API contract metadata | Add or fix `apiEndpoint` / API contract references in the product contract |
| Missing policy checks | Route lacks policy metadata | Add a policy id and verify policy registry coverage |
| Raw strings in generated UI | Missing i18n keys | Add route/screen i18n metadata before generation |
| Generated output would overwrite manual code | Protected-region conflict | Use check mode and merge through protected-region semantics |

## 7. Regression Guards

- `pnpm check:yappc-product-contract-roundtrip`
- Kernel product contract validation
- Product route/API/policy parity checks
- Product i18n and accessibility checks
- Protected-region merge tests
