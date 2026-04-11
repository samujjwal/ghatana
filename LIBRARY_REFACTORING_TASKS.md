# Library Refactoring: Detailed Task Implementation Plan

> **Project**: Ghatana Frontend/TypeScript Library Consolidation
> **Estimated Duration**: 8 weeks
> **Last Updated**: 2026-04-10
> **Status**: Ready for Execution

---

## Executive Summary

This document provides a granular, task-by-task implementation plan for consolidating and restructuring the Ghatana frontend/TypeScript library ecosystem. Each task includes:
- Concrete implementation steps
- Dependencies (what must complete first)
- Validation criteria
- Estimated effort
- Risk assessment

---

## Phase 1: Critical Consolidation (Weeks 1-2)

**Phase Goal**: Eliminate critical duplication, god libraries, and thin wrappers

### Task 1.1: Split @ghatana/canvas (God Library)

**Priority**: P0 | **Estimated Effort**: 3 days | **Risk**: High

#### Task 1.1.1: Create @ghatana/canvas-core
**Dependencies**: None
**Steps**:
```bash
# 1. Create new library structure
mkdir -p platform/typescript/canvas/canvas-core/src
mkdir -p platform/typescript/canvas/canvas-core/src/types
mkdir -p platform/typescript/canvas/canvas-core/src/elements
mkdir -p platform/typescript/canvas/canvas-core/src/utils

# 2. Move core types
cp platform/typescript/canvas/src/types/index.ts platform/typescript/canvas/canvas-core/src/types/
cp platform/typescript/canvas/src/utils/bounds.ts platform/typescript/canvas/canvas-core/src/utils/

# 3. Move base element classes
cp platform/typescript/canvas/src/elements/base.ts platform/typescript/canvas/canvas-core/src/elements/

# 4. Create package.json
cat > platform/typescript/canvas/canvas-core/package.json << 'EOF'
{
  "name": "@ghatana/canvas-core",
  "version": "0.1.0",
  "description": "Core canvas types and base classes - framework agnostic",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "types": "./dist/index.d.ts",
      "import": "./dist/index.js"
    },
    "./types": {
      "types": "./dist/types/index.d.ts",
      "import": "./dist/types/index.js"
    }
  },
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "clean": "rm -rf dist",
    "type-check": "tsc --noEmit",
    "test": "vitest run"
  },
  "dependencies": {
    "zod": "^4.3.6"
  },
  "devDependencies": {
    "typescript": "^6.0.2",
    "vitest": "^4.1.2"
  },
  "license": "MIT"
}
EOF

# 5. Create index.ts
cat > platform/typescript/canvas/canvas-core/src/index.ts << 'EOF'
export * from './types/index.js';
export { Bound } from './utils/bounds.js';
export { CanvasElement } from './elements/base.js';
EOF

# 6. Create tsconfig.json
cat > platform/typescript/canvas/canvas-core/tsconfig.json << 'EOF'
{
  "extends": "../../tsconfig.base.json",
  "compilerOptions": {
    "outDir": "./dist",
    "rootDir": "./src"
  }
}
EOF
```
**Validation**:
- [ ] `pnpm build` succeeds
- [ ] `pnpm test` passes
- [ ] No imports from original canvas

---

#### Task 1.1.2: Create @ghatana/canvas-react
**Dependencies**: Task 1.1.1 (canvas-core)
**Steps**:
```bash
# 1. Create structure
mkdir -p platform/typescript/canvas/canvas-react/src

# 2. Move React-specific code from original canvas
cp platform/typescript/canvas/src/react/*.tsx platform/typescript/canvas/canvas-react/src/ 2>/dev/null || echo "No react folder - extract from hybrid"
cp platform/typescript/canvas/src/hybrid/HybridCanvas.tsx platform/typescript/canvas/canvas-react/src/ 2>/dev/null

# 3. Create package.json with canvas-core dependency
cat > platform/typescript/canvas/canvas-react/package.json << 'EOF'
{
  "name": "@ghatana/canvas-react",
  "version": "0.1.0",
  "description": "React integration for Ghatana Canvas",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "types": "./dist/index.d.ts",
      "import": "./dist/index.js"
    }
  },
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "clean": "rm -rf dist",
    "type-check": "tsc --noEmit",
    "test": "vitest run"
  },
  "dependencies": {
    "@ghatana/canvas-core": "workspace:*",
    "react": "^19.2.4"
  },
  "peerDependencies": {
    "react": "^19.2.4",
    "react-dom": "^19.2.4"
  },
  "devDependencies": {
    "@types/react": "^19.2.14",
    "typescript": "^6.0.2",
    "vitest": "^4.1.2"
  },
  "license": "MIT"
}
EOF
```
**Validation**:
- [ ] Depends on canvas-core
- [ ] React types correct
- [ ] Exports React components only

---

#### Task 1.1.3: Create @ghatana/canvas-plugins
**Dependencies**: Task 1.1.1 (canvas-core)
**Steps**:
```bash
mkdir -p platform/typescript/canvas/canvas-plugins/src

# Move plugin system code
cp -r platform/typescript/canvas/src/plugins/* platform/typescript/canvas/canvas-plugins/src/
cp platform/typescript/canvas/src/core/element-registry.ts platform/typescript/canvas/canvas-plugins/src/
cp platform/typescript/canvas/src/core/tool-registry.ts platform/typescript/canvas/canvas-plugins/src/

# Create package.json
cat > platform/typescript/canvas/canvas-plugins/package.json << 'EOF'
{
  "name": "@ghatana/canvas-plugins",
  "version": "0.1.0",
  "description": "Plugin system for Ghatana Canvas",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "dependencies": {
    "@ghatana/canvas-core": "workspace:*",
    "zod": "^4.3.6"
  },
  "peerDependencies": {
    "react": "^19.2.4"
  },
  "license": "MIT"
}
EOF
```
**Validation**:
- [ ] Plugin types exported
- [ ] Registries functional

---

#### Task 1.1.4: Create @ghatana/canvas-chrome
**Dependencies**: Task 1.1.2 (canvas-react)
**Steps**:
```bash
mkdir -p platform/typescript/canvas/canvas-chrome/src

# Move chrome/shell components
cp -r platform/typescript/canvas/src/chrome/* platform/typescript/canvas/canvas-chrome/src/
cp platform/typescript/canvas/src/components/RoleSwitcher.tsx platform/typescript/canvas/canvas-chrome/src/
cp platform/typescript/canvas/src/components/SmartContextBar.tsx platform/typescript/canvas/canvas-chrome/src/

# Note: These are domain-specific components that should eventually
# move to product libraries. For now, isolate them here.
```
**Validation**:
- [ ] Chrome components isolated
- [ ] No product-specific logic in core libraries

---

#### Task 1.1.5: Create @ghatana/canvas-tools
**Dependencies**: Task 1.1.1 (canvas-core)
**Steps**:
```bash
mkdir -p platform/typescript/canvas/canvas-tools/src

# Move drawing/editing tools
cp -r platform/typescript/canvas/src/tools/* platform/typescript/canvas/canvas-tools/src/
```
**Validation**:
- [ ] All tools functional
- [ ] Tool base classes in core

---

#### Task 1.1.6: Update Original @ghatana/canvas
**Dependencies**: Tasks 1.1.1-1.1.5
**Steps**:
```bash
# 1. Update original canvas to re-export from new libraries
# 2. Mark as deprecated in package.json
# 3. Update all consumers (see Task 1.1.7)
```
**Validation**:
- [ ] Original canvas is thin re-export layer
- [ ] All tests pass

---

#### Task 1.1.7: Update All Canvas Consumers
**Dependencies**: Task 1.1.6
**Steps**:
```bash
# Find all canvas imports
grep -r "from '@ghatana/canvas'" platform/typescript products \
  --include="*.ts" --include="*.tsx" > /tmp/canvas-consumers.txt

# Update imports based on usage:
# - Core types → @ghatana/canvas-core
# - React components → @ghatana/canvas-react
# - Plugins → @ghatana/canvas-plugins
# - Chrome → @ghatana/canvas-chrome
# - Tools → @ghatana/canvas-tools
```
**Files to Update** (estimate based on audit):
- `products/yappc/frontend/libs/yappc-ui` - Canvas integration
- `products/yappc/frontend/apps/*` - Canvas usage
- Any other canvas consumers

**Validation**:
- [ ] No imports from original canvas (except re-export validation)
- [ ] All consumers using specific sub-libraries

---

### Task 1.2: Merge Accessibility Libraries (3→1)

**Priority**: P1 | **Estimated Effort**: 2 days | **Risk**: Medium

#### Task 1.2.1: Create Unified @ghatana/accessibility
**Dependencies**: None
**Steps**:
```bash
# 1. Create new library (or use existing audit-components as base)
mkdir -p platform/typescript/accessibility/src

# 2. Merge content from three sources
cp -r platform/typescript/accessibility-audit/src/* platform/typescript/accessibility/src/ 2>/dev/null
cp -r platform/typescript/audit-components/src/* platform/typescript/accessibility/src/ 2>/dev/null
# Note: @yappc/a11y is product-specific, will be addressed separately

# 3. Create unified package.json
cat > platform/typescript/accessibility/package.json << 'EOF'
{
  "name": "@ghatana/accessibility",
  "version": "1.0.0",
  "description": "Accessibility testing and components for Ghatana platform",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "types": "./dist/index.d.ts",
      "import": "./dist/index.js"
    },
    "./testing": {
      "types": "./dist/testing/index.d.ts",
      "import": "./dist/testing/index.js"
    },
    "./components": {
      "types": "./dist/components/index.d.ts",
      "import": "./dist/components/index.js"
    }
  },
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "test": "vitest run"
  },
  "dependencies": {
    "@ghatana/platform-utils": "workspace:*",
    "@axe-core/react": "^4.11.1",
    "axe-core": "^4.11.2",
    "zod": "^4.3.6"
  },
  "peerDependencies": {
    "react": "^19.2.4",
    "react-dom": "^19.2.4"
  },
  "license": "MIT"
}
EOF

# 4. Organize code into sub-exports
mkdir -p platform/typescript/accessibility/src/testing
mkdir -p platform/typescript/accessibility/src/components

# Move testing code to testing/
# Move component code to components/

# 5. Create index.ts
cat > platform/typescript/accessibility/src/index.ts << 'EOF'
export * from './testing/index.js';
export * from './components/index.js';
EOF
```
**Validation**:
- [ ] All functionality from 3 libraries present
- [ ] Sub-exports work correctly
- [ ] No duplicate code

---

#### Task 1.2.2: Remove @ghatana/accessibility-audit
**Dependencies**: Task 1.2.1
**Steps**:
```bash
# 1. Verify all functionality migrated
diff -r platform/typescript/accessibility-audit/src platform/typescript/accessibility/src/testing

# 2. Update any remaining imports
find . -type f \( -name "*.ts" -o -name "*.tsx" \) \
  -exec grep -l "@ghatana/accessibility-audit" {} \; > /tmp/a11y-audit-refs.txt

# 3. Update imports to @ghatana/accessibility/testing

# 4. Remove library
rm -rf platform/typescript/accessibility-audit

# 5. Update pnpm-workspace.yaml if needed
```
**Validation**:
- [ ] No references to accessibility-audit
- [ ] All consumers updated

---

#### Task 1.2.3: Update @ghatana/audit-components to Re-export
**Dependencies**: Task 1.2.1
**Steps**:
```bash
# Option A: Make audit-components a thin re-export of accessibility
# Option B: Delete audit-components entirely

# Recommended: Option B - delete after migration period
cat > platform/typescript/audit-components/package.json << 'EOF'
{
  "name": "@ghatana/audit-components",
  "version": "1.0.0-deprecated",
  "description": "DEPRECATED: Use @ghatana/accessibility instead",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "scripts": {
    "build": "tsc"
  },
  "dependencies": {
    "@ghatana/accessibility": "workspace:*"
  }
}
EOF

# Update index.ts to re-export
cat > platform/typescript/audit-components/src/index.ts << 'EOF'
// DEPRECATED: Use @ghatana/accessibility instead
export * from '@ghatana/accessibility';
console.warn('@ghatana/audit-components is deprecated. Use @ghatana/accessibility');
EOF
```
**Validation**:
- [ ] Deprecation warning in place
- [ ] All exports functional

---

### Task 1.3: Merge @yappc/state into @ghatana/state

**Priority**: P0 | **Estimated Effort**: 2 days | **Risk**: Medium

#### Task 1.3.1: Analyze YAPPC State Atoms
**Dependencies**: None
**Steps**:
```bash
# 1. Identify all atoms in @yappc/state
cat products/yappc/frontend/libs/yappc-state/src/index.ts
ls -la products/yappc/frontend/libs/yappc-state/src/

# 2. Check which are YAPPC-specific vs platform-generic
# YAPPC-specific: Keep in product lib (or move to app)
# Platform-generic: Move to @ghatana/state

# 3. Check nanostores usage (competing paradigm)
grep -r "nanostores" products/yappc/frontend/libs/yappc-state/
```
**Expected Findings**:
- Most atoms likely wrap `@ghatana/state` with no added value
- Some may be genuinely YAPPC-specific
- Nanostores usage indicates anti-pattern (competing state lib)

**Validation**:
- [ ] Catalog of all atoms created
- [ ] Classification: Platform vs YAPPC-specific

---

#### Task 1.3.2: Migrate Generic Atoms to @ghatana/state
**Dependencies**: Task 1.3.1
**Steps**:
```bash
# 1. Move atoms that should be platform-level
# Example pattern:
# mv products/yappc/frontend/libs/yappc-state/src/atoms/userPreferencesAtom.ts \
#    platform/typescript/state/src/atoms/

# 2. Update @ghatana/state exports
cat >> platform/typescript/state/src/index.ts << 'EOF'
// YAPPC-generic atoms migrated from @yappc/state
export { userPreferencesAtom } from './atoms/userPreferencesAtom.js';
export { workspaceSettingsAtom } from './atoms/workspaceSettingsAtom.js';
EOF

# 3. Remove nanostores dependency
# Edit platform/typescript/state/package.json - remove nanostores
```
**Validation**:
- [ ] Platform atoms in @ghatana/state
- [ ] No nanostores in platform state

---

#### Task 1.3.3: Update @yappc/state to Re-export Platform
**Dependencies**: Task 1.3.2
**Steps**:
```bash
# Update @yappc/state to be thin wrapper or delete
cat > products/yappc/frontend/libs/yappc-state/src/index.ts << 'EOF'
// YAPPC State Management - Re-exports from @ghatana/state
// Add YAPPC-specific atoms only

export * from '@ghatana/state';

// YAPPC-specific atoms
export { yappcSpecificAtom } from './yappc-specific-atom.js';
EOF

# Update package.json
cat > products/yappc/frontend/libs/yappc-state/package.json << 'EOF'
{
  "name": "@yappc/state",
  "version": "1.0.0",
  "description": "YAPPC state management - re-exports @ghatana/state",
  "dependencies": {
    "@ghatana/state": "workspace:*"
  },
  "peerDependencies": {
    "react": "^19.2.4"
  }
}
EOF
```
**Validation**:
- [ ] All @ghatana/state exports available
- [ ] YAPPC-specific atoms minimal
- [ ] No duplicate atom definitions

---

### Task 1.4: Merge @yappc/auth into @ghatana/sso-client

**Priority**: P0 | **Estimated Effort**: 1.5 days | **Risk**: Low

#### Task 1.4.1: Analyze Auth Components
**Dependencies**: None
**Steps**:
```bash
# 1. Identify auth components in @yappc/auth
cat products/yappc/frontend/libs/yappc-auth/src/index.ts
ls -la products/yappc/frontend/libs/yappc-auth/src/

# 2. Check if genuinely YAPPC-specific or generic auth patterns
```
**Expected**: Most auth patterns are generic (RBAC, OAuth, security)

---

#### Task 1.4.2: Migrate Auth to @ghatana/sso-client
**Dependencies**: Task 1.4.1
**Steps**:
```bash
# 1. Extend sso-client with auth functionality
mkdir -p platform/typescript/sso-client/src/rbac
mkdir -p platform/typescript/sso-client/src/oauth
mkdir -p platform/typescript/sso-client/src/security

# 2. Move auth code
cp -r products/yappc/frontend/libs/yappc-auth/src/rbac/* platform/typescript/sso-client/src/rbac/
cp -r products/yappc/frontend/libs/yappc-auth/src/oauth/* platform/typescript/sso-client/src/oauth/
cp -r products/yappc/frontend/libs/yappc-auth/src/security/* platform/typescript/sso-client/src/security/

# 3. Update sso-client exports
cat >> platform/typescript/sso-client/src/index.ts << 'EOF'
export * from './rbac/index.js';
export * from './oauth/index.js';
export * from './security/index.js';
EOF
```
**Validation**:
- [ ] All auth functionality available in sso-client
- [ ] Types preserved

---

#### Task 1.4.3: Delete @yappc/auth
**Dependencies**: Task 1.4.2
**Steps**:
```bash
# 1. Update all imports from @yappc/auth to @ghatana/sso-client
find products/yappc -type f \( -name "*.ts" -o -name "*.tsx" \) \
  -exec sed -i '' 's/from "@yappc\/auth"/from "@ghatana\/sso-client"/g' {} \;

# 2. Remove library
rm -rf products/yappc/frontend/libs/yappc-auth

# 3. Update pnpm-workspace.yaml
```
**Validation**:
- [ ] No @yappc/auth references
- [ ] All consumers updated

---

### Task 1.5: Delete @yappc/api

**Priority**: P0 | **Estimated Effort**: 1 day | **Risk**: Low

#### Task 1.5.1: Verify @yappc/api is Thin Wrapper
**Dependencies**: None
**Steps**:
```bash
# Check if @yappc/api just re-exports or adds value
cat products/yappc/frontend/libs/api/src/index.ts

# Expected: Just re-exports from @ghatana/api with minimal additions
```

---

#### Task 1.5.2: Update Imports and Delete
**Dependencies**: Task 1.5.1
**Steps**:
```bash
# 1. Update all imports from @yappc/api to @ghatana/api
find products/yappc -type f \( -name "*.ts" -o -name "*.tsx" \) \
  -exec sed -i '' 's/from "@yappc\/api"/from "@ghatana\/api"/g' {} \;

# 2. Handle any YAPPC-specific API extensions
# If there are YAPPC-specific endpoints, move to:
# - @yappc/core (if shared across YAPPC)
# - App code (if app-specific)

# 3. Remove library
rm -rf products/yappc/frontend/libs/api

# 4. Update pnpm-workspace.yaml
```
**Validation**:
- [ ] No @yappc/api references
- [ ] All consumers using @ghatana/api

---

### Task 1.6: Delete Product UI Wrappers

**Priority**: P0 | **Estimated Effort**: 2 days | **Risk**: Low

#### Task 1.6.1: Delete @dcmaar/ui
**Dependencies**: None
**Steps**:
```bash
# 1. Update all imports to use @ghatana/design-system directly
find products/dcmaar -type f \( -name "*.ts" -o -name "*.tsx" \) \
  -exec sed -i '' 's/from "@dcmaar\/ui"/from "@ghatana\/design-system"/g' {} \;

# 2. Handle DCMAAR-specific components
# If @dcmaar/ui has genuinely DCMAAR-specific components:
# - Move to @dcmaar/dashboard-core
# - Or inline into apps

# 3. Remove library
rm -rf products/dcmaar/libs/typescript/dcmaar-ui

# 4. Update pnpm-workspace.yaml
```
**Validation**:
- [ ] No @dcmaar/ui references
- [ ] All imports use design-system

---

#### Task 1.6.2: Delete @yappc/ui (or keep minimal)
**Dependencies**: None
**Steps**:
```bash
# Note: @yappc/ui is more substantial than other wrappers
# It may have YAPPC-specific components worth keeping

# 1. Analyze what's genuinely YAPPC-specific vs wrapping design-system
cat products/yappc/frontend/libs/yappc-ui/src/index.ts

# 2. Decision:
# Option A: If mostly wrapper → delete, use design-system directly
# Option B: If has YAPPC-specific UI → keep, remove design-system wrapper code

# For Option A:
find products/yappc -type f \( -name "*.ts" -o -name "*.tsx" \) \
  -exec sed -i '' 's/from "@yappc\/ui"/from "@ghatana\/design-system"/g' {} \;
rm -rf products/yappc/frontend/libs/yappc-ui

# For Option B (preferred based on audit):
# Keep @yappc/ui but refactor to:
# - Remove all design-system wrapping
# - Keep only YAPPC-specific components
# - Import from @ghatana/design-system where needed
```
**Validation**:
- [ ] YAPPC-specific components identified
- [ ] Wrapper code removed
- [ ] Imports updated

---

#### Task 1.6.3: Delete @tutorputor/ui
**Dependencies**: None
**Steps**:
```bash
# 1. Update imports
find products/tutorputor -type f \( -name "*.ts" -o -name "*.tsx" \) \
  -exec sed -i '' 's/from "@tutorputor\/ui"/from "@ghatana\/design-system"/g' {} \;

# 2. Remove library
rm -rf products/tutorputor/libs/tutorputor-ui

# 3. Update pnpm-workspace.yaml
```
**Validation**:
- [ ] No @tutorputor/ui references

---

### Phase 1 Validation

**Run after all Phase 1 tasks**:
```bash
cd /Users/samujjwal/Development/ghatana

# 1. Canvas split validation
grep -r "@ghatana/canvas" platform/typescript products \
  --include="*.ts" --include="*.tsx" | grep -v "canvas-core\|canvas-react\|canvas-plugins\|canvas-chrome\|canvas-tools" || echo "✓ Canvas consumers using specific libs"

# 2. Accessibility merged
grep -r "@ghatana/accessibility-audit\|@ghatana/audit-components" . \
  --include="*.ts" --include="*.tsx" 2>/dev/null || echo "✓ Accessibility libraries merged"

# 3. State merged
grep -r "from '@yappc/state'" products/yappc --include="*.ts" --include="*.tsx" | \
  grep -v "@ghatana/state" || echo "✓ YAPPC state re-exports platform"

# 4. Auth merged
grep -r "@yappc/auth" products/yappc --include="*.ts" --include="*.tsx" 2>/dev/null || echo "✓ YAPPC auth deleted"

# 5. API deleted
grep -r "@yappc/api" products/yappc --include="*.ts" --include="*.tsx" 2>/dev/null || echo "✓ YAPPC API deleted"

# 6. UI wrappers deleted
grep -r "@dcmaar/ui\|@tutorputor/ui" products --include="*.ts" --include="*.tsx" 2>/dev/null || echo "✓ Product UI wrappers deleted"

# 7. Build all
pnpm -r build

# 8. Test all
pnpm -r test
```

---

## Phase 2: Product Library Cleanup (Weeks 3-4)

**Phase Goal**: Split YAPPC god libraries into focused libraries

### Task 2.1: Split @yappc/core

**Priority**: P1 | **Estimated Effort**: 2 days | **Risk**: Medium

#### Task 2.1.1: Create @yappc/chat
**Dependencies**: None
**Steps**:
```bash
mkdir -p products/yappc/frontend/libs/yappc-chat/src

# Move chat code
cp -r products/yappc/frontend/libs/yappc-core/src/chat/* products/yappc/frontend/libs/yappc-chat/src/

# Create package.json
cat > products/yappc/frontend/libs/yappc-chat/package.json << 'EOF'
{
  "name": "@yappc/chat",
  "version": "1.0.0",
  "description": "YAPPC chat and messaging functionality",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "test": "vitest run"
  },
  "dependencies": {
    "@yappc/core": "workspace:*",
    "zod": "^4.3.6"
  },
  "peerDependencies": {
    "react": "^19.2.4"
  },
  "license": "Apache-2.0"
}
EOF

# Create index.ts
cat > products/yappc/frontend/libs/yappc-chat/src/index.ts << 'EOF'
export * from './chat/index.js';
EOF
```
**Validation**:
- [ ] Chat code isolated
- [ ] Builds successfully

---

#### Task 2.1.2: Update @yappc/core
**Dependencies**: Task 2.1.1
**Steps**:
```bash
# Remove chat, auth, security, testing from yappc-core
rm -rf products/yappc/frontend/libs/yappc-core/src/chat
rm -rf products/yappc/frontend/libs/yappc-core/src/auth
rm -rf products/yappc/frontend/libs/yappc-core/src/security
rm -rf products/yappc/frontend/libs/yappc-core/src/testing

# Update index.ts to remove exports
# Keep only: utils, types, constants
```
**Validation**:
- [ ] Core library focused
- [ ] Only utilities/types/constants

---

#### Task 2.1.3: Update All Consumers
**Dependencies**: Task 2.1.2
**Steps**:
```bash
# Update imports from @yappc/core to @yappc/chat where appropriate
find products/yappc -type f \( -name "*.ts" -o -name "*.tsx" \) \
  -exec grep -l "from '@yappc/core'" {} \; | \
  xargs -I {} sed -i '' '/chat/s/from "@yappc\/core"/from "@yappc\/chat"/g' {}

# Similar for auth (now in sso-client)
```
**Validation**:
- [ ] Chat imports use @yappc/chat
- [ ] Auth imports use @ghatana/sso-client
- [ ] Core imports minimal

---

### Task 2.2: Split @yappc/ui

**Priority**: P1 | **Estimated Effort**: 2.5 days | **Risk**: Medium

#### Task 2.2.1: Create @yappc/initialization-ui
**Dependencies**: None
**Steps**:
```bash
mkdir -p products/yappc/frontend/libs/yappc-initialization-ui/src

# Move initialization components
cp -r products/yappc/frontend/libs/yappc-ui/src/components/initialization-ui/* \
  products/yappc/frontend/libs/yappc-initialization-ui/src/

# Create package.json
cat > products/yappc/frontend/libs/yappc-initialization-ui/package.json << 'EOF'
{
  "name": "@yappc/initialization-ui",
  "version": "1.0.0",
  "description": "YAPPC initialization wizard UI components",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "scripts": {
    "build": "tsc",
    "test": "vitest run"
  },
  "dependencies": {
    "@ghatana/design-system": "workspace:*",
    "@ghatana/wizard": "workspace:*",
    "@yappc/core": "workspace:*"
  },
  "peerDependencies": {
    "react": "^19.2.4"
  }
}
EOF
```
**Validation**:
- [ ] Initialization components isolated
- [ ] Uses @ghatana/wizard

---

#### Task 2.2.2: Create @yappc/development-ui
**Dependencies**: None
**Steps**:
```bash
mkdir -p products/yappc/frontend/libs/yappc-development-ui/src

# Move development components
cp -r products/yappc/frontend/libs/yappc-ui/src/components/development-ui/* \
  products/yappc/frontend/libs/yappc-development-ui/src/

# Create package.json (similar to initialization-ui)
```
**Validation**:
- [ ] Development components isolated

---

#### Task 2.2.3: Update @yappc/ui
**Dependencies**: Tasks 2.2.1, 2.2.2
**Steps**:
```bash
# Remove initialization and development components
rm -rf products/yappc/frontend/libs/yappc-ui/src/components/initialization-ui
rm -rf products/yappc/frontend/libs/yappc-ui/src/components/development-ui

# Update index.ts
# Keep only: base, base-ui, navigation-ui (generic YAPPC components)
```
**Validation**:
- [ ] UI library focused on generic components
- [ ] No domain-specific code

---

### Task 2.3: Split @yappc/api

**Priority**: P1 | **Estimated Effort**: 1.5 days | **Risk**: Low

#### Task 2.3.1: Create @yappc/devsecops
**Dependencies**: None
**Steps**:
```bash
mkdir -p products/yappc/frontend/libs/yappc-devsecops/src

# Move devsecops code
cp -r products/yappc/frontend/libs/yappc-api/src/devsecops/* \
  products/yappc/frontend/libs/yappc-devsecops/src/

# Create package.json
cat > products/yappc/frontend/libs/yappc-devsecops/package.json << 'EOF'
{
  "name": "@yappc/devsecops",
  "version": "1.0.0",
  "description": "YAPPC DevSecOps API concerns",
  "type": "module",
  "main": "./src/index.ts",
  "scripts": {
    "type-check": "tsc --noEmit",
    "test": "vitest run"
  },
  "dependencies": {
    "@ghatana/api": "workspace:*",
    "@yappc/core": "workspace:*"
  }
}
EOF
```
**Validation**:
- [ ] DevSecOps code isolated

---

#### Task 2.3.2: Update Consumers
**Dependencies**: Task 2.3.1
**Steps**:
```bash
# Update imports from @yappc/api to @yappc/devsecops for devsecops code
find products/yappc -type f \( -name "*.ts" -o -name "*.tsx" \) \
  -exec grep -l "from '@yappc/api'" {} \; | \
  xargs -I {} grep -l "devsecops" {} | \
  xargs -I {} sed -i '' 's/from "@yappc\/api"/from "@yappc\/devsecops"/g' {}
```
**Validation**:
- [ ] DevSecOps imports use correct library

---

### Task 2.4: Consolidate Code Editors

**Priority**: P1 | **Estimated Effort**: 1.5 days | **Risk**: Low

#### Task 2.4.1: Analyze Differences
**Dependencies**: None
**Steps**:
```bash
# Compare @ghatana/code-editor vs @yappc/code-editor
diff -r platform/typescript/code-editor/src products/yappc/frontend/libs/code-editor/src

# Identify YAPPC-specific features (AST, LSP)
```
**Expected**: YAPPC has AST parsing and LSP client

---

#### Task 2.4.2: Extend Platform Code Editor
**Dependencies**: Task 2.4.1
**Steps**:
```bash
# Add optional AST/LSP modules to @ghatana/code-editor
mkdir -p platform/typescript/code-editor/src/ast
mkdir -p platform/typescript/code-editor/src/lsp

# Copy YAPPC-specific code
cp -r products/yappc/frontend/libs/code-editor/src/ast/* platform/typescript/code-editor/src/ast/
cp -r products/yappc/frontend/libs/code-editor/src/lsp/* platform/typescript/code-editor/src/lsp/

# Update exports
cat >> platform/typescript/code-editor/src/index.ts << 'EOF'
export * from './ast/index.js';
export * from './lsp/index.js';
EOF

# Update package.json to include new dependencies
```
**Validation**:
- [ ] AST functionality available
- [ ] LSP functionality available

---

#### Task 2.4.3: Delete @yappc/code-editor
**Dependencies**: Task 2.4.2
**Steps**:
```bash
# Update imports
find products/yappc -type f \( -name "*.ts" -o -name "*.tsx" \) \
  -exec sed -i '' 's/from "@yappc\/code-editor"/from "@ghatana\/code-editor"/g' {} \;

# Remove library
rm -rf products/yappc/frontend/libs/code-editor

# Update pnpm-workspace.yaml
```
**Validation**:
- [ ] No @yappc/code-editor references

---

### Task 2.5: Move Platform-Shell Atoms to @ghatana/state

**Priority**: P1 | **Estimated Effort**: 1 day | **Risk**: Low

#### Task 2.5.1: Move Atoms
**Dependencies**: None
**Steps**:
```bashn# Move atoms from platform-shell to state
mv platform/typescript/platform-shell/src/atoms platform/typescript/state/src/platform-shell-atoms

# Update @ghatana/state exports
cat >> platform/typescript/state/src/index.ts << 'EOF'
// Platform-shell atoms
export * from './platform-shell-atoms/index.js';
EOF
```
**Validation**:
- [ ] Atoms in @ghatana/state

---

#### Task 2.5.2: Update Platform-Shell
**Dependencies**: Task 2.5.1
**Steps**:
```bash
# Update platform-shell to import from state
cat > platform/typescript/platform-shell/src/index.ts << 'EOF'
// Re-export from @ghatana/state
export { authAtom, notificationAtom, tenantAtom } from '@ghatana/state';
// ... other exports
EOF
```
**Validation**:
- [ ] Platform-shell re-exports from state

---

### Task 2.6: Handle @ghatana/platform-shell

**Priority**: P2 | **Estimated Effort**: 0.5 days | **Risk**: Low

#### Task 2.6.1: Analyze Usage
**Dependencies**: None
**Steps**:
```bash
grep -r "@ghatana/platform-shell" platform/typescript products \
  --include="*.ts" --include="*.tsx" --include="*.json" | wc -l

# If minimal usage (< 5 files), delete
# If significant usage, keep but clarify purpose
```

---

#### Task 2.6.2: Execute Decision
**Dependencies**: Task 2.6.1
**Steps**:
```bash
# If deleting:
# 1. Move any useful components to appropriate libraries
# 2. Update imports
# 3. rm -rf platform/typescript/platform-shell
# 4. Update pnpm-workspace.yaml

# If keeping:
# 1. Clarify purpose in README
# 2. Ensure it doesn't duplicate other libraries
```
**Validation**:
- [ ] Decision executed
- [ ] No orphaned imports

---

### Task 2.7: Handle @ghatana/ui-integration

**Priority**: P2 | **Estimated Effort**: 0.5 days | **Risk**: Low

#### Task 2.7.1: Analyze Usage
**Dependencies**: None
**Steps**:
```bash
grep -r "@ghatana/ui-integration" platform/typescript products \
  --include="*.ts" --include="*.tsx" | wc -l
```

---

#### Task 2.7.2: Execute Decision
**Dependencies**: Task 2.7.1
**Steps**:
```bash
# Similar to Task 2.6.2
```
**Validation**:
- [ ] Decision executed

---

### Phase 2 Validation

```bash
# 1. YAPPC libraries split
grep -r "from '@yappc/core'" products/yappc --include="*.ts" | \
  grep -E "(auth|security|chat)" || echo "✓ YAPPC core split"

# 2. Code editor consolidated
grep -r "@yappc/code-editor" products/yappc 2>/dev/null || echo "✓ Code editor consolidated"

# 3. Platform-shell atoms moved
grep -r "from '@ghatana/platform-shell'" . | \
  grep -E "(authAtom|notificationAtom|tenantAtom)" || echo "✓ Platform atoms in state"

# 4. Build and test
pnpm -r build
pnpm -r test
```

---

## Phase 3: Platform Hardening (Weeks 5-6)

**Phase Goal**: Complete platform library suite, standardize builds

### Task 3.1: Complete @ghatana/forms Implementation

**Priority**: P1 | **Estimated Effort**: 3 days | **Risk**: Medium

#### Task 3.1.1: Implement Core Components
**Dependencies**: None
**Steps**:
```bash
# 1. Verify library structure exists
ls platform/typescript/forms/src/

# 2. Create core components if missing
mkdir -p platform/typescript/forms/src/components
mkdir -p platform/typescript/forms/src/hooks
mkdir -p platform/typescript/forms/src/validation

# 3. Implement Form.tsx
cat > platform/typescript/forms/src/components/Form.tsx << 'EOF'
import React from 'react';
import { useForm, FormProvider } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ZodSchema } from 'zod';

interface FormProps<T> {
  schema: ZodSchema<T>;
  onSubmit: (data: T) => void;
  children: React.ReactNode;
  defaultValues?: Partial<T>;
}

export function Form<T>({ schema, onSubmit, children, defaultValues }: FormProps<T>) {
  const methods = useForm<T>({
    resolver: zodResolver(schema),
    defaultValues,
  });

  return (
    <FormProvider {...methods}>
      <form onSubmit={methods.handleSubmit(onSubmit)}>
        {children}
      </form>
    </FormProvider>
  );
}
EOF

# 4. Implement FormField.tsx, FormError.tsx
# 5. Implement useField hook
# 6. Implement validation utilities
```
**Validation**:
- [ ] Form component works with Zod schemas
- [ ] FormField integrates with react-hook-form
- [ ] Validation errors display correctly

---

#### Task 3.1.2: Migrate Product Forms
**Dependencies**: Task 3.1.1
**Steps**:
```bash
# Find product-specific form implementations
grep -r "FormField\|useForm" products --include="*.tsx" | \
  grep -v "@ghatana/forms" > /tmp/product-forms.txt

# Update to use @ghatana/forms
# Replace custom implementations with platform components
```
**Validation**:
- [ ] Products using @ghatana/forms
- [ ] No duplicate form implementations

---

### Task 3.2: Complete @ghatana/data-grid Implementation

**Priority**: P1 | **Estimated Effort**: 3 days | **Risk**: Medium

#### Task 3.2.1: Implement Core Components
**Dependencies**: None
**Steps**:
```bash
# 1. Verify structure
ls platform/typescript/data-grid/src/

# 2. Create components using TanStack Table
# DataGrid.tsx - Main component
# Pagination.tsx - Pagination controls
# ColumnFilter.tsx - Filter UI
# ColumnSort.tsx - Sort UI
```
**Validation**:
- [ ] DataGrid renders with TanStack Table
- [ ] Pagination works
- [ ] Sorting works
- [ ] Filtering works

---

#### Task 3.2.2: Migrate Product Data Grids
**Dependencies**: Task 3.2.1
**Steps**:
```bash
# Find product data grids
grep -r "DataGrid\|react-table\|@tanstack/react-table" products \
  --include="*.tsx" | grep -v "@ghatana/data-grid" > /tmp/product-datagrids.txt

# Update to use @ghatana/data-grid
```
**Validation**:
- [ ] Products using @ghatana/data-grid
- [ ] No duplicate table implementations

---

### Task 3.3: Implement @ghatana/wizard

**Priority**: P1 | **Estimated Effort**: 2 days | **Risk**: Medium

#### Task 3.3.1: Create Library Structure
**Dependencies**: None
**Steps**:
```bash
# Create if doesn't exist
mkdir -p platform/typescript/wizard/src

# Create package.json
cat > platform/typescript/wizard/package.json << 'EOF'
{
  "name": "@ghatana/wizard",
  "version": "0.1.0",
  "description": "Wizard and stepper library for Ghatana platform",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "test": "vitest run"
  },
  "dependencies": {
    "@ghatana/design-system": "workspace:*",
    "@ghatana/platform-utils": "workspace:*",
    "zod": "^4.3.6"
  },
  "peerDependencies": {
    "react": "^19.2.4",
    "react-dom": "^19.2.4",
    "react-router": "^7.0.0"
  },
  "license": "MIT"
}
EOF

# Create components:
# - Wizard.tsx - Main wrapper
# - WizardStep.tsx - Step wrapper
# - WizardProgress.tsx - Progress indicator
# - WizardNavigation.tsx - Next/Back buttons
# - hooks/useWizard.ts - State management
```
**Validation**:
- [ ] Wizard component functional
- [ ] Step navigation works
- [ ] Progress indicator accurate

---

#### Task 3.3.2: Migrate Product Wizards
**Dependencies**: Task 3.3.1
**Steps**:
```bash
# Find product wizards
grep -r "Wizard\|Stepper\|ConfigurationWizard" products \
  --include="*.tsx" | grep -v "@ghatana/wizard" > /tmp/product-wizards.txt

# Update @yappc/initialization-ui to use @ghatana/wizard
```
**Validation**:
- [ ] YAPPC initialization uses @ghatana/wizard
- [ ] No duplicate wizard implementations

---

### Task 3.4: Split @ghatana/theme

**Priority**: P2 | **Estimated Effort**: 2 days | **Risk**: Low

#### Task 3.4.1: Create @ghatana/theme-core
**Dependencies**: None
**Steps**:
```bash
mkdir -p platform/typescript/theme/theme-core/src

# Move framework-agnostic code
# - Token definitions
# - Theme configuration objects
# - Type definitions
```
**Validation**:
- [ ] No React imports in theme-core

---

#### Task 3.4.2: Create @ghatana/theme-react
**Dependencies**: Task 3.4.1
**Steps**:
```bash
mkdir -p platform/typescript/theme/theme-react/src

# Move React-specific code
# - ThemeProvider
# - useTheme hook
# - React context
```
**Validation**:
- [ ] Depends on theme-core
- [ ] React types correct

---

#### Task 3.4.3: Update Original Theme
**Dependencies**: Tasks 3.4.1, 3.4.2
**Steps**:
```bash
# Make original theme a re-export layer
# Or deprecate and migrate consumers
```
**Validation**:
- [ ] Consumers can use theme-core directly
- [ ] React consumers use theme-react

---

### Task 3.5: Standardize Build Configurations

**Priority**: P1 | **Estimated Effort**: 2 days | **Risk**: Medium

#### Task 3.5.1: Audit Current Builds
**Dependencies**: None
**Steps**:
```bash
# Find all build scripts
grep -r '"build":' platform/typescript products --include="package.json" \
  | grep -v "tsc" > /tmp/non-tsc-builds.txt

# Expected: Some YAPPC libs using tsup
```

---

#### Task 3.5.2: Update to tsc
**Dependencies**: Task 3.5.1
**Steps**:
```bash
# For each non-tsc library:
# 1. Update package.json scripts:
#    "build": "tsc"
#    "dev": "tsc --watch"
#    "type-check": "tsc --noEmit"

# 2. Create/update tsconfig.json extending base

# 3. Remove tsup from devDependencies

# 4. Update exports if needed
```
**Libraries to Update** (from audit):
- `@yappc/core`
- `@yappc/ui`
- `@yappc/ai`
- Any other tsup users

**Validation**:
- [ ] All platform libraries use tsc
- [ ] All product libraries use tsc
- [ ] Builds succeed

---

#### Task 3.5.3: Verify tsconfig.base.json
**Dependencies**: None
**Steps**:
```bash
# Verify or create platform/typescript/tsconfig.base.json
cat platform/typescript/tsconfig.base.json

# Should have strict: true, ES2022 target, proper module resolution
```
**Validation**:
- [ ] All tsconfig.json extend base
- [ ] No divergent compiler options

---

### Task 3.6: Update @ghatana/design-system Domain Exports

**Priority**: P1 | **Estimated Effort**: 1 day | **Risk**: Low

#### Task 3.6.1: Remove Domain Exports
**Dependencies**: None
**Steps**:
```bash
# Edit platform/typescript/design-system/src/index.ts
# Remove:
# export * from './privacy';
# export * from './security';
# export * from './voice';
# export * from './nlp';
# export * from './selection';
# export * from './audit';
```
**Validation**:
- [ ] No domain exports in design-system
- [ ] All consumers updated to use domain-components

---

### Phase 3 Validation

```bash
# 1. Forms library complete
ls platform/typescript/forms/src/components/
[ -f platform/typescript/forms/src/components/Form.tsx ] && echo "✓ Form component exists"

# 2. Data grid complete
ls platform/typescript/data-grid/src/
[ -f platform/typescript/data-grid/src/DataGrid.tsx ] && echo "✓ DataGrid exists"

# 3. Wizard complete
ls platform/typescript/wizard/src/
[ -f platform/typescript/wizard/src/Wizard.tsx ] && echo "✓ Wizard exists"

# 4. Theme split
grep -r "@ghatana/theme-core\|@ghatana/theme-react" . && echo "✓ Theme split"

# 5. All builds use tsc
grep -r '"build":' platform/typescript products --include="package.json" | \
  grep -v "tsc" && echo "WARNING: Non-tsc builds found" || echo "✓ All tsc"

# 6. Build and test
pnpm -r build
pnpm -r test
```

---

## Phase 4: Governance (Weeks 7-8)

**Phase Goal**: Prevent future sprawl through process and automation

### Task 4.1: Enforce LIBRARY_GOVERNANCE.md

**Priority**: P1 | **Estimated Effort**: 1 day | **Risk**: Low

#### Task 4.1.1: Document Review Process
**Dependencies**: None
**Steps**:
```bash
# Update LIBRARY_GOVERNANCE.md with:
# - Library creation proposal template
# - Review checklist
# - Approval workflow
```
**Validation**:
- [ ] Governance document current
- [ ] Review process documented

---

### Task 4.2: Add Automated Checks

**Priority**: P2 | **Estimated Effort**: 2 days | **Risk**: Medium

#### Task 4.2.1: Library Size Check
**Dependencies**: None
**Steps**:
```bash
# Create script to check library size
# Flag libraries with > 50 exports as potential god libraries

cat > scripts/check-library-size.sh << 'EOF'
#!/bin/bash
for pkg in platform/typescript/*/products/*/libs/*; do
  if [ -f "$pkg/src/index.ts" ]; then
    count=$(grep -c "^export" "$pkg/src/index.ts" 2>/dev/null || echo 0)
    if [ "$count" -gt 50 ]; then
      echo "WARNING: $pkg has $count exports (potential god library)"
    fi
  fi
done
EOF

chmod +x scripts/check-library-size.sh
```
**Validation**:
- [ ] Script runs successfully
- [ ] Flags oversized libraries

---

#### Task 4.2.2: Dependency Direction Check
**Dependencies**: None
**Steps**:
```bash
# Create script to check for Platform->Product dependencies
cat > scripts/check-dependencies.sh << 'EOF'
#!/bin/bash
# Check for invalid dependencies in platform packages
for pkg in platform/typescript/*/package.json; do
  deps=$(cat "$pkg" | grep -o '"@[a-z-]*/' | sort | uniq)
  for dep in $deps; do
    if [[ "$dep" =~ ^"@dcmaar"|^"@yappc"|^"@flashit" ]]; then
      echo "VIOLATION: $pkg depends on product $dep"
    fi
  done
done
EOF

chmod +x scripts/check-dependencies.sh
```
**Validation**:
- [ ] Script detects invalid dependencies
- [ ] No false positives

---

#### Task 4.2.3: CI Integration
**Dependencies**: Tasks 4.2.1, 4.2.2
**Steps**:
```bash
# Add to .github/workflows/ci.yml or equivalent
# - Run library size check
# - Run dependency direction check
# - Fail build if violations found
```
**Validation**:
- [ ] Checks run in CI
- [ ] Build fails on violations

---

### Task 4.3: Document Canonical Ownership

**Priority**: P1 | **Estimated Effort**: 1 day | **Risk**: Low

#### Task 4.3.1: Create Ownership Reference
**Dependencies**: None
**Steps**:
```bash
# Create platform/typescript/CANONICAL_OWNERSHIP.md
# List all concepts and their canonical libraries
# Update copilot-instructions.md with this info
```
Already partially done in copilot-instructions.md Section 9.

---

### Task 4.4: Create Migration Guides

**Priority**: P2 | **Estimated Effort**: 2 days | **Risk**: Low

#### Task 4.4.1: Document Breaking Changes
**Dependencies**: All Phase 1-3 tasks
**Steps**:
```bash
# Create LIBRARY_MIGRATION_GUIDE.md
# For each library removed/split:
# - Old import pattern
# - New import pattern
# - Example migration
# - Common issues
```
**Validation**:
- [ ] Guide covers all major changes
- [ ] Examples are clear

---

#### Task 4.4.2: Update Documentation
**Dependencies**: Task 4.4.1
**Steps**:
```bash
# Update README files for:
# - New libraries
# - Changed libraries
# - Removed libraries (with migration path)
```
**Validation**:
- [ ] All library READMEs current

---

### Phase 4 Validation

```bash
# 1. Governance enforced
[ -f platform/typescript/LIBRARY_GOVERNANCE.md ] && echo "✓ Governance exists"

# 2. Automated checks in CI
[ -f .github/workflows/library-checks.yml ] && echo "✓ CI checks configured"

# 3. Canonical ownership documented
grep -q "Canonical Ownership" .windsurf/copilot-instructions.md && echo "✓ Ownership documented"

# 4. Migration guide exists
[ -f LIBRARY_MIGRATION_GUIDE.md ] && echo "✓ Migration guide exists"

# 5. All tests passing
pnpm test
```

---

## Final Project Validation

**Run after all phases complete**:

```bash
cd /Users/samujjwal/Development/ghatana

# 1. Full test suite
pnpm test

# 2. Type checking
pnpm -r type-check

# 3. Linting
pnpm -r lint

# 4. Build all
pnpm -r build

# 5. Library count
platform_count=$(find platform/typescript -name "package.json" -type f | wc -l)
product_count=$(find products -name "package.json" -type f | grep -c "libs/")
echo "Platform libraries: $platform_count"
echo "Product libraries: $product_count"
# Target: ~30 total (reduced from 44+)

# 6. No god libraries
./scripts/check-library-size.sh

# 7. No invalid dependencies
./scripts/check-dependencies.sh

# 8. No imports from removed libraries
grep -r "@yappc/state\|@yappc/auth\|@yappc/api\|@dcmaar/ui\|@tutorputor/ui" \
  platform/typescript products 2>/dev/null || echo "✓ No references to removed libs"

# 9. Canvas consumers using specific libs
grep -r "@ghatana/canvas" platform/typescript products | \
  grep -v "canvas-core\|canvas-react\|canvas-plugins\|canvas-chrome\|canvas-tools" || \
  echo "✓ Canvas properly split"

# 10. Generate summary
cat > LIBRARY_REFACTORING_SUMMARY.md << 'EOF'
# Library Refactoring Summary

## Completed Work

### Libraries Created
- @ghatana/canvas-core
- @ghatana/canvas-react
- @ghatana/canvas-plugins
- @ghatana/canvas-chrome
- @ghatana/canvas-tools
- @ghatana/accessibility (merged)
- @ghatana/forms
- @ghatana/data-grid
- @ghatana/wizard
- @ghatana/theme-core
- @ghatana/theme-react
- @yappc/chat
- @yappc/initialization-ui
- @yappc/development-ui
- @yappc/devsecops

### Libraries Removed
- @ghatana/canvas (split)
- @ghatana/accessibility-audit (merged)
- @ghatana/audit-components (merged)
- @yappc/state (merged into platform)
- @yappc/auth (merged into sso-client)
- @yappc/api (deleted)
- @yappc/code-editor (merged into platform)
- @dcmaar/ui (deleted)
- @yappc/ui (refactored)
- @tutorputor/ui (deleted)

### Key Metrics
- Original library count: 44+
- Final library count: ~30
- God libraries eliminated: 1 (@ghatana/canvas)
- Duplication eliminated: 3 accessibility libs, state libs, auth libs
- Thin wrappers removed: 3 product UI libs

## Test Results
- All tests: PASSING
- Type checking: PASSING
- Linting: PASSING
- Build: PASSING

## Breaking Changes
All changes are breaking. Migration guide available at LIBRARY_MIGRATION_GUIDE.md.
EOF

echo "Library refactoring complete!"
```

---

## Risk Mitigation

| Risk | Mitigation | Owner |
|------|------------|-------|
| Canvas split breaks consumers | Comprehensive consumer updates (Task 1.1.7) | Platform Team |
| State merge loses YAPPC-specific features | Careful atom analysis (Task 1.3.1) | YAPPC Team |
| Build changes break apps | Staged rollout, per-library validation | Platform Team |
| Product teams blocked during refactor | Feature branches, migration guides | Platform Team |
| Circular dependencies introduced | Automated dependency checks | Platform Team |

---

## Timeline Summary

| Phase | Duration | Key Deliverables |
|-------|----------|------------------|
| Phase 1 | Weeks 1-2 | Canvas split, accessibility merged, product wrappers deleted |
| Phase 2 | Weeks 3-4 | YAPPC libraries split, code editor consolidated |
| Phase 3 | Weeks 5-6 | Forms, data-grid, wizard complete, theme split |
| Phase 4 | Weeks 7-8 | Governance, automation, documentation |

**Total Duration**: 8 weeks
**Buffer**: 1 week for unforeseen issues

---

## Appendix: Task Dependencies Graph

```
Phase 1:
Task 1.1.1 (canvas-core) 
  → Task 1.1.2 (canvas-react)
    → Task 1.1.4 (canvas-chrome)
  → Task 1.1.3 (canvas-plugins)
  → Task 1.1.5 (canvas-tools)
    → Task 1.1.6 (update original canvas)
      → Task 1.1.7 (update consumers)

Task 1.2.1 (accessibility create)
  → Task 1.2.2 (remove accessibility-audit)
  → Task 1.2.3 (update audit-components)

Task 1.3.1 (analyze state)
  → Task 1.3.2 (migrate atoms)
    → Task 1.3.3 (update yappc-state)

Task 1.4.1 (analyze auth)
  → Task 1.4.2 (migrate auth)
    → Task 1.4.3 (delete yappc-auth)

Phase 2:
Task 2.1.1 (create chat)
  → Task 2.1.2 (update core)
    → Task 2.1.3 (update consumers)

Task 2.2.1 (create init-ui)
Task 2.2.2 (create dev-ui)
  → Task 2.2.3 (update yappc-ui)

Phase 3:
Task 3.1.1 (forms impl)
  → Task 3.1.2 (migrate forms)

Task 3.2.1 (data-grid impl)
  → Task 3.2.2 (migrate data-grids)

Task 3.3.1 (wizard impl)
  → Task 3.3.2 (migrate wizards)

Task 3.4.1 (theme-core)
  → Task 3.4.2 (theme-react)
    → Task 3.4.3 (update theme)
```
