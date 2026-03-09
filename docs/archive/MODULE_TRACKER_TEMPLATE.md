# Module Migration Tracker Template

**Copy this file into each source module directory being migrated.**

**Example locations:**
- `ghatana/libs/java/governance/MIGRATION_TRACKER.md`
- `ghatana/libs/java/agent-core/MIGRATION_TRACKER.md`
- `ghatana/libs/java/event-cloud/MIGRATION_TRACKER.md`
- etc.

---

## Module Information

**Module Name:** [e.g., governance]  
**Phase:** [A, B, C, or D]  
**Owner:** @team-name  
**Source:** `ghatana/libs/java/[module]/`  
**Target:** `ghatana-new/[target-path]/`  
**Total Files:** [N]  
**Started:** [Date]  
**Completed:** [Date or "Not completed"]

---

## Migration Status

| File | Status | Target Path | Package | Notes |
|------|--------|-------------|---------|-------|
| ExampleFile.java | PENDING | target/ExampleFile.java | com.ghatana.platform | |
| [Add each file from source module] | | | | |

### Status Legend
- **PENDING** - Not started
- **IN_PROGRESS** - Currently being migrated
- **COMPLETED** - Migrated and tested
- **BLOCKED** - Cannot proceed (add reason in Notes)
- **SKIPPED** - Intentionally not migrated (add reason in Notes)

---

## File Migration Checklist Template

For each file, ensure:

- [ ] Source file copied to target (not moved)
- [ ] Package declaration updated to new path
- [ ] Imports updated for new package paths
- [ ] Phase/Owner comment added at top of file
- [ ] ActiveJ Promise used (not CompletableFuture)
- [ ] No compilation errors
- [ ] Tests migrated (if applicable)
- [ ] Tests passing

---

## Build Configuration

### Dependencies to Add
```kotlin
// Add to target module's build.gradle.kts
dependencies {
    api(project(":platform:java:core"))
    // api(project(":platform:java:domain")) // if needed
    // api("dependency:here:version")
}
```

### Build Status
- [ ] build.gradle.kts updated
- [ ] Module compiles: `./gradlew :[path]:compileJava`
- [ ] Tests pass: `./gradlew :[path]:test`

---

## Progress Log

### YYYY-MM-DD
- **Action:** [Started/Completed/Blocked]
- **Files:** [N files worked on]
- **Status:** [description]
- **Blockers:** [None or description]

---

## Blockers

### Active
1. **[Issue description]** - [Action being taken]

### Resolved
1. ~~[Resolved issue description]~~ - [Resolution date]

---

## Notes

- [Any important notes about this module migration]
- [Special considerations]
- [Cross-module dependencies discovered]

---

## Post-Migration Verification

- [ ] All files marked COMPLETED
- [ ] Module compiles without errors
- [ ] All tests pass
- [ ] Integration with dependent modules verified
- [ ] Documentation updated
- [ ] Status file updated: `PARALLEL_MIGRATION_STATUS.md`

---

## Example Completed Entry

### File: StringUtils.java

| Field | Value |
|-------|-------|
| **Source** | `ghatana/libs/java/common-utils/src/main/java/com/ghatana/common/StringUtils.java` |
| **Target** | `ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/util/StringUtils.java` |
| **Package** | `com.ghatana.platform.util` |
| **Status** | COMPLETED |
| **Migrated** | 2026-02-04 |
| **By** | @platform-team |
| **Changes** | Package: common → platform.util; Added phase comment |
| **Tests** | All 15 tests passing |
| **Notes** | No issues |

---

**Document Owner:** [Team/Individual]  
**Last Updated:** [Date]  
**Version:** 1.0
