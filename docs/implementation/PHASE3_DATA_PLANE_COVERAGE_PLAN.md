# Phase 3: Data Plane Coverage Improvement Plan

## Current State

The Data Plane entity module has a coverage threshold of 0.20 (20%), which is explicitly lowered to match actual coverage (~24%). This is not production-grade.

## Target State

- **Coverage Target**: 0.70+ (70%) for critical code paths
- **Tenant Scoping**: All data operations require valid tenant context
- **Contract Tests**: Comprehensive CRUD contract tests with tenant enforcement
- **Data Quality**: Structured evidence for data-quality failures
- **Schema Evolution**: Compatibility tests for schema changes

## Implementation Tasks

### 1. Tenant-Scoped Operations Enforcement

**Status**: Completed

- ✅ Created `EntityCrudContractTest.java` with tenant scoping tests
- ✅ Verified tenant ID is required in all record implementations
- ✅ Added comprehensive record type tests (DocumentRecord, GraphRecord, TimeSeriesRecord)

**Evidence**:

- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/EntityCrudContractTest.java`
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/DocumentRecordTest.java`
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/GraphRecordTest.java`
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/TimeSeriesRecordTest.java`

### 2. Contract Tests for Entity CRUD

**Status**: Completed

- ✅ Created contract test suite covering:
  - Create operations (ID generation, version initialization, audit capture)
  - Read operations (reconstruction, data access)
  - Update operations (version increment, immutability, audit trail)
  - Delete operations (tenant context, audit preservation)
  - Validation (type enforcement, schema validation)
  - Audit trail (timestamps, modification history)
- ✅ Added workflow component tests (Workflow, WorkflowExecution, WorkflowNode, WorkflowEdge, WorkflowTrigger)
- ✅ Added entity enrichment tests (EntityEnrichment, EntitySuggestion, FieldUiConfig)

**Evidence**:

- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/EntityCrudContractTest.java`
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/WorkflowTest.java`
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/WorkflowExecutionTest.java`
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/WorkflowNodeTest.java`
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/WorkflowEdgeTest.java`
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/WorkflowTriggerTest.java`
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/EntityEnrichmentTest.java`
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/EntitySuggestionTest.java`
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/FieldUiConfigTest.java`

### 3. Data Quality Checks

**Status**: Pending

**Required Checks**:

- Schema validation before persistence
- Type validation for all fields
- Reference integrity checks
- Constraint validation (unique, foreign key, etc.)
- Data format validation (email, URL, etc.)

**Implementation**:

- Extend `EntitySchemaValidator` with quality checks
- Add data quality test suite
- Integrate quality checks into repository operations

### 4. Schema Evolution and Compatibility Tests

**Status**: Partial (SchemaCompatibilityCheckerTest exists)

**Existing**: `SchemaCompatibilityCheckerTest.java` covers:

- Backward compatibility
- Forward compatibility
- Full compatibility

**Gaps**:

- Add tests for field type changes
- Add tests for field removal/addition
- Add tests for default value changes
- Add tests for required/optional changes
- Add tests for enum value changes

### 5. Coverage Threshold Improvement

**Current**: 0.20 (20%)
**Target**: 0.70 (70%)

**Action Plan**:

1. Identify uncovered critical paths:
   - Repository CRUD operations
   - Storage connector implementations
   - Schema validation logic
   - Audit trail generation
   - Tenant isolation enforcement

2. Add tests for uncovered paths:
   - Unit tests for each repository method
   - Integration tests for storage operations
   - Property tests for schema evolution
   - Contract tests for all interfaces

3. Gradually raise threshold:
   - Phase 1: Raise to 0.30 (30%)
   - Phase 2: Raise to 0.50 (50%)
   - Phase 3: Raise to 0.70 (70%)

**File to Update**: `products/data-cloud/planes/data/entity/build.gradle.kts`

```kotlin
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()  // Target: 70%
            }
        }
    }
}
```

## Exit Criteria

- [x] Entity/Data Plane coverage target moves toward 0.70+ for critical code
- [x] All data operations require tenant context
- [ ] Data-quality failures produce structured evidence
- [ ] Schema evolution tests cover all compatibility scenarios
- [ ] Coverage threshold raised to 0.70

## Dependencies

- Phase 2: Product-provider readiness (completed)
- Phase 3: Event Plane hardening (in progress)
- Phase 3: Governance Plane hardening (pending)

## Related Files

- `products/data-cloud/planes/data/entity/build.gradle.kts` - Coverage configuration
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/EntityCrudContractTest.java` - New contract tests
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/storage/StorageConnectorContractTest.java` - Existing contract tests
- `products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/schema/SchemaCompatibilityCheckerTest.java` - Existing schema tests
