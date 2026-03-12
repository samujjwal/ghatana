# TDD Test Specification for K-07 Audit Framework

**Document Version:** 2.1  
**Date:** March 10, 2026  
**Status**: Implementation-Ready Test Specification  
**Scope**: K-07 Audit Framework - Immutable audit append, cryptographic chaining, search, export, verification, maker-checker linkage

---

## 1. Scope Summary

**In Scope:**
- Immutable audit event append with cryptographic chaining
- Standardized audit schema validation
- Hash chain computation and verification
- Audit search with filtering and pagination
- Evidence export with job lifecycle
- Maker-checker workflow linkage
- Tamper detection and prevention
- Tenant isolation in audit operations
- Retention policy enforcement
- Trace ID propagation across audit events

**Out of Scope:**
- Advanced analytics on audit data (deferred to K-06)
- Long-term archival strategies (future enhancement)
- Real-time audit monitoring (deferred to K-06)
- External audit system integration

**Authority Sources Used:**
- LLD_K07_AUDIT_FRAMEWORK.md
- ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md
- CURRENT_EXECUTION_PLAN.md
- Architecture specification parts 1-3
- Regulatory architecture document

**Assumptions:**
- Java 21 + ActiveJ for K-07 implementation
- PostgreSQL 15+ for audit log storage
- Cryptographic hashing (SHA-256) for chain integrity
- 10-year minimum retention requirement
- Integration with K-05 for audit event publication
- Maker-checker workflow integration

---

## 2. Source Inventory

| source_id | path | authority | why_it_matters | extracted_behaviors |
|-----------|------|-----------|----------------|-------------------|
| LLD_K07_001 | LLD_K07_AUDIT_FRAMEWORK.md | Primary | Low-level design for K-07 | Audit schema, chaining, search, export |
| ADR_011_001 | ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md | Primary | Stack baseline | Technology choices, security requirements |
| EXEC_PLAN_001 | CURRENT_EXECUTION_PLAN.md | Primary | Build order and dependencies | Phase 1 positioning, K-05 integration |
| ARCH_SPEC_001 | ARCHITECTURE_SPEC_PART_2_SECTIONS_9-10.md | Secondary | Security architecture | Audit requirements, compliance |
| REG_ARCH_001 | REGULATORY_ARCHITECTURE_DOCUMENT.md | Secondary | Regulatory requirements | 10-year retention, audit trails |

---

## 3. Behavior Inventory

### Group: Audit Event Management
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| AE_001 | Audit Event Validator | VALIDATE_SCHEMA | Validate audit event schema | LLD_K07_001 |
| AE_002 | Audit Event Validator | VALIDATE_REQUIRED_FIELDS | Check required fields presence | LLD_K07_001 |
| AE_003 | Audit Event Validator | VALIDATE_DUAL_CALENDAR | Validate BS/Gregorian timestamps | ARCH_SPEC_001 |
| AE_004 | Audit Event Validator | VALIDATE_ACTOR_PERMISSIONS | Check actor authorization | LLD_K07_001 |

### Group: Audit Persistence
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| AP_001 | Audit Store | APPEND_EVENT | Append audit event to immutable log | LLD_K07_001 |
| AP_002 | Audit Store | GENERATE_SEQUENCE | Generate sequential audit numbers | LLD_K07_001 |
| AP_003 | Audit Store | COMPUTE_HASH_CHAIN | Compute previous and current hashes | LLD_K07_001 |
| AP_004 | Audit Store | ENFORCE_APPEND_ONLY | Prevent updates/deletes | LLD_K07_001 |
| AP_005 | Audit Store | HANDLE_CONCURRENT_APPENDS | Manage concurrent audit writes | LLD_K07_001 |

### Group: Hash Chain Verification
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| HV_001 | Chain Verifier | VERIFY_FULL_CHAIN | Verify entire audit chain integrity | LLD_K07_001 |
| HV_002 | Chain Verifier | VERIFY_SUB_RANGE | Verify partial chain segment | LLD_K07_001 |
| HV_003 | Chain Verifier | DETECT_TAMPERING | Detect tampered audit records | LLD_K07_001 |
| HV_004 | Chain Verifier | HANDLE_CHAIN_BREAK | Respond to chain integrity failures | LLD_K07_001 |

### Group: Audit Search
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| AS_001 | Audit Search | SEARCH_WITH_FILTERS | Search audit events with filters | LLD_K07_001 |
| AS_002 | Audit Search | PAGINATE_RESULTS | Paginate search results | LLD_K07_001 |
| AS_003 | Audit Search | CURSOR_NAVIGATION | Navigate results with cursors | LLD_K07_001 |
| AS_004 | Audit Search | ENFORCE_TENANT_ISOLATION | Isolate search by tenant | LLD_K07_001 |

### Group: Evidence Export
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| EE_001 | Export Manager | CREATE_EXPORT_JOB | Create evidence export job | LLD_K07_001 |
| EE_002 | Export Manager | VALIDATE_EXPORT_REQUEST | Validate export parameters | LLD_K07_001 |
| EE_003 | Export Manager | PROCESS_EXPORT_JOB | Process export job lifecycle | LLD_K07_001 |
| EE_004 | Export Manager | GENERATE_EVIDENCE_PACKAGE | Generate evidence export package | LLD_K07_001 |

### Group: Maker-Checker Integration
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| MC_001 | Maker-Checker Linkage | LINK_AUDIT_TO_WORKFLOW | Link audit events to workflows | LLD_K07_001 |
| MC_002 | Maker-Checker Linkage | ENFORCE_SPLIT_ROLES | Prevent self-approval | LLD_K07_001 |
| MC_003 | Maker-Checker Linkage | TRACK_APPROVAL_CHAIN | Track approval chain audit trail | LLD_K07_001 |
| MC_004 | Maker-Checker Linkage | VALIDATE_WORKFLOW_COMPLETION | Validate workflow completion | LLD_K07_001 |

### Group: Retention and Compliance
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| RC_001 | Retention Manager | ENFORCE_RETENTION_POLICY | Apply 10-year retention policy | REG_ARCH_001 |
| RC_002 | Retention Manager | HANDLE_RETENTION_BOUNDARY | Process retention boundary events | LLD_K07_001 |
| RC_003 | Retention Manager | ARCHIVE_OLD_RECORDS | Archive expired audit records | LLD_K07_001 |
| RC_004 | Retention Manager | PREVENT_PREMATURE_DELETION | Block early deletion attempts | LLD_K07_001 |

---

## 4. Risk Inventory

| risk_id | description | severity | impacted_behaviors | required_test_layers |
|---------|-------------|----------|-------------------|---------------------|
| RISK_001 | Hash chain corruption breaks audit integrity | Critical | HV_001-004, AP_001-005 | Integration, Security |
| RISK_002 | Audit event schema validation bypass | High | AE_001-004, AP_001-005 | Unit, Security |
| RISK_003 | Tenant isolation breach exposes cross-tenant data | Critical | AS_004, EE_001-004 | Security, Integration |
| RISK_004 | Maker-checker circumvention enables fraud | Critical | MC_001-004 | Integration, Business |
| RISK_005 | Retention policy violation causes compliance issues | High | RC_001-004 | Integration, Compliance |
| RISK_006 | Export data leakage exposes sensitive information | High | EE_001-004 | Security, Integration |

---

## 5. Test Strategy by Layer

### Unit Tests
- **Purpose**: Validate schema validation, hash computation, sequence generation
- **Tools**: JUnit 5, cryptographic test utilities
- **Coverage Goal**: 100% statement and branch coverage
- **Fixtures Required**: Sample audit events, test hash values
- **Exit Criteria**: All unit tests pass, validation logic correct

### Component Tests
- **Purpose**: Validate audit persistence, chain verification, search operations
- **Tools**: Testcontainers, PostgreSQL with audit schema
- **Coverage Goal**: 100% component interaction scenarios
- **Fixtures Required**: Audit database, test events
- **Exit Criteria**: All components work with real data

### Integration Tests
- **Purpose**: Validate end-to-end audit flow, K-05 integration, export pipeline
- **Tools**: Docker Compose, full audit service
- **Coverage Goal**: 100% critical integration paths
- **Fixtures Required**: Complete audit environment
- **Exit Criteria**: Audit operations work end-to-end

### Security Tests
- **Purpose**: Validate tamper detection, tenant isolation, authorization
- **Tools**: Security test frameworks, penetration testing tools
- **Coverage Goal**: 100% security scenarios
- **Fixtures Required**: Security test harness
- **Exit Criteria**: Security controls work correctly

### Compliance Tests
- **Purpose**: Validate retention policy, regulatory requirements
- **Tools**: Compliance test frameworks, audit verification
- **Coverage Goal**: 100% compliance scenarios
- **Fixtures Required**: Compliance test data
- **Exit Criteria**: Compliance requirements met

---

## 6. Granular Test Catalog

### Test Cases for Audit Event Management

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| AE_TC_001 | Validate complete valid audit event | Audit Event Validator | LLD_K07_001 | Unit | Happy Path | High |
| AE_TC_002 | Reject audit event missing actor | Audit Event Validator | LLD_K07_001 | Unit | Validation Failure | High |
| AE_TC_003 | Reject audit event missing resource_id | Audit Event Validator | LLD_K07_001 | Unit | Validation Failure | High |
| AE_TC_004 | Validate dual-calendar timestamps | Audit Event Validator | ARCH_SPEC_001 | Unit | Happy Path | High |
| AE_TC_005 | Reject unauthorized actor | Audit Event Validator | LLD_K07_001 | Security | Authorization | High |

**AE_TC_001 Details:**
- **Preconditions**: Audit validator available
- **Fixtures**: Valid audit event with all required fields
- **Input**: Audit event {actor: "user123", resource_id: "order-456", action: "CREATE", outcome: "SUCCESS", details: {...}}
- **Execution Steps**:
  1. Validate schema structure
  2. Check required fields presence
  3. Validate timestamp formats
  4. Check actor permissions
  5. Return validation result
- **Expected Output**: Validation success with normalized event
- **Expected State Changes**: Event marked as valid
- **Expected Events**: Validation success event
- **Expected Audit**: Validation logged
- **Expected Observability**: Validation metrics
- **Expected External Interactions**: User directory for actor validation
- **Cleanup**: None
- **Branch IDs Covered**: validation_success, schema_valid
- **Statement Groups Covered**: audit_validator, schema_checker

### Test Cases for Audit Persistence

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| AP_TC_001 | Append audit event successfully | Audit Store | LLD_K07_001 | Component | Happy Path | High |
| AP_TC_002 | Generate sequential audit numbers | Audit Store | LLD_K07_001 | Unit | Happy Path | High |
| AP_TC_003 | Compute hash chain correctly | Audit Store | LLD_K07_001 | Unit | Happy Path | High |
| AP_TC_004 | Prevent audit event update | Audit Store | LLD_K07_001 | Security | Immutability | High |
| AP_TC_005 | Handle concurrent audit appends | Audit Store | LLD_K07_001 | Integration | Concurrency | High |

**AP_TC_001 Details:**
- **Preconditions**: Audit store initialized, validator available
- **Fixtures**: Valid audit event, database connection
- **Input**: Validated audit event
- **Execution Steps**:
  1. Generate sequence number
  2. Compute previous hash
  3. Compute current hash
  4. Append to audit table
  5. Return success confirmation
- **Expected Output**: Audit record stored with sequence and hashes
- **Expected State Changes**: New audit row in database
- **Expected Events**: Audit stored event
- **Expected Audit**: Audit append logged
- **Expected Observability**: Append metrics
- **Expected External Interactions**: PostgreSQL
- **Cleanup**: Remove test audit record
- **Branch IDs Covered**: append_success, hash_computation
- **Statement Groups Covered**: audit_store, sequence_generator, hash_computer

### Test Cases for Hash Chain Verification

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| HV_TC_001 | Verify full audit chain integrity | Chain Verifier | LLD_K07_001 | Unit | Happy Path | High |
| HV_TC_002 | Verify partial chain segment | Chain Verifier | LLD_K07_001 | Unit | Happy Path | High |
| HV_TC_003 | Detect tampered audit record | Chain Verifier | LLD_K07_001 | Security | Tampering | High |
| HV_TC_004 | Handle chain integrity failure | Chain Verifier | LLD_K07_001 | Security | Chain Break | High |
| HV_TC_005 | Verify chain after concurrent appends | Chain Verifier | LLD_K07_001 | Integration | Concurrency | Medium |

**HV_TC_001 Details:**
- **Preconditions**: Audit chain populated with multiple records
- **Fixtures**: Test audit chain with known hash values
- **Input**: Verification request for full chain
- **Execution Steps**:
  1. Read all audit records
  2. Verify hash sequence
  3. Check for gaps or tampering
  4. Return verification result
- **Expected Output**: Chain integrity verified
- **Expected State Changes**: None (read-only)
- **Expected Events**: Chain verification event
- **Expected Audit**: Verification logged
- **Expected Observability**: Verification metrics
- **Expected External Interactions**: PostgreSQL
- **Cleanup**: None
- **Branch IDs Covered**: verification_success, chain_intact
- **Statement Groups Covered**: chain_verifier, hash_validator

### Test Cases for Audit Search

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| AS_TC_001 | Search with exact filters | Audit Search | LLD_K07_001 | Integration | Happy Path | High |
| AS_TC_002 | Search with broad filters | Audit Search | LLD_K07_001 | Integration | Happy Path | High |
| AS_TC_003 | Paginate search results | Audit Search | LLD_K07_001 | Integration | Pagination | High |
| AS_TC_004 | Enforce tenant isolation | Audit Search | LLD_K07_001 | Security | Tenant Isolation | High |
| AS_TC_005 | Navigate with cursors | Audit Search | LLD_K07_001 | Integration | Navigation | Medium |

**AS_TC_001 Details:**
- **Preconditions**: Audit data populated, search service available
- **Fixtures**: Test audit records, search filters
- **Input**: Search request {actor: "user123", date_range: {start: "2023-01-01", end: "2023-01-31"}}
- **Execution Steps**:
  1. Validate search filters
  2. Enforce tenant isolation
  3. Query audit database
  4. Apply pagination
  5. Return search results
- **Expected Output**: Search results with matching audit events
- **Expected State Changes**: None (read-only)
- **Expected Events**: Search executed event
- **Expected Audit**: Search logged
- **Expected Observability**: Search metrics
- **Expected External Interactions**: PostgreSQL
- **Cleanup**: None
- **Branch IDs Covered**: search_success, exact_filters
- **Statement Groups Covered**: audit_search, filter_processor, pagination_manager

### Test Cases for Evidence Export

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| EE_TC_001 | Create export job successfully | Export Manager | LLD_K07_001 | Integration | Happy Path | High |
| EE_TC_002 | Validate export request parameters | Export Manager | LLD_K07_001 | Unit | Validation | High |
| EE_TC_003 | Process export job lifecycle | Export Manager | LLD_K07_001 | Integration | Happy Path | High |
| EE_TC_004 | Generate evidence package | Export Manager | LLD_K07_001 | Integration | Happy Path | High |
| EE_TC_005 | Reject unauthorized export request | Export Manager | LLD_K07_001 | Security | Authorization | High |

**EE_TC_001 Details:**
- **Preconditions**: Export manager available, audit data exists
- **Fixtures**: Export request, test audit records
- **Input**: Export request {date_range: {start: "2023-01-01", end: "2023-01-31"}, format: "JSON", filters: {...}}
- **Execution Steps**:
  1. Validate export request
  2. Check export permissions
  3. Create export job
  4. Schedule job processing
  5. Return job ID
- **Expected Output**: Export job created with ID and status
- **Expected State Changes**: Export job record created
- **Expected Events**: Export job created event
- **Expected Audit**: Export request logged
- **Expected Observability**: Export metrics
- **Expected External Interactions**: PostgreSQL, file storage
- **Cleanup**: Remove export job and files
- **Branch IDs Covered**: export_creation_success, request_valid
- **Statement Groups Covered**: export_manager, job_scheduler

### Test Cases for Maker-Checker Integration

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| MC_TC_001 | Link audit events to workflow | Maker-Checker Linkage | LLD_K07_001 | Integration | Happy Path | High |
| MC_TC_002 | Prevent self-approval | Maker-Checker Linkage | LLD_K07_001 | Security | Split Roles | High |
| MC_TC_003 | Track approval chain audit trail | Maker-Checker Linkage | LLD_K07_001 | Integration | Happy Path | High |
| MC_TC_004 | Validate workflow completion | Maker-Checker Linkage | LLD_K07_001 | Integration | Happy Path | High |

**MC_TC_001 Details:**
- **Preconditions**: Maker-checker workflow available
- **Fixtures**: Workflow instance, audit events
- **Input**: Linkage request {workflow_id: "wf-123", audit_event_ids: ["ae-456", "ae-789"]}
- **Execution Steps**:
  1. Validate workflow exists
  2. Check audit event ownership
  3. Create linkage records
  4. Update workflow audit trail
  5. Return linkage confirmation
- **Expected Output**: Audit events linked to workflow
- **Expected State Changes**: Linkage records created
- **Expected Events**: Linkage created event
- **Expected Audit**: Linkage logged
- **Expected Observability**: Linkage metrics
- **Expected External Interactions**: Workflow service, audit store
- **Cleanup**: Remove linkage records
- **Branch IDs Covered**: linkage_success, workflow_valid
- **Statement Groups Covered**: maker_checker_linkage, workflow_validator

### Test Cases for Retention and Compliance

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| RC_TC_001 | Enforce 10-year retention policy | Retention Manager | REG_ARCH_001 | Integration | Happy Path | High |
| RC_TC_002 | Handle retention boundary events | Retention Manager | LLD_K07_001 | Integration | Boundary | High |
| RC_TC_003 | Archive expired audit records | Retention Manager | LLD_K07_001 | Integration | Archival | Medium |
| RC_TC_004 | Prevent premature deletion | Retention Manager | LLD_K07_001 | Security | Protection | High |

**RC_TC_001 Details:**
- **Preconditions**: Retention manager configured, audit data exists
- **Fixtures**: Test audit records with various ages
- **Input**: Retention check request {audit_record_id: "ar-123", current_date: "2023-01-01"}
- **Execution Steps**:
  1. Calculate record age
  2. Compare to retention policy
  3. Determine retention status
  4. Return retention decision
- **Expected Output**: Retention status {retained: true, reason: "within_10_year_policy"}
- **Expected State Changes**: None (read-only)
- **Expected Events**: Retention check event
- **Expected Audit**: Retention check logged
- **Expected Observability**: Retention metrics
- **Expected External Interactions**: None
- **Cleanup**: None
- **Branch IDs Covered**: retention_enforced, policy_compliant
- **Statement Groups Covered**: retention_manager, age_calculator

---

## 7. Real-World Scenario Suites

### Suite RW_001: Complete Trading Audit Trail
- **Suite ID**: RW_001
- **Business Narrative**: Complete audit trail for trade lifecycle with maker-checker approvals
- **Actors**: Trader, Approver, K-07 Audit Framework, Trading System
- **Preconditions**: Trading system active, maker-checker workflow configured
- **Timeline**: 1 trade lifecycle
- **Exact Input Set**: Trade creation, modification, approval events
- **Expected Outputs per Step**:
  1. Trade creation audit event recorded
  2. Trade modification audit events recorded
  3. Maker-checker approval audit events recorded
  4. Complete audit trail verifiable
  5. Evidence export available
- **Expected Failure Variants**: Missing approvals, tampered records
- **Expected Recovery Variants**: Audit alerts, investigation procedures

### Suite RW_002: Regulatory Audit Export
- **Suite ID**: RW_002
- **Business Narrative**: Export audit evidence for regulatory investigation
- **Actors**: Compliance Officer, K-07 Audit Framework, Regulator
- **Preconditions**: Audit data populated, export permissions configured
- **Timeline**: 1 investigation period
- **Exact Input Set**: Investigation parameters, date ranges, filters
- **Expected Outputs per Step**:
  1. Export job created and validated
  2. Evidence package generated
  3. Chain integrity verified
  4. Package delivered securely
- **Expected Failure Variants**: Unauthorized access, incomplete data
- **Expected Recovery Variants**: Access denied alerts, data补齐 procedures

---

## 8. Coverage Matrices

### Requirement Coverage Matrix
| Requirement ID | Test Cases | Coverage Status |
|----------------|------------|-----------------|
| REQ_AE_001 | AE_TC_001-005 | 100% |
| REQ_AP_001 | AP_TC_001-005 | 100% |
| REQ_HV_001 | HV_TC_001-005 | 100% |
| REQ_AS_001 | AS_TC_001-005 | 100% |
| REQ_EE_001 | EE_TC_001-005 | 100% |
| REQ_MC_001 | MC_TC_001-004 | 100% |
| REQ_RC_001 | RC_TC_001-004 | 100% |

### Branch Coverage Matrix
| Branch ID | Test Cases | Coverage Status |
|-----------|------------|-----------------|
| validation_success | AE_TC_001, AE_TC_004-005 | 100% |
| validation_failure | AE_TC_002-003 | 100% |
| append_success | AP_TC_001-003, AP_TC_005 | 100% |
| immutability_enforced | AP_TC_004 | 100% |
| verification_success | HV_TC_001-002, HV_TC_005 | 100% |
| tampering_detected | HV_TC_003-004 | 100% |
| search_success | AS_TC_001-003, AS_TC_005 | 100% |
| tenant_isolation_enforced | AS_TC_004 | 100% |
| export_success | EE_TC_001-004 | 100% |
| export_rejected | EE_TC_005 | 100% |
| maker_checker_enforced | MC_TC_001-004 | 100% |
| retention_enforced | RC_TC_001-004 | 100% |

### Statement Coverage Matrix
| Statement Group | Test Cases | Coverage Status |
|-----------------|------------|-----------------|
| audit_validator | AE_TC_001-005 | 100% |
| audit_store | AP_TC_001-005 | 100% |
| chain_verifier | HV_TC_001-005 | 100% |
| audit_search | AS_TC_001-005 | 100% |
| export_manager | EE_TC_001-005 | 100% |
| maker_checker_linkage | MC_TC_001-004 | 100% |
| retention_manager | RC_TC_001-004 | 100% |

---

## 9. Coverage Gaps and Exclusions

**No known gaps** - All identified behaviors and scenarios are covered by test cases.

**Exclusions:**
- Advanced analytics on audit data (deferred to K-06)
- Long-term archival strategies (future enhancement)
- Real-time audit monitoring (deferred to K-06)
- External audit system integration (future enhancement)

---

## 10. Recommended Test File Plan

### Unit Tests
- `src/test/java/com/siddhanta/audit/AuditEventValidatorTest.java`
- `src/test/java/com/siddhanta/audit/SequenceGeneratorTest.java`
- `src/test/java/com/siddhanta/audit/HashComputerTest.java`
- `src/test/java/com/siddhanta/audit/ChainVerifierTest.java`
- `src/test/java/com/siddhanta/audit/ExportRequestValidatorTest.java`

### Component Tests
- `src/test/java/com/siddhanta/audit/AuditStoreComponentTest.java`
- `src/test/java/com/siddhanta/audit/AuditSearchComponentTest.java`
- `src/test/java/com/siddhanta/audit/ExportManagerComponentTest.java`
- `src/test/java/com/siddhanta/audit/RetentionManagerComponentTest.java`

### Integration Tests
- `src/test/java/com/siddhanta/audit/AuditFrameworkIntegrationTest.java`
- `src/test/java/com/siddhanta/audit/K05IntegrationTest.java`
- `src/test/java/com/siddhanta/audit/MakerCheckerIntegrationTest.java`
- `src/test/java/com/siddhanta/audit/EndToEndAuditTest.java`

### Security Tests
- `src/test/java/com/siddhanta/audit/TamperingDetectionTest.java`
- `src/test/java/com/siddhanta/audit/TenantIsolationTest.java`
- `src/test/java/com/siddhanta/audit/AuthorizationTest.java`
- `src/test/java/com/siddhanta/audit/DataLeakageTest.java`

### Compliance Tests
- `src/test/java/com/siddhanta/audit/RetentionPolicyTest.java`
- `src/test/java/com/siddhanta/audit/RegulatoryComplianceTest.java`
- `src/test/java/com/siddhanta/audit/EvidenceExportTest.java`
- `src/test/java/com/siddhanta/audit/ChainIntegrityTest.java`

---

## 11. Machine-Readable Appendix

```yaml
test_plan:
  scope: k07_audit_framework
  modules:
    - audit_event_management
    - audit_persistence
    - hash_chain_verification
    - audit_search
    - evidence_export
    - maker_checker_integration
    - retention_and_compliance
  cases:
    - id: AE_TC_001
      title: Validate complete valid audit event
      layer: unit
      module: audit_event_management
      scenario_type: happy_path
      requirement_refs: [LLD_K07_001]
      source_refs: [LLD_K07_AUDIT_FRAMEWORK.md]
      preconditions: [audit_validator_available]
      fixtures: [valid_audit_event]
      input: {audit_event: {actor: "user123", resource_id: "order-456", action: "CREATE", outcome: "SUCCESS", details: {...}}}
      steps:
        - validate_schema_structure
        - check_required_fields_presence
        - validate_timestamp_formats
        - check_actor_permissions
        - return_validation_result
      expected_output: {status: "valid", normalized_event: {...}}
      expected_state_changes: [event_marked_as_valid]
      expected_events: [validation_success_event]
      expected_audit: [validation_logged]
      expected_observability: [validation_metrics]
      expected_external_interactions: [user_directory]
      cleanup: []
      branch_ids_covered: [validation_success, schema_valid]
      statement_groups_covered: [audit_validator, schema_checker]
    
    - id: AP_TC_001
      title: Append audit event successfully
      layer: component
      module: audit_persistence
      scenario_type: happy_path
      requirement_refs: [LLD_K07_001]
      source_refs: [LLD_K07_AUDIT_FRAMEWORK.md]
      preconditions: [audit_store_initialized, validator_available]
      fixtures: [valid_audit_event, database_connection]
      input: {validated_event: {...}}
      steps:
        - generate_sequence_number
        - compute_previous_hash
        - compute_current_hash
        - append_to_audit_table
        - return_success_confirmation
      expected_output: {status: "stored", sequence: 123, previous_hash: "abc123", current_hash: "def456"}
      expected_state_changes: [new_audit_row_in_database]
      expected_events: [audit_stored_event]
      expected_audit: [audit_append_logged]
      expected_observability: [append_metrics]
      expected_external_interactions: [postgresql]
      cleanup: [remove_test_audit_record]
      branch_ids_covered: [append_success, hash_computation]
      statement_groups_covered: [audit_store, sequence_generator, hash_computer]
    
    - id: HV_TC_001
      title: Verify full audit chain integrity
      layer: unit
      module: hash_chain_verification
      scenario_type: happy_path
      requirement_refs: [LLD_K07_001]
      source_refs: [LLD_K07_AUDIT_FRAMEWORK.md]
      preconditions: [audit_chain_populated]
      fixtures: [test_audit_chain]
      input: {verification_request: {range: "full"}}
      steps:
        - read_all_audit_records
        - verify_hash_sequence
        - check_for_gaps_or_tampering
        - return_verification_result
      expected_output: {status: "verified", integrity: "intact", records_verified: 1000}
      expected_state_changes: []
      expected_events: [chain_verification_event]
      expected_audit: [verification_logged]
      expected_observability: [verification_metrics]
      expected_external_interactions: [postgresql]
      cleanup: []
      branch_ids_covered: [verification_success, chain_intact]
      statement_groups_covered: [chain_verifier, hash_validator]
    
    - id: AS_TC_001
      title: Search with exact filters
      layer: integration
      module: audit_search
      scenario_type: happy_path
      requirement_refs: [LLD_K07_001]
      source_refs: [LLD_K07_AUDIT_FRAMEWORK.md]
      preconditions: [audit_data_populated, search_service_available]
      fixtures: [test_audit_records, search_filters]
      input: {search_request: {actor: "user123", date_range: {start: "2023-01-01", end: "2023-01-31"}}}
      steps:
        - validate_search_filters
        - enforce_tenant_isolation
        - query_audit_database
        - apply_pagination
        - return_search_results
      expected_output: {results: [...], total_count: 25, has_more: false}
      expected_state_changes: []
      expected_events: [search_executed_event]
      expected_audit: [search_logged]
      expected_observability: [search_metrics]
      expected_external_interactions: [postgresql]
      cleanup: []
      branch_ids_covered: [search_success, exact_filters]
      statement_groups_covered: [audit_search, filter_processor, pagination_manager]
    
    - id: EE_TC_001
      title: Create export job successfully
      layer: integration
      module: evidence_export
      scenario_type: happy_path
      requirement_refs: [LLD_K07_001]
      source_refs: [LLD_K07_AUDIT_FRAMEWORK.md]
      preconditions: [export_manager_available, audit_data_exists]
      fixtures: [export_request, test_audit_records]
      input: {export_request: {date_range: {start: "2023-01-01", end: "2023-01-31"}, format: "JSON", filters: {...}}}
      steps:
        - validate_export_request
        - check_export_permissions
        - create_export_job
        - schedule_job_processing
        - return_job_id
      expected_output: {job_id: "export-123", status: "scheduled", estimated_completion: "2023-01-01T02:00:00Z"}
      expected_state_changes: [export_job_record_created]
      expected_events: [export_job_created_event]
      expected_audit: [export_request_logged]
      expected_observability: [export_metrics]
      expected_external_interactions: [postgresql, file_storage]
      cleanup: [remove_export_job_and_files]
      branch_ids_covered: [export_creation_success, request_valid]
      statement_groups_covered: [export_manager, job_scheduler]
    
    - id: MC_TC_001
      title: Link audit events to workflow
      layer: integration
      module: maker_checker_integration
      scenario_type: happy_path
      requirement_refs: [LLD_K07_001]
      source_refs: [LLD_K07_AUDIT_FRAMEWORK.md]
      preconditions: [maker_checker_workflow_available]
      fixtures: [workflow_instance, audit_events]
      input: {linkage_request: {workflow_id: "wf-123", audit_event_ids: ["ae-456", "ae-789"]}}
      steps:
        - validate_workflow_exists
        - check_audit_event_ownership
        - create_linkage_records
        - update_workflow_audit_trail
        - return_linkage_confirmation
      expected_output: {status: "linked", workflow_id: "wf-123", linked_events: 2}
      expected_state_changes: [linkage_records_created]
      expected_events: [linkage_created_event]
      expected_audit: [linkage_logged]
      expected_observability: [linkage_metrics]
      expected_external_interactions: [workflow_service, audit_store]
      cleanup: [remove_linkage_records]
      branch_ids_covered: [linkage_success, workflow_valid]
      statement_groups_covered: [maker_checker_linkage, workflow_validator]
    
    - id: RC_TC_001
      title: Enforce 10-year retention policy
      layer: integration
      module: retention_and_compliance
      scenario_type: happy_path
      requirement_refs: [REG_ARCH_001]
      source_refs: [REGULATORY_ARCHITECTURE_DOCUMENT.md]
      preconditions: [retention_manager_configured, audit_data_exists]
      fixtures: [test_audit_records_with_various_ages]
      input: {retention_check_request: {audit_record_id: "ar-123", current_date: "2023-01-01"}}
      steps:
        - calculate_record_age
        - compare_to_retention_policy
        - determine_retention_status
        - return_retention_decision
      expected_output: {retained: true, reason: "within_10_year_policy", expiry_date: "2033-01-01"}
      expected_state_changes: []
      expected_events: [retention_check_event]
      expected_audit: [retention_check_logged]
      expected_observability: [retention_metrics]
      expected_external_interactions: []
      cleanup: []
      branch_ids_covered: [retention_enforced, policy_compliant]
      statement_groups_covered: [retention_manager, age_calculator]

  coverage:
    requirement_ids:
      REQ_AE_001: [AE_TC_001, AE_TC_002, AE_TC_003, AE_TC_004, AE_TC_005]
      REQ_AP_001: [AP_TC_001, AP_TC_002, AP_TC_003, AP_TC_004, AP_TC_005]
      REQ_HV_001: [HV_TC_001, HV_TC_002, HV_TC_003, HV_TC_004, HV_TC_005]
      REQ_AS_001: [AS_TC_001, AS_TC_002, AS_TC_003, AS_TC_004, AS_TC_005]
      REQ_EE_001: [EE_TC_001, EE_TC_002, EE_TC_003, EE_TC_004, EE_TC_005]
      REQ_MC_001: [MC_TC_001, MC_TC_002, MC_TC_003, MC_TC_004]
      REQ_RC_001: [RC_TC_001, RC_TC_002, RC_TC_003, RC_TC_004]
    branch_ids:
      validation_success: [AE_TC_001, AE_TC_004, AE_TC_005]
      validation_failure: [AE_TC_002, AE_TC_003]
      append_success: [AP_TC_001, AP_TC_002, AP_TC_003, AP_TC_005]
      immutability_enforced: [AP_TC_004]
      verification_success: [HV_TC_001, HV_TC_002, HV_TC_005]
      tampering_detected: [HV_TC_003, HV_TC_004]
      search_success: [AS_TC_001, AS_TC_002, AS_TC_003, AS_TC_005]
      tenant_isolation_enforced: [AS_TC_004]
      export_success: [EE_TC_001, EE_TC_002, EE_TC_003, EE_TC_004]
      export_rejected: [EE_TC_005]
      maker_checker_enforced: [MC_TC_001, MC_TC_002, MC_TC_003, MC_TC_004]
      retention_enforced: [RC_TC_001, RC_TC_002, RC_TC_003, RC_TC_004]
    statement_groups:
      audit_validator: [AE_TC_001, AE_TC_002, AE_TC_003, AE_TC_004, AE_TC_005]
      audit_store: [AP_TC_001, AP_TC_002, AP_TC_003, AP_TC_004, AP_TC_005]
      chain_verifier: [HV_TC_001, HV_TC_002, HV_TC_003, HV_TC_004, HV_TC_005]
      audit_search: [AS_TC_001, AS_TC_002, AS_TC_003, AS_TC_004, AS_TC_005]
      export_manager: [EE_TC_001, EE_TC_002, EE_TC_003, EE_TC_004, EE_TC_005]
      maker_checker_linkage: [MC_TC_001, MC_TC_002, MC_TC_003, MC_TC_004]
      retention_manager: [RC_TC_001, RC_TC_002, RC_TC_003, RC_TC_004]
  exclusions: []
```

---

**K-07 Audit Framework TDD specification complete.** This provides exhaustive test coverage for immutable audit append, cryptographic chaining, search, export, verification, and maker-checker linkage. The specification is ready for test implementation and subsequent code generation to satisfy these tests.
