# Monorepo Naming Conventions & Standards

**Status:** Phase 3 - Standards Documentation  
**Created:** March 17, 2026  
**Effective Date:** Upon team approval  
**Enforcement:** Warning mode (Phase 4 will enable errors)

---

## Executive Summary

This document defines the naming conventions and organizational standards for the Ghatana monorepo. Following these standards ensures:
- **Clarity:** Immediate understanding of package purpose and scope
- **Consistency:** Predictable structure across all products
- **Tooling:** Automated linting and validation
- **Scalability:** Clear boundaries as the monorepo grows

---

## Package Naming Standards

### Platform Java Package Policy

**Canonical public namespace:** `com.ghatana.platform.*`

#### Rules
- New consumer-facing platform Java APIs must live under `com.ghatana.platform.*`.
- `com.ghatana.core.*` packages are transitional or legacy unless a migration document explicitly marks them canonical.
- Consumers must depend on contract packages such as `..port..`, `..api..`, and `..spi..` instead of concrete implementation packages.
- Concrete implementation packages such as `com.ghatana.platform.security.jwt` are composition-boundary details and must not be referenced directly from shared services or product code when a platform port exists.

#### Example

```java
// Preferred: depend on the platform port and factory seam
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;

JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(secret, expiryMs);

// Avoid: binding callers to the concrete implementation package
// import com.ghatana.platform.security.jwt.JwtTokenProvider;
```

### 1. Platform Libraries

**Scope:** `@ghatana/*`  
**Purpose:** Shared infrastructure used by multiple products  
**Location:** `platform/typescript/*`, `platform/java/*`

#### Examples

```
✅ @ghatana/design-system    # UI component primitives
✅ @ghatana/theme            # Theming system
✅ @ghatana/canvas           # Core canvas functionality
✅ @ghatana/http-server      # Java HTTP abstractions
✅ @ghatana/ai-integration   # AI/ML infrastructure
```

#### Rules
- Use kebab-case (lowercase with hyphens)
- Be descriptive but concise
- No product-specific names
- Must be genuinely reusable across products

---

### 2. Product Libraries

**Scope:** `@{product}/*`  
**Purpose:** Product-specific functionality  
**Location:** `products/{product}/*`

#### Current State (Non-Compliant)

```
❌ @ghatana/yappc-types       # Wrong scope
❌ @ghatana/yappc-ui          # Wrong scope
❌ @ghatana/yappc-canvas      # Wrong scope
❌ @ghatana/yappc-ai          # Wrong scope
```

#### Target State (Compliant)

```
✅ @yappc/core               # Types, utils, api, config
✅ @yappc/ui                 # UI components
✅ @yappc/canvas             # Canvas functionality
✅ @yappc/ai                 # AI features
✅ @yappc/ide                # IDE tools
✅ @yappc/testing            # Test utilities

✅ @data-cloud/ui            # Data Cloud UI
✅ @flashit/mobile          # Flashit mobile app
✅ @tutorputor/web          # TutorPutor web app
```

#### Rules
- Use product-specific scope (not @ghatana)
- Short, clear names (2-3 segments max)
- Prefer single-word names when possible
- Use kebab-case for multi-word names

---

### 3. Product-to-Product Dependencies

**Status:** Discouraged  
**Guideline:** Products should not depend on each other directly

#### Problematic Pattern

```
❌ @data-cloud/ui depends on @ghatana/yappc-code-editor
❌ @flashit/web depends on @yappc/ui
```

#### Preferred Pattern

```
✅ @data-cloud/ui depends on @ghatana/code-editor (platform)
✅ Products share through @ghatana/* abstractions
```

#### Exception Process
If product-to-product dependency is required:
1. Document the dependency in ADR
2. Get approval from both product leads
3. Add explicit architectural review gate
4. Mark as temporary with sunset date

---

## Library Consolidation Standards

### Target Library Count

| Product | Current | Target | Reduction |
|---------|---------|--------|-----------|
| YAPPC | 22 | 6 | 73% |
| Data Cloud | ~8 | 4 | 50% |
| Other products | varies | 3-5 | ~50% |

### Consolidation Principles

#### 1. Cohesion Over Coupling

**Good Consolidation:**
```
@yappc/core = types + utils + api + config
# All are foundational, no UI dependencies
```

**Bad Consolidation:**
```
❌ @yappc/everything = ui + canvas + ai + testing
# Mixes concerns, creates tight coupling
```

#### 2. Logical Grouping

**Group by Layer:**
- **Core:** Types, utilities, API clients, configuration
- **UI:** Components, hooks, theming
- **Canvas:** Drawing, collaboration, CRDT
- **IDE:** Editor, preview, build tools
- **AI:** ML models, agents, NLP
- **Testing:** Mocks, helpers, utilities

#### 3. Export Structure

```typescript
// @yappc/core - Clean subpath exports
export * from './types';
export * from './utils';
export * from './api';
export * from './config';

// Usage
import { User } from '@yappc/core/types';
import { formatDate } from '@yappc/core/utils';
import { apiClient } from '@yappc/core/api';
```

---

## File & Directory Naming

### 1. Source Files

```
✅ Button.tsx              # PascalCase for components
✅ useAuth.ts              # camelCase for hooks
✅ formatDate.ts           # camelCase for utilities
✅ types.ts                # camelCase for types

❌ button.tsx              # lowercase components
❌ UseAuth.ts              # PascalCase hooks
❌ format-date.ts          # kebab-case utilities
```

### 2. Directories

```
✅ components/             # lowercase plural
✅ hooks/                  # lowercase plural
✅ utils/                  # lowercase plural
✅ __tests__/             # double underscore for tests

❌ Components/             # PascalCase
❌ Hooks/                  # PascalCase
❌ test/                   # single underscore
```

### 3. Test Files

```
✅ Button.test.tsx         # Component.test.tsx
✅ useAuth.spec.ts         # Hook.spec.ts
✅ formatDate.test.ts      # Utility.test.ts
✅ e2e/canvas.spec.ts      # Feature e2e tests

❌ button-test.tsx         # hyphenated
❌ test-button.tsx         # reversed
```

---

## Import Standards

### 1. Import Organization

```typescript
// 1. Built-in (Node.js)
import fs from 'fs';
import path from 'path';

// 2. External dependencies
import React from 'react';
import { atom } from 'jotai';

// 3. Platform libraries (@ghatana/*)
import { Button } from '@ghatana/ui';
import { theme } from '@ghatana/theme';

// 4. Product libraries (@yappc/*)
import { useCanvasState } from '@yappc/canvas';
import type { User } from '@yappc/core/types';

// 5. Parent/sibling imports
import { useAuth } from '../hooks/useAuth';
import { utils } from './utils';
```

### 2. Import Types

```typescript
// ✅ Named imports (preferred)
import { Button, Card } from '@ghatana/ui';

// ✅ Type imports (explicit)
import type { CanvasNode } from '@yappc/core/types';

// ✅ Namespace imports (when needed)
import * as utils from '@yappc/core/utils';

// ✅ Dynamic imports (for code splitting)
const MonacoEditor = await import('@yappc/code-editor');

// ❌ Deep imports (avoid)
import { Button } from '@ghatana/ui/components/Button';
```

### 3. Migration Path for Imports

**Phase 1: Create New Packages**
```typescript
// New packages export same APIs
export * from '@yappc/core/types';
```

**Phase 2: Update Old Packages**
```typescript
// Old packages re-export from new
export * from '@yappc/core/types';  // Re-export
console.warn('@ghatana/yappc-types deprecated, use @yappc/core');
```

**Phase 3: Gradual Migration**
```typescript
// Developers update imports gradually
// Before
import { User } from '@ghatana/yappc-types';

// After
import { User } from '@yappc/core/types';
```

---

## Migration Timeline

### Phase 3: Documentation (Current - Week 1)
- [x] Create naming convention standards (this document)
- [ ] Add ESLint rules (warning mode)
- [ ] Create migration scripts
- [ ] Team communication

### Phase 4: Dependency Convergence (Week 2)
- [ ] Align dependency versions
- [ ] Update root package.json
- [ ] Validate builds pass

### Phase 5: Library Consolidation (Weeks 3-6)
- [ ] Create @yappc/* packages
- [ ] Add re-exports to old packages
- [ ] Deprecate old packages

### Phase 6: Import Migration (Weeks 7-10)
- [ ] Gradual import updates
- [ ] ESLint rules to error mode
- [ ] Remove deprecated packages

---

## ESLint Configuration

### Warning Mode (Phase 3)

```javascript
// .eslintrc.js
module.exports = {
  rules: {
    // Naming convention warnings
    'import/no-restricted-paths': ['warn', {
      zones: [
        // Discourage product-to-product imports
        {
          target: './products/data-cloud',
          from: './products/yappc',
          message: 'Products should not depend on each other. Use @ghatana/* abstractions.'
        }
      ]
    }],
    
    // Import order
    'import/order': ['warn', {
      groups: [
        'builtin',
        'external',
        'internal',
        'parent',
        'sibling',
        'index'
      ],
      pathGroups: [
        {
          pattern: '@ghatana/**',
          group: 'internal',
          position: 'before'
        },
        {
          pattern: '@yappc/**',
          group: 'internal',
          position: 'before'
        },
        {
          pattern: '@data-cloud/**',
          group: 'internal',
          position: 'before'
        }
      ],
      alphabetize: {
        order: 'asc',
        caseInsensitive: true
      }
    }]
  }
};
```

### Error Mode (Phase 6)

```javascript
// Change 'warn' to 'error' after migration
'import/no-restricted-paths': ['error', { ... }],
'import/order': ['error', { ... }],
```

---

## Exceptions & Edge Cases

### 1. Legacy Packages

Some packages are grandfathered in:
- `@ghatana/yappc-*` packages (migration in progress)
- Third-party wrappers (`@ghatana/monaco-editor` if exists)

### 2. Build Tools

Build configuration packages may use different naming:
```
✅ @yappc/eslint-config      # ESLint configuration
✅ @yappc/tsconfig           # TypeScript configuration
✅ @yappc/vite-config         # Vite configuration
```

### 3. Application Packages

Applications follow different rules:
```
✅ @yappc/web-app            # Web application
✅ @yappc/api                # API server
✅ @flashit/mobile-app       # Mobile application
```

---

## Validation & Tooling

### Automated Checks

1. **ESLint:** Real-time in IDE and CI
2. **CI Workflow:** `governance-checks.yml` naming convention check
3. **Pre-commit Hook:** Block commits with violations (Phase 6)

### Manual Review Checklist

When creating new packages:
- [ ] Scope follows `@{owner}/*` pattern
- [ ] Name is kebab-case
- [ ] No product dependencies in platform packages
- [ ] Documented in ADR if exception needed

---

## Communication Plan

### Week 1: Announcement
- Slack announcement with link to this document
- Team meeting to discuss standards
- Q&A session for edge cases

### Week 2-3: Grace Period
- ESLint rules in warning mode
- No blocking of builds
- Support for questions

### Week 4+: Enforcement
- CI checks must pass
- PRs blocked on violations
- Migration progress tracked

---

## FAQ

**Q: Can I create a new @ghatana/yappc-* package?**  
A: No. Use @yappc/* scope for all new YAPPC packages.

**Q: What if I need to share code between YAPPC and Data Cloud?**  
A: Extract to @ghatana/* platform library first.

**Q: How long is the migration period?**  
A: 10 weeks total (6 weeks consolidation + 4 weeks import migration)

**Q: Will old packages break during migration?**  
A: No. Re-exports ensure backward compatibility.

**Q: Can I migrate my imports early?**  
A: Yes! Once @yappc/* packages are published, early migration is encouraged.

---

## Related Documents

- [Governance Implementation Plan](../GOVERNANCE_IMPLEMENTATION_PLAN.md)
- [Library Dependency Matrix](../LIBRARY_DEPENDENCY_MATRIX.md)
- [Import Pattern Analysis](../IMPORT_PATTERN_ANALYSIS.md)
- [Architecture Decision Records](../architecture/)

---

**Document Status:** Draft for Review  
**Approval Required:** Yes  
**Effective Date:** TBD (after approval)
