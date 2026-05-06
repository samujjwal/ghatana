# Kernel / Product Responsibility Matrix

> **Canonical owner map** for Kernel, shared libraries, design system, infrastructure, and product modules.
> **Last Updated:** 2026-05-04

## Ownership Rules

| Capability Class | Owner | Notes |
|---|---|---|
| Lifecycle, boundary policy validation, bridge contracts, audit/tenant primitives | Kernel | Must stay product-agnostic. |
| Reusable domain-neutral helpers and shared SDKs | Shared libraries | No product-specific semantics. |
| App shell, page layout, tokens, navigation primitives | Design system | Products provide metadata, branding, and slots only. |
| Docker/compose templates, observability stack plumbing, CI matrices, workspace/build conventions | Infrastructure/tooling | Products override ports, domain services, and dashboards only. |
| Consent, trading, campaigns, moments, compliance packs, route manifests, domain workflows | Product-specific modules | Product-owned business logic and regulated semantics. |

## Product Capability Ownership

| Product | Kernel-owned capabilities consumed | Shared/design-system capabilities consumed | Product-owned capabilities |
|---|---|---|---|
| `digital-marketing` | Boundary policy resolver, bridge contracts, audit, approval, policy validator | Product app shell, tokens, route entitlement contract, frontend script contract | Campaign, audience, contact, connector, budget, content, consent-aware marketing workflows, DM policy/compliance packs |
| `phr` | Boundary policy resolver, consent/audit ports, bridge contracts, tenant context, policy validator | Product app shell, tokens, route entitlement contract, frontend script contract | Subject record access, interop consent, FHIR/HL7 workflows, healthcare policy/compliance packs |
| `finance` | Boundary policy resolver, audit/approval ports, bridge contracts, tenant context, policy validator | Product app shell when UI exists, tokens, route entitlement contract | Transaction mutation approval, market-data/risk flows, finance policy/compliance packs, finance domain services |
| `flashit` | Boundary policy resolver, audit/approval ports, observability conventions, policy validator | Product app shell, tokens, route entitlement contract, frontend script contract | Moments, reflections, exports, FlashIt policy/compliance packs, FlashIt client/backend product workflows |

## Anti-Duplication Contract

| Capability | Canonical Owner | Products must not duplicate |
|---|---|---|
| `boundary-policy-validation` | Kernel | Product-local regex/source scanning or class-existence-only checks |
| `app-shell/layout` | Design system | Product-local shell implementations except thin wrappers; audited web products must compose `@ghatana/product-shell` |
| `component-library ownership` | Design system | Direct product imports of overlapping UI systems such as MUI, Chakra, Mantine, Ant Design, or Semantic UI unless explicitly approved |
| `router/version strategy` | Kernel frontend platform | Mixed router families across products or product web packages depending directly on `react-router` |
| `compose/docker base image, healthcheck, secret rules, observability wiring` | Infrastructure/tooling | Product-local copies of the base runtime setup |
| `trace/log/metric/audit conventions` | Kernel observability | Product-specific logging schemas for shared flows |

## Product Declarations Required

Every product manifest or domain-pack manifest must declare:

- `kernelCapabilitiesConsumed`
- `policyActions`
- `policyResources`
- `pluginsConsumed`
- `bridgesConsumed`
- `domainPacksProvided`
- `uiSurfaces`
- `runtimeServices`
- `dataSensitivity`
