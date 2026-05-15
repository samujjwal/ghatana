# Mastery System CI Regression Gates

## Overview

This document defines the regression gates for the mastery system to ensure changes don't break existing functionality. These gates should be integrated into the CI pipeline.

## Required Test Categories

### 1. Mastery Registry Tests

**Purpose**: Ensure mastery persistence and querying work correctly

**Required Tests**:
- `DataCloudMasteryRegistryTest` - Basic CRUD operations
- `DataCloudMasteryRegistryTest.testSaveAndRetrieve` - Save and retrieve mastery items
- `DataCloudMasteryRegistryTest.testQueryWithFilters` - Query with skillId, agentId, state filters
- `DataCloudMasteryRegistryTest.testTransition` - Mastery state transitions
- `DataCloudMasteryRegistryTest.testFindBest` - Best item selection with ranking
- `DataCloudMasteryRegistryTest.testSchemaValidation` - Schema validation on save

**Gate**: Must pass with 100% success rate

**Coverage**: Minimum 80% line coverage for DataCloudMasteryRegistry

### 2. Promotion Engine Tests

**Purpose**: Ensure promotion logic is correct and deterministic

**Required Tests**:
- `DefaultPromotionEngineTest` - Promotion evaluation
- `DefaultPromotionEngineTest.testCanPromote` - Promotion eligibility
- `DefaultPromotionEngineTest.testTargetState` - Target state determination
- `DefaultPromotionEngineTest.testPromotionWithEvidenceBundle` - Evidence bundle integration
- `DefaultPromotionPolicyTest` - Policy rule evaluation

**Gate**: Must pass with 100% success rate

**Coverage**: Minimum 85% line coverage for DefaultPromotionEngine and DefaultPromotionPolicy

### 3. Learning Engine Tests

**Purpose**: Ensure learning delta creation and contract enforcement

**Required Tests**:
- `LearningEngineTest` - Learning delta creation
- `LearningEngineTest.testLearningContractEnforcement` - Contract permits check
- `LearningEngineTest.testProceduralDeltaCreation` - Procedural skill deltas
- `LearningEngineTest.testOfflineOnlyTargetEnforcement` - MASTERY_STATE offline-only

**Gate**: Must pass with 100% success rate

**Coverage**: Minimum 80% line coverage for LearningEngine

### 4. GovernedAgentDispatcher Tests

**Purpose**: Ensure governance checks are applied during dispatch

**Required Tests**:
- `GovernedAgentDispatcherTest` - Governance-aware dispatch
- `GovernedAgentDispatcherTest.testMasteryCheck` - Mastery state check
- `GovernedAgentDispatcherTest.testSkillIdRequirement` - SkillId mandatory check
- `GovernedAgentDispatcherTest.testMemoryRetrieval` - Memory retrieval integration
- `GovernedAgentDispatcherTest.testModeSelectionBlocking` - BLOCKED mode handling

**Gate**: Must pass with 100% success rate

**Coverage**: Minimum 75% line coverage for GovernedAgentDispatcher

### 5. Evidence Bundle Tests

**Purpose**: Ensure evidence bundles work correctly

**Required Tests**:
- `MasteryEvidenceBundleTest` - Bundle creation and validation
- `MasteryEvidenceBundleTest.testAggregateWeight` - Weight computation
- `MasteryEvidenceBundleTest.testStatusTransitions` - Status lifecycle

**Gate**: Must pass with 100% success rate

**Coverage**: Minimum 80% line coverage for MasteryEvidenceBundle

### 6. Obsolescence Detection Tests

**Purpose**: Ensure obsolescence signals trigger correct transitions

**Required Tests**:
- `ObsolescenceDetectorTest` - Obsolescence detection
- `ObsolescenceDetectorTest.testScanAll` - Full scan with environment fingerprint
- `DataCloudObsolescenceSignalRepositoryTest` - Signal persistence

**Gate**: Must pass with 100% success rate

**Coverage**: Minimum 75% line coverage for ObsolescenceDetector

### 7. TypeScript Model Alignment Tests

**Purpose**: Ensure TypeScript models match Java enums

**Required Tests**:
- TypeScript type checking for MasteryState alignment
- TypeScript type checking for MasteryEvidenceBundleType
- TypeScript type checking for MasteryEvidenceBundleStatus

**Gate**: TypeScript compilation must pass without errors

**Coverage**: N/A (type-level validation)

### 8. Integration Tests

**Purpose**: Ensure end-to-end mastery workflows work

**Required Tests**:
- `MasteryIntegrationTest` - Full mastery lifecycle
- `MasteryIntegrationTest.testPromotionWorkflow` - Promotion from L2 to L3
- `MasteryIntegrationTest.testObsolescenceWorkflow` - Obsolescence detection and transition

**Gate**: Must pass with 100% success rate

**Coverage**: Minimum 70% line coverage for integration test scenarios

## CI Pipeline Configuration

### Gradle Configuration

```gradle
// In build.gradle.kts
tasks.test {
    // Enable test coverage
    useJUnitPlatform()
    
    // Configure coverage thresholds
    finalizedBy(jacocoTestReport)
    
    // Fail on test failures
    ignoreFailures = false
    
    // Set test JVM args
    jvmArgs("-Xmx2g")
}

// JaCoCo coverage thresholds
jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.80 // 80% coverage
            }
        }
        
        // Module-specific thresholds
        rule {
            element = "CLASS"
            includes = ["com.ghatana.agent.promotion.*"]
            limit {
                minimum = 0.85
            }
        }
        
        rule {
            element = "CLASS"
            includes = ["com.ghatana.agent.learning.*"]
            limit {
                minimum = 0.80
            }
        }
        
        rule {
            element = "CLASS"
            includes = ["com.ghatana.agent.mastery.*"]
            limit {
                minimum = 0.80
            }
        }
    }
}
```

### GitHub Actions Configuration

```yaml
name: Mastery Regression Gates

on:
  pull_request:
    paths:
      - 'platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/**'
      - 'platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/**'
      - 'platform/java/agent-core/src/main/java/com/ghatana/agent/learning/**'
      - 'products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/**'

jobs:
  mastery-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Run mastery registry tests
        run: ./gradlew :platform:java:agent-core:test --tests "*DataCloudMasteryRegistryTest*"
      
      - name: Run promotion engine tests
        run: ./gradlew :platform:java:agent-core:test --tests "*DefaultPromotionEngineTest*"
      
      - name: Run learning engine tests
        run: ./gradlew :platform:java:agent-core:test --tests "*LearningEngineTest*"
      
      - name: Run governed dispatcher tests
        run: ./gradlew :products:data-cloud:planes:action:agent-runtime:test --tests "*GovernedAgentDispatcherTest*"
      
      - name: Run evidence bundle tests
        run: ./gradlew :platform:java:agent-core:test --tests "*MasteryEvidenceBundleTest*"
      
      - name: Run obsolescence tests
        run: ./gradlew :platform:java:agent-core:test --tests "*ObsolescenceDetectorTest*"
      
      - name: Run integration tests
        run: ./gradlew :platform:java:agent-core:test --tests "*MasteryIntegrationTest*"
      
      - name: Check coverage
        run: ./gradlew :platform:java:agent-core:jacocoTestCoverageVerification
      
      - name: TypeScript type check
        run: cd platform/typescript/kernel-product-contracts && npm run type-check
```

## Pre-Merge Checklist

Before merging mastery-related changes, ensure:

- [ ] All required test categories pass
- [ ] Code coverage thresholds are met
- [ ] TypeScript models align with Java enums
- [ ] No deprecation warnings in compilation
- [ ] Documentation is updated for any API changes
- [ ] Integration tests pass with test database
- [ ] Performance benchmarks show no regression (< 10% overhead)

## Performance Benchmarks

The following performance benchmarks must not regress:

- `MasteryRegistry.query()`: < 50ms for 1000 items
- `MasteryRegistry.save()`: < 20ms per item
- `GovernedAgentDispatcher.dispatch()`: < 100ms overhead over base dispatcher
- `PromotionEngine.evaluate()`: < 100ms per delta
- `ObsolescenceDetector.scanAll()`: < 500ms for 1000 items

## Rollback Criteria

If any of the following occur, the change should be rolled back:

- Test success rate drops below 95%
- Coverage drops below minimum thresholds
- Performance regression > 20%
- TypeScript compilation errors
- Integration test failures
- Data migration issues in existing deployments

## Monitoring in Production

After deployment, monitor:

- Mastery state transition latency (p95 < 100ms)
- Promotion queue processing time (p95 < 500ms)
- Obsolescence scan duration (p95 < 1s)
- Error rates for mastery operations (< 1%)
- Database query performance for mastery collections
