# Utility Naming Conventions

## Overview

This document defines naming conventions for utility classes and packages across the Ghatana platform. Consistent naming improves code discoverability and maintainability.

## Java Utility Naming

### Class Naming

All utility classes should follow these conventions:

1. **Suffix with "Utils"** - All utility classes must end with "Utils"
   - ✅ `JsonUtils`, `StringUtils`, `CollectionUtils`
   - ❌ `JsonHelper`, `StringUtil`, `Collections`

2. **Descriptive Prefix** - Use clear, descriptive prefixes
   - ✅ `DateTimeUtils`, `ValidationUtils`, `SecurityUtils`
   - ❌ `DTUtils`, `ValUtils`, `SecUtils`

3. **Domain-Specific Prefixes** - Include domain context when needed
   - ✅ `HttpTestUtils`, `DatabaseTestUtils`, `AudioVideoTestUtils`
   - ❌ `TestUtils`, `DbUtils`, `AVUtils`

### Package Organization

Utility classes should be organized by domain:

```
platform/java/
├── core/src/main/java/com/ghatana/platform/core/
│   ├── util/                    # Core utilities
│   │   ├── JsonUtils.java
│   │   ├── StringUtils.java
│   │   ├── CollectionUtils.java
│   │   ├── DateTimeUtils.java
│   │   └── ValidationUtils.java
│   ├── client/                  # Client utilities
│   └── validation/              # Validation framework
│
├── http/src/main/java/com/ghatana/platform/http/
│   └── util/                    # HTTP-specific utilities
│
├── security/src/main/java/com/ghatana/platform/security/
│   ├── SecurityUtils.java       # General security utilities
│   └── auth/
│       └── AuthenticationUtils.java
│
└── testing/src/main/java/com/ghatana/platform/testing/
    └── utils/                   # Test utilities
        ├── ConfigTestUtils.java
        ├── ServiceTestUtils.java
        └── DatabaseTestUtils.java
```

### Utility Class Structure

All utility classes should follow this structure:

```java
package com.ghatana.platform.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * [Brief description of utility purpose]
 * 
 * <p>[Detailed description with usage guidelines]</p>
 * 
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Example code here
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose [Purpose description]
 * @doc.layer [core|platform|service]
 * @doc.pattern Utility
 */
public final class MyUtils {

    private MyUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * [Method description]
     * 
     * @param param parameter description
     * @return return value description
     */
    @NotNull
    public static ReturnType methodName(@NotNull ParamType param) {
        // Implementation
    }
}
```

### Key Requirements

1. **Final class** - Utility classes must be `final`
2. **Private constructor** - Prevent instantiation
3. **Static methods** - All methods must be `static`
4. **Null annotations** - Use `@NotNull` and `@Nullable`
5. **Javadoc** - Complete documentation with examples
6. **Thread-safe** - All utilities must be thread-safe

## TypeScript Utility Naming

### Package Naming

TypeScript utilities follow a different convention:

1. **Descriptive package names** - Use full words, not abbreviations
   - ✅ `@ghatana/platform-utils`, `@ghatana/design-system`
   - ❌ `@ghatana/utils`, `@ghatana/ds`

2. **Scope prefix** - Always use `@ghatana/` scope
   - ✅ `@ghatana/platform-utils`
   - ❌ `platform-utils`

3. **Hyphenated names** - Use hyphens for multi-word names
   - ✅ `platform-utils`, `design-system`
   - ❌ `platformUtils`, `designSystem`

### File Naming

TypeScript utility files should be:

1. **Lowercase with hyphens** - For multi-word files
   - ✅ `use-media-query.ts`, `format-date.ts`
   - ❌ `useMediaQuery.ts`, `formatDate.ts`

2. **Descriptive names** - Clear purpose from filename
   - ✅ `accessibility.ts`, `responsive.ts`
   - ❌ `a11y.ts`, `resp.ts`

### Export Organization

```typescript
// platform-utils/src/index.ts
export { cn } from './cn';
export { formatDate, formatTime } from './formatters';
export { useMediaQuery } from './responsive';
export type { Breakpoint } from './responsive';
```

## Utility Discovery Guide

### Finding the Right Utility

1. **Core utilities** - Check `platform/java/core/src/main/java/com/ghatana/platform/core/util/`
   - String manipulation → `StringUtils`
   - JSON processing → `JsonUtils`
   - Collections → `CollectionUtils`
   - Date/time → `DateTimeUtils`
   - Validation → `ValidationUtils`

2. **Domain utilities** - Check domain-specific packages
   - HTTP → `platform/java/http/`
   - Security → `platform/java/security/`
   - Database → `platform/java/database/`
   - Testing → `platform/java/testing/`

3. **TypeScript utilities** - Check `platform/typescript/foundation/platform-utils/`
   - Class names → `cn`
   - Formatting → `formatters`
   - Responsive → `responsive`
   - Accessibility → `accessibility`

### When to Create a New Utility

Create a new utility class when:
- ✅ Logic is used in 3+ places
- ✅ Logic is stateless and reusable
- ✅ Logic is domain-agnostic or domain-specific
- ✅ Logic has no side effects

Do NOT create a utility class when:
- ❌ Logic is used only once
- ❌ Logic requires state management
- ❌ Logic has side effects
- ❌ Logic is better as a service

## Migration Guide

### Deprecated Utilities

When deprecating a utility:

1. Add `@Deprecated` annotation
2. Add Javadoc explaining replacement
3. Update all usages
4. Keep for 2 releases
5. Remove in major version

Example:
```java
/**
 * @deprecated Use {@link StringUtils#isBlank(String)} instead.
 * This method will be removed in version 2.0.
 */
@Deprecated(since = "1.5", forRemoval = true)
public static boolean isEmpty(String str) {
    return StringUtils.isBlank(str);
}
```

### TypeScript Package Migration

For deprecated TypeScript packages:

1. Update package.json with deprecation notice
2. Add migration guide to README
3. Update all imports in codebase
4. Remove package after migration complete

Example package.json:
```json
{
  "name": "@ghatana/utils",
  "deprecated": "This package is deprecated. Use @ghatana/platform-utils instead.",
  "description": "⚠️ DEPRECATED — Use @ghatana/platform-utils directly."
}
```

## Code Review Checklist

When reviewing utility code, verify:

- [ ] Class name ends with "Utils"
- [ ] Class is `final`
- [ ] Constructor is `private`
- [ ] All methods are `static`
- [ ] Null annotations are present
- [ ] Javadoc is complete with examples
- [ ] Thread-safety is guaranteed
- [ ] No duplicate utilities exist
- [ ] Package location is correct
- [ ] Tests are comprehensive

## Examples

### Good Utility Class

```java
package com.ghatana.platform.core.util;

import org.jetbrains.annotations.NotNull;

/**
 * Email validation utilities.
 */
public final class EmailUtils {

    private EmailUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Validate email format.
     */
    @NotNull
    public static boolean isValidEmail(@NotNull String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
}
```

### Bad Utility Class

```java
// ❌ Not final
// ❌ No private constructor
// ❌ No null annotations
// ❌ No Javadoc
public class EmailHelper {
    public static boolean validate(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
}
```

## References

- [Java Utility Classes Best Practices](https://docs.oracle.com/javase/tutorial/java/javaOO/classvars.html)
- [TypeScript Module Best Practices](https://www.typescriptlang.org/docs/handbook/modules.html)
- [Platform Architecture Guidelines](../docs/PLATFORM_ARCHITECTURE.md)
