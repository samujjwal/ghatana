# AI Library Consolidation Plan

**Date:** 2026-01-31  
**Task:** 2.3 - Consolidate AI Libraries  
**Current Libraries:** @yappc/ai-core, @yappc/ai-ui, @yappc/ml  
**Target:** Single unified @yappc/ai library with clear module boundaries

---

## Executive Summary

**Problem:** Three separate AI-related libraries with unclear boundaries and potential overlap.

**Current Structure:**
- **@yappc/ai-core** (72 files) - AI services, agents, requirements, parsing, validation
- **@yappc/ai-ui** (18 files) - AI UI components for requirements and editor
- **@yappc/ml** (10 files) - ML features (tracking, recommendations, A/B testing)
- **Total:** 100 files across 3 packages

**Target Structure:**
- **@yappc/ai** (100 files) - Single unified library with organized modules:
  - `core/` - AI services, providers, agents (from ai-core)
  - `requirements/` - Requirements parsing, validation, agents (from ai-core)
  - `ui/` - UI components for AI features (from ai-ui)
  - `ml/` - Machine learning, tracking, recommendations (from ml)

**Benefits:**
- ✅ **Clearer boundaries:** Single entry point for all AI features
- ✅ **Easier imports:** `@yappc/ai/core`, `@yappc/ai/requirements`, `@yappc/ai/ui`, `@yappc/ai/ml`
- ✅ **Better discoverability:** All AI features in one place
- ✅ **Reduced maintenance:** One package.json, one tsconfig, one build process
- ✅ **Consistent versioning:** Single version number for all AI features

---

## Current Library Analysis

### @yappc/ai-core (72 files)

**Purpose:** Core AI infrastructure - agents, requirements service, parsing, validation

**Directory Structure:**
```
libs/ai-core/src/
├── core/                 # AI services, providers, types
│   ├── AIService.ts
│   ├── types.ts
│   └── providers/        # OpenAI, Anthropic, Local
├── agents/               # AI agents (22 files)
│   ├── BaseAgent.ts
│   ├── CopilotAgent.ts
│   ├── PredictionAgent.ts
│   ├── AnomalyDetectorAgent.ts
│   ├── TicketClassifierAgent.ts
│   ├── PRAnalyzerAgent.ts
│   ├── QueryParserAgent.ts
│   ├── AgentOrchestrator.ts
│   └── agents/           # Code, Review, Design agents
├── requirements/         # Requirements parsing and analysis
├── parser/               # Natural language parsing
├── validation/           # Validation logic
├── prompts/              # Prompt templates
├── sentiment/            # Sentiment analysis
├── nlp/                  # NLP utilities
├── security/             # Prompt sanitization
│   └── promptSanitizer.ts
├── cache/                # Semantic caching
│   └── SemanticCacheService.ts
├── ab-testing/           # A/B testing service
│   └── ABTestingService.ts
├── components/           # React components (9 files)
│   ├── AnomalyBanner.tsx
│   ├── PredictionCard.tsx
│   ├── SmartSuggestions.tsx
│   ├── AICopilotPanel.tsx
│   ├── AIPersonaBriefing.tsx
│   ├── AiWorkflowWizard.tsx
│   └── workflow/         # Workflow step components
└── hooks/                # React hooks
```

**Package Exports:**
```json
{
  ".": "./src/index.ts",
  "./agents": "./src/agents/index.ts",
  "./requirements": "./src/requirements/index.ts",
  "./parser": "./src/parser/index.ts",
  "./validation": "./src/validation/index.ts"
}
```

**Dependencies:**
- No external dependencies (self-contained)

**Usage:** 24 imports across codebase
- libs/canvas (9 imports) - IAIService, CompletionResponse, CompletionOptions
- libs/ui (14 imports) - IAIService, SentimentAnalyzer, SentimentResult
- apps/web (1 import) - For AI features

---

### @yappc/ai-ui (18 files)

**Purpose:** AI user interface components - requirements UI and editor

**Directory Structure:**
```
libs/ai-ui/src/
├── requirements/         # Requirements UI components
│   └── (requirement display, editing UI)
└── editor/               # AI-assisted requirement editor
    └── (editor components)
```

**Package Exports:**
```json
{
  ".": "./src/index.ts",
  "./requirements": "./src/requirements/index.ts",
  "./editor": "./src/editor/index.ts"
}
```

**Dependencies:**
- `@yappc/ai-core` (workspace) - Core AI services
- `@yappc/ui` (workspace) - Base UI components
- `react` (peer) - React framework

**Usage:** 0 direct imports found (may be used in apps)

---

### @yappc/ml (10 files)

**Purpose:** Machine learning and personalization features

**Directory Structure:**
```
libs/ml/src/
├── tracking/             # Behavior tracking
│   └── BehaviorTracker.ts
├── recommendations/      # Recommendation engine
│   └── RecommendationEngine.ts
├── adaptive/             # Adaptive UI
│   └── AdaptiveUI.ts
├── testing/              # A/B testing framework
│   └── ABTestFramework.ts
└── ui/                   # ML UI components
    └── (ML-specific UI)
```

**Package Exports:**
```json
{
  ".": "./src/index.ts"
}
```

**Dependencies:**
- `@yappc/types` (workspace) - Shared types

**Usage:** 0 direct imports found (standalone ML features)

---

## Overlap and Boundary Issues

### 1. A/B Testing Duplication 🔴
**Problem:** A/B testing exists in BOTH ai-core and ml
- `ai-core/src/ab-testing/ABTestingService.ts`
- `ml/src/testing/ABTestFramework.ts`

**Resolution:** Keep ML version (more comprehensive), delete ai-core version

---

### 2. UI Components Split Across Libraries 🟠
**Problem:** UI components in multiple places
- `ai-core/src/components/` (9 React components)
- `ai-ui/src/` (18 files)
- `ml/src/ui/` (ML-specific UI)

**Resolution:** Consolidate all UI into single `ui/` module

---

### 3. Unclear Library Boundaries 🟡
**Problem:** Not clear when to use ai-core vs ml
- ai-core has agents, requirements, parsing
- ml has tracking, recommendations, A/B testing
- Some overlap in purpose (both do "AI/ML")

**Resolution:** Single library eliminates confusion

---

## Target Structure

### Consolidated @yappc/ai Package

```
libs/ai/
├── package.json                 # Single package manifest
├── tsconfig.json                # Unified TypeScript config
├── README.md                    # Comprehensive AI library documentation
└── src/
    ├── index.ts                 # Main barrel file
    │
    ├── core/                    # Core AI infrastructure (from ai-core)
    │   ├── index.ts
    │   ├── AIService.ts
    │   ├── types.ts
    │   ├── providers/           # OpenAI, Anthropic, Local providers
    │   │   ├── index.ts
    │   │   ├── OpenAIProvider.ts
    │   │   ├── AnthropicProvider.ts
    │   │   └── LocalProvider.ts
    │   ├── cache/               # Semantic caching
    │   │   ├── index.ts
    │   │   └── SemanticCacheService.ts
    │   └── security/            # Security features
    │       ├── index.ts
    │       └── promptSanitizer.ts
    │
    ├── agents/                  # AI agents (from ai-core)
    │   ├── index.ts
    │   ├── BaseAgent.ts
    │   ├── CopilotAgent.ts
    │   ├── PredictionAgent.ts
    │   ├── AnomalyDetectorAgent.ts
    │   ├── TicketClassifierAgent.ts
    │   ├── PRAnalyzerAgent.ts
    │   ├── QueryParserAgent.ts
    │   ├── AgentOrchestrator.ts
    │   ├── api-client.ts
    │   └── agents/              # Specialized agents
    │       ├── CodeAgent.ts
    │       ├── ReviewAgent.ts
    │       └── DesignAgent.ts
    │
    ├── requirements/            # Requirements engineering (from ai-core)
    │   ├── index.ts
    │   ├── types.ts
    │   └── (requirements parsing logic)
    │
    ├── nlp/                     # Natural language processing (from ai-core)
    │   ├── index.ts
    │   ├── parser/              # Text parsing
    │   ├── sentiment/           # Sentiment analysis
    │   ├── prompts/             # Prompt templates
    │   └── validation/          # NLP validation
    │
    ├── ml/                      # Machine learning features (from ml)
    │   ├── index.ts
    │   ├── tracking/            # Behavior tracking
    │   │   ├── index.ts
    │   │   └── BehaviorTracker.ts
    │   ├── recommendations/     # Recommendation engine
    │   │   ├── index.ts
    │   │   └── RecommendationEngine.ts
    │   ├── adaptive/            # Adaptive UI
    │   │   ├── index.ts
    │   │   └── AdaptiveUI.ts
    │   └── testing/             # A/B testing (MERGED from ai-core + ml)
    │       ├── index.ts
    │       └── ABTestFramework.ts
    │
    ├── ui/                      # UI components (from ai-ui + ai-core/components)
    │   ├── index.ts
    │   ├── core/                # Core AI UI (from ai-core/components)
    │   │   ├── AnomalyBanner.tsx
    │   │   ├── PredictionCard.tsx
    │   │   ├── SmartSuggestions.tsx
    │   │   ├── AICopilotPanel.tsx
    │   │   ├── AIPersonaBriefing.tsx
    │   │   ├── AiWorkflowWizard.tsx
    │   │   └── workflow/        # Workflow components
    │   ├── requirements/        # Requirements UI (from ai-ui)
    │   │   └── (requirements UI components)
    │   ├── editor/              # AI editor (from ai-ui)
    │   │   └── (editor components)
    │   └── ml/                  # ML UI (from ml/ui)
    │       └── (ML UI components)
    │
    └── hooks/                   # React hooks (from ai-core)
        ├── index.ts
        └── (AI-related hooks)
```

---

## Package Configuration

### package.json

```json
{
  "name": "@yappc/ai",
  "version": "0.1.0",
  "description": "Unified AI library for YAPPC - agents, ML, requirements, and UI components",
  "type": "module",
  "main": "./src/index.ts",
  "types": "./src/index.ts",
  "exports": {
    ".": {
      "import": "./src/index.ts",
      "types": "./src/index.ts"
    },
    "./core": {
      "import": "./src/core/index.ts",
      "types": "./src/core/index.ts"
    },
    "./agents": {
      "import": "./src/agents/index.ts",
      "types": "./src/agents/index.ts"
    },
    "./requirements": {
      "import": "./src/requirements/index.ts",
      "types": "./src/requirements/index.ts"
    },
    "./nlp": {
      "import": "./src/nlp/index.ts",
      "types": "./src/nlp/index.ts"
    },
    "./ml": {
      "import": "./src/ml/index.ts",
      "types": "./src/ml/index.ts"
    },
    "./ml/tracking": {
      "import": "./src/ml/tracking/index.ts",
      "types": "./src/ml/tracking/index.ts"
    },
    "./ml/recommendations": {
      "import": "./src/ml/recommendations/index.ts",
      "types": "./src/ml/recommendations/index.ts"
    },
    "./ml/adaptive": {
      "import": "./src/ml/adaptive/index.ts",
      "types": "./src/ml/adaptive/index.ts"
    },
    "./ml/testing": {
      "import": "./src/ml/testing/index.ts",
      "types": "./src/ml/testing/index.ts"
    },
    "./ui": {
      "import": "./src/ui/index.ts",
      "types": "./src/ui/index.ts"
    },
    "./ui/requirements": {
      "import": "./src/ui/requirements/index.ts",
      "types": "./src/ui/requirements/index.ts"
    },
    "./ui/editor": {
      "import": "./src/ui/editor/index.ts",
      "types": "./src/ui/editor/index.ts"
    },
    "./hooks": {
      "import": "./src/hooks/index.ts",
      "types": "./src/hooks/index.ts"
    }
  },
  "scripts": {
    "type-check": "tsc --noEmit",
    "lint": "eslint src --ext .ts,.tsx",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "@yappc/types": "workspace:*",
    "@yappc/ui": "workspace:*"
  },
  "peerDependencies": {
    "react": "^19.0.0"
  },
  "devDependencies": {
    "@types/node": "^25.0.3",
    "@types/react": "^19.0.6",
    "react": "^19.1.0",
    "typescript": "^5.9.3",
    "vitest": "^4.0.16"
  },
  "keywords": [
    "ai",
    "artificial-intelligence",
    "machine-learning",
    "agents",
    "requirements",
    "recommendations",
    "personalization",
    "behavior-tracking",
    "ab-testing",
    "ui-components",
    "react"
  ],
  "author": "YAPPC Team",
  "license": "MIT"
}
```

---

## Import Migration Map

### Before (Current)

```typescript
// AI Core Services
import { AIService, IAIService } from '@yappc/ai-core';
import { CompletionResponse, CompletionOptions } from '@yappc/ai-core';
import { OpenAIProvider } from '@yappc/ai-core';

// AI Agents
import { CopilotAgent, PredictionAgent } from '@yappc/ai-core/agents';
import { AgentOrchestrator } from '@yappc/ai-core/agents';

// AI Components
import { AnomalyBanner, PredictionCard } from '@yappc/ai-core';
import { SmartSuggestions } from '@yappc/ai-core';

// Sentiment Analysis
import { SentimentAnalyzer, SentimentResult } from '@yappc/ai-core';

// Requirements UI
import { RequirementsPanel } from '@yappc/ai-ui/requirements';
import { RequirementEditor } from '@yappc/ai-ui/editor';

// ML Features
import { BehaviorTracker } from '@yappc/ml';
import { RecommendationEngine } from '@yappc/ml';
import { AdaptiveUI } from '@yappc/ml';
import { ABTestFramework } from '@yappc/ml';
```

### After (Consolidated)

```typescript
// AI Core Services
import { AIService, IAIService } from '@yappc/ai/core';
import { CompletionResponse, CompletionOptions } from '@yappc/ai/core';
import { OpenAIProvider } from '@yappc/ai/core';

// AI Agents
import { CopilotAgent, PredictionAgent } from '@yappc/ai/agents';
import { AgentOrchestrator } from '@yappc/ai/agents';

// AI Components
import { AnomalyBanner, PredictionCard } from '@yappc/ai/ui';
import { SmartSuggestions } from '@yappc/ai/ui';

// Sentiment Analysis
import { SentimentAnalyzer, SentimentResult } from '@yappc/ai/nlp';

// Requirements UI
import { RequirementsPanel } from '@yappc/ai/ui/requirements';
import { RequirementEditor } from '@yappc/ai/ui/editor';

// ML Features
import { BehaviorTracker } from '@yappc/ai/ml/tracking';
import { RecommendationEngine } from '@yappc/ai/ml/recommendations';
import { AdaptiveUI } from '@yappc/ai/ml/adaptive';
import { ABTestFramework } from '@yappc/ai/ml/testing';
```

---

## Implementation Steps

### Step 1: Create New Consolidated Structure (30 minutes)

```bash
# Create new directory structure
mkdir -p libs/ai/src/{core,agents,requirements,nlp,ml,ui,hooks}
mkdir -p libs/ai/src/core/{providers,cache,security}
mkdir -p libs/ai/src/agents/agents
mkdir -p libs/ai/src/nlp/{parser,sentiment,prompts,validation}
mkdir -p libs/ai/src/ml/{tracking,recommendations,adaptive,testing}
mkdir -p libs/ai/src/ui/{core,requirements,editor,ml}

# Create package.json and tsconfig.json
touch libs/ai/package.json
touch libs/ai/tsconfig.json
touch libs/ai/README.md
```

**Deliverable:** Empty directory structure with configuration files

---

### Step 2: Move Core AI Infrastructure (45 minutes)

```bash
# Move core AI services from ai-core
cp -r libs/ai-core/src/core/* libs/ai/src/core/
cp -r libs/ai-core/src/providers/* libs/ai/src/core/providers/
cp -r libs/ai-core/src/cache/* libs/ai/src/core/cache/
cp -r libs/ai-core/src/security/* libs/ai/src/core/security/

# Move agents
cp -r libs/ai-core/src/agents/* libs/ai/src/agents/

# Move hooks
cp -r libs/ai-core/src/hooks/* libs/ai/src/hooks/
```

**Deliverable:** Core AI infrastructure moved to new location

---

### Step 3: Move NLP and Requirements (30 minutes)

```bash
# Move NLP components
cp -r libs/ai-core/src/parser/* libs/ai/src/nlp/parser/
cp -r libs/ai-core/src/sentiment/* libs/ai/src/nlp/sentiment/
cp -r libs/ai-core/src/prompts/* libs/ai/src/nlp/prompts/
cp -r libs/ai-core/src/validation/* libs/ai/src/nlp/validation/

# Move requirements
cp -r libs/ai-core/src/requirements/* libs/ai/src/requirements/
```

**Deliverable:** NLP and requirements modules consolidated

---

### Step 4: Move ML Features (30 minutes)

```bash
# Move ML modules from ml library
cp -r libs/ml/src/tracking/* libs/ai/src/ml/tracking/
cp -r libs/ml/src/recommendations/* libs/ai/src/ml/recommendations/
cp -r libs/ml/src/adaptive/* libs/ai/src/ml/adaptive/

# Move A/B testing (use ML version, more comprehensive)
cp -r libs/ml/src/testing/* libs/ai/src/ml/testing/

# Move ML UI
cp -r libs/ml/src/ui/* libs/ai/src/ui/ml/
```

**Deliverable:** ML features consolidated into @yappc/ai/ml

---

### Step 5: Consolidate UI Components (45 minutes)

```bash
# Move AI core components
cp -r libs/ai-core/src/components/* libs/ai/src/ui/core/

# Move AI UI components
cp -r libs/ai-ui/src/requirements/* libs/ai/src/ui/requirements/
cp -r libs/ai-ui/src/editor/* libs/ai/src/ui/editor/
```

**Deliverable:** All AI UI components in single @yappc/ai/ui module

---

### Step 6: Create Barrel Files (45 minutes)

**Create index.ts files for all modules:**

```typescript
// libs/ai/src/index.ts (main barrel)
export * from './core';
export * from './agents';
export * from './requirements';
export * from './nlp';
export * from './ml';
export * from './ui';
export * from './hooks';

// libs/ai/src/core/index.ts
export * from './AIService';
export * from './types';
export * from './providers';
export * from './cache';
export * from './security';

// libs/ai/src/agents/index.ts
export * from './BaseAgent';
export * from './CopilotAgent';
export * from './PredictionAgent';
// ... etc for all agents

// libs/ai/src/nlp/index.ts
export * from './parser';
export * from './sentiment';
export * from './prompts';
export * from './validation';

// libs/ai/src/ml/index.ts
export * from './tracking';
export * from './recommendations';
export * from './adaptive';
export * from './testing';

// libs/ai/src/ui/index.ts
export * from './core';
export * from './requirements';
export * from './editor';
export * from './ml';
```

**Deliverable:** Complete barrel file structure for easy imports

---

### Step 7: Update Import Statements (1.5 hours)

**Automated script:**

```bash
# Create import update script
cat > scripts/update-ai-imports.sh << 'EOF'
#!/bin/bash

# Update @yappc/ai-core imports
find apps libs -type f \( -name "*.ts" -o -name "*.tsx" \) -exec sed -i '' \
  -e "s|from '@yappc/ai-core'|from '@yappc/ai/core'|g" \
  -e "s|from '@yappc/ai-core/agents'|from '@yappc/ai/agents'|g" \
  -e "s|from '@yappc/ai-core/requirements'|from '@yappc/ai/requirements'|g" \
  -e "s|from '@yappc/ai-core/parser'|from '@yappc/ai/nlp/parser'|g" \
  -e "s|from '@yappc/ai-core/validation'|from '@yappc/ai/nlp/validation'|g" \
  {} +

# Update @yappc/ai-ui imports
find apps libs -type f \( -name "*.ts" -o -name "*.tsx" \) -exec sed -i '' \
  -e "s|from '@yappc/ai-ui/requirements'|from '@yappc/ai/ui/requirements'|g" \
  -e "s|from '@yappc/ai-ui/editor'|from '@yappc/ai/ui/editor'|g" \
  -e "s|from '@yappc/ai-ui'|from '@yappc/ai/ui'|g" \
  {} +

# Update @yappc/ml imports
find apps libs -type f \( -name "*.ts" -o -name "*.tsx" \) -exec sed -i '' \
  -e "s|from '@yappc/ml'|from '@yappc/ai/ml'|g" \
  {} +

echo "✅ Import statements updated"
EOF

chmod +x scripts/update-ai-imports.sh
./scripts/update-ai-imports.sh
```

**Manual verification:**
- Check 24 files importing from @yappc/ai-core
- Verify sentiment analysis imports (14 in libs/ui)
- Test canvas integration (9 imports)

**Deliverable:** All import statements updated to new paths

---

### Step 8: Update Path Aliases (15 minutes)

**Update tsconfig.base.json:**

```json
{
  "compilerOptions": {
    "paths": {
      // Remove old aliases
      // "@yappc/ai-core": ["libs/ai-core/src/index.ts"],
      // "@yappc/ai-core/*": ["libs/ai-core/src/*"],
      // "@yappc/ai-ui": ["libs/ai-ui/src/index.ts"],
      // "@yappc/ai-ui/*": ["libs/ai-ui/src/*"],
      // "@yappc/ml": ["libs/ml/src/index.ts"],
      // "@yappc/ml/*": ["libs/ml/src/*"],
      
      // Add new consolidated aliases
      "@yappc/ai": ["libs/ai/src/index.ts"],
      "@yappc/ai/*": ["libs/ai/src/*"],
      "@yappc/ai/core": ["libs/ai/src/core/index.ts"],
      "@yappc/ai/agents": ["libs/ai/src/agents/index.ts"],
      "@yappc/ai/requirements": ["libs/ai/src/requirements/index.ts"],
      "@yappc/ai/nlp": ["libs/ai/src/nlp/index.ts"],
      "@yappc/ai/ml": ["libs/ai/src/ml/index.ts"],
      "@yappc/ai/ui": ["libs/ai/src/ui/index.ts"],
      "@yappc/ai/hooks": ["libs/ai/src/hooks/index.ts"]
    }
  }
}
```

**Update vite.config.ts:**

```typescript
export default defineConfig({
  resolve: {
    alias: {
      // Remove old aliases
      // '@yappc/ai-core': path.resolve(__dirname, 'libs/ai-core/src'),
      // '@yappc/ai-ui': path.resolve(__dirname, 'libs/ai-ui/src'),
      // '@yappc/ml': path.resolve(__dirname, 'libs/ml/src'),
      
      // Add new consolidated aliases
      '@yappc/ai': path.resolve(__dirname, 'libs/ai/src'),
      '@yappc/ai/core': path.resolve(__dirname, 'libs/ai/src/core'),
      '@yappc/ai/agents': path.resolve(__dirname, 'libs/ai/src/agents'),
      '@yappc/ai/requirements': path.resolve(__dirname, 'libs/ai/src/requirements'),
      '@yappc/ai/nlp': path.resolve(__dirname, 'libs/ai/src/nlp'),
      '@yappc/ai/ml': path.resolve(__dirname, 'libs/ai/src/ml'),
      '@yappc/ai/ui': path.resolve(__dirname, 'libs/ai/src/ui'),
      '@yappc/ai/hooks': path.resolve(__dirname, 'libs/ai/src/hooks'),
    },
  },
});
```

**Deliverable:** TypeScript and Vite configurations updated

---

### Step 9: Delete Old Libraries (10 minutes)

```bash
# Verify no imports remain
echo "Checking for old imports..."
grep -r "@yappc/ai-core" apps/ libs/ --include="*.ts" --include="*.tsx" || echo "✅ No ai-core imports"
grep -r "@yappc/ai-ui" apps/ libs/ --include="*.ts" --include="*.tsx" || echo "✅ No ai-ui imports"  
grep -r "@yappc/ml" apps/ libs/ --include="*.ts" --include="*.tsx" || echo "✅ No ml imports"

# Delete old libraries
rm -rf libs/ai-core
rm -rf libs/ai-ui
rm -rf libs/ml

echo "✅ Old libraries deleted"
```

**Deliverable:** Old libraries removed from codebase

---

### Step 10: Verify and Test (30 minutes)

```bash
# Type checking
pnpm typecheck
# Expected: ✅ No TypeScript errors

# Run tests
pnpm test
# Expected: ✅ All tests pass

# Build
pnpm build:web
# Expected: ✅ Build succeeds

# Check for circular dependencies in new structure
npx madge --circular --extensions ts,tsx libs/ai/
# Expected: ✅ No circular dependencies (or document acceptable ones)
```

**Deliverable:** Fully functional consolidated library with passing tests

---

## File Migration Matrix

| Source | Destination | Files | Notes |
|--------|-------------|-------|-------|
| ai-core/src/core/ | ai/src/core/ | 8 | Core AI services |
| ai-core/src/providers/ | ai/src/core/providers/ | 5 | AI providers |
| ai-core/src/cache/ | ai/src/core/cache/ | 1 | Semantic cache |
| ai-core/src/security/ | ai/src/core/security/ | 1 | Prompt sanitizer |
| ai-core/src/agents/ | ai/src/agents/ | 22 | All agent types |
| ai-core/src/requirements/ | ai/src/requirements/ | - | Requirements logic |
| ai-core/src/parser/ | ai/src/nlp/parser/ | - | NLP parsing |
| ai-core/src/sentiment/ | ai/src/nlp/sentiment/ | - | Sentiment analysis |
| ai-core/src/prompts/ | ai/src/nlp/prompts/ | - | Prompt templates |
| ai-core/src/validation/ | ai/src/nlp/validation/ | - | Validation logic |
| ai-core/src/components/ | ai/src/ui/core/ | 9 | AI UI components |
| ai-core/src/hooks/ | ai/src/hooks/ | - | React hooks |
| ai-core/src/ab-testing/ | DELETE | 1 | Use ML version instead |
| ai-ui/src/requirements/ | ai/src/ui/requirements/ | - | Requirements UI |
| ai-ui/src/editor/ | ai/src/ui/editor/ | - | AI editor UI |
| ml/src/tracking/ | ai/src/ml/tracking/ | 1 | Behavior tracking |
| ml/src/recommendations/ | ai/src/ml/recommendations/ | 1 | Recommendations |
| ml/src/adaptive/ | ai/src/ml/adaptive/ | 1 | Adaptive UI |
| ml/src/testing/ | ai/src/ml/testing/ | 1 | A/B testing |
| ml/src/ui/ | ai/src/ui/ml/ | - | ML UI components |

**Total Files:** ~100 files migrated

---

## Testing Strategy

### Unit Tests
- ✅ All existing tests should pass unchanged (moved with code)
- ✅ Update test imports to new paths
- ✅ Run full test suite: `pnpm test`

### Integration Tests
- ✅ Test AI service initialization
- ✅ Test agent orchestration
- ✅ Test ML feature integration
- ✅ Test UI component rendering

### Import Resolution Tests
```bash
# Test that all new imports resolve correctly
pnpm typecheck

# Test that no old imports remain
grep -r "@yappc/ai-core" apps/ libs/ --include="*.ts" --include="*.tsx"
grep -r "@yappc/ai-ui" apps/ libs/ --include="*.ts" --include="*.tsx"
grep -r "@yappc/ml" apps/ libs/ --include="*.ts" --include="*.tsx"
```

---

## Success Criteria

- ✅ **Zero old imports:** No references to @yappc/ai-core, @yappc/ai-ui, @yappc/ml
- ✅ **Type checking passes:** `pnpm typecheck` succeeds
- ✅ **All tests pass:** `pnpm test` with 100% success rate
- ✅ **Build succeeds:** `pnpm build:web` completes successfully
- ✅ **No circular dependencies:** New structure has no circular deps (or documented acceptable ones)
- ✅ **Documentation updated:** README.md explains new structure
- ✅ **Import guidelines updated:** docs/development/imports.md reflects new paths

---

## Rollback Plan

If issues arise during consolidation:

1. **Revert import changes:**
```bash
git checkout -- apps/ libs/ # Revert import changes
```

2. **Restore old libraries:**
```bash
git checkout -- libs/ai-core libs/ai-ui libs/ml
```

3. **Restore old aliases:**
```bash
git checkout -- tsconfig.base.json vite.config.ts
```

4. **Clean up:**
```bash
rm -rf libs/ai
pnpm install
pnpm typecheck
```

---

## Timeline Estimate

| Step | Duration | Description |
|------|----------|-------------|
| 1. Create Structure | 30 min | Create directories and config files |
| 2. Move Core | 45 min | Move core AI services |
| 3. Move NLP | 30 min | Move NLP and requirements |
| 4. Move ML | 30 min | Move ML features |
| 5. Consolidate UI | 45 min | Consolidate UI components |
| 6. Barrel Files | 45 min | Create index.ts exports |
| 7. Update Imports | 90 min | Update all import statements |
| 8. Update Aliases | 15 min | Update tsconfig and vite config |
| 9. Delete Old | 10 min | Remove old libraries |
| 10. Verify & Test | 30 min | Full testing and validation |
| **Total** | **5.5 hours** | **Complete consolidation** |

---

## Documentation Updates

### README.md (libs/ai/README.md)

```markdown
# @yappc/ai - Unified AI Library

Comprehensive AI and machine learning library for YAPPC, providing agents, NLP, ML features, and UI components.

## Modules

### Core AI (`@yappc/ai/core`)
- AI services and providers (OpenAI, Anthropic, Local)
- Semantic caching
- Security (prompt sanitization)

### Agents (`@yappc/ai/agents`)
- Base agent framework
- Specialized agents (Copilot, Prediction, Anomaly Detection, etc.)
- Agent orchestration

### Requirements (`@yappc/ai/requirements`)
- Requirements parsing and analysis
- Validation logic

### NLP (`@yappc/ai/nlp`)
- Natural language parsing
- Sentiment analysis
- Prompt templates
- Text validation

### Machine Learning (`@yappc/ai/ml`)
- Behavior tracking (`@yappc/ai/ml/tracking`)
- Recommendations (`@yappc/ai/ml/recommendations`)
- Adaptive UI (`@yappc/ai/ml/adaptive`)
- A/B testing (`@yappc/ai/ml/testing`)

### UI Components (`@yappc/ai/ui`)
- Core AI components (Anomaly Banner, Prediction Card, etc.)
- Requirements UI
- AI editor
- ML UI components

### Hooks (`@yappc/ai/hooks`)
- React hooks for AI features

## Usage

```typescript
// Core AI
import { AIService } from '@yappc/ai/core';

// Agents
import { CopilotAgent } from '@yappc/ai/agents';

// ML
import { BehaviorTracker } from '@yappc/ai/ml/tracking';

// UI
import { SmartSuggestions } from '@yappc/ai/ui';
```

---

**Generated by:** YAPPC Implementation Task 2.3  
**Date:** 2026-01-31
