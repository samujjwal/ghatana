# Library Refactoring Implementation Plan

**Objective**: Restructure frontend and TypeScript library ecosystem to achieve simplicity, reusability, and clear boundaries.

**Principles**:
- Keep code and structure simple - no over-engineering
- No backward compatibility - breaking changes are expected and acceptable
- Fix all references across the codebase
- Ensure all tests pass after each phase

**Estimated Timeline**: 8 weeks (4 phases × 2 weeks each)

---

## Phase 1: Critical Fixes (Weeks 1-2)

### 1.1 Split @ghatana/design-system

**Objective**: Split god library into focused libraries with clear boundaries.

#### Step 1.1.1: Create new library structure

```bash
# Create new library directories
mkdir -p platform/typescript/domain-components/src
mkdir -p platform/typescript/audit-components/src
```

#### Step 1.1.2: Create package.json for @ghatana/domain-components

```bash
# Create platform/typescript/domain-components/package.json
cat > platform/typescript/domain-components/package.json << 'EOF'
{
  "name": "@ghatana/domain-components",
  "sideEffects": false,
  "version": "0.1.0",
  "description": "Domain-specific components for Ghatana platform - privacy, security, voice, NLP, selection",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "types": "./dist/index.d.ts",
      "import": "./dist/index.js"
    },
    "./privacy": {
      "types": "./dist/privacy.d.ts",
      "import": "./dist/privacy.js"
    },
    "./security": {
      "types": "./dist/security.d.ts",
      "import": "./dist/security.js"
    },
    "./voice": {
      "types": "./dist/voice.d.ts",
      "import": "./dist/voice.js"
    },
    "./nlp": {
      "types": "./dist/nlp.d.ts",
      "import": "./dist/nlp.js"
    },
    "./selection": {
      "types": "./dist/selection.d.ts",
      "import": "./dist/selection.js"
    }
  },
  "files": ["dist", "README.md"],
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "clean": "rm -rf dist",
    "type-check": "tsc --noEmit",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "@ghatana/design-system": "workspace:*",
    "@ghatana/platform-utils": "workspace:*",
    "zod": "^4.3.6"
  },
  "peerDependencies": {
    "react": "^19.2.4",
    "react-dom": "^19.2.4"
  },
  "devDependencies": {
    "@types/react": "^19.2.14",
    "@types/react-dom": "^19.2.3",
    "typescript": "^6.0.2",
    "vitest": "^4.1.2"
  },
  "keywords": ["ghatana", "domain-components", "privacy", "security", "voice", "nlp"],
  "author": "Ghatana Team",
  "license": "MIT"
}
EOF
```

#### Step 1.1.3: Move domain-specific folders to new library

```bash
# Move folders from design-system to domain-components
mv platform/typescript/design-system/src/privacy platform/typescript/domain-components/src/
mv platform/typescript/design-system/src/security platform/typescript/domain-components/src/
mv platform/typescript/design-system/src/voice platform/typescript/domain-components/src/
mv platform/typescript/design-system/src/nlp platform/typescript/domain-components/src/
mv platform/typescript/design-system/src/selection platform/typescript/domain-components/src/
```

#### Step 1.1.4: Create index.ts for @ghatana/domain-components

```typescript
// platform/typescript/domain-components/src/index.ts
export * from './privacy';
export * from './security';
export * from './voice';
export * from './nlp';
export * from './selection';
```

#### Step 1.1.5: Create package.json for @ghatana/audit-components

```bash
cat > platform/typescript/audit-components/package.json << 'EOF'
{
  "name": "@ghatana/audit-components",
  "sideEffects": false,
  "version": "0.1.0",
  "description": "Audit and accessibility testing components for Ghatana platform",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "types": "./dist/index.d.ts",
      "import": "./dist/index.js"
    }
  },
  "files": ["dist", "README.md"],
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "clean": "rm -rf dist",
    "type-check": "tsc --noEmit",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "@ghatana/design-system": "workspace:*",
    "@ghatana/platform-utils": "workspace:*",
    "@axe-core/react": "^4.11.1",
    "axe-core": "^4.11.2"
  },
  "peerDependencies": {
    "react": "^19.2.4",
    "react-dom": "^19.2.4"
  },
  "devDependencies": {
    "@testing-library/react": "^16.3.0",
    "@testing-library/jest-dom": "^6.6.3",
    "@types/react": "^19.2.14",
    "@types/react-dom": "^19.2.3",
    "jsdom": "^26.1.0",
    "react": "^19.2.4",
    "react-dom": "^19.2.4",
    "typescript": "^6.0.2",
    "vitest": "^4.1.2"
  },
  "keywords": ["ghatana", "audit", "accessibility", "testing"],
  "author": "Ghatana Team",
  "license": "MIT"
}
EOF
```

#### Step 1.1.6: Move audit folder to new library

```bash
# Move audit from design-system to audit-components
mv platform/typescript/design-system/src/audit platform/typescript/audit-components/src/
```

#### Step 1.1.7: Merge accessibility-audit into audit-components

```bash
# Move accessibility-audit content into audit-components
cp -r platform/typescript/accessibility-audit/src/* platform/typescript/audit-components/src/
# Remove old accessibility-audit library after confirming migration
# rm -rf platform/typescript/accessibility-audit
```

#### Step 1.1.8: Update @ghatana/design-system index.ts

Remove domain-specific exports from `platform/typescript/design-system/src/index.ts`:

```typescript
// Remove these lines:
// export * from './audit';
// export * from './privacy';
// export * from './security';
// export * from './voice';
// export * from './nlp';
// export * from './selection';
```

#### Step 1.1.9: Update all consumer imports

```bash
# Find all files importing from design-system domain-specific exports
grep -r "from '@ghatana/design-system'" platform/typescript products --include="*.ts" --include="*.tsx" | grep -E "(privacy|security|voice|nlp|selection|audit)" > /tmp/design-system-imports.txt

# Update imports (example pattern - need to run manually for each file):
# Old: import { X } from '@ghatana/design-system'
# New: import { X } from '@ghatana/domain-components'
```

**Files to update** (based on audit):
- `platform/typescript/design-system/src/index.ts` - remove domain exports
- Any files importing privacy, security, voice, nlp, selection, audit from design-system
- Update to import from new libraries instead

#### Step 1.1.10: Build and test

```bash
# Build new libraries
cd platform/typescript/domain-components && pnpm build
cd platform/typescript/audit-components && pnpm build

# Build updated design-system
cd platform/typescript/design-system && pnpm build

# Run tests
cd platform/typescript/domain-components && pnpm test
cd platform/typescript/audit-components && pnpm test
cd platform/typescript/design-system && pnpm test

# Run full platform tests
cd platform/typescript && pnpm test
```

---

### 1.2 Simplify @ghatana/canvas

**Objective**: Reduce complexity, resolve naming conflicts, move app-specific implementations.

#### Step 1.2.1: Remove @ghatana/flow-canvas

```bash
# Check if flow-canvas is used anywhere
grep -r "@ghatana/flow-canvas" platform/typescript products --include="*.ts" --include="*.tsx" --include="package.json"

# If not used, remove it
rm -rf platform/typescript/canvas/flow-canvas

# If used, merge functionality into canvas and update all imports
```

#### Step 1.2.2: Resolve naming conflicts in canvas index.ts

Edit `platform/typescript/canvas/src/index.ts`:

```typescript
// Remove conflicting exports and keep canonical names only:
// Remove "Legacy names" section (lines 114-119)
// Keep only canonical names: screenToWorld, worldToScreen

// Remove duplicate exports:
// Keep only one version of each type/component
// Use explicit renaming where necessary to avoid conflicts
```

#### Step 1.2.3: Move application-specific implementations to product libraries

```bash
# Check for product-specific implementations mentioned in comments
# These should be moved to respective product libraries:
# - AEP: @aep/canvas (if exists)
# - Data-Cloud: @datacloud/canvas (if exists)
# - YAPPC: @yappc/canvas (if exists)

# For now, remove references to product-specific implementations from canvas index.ts
# Document that products should create their own canvas extensions
```

#### Step 1.2.4: Reduce export surface through sub-package exports

Edit `platform/typescript/canvas/src/index.ts`:

```typescript
// Group related exports and use sub-package exports where possible
// Example: instead of exporting 20 individual components, export them via:
// export * from './components';
// export * from './plugins';
// export * from './tools';
```

#### Step 1.2.5: Update all consumer imports

```bash
# Find all canvas imports
grep -r "from '@ghatana/canvas'" platform/typescript products --include="*.ts" --include="*.tsx" > /tmp/canvas-imports.txt

# Update imports that use legacy/conflicting names
# Update to use canonical names only
```

#### Step 1.2.6: Build and test

```bash
cd platform/typescript/canvas && pnpm build
cd platform/typescript/canvas && pnpm test

# Run full platform tests
cd platform/typescript && pnpm test
```

---

### 1.3 Centralize cn() utility

**Objective**: Remove all local cn() implementations, use canonical version from platform-utils.

#### Step 1.3.1: Verify canonical cn() exists

```bash
# Check platform-utils has cn() function
cat platform/typescript/foundation/platform-utils/src/cn.ts
```

#### Step 1.3.2: Find all local cn() implementations

```bash
# Find all cn.ts files
find platform/typescript products -name "cn.ts" -type f

# Expected locations to remove:
# - products/yappc/frontend/libs/yappc-ui/src/base/utils/cn.ts
# - products/yappc/frontend/libs/yappc-ui/src/components/base-ui/utils/cn.ts
# - products/yappc/frontend/libs/yappc-ui/src/components/navigation-ui/utils/cn.ts
# - products/yappc/frontend/libs/yappc-ui/src/components/utils/cn.ts
# - products/dcmaar/libs/typescript/dcmaar-ui/src/agent/utils/cn.ts
```

#### Step 1.3.3: Remove local cn() files

```bash
# Remove all local cn.ts files
rm products/yappc/frontend/libs/yappc-ui/src/base/utils/cn.ts
rm products/yappc/frontend/libs/yappc-ui/src/components/base-ui/utils/cn.ts
rm products/yappc/frontend/libs/yappc-ui/src/components/navigation-ui/utils/cn.ts
rm products/yappc/frontend/libs/yappc-ui/src/components/utils/cn.ts
rm products/dcmaar/libs/typescript/dcmaar-ui/src/agent/utils/cn.ts
```

#### Step 1.3.4: Update all cn() imports

```bash
# Find all cn imports
grep -r "from.*cn" platform/typescript products --include="*.ts" --include="*.tsx" > /tmp/cn-imports.txt

# Update imports to use canonical location:
# Old: import { cn } from './utils/cn'
# New: import { cn } from '@ghatana/platform-utils'

# Update files:
# - products/yappc/frontend/libs/yappc-ui/src/base/index.ts
# - products/yappc/frontend/libs/yappc-ui/src/components/base-ui/index.ts
# - products/yappc/frontend/libs/yappc-ui/src/components/navigation-ui/index.ts
# - products/dcmaar/libs/typescript/dcmaar-ui/src/agent/utils/index.ts
```

#### Step 1.3.5: Ensure platform-utils exports cn()

```bash
# Verify platform-utils exports cn
cat platform/typescript/foundation/platform-utils/src/index.ts
# Should have: export * from './cn';
```

#### Step 1.3.6: Build and test

```bash
cd platform/typescript/foundation/platform-utils && pnpm build

# Build affected libraries
cd products/yappc/frontend/libs/yappc-ui && pnpm build
cd products/dcmaar/libs/typescript/dcmaar-ui && pnpm build

# Run tests
cd platform/typescript && pnpm test
cd products/yappc/frontend && pnpm test
cd products/dcmaar/libs/typescript && pnpm test
```

---

### Phase 1 Completion Validation

```bash
# Run full test suite
cd /Users/samujjwal/Development/ghatana
pnpm test

# Check for any remaining import errors
grep -r "from '@ghatana/design-system'" platform/typescript products --include="*.ts" --include="*.tsx" | grep -E "(privacy|security|voice|nlp|selection|audit)" || echo "No domain-specific imports from design-system remaining"

# Check for any remaining local cn implementations
find platform/typescript products -name "cn.ts" -type f || echo "No local cn files remaining"

# Verify new libraries build
cd platform/typescript/domain-components && pnpm build && pnpm test
cd platform/typescript/audit-components && pnpm build && pnpm test
cd platform/typescript/design-system && pnpm build && pnpm test
cd platform/typescript/canvas && pnpm build && pnpm test
```

---

## Phase 2: Boundary/Reuse Cleanup (Weeks 3-4)

### 2.1 Consolidate State Management

**Objective**: Merge scattered atoms into @ghatana/state, establish clear ownership patterns.

#### Step 2.1.1: Move platform-shell atoms to @ghatana/state

```bash
# Move atoms from platform-shell to state
mv platform/typescript/platform-shell/src/atoms platform/typescript/state/src/platform-shell-atoms
```

Edit `platform/typescript/state/src/index.ts`:

```typescript
// Add platform-shell atom exports
export * from './platform-shell-atoms/authAtom';
export * from './platform-shell-atoms/notificationAtom';
export * from './platform-shell-atoms/tenantAtom';
```

#### Step 2.1.2: Move canvas atoms to @ghatana/state or keep in canvas

```bash
# Canvas atoms are canvas-specific and should stay in canvas
# Document this in canvas README
# Establish pattern: canvas-specific atoms stay in canvas library
```

#### Step 2.1.3: Update platform-shell to import from @ghatana/state

Edit `platform/typescript/platform-shell/src/index.ts`:

```typescript
// Remove local atom exports
// Import from @ghatana/state instead
export { isAuthenticatedAtom, isTokenExpiredAtom, currentUserEmailAtom } from '@ghatana/state';
export { unreadCountAtom, pushNotificationAtom, markReadAtom, markAllReadAtom } from '@ghatana/state';
export { tenantAtom, availableTenantsAtom, hasRealTenantAtom } from '@ghatana/state';
```

#### Step 2.1.4: Document state ownership patterns

Create `platform/typescript/state/OWNERSHIP.md`:

```markdown
# State Ownership Patterns

## Platform-Level Atoms
Location: `@ghatana/state`
- Authentication state
- Tenant selection
- Platform notifications
- Generic platform settings

## Canvas-Specific Atoms
Location: `@ghatana/canvas`
- Canvas viewport state
- Canvas selection state
- Canvas tool state
- Canvas layer state

## Product-Level Atoms
Location: Product libraries (e.g., `@yappc/state`)
- Product-specific business logic
- Product-specific UI state
- Product-specific configuration

## App-Level Atoms
Location: App code
- App-specific transient state
- App-specific UI state
- Component-level state
```

#### Step 2.1.5: Update all imports

```bash
# Find platform-shell atom imports
grep -r "from '@ghatana/platform-shell" platform/typescript products --include="*.ts" --include="*.tsx" | grep -E "(authAtom|notificationAtom|tenantAtom)" > /tmp/platform-shell-atom-imports.txt

# Update to import from @ghatana/state instead
```

#### Step 2.1.6: Remove or deprecate @ghatana/platform-shell atoms folder

```bash
# After confirming all imports updated, remove old atoms folder
rm -rf platform/typescript/platform-shell/src/atoms
```

#### Step 2.1.7: Build and test

```bash
cd platform/typescript/state && pnpm build && pnpm test
cd platform/typescript/platform-shell && pnpm build && pnpm test

# Run full platform tests
cd platform/typescript && pnpm test
```

---

### 2.2 Remove Config Library Duplications

**Objective**: Remove @dcmaar/config-presets and @yappc/config, use @ghatana/config instead.

#### Step 2.2.1: Analyze config-presets usage

```bash
# Find all imports of config-presets
grep -r "@dcmaar/config-presets" products/dcmaar --include="*.ts" --include="*.tsx" --include="package.json" > /tmp/config-presets-usage.txt
```

#### Step 2.2.2: Migrate config-presets to @ghatana/config extensions

If config-presets has product-specific presets:
- Move them to a new file in @ghatana/config: `src/presets/dcmaar.ts`
- Export from @ghatana/config

#### Step 2.2.3: Update all config-presets imports

```bash
# Update imports:
# Old: import { X } from '@dcmaar/config-presets'
# New: import { X } from '@ghatana/config'
```

#### Step 2.2.4: Remove @dcmaar/config-presets

```bash
# After confirming all imports updated
rm -rf products/dcmaar/libs/typescript/config-presets

# Remove from workspace
# Update pnpm-workspace.yaml if needed
```

#### Step 2.2.5: Analyze @yappc/config usage

```bash
# Find all imports of @yappc/config
grep -r "@yappc/config" products/yappc --include="*.ts" --include="*.tsx" --include="package.json" > /tmp/yappc-config-usage.txt
```

#### Step 2.2.6: Update all @yappc/config imports

```bash
# Update imports:
# Old: import { X } from '@yappc/config'
# New: import { X } from '@ghatana/config'
```

#### Step 2.2.7: Remove @yappc/config

```bash
# After confirming all imports updated
rm -rf products/yappc/frontend/libs/config

# Remove from workspace
# Update pnpm-workspace.yaml if needed
```

#### Step 2.2.8: Build and test

```bash
cd platform/typescript/config && pnpm build && pnpm test

# Build affected product libraries
cd products/dcmaar && pnpm build
cd products/yappc/frontend && pnpm build

# Run tests
cd products/dcmaar && pnpm test
cd products/yappc/frontend && pnpm test
```

---

### 2.3 Consolidate Code Editor Libraries

**Objective**: Merge @ghatana/code-editor and @yappc/code-editor into single library.

#### Step 2.3.1: Analyze differences between code editors

```bash
# Compare features
# @ghatana/code-editor: Monaco-based with lazy loading
# @yappc/code-editor: Monaco with AST parsing and LSP client

# Check if AST/LSP can be optional features
cat platform/typescript/code-editor/src/index.ts
cat products/yappc/frontend/libs/code-editor/src/index.ts
```

#### Step 2.3.2: Add AST/LSP as optional features to @ghatana/code-editor

```bash
# Create optional AST and LSP modules in platform code-editor
mkdir -p platform/typescript/code-editor/src/ast
mkdir -p platform/typescript/code-editor/src/lsp

# Copy AST/LSP code from yappc code-editor
cp -r products/yappc/frontend/libs/code-editor/src/ast/* platform/typescript/code-editor/src/ast/
cp -r products/yappc/frontend/libs/code-editor/src/lsp/* platform/typescript/code-editor/src/lsp/
```

Edit `platform/typescript/code-editor/src/index.ts`:

```typescript
// Add optional exports
export * from './ast';
export * from './lsp';
```

Update `platform/typescript/code-editor/package.json`:

```json
{
  "dependencies": {
    "react": "^19.2.4",
    "react-dom": "^19.2.4",
    "typescript": "^6.0.2",
    "vscode-languageserver-protocol": "^3.17.5"
  }
}
```

#### Step 2.3.3: Update all yappc code-editor imports

```bash
# Find all imports
grep -r "@yappc/code-editor" products/yappc --include="*.ts" --include="*.tsx" --include="package.json" > /tmp/yappc-code-editor-usage.txt

# Update imports:
# Old: import { X } from '@yappc/code-editor'
# New: import { X } from '@ghatana/code-editor'
```

#### Step 2.3.4: Remove @yappc/code-editor

```bash
# After confirming all imports updated
rm -rf products/yappc/frontend/libs/code-editor

# Remove from workspace
# Update pnpm-workspace.yaml if needed
```

#### Step 2.3.5: Build and test

```bash
cd platform/typescript/code-editor && pnpm build && pnpm test

# Build affected yappc libraries
cd products/yappc/frontend && pnpm build

# Run tests
cd products/yappc/frontend && pnpm test
```

---

### Phase 2 Completion Validation

```bash
# Run full test suite
cd /Users/samujjwal/Development/ghatana
pnpm test

# Verify no platform-shell atom imports remain
grep -r "from '@ghatana/platform-shell" platform/typescript products --include="*.ts" --include="*.tsx" | grep -E "(authAtom|notificationAtom|tenantAtom)" || echo "No platform-shell atom imports remaining"

# Verify no config library duplications
grep -r "@dcmaar/config-presets" products/dcmaar --include="*.ts" --include="*.tsx" || echo "No config-presets imports remaining"
grep -r "@yappc/config" products/yappc --include="*.ts" --include="*.tsx" || echo "No yappc/config imports remaining"

# Verify no yappc code-editor imports
grep -r "@yappc/code-editor" products/yappc --include="*.ts" --include="*.tsx" || echo "No yappc/code-editor imports remaining"

# Verify state library builds
cd platform/typescript/state && pnpm build && pnpm test
```

---

## Phase 3: Simplification/Consolidation (Weeks 5-6)

### 3.1 Split @yappc/core

**Objective**: Split into focused libraries (core, auth, chat).

#### Step 3.1.1: Create new library structures

```bash
mkdir -p products/yappc/frontend/libs/yappc-auth/src
mkdir -p products/yappc/frontend/libs/yappc-chat/src
```

#### Step 3.1.2: Create package.json for @yappc/auth

```bash
cat > products/yappc/frontend/libs/yappc-auth/package.json << 'EOF'
{
  "name": "@yappc/auth",
  "version": "1.0.0",
  "description": "YAPPC authentication and security library",
  "type": "module",
  "main": "./dist/index.js",
  "module": "./dist/index.mjs",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "import": "./dist/index.mjs",
      "require": "./dist/index.js",
      "types": "./dist/index.d.ts"
    }
  },
  "files": ["dist"],
  "scripts": {
    "build": "tsup",
    "dev": "tsup --watch",
    "test": "vitest",
    "test:coverage": "vitest --coverage",
    "lint": "eslint src --ext .ts,.tsx",
    "type-check": "tsc --noEmit"
  },
  "dependencies": {
    "@yappc/core": "workspace:*",
    "zod": "^4.3.6",
    "tslib": "^2.8.1"
  },
  "devDependencies": {
    "@types/react": "^19.2.14",
    "tsup": "^8.5.1",
    "typescript": "^6.0.2",
    "vitest": "^4.1.2"
  },
  "peerDependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0"
  },
  "keywords": ["yappc", "auth", "security"],
  "license": "Apache-2.0"
}
EOF
```

#### Step 3.1.3: Create package.json for @yappc/chat

```bash
cat > products/yappc/frontend/libs/yappc-chat/package.json << 'EOF'
{
  "name": "@yappc/chat",
  "version": "1.0.0",
  "description": "YAPPC chat and messaging library",
  "type": "module",
  "main": "./dist/index.js",
  "module": "./dist/index.mjs",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "import": "./dist/index.mjs",
      "require": "./dist/index.js",
      "types": "./dist/index.d.ts"
    }
  },
  "files": ["dist"],
  "scripts": {
    "build": "tsup",
    "dev": "tsup --watch",
    "test": "vitest",
    "test:coverage": "vitest --coverage",
    "lint": "eslint src --ext .ts,.tsx",
    "type-check": "tsc --noEmit"
  },
  "dependencies": {
    "@yappc/core": "workspace:*",
    "zod": "^4.3.6",
    "tslib": "^2.8.1"
  },
  "devDependencies": {
    "@types/react": "^19.2.14",
    "tsup": "^8.5.1",
    "typescript": "^6.0.2",
    "vitest": "^4.1.2"
  },
  "peerDependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0"
  },
  "keywords": ["yappc", "chat", "messaging"],
  "license": "Apache-2.0"
}
EOF
```

#### Step 3.1.4: Move auth and security to @yappc/auth

```bash
# Move auth and security folders from yappc-core to yappc-auth
mv products/yappc/frontend/libs/yappc-core/src/auth products/yappc/frontend/libs/yappc-auth/src/
mv products/yappc/frontend/libs/yappc-core/src/security products/yappc/frontend/libs/yappc-auth/src/
```

#### Step 3.1.5: Move chat to @yappc/chat

```bash
# Move chat folder from yappc-core to yappc-chat
mv products/yappc/frontend/libs/yappc-core/src/chat products/yappc/frontend/libs/yappc-chat/src/
```

#### Step 3.1.6: Remove testing from yappc-core

```bash
# Remove testing folder (use @ghatana/platform-testing instead)
rm -rf products/yappc/frontend/libs/yappc-core/src/testing
```

#### Step 3.1.7: Update @yappc/core index.ts

Edit `products/yappc/frontend/libs/yappc-core/src/index.ts`:

```typescript
// Remove auth, security, chat, testing exports
// Keep only utils, types, constants
```

#### Step 3.1.8: Create index files for new libraries

```typescript
// products/yappc/frontend/libs/yappc-auth/src/index.ts
export * from './auth';
export * from './security';

// products/yappc/frontend/libs/yappc-chat/src/index.ts
export * from './chat';
```

#### Step 3.1.9: Update all consumer imports

```bash
# Find auth/security imports from yappc-core
grep -r "from '@yappc/core'" products/yappc --include="*.ts" --include="*.tsx" | grep -E "(auth|security)" > /tmp/yappc-auth-imports.txt

# Update imports:
# Old: import { X } from '@yappc/core'
# New: import { X } from '@yappc/auth'

# Find chat imports from yappc-core
grep -r "from '@yappc/core'" products/yappc --include="*.ts" --include="*.tsx" | grep -E "(chat)" > /tmp/yappc-chat-imports.txt

# Update imports:
# Old: import { X } from '@yappc/core'
# New: import { X } from '@yappc/chat'

# Find testing imports from yappc-core
grep -r "from '@yappc/core'" products/yappc --include="*.ts" --include="*.tsx" | grep -E "(testing)" > /tmp/yappc-testing-imports.txt

# Update imports:
# Old: import { X } from '@yappc/core'
# New: import { X } from '@ghatana/platform-testing'
```

#### Step 3.1.10: Update package.json dependencies

```bash
# Update yappc-ai to depend on yappc-auth instead of yappc-core for auth
# Update yappc-ui to depend on yappc-auth and yappc-chat instead of yappc-core
```

#### Step 3.1.11: Build and test

```bash
cd products/yappc/frontend/libs/yappc-core && pnpm build && pnpm test
cd products/yappc/frontend/libs/yappc-auth && pnpm build && pnpm test
cd products/yappc/frontend/libs/yappc-chat && pnpm build && pnpm test

# Run full yappc tests
cd products/yappc/frontend && pnpm test
```

---

### 3.2 Split @yappc/ui

**Objective**: Split into focused libraries (ui, initialization-ui, development-ui).

#### Step 3.2.1: Create new library structures

```bash
mkdir -p products/yappc/frontend/libs/yappc-initialization-ui/src
mkdir -p products/yappc/frontend/libs/yappc-development-ui/src
```

#### Step 3.2.2: Create package.json for @yappc/initialization-ui

```bash
cat > products/yappc/frontend/libs/yappc-initialization-ui/package.json << 'EOF'
{
  "name": "@yappc/initialization-ui",
  "version": "1.0.0",
  "description": "YAPPC initialization-specific UI components",
  "type": "module",
  "main": "./dist/index.js",
  "module": "./dist/index.mjs",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "import": "./dist/index.mjs",
      "require": "./dist/index.js",
      "types": "./dist/index.d.ts"
    }
  },
  "files": ["dist"],
  "scripts": {
    "build": "tsup",
    "dev": "tsup --watch",
    "test": "vitest",
    "lint": "eslint src --ext .ts,.tsx",
    "type-check": "tsc --noEmit"
  },
  "dependencies": {
    "@ghatana/design-system": "workspace:*",
    "@ghatana/platform-utils": "workspace:*",
    "@yappc/core": "workspace:*",
    "zod": "^4.3.6",
    "clsx": "^2.1.1",
    "tslib": "^2.8.1"
  },
  "devDependencies": {
    "@types/react": "^19.2.14",
    "tsup": "^8.5.1",
    "typescript": "^6.0.2",
    "vitest": "^4.1.2"
  },
  "peerDependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0"
  },
  "keywords": ["yappc", "initialization", "ui"],
  "license": "Apache-2.0"
}
EOF
```

#### Step 3.2.3: Create package.json for @yappc/development-ui

```bash
cat > products/yappc/frontend/libs/yappc-development-ui/package.json << 'EOF'
{
  "name": "@yappc/development-ui",
  "version": "1.0.0",
  "description": "YAPPC development-specific UI components",
  "type": "module",
  "main": "./dist/index.js",
  "module": "./dist/index.mjs",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "import": "./dist/index.mjs",
      "require": "./dist/index.js",
      "types": "./dist/index.d.ts"
    }
  },
  "files": ["dist"],
  "scripts": {
    "build": "tsup",
    "dev": "tsup --watch",
    "test": "vitest",
    "lint": "eslint src --ext .ts,.tsx",
    "type-check": "tsc --noEmit"
  },
  "dependencies": {
    "@ghatana/design-system": "workspace:*",
    "@ghatana/platform-utils": "workspace:*",
    "@yappc/core": "workspace:*",
    "zod": "^4.3.6",
    "clsx": "^2.1.1",
    "tslib": "^2.8.1"
  },
  "devDependencies": {
    "@types/react": "^19.2.14",
    "tsup": "^8.5.1",
    "typescript": "^6.0.2",
    "vitest": "^4.1.2"
  },
  "peerDependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0"
  },
  "keywords": ["yappc", "development", "ui"],
  "license": "Apache-2.0"
}
EOF
```

#### Step 3.2.4: Move initialization-ui to new library

```bash
# Move initialization-ui components
mv products/yappc/frontend/libs/yappc-ui/src/components/initialization-ui products/yappc/frontend/libs/yappc-initialization-ui/src/
```

#### Step 3.2.5: Move development-ui to new library

```bash
# Move development-ui components
mv products/yappc/frontend/libs/yappc-ui/src/components/development-ui products/yappc/frontend/libs/yappc-development-ui/src/
```

#### Step 3.2.6: Update @yappc/ui to keep only generic components

Edit `products/yappc/frontend/libs/yappc-ui/src/index.ts`:

```typescript
// Remove initialization and development exports
// Keep only generic components (base, base-ui, navigation-ui)
```

#### Step 3.2.7: Create index files for new libraries

```typescript
// products/yappc/frontend/libs/yappc-initialization-ui/src/index.ts
export * from './initialization-ui';

// products/yappc/frontend/libs/yappc-development-ui/src/index.ts
export * from './development-ui';
```

#### Step 3.2.8: Update all consumer imports

```bash
# Find initialization-ui imports from yappc-ui
grep -r "from '@yappc/ui'" products/yappc --include="*.ts" --include="*.tsx" | grep -E "(ConfigurationWizard|InfrastructureForm|ProviderSelector|CostEstimator)" > /tmp/yappc-init-imports.txt

# Update imports:
# Old: import { X } from '@yappc/ui'
# New: import { X } from '@yappc/initialization-ui'

# Find development-ui imports from yappc-ui
grep -r "from '@yappc/ui'" products/yappc --include="*.ts" --include="*.tsx" | grep -E "(development)" > /tmp/yappc-dev-imports.txt

# Update imports:
# Old: import { X } from '@yappc/ui'
# New: import { X } from '@yappc/development-ui'
```

#### Step 3.2.9: Build and test

```bash
cd products/yappc/frontend/libs/yappc-ui && pnpm build && pnpm test
cd products/yappc/frontend/libs/yappc-initialization-ui && pnpm build && pnpm test
cd products/yappc/frontend/libs/yappc-development-ui && pnpm build && pnpm test

# Run full yappc tests
cd products/yappc/frontend && pnpm test
```

---

### 3.3 Split @yappc/api

**Objective**: Split into api and devsecops libraries.

#### Step 3.3.1: Create @yappc/devsecops library

```bash
mkdir -p products/yappc/frontend/libs/yappc-devsecops/src
```

#### Step 3.3.2: Create package.json for @yappc/devsecops

```bash
cat > products/yappc/frontend/libs/yappc-devsecops/package.json << 'EOF'
{
  "name": "@yappc/devsecops",
  "version": "1.0.0",
  "description": "YAPPC DevSecOps-specific API concerns",
  "type": "module",
  "main": "./src/index.ts",
  "types": "./src/index.ts",
  "exports": {
    ".": "./src/index.ts"
  },
  "scripts": {
    "type-check": "tsc --noEmit",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "@yappc/core": "workspace:*",
    "@ghatana/api": "workspace:*"
  },
  "devDependencies": {
    "typescript": "^6.0.2",
    "vitest": "^4.1.2"
  },
  "keywords": ["yappc", "devsecops", "api"],
  "license": "Apache-2.0"
}
EOF
```

#### Step 3.3.3: Move devsecops to new library

```bash
# Move devsecops folder from yappc-api to yappc-devsecops
mv products/yappc/frontend/libs/api/src/devsecops products/yappc/frontend/libs/yappc-devsecops/src/
```

#### Step 3.3.4: Update @yappc/api index.ts

Edit `products/yappc/frontend/libs/api/src/index.ts`:

```typescript
// Remove devsecops exports
// Keep only auth, ai, graphql, hooks
```

#### Step 3.3.5: Create index file for devsecops

```typescript
// products/yappc/frontend/libs/yappc-devsecops/src/index.ts
export * from './devsecops';
```

#### Step 3.3.6: Update all consumer imports

```bash
# Find devsecops imports from yappc-api
grep -r "from '@yappc/api'" products/yappc --include="*.ts" --include="*.tsx" | grep -E "(devsecops)" > /tmp/yappc-devsecops-imports.txt

# Update imports:
# Old: import { X } from '@yappc/api/devsecops'
# New: import { X } from '@yappc/devsecops'
```

#### Step 3.3.7: Build and test

```bash
cd products/yappc/frontend/libs/api && pnpm test
cd products/yappc/frontend/libs/yappc-devsecops && pnpm test

# Run full yappc tests
cd products/yappc/frontend && pnpm test
```

---

### 3.4 Remove or Clarify @ghatana/platform-shell

**Objective**: Remove if not widely used, or clarify purpose and keep shell components.

#### Step 3.4.1: Analyze platform-shell usage

```bash
# Find all imports of platform-shell
grep -r "@ghatana/platform-shell" platform/typescript products --include="*.ts" --include="*.tsx" --include="package.json" > /tmp/platform-shell-usage.txt
```

#### Step 3.4.2: Decision: Remove if not used

If platform-shell has minimal usage:
- Remove the library
- Move any useful components to appropriate location

#### Step 3.4.3: Execute removal (if minimal usage)

```bash
# After confirming minimal usage
rm -rf platform/typescript/platform-shell

# Remove from workspace
# Update pnpm-workspace.yaml if needed
```

#### Step 3.4.4: Update any remaining imports

```bash
# If any imports remain, update to use alternative libraries
```

---

### 3.5 Remove or Clarify @ghatana/ui-integration

**Objective**: Remove if not widely used, or clarify purpose and expand.

#### Step 3.5.1: Analyze ui-integration usage

```bash
# Find all imports of ui-integration
grep -r "@ghatana/ui-integration" platform/typescript products --include="*.ts" --include="*.tsx" --include="package.json" > /tmp/ui-integration-usage.txt
```

#### Step 3.5.2: Decision: Remove if not used

If ui-integration has minimal usage:
- Remove the library
- Move AI features to design-system or remove
- Move collaboration infrastructure to appropriate location

#### Step 3.5.3: Execute removal (if minimal usage)

```bash
# After confirming minimal usage
rm -rf platform/typescript/ui-integration

# Remove from workspace
# Update pnpm-workspace.yaml if needed
```

#### Step 3.5.4: Update any remaining imports

```bash
# If any imports remain, update to use alternative libraries
```

---

### Phase 3 Completion Validation

```bash
# Run full test suite
cd /Users/samujjwal/Development/ghatana
pnpm test

# Verify no yappc-core auth/security/chat imports
grep -r "from '@yappc/core'" products/yappc --include="*.ts" --include="*.tsx" | grep -E "(auth|security|chat|testing)" || echo "No domain-specific imports from yappc-core remaining"

# Verify yappc-ui split libraries build
cd products/yappc/frontend/libs/yappc-ui && pnpm build && pnpm test
cd products/yappc/frontend/libs/yappc-initialization-ui && pnpm build && pnpm test
cd products/yappc/frontend/libs/yappc-development-ui && pnpm build && pnpm test

# Verify yappc-api split libraries build
cd products/yappc/frontend/libs/api && pnpm test
cd products/yappc/frontend/libs/yappc-devsecops && pnpm test
```

---

## Phase 4: Long-Term Hardening (Weeks 7-8)

### 4.1 Create Shared Form Library

**Objective**: Create @ghatana/forms with comprehensive form patterns.

#### Step 4.1.1: Create @ghatana/forms library

```bash
mkdir -p platform/typescript/forms/src
```

#### Step 4.1.2: Create package.json

```bash
cat > platform/typescript/forms/package.json << 'EOF'
{
  "name": "@ghatana/forms",
  "sideEffects": false,
  "version": "0.1.0",
  "description": "Comprehensive form library for Ghatana platform",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "types": "./dist/index.d.ts",
      "import": "./dist/index.js"
    },
    "./hooks": {
      "types": "./dist/hooks.d.ts",
      "import": "./dist/hooks.js"
    },
    "./components": {
      "types": "./dist/components.d.ts",
      "import": "./dist/components.js"
    },
    "./validation": {
      "types": "./dist/validation.d.ts",
      "import": "./dist/validation.js"
    }
  },
  "files": ["dist", "README.md"],
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "clean": "rm -rf dist",
    "type-check": "tsc --noEmit",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "@ghatana/design-system": "workspace:*",
    "@ghatana/platform-utils": "workspace:*",
    "zod": "^4.3.6"
  },
  "peerDependencies": {
    "react": "^19.2.4",
    "react-dom": "^19.2.4",
    "react-hook-form": "^7.0.0"
  },
  "devDependencies": {
    "@types/react": "^19.2.14",
    "@types/react-dom": "^19.2.3",
    "typescript": "^6.0.2",
    "vitest": "^4.1.2"
  },
  "keywords": ["ghatana", "forms", "validation", "react-hook-form"],
  "author": "Ghatana Team",
  "license": "MIT"
}
EOF
```

#### Step 4.1.3: Implement form components

Create basic form components in `platform/typescript/forms/src/`:
- `Form.tsx` - Form wrapper
- `FormField.tsx` - Form field wrapper
- `FormError.tsx` - Error display
- `FormSuccess.tsx` - Success message
- `hooks/index.ts` - useForm, useField, useFormState
- `validation/index.ts` - Zod schema validation
- `components/index.ts` - Component exports

#### Step 4.1.4: Update product forms to use @ghatana/forms

```bash
# Find product-specific form implementations
grep -r "FormField\|FormWrapper" products --include="*.tsx" > /tmp/product-forms.txt

# Update to use @ghatana/forms instead
```

#### Step 4.1.5: Build and test

```bash
cd platform/typescript/forms && pnpm build && pnpm test

# Update affected products and test
```

---

### 4.2 Create Shared Data Grid Library

**Objective**: Create @ghatana/data-grid with pagination, sorting, filtering.

#### Step 4.2.1: Create @ghatana/data-grid library

```bash
mkdir -p platform/typescript/data-grid/src
```

#### Step 4.2.2: Create package.json

```bash
cat > platform/typescript/data-grid/package.json << 'EOF'
{
  "name": "@ghatana/data-grid",
  "sideEffects": false,
  "version": "0.1.0",
  "description": "Data grid with pagination, sorting, filtering for Ghatana platform",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "types": "./dist/index.d.ts",
      "import": "./dist/index.js"
    }
  },
  "files": ["dist", "README.md"],
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "clean": "rm -rf dist",
    "type-check": "tsc --noEmit",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "@ghatana/design-system": "workspace:*",
    "@ghatana/platform-utils": "workspace:*",
    "@tanstack/react-table": "^8.0.0"
  },
  "peerDependencies": {
    "react": "^19.2.4",
    "react-dom": "^19.2.4"
  },
  "devDependencies": {
    "@types/react": "^19.2.14",
    "@types/react-dom": "^19.2.3",
    "typescript": "^6.0.2",
    "vitest": "^4.1.2"
  },
  "keywords": ["ghatana", "data-grid", "table", "pagination"],
  "author": "Ghatana Team",
  "license": "MIT"
}
EOF
```

#### Step 4.2.3: Implement data grid components

Create data grid components using TanStack Table:
- `DataGrid.tsx` - Main data grid
- `Pagination.tsx` - Pagination controls
- `ColumnFilter.tsx` - Column filtering
- `ColumnSort.tsx` - Column sorting

#### Step 4.2.4: Update product data grids to use @ghatana/data-grid

```bash
# Find product-specific data grid implementations
grep -r "DataGrid\|Table.*pagination" products --include="*.tsx" > /tmp/product-datagrids.txt

# Update to use @ghatana/data-grid instead
```

#### Step 4.2.5: Build and test

```bash
cd platform/typescript/data-grid && pnpm build && pnpm test

# Update affected products and test
```

---

### 4.3 Create Shared Wizard Library

**Objective**: Create @ghatana/wizard with wizard/stepper patterns.

#### Step 4.3.1: Create @ghatana/wizard library

```bash
mkdir -p platform/typescript/wizard/src
```

#### Step 4.3.2: Create package.json

```bash
cat > platform/typescript/wizard/package.json << 'EOF'
{
  "name": "@ghatana/wizard",
  "sideEffects": false,
  "version": "0.1.0",
  "description": "Wizard and stepper library for Ghatana platform",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "types": "./dist/index.d.ts",
      "import": "./dist/index.js"
    }
  },
  "files": ["dist", "README.md"],
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "clean": "rm -rf dist",
    "type-check": "tsc --noEmit",
    "test": "vitest run",
    "test:watch": "vitest"
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
  "devDependencies": {
    "@types/react": "^19.2.14",
    "@types/react-dom": "^19.2.3",
    "typescript": "^6.0.2",
    "vitest": "^4.1.2"
  },
  "keywords": ["ghatana", "wizard", "stepper"],
  "author": "Ghatana Team",
  "license": "MIT"
}
EOF
```

#### Step 4.3.3: Implement wizard components

Create wizard components:
- `Wizard.tsx` - Main wizard wrapper
- `WizardStep.tsx` - Individual step
- `WizardProgress.tsx` - Progress indicator
- `WizardNavigation.tsx` - Next/Back buttons
- `hooks/index.ts` - useWizard, useWizardStep

#### Step 4.3.4: Update product wizards to use @ghatana/wizard

```bash
# Find product-specific wizard implementations
grep -r "ConfigurationWizard\|Wizard\|Stepper" products --include="*.tsx" > /tmp/product-wizards.txt

# Update to use @ghatana/wizard instead
# Specifically update yappc-initialization-ui
```

#### Step 4.3.5: Build and test

```bash
cd platform/typescript/wizard && pnpm build && pnpm test

# Update affected products and test
```

---

### 4.4 Standardize Build Configurations

**Objective**: Choose single build tool (tsc), standardize TypeScript configs.

#### Step 4.4.1: Audit current build configurations

```bash
# Find all tsconfig.json files
find platform/typescript products -name "tsconfig.json" -type f > /tmp/tsconfigs.txt

# Find all package.json build scripts
grep -r "\"build\":" platform/typescript products --include="package.json" > /tmp/build-scripts.txt
```

#### Step 4.4.2: Standardize on tsc for all libraries

Update all package.json build scripts to use tsc:

```json
{
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "clean": "rm -rf dist",
    "type-check": "tsc --noEmit"
  }
}
```

#### Step 4.4.3: Create base TypeScript configuration

Create `platform/typescript/tsconfig.base.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "allowJs": true,
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "declaration": true,
    "declarationMap": true,
    "sourceMap": true,
    "outDir": "./dist",
    "rootDir": "./src"
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist", "**/*.test.ts", "**/*.test.tsx"]
}
```

#### Step 4.4.4: Update all tsconfig.json to extend base

Update each library's tsconfig.json:

```json
{
  "extends": "../../tsconfig.base.json",
  "compilerOptions": {
    "outDir": "./dist",
    "rootDir": "./src"
  }
}
```

#### Step 4.4.5: Remove tsup usage

```bash
# Remove tsup from package.json where not needed
# Update to use tsc instead
```

#### Step 4.4.6: Build and test all libraries

```bash
# Build all platform libraries
cd platform/typescript
pnpm -r build

# Test all platform libraries
pnpm -r test

# Build all product libraries
cd products
pnpm -r build

# Test all product libraries
pnpm -r test
```

---

### 4.5 Establish Library Governance

**Objective**: Document criteria for creating new libraries, require review.

#### Step 4.5.1: Create library governance document

Create `platform/typescript/LIBRARY_GOVERNANCE.md`:

```markdown
# Library Governance

## When to Create a New Library

Create a new library when:

1. **Clear separation of concerns**: The library has a single, well-defined purpose
2. **Multiple consumers**: At least 2 different packages will use it
3. **No suitable existing library**: No existing library can accommodate the functionality
4. **Framework-agnostic**: The library is not tied to a specific framework unless necessary

## When NOT to Create a New Library

Do NOT create a new library when:

1. **Single consumer**: Only one package will use it
2. **Existing library suffices**: An existing library can be extended
3. **Unclear purpose**: The library's purpose is vague or mixed
4. **Temporary need**: The functionality is experimental or short-lived

## Library Creation Process

1. **Proposal**: Create a proposal document describing:
   - Library name and purpose
   - Intended consumers
   - Dependencies
   - API surface
   - Why existing libraries cannot be used

2. **Review**: Submit proposal for review to platform team

3. **Approval**: Get approval before implementation

4. **Implementation**: Follow platform library patterns:
   - Use tsc for building
   - Extend tsconfig.base.json
   - Include tests
   - Document public API

5. **Validation**: Ensure all tests pass before merge

## Library Ownership

- **Platform libraries**: Owned by platform team
- **Product libraries**: Owned by product team
- **Cross-cutting libraries**: Owned by platform team with product input

## Library Deprecation

When deprecating a library:

1. **Announce**: Communicate deprecation to all consumers
2. **Migration path**: Provide clear migration instructions
3. **Grace period**: Allow at least 2 weeks for migration
4. **Removal**: Remove after migration complete
```

#### Step 4.5.2: Update coding-instructions.md

Add library governance section to `coding-instructions.md`:

```markdown
## Library Creation

Before creating a new library:
1. Review LIBRARY_GOVERNANCE.md
2. Ensure criteria for new library are met
3. Submit proposal for review
4. Get approval before implementation

## Library Patterns

- Use tsc for building
- Extend tsconfig.base.json
- Include comprehensive tests
- Document public API
- Follow naming conventions
```

---

### Phase 4 Completion Validation

```bash
# Run full test suite
cd /Users/samujjwal/Development/ghatana
pnpm test

# Verify new shared libraries build
cd platform/typescript/forms && pnpm build && pnpm test
cd platform/typescript/data-grid && pnpm build && pnpm test
cd platform/typescript/wizard && pnpm build && pnpm test

# Verify all libraries use consistent build
grep -r "\"build\":" platform/typescript products --include="package.json" | grep -v "tsc" || echo "All libraries use tsc"

# Verify governance document exists
ls platform/typescript/LIBRARY_GOVERNANCE.md
```

---

## Final Validation

### Complete Test Suite

```bash
cd /Users/samujjwal/Development/ghatana

# Run all tests
pnpm test

# Run type checking
pnpm -r type-check

# Run linting
pnpm -r lint
```

### Verify Library Count

```bash
# Count platform TypeScript libraries
find platform/typescript -name "package.json" -type f | wc -l

# Count product TypeScript libraries
find products -name "package.json" -type f | grep -E "libs/" | wc -l

# Should see reduction from original 44+ libraries
```

### Verify No Circular Dependencies

```bash
# Use madge or similar tool to check for circular dependencies
pnpm exec madge --circular --extensions ts,tsx platform/typescript products
```

### Verify All Imports Updated

```bash
# Check for any remaining imports from removed libraries
grep -r "@ghatana/flow-canvas" platform/typescript products --include="*.ts" --include="*.tsx" || echo "No flow-canvas imports remaining"
grep -r "@dcmaar/config-presets" products/dcmaar --include="*.ts" --include="*.tsx" || echo "No config-presets imports remaining"
grep -r "@yappc/config" products/yappc --include="*.ts" --include="*.tsx" || echo "No yappc/config imports remaining"
grep -r "@yappc/code-editor" products/yappc --include="*.ts" --include="*.tsx" || echo "No yappc/code-editor imports remaining"
```

### Generate Migration Summary

Create summary document of all changes:

```bash
cat > /Users/samujjwal/Development/ghatana/LIBRARY_MIGRATION_SUMMARY.md << 'EOF'
# Library Migration Summary

## Libraries Created
- @ghatana/domain-components
- @ghatana/audit-components
- @ghatana/forms
- @ghatana/data-grid
- @ghatana/wizard
- @yappc/auth
- @yappc/chat
- @yappc-initialization-ui
- @yappc-development-ui
- @yappc-devsecops

## Libraries Removed
- @ghatana/flow-canvas
- @ghatana/accessibility-audit (merged into audit-components)
- @dcmaar/config-presets
- @yappc/config
- @yappc/code-editor (merged into @ghatana/code-editor)
- @ghatana/platform-shell (if not widely used)
- @ghatana/ui-integration (if not widely used)

## Libraries Split
- @ghatana/design-system → design-system, domain-components, audit-components
- @yappc/core → core, auth, chat
- @yappc/ui → ui, initialization-ui, development-ui
- @yappc/api → api, devsecops

## Libraries Consolidated
- cn() utility centralized in @ghatana/platform-utils
- State atoms consolidated in @ghatana/state
- Code editor consolidated in @ghatana/code-editor

## Breaking Changes
All changes are breaking - no backward compatibility maintained.
All imports have been updated across the codebase.
All tests have been updated and pass.

## Test Results
- Platform tests: PASSING
- Product tests: PASSING
- Type checking: PASSING
- Linting: PASSING
EOF
```

---

## Rollback Plan

If any phase causes critical issues:

1. **Stop current phase**
2. **Revert all changes in current phase**
3. **Identify root cause**
4. **Fix root cause**
5. **Retry phase**

To revert a phase:

```bash
# Use git to revert changes
git revert <commit-range>

# Or manually restore from backup
# (Ensure backups are taken before each phase)
```

---

## Success Criteria

The refactoring is successful when:

1. ✅ All tests pass
2. ✅ No circular dependencies
3. ✅ No imports from removed libraries
4. ✅ Library count reduced from 44+ to ~30
5. ✅ No god libraries (all libraries have single purpose)
6. ✅ Clear ownership patterns documented
7. ✅ Consistent build configurations
8. ✅ Governance process established
9. ✅ All imports updated across codebase
10. ✅ Type checking passes for all libraries
