# @ghatana/product-conformance

Kernel-owned product conformance validation tools. Provides schema-based, AST-based, and runtime conformance validation for products, replacing token-scanning conformance gates with structured validation.

## Installation

```bash
pnpm add @ghatana/product-conformance
```

## Usage

### Schema Validation

Validate product manifests and observability flows using Zod schemas:

```typescript
import { validateProductManifest, validateObservabilityFlow } from '@ghatana/product-conformance/schema.js';

const manifest = validateProductManifest(manifestData);
const flow = validateObservabilityFlow(flowData);
```

### AST Validation

Validate code structure and patterns:

```typescript
import { validateRequiredImports, validateRequiredCalls } from '@ghatana/product-conformance/ast.js';

const result = validateRequiredImports('./src/server.ts', ['@ghatana/logger', '@ghatana/observability']);
if (!result.valid) {
  console.error(result.errors);
}
```

### Runtime Validation

Perform runtime conformance checks:

```typescript
import { validateProductConformance } from '@ghatana/product-conformance/runtime.js';

const result = validateProductConformance({
  repoRoot: process.cwd(),
  productManifestPath: 'domain-pack-manifest.json',
});

if (!result.valid) {
  console.error(result.errors);
  process.exit(1);
}
```

## Modules

- `schema` - Zod schemas for product manifests and observability flows
- `ast` - AST-based validation for code structure
- `runtime` - Runtime conformance checks

## Replacing Token-Scanning Gates

This package replaces token-scanning conformance gates (e.g., checking if strings exist in files) with structured validation:

1. **Schema validation**: Ensures manifest files match expected structure
2. **AST validation**: Validates code structure and imports
3. **Runtime validation**: Performs checks during execution

This provides better error messages, type safety, and maintainability compared to simple string scanning.
