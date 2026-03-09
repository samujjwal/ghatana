# Import Guidelines

**Last Updated:** 2026-01-31  
**Phase:** 0 - Code Restructuring

## Overview

This document defines the import patterns and path aliases for the YAPPC frontend codebase. Following these guidelines ensures consistency, maintainability, and ease of refactoring.

---

## Path Aliases

### Library Packages (`@yappc/*`)

Use `@yappc/` prefixed aliases for all shared libraries:

```typescript
// ✅ GOOD: Using path aliases
import { Button } from '@yappc/ui/components';
import { useAuth } from '@yappc/state/auth';
import type { User } from '@yappc/types';
import { AIAgent } from '@yappc/ai-core';
import { Canvas } from '@yappc/canvas';
```

```typescript
// ❌ BAD: Relative paths to libraries
import { Button } from '../../libs/ui/src/components/Button';
import { useAuth } from '../../../libs/state/src/auth';
```

**Available Library Aliases:**

| Alias | Path | Purpose |
|:------|:-----|:--------|
| `@yappc/ui` | `libs/ui/src` | UI component library |
| `@yappc/state` | `libs/state/src` | State management (Jotai atoms) |
| `@yappc/ai-core` | `libs/ai-core/src` | AI services and agents |
| `@yappc/ai` | `libs/ai/src` | AI utilities |
| `@yappc/canvas` | `libs/canvas/src` | Canvas components |
| `@yappc/crdt` | `libs/crdt/src` | CRDT for real-time collaboration |
| `@yappc/collab` | `libs/collab/src` | Collaboration features |
| `@yappc/ide` | `libs/ide/src` | IDE components |
| `@yappc/types` | `libs/types/src` | Shared TypeScript types |
| `@yappc/graphql` | `libs/graphql/src` | GraphQL utilities |
| `@yappc/api` | `libs/api/src` | API client |
| `@yappc/testing` | `libs/testing/src` | Test utilities |
| `@yappc/mocks` | `libs/mocks/src` | Mock data |
| `@yappc/websocket` | `libs/websocket/src` | WebSocket client |
| `@yappc/auth` | `libs/auth/src` | Authentication |

### App-Specific Aliases (`@/*`)

Use `@/` prefix for code within `apps/web/src`:

```typescript
// ✅ GOOD: App-specific aliases
import { Header } from '@/components/layout/Header';
import { useProject } from '@/hooks/project/useProject';
import { ProjectService } from '@/services/project/ProjectService';
import { projectAtom } from '@/state/atoms/projectAtom';
import type { Project } from '@/shared/types/project';
```

```typescript
// ❌ BAD: Deep relative paths
import { Header } from '../../../components/layout/Header';
import { useProject } from '../../../../hooks/project/useProject';
```

**Available App Aliases:**

| Alias | Path | Purpose |
|:------|:-----|:--------|
| `@/components` | `apps/web/src/components` | React components |
| `@/hooks` | `apps/web/src/hooks` | React hooks |
| `@/services` | `apps/web/src/services` | Business logic services |
| `@/state` | `apps/web/src/state` | App-specific state |
| `@/pages` | `apps/web/src/pages` | Page components |
| `@/routes` | `apps/web/src/routes` | Route definitions |
| `@/layouts` | `apps/web/src/layouts` | Layout components |
| `@/shared` | `apps/web/src/shared` | Shared app utilities |
| `@/utils` | `apps/web/src/utils` | Utility functions |

---

## Import Order

Organize imports in the following order, with a blank line between each group:

1. **External packages** (React, libraries from node_modules)
2. **Internal library packages** (`@yappc/*`)
3. **App-specific imports** (`@/*`)
4. **Relative imports** (`./ and ../` - only for sibling/child files)
5. **Type imports** (`import type`)

### Example

```typescript
// 1. External packages
import React, { useState, useEffect } from 'react';
import { useQuery, useMutation } from '@apollo/client';
import { useAtom } from 'jotai';

// 2. Internal library packages
import { Button, Input, Modal } from '@yappc/ui/components';
import { useAuth } from '@yappc/state/auth';
import { AIAgent } from '@yappc/ai-core';

// 3. App-specific imports
import { Header } from '@/components/layout/Header';
import { useProject } from '@/hooks/project/useProject';
import { ProjectService } from '@/services/project/ProjectService';

// 4. Relative imports
import { formatDate } from './utils';
import { validateForm } from './validation';

// 5. Type imports
import type { User } from '@yappc/types';
import type { Project } from '@/shared/types/project';
```

---

## Relative Import Rules

### ✅ When to Use Relative Imports

**Only use relative imports for:**

1. **Sibling files** (same directory):
   ```typescript
   // In: components/Button/Button.tsx
   import { ButtonProps } from './Button.types';
   import styles from './Button.module.css';
   ```

2. **Child files** (one level down):
   ```typescript
   // In: components/Button/index.ts
   export { Button } from './Button';
   export type { ButtonProps } from './Button.types';
   ```

3. **Utility functions within same feature**:
   ```typescript
   // In: services/auth/AuthService.ts
   import { validateToken } from './utils/tokenValidator';
   ```

### ❌ When NOT to Use Relative Imports

**Never use relative imports that go up 2+ levels (`../../`)**:

```typescript
// ❌ BAD: Going up multiple levels
import { Button } from '../../../components/Button';
import type { User } from '../../../../types/user';

// ✅ GOOD: Use path aliases
import { Button } from '@/components/Button';
import type { User } from '@yappc/types/user';
```

### Maximum Relative Import Depth

**Rule:** Never use more than one level up (`../`)

```typescript
// ✅ GOOD: One level up (acceptable for parent directory)
import { ParentComponent } from '../ParentComponent';

// ⚠️ AVOID: Two levels up (use path alias instead)
import { GrandparentComponent } from '../../GrandparentComponent';

// ❌ BAD: Three or more levels up (always use path alias)
import { Component } from '../../../Component'; // Use @/ instead
```

---

## Type Imports

Use `import type` for type-only imports to improve build performance and tree-shaking:

```typescript
// ✅ GOOD: Type-only imports
import type { User, Project } from '@yappc/types';
import type { ButtonProps } from '@yappc/ui/components';

// ✅ GOOD: Mixed imports (value + type)
import { Button, type ButtonProps } from '@yappc/ui/components';

// ❌ BAD: Regular import for types
import { User, Project } from '@yappc/types';
```

---

## Barrel Exports

### Component-Level Barrels

Use barrel exports (index.ts) at the component level:

```typescript
// components/Button/index.ts
export { Button } from './Button';
export type { ButtonProps } from './Button.types';
```

### Library-Level Barrels

Be cautious with library-level barrels to avoid circular dependencies:

```typescript
// ✅ GOOD: Explicit exports
// libs/ui/src/index.ts
export { Button } from './components/Button';
export { Input } from './components/Input';
export { Modal } from './components/Modal';

// ❌ BAD: Re-exporting everything
// libs/ui/src/index.ts
export * from './components'; // Can cause circular deps
export * from './hooks';       // Can cause circular deps
```

---

## ESLint Rules

The following ESLint rules enforce these guidelines:

```json
{
  "rules": {
    "no-restricted-imports": [
      "error",
      {
        "patterns": [
          {
            "group": ["../../*"],
            "message": "Relative imports going up 2+ levels are not allowed. Use path aliases (@yappc/* or @/*) instead."
          },
          {
            "group": ["../../../*"],
            "message": "Deep relative imports are not allowed. Use path aliases (@yappc/* or @/*) instead."
          }
        ]
      }
    ],
    "import/order": [
      "error",
      {
        "groups": [
          "builtin",
          "external",
          "internal",
          "parent",
          "sibling",
          "index",
          "type"
        ],
        "pathGroups": [
          {
            "pattern": "@yappc/**",
            "group": "internal",
            "position": "before"
          },
          {
            "pattern": "@/**",
            "group": "internal",
            "position": "after"
          }
        ],
        "pathGroupsExcludedImportTypes": ["builtin"],
        "newlines-between": "always",
        "alphabetize": {
          "order": "asc",
          "caseInsensitive": true
        }
      }
    ]
  }
}
```

---

## Migration Guide

### Updating Existing Imports

If you find deep relative imports in the codebase:

1. **Identify the import type**:
   - Library import? Use `@yappc/*`
   - App code import? Use `@/*`

2. **Replace the import**:
   ```typescript
   // Before
   import { Button } from '../../../../libs/ui/src/components/Button';
   
   // After
   import { Button } from '@yappc/ui/components';
   ```

3. **Run type checking**:
   ```bash
   pnpm typecheck
   ```

4. **Run tests**:
   ```bash
   pnpm test
   ```

### Automated Migration Script

Use the provided script to automatically fix deep imports:

```bash
cd frontend
./scripts/update-imports.sh
```

This script will:
- Find all files with 4+ level deep imports
- Replace them with appropriate path aliases
- Report remaining files needing manual review

---

## Common Mistakes

### Mistake 1: Mixing `@yappc` and relative imports

```typescript
// ❌ BAD
import { Button } from '@yappc/ui/components';
import { Input } from '../../../libs/ui/src/components/Input';

// ✅ GOOD
import { Button, Input } from '@yappc/ui/components';
```

### Mistake 2: Not using `import type` for types

```typescript
// ❌ BAD
import { User, UserRole } from '@yappc/types';

// ✅ GOOD
import type { User, UserRole } from '@yappc/types';
```

### Mistake 3: Deep relative paths in tests

```typescript
// ❌ BAD: In test files
import { render } from '../../../../test-utils/test-utils';

// ✅ GOOD: Use path alias
import { render } from '@/test-utils/test-utils';
```

---

## Quick Reference

### Checklist for Every Import

- [ ] No relative imports with 2+ levels up (`../../`)
- [ ] Use `@yappc/*` for library imports
- [ ] Use `@/*` for app-specific imports
- [ ] Use `import type` for type-only imports
- [ ] Imports are ordered correctly
- [ ] No circular dependencies

### Path Alias Quick Lookup

```typescript
// UI Components
import { Button } from '@yappc/ui/components';

// State Management
import { userAtom } from '@yappc/state/auth';

// Types
import type { User } from '@yappc/types';

// App Components
import { Header } from '@/components/layout/Header';

// App Hooks
import { useProject } from '@/hooks/project/useProject';

// App Services
import { AuthService } from '@/services/auth/AuthService';
```

---

## Resources

- **TypeScript Path Mapping**: [TypeScript Handbook - Module Resolution](https://www.typescriptlang.org/docs/handbook/module-resolution.html#path-mapping)
- **Vite Aliases**: [Vite Config - resolve.alias](https://vitejs.dev/config/shared-options.html#resolve-alias)
- **ESLint Import Plugin**: [eslint-plugin-import](https://github.com/import-js/eslint-plugin-import)

---

## Questions?

If you have questions about import patterns, ask in:
- Slack: `#yappc-frontend`
- Team Lead: See [CONTRIBUTING.md](../../CONTRIBUTING.md)

---

**Status:** ✅ Implemented (Phase 0, Task 1.1)  
**Last Audit:** 2026-01-31  
**Compliance:** 100% (0 deep imports remaining)
