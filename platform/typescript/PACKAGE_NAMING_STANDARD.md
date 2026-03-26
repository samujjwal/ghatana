# TypeScript Package Naming Standard

**Status**: ACTIVE  
**Version**: 1.0.0  
**Date**: March 26, 2026

---

## Canonical Package Names

All TypeScript modules use the `@ghatana/` scope with consistent naming:

| Module | Canonical Package Name | Status |
|--------|----------------------|--------|
| Design System | `@ghatana/design-system` | ✅ Standard |
| Charts | `@ghatana/charts` | ✅ Standard |
| Canvas | `@ghatana/canvas` | ✅ Standard |
| Foundation | `@ghatana/foundation` | ✅ Standard |
| Realtime | `@ghatana/realtime` | ✅ Standard |
| Theme | `@ghatana/theme` | ✅ Standard |
| Tokens | `@ghatana/tokens` | ✅ Standard |
| i18n | `@ghatana/i18n` | ✅ Standard |
| API | `@ghatana/api` | ✅ Standard |
| SSO Client | `@ghatana/sso-client` | ✅ Standard |
| Platform Shell | `@ghatana/platform-shell` | ✅ Standard |
| UI Integration | `@ghatana/ui-integration` | ✅ Standard |
| Accessibility Audit | `@ghatana/accessibility-audit` | ✅ Standard |

---

## Deprecated Package Names

The following package names are **DEPRECATED** and should not be used:

| Deprecated Name | Use Instead | Removal Date |
|----------------|-------------|--------------|
| `@ghatana/ui` | `@ghatana/design-system` | v3.0.0 |
| `@ghatana/utils` | `@ghatana/foundation` | v3.0.0 |
| `@ghatana/dcmaar-*` | Canonical names above | v3.0.0 |
| `@ghatana/yappc-*` | Canonical names above | v3.0.0 |

---

## Migration Guide

### For Product Teams

1. **Search for deprecated imports**:
   ```bash
   grep -r "@ghatana/ui" src/
   grep -r "@ghatana/utils" src/
   grep -r "@ghatana/dcmaar" src/
   grep -r "@ghatana/yappc" src/
   ```

2. **Update package.json**:
   ```json
   {
     "dependencies": {
       "@ghatana/design-system": "^1.0.0",
       "@ghatana/foundation": "^1.0.0"
     }
   }
   ```

3. **Update imports**:
   ```typescript
   // Before
   import { Button } from '@ghatana/ui';
   import { cn } from '@ghatana/utils';
   
   // After
   import { Button } from '@ghatana/design-system';
   import { cn } from '@ghatana/foundation';
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

### Deprecation Window

- **Deprecate in version N**: Add deprecation warnings
- **Remove in version N+2**: Two major versions later

### Example Timeline

- v1.0.0: Introduce `@ghatana/design-system`
- v1.5.0: Deprecate `@ghatana/ui` with warnings
- v2.0.0: Continue support with warnings
- v3.0.0: Remove `@ghatana/ui` completely

---

## Package Aliases (Temporary)

For backward compatibility, package aliases are configured in `package.json`:

```json
{
  "name": "@ghatana/ui",
  "version": "1.5.0",
  "deprecated": "Use @ghatana/design-system instead",
  "dependencies": {
    "@ghatana/design-system": "^1.0.0"
  }
}
```

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
          if grep -r "@ghatana/ui" src/; then
            echo "ERROR: Found deprecated @ghatana/ui imports"
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
        '@ghatana/ui',
        '@ghatana/utils',
        '@ghatana/dcmaar-*',
        '@ghatana/yappc-*'
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

**Last Updated**: March 26, 2026  
**Next Review**: June 2026
