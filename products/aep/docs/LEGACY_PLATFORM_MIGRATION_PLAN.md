# AEP Legacy Platform Migration Plan

## Current State Analysis

The legacy `platform/` module contains remaining Java files that need to be migrated to appropriate new modules.

## Migration Strategy

### Phase 1: Create New Target Modules

1. **platform-connectors** - All connector strategies (Kafka, RabbitMQ, S3, SQS, HTTP)
2. **platform-agent** - Agent adapter and context bridge
3. **platform-api** - Expert interface classes
4. **platform-analytics** - Already exists, populate with remaining analytics
5. **Archive/tests** - Test files remain in platform/ until fully migrated

### Phase 2: Migrate by Category

#### Connectors (19 files)
**Source**: `com.ghatana.aep.connector.strategy.*`
**Target**: `platform-connectors`
**Classes**:
- HttpIngressConfig, HttpPollingIngressStrategy, HttpWebhookEgressStrategy
- KafkaConsumerConfig, KafkaConsumerStrategy, KafkaProducerConfig, KafkaProducerStrategy
- RabbitMQConfig, RabbitMQConsumerStrategy
- S3Config, DefaultS3StorageStrategy
- SqsConfig, SqsConsumerStrategy, SqsProducerStrategy
- QueueConsumerStrategy, QueueMessage, QueueProducerStrategy

#### Agent (2 files)
**Source**: `com.ghatana.aep.agent.*`
**Target**: `platform-agent`
**Classes**:
- AepAgentAdapter, AepContextBridge

#### Analytics (16 files)
**Source**: `com.ghatana.aep.analytics.*`
**Target**: `platform-analytics`
**Classes**:
- AnalyticsEngine, AdvancedTimeSeriesForecaster, BusinessIntelligenceService
- DefaultAdvancedTimeSeriesForecaster, DefaultBusinessIntelligenceService
- DefaultIntelligentPredictiveAlerting, DefaultKPIAggregator
- DefaultPatternPerformanceAnalyzer, DefaultPredictiveAnalyticsEngine
- DefaultRealTimeAnomalyDetectionEngine, IntelligentPredictiveAlerting
- KPIAggregator, KPIReport, PatternPerformanceAnalyzer
- PredictiveAnalyticsEngine, RealTimeAnomalyDetectionEngine

#### Domain/Scaling/Preprocessing (49 files)
**Source**: `com.ghatana.aep.domain.*`, `com.ghatana.aep.scaling.*`, `com.ghatana.aep.preprocessing.*`
**Target**: `platform-core` (extend existing)

#### Expert Interface (46 files)
**Source**: `com.ghatana.aep.expertinterface.*`
**Target**: `platform-api` (new module)

#### Config/DI/Operator (22 files)
**Source**: `com.ghatana.aep.config.*`, `com.ghatana.aep.di.*`, `com.ghatana.aep.operator.*`
**Target**: `platform-core` (infrastructure classes)

#### Compliance/Catalog (4 files)
**Source**: `com.ghatana.aep.compliance.*`, `com.ghatana.aep.catalog.*`
**Target**: `platform-security` (compliance), `platform-core` (catalog)

#### Event/Feature/Integration (26 files)
**Source**: `com.ghatana.aep.event.*`, `com.ghatana.aep.feature.*`, `com.ghatana.aep.integration.*`
**Target**: `platform-core`

#### Platform/Schema (4 files)
**Source**: `com.ghatana.aep.platform.*`, `com.ghatana.aep.schema.*`
**Target**: `platform-core`

#### Detection Engine (2 files)
**Source**: `com.ghatana.aep.detectionengine.*`
**Target**: `platform-analytics`

#### Pipeline Registry (remaining)
**Source**: `com.ghatana.pipeline.registry.*`
**Target**: `platform-registry` (already migrated)

### Phase 3: Build Configuration

Update `settings.gradle.kts` to include new modules.
Update dependency graphs.

### Phase 4: Clean Up

Remove migrated files from legacy platform/.
Remove empty packages.
Archive or delete obsolete test files.

## Implementation Order

1. Create `platform-connectors` module + migrate connectors
2. Create `platform-agent` module + migrate agent classes
3. Create `platform-api` module + migrate expert interface
4. Extend `platform-analytics` with remaining analytics
5. Extend `platform-core` with domain/scaling/preprocessing
6. Update builds and verify
7. Clean up legacy platform/
