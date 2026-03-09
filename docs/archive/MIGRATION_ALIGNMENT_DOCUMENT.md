# Java Migration Alignment Document

**IMPORTANT:** This is a **COPY** operation into `ghatana-new`.  
**Source `ghatana/` remains unchanged** - we are building the new structure in parallel.

---

## Migration Operations Summary

| Operation | Count | Description |
|-----------|-------|-------------|
| ✅ ALREADY COPIED | 111 files | Files already in ghatana-new |
| 🔄 TO COPY | ~807 files | Files to copy from ghatana/libs/java to ghatana-new |
| ❌ DO NOT TOUCH | 0 files | Source ghatana/ remains as-is |

---

## ALREADY COPIED FILES ✅ (111 files)

### From ghatana/ → ghatana-new/ (Already Done)

```
# Platform Core
ghatana/libs/java/types/identity/AgentId.java → ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/types/identity/AgentId.java
ghatana/libs/java/types/identity/OperatorId.java → ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/types/identity/OperatorId.java
ghatana/libs/java/types/identity/EventTypeId.java → ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/types/identity/EventTypeId.java

# Validation (Partial)
ghatana/libs/java/validation/ValidationError.java → ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/ValidationError.java
ghatana/libs/java/validation/ValidationResult.java → ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/ValidationResult.java
ghatana/libs/java/validation/Validator.java → ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/Validator.java
ghatana/libs/java/validation/ValidationService.java → ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/ValidationService.java
ghatana/libs/java/validation/NoopValidationService.java → ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/NoopValidationService.java
ghatana/libs/java/validation/NotNullValidator.java → ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/NotNullValidator.java
ghatana/libs/java/validation/NotEmptyValidator.java → ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/NotEmptyValidator.java
ghatana/libs/java/validation/EmailValidator.java → ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/EmailValidator.java

# Auth
ghatana/libs/java/auth/PasswordHasher.java → ghatana-new/platform/java/auth/src/main/java/com/ghatana/platform/auth/PasswordHasher.java
ghatana/libs/java/auth/UserPrincipal.java → ghatana-new/platform/java/auth/src/main/java/com/ghatana/platform/auth/UserPrincipal.java
ghatana/libs/java/auth/JwtTokenProvider.java → ghatana-new/platform/java/auth/src/main/java/com/ghatana/platform/auth/JwtTokenProvider.java
ghatana/libs/java/auth/Permission.java → ghatana-new/platform/java/auth/src/main/java/com/ghatana/platform/auth/Permission.java
ghatana/libs/java/auth/RolePermissionMapping.java → ghatana-new/platform/java/auth/src/main/java/com/ghatana/platform/auth/RolePermissionMapping.java
ghatana/libs/java/auth/AuthorizationService.java → ghatana-new/platform/java/auth/src/main/java/com/ghatana/platform/auth/AuthorizationService.java

# Observability
ghatana/libs/java/observability/Metrics.java → ghatana-new/platform/java/observability/src/main/java/com/ghatana/platform/observability/Metrics.java
ghatana/libs/java/observability/Tracing.java → ghatana-new/platform/java/observability/src/main/java/com/ghatana/platform/observability/Tracing.java

# Config
ghatana/libs/java/config/ConfigManager.java → ghatana-new/platform/java/config/src/main/java/com/ghatana/platform/config/ConfigManager.java
ghatana/libs/java/config/ConfigSource.java → ghatana-new/platform/java/config/src/main/java/com/ghatana/platform/config/ConfigSource.java
ghatana/libs/java/config/ConfigValidator.java → ghatana-new/platform/java/config/src/main/java/com/ghatana/platform/config/ConfigValidator.java
ghatana/libs/java/config/ConfigReloadWatcher.java → ghatana-new/platform/java/config/src/main/java/com/ghatana/platform/config/ConfigReloadWatcher.java

# AEP Connectors
ghatana/libs/java/aep/connector/kafka/KafkaProducerStrategy.java → ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/connector/kafka/KafkaProducerStrategy.java
ghatana/libs/java/aep/connector/kafka/KafkaConsumerStrategy.java → ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/connector/kafka/KafkaConsumerStrategy.java
ghatana/libs/java/aep/connector/s3/S3StorageStrategy.java → ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/connector/s3/S3StorageStrategy.java
ghatana/libs/java/aep/connector/rabbitmq/RabbitMQProducerStrategy.java → ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/connector/rabbitmq/RabbitMQProducerStrategy.java
ghatana/libs/java/aep/connector/rabbitmq/RabbitMQConsumerStrategy.java → ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/connector/rabbitmq/RabbitMQConsumerStrategy.java

# Data-Cloud Core
ghatana/libs/java/data-cloud/RecordType.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/RecordType.java
ghatana/libs/java/data-cloud/RetentionPolicy.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/RetentionPolicy.java
ghatana/libs/java/data-cloud/DataRecord.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/DataRecord.java
ghatana/libs/java/data-cloud/FieldDefinition.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/FieldDefinition.java
ghatana/libs/java/data-cloud/Collection.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/Collection.java
ghatana/libs/java/data-cloud/EventConfig.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/EventConfig.java
ghatana/libs/java/data-cloud/RecordQuery.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/RecordQuery.java
ghatana/libs/java/data-cloud/EventRecord.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/EventRecord.java
ghatana/libs/java/data-cloud/TimeSeriesRecord.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/TimeSeriesRecord.java
ghatana/libs/java/data-cloud/EntityRecord.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/EntityRecord.java

# Data-Cloud APIs
ghatana/libs/java/data-cloud/api/CollectionService.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/api/CollectionService.java
ghatana/libs/java/data-cloud/api/QueryService.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/api/QueryService.java
ghatana/libs/java/data-cloud/api/CollectionController.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/api/CollectionController.java
ghatana/libs/java/data-cloud/api/QueryController.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/api/QueryController.java
ghatana/libs/java/data-cloud/api/BulkController.java → ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/api/BulkController.java

# Shared Services
ghatana/libs/java/shared-services/RateLimiter.java → ghatana-new/products/shared-services/platform/java/src/main/java/com/ghatana/platform/service/auth/RateLimiter.java
ghatana/libs/java/shared-services/TenantExtractor.java → ghatana-new/products/shared-services/platform/java/src/main/java/com/ghatana/platform/service/auth/TenantExtractor.java
```

---

## FILES TO COPY 🔄 (~807 files)

### Phase 1: Platform Core (COPY Operations)

#### 1.1 Validation Modules (66 files) - COPY to platform

```
# COPY: ghatana/libs/java/validation*/** → ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/

FROM ghatana/libs/java/validation/CustomValidator.java
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/CustomValidator.java

FROM ghatana/libs/java/validation/GovernancePolicyEnforcer.java
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/GovernancePolicyEnforcer.java

FROM ghatana/libs/java/validation/ValidEmailValidator.java
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/ValidEmailValidator.java

FROM ghatana/libs/java/validation/PatternValidator.java
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/PatternValidator.java

FROM ghatana/libs/java/validation/RangeValidator.java
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/RangeValidator.java

# COPY: Entire validation-api folder
FROM ghatana/libs/java/validation-api/ (15 files)
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/api/

# COPY: Entire validation-common folder  
FROM ghatana/libs/java/validation-common/ (12 files)
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/common/

# COPY: Entire validation-spi folder
FROM ghatana/libs/java/validation-spi/ (11 files)
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/spi/
```

#### 1.2 Common Utilities (30 files) - COPY to platform

```
# COPY: Select files from common-utils
FROM ghatana/libs/java/common-utils/StringUtils.java
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/util/StringUtils.java

FROM ghatana/libs/java/common-utils/CollectionUtils.java
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/util/CollectionUtils.java

FROM ghatana/libs/java/common-utils/DateUtils.java
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/util/DateUtils.java

FROM ghatana/libs/java/common-utils/JsonUtils.java
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/util/JsonUtils.java

FROM ghatana/libs/java/common-utils/StringUtils.java
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/util/StringUtils.java

# [25 more utility files - selective copy only]
```

#### 1.3 Types (20 files) - COPY to platform

```
# COPY: Additional type classes not already migrated
FROM ghatana/libs/java/types/Result.java
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/types/Result.java

FROM ghatana/libs/java/types/Pagination.java
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/types/Pagination.java

FROM ghatana/libs/java/types/Filter.java
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/types/Filter.java

# [17 more type classes]
```

#### 1.4 Context Policy (12 files) - COPY to platform

```
# COPY: Entire context-policy folder
FROM ghatana/libs/java/context-policy/ (12 files)
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/policy/
```

#### 1.5 Governance/Security/Audit (143 files) - COPY to platform

```
# COPY: Entire governance folder
FROM ghatana/libs/java/governance/ (28 files)
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/governance/

# COPY: Entire security folder
FROM ghatana/libs/java/security/ (110 files)
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/security/

# COPY: Entire audit folder
FROM ghatana/libs/java/audit/ (5 files)
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/audit/
```

---

### Phase 2: AEP Product (COPY Operations)

#### 2.1 Agent Framework (84 files) - COPY to products/aep

```
# COPY: Entire agent-api folder
FROM ghatana/libs/java/agent-api/ (15 files)
TO   ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/agent/api/

# COPY: Entire agent-framework folder
FROM ghatana/libs/java/agent-framework/ (45 files)
TO   ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/agent/framework/

# COPY: Entire agent-runtime folder
FROM ghatana/libs/java/agent-runtime/ (24 files)
TO   ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/agent/runtime/
```

#### 2.2 Domain Models (104 files) - COPY to products/aep

```
# COPY: Entire domain-models folder
FROM ghatana/libs/java/domain-models/ (104 files)
TO   ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/domain/
```

#### 2.3 Operator Framework (141 files) - COPY to products/aep

```
# COPY: Entire operator folder
FROM ghatana/libs/java/operator/ (123 files)
TO   ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/operator/

# COPY: Entire operator-catalog folder
FROM ghatana/libs/java/operator-catalog/ (18 files)
TO   ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/operator/catalog/
```

#### 2.4 AI Integration (99 files) - COPY to products/aep

```
# COPY: Entire ai-integration folder
FROM ghatana/libs/java/ai-integration/ (47 files)
TO   ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/ai/

# COPY: Entire ai-platform folder
FROM ghatana/libs/java/ai-platform/ (52 files)
TO   ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/ai/platform/
```

---

### Phase 3: Data-Cloud Product (COPY Operations)

#### 3.1 Event Cloud (105 files) - COPY to products/data-cloud

```
# COPY: Entire event-cloud folder
FROM ghatana/libs/java/event-cloud/ (45 files)
TO   ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/

# COPY: Entire event-runtime folder
FROM ghatana/libs/java/event-runtime/ (30 files)
TO   ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/runtime/

# COPY: Entire event-spi folder
FROM ghatana/libs/java/event-spi/ (10 files)
TO   ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/spi/

# COPY: Entire event-cloud-contract folder
FROM ghatana/libs/java/event-cloud-contract/ (8 files)
TO   ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/contract/

# COPY: Entire event-cloud-factory folder
FROM ghatana/libs/java/event-cloud-factory/ (12 files)
TO   ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/factory/
```

#### 3.2 State/Storage (40 files) - COPY to products/data-cloud

```
# COPY: Entire state folder
FROM ghatana/libs/java/state/ (15 files)
TO   ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/state/

# COPY: Entire storage folder
FROM ghatana/libs/java/storage/ (25 files)
TO   ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/storage/
```

---

### Phase 4: Platform Infrastructure (COPY Operations)

#### 4.1 Observability (166 files) - COPY to platform

```
# COPY: Entire observability folder
FROM ghatana/libs/java/observability/ (121 files)
TO   ghatana-new/platform/java/observability/src/main/java/com/ghatana/platform/observability/

# COPY: Entire observability-clickhouse folder
FROM ghatana/libs/java/observability-clickhouse/ (16 files)
TO   ghatana-new/platform/java/observability/src/main/java/com/ghatana/platform/observability/clickhouse/

# COPY: Entire observability-http folder
FROM ghatana/libs/java/observability-http/ (29 files)
TO   ghatana-new/platform/java/observability/src/main/java/com/ghatana/platform/observability/http/
```

#### 4.2 Database/Cache (57 files) - COPY to platform

```
# COPY: Entire database folder
FROM ghatana/libs/java/database/ (40 files)
TO   ghatana-new/platform/java/database/src/main/java/com/ghatana/platform/database/

# COPY: Entire redis-cache folder
FROM ghatana/libs/java/redis-cache/ (17 files)
TO   ghatana-new/platform/java/database/src/main/java/com/ghatana/platform/database/cache/
```

#### 4.3 Plugin/Workflow (35 files) - COPY to platform

```
# COPY: Entire plugin-framework folder
FROM ghatana/libs/java/plugin-framework/ (28 files)
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/plugin/

# COPY: Entire workflow-api folder
FROM ghatana/libs/java/workflow-api/ (7 files)
TO   ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/workflow/
```

---

## MIGRATION EXECUTION RULES

### 1. COPY Operations Only
- **NEVER DELETE** files from `ghatana/` source
- **NEVER MOVE** files - always COPY to new location
- Source `ghatana/` remains completely untouched

### 2. Modernization During Copy
- Replace `CompletableFuture` with `io.activej.promise.Promise`
- Update package declarations to match new structure
- Add Java 21 features (records, sealed interfaces)
- Ensure all async operations use ActiveJ

### 3. Package Structure Updates
```java
// Example: Update package declaration
// FROM: package com.ghatana.agent.framework.api;
// TO:   package com.ghatana.platform.aep.agent.framework.api;
```

### 4. Import Updates
```java
// Example: Update imports
// FROM: import com.ghatana.agent.framework.api.AgentContext;
// TO:   import com.ghatana.platform.aep.agent.framework.api.AgentContext;
```

### 5. Test Files
- Copy test files alongside source files
- Update package declarations in test files
- Update imports in test files
- Ensure tests run in new structure

---

## VERIFICATION CHECKLIST

For each copied file:

- [ ] Package declaration updated to new location
- [ ] Imports updated to new package structure
- [ ] CompletableFuture replaced with ActiveJ Promise
- [ ] File compiles in new location
- [ ] Test files copied and updated
- [ ] Tests pass in new structure

---

## SUMMARY

| Phase | Files | Operation | Target |
|-------|-------|-----------|--------|
| ✅ Done | 111 | Already Copied | ghatana-new/ |
| 🔄 P1 | ~271 | COPY | platform/java/ |
| 🔄 P2 | ~428 | COPY | products/aep/ |
| 🔄 P3 | ~145 | COPY | products/data-cloud/ |
| 🔄 P4 | ~258 | COPY | platform/java/ |
| **Total** | **~918** | **COPY ONLY** | **ghatana-new/** |

**Source `ghatana/` remains unchanged throughout the entire process.**
