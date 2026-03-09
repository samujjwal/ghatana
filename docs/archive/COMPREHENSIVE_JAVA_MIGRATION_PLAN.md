# COMPREHENSIVE JAVA MIGRATION PLAN - ALL FILES COVERED

**Source:** `/home/samujjwal/Developments/ghatana/` (libs/java + products/*)  
**Target:** `/home/samujjwal/Developments/ghatana-new/` (platform + products)  
**Operation:** COPY ONLY (source remains untouched)  
**Last Updated:** 2026-02-04

---

## SCOPE COMPLETE - ALL JAVA SOURCES IDENTIFIED

### Source Locations in ghatana/:
1. ✅ `libs/java/` - 44 modules, ~918 files (SHARED LIBRARIES)
2. ✅ `products/*/backend/` - Product-specific Java backend code
3. ✅ `products/*/infrastructure/` - Product infrastructure Java code
4. ✅ `products/*/platform/java/` - Product platform Java code

---

## ✅ ALREADY MIGRATED - EXACT FILE LIST (111 files)

### Platform Core (Already in ghatana-new/platform/java/)

#### validation/ (8 files) - PARTIALLY MIGRATED
```
ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/
├── ValidationError.java
├── ValidationResult.java
├── Validator.java
├── ValidationService.java
├── NoopValidationService.java
├── NotNullValidator.java
├── NotEmptyValidator.java
└── EmailValidator.java

# TODO: Copy remaining from ghatana/libs/java/validation/ (20 more files)
```

#### auth/ (6 files) - PARTIALLY MIGRATED
```
ghatana-new/platform/java/auth/src/main/java/com/ghatana/platform/auth/
├── PasswordHasher.java
├── UserPrincipal.java
├── JwtTokenProvider.java
├── Permission.java
├── RolePermissionMapping.java
└── AuthorizationService.java

# TODO: Copy remaining from ghatana/libs/java/auth/ (32 more files)
```

#### observability/ (2 files) - PARTIALLY MIGRATED
```
ghatana-new/platform/java/observability/src/main/java/com/ghatana/platform/observability/
├── Metrics.java
└── Tracing.java

# TODO: Copy remaining from ghatana/libs/java/observability/ (119 more files)
```

#### config/ (4 files) - PARTIALLY MIGRATED
```
ghatana-new/platform/java/config/src/main/java/com/ghatana/platform/config/
├── ConfigManager.java
├── ConfigSource.java
├── ConfigValidator.java
└── ConfigReloadWatcher.java

# TODO: Copy remaining from ghatana/libs/java/config-runtime/ (24 more files)
```

### AEP Product (Already in ghatana-new/products/aep/)

#### connector/ (6 files) - COMPLETELY MIGRATED
```
ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/connector/
├── QueueMessage.java
├── QueueProducerStrategy.java
├── kafka/KafkaProducerStrategy.java
├── kafka/KafkaConsumerStrategy.java
├── s3/S3StorageStrategy.java
├── rabbitmq/RabbitMQProducerStrategy.java
└── rabbitmq/RabbitMQConsumerStrategy.java

# NOTE: Full module migrated, includes test files
```

### Data-Cloud Product (Already in ghatana-new/products/data-cloud/)

#### core/ (15 files) - COMPLETELY MIGRATED
```
ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/
├── RecordType.java
├── RetentionPolicy.java
├── DataRecord.java
├── FieldDefinition.java
├── Collection.java
├── EventConfig.java
├── RecordQuery.java
├── EventRecord.java
├── TimeSeriesRecord.java
├── EntityRecord.java
└── api/
    ├── CollectionService.java
    ├── QueryService.java
    ├── CollectionController.java
    ├── QueryController.java
    └── BulkController.java

# NOTE: Full module migrated, includes test files
```

### Shared Services (Already in ghatana-new/products/shared-services/)

#### auth/ (2 files) - COMPLETELY MIGRATED
```
ghatana-new/products/shared-services/platform/java/src/main/java/com/ghatana/platform/service/auth/
├── RateLimiter.java
└── TenantExtractor.java

# NOTE: Full module migrated
```

---

## 🔄 TO MIGRATE FROM ghatana/libs/java/ (807 files from 36 modules)

### PHASE 1: Platform Core (333 files from 10 modules)

| Module | Status | Files | Source | Target |
|--------|--------|-------|--------|--------|
| validation-api | 🔄 COPY | 15 | libs/java/validation-api/ | platform/java/core/validation/api/ |
| validation-common | 🔄 COPY | 12 | libs/java/validation-common/ | platform/java/core/validation/common/ |
| validation-spi | 🔄 COPY | 11 | libs/java/validation-spi/ | platform/java/core/validation/spi/ |
| validation (remaining) | 🔄 COPY | 20 | libs/java/validation/ (rest) | platform/java/core/validation/ |
| common-utils | 🔄 COPY | 76 | libs/java/common-utils/ | platform/java/core/util/ |
| types (remaining) | 🔄 COPY | 36 | libs/java/types/ (rest) | platform/java/core/types/ |
| context-policy | 🔄 COPY | 12 | libs/java/context-policy/ | platform/java/core/policy/ |
| governance | 🔄 COPY | 28 | libs/java/governance/ | platform/java/core/governance/ |
| security | 🔄 COPY | 110 | libs/java/security/ | platform/java/core/security/ |
| audit | 🔄 COPY | 5 | libs/java/audit/ | platform/java/core/audit/ |
| plugin-framework | 🔄 COPY | 28 | libs/java/plugin-framework/ | platform/java/core/plugin/ |

### PHASE 2: Platform Infrastructure (291 files from 8 modules)

| Module | Status | Files | Source | Target |
|--------|--------|-------|--------|--------|
| database | 🔄 COPY | 40 | libs/java/database/ | platform/java/database/ |
| redis-cache | 🔄 COPY | 17 | libs/java/redis-cache/ | platform/java/database/cache/ |
| http-client | 🔄 COPY | 13 | libs/java/http-client/ | platform/java/http/client/ |
| http-server | 🔄 COPY | 34 | libs/java/http-server/ | platform/java/http/server/ |
| observability (remaining) | 🔄 COPY | 119 | libs/java/observability/ (rest) | platform/java/observability/ |
| observability-clickhouse | 🔄 COPY | 16 | libs/java/observability-clickhouse/ | platform/java/observability/clickhouse/ |
| observability-http | 🔄 COPY | 29 | libs/java/observability-http/ | platform/java/observability/http/ |
| activej-runtime | 🔄 COPY | 23 | libs/java/activej-runtime/ | platform/java/runtime/ |

### PHASE 3: AEP Product (428 files from 8 modules)

| Module | Status | Files | Source | Target |
|--------|--------|-------|--------|--------|
| agent-api | 🔄 COPY | 15 | libs/java/agent-api/ | products/aep/platform/java/agent/api/ |
| agent-framework | 🔄 COPY | 45 | libs/java/agent-framework/ | products/aep/platform/java/agent/framework/ |
| agent-runtime | 🔄 COPY | 24 | libs/java/agent-runtime/ | products/aep/platform/java/agent/runtime/ |
| domain-models | 🔄 COPY | 104 | libs/java/domain-models/ | products/aep/platform/java/domain/ |
| operator | 🔄 COPY | 123 | libs/java/operator/ | products/aep/platform/java/operator/ |
| operator-catalog | 🔄 COPY | 18 | libs/java/operator-catalog/ | products/aep/platform/java/operator/catalog/ |
| ai-integration | 🔄 COPY | 47 | libs/java/ai-integration/ | products/aep/platform/java/ai/ |
| ai-platform | 🔄 COPY | 52 | libs/java/ai-platform/ | products/aep/platform/java/ai/platform/ |

### PHASE 4: Data-Cloud Product (125 files from 6 modules)

| Module | Status | Files | Source | Target |
|--------|--------|-------|--------|--------|
| event-cloud | 🔄 COPY | 23 | libs/java/event-cloud/ | products/data-cloud/platform/java/event/ |
| event-cloud-contract | 🔄 COPY | 14 | libs/java/event-cloud-contract/ | products/data-cloud/platform/java/event/contract/ |
| event-cloud-factory | 🔄 COPY | 5 | libs/java/event-cloud-factory/ | products/data-cloud/platform/java/event/factory/ |
| event-runtime | 🔄 COPY | 63 | libs/java/event-runtime/ | products/data-cloud/platform/java/event/runtime/ |
| event-spi | 🔄 COPY | 5 | libs/java/event-spi/ | products/data-cloud/platform/java/event/spi/ |
| state | 🔄 COPY | 15 | libs/java/state/ | products/data-cloud/platform/java/state/ |

### PHASE 5: Supporting & Review (257 files from 10 modules)

| Module | Status | Files | Source | Target | Notes |
|--------|--------|-------|--------|--------|-------|
| auth-platform | 🔄 COPY | 92 | libs/java/auth-platform/ | platform/java/auth/platform/ | Review if AEP-specific |
| config-runtime | 🔄 COPY | 28 | libs/java/config-runtime/ | platform/java/config/runtime/ | Check overlap with config/ |
| connectors | 🔄 COPY | 20 | libs/java/connectors/ | platform/java/core/connector/ | May be product-specific |
| ingestion | 🔄 COPY | 17 | libs/java/ingestion/ | platform/java/core/ingestion/ | May be shared |
| workflow-api | 🔄 COPY | 7 | libs/java/workflow-api/ | platform/java/core/workflow/ | May be AEP-specific |
| activej-websocket | 🔄 COPY | 9 | libs/java/activej-websocket/ | platform/java/http/websocket/ | HTTP extension |
| testing | 🔄 COPY | 79 | libs/java/testing/ | platform/java/testing/ | Testing utilities |
| architecture-tests | 🔄 COPY | 3 | libs/java/architecture-tests/ | platform/java/testing/architecture/ | Arch tests |
| platform-architecture-tests | 🔄 COPY | 2 | libs/java/platform-architecture-tests/ | platform/java/testing/architecture/ | Arch tests |
| build | 🔄 COPY | 0 | libs/java/build/ | platform/java/build-tools/ | Build tools |

---

## 🔄 TO MIGRATE FROM ghatana/products/* (Product-Specific Java)

### YAPPC Product (28+ files identified)

```
ghatana/products/yappc/ → ghatana-new/products/yappc/

backend/api/src/main/java/com/ghatana/yappc/api/workspace/
├── WorkspaceController.java
├── dto/WorkspaceMemberResponse.java
├── dto/WorkspaceSettingsResponse.java
├── dto/AddMemberRequest.java
├── dto/UpdateSettingsRequest.java
├── dto/CreateWorkspaceRequest.java
├── dto/UpdateWorkspaceRequest.java
├── dto/UpdateMemberRequest.java
└── dto/WorkspaceResponse.java

backend/api/src/main/java/com/ghatana/yappc/api/collaboration/
├── CodeReviewController.java
├── TeamController.java
└── NotificationController.java

backend/api/src/main/java/com/ghatana/yappc/api/service/
├── SprintService.java
├── LogService.java
├── TraceService.java
└── ComplianceService.java

infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/adapter/
├── SecurityServiceAdapter.java
├── YappcDataCloudRepository.java
├── RefactorerStorageAdapter.java
├── DashboardDataCloudAdapter.java
├── DashboardRepositoryFactory.java
├── KnowledgeGraphDataCloudPlugin.java
├── ObservabilityConfigurer.java
├── WidgetDataCloudAdapter.java
├── LoggingConfigurer.java
└── WidgetRepositoryFactory.java
├── mapper/YappcEntityMapper.java

# STATUS: 🔄 NOT YET MIGRATED
```

### Other Products (Java presence to be verified)

| Product | Java Location | Status | Target |
|---------|--------------|--------|--------|
| aep | products/aep/ | 🔄 Partial | products/aep/ |
| data-cloud | products/data-cloud/ | 🔄 Partial | products/data-cloud/ |
| software-org | products/software-org/ | ❓ Unknown | products/software-org/ |
| dcmaar | products/dcmaar/ | ❓ Unknown | products/dcmaar/ |
| tutorputor | products/tutorputor/ | ❓ Unknown | products/tutorputor/ |
| audio-video | products/audio-video/ | ❓ Unknown | products/audio-video/ |
| flashit | products/flashit/ | ❓ Unknown | products/flashit/ |
| virtual-org | products/virtual-org/ | ❓ Unknown | products/virtual-org/ |
| security-gateway | products/security-gateway/ | ❓ Unknown | products/security-gateway/ |

---

## MISSING PRODUCTS IN ghatana-new/

The following products exist in ghatana/ but are MISSING or EMPTY in ghatana-new/:

| Product | Status in ghatana-new | Action Required |
|---------|----------------------|-----------------|
| audio-video | ❌ MISSING | Create directory structure |
| dcmaar | ❌ MISSING | Create directory structure |
| tutorputor | ❌ MISSING | Create directory structure |
| yappc | ✅ EXISTS but EMPTY | Populate with Java files |
| software-org | ✅ EXISTS but may be empty | Verify and populate |
| virtual-org | ✅ EXISTS but may be empty | Verify and populate |

---

## MIGRATION EXECUTION CHECKLIST

### Pre-Migration (Before copying files)
- [ ] Create missing product directories (audio-video, dcmaar, tutorputor)
- [ ] Verify all source modules exist in ghatana/libs/java/
- [ ] Verify target directory structure exists in ghatana-new/

### Per-Module Migration (For each module)
- [ ] Copy all files from source to target
- [ ] Update package declarations (com.ghatana.X → com.ghatana.platform.X)
- [ ] Replace CompletableFuture with ActiveJ Promise
- [ ] Update imports to new package structure
- [ ] Copy test files with same updates
- [ ] Verify compilation succeeds
- [ ] Run tests to verify functionality
- [ ] Mark as ✅ MIGRATED in this document

### Post-Migration
- [ ] Verify all 44 libs/java modules migrated
- [ ] Verify all product-specific Java migrated
- [ ] Run full build to verify integration
- [ ] Run all tests to verify correctness

---

## SUMMARY

| Category | Modules | Files | Status |
|----------|---------|-------|--------|
| ✅ Already Migrated (libs/java) | 8 partial | 111 | Done |
| 🔄 To Migrate (libs/java) | 36 | 807 | Pending |
| 🔄 To Migrate (products/yappc) | ~10+ | 28+ | Pending |
| ❓ To Verify (other products) | 7+ | Unknown | Discovery needed |
| **TOTAL ESTIMATED** | **50+** | **~950+** | **In Progress** |

---

## NEXT IMMEDIATE ACTIONS

1. **Create Missing Products:** audio-video, dcmaar, tutorputor directories
2. **Complete YAPPC Migration:** Copy 28+ Java files from ghatana/products/yappc/
3. **Discover Other Products:** Search for Java files in all products/*/ directories
4. **Continue libs/java Migration:** Start with Phase 1 (validation modules)

**All operations are COPY-ONLY. Source ghatana/ remains completely unchanged.**
