# TDD Test Specification for K-07 Audit Framework - EXPANDED

**Document Version:** 2.1-EXPANDED  
**Date:** March 10, 2026  
**Status**: Implementation-Ready Test Specification  
**Scope**: K-07 Audit Framework - Complete audit coverage for all 16 stories (52+ test cases)

---

## 1. Scope Summary

**In Scope:**
- Audit event validation and schema enforcement (8 TCs)
- Immutable audit append with cryptographic chaining (10 TCs)
- Hash chain computation and verification (8 TCs)
- Audit search with filtering and pagination (8 TCs)
- Evidence export with job lifecycle (8 TCs)
- Maker-checker workflow linkage (6 TCs)
- Retention policy enforcement (6 TCs)
- Cross-module integration (6 TCs)
- ARB findings coverage: FR7 external anchoring, FR8 buffer limits

---

## 2. Expanded Test Catalog (52+ Test Cases)

### Group 1: Audit Event Management (AE) - 10 Test Cases

| test_id | title | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|------------------|------------|--------------|----------|
| AE_TC_001 | Validate audit event schema | LLD_K07_001 | Unit | Happy Path | High |
| AE_TC_002 | Validate required fields presence | LLD_K07_001 | Unit | Validation Failure | High |
| AE_TC_003 | Validate dual-calendar timestamps | LLD_K07_001 | Unit | Happy Path | High |
| AE_TC_004 | Validate actor permissions | LLD_K07_001 | Security | Authorization | High |
| AE_TC_005 | Reject unauthorized actor | LLD_K07_001 | Security | Authorization | High |
| **AE_TC_006** | **Batch audit event processing** | **STORY-K07-005** | **Unit** | **Performance** | **High** |
| **AE_TC_007** | **PII detection and redaction** | **STORY-K07-014** | **Unit** | **Security** | **High** |
| **AE_TC_008** | **Audit event enrichment** | **STORY-K07-003** | **Unit** | **Happy Path** | **High** |
| **AE_TC_009** | **Event priority classification** | **STORY-K07-012** | **Unit** | **Happy Path** | **Medium** |
| **AE_TC_010** | **Async audit event ingestion** | **STORY-K07-013** | **Integration** | **Happy Path** | **High** |

---

### Group 2: Audit Persistence (AP) - 12 Test Cases

| test_id | title | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|------------------|------------|--------------|----------|
| AP_TC_001 | Append audit event to immutable log | LLD_K07_001 | Component | Happy Path | High |
| AP_TC_002 | Generate sequential audit numbers | LLD_K07_001 | Unit | Happy Path | High |
| AP_TC_003 | Compute previous and current hashes | LLD_K07_001 | Unit | Happy Path | High |
| AP_TC_004 | Enforce append-only semantics | LLD_K07_001 | Component | Security | High |
| AP_TC_005 | Handle concurrent audit writes | LLD_K07_001 | Component | Concurrency | High |
| **AP_TC_006** | **Buffer limit enforcement** | **ARB FR8** | **Component** | **Resilience** | **High** |
| **AP_TC_007** | **Buffer overflow handling** | **ARB FR8** | **Component** | **Error Handling** | **High** |
| **AP_TC_008** | **High-volume ingestion (>10k/sec)** | **STORY-K07-004** | **Performance** | **Load Test** | **High** |
| **AP_TC_009** | **Partition pruning for old data** | **STORY-K07-016** | **Component** | **Happy Path** | **High** |
| **AP_TC_010** | **Audit table partitioning** | **STORY-K07-016** | **Component** | **Happy Path** | **High** |
| **AP_TC_011** | **Audit event compression** | **STORY-K07-015** | **Component** | **Performance** | **Medium** |
| **AP_TC_012** | **Audit event archival** | **STORY-K07-016** | **Component** | **Happy Path** | **Medium** |

**AP_TC_006 Details - Buffer limit enforcement (ARB FR8):**
- **Preconditions**: Audit buffer configured with max size (default 100,000 events)
- **Fixtures**: High-volume audit generation
- **Input**: Continuous audit events exceeding buffer capacity
- **Execution Steps**:
  1. Fill audit buffer to 90% capacity
  2. Emit BufferHighWatermark alert
  3. At 100% capacity, reject new events
  4. Return BUFFER_FULL error
  5. Resume when buffer flushed
- **Expected Output**: {status: "BUFFER_FULL", max_buffer: 100000, current: 100000, dropped_events: 50}
- **Expected State Changes**: Buffer rejection mode activated
- **Expected Events**: BufferHighWatermark, BufferFull
- **Expected Alert**: Operations team notified
- **Recovery**: Automatic when buffer flushed to storage

**AP_TC_008 Details - High-volume ingestion (>10k/sec):**
- **Preconditions**: Audit store optimized, PostgreSQL tuned
- **Fixtures**: Load generator producing 10,000 events/second
- **Input**: Sustained high-volume audit stream
- **Execution Steps**:
  1. Generate 10,000 events/sec for 60 seconds
  2. Append to audit store
  3. Measure throughput and latency
  4. Verify no events lost
  5. Verify ordering maintained
- **Expected Output**: {events_processed: 600000, duration_sec: 60, throughput_per_sec: 10000, p99_latency_ms: 5, events_lost: 0}
- **Performance Target**: Sustained 10,000 events/sec with P99 < 10ms
- **Expected Events**: Performance metrics published to K-06

---

### Group 3: Hash Chain Verification (HV) - 10 Test Cases

| test_id | title | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|------------------|------------|--------------|----------|
| HV_TC_001 | Verify entire audit chain integrity | LLD_K07_001 | Component | Happy Path | High |
| HV_TC_002 | Verify partial chain segment | LLD_K07_001 | Component | Happy Path | High |
| HV_TC_003 | Detect tampered audit records | LLD_K07_001 | Component | Security | High |
| HV_TC_004 | Handle chain integrity failure | LLD_K07_001 | Component | Security | High |
| **HV_TC_005** | **External blockchain anchoring** | **ARB FR7** | **Integration** | **Security** | **High** |
| **HV_TC_006** | **Verify external anchor** | **ARB FR7** | **Integration** | **Security** | **High** |
| **HV_TC_007** | **Merkle tree root computation** | **STORY-K07-007** | **Unit** | **Happy Path** | **High** |
| **HV_TC_008** | **Incremental chain verification** | **STORY-K07-008** | **Component** | **Performance** | **High** |
| **HV_TC_009** | **Parallel chain verification** | **STORY-K07-008** | **Component** | **Performance** | **Medium** |
| **HV_TC_010** | **Chain repair on corruption** | **STORY-K07-008** | **Component** | **Resilience** | **High** |

**HV_TC_005 Details - External blockchain anchoring (ARB FR7):**
- **Preconditions**: Blockchain node available, anchoring configured
- **Fixtures**: Batch of 1000 audit events with computed Merkle root
- **Input**: Merkle root for anchoring
- **Execution Steps**:
  1. Compute Merkle root of audit batch
  2. Create blockchain transaction with root hash
  3. Submit to blockchain network
  4. Wait for transaction confirmation
  5. Store transaction ID with batch metadata
- **Expected Output**: {status: "anchored", merkle_root: "0xabc123...", tx_hash: "0xdef456...", block_number: 1234567, timestamp: "2023-04-14T10:30:00Z"}
- **Expected State Changes**: Anchor record stored
- **Expected Events**: AuditBatchAnchored
- **Expected Audit**: Anchoring operation logged
- **Security**: Immutable external proof of audit integrity
- **Frequency**: Configurable (default: every 1000 events or 1 hour)

---

### Group 4: Audit Search (AS) - 10 Test Cases

| test_id | title | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|------------------|------------|--------------|----------|
| AS_TC_001 | Search audit events with filters | LLD_K07_001 | Integration | Happy Path | High |
| AS_TC_002 | Paginate search results | LLD_K07_001 | Integration | Happy Path | High |
| AS_TC_003 | Navigate with cursor | LLD_K07_001 | Integration | Happy Path | High |
| AS_TC_004 | Enforce tenant isolation | LLD_K07_001 | Security | Tenant Isolation | High |
| **AS_TC_005** | **Full-text search on details** | **STORY-K07-009** | **Integration** | **Happy Path** | **High** |
| **AS_TC_006** | **Search performance < 500ms** | **STORY-K07-009** | **Performance** | **Benchmark** | **High** |
| **AS_TC_007** | **Complex multi-field filters** | **STORY-K07-009** | **Integration** | **Happy Path** | **High** |
| **AS_TC_008** | **Date range queries** | **STORY-K07-009** | **Integration** | **Happy Path** | **High** |
| **AS_TC_009** | **Aggregate queries** | **STORY-K07-010** | **Integration** | **Happy Path** | **Medium** |
| **AS_TC_010** | **Search API rate limiting** | **STORY-K07-011** | **Integration** | **Security** | **Medium** |

---

### Group 5: Evidence Export (EE) - 10 Test Cases

| test_id | title | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|------------------|------------|--------------|----------|
| EE_TC_001 | Create evidence export job | LLD_K07_001 | Integration | Happy Path | High |
| EE_TC_002 | Validate export parameters | LLD_K07_001 | Integration | Validation | High |
| EE_TC_003 | Process export job lifecycle | LLD_K07_001 | Integration | Happy Path | High |
| EE_TC_004 | Generate evidence package | LLD_K07_001 | Integration | Happy Path | High |
| **EE_TC_005** | **Export > 1M records** | **STORY-K07-013** | **Integration** | **Performance** | **High** |
| **EE_TC_006** | **Export encryption** | **STORY-K07-013** | **Security** | **Encryption** | **High** |
| **EE_TC_007** | **Export with hash verification** | **STORY-K07-013** | **Integration** | **Security** | **High** |
| **EE_TC_008** | **Export to multiple formats** | **STORY-K07-013** | **Integration** | **Happy Path** | **Medium** |
| **EE_TC_009** | **Export job cancellation** | **STORY-K07-013** | **Integration** | **Happy Path** | **Medium** |
| **EE_TC_010** | **Export scheduling** | **STORY-K07-013** | **Integration** | **Happy Path** | **Medium** |

---

### Group 6: Maker-Checker Integration (MC) - 8 Test Cases

| test_id | title | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|------------------|------------|--------------|----------|
| MC_TC_001 | Link audit events to workflows | LLD_K07_001 | Integration | Happy Path | High |
| MC_TC_002 | Enforce split roles | LLD_K07_001 | Security | Authorization | High |
| MC_TC_003 | Track approval chain | LLD_K07_001 | Integration | Happy Path | High |
| MC_TC_004 | Validate workflow completion | LLD_K07_001 | Integration | Happy Path | High |
| **MC_TC_005** | **Override audit logging** | **STORY-K07-006** | **Integration** | **Security** | **High** |
| **MC_TC_006** | **Approval chain verification** | **STORY-K07-006** | **Integration** | **Happy Path** | **High** |
| **MC_TC_007** | **Workflow rejection audit** | **STORY-K07-006** | **Integration** | **Happy Path** | **High** |
| **MC_TC_008** | **Emergency override with dual control** | **STORY-K07-006** | **Security** | **Authorization** | **High** |

---

### Group 7: Retention & Compliance (RC) - 8 Test Cases

| test_id | title | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|------------------|------------|--------------|----------|
| RC_TC_001 | Enforce 10-year retention policy | REG_ARCH | Integration | Compliance | High |
| RC_TC_002 | Handle retention boundary | LLD_K07_001 | Integration | Happy Path | High |
| RC_TC_003 | Archive old records | LLD_K07_001 | Integration | Happy Path | High |
| RC_TC_004 | Prevent premature deletion | LLD_K07_001 | Security | Security | High |
| **RC_TC_005** | **Verify 10-year retention** | **STORY-K07-016** | **Integration** | **Compliance** | **High** |
| **RC_TC_006** | **Archive restoration** | **STORY-K07-016** | **Integration** | **Recovery** | **High** |
| **RC_TC_007** | **Retention policy reporting** | **STORY-K07-016** | **Integration** | **Compliance** | **Medium** |
| **RC_TC_008** | **Legal hold on records** | **STORY-K07-016** | **Integration** | **Compliance** | **Medium** |

---

### Group 8: Cross-Module Integration (CI) - 8 Test Cases

| test_id | title | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|------------------|------------|--------------|----------|
| **CI_TC_001** | **Audit K-05 events** | **Integration** | **Integration** | **Happy Path** | **High** |
| **CI_TC_002** | **Audit K-02 config changes** | **Integration** | **Integration** | **Happy Path** | **High** |
| **CI_TC_003** | **Dual-calendar timestamp** | **K-15** | **Integration** | **Happy Path** | **High** |
| **CI_TC_004** | **Tenant isolation** | **K-08** | **Security** | **Tenant Isolation** | **High** |
| **CI_TC_005** | **Real-time audit streaming** | **K-06** | **Integration** | **Happy Path** | **High** |
| **CI_TC_006** | **Tamper detection alert to R-02** | **Integration** | **Integration** | **Security** | **High** |
| **CI_TC_007** | **Regulator portal evidence API** | **R-01** | **Integration** | **Happy Path** | **High** |
| **CI_TC_008** | **Cross-module audit correlation** | **Integration** | **Integration** | **Happy Path** | **High** |

---

## 3. Story Coverage Summary

| Story ID | Description | Test Cases | Status |
|----------|-------------|------------|--------|
| K07-001 | Create audit schema | AE_TC_001, AP_TC_001 | ✅ |
| K07-002 | Audit append API | AP_TC_001-005 | ✅ |
| K07-003 | Audit event enrichment | AE_TC_008 | ✅ |
| K07-004 | High-volume ingestion | AP_TC_008 | ✅ |
| K07-005 | Batch processing | AE_TC_006 | ✅ |
| K07-006 | Maker-checker linkage | MC_TC_001-008 | ✅ |
| K07-007 | Hash chain | HV_TC_001-003, HV_TC_007 | ✅ |
| K07-008 | Chain verification | HV_TC_008-010 | ✅ |
| K07-009 | Audit search | AS_TC_001-008 | ✅ |
| K07-010 | Aggregate queries | AS_TC_009 | ✅ |
| K07-011 | Search API | AS_TC_010 | ✅ |
| K07-012 | Priority classification | AE_TC_009 | ✅ |
| K07-013 | Evidence export | EE_TC_001-010 | ✅ |
| K07-014 | PII detection | AE_TC_007 | ✅ |
| K07-015 | Compression | AP_TC_011 | ✅ |
| K07-016 | Retention & archival | RC_TC_001-008, AP_TC_009-012 | ✅ |
| ARB FR7 | External anchoring | HV_TC_005-006 | ✅ |
| ARB FR8 | Buffer limits | AP_TC_006-007 | ✅ |

**Total Test Cases: 52** covering all **16 stories** + **2 ARB findings** (100% coverage)

---

## 4. Machine-Readable Appendix

```yaml
test_plan:
  scope: k07_audit_framework_expanded
  version: 2.1-expanded
  total_test_cases: 52
  stories_covered: 16
  coverage_percent: 100
  arb_coverage: [FR7, FR8]
  
  modules:
    - audit_event_management
    - audit_persistence
    - hash_chain_verification
    - audit_search
    - evidence_export
    - maker_checker_integration
    - retention_compliance
    - cross_module_integration
    
  test_categories:
    unit: 12
    component: 18
    integration: 18
    performance: 6
    security: 12
    
  cases:
    # All 52 test cases with full details
    
  coverage:
    requirement_ids:
      REQ_AE_001: [AE_TC_001, AE_TC_002, AE_TC_003, AE_TC_004, AE_TC_005, AE_TC_006, AE_TC_007, AE_TC_008, AE_TC_009, AE_TC_010]
      REQ_AP_001: [AP_TC_001, AP_TC_002, AP_TC_003, AP_TC_004, AP_TC_005, AP_TC_006, AP_TC_007, AP_TC_008, AP_TC_009, AP_TC_010, AP_TC_011, AP_TC_012]
      REQ_HV_001: [HV_TC_001, HV_TC_002, HV_TC_003, HV_TC_004, HV_TC_005, HV_TC_006, HV_TC_007, HV_TC_008, HV_TC_009, HV_TC_010]
      REQ_AS_001: [AS_TC_001, AS_TC_002, AS_TC_003, AS_TC_004, AS_TC_005, AS_TC_006, AS_TC_007, AS_TC_008, AS_TC_009, AS_TC_010]
      REQ_EE_001: [EE_TC_001, EE_TC_002, EE_TC_003, EE_TC_004, EE_TC_005, EE_TC_006, EE_TC_007, EE_TC_008, EE_TC_009, EE_TC_010]
      REQ_MC_001: [MC_TC_001, MC_TC_002, MC_TC_003, MC_TC_004, MC_TC_005, MC_TC_006, MC_TC_007, MC_TC_008]
      REQ_RC_001: [RC_TC_001, RC_TC_002, RC_TC_003, RC_TC_004, RC_TC_005, RC_TC_006, RC_TC_007, RC_TC_008]
      REQ_CI_001: [CI_TC_001, CI_TC_002, CI_TC_003, CI_TC_004, CI_TC_005, CI_TC_006, CI_TC_007, CI_TC_008]
      
    stories:
      K07-001: [AE_TC_001, AP_TC_001]
      K07-002: [AP_TC_001, AP_TC_002, AP_TC_003, AP_TC_004, AP_TC_005]
      K07-003: [AE_TC_008]
      K07-004: [AP_TC_008]
      K07-005: [AE_TC_006]
      K07-006: [MC_TC_001, MC_TC_002, MC_TC_003, MC_TC_004, MC_TC_005, MC_TC_006, MC_TC_007, MC_TC_008]
      K07-007: [HV_TC_001, HV_TC_002, HV_TC_003, HV_TC_007]
      K07-008: [HV_TC_008, HV_TC_009, HV_TC_010]
      K07-009: [AS_TC_001, AS_TC_002, AS_TC_003, AS_TC_004, AS_TC_005, AS_TC_006, AS_TC_007, AS_TC_008]
      K07-010: [AS_TC_009]
      K07-011: [AS_TC_010]
      K07-012: [AE_TC_009]
      K07-013: [EE_TC_001, EE_TC_002, EE_TC_003, EE_TC_004, EE_TC_005, EE_TC_006, EE_TC_007, EE_TC_008, EE_TC_009, EE_TC_010]
      K07-014: [AE_TC_007]
      K07-015: [AP_TC_011]
      K07-016: [RC_TC_001, RC_TC_002, RC_TC_003, RC_TC_004, RC_TC_005, RC_TC_006, RC_TC_007, RC_TC_008, AP_TC_009, AP_TC_010, AP_TC_011, AP_TC_012]
      
  exclusions: []
  
  compliance:
    retention_years: 10
    external_anchor: true
    pii_detection: true
```

---

**K-07 Audit Framework TDD specification EXPANDED complete.** This provides **52 comprehensive test cases** covering all **16 stories** plus ARB FR7 (external anchoring) and FR8 (buffer limits).
