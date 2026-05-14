# Kernel Providers

## Purpose

`@ghatana/kernel-providers` contains product-neutral provider implementations that adapt external or repository-backed sources into Kernel ProductUnit contracts.

## Current provider: GhatanaFileRegistryProvider

`GhatanaFileRegistryProvider` reads `config/canonical-product-registry.json`, validates registry entries, and maps them into `ProductUnit` objects from `@ghatana/kernel-product-contracts`.

## Current limitations

- The provider is file-backed only.
- Strict mode validates registry shape but does not execute lifecycle phases.
- External registry, source, artifact, and deployment providers are still target architecture.

## Provider boundary rules

- Providers must depend on public Kernel contracts.
- Providers must not import product implementation code.
- Providers must preserve source metadata instead of silently rewriting unknown registry values.
- Strict mode must fail closed for invalid executable lifecycle entries.

## How to add a provider

1. Add a provider implementation under `src/<provider-kind>`.
2. Implement the matching interface from `@ghatana/kernel-product-contracts`.
3. Export only the finished provider from `src/index.ts`.
4. Add focused tests for conversion, validation, and boundary behavior.

## Validation commands

```bash
pnpm --dir platform/typescript/kernel-providers typecheck
pnpm --dir platform/typescript/kernel-providers test
pnpm --dir platform/typescript/kernel-providers build
```

## No product imports rule

Provider code may read product metadata from registries or manifests, but it must not import from `products/*`.
