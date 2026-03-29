# Deprecation Notice: core/spi Module

**Status**: DEPRECATED  
**Effective Date**: March 28, 2026  
**Removal Target**: Q2 2026  

## Summary

The `products:yappc:core:spi` module is being **deprecated** in favor of the consolidated `products:yappc:core:yappc-shared` module. All plugin SPI interfaces and implementations are now exported through `yappc-shared`.

## Migration Path

### For Plugin Developers

**Before (Deprecated)**:
```gradle
dependencies {
    implementation(project(":products:yappc:core:spi"))
}
```

**After (Recommended)**:
```gradle
dependencies {
    implementation(project(":products:yappc:core:yappc-shared"))
}
```

### Import Changes

**No import changes required** - All plugin interfaces remain in the same package:
- `com.ghatana.yappc.plugin.*`

The deprecated `core:spi` module now acts only as a compatibility wrapper over `yappc-shared`.

## Rationale

1. **Consolidation**: Reduces module fragmentation and simplifies dependency management
2. **Single Source of Truth**: `yappc-shared` is the canonical module for all shared YAPPC utilities and APIs
3. **Simplified Onboarding**: New developers only need to know about `yappc-shared`
4. **Reduced Build Complexity**: Fewer modules means faster builds and easier maintenance

## Timeline

- **March 28, 2026**: Deprecation announced, `core:spi` converted into a compatibility wrapper over `yappc-shared`
- **April 2026**: Migration guide published, automated migration scripts available
- **May 2026**: All internal YAPPC modules migrated to use `yappc-shared`
- **June 2026**: `core:spi` module removed from codebase

## What's Changing

### Unchanged
- ✅ All plugin interfaces (`YAPPCPlugin`, `ValidatorPlugin`, `GeneratorPlugin`, `AgentPlugin`)
- ✅ All plugin context and metadata classes
- ✅ Plugin registry and lifecycle management
- ✅ Package names (`com.ghatana.yappc.plugin.*`)

### Changed
- ⚠️ Dependency declaration (use `yappc-shared` instead of `core:spi`)
- ⚠️ Module path in Gradle builds

## Support

For questions or migration assistance:
- See: `docs/YAPPC_SPI_MIGRATION_GUIDE.md`
- Contact: YAPPC Platform Team
- Slack: #yappc-platform

## Automated Migration

Run the migration script to automatically update your build files:

```bash
./scripts/migrate-spi-to-shared.sh
```

This script will:
1. Update all `build.gradle.kts` files
2. Verify no import changes needed
3. Run tests to ensure compatibility
4. Generate migration report

---

**Note**: The `core:spi` module will continue to resolve until Q2 2026 as a compatibility dependency only, but all new development should use `yappc-shared` directly.
