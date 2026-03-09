# TutorPutor Domain Content Loader

Load domain content (physics, chemistry, etc.) from JSON files into the TutorPutor database.

## ✅ Implementation Status

| Feature                                         | Status                    |
| ----------------------------------------------- | ------------------------- |
| Curriculum Types (`contracts/v1/curriculum/`)   | ✅ Complete               |
| Prisma Schema (DomainConcept, Curriculum, etc.) | ✅ Complete               |
| Physics Parser                                  | ✅ Complete (35 concepts) |
| Chemistry Parser                                | ✅ Complete (29 concepts) |
| Domain Loader ETL                               | ✅ Complete               |
| Module Generator                                | ✅ Complete               |
| Learning Path Generator                         | ✅ Complete               |
| **Content Block Generator**                     | ✅ Complete (NEW)         |
| **Simulation Manifest Generator**               | ✅ Complete (NEW)         |
| CLI (`validate`, `load`, `stats`)               | ✅ Complete               |
| TypeScript Compilation                          | ✅ 0 errors               |
| Prisma Migration                                | ✅ Applied                |
| **Unit Tests**                                  | ✅ 69 tests passing       |

## ⚠️ Known Issue: Node.js 24 + Prisma 7.0.1 Compatibility

There is a known issue with `PrismaClient` instantiation on Node.js 24.x with Prisma 7.0.1:

```
TypeError: Cannot read properties of undefined (reading '__internal')
```

**Workarounds:**

1. Use Node.js 20.x or 18.x (LTS)
2. Wait for Prisma 7.1.x which should fix this issue
3. Use the `test-run.cjs` script for validation (doesn't need Prisma)

## Quick Start

### Validate Content (No Prisma Required)

```bash
# Simple validation with CommonJS
node src/test-run.cjs
```

### Full CLI (Requires Node.js 20.x)

```bash
# Validate domain content
npx tsx src/cli.ts validate --content-dir ../../content/domains --verbose

# Dry-run load (no database changes)
npx tsx src/cli.ts load --tenant default --dry-run

# Actual load
npx tsx src/cli.ts load --tenant default
```

## Overview

This service parses domain content JSON files and:

1. Creates `DomainConcept` records in the database
2. Links prerequisites between concepts
3. Generates `Module` records from concepts
4. Creates learning objectives, tags, and content blocks
5. Generates `LearningPath` records based on prerequisite ordering
6. **Generates content blocks** (intro, objectives, simulation, AI tutor, exercises)
7. **Generates simulation manifests** with domain-specific templates

## Usage

### CLI Commands

```bash
# Load all domain content
pnpm tutorputor:load-domains --tenant=default

# Load specific domain
pnpm tutorputor:load-domains --tenant=default --domain=physics

# Load with content block generation
pnpm tutorputor:load-domains --tenant=default --with-content-blocks

# Load with simulation manifest generation
pnpm tutorputor:load-domains --tenant=default --with-manifests

# Full load with all generators
pnpm tutorputor:load-domains --tenant=default --with-content-blocks --with-manifests

# Validate content without loading
pnpm tutorputor:load-domains validate

# Dry run (validate and show what would be loaded)
pnpm tutorputor:load-domains load --dry-run

# Show content statistics
pnpm tutorputor:load-domains stats
```

### Programmatic Usage

```typescript
import { PrismaClient } from "@prisma/client";
import {
  loadDomainContent,
  generateContentBlocks,
  generateManifestFromConcept,
} from "@tutorputor/domain-loader";

const prisma = new PrismaClient();

// Load concepts
const result = await loadDomainContent(prisma, {
  tenantId: "my-tenant",
  domain: "physics",
  contentDir: "./content/domains",
  verbose: true,
});

console.log(`Loaded ${result.stats.conceptsLoaded} concepts`);

// Generate content blocks for a module
await generateContentBlocks(prisma, moduleId, concept, {
  includeSimulation: true,
  includeAiTutor: true,
  includeExercise: true,
});

// Generate simulation manifest
const manifest = generateManifestFromConcept(concept, {
  tenantId: "my-tenant",
  authorId: "user-123",
});
```

## Content Structure

### physics.json

Array of level objects:

```json
[
  {
    "domain": "Physics",
    "level": "Foundational",
    "concepts": [
      {
        "id": "phy_F_1",
        "name": "Scalars and Vectors",
        "description": "...",
        "prerequisites": [],
        "keywords": ["scalar", "vector"],
        "simulation_metadata": { ... },
        "learning_object_metadata": { ... },
        "pedagogical_metadata": { ... }
      }
    ]
  }
]
```

### chemistry.json

Object with levels:

```json
{
  "domain": "Chemistry",
  "levels": {
    "Foundational": {
      "concepts": [...]
    },
    "Intermediate": {
      "concepts": [...]
    }
  }
}
```

## Generated Database Records

### DomainConcept

Stores the raw concept with all metadata.

### Module

One module per concept with:

- Title, description from concept
- Difficulty mapped from level (FOUNDATIONAL → INTRO, etc.)
- Domain mapped (PHYSICS/CHEMISTRY → SCIENCE)

### ModuleContentBlock

Each module gets up to 6 content blocks:

1. **Introduction** (rich_text) - Concept name, description, badges
2. **Learning Objectives** (rich_text) - Bloom's taxonomy aligned
3. **Simulation** (simulation) - Interactive visualization placeholder
4. **Key Concepts** (rich_text) - Keywords and competencies
5. **AI Tutor** (ai_tutor_prompt) - Contextual AI assistant
6. **Exercises** (exercise) - Practice questions with multiple formats

### SimulationManifest

Auto-generated manifests based on domain and simulation type:

**Physics Templates:**

- `projectile_motion` - For kinematics, motion, projectile concepts
- `harmonic_oscillator` - For spring, oscillation, harmonic concepts
- `inclined_plane` - For force, friction, incline concepts
- `wave_physics` - For wave, optic concepts
- `electromagnetic` - For electric, magnetic, circuit concepts
- `thermodynamics` - For heat, energy, thermo concepts
- `physics_generic` - Fallback for other physics concepts

**Chemistry Templates:**

- `chemical_reaction` - For reactions, substitution concepts
- `chemical_equilibrium` - For equilibrium concepts
- `atomic_orbital` - For orbital, electron, quantum concepts
- `molecular_structure` - For bond, structure concepts
- `chemistry_generic` - Fallback for other chemistry concepts

### LearningPath

Auto-generated paths:

- Per-level paths (e.g., "Physics Foundational Path")
- Cross-level paths (e.g., "Physics Complete Journey")

## Architecture

```
┌─────────────────────┐
│   JSON Files        │
│ physics.json        │
│ chemistry.json      │
└──────────┬──────────┘
           │
           ▼
┌──────────────────────┐
│   Parsers            │
│ physics-parser.ts    │
│ chemistry-parser.ts  │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│   Domain Loader      │
│ Validates & persists │
└──────────┬───────────┘
           │
    ┌──────┼──────────┬─────────────┐
    ▼      ▼          ▼             ▼
┌────────┐ ┌─────────┐ ┌───────────┐ ┌────────────┐
│ DB:    │ │ Module  │ │ Content   │ │ Manifest   │
│Concept │ │ Gen     │ │ Block Gen │ │ Generator  │
│Prereq  │ │         │ │           │ │            │
└────────┘ └─────────┘ └───────────┘ └────────────┘
                │           │             │
                ▼           ▼             ▼
           ┌─────────┐ ┌─────────┐  ┌──────────┐
           │Learning │ │ Module  │  │Simulation│
           │ Path    │ │ Content │  │ Manifest │
           │ Gen     │ │ Blocks  │  │ USP      │
           └─────────┘ └─────────┘  └──────────┘
```

## Generators

### Content Block Generator

Generates comprehensive content blocks for modules:

```typescript
import { generateContentBlocks } from "@tutorputor/domain-loader";

const result = await generateContentBlocks(prisma, moduleId, concept, {
  includeSimulation: true, // Include simulation placeholder
  includeAiTutor: true, // Include AI tutor prompt
  includeExercise: true, // Include practice exercises
  includeCompetencies: true, // Include competencies section
  verbose: false,
});

console.log(`Created ${result.blocksCreated} blocks`);
console.log(`Block types: ${result.blockTypes.join(", ")}`);
```

### Manifest Generator

Generates simulation manifests from domain concepts:

```typescript
import {
  generateManifestFromConcept,
  generateManifestsFromConcepts,
} from "@tutorputor/domain-loader";

// Single concept
const result = generateManifestFromConcept(concept, {
  tenantId: "my-tenant",
  authorId: "user-123",
  version: "1.0.0",
});

console.log(`Template: ${result.templateType}`);
console.log(`Manifest ID: ${result.manifest.id}`);

// Multiple concepts
const results = generateManifestsFromConcepts(concepts, options);
console.log(`Generated ${results.size} manifests`);
```

## Testing

```bash
# Run all tests
pnpm test

# Run tests in watch mode
pnpm test:watch

# Typecheck
pnpm typecheck
```

**Test Coverage:**

- `domain-loader.spec.ts` - Parser and mapper tests (16 tests)
- `content-block-generator.spec.ts` - Content block generation (12 tests)
- `manifest-generator.spec.ts` - Manifest generation (28 tests)
- `integration.spec.ts` - Full pipeline tests (13 tests)

## License

Private - TutorPutor
