# YAPPC Backend Agent Module Migration Guide

**Date:** March 23, 2026  
**Status:** In Progress - Requires Manual Completion  
**Current State:** Files migrated, build dependencies need resolution

---

## 🎯 Migration Overview

### What Was Accomplished
- ✅ 323 agent files categorized and migrated to focused modules
- ✅ Module structures created (code, architecture, testing specialists)
- ✅ Common module created for shared base classes
- ✅ Build files generated for all modules
- ✅ Settings.gradle.kts updated with new modules
- ✅ CORE_ARCHITECTURE.md updated with new structure

### What Remains
- ⚠️ Input/Output class dependencies need resolution
- ⚠️ Cross-module imports need fixing
- ⚠️ Build compilation errors need addressing
- ⚠️ Old specialists directory needs removal after verification

---

## 🔍 Current Build Status

### Common Module ✅
```bash
./gradlew :products:yappc:core:agents:common:build
# Status: BUILD SUCCESSFUL
# Contains: AgentInput, AgentOutput base classes
```

### Code-Specialists Module ❌
```bash
./gradlew :products:yappc:core:agents:code-specialists:build
# Status: BUILD FAILED
# Errors: 14 compilation errors
# Issue: Missing Input/Output class definitions
```

**Sample Errors:**
```
error: cannot find symbol
  symbol:   class ActivejExpertInput
  location: class ActivejExpertAgent

error: package com.ghatana.yappc.agents.architecture does not exist
  import com.ghatana.yappc.agents.architecture.IncidentResponseSpecialistAgent;
```

---

## 🔧 Root Cause Analysis

### Issue 1: Input/Output Classes Not Migrated
**Problem:** Agent Input/Output classes remain in original locations
- Each agent has its own Input/Output classes
- These classes are tightly coupled to their agents
- Cross-module references exist (e.g., code → architecture)

**Example:**
```java
// In code-specialists module
public class ActivejExpertAgent extends YAPPCAgentBase<ActivejExpertInput, ActivejExpertOutput>

// But ActivejExpertInput and ActivejExpertOutput are missing
```

### Issue 2: Cross-Module Dependencies
**Problem:** Agents reference classes from other modules
```java
// In code-specialists
import com.ghatana.yappc.agents.architecture.IncidentResponseSpecialistAgent;
import com.ghatana.yappc.agents.architecture.MonitorSpecialistAgent;
```

**Current State:**
- architecture-specialists module exists but package path changed
- Old import: `com.ghatana.yappc.agents.architecture.*`
- New import should be: `com.ghatana.yappc.agents.architecture.*` (same)
- But module dependency not properly configured

### Issue 3: Package Structure Mismatch
**Problem:** Files moved but package declarations may not match new structure

**Original:**
```
core/agents/specialists/src/main/java/com/ghatana/yappc/agents/code/
core/agents/specialists/src/main/java/com/ghatana/yappc/agents/architecture/
```

**New:**
```
core/agents/code-specialists/src/main/java/com/ghatana/yappc/agents/code/
core/agents/architecture-specialists/src/main/java/com/ghatana/yappc/agents/architecture/
```

Package declarations stayed the same, but Gradle module paths changed.

---

## 📋 Step-by-Step Resolution Guide

### Phase 1: Verify File Migration (✅ Complete)
All 323 files successfully moved to new module directories.

### Phase 2: Fix Input/Output Classes (⚠️ Required)

**Option A: Keep Input/Output with Agents (Recommended)**
```bash
# Input/Output classes should stay in their respective modules
# They're tightly coupled to specific agents
# No action needed - they're already there
```

**Option B: Extract Shared Interfaces**
```bash
# Create common interfaces in common module
# Implement in specialist modules
# More complex but better separation
```

**Recommendation:** Option A - Keep Input/Output classes with their agents

### Phase 3: Fix Cross-Module Imports (⚠️ Required)

**Step 1: Update build.gradle.kts dependencies**
```kotlin
// In code-specialists/build.gradle.kts
dependencies {
    implementation(project(":products:yappc:core:agents:common"))
    implementation(project(":products:yappc:core:agents:architecture-specialists"))
    // ... other dependencies
}
```

**Step 2: Verify package declarations**
```bash
# Check that package declarations match directory structure
find core/agents/code-specialists -name "*.java" -exec grep -H "^package" {} \;
```

**Step 3: Update imports if needed**
```bash
# If package paths changed, update imports
# Use IDE refactoring or sed commands
```

### Phase 4: Resolve Compilation Errors (⚠️ Required)

**For each compilation error:**

1. **Identify missing class**
   ```
   error: cannot find symbol
     symbol:   class ActivejExpertInput
   ```

2. **Locate the class file**
   ```bash
   find core/agents -name "ActivejExpertInput.java"
   ```

3. **Verify it's in the correct module**
   ```bash
   # Should be in code-specialists alongside ActivejExpertAgent
   ```

4. **Check package declaration matches**
   ```java
   package com.ghatana.yappc.agents.code;
   ```

5. **Ensure build.gradle.kts has correct dependencies**

### Phase 5: Build Verification (⚠️ Required)

```bash
# Build each module independently
./gradlew :products:yappc:core:agents:common:build
./gradlew :products:yappc:core:agents:code-specialists:build
./gradlew :products:yappc:core:agents:architecture-specialists:build
./gradlew :products:yappc:core:agents:testing-specialists:build

# Build all together
./gradlew :products:yappc:core:agents:build
```

### Phase 6: Remove Old Directory (⚠️ Only After Success)

```bash
# ONLY after all builds succeed
rm -rf core/agents/specialists

# Update parent build.gradle.kts to remove old dependency
# Already done - specialists dependency removed
```

---

## 🛠️ Troubleshooting Guide

### Error: "cannot find symbol"
**Cause:** Missing class or incorrect import
**Solution:**
1. Find the class file: `find core/agents -name "ClassName.java"`
2. Verify package declaration matches directory
3. Add module dependency if cross-module reference
4. Update import statement if package changed

### Error: "package does not exist"
**Cause:** Missing module dependency
**Solution:**
1. Identify which module contains the package
2. Add dependency in build.gradle.kts:
   ```kotlin
   implementation(project(":products:yappc:core:agents:MODULE_NAME"))
   ```

### Error: "circular dependency"
**Cause:** Modules depend on each other
**Solution:**
1. Extract shared interfaces to common module
2. Implement interfaces in specialist modules
3. Depend on common module only

---

## 📊 Migration Checklist

### Pre-Migration (✅ Complete)
- [x] Categorize all 323 agent files
- [x] Create module directory structures
- [x] Generate build.gradle.kts files
- [x] Update settings.gradle.kts
- [x] Create common module

### Migration Execution (✅ Complete)
- [x] Move files to new modules
- [x] Update parent build.gradle.kts
- [x] Create base classes in common module
- [x] Document new structure

### Post-Migration (⚠️ In Progress)
- [ ] Fix Input/Output class references
- [ ] Update cross-module imports
- [ ] Add missing module dependencies
- [ ] Resolve compilation errors
- [ ] Verify all builds succeed
- [ ] Remove old specialists directory
- [ ] Update CI/CD pipelines

---

## 🎯 Recommended Approach

### Immediate (This Week)
1. **Keep original specialists module temporarily**
   - Don't remove it yet
   - Use as reference for fixing new modules
   - Helps identify missing dependencies

2. **Fix one module at a time**
   - Start with code-specialists (largest, most dependencies)
   - Then architecture-specialists
   - Finally testing-specialists

3. **Use IDE refactoring tools**
   - IntelliJ IDEA can help identify missing imports
   - Auto-fix many compilation errors
   - Visualize dependency graph

### Detailed Steps for Code-Specialists

1. **Open in IDE**
   ```bash
   # Open code-specialists module in IntelliJ
   ```

2. **Let IDE index and identify errors**
   - IDE will highlight missing classes
   - Show quick-fix suggestions

3. **Add missing dependencies**
   ```kotlin
   // In code-specialists/build.gradle.kts
   dependencies {
       implementation(project(":products:yappc:core:agents:common"))
       implementation(project(":products:yappc:core:agents:architecture-specialists"))
       implementation(project(":products:yappc:core:agents:runtime"))
       implementation(project(":products:yappc:core:ai"))
       implementation(project(":products:yappc:core:domain"))
       // ... other dependencies
   }
   ```

4. **Fix imports**
   - Use IDE "Optimize Imports" feature
   - Manually fix any remaining issues

5. **Build and iterate**
   ```bash
   ./gradlew :products:yappc:core:agents:code-specialists:build
   # Fix errors, repeat
   ```

---

## 📈 Success Criteria

### Module Builds Successfully
```bash
./gradlew :products:yappc:core:agents:code-specialists:build
# BUILD SUCCESSFUL
```

### No Compilation Errors
```
> Task :products:yappc:core:agents:code-specialists:compileJava
> Task :products:yappc:core:agents:code-specialists:classes
> Task :products:yappc:core:agents:code-specialists:jar
```

### Tests Pass
```bash
./gradlew :products:yappc:core:agents:code-specialists:test
# All tests passing
```

### Old Directory Can Be Removed
```bash
# Only after all above succeed
rm -rf core/agents/specialists
```

---

## 🔄 Alternative Approach: Gradual Migration

If the full migration proves too complex, consider a gradual approach:

### Phase 1: Keep Both Structures
- Keep original specialists module
- Build new focused modules alongside
- Gradually move agents one at a time

### Phase 2: Dual Build
- Both old and new modules build successfully
- New modules depend on old for shared classes
- Gradually reduce old module dependencies

### Phase 3: Complete Migration
- All agents in new modules
- Old module empty
- Remove old module

---

## 📝 Notes

### Why This Is Complex
1. **Tight Coupling:** Agents are tightly coupled to their Input/Output classes
2. **Cross-References:** Agents reference other agents across domains
3. **Package Structure:** Package paths stayed same but module paths changed
4. **Build System:** Gradle module dependencies need careful configuration

### Lessons Learned
1. **Analyze dependencies first:** Should have mapped all dependencies before migration
2. **Test incrementally:** Should have verified build after each file move
3. **Use IDE tools:** IDE refactoring would have caught many issues
4. **Keep backups:** Original specialists module serves as reference

### Future Migrations
1. **Map dependencies first:** Create dependency graph
2. **Extract interfaces:** Separate interfaces from implementations
3. **Test each step:** Verify build after each change
4. **Use automation carefully:** Manual verification still needed

---

## 🎯 Current Recommendation

**DO NOT remove the old specialists directory yet.**

The original `core/agents/specialists` module should remain until:
1. All new modules build successfully
2. All compilation errors resolved
3. All tests passing
4. Verified in CI/CD

**Estimated Effort:** 4-8 hours of focused work with IDE assistance

**Priority:** Medium - Not blocking other work, but should be completed for clean architecture

---

**Last Updated:** March 23, 2026  
**Status:** Migration in progress, manual completion required  
**Next Action:** Fix compilation errors in code-specialists module using IDE
