# TypeScript Package Naming Standard

**Status**: ACTIVE  
**Version**: 1.0.0  
**Date**: March 26, 2026  
**Last Updated**: 2026-04-20 (post-audit Phase 3 remediation)

> **Authoritative Entry Point:** For the complete repository-wide engineering standards including package naming, see [`.github/copilot-instructions.md`](../../.github/copilot-instructions.md). This document provides historical context and migration guidance for package naming conventions.

---

## Canonical Package Names

All TypeScript modules use the `@ghatana/` scope with consistent naming:

> **Authoritative Source:** The complete canonical package registry is maintained in [LIBRARY_GOVERNANCE.md](./LIBRARY_GOVERNANCE.md). This table provides a quick reference.

| Module | Canonical Package Name | Status |
|--------|----------------------|--------|
| Design System | `@ghatana/design-system` | ✅ Active |
| Platform Utils | `@ghatana/platform-utils` | ✅ Active |
| Canvas | `@ghatana/canvas` | ✅ Active |
| Code Editor | `@ghatana/code-editor` | ✅ Active |
| Config | `@ghatana/config` | ✅ Active |
| State | `@ghatana/state` | ✅ Active |
| Forms | `@ghatana/forms` | ✅ Active |
| Data Grid | `@ghatana/data-grid` | ✅ Active |
| Wizard | `@ghatana/wizard` | ✅ Active |
| Charts | `@ghatana/charts` | ✅ Active |
| Tokens | `@ghatana/tokens` | ✅ Active |
| Theme | `@ghatana/theme` | ✅ Active |
| i18n | `@ghatana/i18n` | ✅ Active |
| Realtime | `@ghatana/realtime` | ✅ Active |
| Events | `@ghatana/events` | ✅ Active |
| API | `@ghatana/api` | ✅ Active |
| SSO Client | `@ghatana/sso-client` | ✅ Active |
| Domain Components | `@ghatana/domain-components` | ✅ Active |
| Accessibility | `@ghatana/accessibility` | ✅ Active |

---

## Deprecated Package Names

The following package names are **DEPRECATED** and should not be used:

> **Authoritative Source:** The complete deprecation list is maintained in [LIBRARY_GOVERNANCE.md](./LIBRARY_GOVERNANCE.md). Migrate consumers directly to the canonical package.

| Deprecated Package | Canonical Replacement | Migration |
|--------------------|-----------------------|-----------|
| `@ghatana/accessibility-audit` | `@ghatana/accessibility` | Replace import |
| `@ghatana/audit-components` | `@ghatana/accessibility` | Replace import |
| `@ghatana/canvas-core` | `@ghatana/canvas` or `@ghatana/canvas/core` | Replace import |
| `@ghatana/canvas-react` | `@ghatana/canvas` or `@ghatana/canvas/react` | Replace import |
| `@ghatana/canvas-plugins` | `@ghatana/canvas` or `@ghatana/canvas/plugins` | Replace import |
| `@ghatana/canvas-tools` | `@ghatana/canvas` or `@ghatana/canvas/tools` | Replace import |
| `@ghatana/canvas-chrome` | `@ghatana/canvas` or `@ghatana/canvas/chrome` | Replace import |

---

## Migration Guide

### For Product Teams

1. **Search for deprecated imports**:
   ```bash
   grep -r "@ghatana/accessibility-audit" src/
   grep -r "@ghatana/audit-components" src/
   grep -r "@ghatana/canvas-core" src/
   grep -r "@ghatana/canvas-react" src/
   grep -r "@ghatana/canvas-plugins" src/
   grep -r "@ghatana/canvas-tools" src/
   grep -r "@ghatana/canvas-chrome" src/
   ```

2. **Update package.json**:
   ```json
   {
     "dependencies": {
       "@ghatana/design-system": "^1.0.0",
       "@ghatana/platform-utils": "^1.0.0",
       "@ghatana/canvas": "^1.0.0",
       "@ghatana/accessibility": "^1.0.0"
     }
   }
   ```

3. **Update imports**:
   ```typescript
   // Before
   import { Button } from '@ghatana/ui';
   import { cn } from '@ghatana/utils';
   import { Canvas } from '@ghatana/canvas-core';
   import { AuditLog } from '@ghatana/accessibility-audit';

   // After
   import { Button } from '@ghatana/design-system';
   import { cn } from '@ghatana/platform-utils';
   import { Canvas } from '@ghatana/canvas';
   import { AuditLog } from '@ghatana/accessibility';
   ```

---

## Naming Conventions

### Package Naming Rules

1. **Use kebab-case**: `design-system`, `sso-client`
2. **Be descriptive**: Name should indicate purpose
3. **Avoid abbreviations**: Use `accessibility-audit` not `a11y-audit`
4. **No product prefixes**: Never use product-specific names

### Internal Module Structure

```
@ghatana/design-system/
├── atoms/           # Atomic components
├── molecules/       # Composite components
├── organisms/       # Complex components
├── hooks/           # Shared hooks
├── utils/           # Internal utilities
└── index.ts         # Public exports
```

---

## Backward Compatibility

### Fix-Forward Policy

Following platform governance, deprecated packages are removed without compatibility shims. Consumers must migrate directly to the canonical package. See [LIBRARY_GOVERNANCE.md](./LIBRARY_GOVERNANCE.md) for the complete fix-forward deprecation policy.

---

## Enforcement

### CI/CD Checks

Add to `.github/workflows/package-naming-check.yml`:

```yaml
name: Package Naming Check

on: [pull_request]

jobs:
  check-imports:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Check for deprecated package names
        run: |
          if grep -r "@ghatana/accessibility-audit" src/; then
            echo "ERROR: Found deprecated @ghatana/accessibility-audit imports"
            exit 1
          fi
          if grep -r "@ghatana/audit-components" src/; then
            echo "ERROR: Found deprecated @ghatana/audit-components imports"
            exit 1
          fi
          if grep -r "@ghatana/canvas-core" src/; then
            echo "ERROR: Found deprecated @ghatana/canvas-core imports"
            exit 1
          fi
```

### ESLint Rule

```javascript
// .eslintrc.js
module.exports = {
  rules: {
    'no-restricted-imports': ['error', {
      patterns: [
        '@ghatana/accessibility-audit',
        '@ghatana/audit-components',
        '@ghatana/canvas-core',
        '@ghatana/canvas-react',
        '@ghatana/canvas-plugins',
        '@ghatana/canvas-tools',
        '@ghatana/canvas-chrome'
      ]
    }]
  }
};
```

---

## Documentation Requirements

Every package must have:

1. **README.md** with canonical package name
2. **package.json** with correct name
3. **CHANGELOG.md** documenting name changes
4. **Migration guide** if renamed from previous version

---

## Contact

For questions about package naming:
- Platform Team: platform-team@ghatana.com
- Architecture Review: Submit ADR for new packages

---

**Last Updated**: 2026-04-20 (aligned with LIBRARY_GOVERNANCE.md)
