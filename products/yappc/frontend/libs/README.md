# YAPPC Frontend Libraries

`products/yappc/frontend/libs` contains YAPPC product-local TypeScript libraries. These are not platform packages; cross-product reusable code belongs under `platform/typescript/*` using the canonical `@ghatana/*` package registry.

## Ownership Rules

- Keep libraries product-local unless at least one other product has a real consumer.
- Prefer existing platform packages for design-system, canvas, i18n, API, theme, tokens, forms, and utilities.
- Avoid duplicate local primitives when `@ghatana/design-system`, `@ghatana/canvas`, `@ghatana/i18n`, or `@ghatana/platform-utils` already owns the capability.
- Do not use the historical `app-creator` name in new docs, package names, or import examples.

## Current Library Inventory

| Library directory | Package identity | Primary owner/purpose |
| --- | --- | --- |
| `a11y` | product-local | YAPPC accessibility helpers and test support. |
| `aep-config` | product-local | AEP configuration adapters for YAPPC frontend flows. |
| `collab` | product-local | Collaboration behavior used by YAPPC surfaces. |
| `config-compiler` | `yappc-config-compiler` | YAPPC config compilation. |
| `config-schema` | `yappc-config-schema` | YAPPC config schemas. |
| `data-cloud-config` | product-local | Data Cloud configuration helpers. |
| `ide` | `@ghatana/yappc-ide` | YAPPC IDE integration surface. |
| `mobile` | product-local | Mobile-oriented shared code for YAPPC experiments. |
| `mocks` | product-local | Test mocks and fixtures. |
| `shortcuts` | product-local | Keyboard shortcut helpers. |
| `yappc-ai` | `yappc-ai` | YAPPC AI hooks and frontend adapters. |
| `yappc-artifact-compiler` | `yappc-artifact-compiler` | Artifact compiler/decompiler frontend runtime. |
| `yappc-auth` | `yappc-auth` | YAPPC auth client helpers. |
| `yappc-chat` | `yappc-chat` | Chat UI/domain helpers. |
| `yappc-core` | `yappc-core` | Shared YAPPC frontend domain utilities. |
| `yappc-devsecops` | product-local | DevSecOps frontend helpers. |
| `yappc-initialization-ui` | `yappc-initialization-ui` | Initialization and onboarding UI. |
| `yappc-product-theme` | `yappc-product-theme` | Product-specific theme wiring. |
| `yappc-state` | `yappc-state` | Product-local frontend state. |
| `yappc-ui` | `yappc-ui` | YAPPC-specific UI components that are not platform design-system primitives. |

## Validation

Use the web app design-system inventory guard when changing shared UI:

```bash
pnpm -C products/yappc/frontend/web inventory:components:check
```