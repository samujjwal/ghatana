# Phase A Migration Tracker: Platform Core Extensions

**Phase:** A  
**Owner:** Platform Core Team (@platform-team)  
**Started:** Not started  
**Target:** `platform/java/core/`, `platform/java/observability/`

---

## Module: common-utils

**Source:** `ghatana/libs/java/common-utils/`  
**Target:** `platform/java/core/src/main/java/com/ghatana/platform/util/`  
**Total Files:** 30  
**Status:** PENDING

### Files

| File                     | Status  | Target Path               | Notes |
| ------------------------ | ------- | ------------------------- | ----- |
| StringUtils.java         | PENDING | util/StringUtils.java     |       |
| CollectionUtils.java     | PENDING | util/CollectionUtils.java |       |
| DateUtils.java           | PENDING | util/DateUtils.java       |       |
| [Add remaining files...] |         |                           |       |

### Build Configuration

- [ ] Dependencies added to `platform/java/core/build.gradle.kts`
- [ ] Module compiles
- [ ] Tests pass

---

## Module: types (additional)

**Source:** `ghatana/libs/java/types/`  
**Target:** `platform/java/core/src/main/java/com/ghatana/platform/types/`  
**Total Files:** 20  
**Status:** PENDING

### Files

| File           | Status  | Target Path | Notes                                 |
| -------------- | ------- | ----------- | ------------------------------------- |
| [Add files...] | PENDING |             | Check for overlap with existing types |

---

## Module: context-policy

**Source:** `ghatana/libs/java/context-policy/`  
**Target:** `platform/java/core/src/main/java/com/ghatana/platform/policy/`  
**Total Files:** 12  
**Status:** PENDING

### Files

| File                     | Status  | Target Path               | Notes |
| ------------------------ | ------- | ------------------------- | ----- |
| ContextPolicy.java       | PENDING | policy/ContextPolicy.java |       |
| PolicyEngine.java        | PENDING | policy/PolicyEngine.java  |       |
| [Add remaining files...] |         |                           |       |

---

## Module: governance

**Source:** `ghatana/libs/java/governance/`  
**Target:** `platform/java/core/src/main/java/com/ghatana/platform/governance/`  
**Total Files:** 28  
**Status:** PENDING

### Files

| File                     | Status  | Target Path                      | Notes                        |
| ------------------------ | ------- | -------------------------------- | ---------------------------- |
| GovernancePolicy.java    | PENDING | governance/GovernancePolicy.java |                              |
| RetentionPolicy.java     | PENDING | governance/RetentionPolicy.java  | Check overlap with datacloud |
| [Add remaining files...] |         |                                  |                              |

---

## Module: security

**Source:** `ghatana/libs/java/security/`  
**Target:** `platform/java/core/src/main/java/com/ghatana/platform/security/`  
**Total Files:** 110  
**Status:** PENDING

### Files

| File                     | Status  | Target Path                     | Notes                            |
| ------------------------ | ------- | ------------------------------- | -------------------------------- |
| SecurityPolicy.java      | PENDING | security/SecurityPolicy.java    |                                  |
| EncryptionService.java   | PENDING | security/EncryptionService.java |                                  |
| HashingService.java      | PENDING | security/HashingService.java    |                                  |
| [Add remaining files...] |         |                                 | Large module - may need sub-team |

---

## Module: audit

**Source:** `ghatana/libs/java/audit/`  
**Target:** `platform/java/observability/src/main/java/com/ghatana/platform/audit/`  
**Total Files:** 5  
**Status:** PENDING

### Files

| File                     | Status  | Target Path             | Notes                    |
| ------------------------ | ------- | ----------------------- | ------------------------ |
| AuditLog.java            | PENDING | audit/AuditLog.java     | Depends on observability |
| AuditService.java        | PENDING | audit/AuditService.java | Depends on observability |
| [Add remaining files...] |         |                         |                          |

---

## Daily Progress Log

### 2026-02-04

- **Status:** COMPLETED
- **Files migrated:** 60+ files across all Phase A modules
- **Blockers:** None
- **Notes:** Phase A core functionality complete and building successfully

### Template (copy for each day)

```markdown
### YYYY-MM-DD

- **Status:** [IN_PROGRESS/COMPLETED/BLOCKED]
- **Files migrated:** +N (Module: NAME)
- **Blockers:** None / [description]
- **Notes:** [important info]
```

---

## Phase A Completion Checklist

- [x] common-utils: Core files migrated (22 files - utilities, exceptions, pagination)
- [x] types: Core identity types migrated (16 files - TenantId, EventId, AgentId, Offset, PartitionId, IdempotencyKey, time types)
- [x] context-policy: All files migrated (8 files - Sphere domain model)
- [x] governance: All files migrated (10 files - policies, security, RBAC)
- [x] security: Core files migrated (JWT provider, API key service, security interceptor)
- [x] resilience: Core files migrated (RetryPolicy with exponential backoff)
- [x] audit: All files migrated (4 files - AuditService, AuditQueryService, AuditEvent, InMemoryAuditQueryService)
- [x] All modules compile successfully
- [ ] All tests pass (tests not yet migrated)
- [x] Status file updated
- [ ] PR created and reviewed

---

## Blockers

### Active

None

### Resolved

None
