# Plugin & Feature Gap Analysis + Migration Plan

**Date:** February 5, 2026  
**Status:** ⚠️ CRITICAL GAPS IDENTIFIED  
**Priority:** P0 - Immediate Action Required

---

## Executive Summary

**CRITICAL FINDING:** 190 plugin files from Data Cloud were **NOT migrated**. These are production-ready plugins providing essential capabilities like analytics, knowledge graphs, enterprise features, and storage backends.

### Gap Overview

| Category | Old Repo | New Repo | Missing | Status |
|----------|----------|----------|---------|--------|
| **Data Cloud Plugins** | 190 files | ~24 files | 166 files | ❌ **CRITICAL GAP** |
| **AEP Libs** | 462 files | ~100 files | 362 files | ⚠️ **INVESTIGATION NEEDED** |
| **Platform** | 0 | 1,700 | N/A | ✅ NEW |
| **Shared Services** | 8 | 96 | N/A | ✅ EXPANDED |

---

## Part 1: Data Cloud Missing Plugins (190 Files)

### 1.1 Analytics Plugin ❌ **MISSING (122 files)**

**Location:** `products/data-cloud/plugins/analytics/`

#### What's Missing:

```
analytics/
├── trino/ (20 files)
│   └── Trino SQL connector for querying Data Cloud
├── trino-connector/ (102 files)
│   ├── EventCloudConnector.java
│   ├── EventCloudRecordSetProvider.java
│   ├── EventCloudSplitManager.java
│   ├── EventCloudTableHandle.java
│   ├── EventCloudRecordCursor.java
│   └── Full Trino connector implementation
└── build artifacts
```

#### Business Impact:

- **HIGH:** No SQL querying capability via Trino
- **HIGH:** No analytics integration with Presto/Trino ecosystem
- **HIGH:** Cannot query Data Cloud collections with standard SQL

#### Migration Plan:

**Option A: Migrate to platform/plugins/** (Recommended)
```
data-cloud/platform/plugins/analytics/
└── trino/
    ├── EventCloudConnector.java
    ├── EventCloudTableHandle.java
    ├── EventCloudSplitManager.java
    └── ... (all 122 files)
```

**Option B: Create separate analytics service**
```
products/data-cloud/services/analytics-service/
└── trino-connector/
```

**Recommendation:** Option A - Keep with platform for tight integration

---

### 1.2 Knowledge Graph Plugin ❌ **MISSING (14 files)**

**Location:** `products/data-cloud/plugins/knowledge-graph/`

#### What's Missing:

```
knowledge-graph/
├── KnowledgeGraphPlugin.java
├── KnowledgeGraphPluginFactory.java
├── KnowledgeGraphPluginImpl.java
├── model/
│   ├── GraphNode.java
│   ├── GraphEdge.java
│   └── GraphQuery.java
├── storage/
│   ├── GraphStorageAdapter.java
│   └── DataCloudGraphStorageAdapter.java
├── traversal/
│   ├── GraphTraversalEngine.java
│   └── BfsTraversalEngine.java
├── analytics/
│   ├── GraphAnalyticsEngine.java
│   └── CentralityAnalyticsEngine.java
└── api/
    ├── GraphApiController.java
    └── JsonMapper.java
```

#### Business Impact:

- **HIGH:** No graph database capability
- **HIGH:** Cannot model relationships between entities
- **MEDIUM:** Missing graph traversal and analytics
- **MEDIUM:** No knowledge graph queries

#### Migration Plan:

```
data-cloud/platform/plugins/knowledge-graph/
├── model/
│   ├── GraphNode.java
│   ├── GraphEdge.java
│   └── GraphQuery.java
├── storage/
│   └── GraphStorageAdapter.java
├── traversal/
│   └── GraphTraversalEngine.java
├── analytics/
│   └── GraphAnalyticsEngine.java
└── api/
    └── GraphApiController.java
```

---

### 1.3 Enterprise Plugins ❌ **MISSING (8 files)**

**Location:** `products/data-cloud/plugins/enterprise/`

#### What's Missing:

```
enterprise/
├── compliance/
│   └── CompliancePlugin.java (GDPR, CCPA, data residency)
├── iceberg/
│   └── IcebergPlugin.java (Apache Iceberg lakehouse)
├── lineage/
│   └── LineagePlugin.java (Data lineage tracking)
└── src/
    ├── documentation/
    │   └── AutoDocumentationGenerator.java
    ├── recovery/
    │   └── DisasterRecoveryManager.java
    ├── compliance/
    │   └── ComplianceReporter.java
    └── lineage/
        └── LineageTracker.java
```

#### Business Impact:

- **CRITICAL:** No GDPR/CCPA compliance features
- **HIGH:** No data lineage tracking
- **HIGH:** No disaster recovery automation
- **MEDIUM:** Missing Apache Iceberg integration
- **LOW:** No auto-documentation

#### Migration Plan:

```
data-cloud/platform/plugins/enterprise/
├── compliance/
│   ├── CompliancePlugin.java
│   ├── ComplianceReporter.java
│   └── GdprHandler.java
├── lineage/
│   ├── LineagePlugin.java
│   └── LineageTracker.java
├── recovery/
│   └── DisasterRecoveryManager.java
├── iceberg/
│   └── IcebergPlugin.java
└── documentation/
    └── AutoDocumentationGenerator.java
```

---

### 1.4 Storage Plugins ❌ **PARTIALLY MISSING (27 files)**

**Location:** `products/data-cloud/plugins/storage/`

#### What Exists in New Repo:

```
infrastructure/storage/ (5 files)
├── BlobStorageConnectorConfig.java
├── KeyValueConnectorConfig.java
├── LakehouseConnector.java
├── PostgresJsonbConnector.java
└── TimeSeriesConnector.java
```

#### What's Missing:

```
storage/
├── iceberg/ (5 files) ❌
│   ├── IcebergStorageConfig.java
│   ├── IcebergTableManager.java
│   ├── CoolTierStoragePlugin.java
│   └── TierMigrationScheduler.java
├── memory/ (5 files) ⚠️ PARTIALLY EXISTS
│   ├── InMemoryStoragePlugin.java
│   ├── HashRoutingPlugin.java
│   └── InMemoryStreamingPlugin.java
├── postgres/ (7 files) ⚠️ CONNECTOR EXISTS
│   ├── PostgresStoragePlugin.java
│   ├── PostgresStorageConfig.java
│   ├── MetricsStoragePlugin.java
│   └── postgres/ subpackage
├── redis/ (5 files) ❌
│   ├── RedisStorageConfig.java
│   ├── RedisHotTierPlugin.java
│   ├── EventHolder.java
│   └── redis/ subpackage
└── s3-archive/ (5 files) ❌
    ├── S3ArchiveConfig.java
    ├── ColdTierArchivePlugin.java
    ├── ArchiveMigrationScheduler.java
    ├── GlacierRestoreManager.java
    └── PiiMaskingUtil.java
```

#### Business Impact:

- **HIGH:** Missing Redis hot-tier plugin (performance)
- **HIGH:** Missing S3 cold-tier archival (cost optimization)
- **HIGH:** Missing Iceberg tiering (lakehouse integration)
- **MEDIUM:** No PII masking for archives
- **MEDIUM:** No Glacier restore capability

#### Migration Plan:

```
data-cloud/platform/plugins/storage/
├── redis/
│   ├── RedisStorageConfig.java
│   ├── RedisHotTierPlugin.java
│   └── RedisConnector.java
├── s3/
│   ├── S3ArchiveConfig.java
│   ├── ColdTierArchivePlugin.java
│   ├── ArchiveMigrationScheduler.java
│   ├── GlacierRestoreManager.java
│   └── PiiMaskingUtil.java
└── iceberg/
    ├── IcebergStorageConfig.java
    ├── IcebergTableManager.java
    ├── CoolTierStoragePlugin.java
    └── TierMigrationScheduler.java
```

---

### 1.5 Streaming Plugins ❌ **MISSING (13 files)**

**Location:** `products/data-cloud/plugins/streaming/`

#### What's Missing:

```
streaming/
├── kafka/ (7 files)
│   ├── KafkaStreamingPlugin.java
│   ├── KafkaProducerConfig.java
│   ├── KafkaConsumerConfig.java
│   └── KafkaTopicManager.java
└── pulsar/ (6 files)
    ├── PulsarStreamingPlugin.java
    ├── PulsarProducerConfig.java
    └── PulsarConsumerConfig.java
```

#### Business Impact:

- **HIGH:** No Kafka integration
- **MEDIUM:** No Pulsar integration
- **HIGH:** Missing real-time streaming capabilities

#### Migration Plan:

```
data-cloud/platform/plugins/streaming/
├── kafka/
│   ├── KafkaStreamingPlugin.java
│   ├── KafkaConfig.java
│   └── KafkaTopicManager.java
└── pulsar/
    ├── PulsarStreamingPlugin.java
    └── PulsarConfig.java
```

---

### 1.6 Vector Plugin ❌ **MISSING (4 files)**

**Location:** `products/data-cloud/plugins/vector/`

#### What's Missing:

```
vector/
├── VectorStoragePlugin.java
├── VectorSearchCapability.java
├── EmbeddingGenerator.java
└── SimilaritySearchEngine.java
```

#### Business Impact:

- **HIGH:** No vector embeddings storage
- **HIGH:** No similarity search
- **HIGH:** Missing AI/ML vector capabilities

#### Migration Plan:

```
data-cloud/platform/plugins/vector/
├── VectorStoragePlugin.java
├── VectorSearchCapability.java
├── EmbeddingGenerator.java
└── SimilaritySearchEngine.java
```

---

### 1.7 Agentic Plugin ❌ **MISSING (2 files)**

**Location:** `products/data-cloud/plugins/agentic/`

#### What's Missing:

```
agentic/
├── AgenticDataProcessor.java
└── AgenticDataProcessorTest.java
```

#### Business Impact:

- **MEDIUM:** No agentic AI capabilities
- **MEDIUM:** Missing autonomous data processing

#### Migration Plan:

```
data-cloud/platform/plugins/agentic/
└── AgenticDataProcessor.java
```

---

## Part 2: AEP Missing Libraries

### 2.1 AEP Libs Analysis

**Total in old repo:** 462 files in `aep-libs/`  
**Consolidated in new:** ~100 files in `platform/`  
**Potential gap:** 362 files

#### Breakdown:

```
aep-libs/ (Old Repo):
├── aep-canvas/ (0 Java files - TypeScript project) ✅ N/A
├── analytics-api/ (10 files) ⚠️ CHECK
├── connector-strategies/ (22 files) ⚠️ CHECK
├── event-integration/ (5 files) ⚠️ CHECK
├── event-processing-unified/ (113 files) ✅ MIGRATED
├── final-acceptance/ (5 files) ❌ TEST UTILITIES?
├── java/ (38 files) ⚠️ CHECK
│   ├── aep-core/
│   ├── domain-models/
│   ├── langchain4j-integration/
│   └── pipeline-dsl/
├── learning-api/ (1 file) ⚠️ CHECK
├── pattern-system/ (157 files) ✅ MIGRATED
├── planner/ (31 files) ⚠️ CHECK
├── routing/ (1 file) ⚠️ CHECK
└── testing/ (79 files) ⚠️ CHECK
```

#### Need Investigation:

1. **analytics-api/** (10 files) - Where did this go?
2. **connector-strategies/** (22 files) - Are connectors migrated?
3. **java/langchain4j-integration/** - AI integration lost?
4. **planner/** (31 files) - Planning/orchestration logic?
5. **testing/** (79 files) - Test utilities and frameworks?

---

## Part 3: Consolidated Migration Plan

### Phase 1: Critical Plugins (P0) - Week 1

**Must Have for Production**

#### 1.1 Data Cloud Storage Plugins
- ✅ Redis hot-tier (5 files)
- ✅ S3 cold-tier (5 files)
- ✅ Iceberg tiering (4 files)

**Effort:** 2 days  
**Risk:** Low (well-defined interfaces)

#### 1.2 Enterprise Compliance
- ✅ CompliancePlugin (GDPR/CCPA)
- ✅ LineagePlugin (data lineage)
- ✅ ComplianceReporter

**Effort:** 3 days  
**Risk:** Medium (legal requirements)

#### 1.3 Streaming Integrations
- ✅ Kafka plugin (7 files)
- ⚠️ Pulsar plugin (optional - 6 files)

**Effort:** 2 days  
**Risk:** Low (standard integrations)

**Total Phase 1:** ~7 days, 30+ files

---

### Phase 2: High-Value Plugins (P1) - Week 2

#### 2.1 Analytics (Trino Connector)
- ✅ Trino connector (122 files)
- ✅ SQL query capability

**Effort:** 4 days  
**Risk:** Medium (complex connector)

#### 2.2 Knowledge Graph
- ✅ Graph model (3 files)
- ✅ Graph storage (2 files)
- ✅ Traversal engine (2 files)
- ✅ Analytics (2 files)
- ✅ API (2 files)

**Effort:** 3 days  
**Risk:** Low (clean architecture)

#### 2.3 Vector Search
- ✅ Vector storage (4 files)
- ✅ Similarity search

**Effort:** 2 days  
**Risk:** Low (emerging standard)

**Total Phase 2:** ~9 days, 140+ files

---

### Phase 3: AEP Libraries Audit (P2) - Week 3

#### 3.1 Verify Consolidation
- ⚠️ Check analytics-api (10 files)
- ⚠️ Check connector-strategies (22 files)
- ⚠️ Check planner (31 files)
- ⚠️ Verify langchain4j integration

#### 3.2 Restore Missing
- Restore any non-duplicate libraries
- Document consolidation decisions

**Effort:** 5 days  
**Risk:** Low (investigation phase)

**Total Phase 3:** ~5 days

---

### Phase 4: Polish & Testing (P3) - Week 4

#### 4.1 Remaining Plugins
- ✅ Agentic processor (2 files)
- ✅ Disaster recovery (1 file)
- ✅ Auto-documentation (1 file)

#### 4.2 Integration Testing
- Test all migrated plugins
- End-to-end workflows
- Performance validation

**Effort:** 5 days  
**Risk:** Low

**Total Phase 4:** ~5 days, 4+ files

---

## Part 4: Directory Structure (Proposed)

### Data Cloud Platform

```
products/data-cloud/platform/
├── src/main/java/com/ghatana/datacloud/
│   ├── [existing core code...]
│   └── plugins/                    # NEW
│       ├── analytics/              # 122 files
│       │   └── trino/
│       ├── enterprise/             # 8 files
│       │   ├── compliance/
│       │   ├── lineage/
│       │   ├── recovery/
│       │   └── iceberg/
│       ├── knowledge-graph/        # 14 files
│       │   ├── model/
│       │   ├── storage/
│       │   ├── traversal/
│       │   ├── analytics/
│       │   └── api/
│       ├── storage/                # 15 files (add to existing 5)
│       │   ├── redis/
│       │   ├── s3/
│       │   └── iceberg/
│       ├── streaming/              # 13 files
│       │   ├── kafka/
│       │   └── pulsar/
│       ├── vector/                 # 4 files
│       │   └── embeddings/
│       └── agentic/                # 2 files
│           └── processor/
```

### Build Configuration

```gradle
// products/data-cloud/platform/build.gradle.kts

dependencies {
    // Plugin dependencies
    
    // Analytics
    implementation("io.trino:trino-spi:426")
    implementation("io.trino:trino-plugin-toolkit:426")
    
    // Knowledge Graph
    implementation("org.apache.tinkerpop:gremlin-core:3.7.0")
    
    // Storage
    implementation("io.lettuce:lettuce-core:6.3.0") // Redis
    implementation("software.amazon.awssdk:s3:2.20.0")
    implementation("org.apache.iceberg:iceberg-core:1.4.0")
    
    // Streaming
    implementation("org.apache.kafka:kafka-clients:3.6.0")
    implementation("org.apache.pulsar:pulsar-client:3.1.0")
    
    // Vector
    implementation("dev.langchain4j:langchain4j-embeddings:0.27.0")
    
    // Enterprise
    implementation("com.amazonaws:aws-java-sdk-glacier:1.12.600")
}
```

---

## Part 5: Risk Assessment

### High-Risk Items

1. **Trino Connector (122 files)** 
   - Complex SPI implementation
   - Many moving parts
   - Mitigation: Thorough testing with Trino cluster

2. **Compliance Plugin**
   - Legal/regulatory requirements
   - Must be 100% correct
   - Mitigation: Legal review, audit trails

3. **Data Lineage**
   - Cross-cutting concern
   - Affects all data operations
   - Mitigation: Event-driven architecture

### Medium-Risk Items

1. **Knowledge Graph** - New capability, learning curve
2. **Storage Plugins** - Well-understood patterns
3. **Streaming** - Standard integrations

### Low-Risk Items

1. **Vector Storage** - Simple interface
2. **Agentic Processor** - Optional feature
3. **Documentation Generator** - Nice-to-have

---

## Part 6: Success Criteria

### Phase 1 Complete When:
- ✅ Redis hot-tier plugin operational
- ✅ S3 cold-tier archival working
- ✅ Kafka streaming integrated
- ✅ Compliance plugin functional
- ✅ Data lineage tracking active

### Phase 2 Complete When:
- ✅ Trino connector passing all tests
- ✅ SQL queries working on Data Cloud
- ✅ Knowledge graph operational
- ✅ Graph queries functional
- ✅ Vector search working

### Phase 3 Complete When:
- ✅ All AEP libs accounted for
- ✅ No missing capabilities
- ✅ Consolidation decisions documented

### Phase 4 Complete When:
- ✅ All plugins migrated
- ✅ Integration tests passing
- ✅ Performance benchmarks met
- ✅ Documentation complete

---

## Part 7: Resource Requirements

### Development Team

- **Phase 1:** 2 engineers (storage, streaming, compliance)
- **Phase 2:** 2 engineers (analytics, knowledge graph)
- **Phase 3:** 1 engineer (audit, verification)
- **Phase 4:** 1 engineer (polish, testing)

### Infrastructure

- Trino cluster for testing
- Redis cluster
- S3/MinIO for archives
- Kafka cluster
- Neo4j or TinkerPop for graph testing

---

## Part 8: Immediate Next Steps

### Day 1 (Today)

1. ✅ Review and approve this plan
2. ⚠️ Create plugin migration branches
3. ⚠️ Set up plugins directory structure
4. ⚠️ Add plugin dependencies to build.gradle.kts

### Day 2-3 (Phase 1 Start)

1. Migrate Redis hot-tier plugin
2. Migrate S3 cold-tier plugin
3. Migrate Iceberg tiering
4. Basic integration tests

### Day 4-5

1. Migrate Kafka streaming
2. Migrate compliance plugin
3. Migrate lineage tracker
4. Phase 1 integration testing

### Week 2 (Phase 2)

1. Start Trino connector migration
2. Knowledge graph parallel track
3. Vector search implementation

---

## Part 9: Rollback Plan

If migration causes issues:

1. **Plugins are additive** - Can disable individual plugins
2. **Keep old repo** - Reference implementation available
3. **Feature flags** - Enable plugins gradually
4. **Incremental rollout** - One plugin at a time

---

## Conclusion

### Summary

- **190 plugin files** need migration from Data Cloud
- **Estimated effort:** 26 days (4 weeks)
- **Priority:** P0 (blocking production deployment)
- **Risk:** Medium (manageable with phased approach)

### Recommendation

**PROCEED IMMEDIATELY** with Phase 1 migration:
1. Storage plugins (Redis, S3, Iceberg)
2. Compliance & lineage
3. Kafka streaming

These are **production blockers** without which the platform lacks critical capabilities.

---

**Report Generated:** February 5, 2026  
**Status:** ⚠️ AWAITING APPROVAL  
**Next Action:** Begin Phase 1 migration immediately
