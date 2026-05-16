# @yappc/artifact-compiler

Forward-only artifact-to-model pipeline for Ghatana. Decompiles codebases into governed product models with provenance tracking and confidence scoring.

## Architecture

The artifact compiler implements a forward-only decompilation pipeline:

```
existing artifacts → extractors → ArtifactGraph
ArtifactGraph → semantic synthesis → SemanticProductModel
```

**Current Status**: The forward decompilation pipeline (scan → extract → graph → model) is implemented. The compile-back round-trip (model → generated code) with patch lifecycle, validation, and rollback is implemented.

## Modules

| Module | Purpose | Status |
|--------|---------|--------|
| `inventory` | Repository-wide file scanning, classification, and eligibility | ✅ Implemented |
| `graph` | ArtifactGraph data model - the "reverse compiler IR" | ✅ Implemented |
| `model` | SemanticProductModel - normalized product-facing models | ✅ Implemented |
| `provenance` | Confidence scoring, provenance tracking, security/privacy flags | ✅ Implemented |
| `residual` | Residual islands for unmodelable artifacts | ✅ Implemented |
| `extractors` | Language-specific extractors (TypeScript, Storybook, Prisma) | ✅ Implemented |
| `synthesis` | Graph-to-model synthesis pipeline | ✅ Implemented |
| `merge` | Round-trip merge engine for semantic diff | ✅ Implemented |
| `source-providers` | GitHub, GitLab, ZIP, local folder source acquisition | ✅ Implemented |
| `compile-back` | Model-to-code generation with patch lifecycle, validation, rollback | ✅ Implemented |
| `capabilities` | Runtime capability discovery registry for providers/extractors/emitters/validators | ✅ Implemented |

## Extractors

### Implemented

| Extractor | Languages | Phase | Status |
|-----------|-----------|-------|--------|
| `typescript-component` | TSX, JSX | 1 | ✅ AST-based component extraction |
| `storybook-csf` | TSX, JSX | 1 | ✅ CSF parsing with variant extraction |
| `typescript-page` | TSX, JSX | 1 | ✅ Next.js/React Router page extraction |
| `state-store` | TS, JS | 1 | ✅ Redux, Zustand, Jotai, Context detection |
| `prisma-schema` | Prisma | 1 | ✅ Schema model, relation, index extraction |
| `inventory-scanner` | All | 0 | ✅ Repository file classification |

### Pending

| Extractor | Phase | Notes |
|-----------|-------|-------|
| Tree-sitter structural index | 1 | Cross-language parsing substrate |
| PostCSS style extraction | 3 | Token candidate mining |
| OpenRewrite Java LST | 4 | Lossless semantic tree extraction |
| SQLGlot SQL canonicalization | 4 | Multi-dialect SQL diff |
| CI/CD workflow extraction | 5 | GitHub Actions, GitLab CI |

## Usage

```typescript
import { scanRepository } from '@yappc/artifact-compiler/inventory';
import { SynthesisPipeline, defaultExtractorRegistry } from '@yappc/artifact-compiler/synthesis';

// Scan repository
const inventory = await scanRepository({ rootPath: '/path/to/repo' });

// Run full synthesis pipeline
const pipeline = new SynthesisPipeline({
  extractors: defaultExtractorRegistry.getAll(),
  residualConfidenceThreshold: 0.5,
});

const result = await pipeline.runFromLocalPath('/path/to/repo');
console.log(`Extracted ${result.stats.extractedNodes} nodes, ${result.stats.modelElementsGenerated} model elements`);
```

## Key Design Decisions

1. **No external service dependencies** - All extraction operates on local source files only
2. **TypeScript Compiler API for TS/TSX** - Not regex, not Babel - real AST with type awareness
3. **Confidence-graded extraction** - Every model element carries confidence and provenance
4. **Residual islands** - Unmodelable code is preserved with risk assessment, not silently dropped
5. **Self-contained open source** - Only MIT/Apache/BSD dependencies, no proprietary tools
6. **Full bidirectional pipeline** - Both decompilation (artifacts → model) and compile-back (model → code) are implemented with patch lifecycle, validation, and rollback support

## Standards Compliance

- Design Tokens: Aligned with DTCG spec 2025.10 (modern color spaces, cross-platform)
- OpenAPI: API model supports 3.2.0 features (hierarchical tags, streaming media types)
- Zod: All schemas validated at boundaries with strict typing

## Testing

```bash
pnpm test
```

## Dependencies

- `typescript` - Native compiler API for TS/TSX extraction
- `zod` - Schema validation
- `@ghatana/ui-builder` - BuilderDocument integration
- `@ghatana/ds-schema` - Component contract schemas
- `@ghatana/ds-registry` - Registry integration
