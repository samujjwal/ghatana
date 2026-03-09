# Gradle Folder Organization

## Structure Overview

```
gradle/
├── libs.versions.toml              # ✅ SINGLE SOURCE OF TRUTH for versions
├── conventions/                     # ✅ Custom convention plugins
│   └── src/main/groovy/com/ghatana/mas/conventions/
│       └── JavaConventionsPlugin.groovy
├── templates/                       # ✅ Project scaffolding templates
└── wrapper/                         # ✅ Gradle wrapper distribution

# Utility Files (gradle/*.gradle)
├── common-build.gradle              # ✅ Common build logic (if not in conventions/)
├── java-conventions.gradle          # ⚠️  CANDIDATE FOR DELETION (duplicate of convention plugin?)
├── duplicate-check.gradle           # ✅ Dependency duplicate detection
├── log4j2-config.gradle             # ✅ Log4j2 configuration
├── observability.gradle             # ✅ Metrics/tracing setup
├── proto-fix.gradle                 # ✅ Protobuf workarounds
├── proto-pojo-generation.gradle     # ✅ Protobuf code generation
├── publishing.gradle                # ✅ Maven/artifact publishing
├── source-sets-central-config.gradle # ✅ Source set configuration
├── source-sets-examples.gradle      # 📖 Documentation/examples
├── source-sets.gradle               # ⚠️  CANDIDATE FOR CONSOLIDATION
├── test-module.gradle               # ✅ Test module configuration
└── web.gradle                       # ✅ Web/frontend module configuration
```

## Recommendations

### 1. Consolidate Source Set Files
**Issue**: 3 source-set related files
- `source-sets-central-config.gradle`
- `source-sets.gradle`  
- `source-sets-examples.gradle`

**Action**: Merge into single `source-sets.gradle` with examples in comments/docs

### 2. Remove java-conventions.gradle (if redundant)
**Check**: Does it duplicate `JavaConventionsPlugin.groovy`?
**Action**: If yes, delete and update references to use `id 'com.ghatana.mas.conventions.java'`

### 3. Convert Convention Plugin to Kotlin DSL
**Current**: `JavaConventionsPlugin.groovy`
**Target**: `JavaConventionsPlugin.gradle.kts`
**Benefit**: Type-safe, consistent with KTS migration strategy

### 4. Version Catalog Usage
**Current**: Convention plugin had hardcoded versions (now removed)
**Status**: ✅ Fixed - modules must use `libs.versions.toml` directly

### 5. Documentation
**Missing**: `source-sets-usage-guide.md` exists but needs comprehensive gradle/ README
**Action**: Create `gradle/README.md` explaining each utility file's purpose

## Next Steps
1. Audit `java-conventions.gradle` for redundancy
2. Consolidate source-set files
3. Convert `JavaConventionsPlugin` to Kotlin DSL when ready for KTS migration
4. Create `gradle/README.md` documentation
