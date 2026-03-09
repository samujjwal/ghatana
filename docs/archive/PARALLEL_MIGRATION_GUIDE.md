# Parallel Migration Coordination Plan

## Overview

This document defines **4 independent migration phases** that can be executed in parallel by different teams, with clear ownership, tracking mechanisms, and conflict avoidance strategies.

**Total Files:** ~1,046 files across all phases  
**Estimated Timeline:** 2 weeks (parallel execution)  
**Status Document:** `ghatana-new/PARALLEL_MIGRATION_STATUS.md` (auto-updated by each team)

---

## The Four Parallel Phases

### Phase A: Platform Core Extensions
**Owner:** Platform Core Team  
**Target Directory:** `platform/java/core/`, `platform/java/observability/`  
**Source Modules:** `common-utils`, `types`, `context-policy`, `governance`, `security`, `audit`

| Module | Files | Target Package | Dependencies |
|--------|-------|----------------|--------------|
| common-utils | ~30 | `com.ghatana.platform.util` | core |
| types (addl) | ~20 | `com.ghatana.platform.types` | core |
| context-policy | ~12 | `com.ghatana.platform.policy` | core |
| governance | ~28 | `com.ghatana.platform.governance` | core |
| security | ~110 | `com.ghatana.platform.security` | core |
| audit | ~5 | `com.ghatana.platform.audit` | core, observability |

**Migration Rules:**
- Package: `com.ghatana.platform.*`
- Async: ActiveJ Promise only
- Tests: JUnit 5 + AssertJ

---

### Phase B: AEP Product Migration
**Owner:** AEP Team  
**Target Directory:** `products/aep/platform/java/`  
**Source Modules:** `agent*`, `domain-models`, `operator*`, `ai*`

| Module | Files | Target Package | Dependencies |
|--------|-------|----------------|--------------|
| agent* | ~84 | `com.ghatana.aep.agent` | platform core |
| domain-models | ~104 | `com.ghatana.aep.domain` | platform core, domain |
| operator* | ~141 | `com.ghatana.aep.operator` | platform core |
| ai* | ~99 | `com.ghatana.aep.ai` | platform core |

**Migration Rules:**
- Package: `com.ghatana.aep.*`
- Domain models: Event, Pattern, Tenant (AEP-specific)
- No dependencies on Data-Cloud or other products

---

### Phase C: Data-Cloud Product Migration
**Owner:** Data-Cloud Team  
**Target Directory:** `products/data-cloud/platform/java/`  
**Source Modules:** `event*`, `state`, `storage`

| Module | Files | Target Package | Dependencies |
|--------|-------|----------------|--------------|
| event* | ~105 | `com.ghatana.datacloud.event` | platform core |
| state | ~15 | `com.ghatana.datacloud.state` | platform core |
| storage | ~25 | `com.ghatana.datacloud.storage` | platform core |

**Migration Rules:**
- Package: `com.ghatana.datacloud.*`
- Event processing: EventStore, EventStream, EventRouter
- State/Storage: Multi-tenant data management
- No dependencies on AEP

---

### Phase D: Platform Infrastructure
**Owner:** Infrastructure Team  
**Target Directory:** `platform/java/observability/`, `platform/java/database/`, `platform/java/http/`, `platform/java/testing/`, `platform/java/plugin/`, `platform/java/workflow/`  
**Source Modules:** `observability*`, `database`, `redis-cache`, `plugin-framework`, `workflow-api`, `http-client`, `http-server`, `testing`

| Module | Files | Target Package | Dependencies |
|--------|-------|----------------|--------------|
| observability* | ~166 | `com.ghatana.platform.observability` | core |
| database | ~40 | `com.ghatana.platform.database` | core |
| redis-cache | ~17 | `com.ghatana.platform.database.cache` | core |
| plugin-framework | ~28 | `com.ghatana.platform.plugin` | core |
| workflow-api | ~7 | `com.ghatana.platform.workflow` | core |
| http-client | ~13 | `com.ghatana.platform.http.client` | core, http |
| http-server (ext) | ~21 | `com.ghatana.platform.http.server` | core, http |
| testing | ~79 | `com.ghatana.platform.testing` | core |

**Migration Rules:**
- Consolidate observability modules into single module
- Merge http-client/server extensions with existing
- Testing utilities last (depends on most things)

---

## Status Tracking System

### Central Status File
**Location:** `/home/samujjwal/Developments/ghatana-new/PARALLEL_MIGRATION_STATUS.md`

**Update Protocol:**
1. Each team updates ONLY their section
2. Use atomic commits with message format: `[PHASE-X] Status update: Y files migrated`
3. Never modify other teams' sections
4. Mark files as IN_PROGRESS or COMPLETED only

### Status File Structure

```markdown
# PARALLEL_MIGRATION_STATUS.md

## Phase A: Platform Core (Owner: @platform-team)
| Module | Total | Migrated | Status |
|--------|-------|----------|--------|
| common-utils | 30 | 30 | COMPLETED |
| types | 20 | 15 | IN_PROGRESS |
| ... | ... | ... | ... |

Last updated: 2026-02-04 by @platform-team

## Phase B: AEP Product (Owner: @aep-team)
| Module | Total | Migrated | Status |
|--------|-------|----------|--------|
| agent-core | 45 | 0 | PENDING |
| ... | ... | ... | ... |

Last updated: 2026-02-04 by @aep-team

## Phase C: Data-Cloud (Owner: @datacloud-team)
...

## Phase D: Infrastructure (Owner: @infra-team)
...

## Blocking Issues
- Issue #1: Phase X blocked on Y (link to ticket)
```

### Module-Level Tracking

For each module being migrated, create a `MIGRATION_TRACKER.md` in the source directory:

**Example:** `ghatana/libs/java/governance/MIGRATION_TRACKER.md`
```markdown
# Governance Module Migration Tracker

**Phase:** A (Platform Core)  
**Owner:** @platform-team  
**Source:** ghatana/libs/java/governance/  
**Target:** ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/governance/  

## Files
| File | Status | Target Path | Notes |
|------|--------|-------------|-------|
| GovernancePolicy.java | COMPLETED | .../governance/GovernancePolicy.java | Renamed from GovernancePolicyImpl |
| RetentionPolicy.java | IN_PROGRESS | .../governance/RetentionPolicy.java | Check overlap with datacloud |
| ... | ... | ... | ... |

## Build Status
- [x] Module compiles
- [x] Tests pass
- [ ] Integration verified
```

---

## Conflict Avoidance Rules

### 1. File Ownership
- **Never modify files in other phases**
- If a file needs to be shared, copy it (don't move) and add to "Shared Files" section

### 2. Package Naming
- **Phase A:** `com.ghatana.platform.*`
- **Phase B:** `com.ghatana.aep.*`
- **Phase C:** `com.ghatana.datacloud.*`
- **Phase D:** `com.ghatana.platform.*` (infrastructure)

### 3. Dependency Declaration
Add this comment at the top of each migrated file:
```java
// PHASE: X
// OWNER: @team-name
// DEPENDS_ON: platform:java:core, platform:java:domain
// MIGRATED: 2026-02-04
```

### 4. Build.gradle.kts Ownership
Each team owns specific build files:
- Phase A: `platform/java/core/build.gradle.kts` (append dependencies)
- Phase B: `products/aep/platform/java/build.gradle.kts` (create)
- Phase C: `products/data-cloud/platform/java/build.gradle.kts` (create)
- Phase D: `platform/java/{observability,database,http,testing}/build.gradle.kts`

---

## Communication Protocol

### Daily Standup Format
Each team posts in #migration channel:
```
[PHASE-X] Daily Update - YYYY-MM-DD
- Files migrated: +N (total: M/Z)
- Blockers: None / [description]
- Needs help: None / [description from other teams]
- ETA: [date]
```

### Blocker Escalation
If a team is blocked on another phase:
1. Create ticket with label `migration-blocker`
2. Tag both team leads
3. Update central status file with `[BLOCKED]` prefix
4. Escalate to migration coordinator if not resolved in 24h

### Cross-Phase Dependencies
If a cross-phase dependency is discovered:
1. **Don't implement workaround**
2. Add to "Shared Dependencies" section of status file
3. Coordinator will schedule dependency-first resolution
4. Blocked team continues with non-dependent files

---

## Verification Checklist

### Per-File Checklist
- [ ] Source file copied to target (not moved)
- [ ] Package declaration updated
- [ ] Imports updated for new package paths
- [ ] ActiveJ Promise used (not CompletableFuture)
- [ ] Tests migrated and passing
- [ ] Phase/Owner comment added
- [ ] Added to module tracker

### Per-Module Checklist
- [ ] All files from tracker migrated
- [ ] build.gradle.kts dependencies declared
- [ ] Module compiles (`./gradlew :path:compileJava`)
- [ ] Tests pass (`./gradlew :path:test`)
- [ ] Status file updated
- [ ] Module tracker marked COMPLETED

### Phase Completion Checklist
- [ ] All modules in phase COMPLETED
- [ ] Integration test between modules passes
- [ ] No compilation errors in phase modules
- [ ] Documentation updated
- [ ] Status file shows 100% migrated
- [ ] PR created and reviewed

---

## Quick Reference: File Locations

### Status & Tracking
| Document | Location | Updated By |
|----------|----------|------------|
| Central Status | `PARALLEL_MIGRATION_STATUS.md` | All teams |
| Phase A Tracker | `PHASE_A_TRACKER.md` | Platform team |
| Phase B Tracker | `PHASE_B_TRACKER.md` | AEP team |
| Phase C Tracker | `PHASE_C_TRACKER.md` | Data-Cloud team |
| Phase D Tracker | `PHASE_D_TRACKER.md` | Infra team |
| Module Trackers | `libs/java/MODULE/MIGRATION_TRACKER.md` | Owning team |

### Source Code (READ-ONLY)
```
ghatana/libs/java/
├── common-utils/     # Phase A
├── types/            # Phase A
├── context-policy/   # Phase A
├── governance/       # Phase A
├── security/         # Phase A
├── audit/            # Phase A
├── agent*/           # Phase B
├── domain-models/    # Phase B
├── operator*/        # Phase B
├── ai*/              # Phase B
├── event*/           # Phase C
├── state/            # Phase C
├── storage/          # Phase C
├── observability*/   # Phase D
├── database/         # Phase D
├── redis-cache/      # Phase D
├── plugin-framework/ # Phase D
├── workflow-api/     # Phase D
├── http*/            # Phase D
└── testing/          # Phase D
```

### Target Code (WRITE)
```
ghatana-new/
├── platform/java/core/          # Phase A
├── platform/java/observability/ # Phase A, D
├── platform/java/database/      # Phase D
├── platform/java/http/          # Phase D
├── platform/java/testing/       # Phase D
├── platform/java/plugin/        # Phase D
├── platform/java/workflow/      # Phase D
├── products/aep/platform/java/       # Phase B
├── products/data-cloud/platform/java/  # Phase C
```

---

## Emergency Contacts

| Role | Team | Contact |
|------|------|---------|
| Migration Coordinator | All | @coordinator |
| Phase A Lead | Platform Core | @platform-lead |
| Phase B Lead | AEP | @aep-lead |
| Phase C Lead | Data-Cloud | @datacloud-lead |
| Phase D Lead | Infrastructure | @infra-lead |
| Build Issues | DevOps | @devops |
| Architecture Questions | Architecture | @architect |

---

## Glossary

- **Phase**: One of 4 parallel migration tracks
- **Module**: Source library from ghatana/libs/java/
- **Tracker**: File-level status document
- **Status File**: Central PARALLEL_MIGRATION_STATUS.md
- **Owner**: Team responsible for a phase
- **Depends On**: Required modules that must be migrated first
- **BLOCKED**: Phase cannot proceed without external help

---

*Last Updated: 2026-02-04*  
*Version: 1.0*
