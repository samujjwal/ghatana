# Kernel Product Contracts

`@ghatana/kernel-product-contracts` owns the public TypeScript contracts for Kernel ProductUnit lifecycle execution and governance.

The package exports ProductUnit models, provider interfaces, lifecycle event contracts, health snapshots, plugin contracts, artifacts, deployments, environments, gates, and surface contracts. It must remain product-neutral: product-specific behavior belongs in product registry data, providers, plugin bindings, or product lifecycle configuration.

Validation:

```bash
pnpm --dir platform/typescript/kernel-product-contracts typecheck
pnpm --dir platform/typescript/kernel-product-contracts test
pnpm --dir platform/typescript/kernel-product-contracts build
```
