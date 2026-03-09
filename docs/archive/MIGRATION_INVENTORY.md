# Complete Java Migration Inventory

**Source:** `/home/samujjwal/Developments/ghatana/libs/java/` (44 modules, ~918 files)  
**Target:** `/home/samujjwal/Developments/ghatana-new/` (platform + products structure)  
**Architecture:** ActiveJ Promise (NOT CompletableFuture)  
**Pattern:** True shared code in platform, product-specific in products/

---

## ALREADY MIGRATED ✅ (111 files)

### platform/java/ (Core Platform - Already Migrated)

```
platform/java/core/src/main/java/com/ghatana/platform/
├── types/
│   ├── identity/AgentId.java
│   ├── identity/OperatorId.java
│   └── identity/EventTypeId.java
├── validation/
│   ├── ValidationError.java
│   ├── ValidationResult.java
│   ├── Validator.java
│   ├── ValidationService.java
│   ├── NoopValidationService.java
│   ├── NotNullValidator.java
│   ├── NotEmptyValidator.java
│   └── EmailValidator.java
└── [other core utilities]

platform/java/auth/src/main/java/com/ghatana/platform/auth/
├── PasswordHasher.java
├── UserPrincipal.java
├── JwtTokenProvider.java
├── Permission.java
├── RolePermissionMapping.java
└── AuthorizationService.java

platform/java/observability/src/main/java/com/ghatana/platform/observability/
├── Metrics.java
└── Tracing.java

platform/java/config/src/main/java/com/ghatana/platform/config/
├── ConfigManager.java
├── ConfigSource.java
├── ConfigValidator.java
└── ConfigReloadWatcher.java

platform/java/http/src/main/java/com/ghatana/platform/http/
├── [existing HTTP abstractions - NOT migrating]

platform/java/database/src/main/java/com/ghatana/platform/database/
├── [existing DB abstractions - NOT migrating]

platform/java/testing/src/main/java/com/ghatana/platform/testing/
├── [existing test utilities - NOT migrating]
```

### products/ (Product Modules - Already Migrated)

```
products/aep/platform/java/src/main/java/com/ghatana/platform/aep/connector/
├── QueueMessage.java
├── QueueProducerStrategy.java
├── kafka/
│   ├── KafkaConsumerConfig.java
│   ├── KafkaProducerConfig.java
│   ├── KafkaProducerStrategy.java
│   └── KafkaConsumerStrategy.java
├── s3/
│   ├── S3Config.java
│   └── S3StorageStrategy.java
├── rabbitmq/
│   ├── RabbitMQConfig.java
│   ├── RabbitMQProducerStrategy.java
│   └── RabbitMQConsumerStrategy.java
└── sqs/
    └── SqsConfig.java

products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/
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

products/shared-services/platform/java/src/main/java/com/ghatana/platform/service/auth/
├── RateLimiter.java
└── TenantExtractor.java
```

---

## REMAINING TO MIGRATE 🔴 (~807 files)

### PATTERN 1: Entire Folder Copy (Validation Consolidation)

**Source:** `ghatana/libs/java/validation*/**/*`  
**Target:** `ghatana-new/platform/java/core/src/main/java/com/ghatana/platform/validation/`

```
validation/ → platform/java/core/src/main/java/com/ghatana/platform/validation/
├── ValidationService.java (already done - NOOP)
├── ValidationResult.java (already done - NOOP)
├── ValidationError.java (already done - NOOP)
├── Validator.java (already done - NOOP)
├── CustomValidator.java
├── GovernancePolicyEnforcer.java
├── NotEmptyValidator.java (already done - NOOP)
├── NotNullValidator.java (already done - NOOP)
├── ValidEmailValidator.java
├── PatternValidator.java
├── RangeValidator.java
└── impl/
    └── NoopValidationService.java (already done - NOOP)

validation-api/ → platform/java/core/src/main/java/com/ghatana/platform/validation/api/
├── [all API interfaces]

validation-common/ → platform/java/core/src/main/java/com/ghatana/platform/validation/common/
├── [common utilities]

validation-spi/ → platform/java/core/src/main/java/com/ghatana/platform/validation/spi/
├── [service provider interfaces]
```

**Action:** Copy entire validation\* folders to platform/java/core, consolidate into single validation module.

---

### PATTERN 2: Entire Folder Copy (Agent Framework) → AEP PRODUCT

**Source:** `ghatana/libs/java/agent*/**/*`  
**Target:** `ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/agent/`

```
agent-api/ → products/aep/platform/java/src/main/java/com/ghatana/platform/aep/agent/api/
├── Agent.java
├── AgentCapability.java
├── AgentRequest.java
├── AgentResponse.java
└── [all agent API interfaces]

agent-framework/ → products/aep/platform/java/src/main/java/com/ghatana/platform/aep/agent/framework/
├── runtime/
│   ├── BaseAgent.java
│   ├── AgentRuntime.java
│   ├── AgentLifecycle.java
│   └── generators/
│       ├── LLMGenerator.java
│       ├── PipelineGenerator.java
│       ├── RuleBasedGenerator.java
│       └── TemplateGenerator.java
├── api/
│   ├── AgentContext.java (with ActiveJ Promise)
│   ├── DefaultAgentContext.java
│   ├── OutputGenerator.java (with ActiveJ Promise)
│   └── GeneratorMetadata.java
├── memory/
│   ├── MemoryStore.java
│   ├── Episode.java
│   ├── Fact.java
│   ├── Policy.java
│   └── Preference.java
├── coordination/
│   ├── OrchestrationStrategy.java
│   ├── SequentialOrchestration.java
│   ├── ParallelOrchestration.java
│   └── HierarchicalOrchestration.java
└── [all framework classes]

agent-runtime/ → products/aep/platform/java/src/main/java/com/ghatana/platform/aep/agent/runtime/
├── AgentExecutor.java
├── AgentRegistry.java
├── AgentFactory.java
└── [runtime infrastructure]
```

**Action:** Copy entire agent\* folders to products/aep (AEP-specific, NOT platform).

---

### PATTERN 3: Entire Folder Copy (Domain Models)

**Source:** `ghatana/libs/java/domain-models/**/*`  
**Target:** `ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/domain/`

```
domain-models/ → products/aep/platform/java/src/main/java/com/ghatana/platform/aep/domain/
├── event/
│   ├── GEvent.java
│   ├── GEventType.java
│   ├── EventMetadata.java
│   └── EventPayload.java
├── pattern/
│   ├── Pattern.java
│   ├── PatternDefinition.java
│   └── PatternInstance.java
├── tenant/
│   ├── Tenant.java
│   ├── TenantConfig.java
│   └── TenantContext.java
└── [all domain entities specific to AEP]
```

**Action:** Move to products/aep (product-specific domain, NOT platform).

---

### PATTERN 4: Entire Folder Copy (Event Cloud)

**Source:** `ghatana/libs/java/event*/**/*`  
**Target:** `ghatana-new/products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/`

```
event-cloud/ → products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/
├── EventStore.java
├── EventStream.java
├── EventProducer.java
├── EventConsumer.java
├── EventRouter.java
└── [all event processing]

event-runtime/ → products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/runtime/
├── EventProcessor.java
├── EventHandler.java
├── EventDispatcher.java
└── [runtime components]

event-spi/ → products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/spi/
└── [service provider interfaces]

event-cloud-contract/ → products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/contract/
└── [contract classes]

event-cloud-factory/ → products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/event/factory/
└── [factory classes]
```

**Action:** Move to products/data-cloud (product-specific event processing).

---

### PATTERN 5: Entire Folder Copy (Operator Framework)

**Source:** `ghatana/libs/java/operator*/**/*`  
**Target:** `ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/operator/`

```
operator/ → products/aep/platform/java/src/main/java/com/ghatana/platform/aep/operator/
├── Operator.java
├── OperatorDefinition.java
├── OperatorExecutor.java
├── OperatorRegistry.java
├── OperatorContext.java
├── OperatorFactory.java
└── [123 files total]

operator-catalog/ → products/aep/platform/java/src/main/java/com/ghatana/platform/aep/operator/catalog/
├── Catalog.java
├── CatalogEntry.java
└── [18 files total]
```

**Action:** Move to products/aep (AEP-specific operator framework).

---

### PATTERN 6: Entire Folder Copy (AI Integration)

**Source:** `ghatana/libs/java/ai*/**/*`  
**Target:** `ghatana-new/products/aep/platform/java/src/main/java/com/ghatana/platform/aep/ai/`

```
ai-integration/ → products/aep/platform/java/src/main/java/com/ghatana/platform/aep/ai/
├── LLMGateway.java
├── LLMClient.java
├── PromptBuilder.java
├── ResponseParser.java
├── TokenUsage.java
├── CostEstimator.java
└── [47 files total]

ai-platform/ → products/aep/platform/java/src/main/java/com/ghatana/platform/aep/ai/platform/
├── ModelRegistry.java
├── ModelConfig.java
└── [52 files total]
```

**Action:** Move to products/aep (AEP-specific AI components).

---

### PATTERN 7: Selective File Copy (Platform Infrastructure)

**Source:** `ghatana/libs/java/*` → **Target:** `ghatana-new/platform/java/*/`

```
# Individual modules to platform/java/

common-utils/ → platform/java/core/src/main/java/com/ghatana/platform/util/
├── StringUtils.java
├── CollectionUtils.java
├── DateUtils.java
└── [select utilities, NOT all 76 files]

types/ → platform/java/core/src/main/java/com/ghatana/platform/types/
├── [additional type classes not already migrated]

# STATE/STORAGE modules go to DATA-CLOUD (product-specific)
state/ → products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/state/
├── StateManager.java
└── [15 files]

# STORAGE modules go to DATA-CLOUD (product-specific)
storage/ → products/data-cloud/platform/java/src/main/java/com/ghatana/platform/datacloud/storage/
├── StorageManager.java
└── [storage-related files]

context-policy/ → platform/java/core/src/main/java/com/ghatana/platform/policy/
├── ContextPolicy.java
├── PolicyEngine.java
└── [12 files]

governance/ → platform/java/core/src/main/java/com/ghatana/platform/governance/
├── GovernancePolicy.java
├── RetentionPolicy.java (check for overlap)
└── [28 files]

security/ → platform/java/core/src/main/java/com/ghatana/platform/security/
├── SecurityPolicy.java
├── EncryptionService.java
├── HashingService.java
└── [110 files - REVIEW, may be product-specific]

audit/ → platform/java/core/src/main/java/com/ghatana/platform/audit/
├── AuditLog.java
├── AuditService.java
└── [5 files]

redis-cache/ → platform/java/database/src/main/java/com/ghatana/platform/database/cache/
├── RedisCache.java
└── [17 files]

plugin-framework/ → platform/java/core/src/main/java/com/ghatana/platform/plugin/
├── PluginManager.java
├── PluginLoader.java
└── [28 files]

workflow-api/ → platform/java/core/src/main/java/com/ghatana/platform/workflow/
├── WorkflowEngine.java
├── WorkflowDefinition.java
└── [7 files]

ingestion/ → platform/java/core/src/main/java/com/ghatana/platform/ingestion/
├── DataIngestor.java
└── [17 files]

connectors/ → platform/java/core/src/main/java/com/ghatana/platform/connector/
├── Connector.java
├── ConnectorFactory.java
└── [20 files]

http-client/ → platform/java/http/src/main/java/com/ghatana/platform/http/client/
├── HttpClient.java
└── [13 files]

http-server/ → platform/java/http/src/main/java/com/ghatana/platform/http/server/
├── [already exists - check for overlap, 34 files]

observability/ → platform/java/observability/src/main/java/com/ghatana/platform/observability/
├── [already started - 121 files in source, consolidate]

observability-clickhouse/ → platform/java/observability/src/main/java/com/ghatana/platform/observability/clickhouse/
├── ClickHouseMetrics.java
└── [16 files]

observability-http/ → platform/java/observability/src/main/java/com/ghatana/platform/observability/http/
├── HttpMetrics.java
└── [29 files]

database/ → platform/java/database/src/main/java/com/ghatana/platform/database/
├── [already exists - check for overlap, 40 files]

activej-runtime/ → platform/java/runtime/src/main/java/com/ghatana/platform/runtime/
├── [23 files - core runtime]

activej-websocket/ → platform/java/http/src/main/java/com/ghatana/platform/http/websocket/
├── [9 files]

auth/ → platform/java/auth/src/main/java/com/ghatana/platform/auth/
├── [already started - 38 files, check for overlap]

auth-platform/ → platform/java/auth/src/main/java/com/ghatana/platform/auth/platform/
├── [92 files - REVIEW, may be product-specific]

config-runtime/ → platform/java/config/src/main/java/com/ghatana/platform/config/runtime/
├── [28 files - check for overlap with existing]

testing/ → platform/java/testing/src/main/java/com/ghatana/platform/testing/
├── [79 files - testing utilities]

architecture-tests/ → platform/java/testing/src/main/java/com/ghatana/platform/testing/architecture/
├── [3 files]

platform-architecture-tests/ → platform/java/testing/src/main/java/com/ghatana/platform/testing/architecture/
├── [2 files]
```

---

## MIGRATION PRIORITY ORDER

### Phase 1: Platform Core (High Priority) - TRULY SHARED ONLY

1. ✅ validation (DONE - cross-cutting)
2. common-utils (selective) - ~30 files
3. types (additional) - ~20 files
4. context-policy - ~12 files
5. governance, security, audit (review for truly shared) - ~143 files

### Phase 2: AEP Product (High Priority)

6. agent\* → products/aep - 84 files
7. domain-models → products/aep - 104 files
8. operator\* → products/aep - 141 files
9. ai\* → products/aep - 99 files

### Phase 3: Data-Cloud Product (High Priority)

10. event\* → products/data-cloud - 105 files
11. state, storage → products/data-cloud - ~40 files

### Phase 4: Platform Infrastructure (Medium Priority)

12. observability\* → platform (consolidate) - ~166 files
13. database, redis-cache → platform - ~57 files
14. plugin-framework, workflow-api → platform - ~35 files

### Phase 5: Supporting (Lower Priority)

15. http-client, http-server extensions
16. auth-platform (review for product-specific)
17. testing, architecture-tests

---

## FILE COUNT SUMMARY

| Category            | Source Files | Target Location             | Priority |
| ------------------- | ------------ | --------------------------- | -------- |
| ✅ Already Migrated | 111          | ghatana-new/                | Done     |
| validation\*        | ~66          | platform/java/core          | P1       |
| common-utils        | ~30          | platform/java/core          | P1       |
| types (additional)  | ~20          | platform/java/core          | P1       |
| context-policy      | ~12          | platform/java/core          | P1       |
| governance/security | ~143         | platform/java/core          | P1       |
| agent\*             | ~84          | products/aep                | P2       |
| domain-models       | ~104         | products/aep                | P2       |
| operator\*          | ~141         | products/aep                | P2       |
| ai\*                | ~99          | products/aep                | P2       |
| event\*             | ~105         | products/data-cloud         | P3       |
| state/storage       | ~40          | products/data-cloud         | P3       |
| observability\*     | ~166         | platform/java/observability | P4       |
| database/cache      | ~57          | platform/java/database      | P4       |
| plugin/workflow     | ~35          | platform/java/core          | P4       |
| Other platform      | ~150         | platform/java/\*            | P4-P5    |
| **Total Remaining** | **~807**     |                             |          |

---

## NEXT ACTIONS

1. **Immediate:** Complete validation module (Phase 1 - Platform)
2. **Next:** common-utils, types, context-policy (Phase 1 - Platform)
3. **Then:** AEP product modules (agent*, domain-models, operator*, ai\*)
4. **Finally:** Data-Cloud event\* and state/storage modules

**Key Principle:** Platform = true shared infrastructure only. Products = domain-specific code.

**Ownership Rules:**

- **platform/** = validation, utils, types, auth, observability, database, http (truly cross-cutting)
- **products/aep/** = agents, operators, AI, patterns, domain-models (AEP-specific)
- **products/data-cloud/** = events, storage, state (Data-Cloud-specific)

**Technical Note:** All async code MUST use `io.activej.promise.Promise` (NOT CompletableFuture) to maintain consistency with existing codebase.
