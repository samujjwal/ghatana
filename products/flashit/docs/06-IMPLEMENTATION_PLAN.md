# FlashIt Implementation Plan

Current onboarding scope covers domain-pack manifest registration, FlashIt boundary/compliance packs, CI script conformance, secure local runtime defaults, and product-owned docs aligned to the kernel taxonomy.

## Kernel Onboarding

- Keep FlashIt's domain-pack manifest, boundary policy store, plugin bindings, and compliance packs product-owned.
- Consume kernel policy validators and route metadata contracts instead of product-local approximations.
- Preserve the rule that FlashIt only imports stable kernel interfaces and ports.

## UI Conformance

- Web routes and navigation must continue to derive from the product route manifest and shared shell contracts.
- Mobile routes and settings shortcuts must derive from the mobile route manifest and shared token-backed theme values.
- Shared client conventions must keep FlashIt on `@flashit/shared`, `@ghatana/tokens`, and `@ghatana/product-shell` instead of duplicating platform primitives.

## Runtime Conformance

- Keep launcher Dockerfiles and local compose aligned to shared template sources.
- Reserve local ports through env-backed declarations checked at repo level.
- Treat FlashIt observability files as product-specific dashboards/alerts layered onto shared platform conventions rather than a separate platform stack.

## Remaining Delivery Work

- Deeper end-to-end workflow validation across gateway, agent, and clients
- Broader design-system migration for remaining product-local UI primitives
- Additional observability and entitlement API integration beyond the current static conformance checks
