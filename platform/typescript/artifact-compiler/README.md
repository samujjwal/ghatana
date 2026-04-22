# @ghatana/artifact-compiler

Bidirectional artifact-to-model pipeline for Ghatana. Decompiles arbitrary codebases into governed product models and enables round-trip code generation.

## Architecture

The artifact compiler follows the 5-phase pipeline from the Artifact Decompilation Report:

```
existing artifacts → extractors → ArtifactGraph
ArtifactGraph → semantic synthesis → SemanticProductModel
SemanticProductModel → builder/design-system/runtime projections
projections → generated code/artifacts
regeneration and re-import both attach provenance, confidence, and residual loss markers
```

## Modules

| Module | Purpose |
|--------|---------|
| `inventory` | Repository-wide file scanning, classification, and eligibility |
| `graph` | ArtifactGraph data model - the "reverse compiler IR" |
| `model` | SemanticProductModel - normalized product-facing models |
| `provenance` | Confidence scoring, provenance tracking, security/privacy flags |
| `residual` | Residual islands for unmodelable artifacts |
| `extractors` | Language-specific extractors (TypeScript, Storybook, Prisma) |
| `synthesis` | Graph-to-model synthesis pipeline |
| `merge` | Round-trip merge engine for semantic diff |

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
import { scanRepository } from '@ghatana/artifact-compiler/inventory';
import { extractComponentsFromSource } from '@ghatana/artifact-compiler/extractors';

// Scan repository
const inventory = await scanRepository({ rootPath: '/path/to/repo' });

// Extract components from a TSX file
const components = extractComponentsFromSource(sourceCode, 'Button.tsx');
```

## Key Design Decisions

1. **No external service dependencies** - All extraction operates on local source files only
2. **TypeScript Compiler API for TS/TSX** - Not regex, not Babel - real AST with type awareness
3. **Confidence-graded extraction** - Every model element carries confidence and provenance
4. **Residual islands** - Unmodelable code is preserved, not silently dropped
5. **Self-contained open source** - Only MIT/Apache/BSD dependencies, no proprietary tools

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
