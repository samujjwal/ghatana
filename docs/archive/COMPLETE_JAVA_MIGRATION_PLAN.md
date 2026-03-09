# Complete Java Migration Plan - All 44 Modules

**Source:** `/home/samujjwal/Developments/ghatana/libs/java/` (44 modules, 918+ files)  
**Target:** `/home/samujjwal/Developments/ghatana-new/` (platform + products structure)  
**Operation:** COPY ONLY (source remains unchanged)

---

## MODULE COVERAGE CHECKLIST

### ✅ ALREADY MIGRATED (111 files from 8 modules)

| Module | Files | Status | Target Location |
|--------|-------|--------|-----------------|
| types | ~10 | ✅ Partial | platform/java/core |
| validation | ~8 | ✅ Partial | platform/java/core |
| auth | ~6 | ✅ Partial | platform/java/auth |
| observability | ~2 | ✅ Partial | platform/java/observability |
| config | ~4 | ✅ Partial | platform/java/config |
| aep/connectors | ~6 | ✅ Complete | products/aep/platform/java |
| data-cloud/core | ~10 | ✅ Complete | products/data-cloud/platform/java |
| data-cloud/api | ~5 | ✅ Complete | products/data-cloud/platform/java |
| shared-services | ~2 | ✅ Complete | products/shared-services/platform/java |

---

## 🔄 TO MIGRATE - ALL REMAINING MODULES (36 modules)

### Phase 1: Platform Core (TRULY SHARED) - 10 modules

| Module | Files | From | To |
|--------|-------|------|----|
| validation-api | 15 | ghatana/libs/java/validation-api/ | ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/api/ |
| validation-common | 12 | ghatana/libs/java/validation-common/ | ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/common/ |
| validation-spi | 11 | ghatana/libs/java/validation-spi/ | ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/spi/ |
| common-utils | 76 | ghatana/libs/java/common-utils/ | ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/util/ |
| types (remaining) | ~36 | ghatana/libs/java/types/ | ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/types/ |
| context-policy | 12 | ghatana/libs/java/context-policy/ | ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/policy/ |
| governance | 28 | ghatana/libs/java/governance/ | ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/governance/ |
| security | 110 | ghatana/libs/java/security/ | ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/security/ |
| audit | 5 | ghatana/libs/java/audit/ | ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/audit/ |
| plugin-framework | 28 | ghatana/libs/java/plugin-framework/ | ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/plugin/ |

**Phase 1 Total: 333 files**

### Phase 2: Platform Infrastructure (SHARED SERVICES) - 8 modules

| Module | Files | From | To |
|--------|-------|------|----|
| database | 40 | ghatana/libs/java/database/ | ghatana-new/platform/java/database/src/main/java/com/ghatana/platform/database/ |
| redis-cache | 17 | ghatana/libs/java/redis-cache/ | ghatana-new/platform/java/database/src/main/java/com/ghatana/platform/database/cache/ |
| http-client | 13 | ghatana/libs/java/http-client/ | ghatana-new/platform/java/http/src/main/java/com/ghatana/platform/http/client/ |
| http-server | 34 | ghatana/libs/java/http-server/ | ghatana-new/platform/java/http/src/main/java/com/ghatana/platform/http/server/ |
| observability (remaining) | ~119 | ghatana/libs/java/observability/ | ghatana-new/platform/java/observability/src/main/java/com/ghatana/platform/observability/ |
| observability-clickhouse | 16 | ghatana/libs/java/observability-clickhouse/ | ghatana-new/platform/java/observability/src/main/java/com/ghatana/platform/observability/clickhouse/ |
| observability-http | 29 | ghatana/libs/java/observability-http/ | ghatana-new/platform/java/observability/src/main/java/com/ghatana/platform/observability/http/ |
| activej-runtime | 23 | ghatana/libs/java/activej-runtime/ | ghatana-new/platform/java/runtime/src/main/java/com/ghatana/platform/runtime/ |

**Phase 2 Total: 291 files**

### Phase 3: AEP Product (AEP-SPECIFIC) - 8 modules

| Module | Files | From | To |
|--------|-------|------|----|
| agent-api | 15 | ghatana/libs/java/agent-api/ | ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/agent/api/ |
| agent-framework | 45 | ghatana/libs/java/agent-framework/ | ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/agent/framework/ |
| agent-runtime | 24 | ghatana/libs/java/agent-runtime/ | ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/agent/runtime/ |
| domain-models | 104 | ghatana/libs/java/domain-models/ | ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/domain/ |
| operator | 123 | ghatana/libs/java/operator/ | ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/operator/ |
| operator-catalog | 18 | ghatana/libs/java/operator-catalog/ | ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/operator/catalog/ |
| ai-integration | 47 | ghatana/libs/java/ai-integration/ | ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/ai/ |
| ai-platform | 52 | ghatana/libs/java/ai-platform/ | ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/ai/platform/ |

**Phase 3 Total: 428 files**

### Phase 4: Data-Cloud Product (DATA-CLOUD-SPECIFIC) - 6 modules

| Module | Files | From | To |
|--------|-------|------|----|
| event-cloud | 23 | ghatana/libs/java/event-cloud/ | ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/ |
| event-cloud-contract | 14 | ghatana/libs/java/event-cloud-contract/ | ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/contract/ |
| event-cloud-factory | 5 | ghatana/libs/java/event-cloud-factory/ | ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/factory/ |
| event-runtime | 63 | ghatana/libs/java/event-runtime/ | ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/runtime/ |
| event-spi | 5 | ghatana/libs/java/event-spi/ | ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/spi/ |
| state | 15 | ghatana/libs/java/state/ | ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/state/ |

**Phase 4 Total: 125 files**

### Phase 5: Supporting Modules (REVIEW NEEDED) - 10 modules

| Module | Files | From | To | Review Needed |
|--------|-------|------|----|---------------|
| auth-platform | 92 | ghatana/libs/java/auth-platform/ | ghatana-new/platform/java/auth/src/main/java/com/ghatana/platform/auth/platform/ | May be product-specific |
| config-runtime | 28 | ghatana/libs/java/config-runtime/ | ghatana-new/platform/java/config/src/main/java/com/ghatana/platform/config/runtime/ | Check overlap |
| connectors | 20 | ghatana/libs/java/connectors/ | ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/connector/ | May be shared |
| ingestion | 17 | ghatana/libs/java/ingestion/ | ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/ingestion/ | May be shared |
| workflow-api | 7 | ghatana/libs/java/workflow-api/ | ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/workflow/ | May be shared |
| activej-websocket | 9 | ghatana/libs/java/activej-websocket/ | ghatana-new/platform/java/http/src/main/java/com/ghatana/platform/http/websocket/ | HTTP extension |
| testing | 79 | ghatana/libs/java/testing/ | ghatana-new/platform/java/testing/src/main/java/com/ghatana/platform/testing/ | Testing utilities |
| architecture-tests | 3 | ghatana/libs/java/architecture-tests/ | ghatana-new/platform/java/testing/src/main/java/com/ghatana/platform/testing/architecture/ | Architecture tests |
| platform-architecture-tests | 2 | ghatana/libs/java/platform-architecture-tests/ | ghatana-new/platform/java/testing/src/main/java/com/ghatana/platform/testing/architecture/ | Architecture tests |
| build | 0 | ghatana/libs/java/build/ | ghatana-new/platform/java/build-tools/ | Build tools |

**Phase 5 Total: 257 files**

---

## COMPLETE MIGRATION SUMMARY

| Phase | Modules | Files | Target Structure |
|-------|---------|-------|------------------|
| ✅ Done | 8 | 111 | Already in ghatana-new |
| 🔄 P1 | 10 | 333 | platform/java/core |
| 🔄 P2 | 8 | 291 | platform/java/* |
| 🔄 P3 | 8 | 428 | products/aep/platform/java |
| 🔄 P4 | 6 | 125 | products/data-cloud/platform/java |
| 🔄 P5 | 10 | 257 | Various (review needed) |
| **TOTAL** | **44** | **~1545** | **ghatana-new/** |

---

## OWNERSHIP VERIFICATION

### Platform (Truly Shared) - 18 modules
- validation*, common-utils, types, context-policy, governance, security, audit, plugin-framework
- database, redis-cache, http-client, http-server, observability*, activej-runtime
- testing, architecture-tests, build-tools

### Products/AEP (AEP-Specific) - 8 modules  
- agent*, domain-models, operator*, ai*

### Products/Data-Cloud (Data-Cloud-Specific) - 6 modules
- event*, state

### Review Needed - 10 modules
- auth-platform, config-runtime, connectors, ingestion, workflow-api, activej-websocket

---

## DETAILED COPY INSTRUCTIONS

For each module:

1. **COPY ENTIRE FOLDER** from source to target
2. **UPDATE PACKAGE DECLARATIONS** to match new structure
3. **REPLACE CompletableFuture** with ActiveJ Promise
4. **COPY TEST FILES** alongside source files
5. **UPDATE IMPORTS** in all files
6. **VERIFY COMPILATION** in new location

### Example Copy Operation:
```bash
# Copy entire module
cp -r ghatana/libs/java/agent-api/ ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/agent/api/

# Update package declarations in all files
find ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/agent/api/ -name "*.java" -exec sed -i 's/package com\.ghatana\.agent\.api/package com.ghatana.platform.aep.agent.api/g' {} \;
```

---

## VERIFICATION CHECKLIST

For each copied module:

- [ ] All files copied to correct target location
- [ ] Package declarations updated
- [ ] Imports updated to new structure  
- [ ] CompletableFuture → ActiveJ Promise
- [ ] Test files copied and updated
- [ ] Module compiles successfully
- [ ] Tests pass in new structure
- [ ] No dependencies on old package structure

---

## NEXT ACTIONS

1. **Start Phase 1:** Platform Core (validation-api, validation-common, validation-spi)
2. **Continue Phase 1:** common-utils, types, context-policy
3. **Complete Phase 1:** governance, security, audit, plugin-framework
4. **Proceed to Phase 2:** Platform Infrastructure
5. **Then Phase 3:** AEP Product modules
6. **Then Phase 4:** Data-Cloud Product modules
7. **Finally Phase 5:** Review and migrate supporting modules

**All 44 modules will be migrated with clear file-by-file tracking.**
