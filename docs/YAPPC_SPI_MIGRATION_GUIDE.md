# YAPPC SPI Migration Guide: core/spi → yappc-shared

**Version**: 1.0  
**Date**: March 28, 2026  
**Status**: Active  

## Overview

This guide provides step-by-step instructions for migrating from the deprecated `core:spi` module to the consolidated `yappc-shared` module.

## Quick Start

For most projects, migration is a simple dependency change:

```gradle
// Before
dependencies {
    implementation(project(":products:yappc:core:spi"))
}

// After
dependencies {
    implementation(project(":products:yappc:core:yappc-shared"))
}
```

**No code changes required** - all imports remain the same!

## Detailed Migration Steps

### Step 1: Update Build Files

#### Gradle (build.gradle.kts)

Find all references to `:products:yappc:core:spi` and replace with `:products:yappc:core:yappc-shared`:

```bash
# Find all affected build files
find . -name "build.gradle.kts" -exec grep -l "core:spi" {} \;

# Automated replacement (review before committing)
find . -name "build.gradle.kts" -exec sed -i '' 's/:products:yappc:core:spi/:products:yappc:core:yappc-shared/g' {} \;
```

### Step 2: Verify Imports

**Good news**: No import changes needed! All plugin interfaces remain in `com.ghatana.yappc.plugin.*`

Verify your imports are correct:
```java
import com.ghatana.yappc.plugin.YAPPCPlugin;
import com.ghatana.yappc.plugin.ValidatorPlugin;
import com.ghatana.yappc.plugin.GeneratorPlugin;
import com.ghatana.yappc.plugin.AgentPlugin;
import com.ghatana.yappc.plugin.PluginContext;
import com.ghatana.yappc.plugin.PluginRegistry;
// ... etc
```

These imports work with both `core:spi` and `yappc-shared`.

### Step 3: Rebuild and Test

```bash
# Clean build
./gradlew clean

# Build with new dependencies
./gradlew build

# Run tests
./gradlew test
```

### Step 4: Verify Plugin Loading

If you're using ServiceLoader for plugin discovery, verify your `META-INF/services` files are still correct:

```
META-INF/services/com.ghatana.yappc.plugin.YAPPCPlugin
META-INF/services/com.ghatana.yappc.plugin.ValidatorPlugin
META-INF/services/com.ghatana.yappc.plugin.GeneratorPlugin
META-INF/services/com.ghatana.yappc.plugin.AgentPlugin
```

## Migration Checklist

- [ ] Updated all `build.gradle.kts` files to use `yappc-shared`
- [ ] Removed direct dependencies on `core:spi`
- [ ] Verified no compilation errors
- [ ] Ran full test suite
- [ ] Verified plugin loading works (if applicable)
- [ ] Updated CI/CD pipelines (if needed)
- [ ] Updated documentation references

## Common Issues

### Issue 1: Circular Dependency

**Symptom**: Build fails with circular dependency error

**Solution**: Ensure you're not mixing `core:spi` and `yappc-shared` dependencies. Use only `yappc-shared`.

```gradle
// BAD - Don't mix!
dependencies {
    implementation(project(":products:yappc:core:spi"))
    implementation(project(":products:yappc:core:yappc-shared"))
}

// GOOD - Use only yappc-shared
dependencies {
    implementation(project(":products:yappc:core:yappc-shared"))
}
```

### Issue 2: Plugin Not Found

**Symptom**: `PluginRegistry` can't find your plugin

**Solution**: Verify your plugin is still registered in `META-INF/services` and implements the correct interface.

### Issue 3: Compilation Errors After Migration

**Symptom**: Code that compiled before now fails

**Solution**: 
1. Clean build: `./gradlew clean`
2. Invalidate IDE caches (IntelliJ: File → Invalidate Caches)
3. Reimport Gradle project
4. Verify no duplicate dependencies

## Module Comparison

| Feature | core:spi | yappc-shared |
|---------|----------|--------------|
| Plugin Interfaces | ✅ (compatibility wrapper) | ✅ |
| Plugin Context | ✅ (compatibility wrapper) | ✅ |
| Plugin Registry | ✅ (compatibility wrapper) | ✅ |
| Shared Utilities | ❌ | ✅ |
| JSON Utils | ❌ | ✅ |
| Config Management | ❌ | ✅ |
| Platform Core API | ❌ | ✅ |
| Future Support | ⚠️ Deprecated | ✅ Active |

## Benefits of Migration

1. **Single Dependency**: One module for all shared YAPPC functionality
2. **Additional Utilities**: Access to JSON utils, config management, etc.
3. **Future-Proof**: Active development and support
4. **Better Documentation**: Consolidated docs in one place
5. **Faster Builds**: Reduced module graph complexity

## Automated Migration Script

Use the provided script for automated migration:

```bash
./scripts/migrate-spi-to-shared.sh [options]

Options:
  --dry-run     Show what would be changed without making changes
  --verify      Verify migration completed successfully
  --rollback    Rollback to core:spi (emergency use only)
```

Example:
```bash
# Preview changes
./scripts/migrate-spi-to-shared.sh --dry-run

# Perform migration
./scripts/migrate-spi-to-shared.sh

# Verify success
./scripts/migrate-spi-to-shared.sh --verify
```

## API Compatibility Matrix

| API | core:spi | yappc-shared | Notes |
|-----|----------|--------------|-------|
| `YAPPCPlugin` | ✅ | ✅ | 100% compatible |
| `ValidatorPlugin` | ✅ | ✅ | 100% compatible |
| `GeneratorPlugin` | ✅ | ✅ | 100% compatible |
| `AgentPlugin` | ✅ | ✅ | 100% compatible |
| `PluginContext` | ✅ | ✅ | 100% compatible |
| `PluginRegistry` | ✅ | ✅ | 100% compatible |
| `PluginMetadata` | ✅ | ✅ | 100% compatible |
| `PluginCapabilities` | ✅ | ✅ | 100% compatible |

**Compatibility**: 100% - No breaking changes

## Testing Your Migration

### Unit Tests

Verify your plugin unit tests still pass:

```bash
./gradlew :your-plugin:test
```

### Integration Tests

Test plugin loading and execution:

```java
@Test
void testPluginLoadsWithYappcShared() {
    PluginContext context = DefaultPluginContext.builder()
        .yappcVersion("2.0.0")
        .build();
    
    PluginRegistry registry = PluginRegistry.create(context);
    registry.initialize().get();
    
    // Your plugin should load successfully
    assertTrue(registry.isRegistered("your-plugin-id"));
}
```

### E2E Tests

Run full platform tests to verify end-to-end functionality:

```bash
./gradlew e2eTest
```

## Rollback Plan

If you encounter issues, you can temporarily rollback:

```gradle
// Emergency rollback (temporary only)
dependencies {
    implementation(project(":products:yappc:core:spi"))
}
```

**Note**: This is only for emergency use. The `core:spi` module will be removed in Q2 2026.

## Support and Resources

- **Documentation**: `/docs/YAPPC_PLATFORM_GUIDE.md`
- **API Reference**: `/docs/api/yappc-shared/`
- **Examples**: `/products/yappc/examples/plugins/`
- **Slack**: `#yappc-platform`
- **Issues**: File in JIRA under `YAPPC-MIGRATION`

## FAQ

**Q: Do I need to change my plugin code?**  
A: No, only the dependency declaration in `build.gradle.kts`.

**Q: Will my existing plugins break?**  
A: No, `yappc-shared` maintains 100% API compatibility.

**Q: When will core:spi be removed?**  
A: Q2 2026 (June 2026 target).

**Q: Can I use both modules during migration?**  
A: Not recommended - use only `yappc-shared` to avoid conflicts.

**Q: What if I find a bug after migrating?**  
A: File a JIRA issue with `YAPPC-MIGRATION` label for priority support.

## Migration Timeline

- **March 28, 2026**: Migration guide published ✅
- **April 1-15, 2026**: Internal modules migration
- **April 16-30, 2026**: External plugin migration support
- **May 2026**: Deprecation warnings added to `core:spi`
- **June 2026**: `core:spi` module removed

## Success Stories

Track migration progress:
- [ ] Core YAPPC modules
- [ ] Internal plugins
- [ ] External plugins
- [ ] Example projects
- [ ] Documentation

---

**Last Updated**: March 28, 2026  
**Maintained By**: YAPPC Platform Team  
**Version**: 1.0
