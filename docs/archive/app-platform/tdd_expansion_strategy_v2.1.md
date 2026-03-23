# TDD Test Specification Expansion Strategy

**Document Version:** 2.1  
**Date:** March 10, 2026  
**Scope:** Comprehensive expansion from 294+ to 600+ test cases covering all 654 stories

---

## Current State vs Target

| Phase                       | Current TCs        | Target TCs | Stories | Gap      |
| --------------------------- | ------------------ | ---------- | ------- | -------- |
| Phase 0 (Bootstrap)         | 15                 | 30         | N/A     | +15      |
| Phase 1 (Kernel Foundation) | 134                | 200        | 78      | +66      |
| Phase 2 (Kernel Completion) | 64                 | 150        | 147     | +86      |
| Phase 3 (Trading MVP)       | 40                 | 150        | 125     | +110     |
| Phase 4/5 (Operational)     | 40                 | 100        | 104     | +60      |
| Phase 4 (Testing/Chaos)     | 0 (included above) | 30         | 30      | +30      |
| **TOTAL**                   | **294**            | **660**    | **654** | **+366** |

---

## Expansion Strategy by Module

### Phase 0: Bootstrap (15 → 30 test cases)

**New Test Categories:**

- Repository structure validation (5 additional tests)
- Service template edge cases (5 additional tests)
- Contract validation comprehensive scenarios (5 additional tests)
- CI/CD pipeline failure scenarios (5 additional tests)

### Phase 1: Kernel Foundation (134 → 200 test cases)

#### K-05 Event Bus (35 → 60 test cases, 32 stories)

**Additional Coverage:**

- Partition management tests (5)
- Consumer group rebalancing (5)
- Schema evolution edge cases (5)
- Saga compensation scenarios (5)
- DLQ management (5)
- Performance under load (5)

#### K-02 Configuration Engine (34 → 55 test cases, 17 stories)

**Additional Coverage:**

- Hierarchical resolution edge cases (5)
- Maker-checker workflow variations (5)
- Temporal resolution boundaries (5)
- Cache invalidation scenarios (5)
- T1 pack signature failures (3)
- Hot reload stress tests (3)

#### K-07 Audit Framework (32 → 50 test cases, 16 stories)

**Additional Coverage:**

- Hash chain verification edge cases (5)
- Export job lifecycle variations (5)
- Retention policy enforcement (5)
- Concurrent audit writes (3)
- Audit search performance (3)
- Tamper detection scenarios (2)

#### K-15 Dual-Calendar (33 → 45 test cases, 13 stories)

**Additional Coverage:**

- Leap year edge cases (5)
- Fiscal year boundary conditions (3)
- Holiday calendar update propagation (3)
- Settlement calculation edge cases (3)
- Batch conversion performance (3)
- Invalid date handling (3)

### Phase 2: Kernel Completion (64 → 150 test cases)

#### K-01 IAM (4 → 20 test cases, 23 stories)

**New Coverage:**

- Multi-factor authentication scenarios
- Session management edge cases
- Token refresh and revocation
- RBAC permission inheritance
- Approval rate limiting (ARB FR11)
- Anomaly detection (ARB FR12)

#### K-14 Secrets Management (4 → 15 test cases, 14 stories)

**New Coverage:**

- Secret rotation scenarios
- Encryption key lifecycle
- Secret access patterns
- Audit and compliance

#### K-03 Rules Engine (4 → 25 test cases, 14 stories)

**New Coverage:**

- OPA/Rego integration scenarios
- Policy evaluation performance
- Rule caching strategies
- Circuit breaker degraded mode (ARB FR9)
- Mid-session rule deployment (ARB FR10)

#### K-04 Plugin Runtime (4 → 20 test cases, 15 stories)

**New Coverage:**

- Plugin isolation verification
- Resource quota enforcement (ARB FR9)
- Exfiltration prevention (ARB FR10)
- Plugin signature verification
- Sandbox escape attempts

#### K-06 Observability (4 → 25 test cases, 22 stories)

**New Coverage:**

- Metrics aggregation scenarios
- Distributed tracing edge cases
- Log correlation patterns
- ML PII detection (ARB FR9)
- SLI/SLO tracking (ARB FR10)

#### K-18 Resilience Patterns (4 → 20 test cases, 13 stories)

**New Coverage:**

- Circuit breaker state transitions
- Retry backoff strategies
- Bulkhead isolation
- Timeout handling
- Fallback scenarios

#### K-19 DLQ Management (4 → 20 test cases, 15 stories)

**New Coverage:**

- Poison message handling
- DLQ replay scenarios
- Message classification
- Root cause analysis

#### K-16 Ledger Framework (4 → 25 test cases, 19 stories)

**New Coverage:**

- Double-entry verification
- Account reconciliation
- Trial balance generation
- Precision/rounding (ARB FR8)
- Temporal query performance (ARB FR9)
- Idempotency collision (ARB FR10)

#### K-17 Distributed Transaction Coordinator (4 → 20 test cases, 14 stories)

**New Coverage:**

- Saga orchestration patterns
- Compensation scenarios
- Timeout handling
- Transaction state recovery

#### Control Plane Modules (20 → 60 test cases combined)

- K-08 Data Governance: RLS, policies, classification
- K-09 AI Governance: Model governance, prompt injection
- K-10 Deployment: Environment promotion, rollback
- K-11 API Gateway: Rate limiting, validation
- K-13 Admin Portal: UI flows, RBAC
- PU-004 Platform Manifest: State management
- K-12 Platform SDK: Contract stability

### Phase 3: Trading MVP (40 → 150 test cases)

#### D-11 Reference Data (5 → 25 test cases, 13 stories)

**New Coverage:**

- Instrument master CRUD
- Temporal validity queries
- Entity master relationships
- Benchmark calculations
- Feed adapter integration
- Snapshot service
- Change audit trail

#### D-04 Market Data (5 → 35 test cases, 15 stories)

**New Coverage:**

- L1/L2/L3 order book distribution
- Feed normalization scenarios
- Multi-source arbitration
- Tick validation and anomaly detection
- Stale data handling
- Feed failover and recovery
- Historical data replay

#### D-01 OMS (5 → 40 test cases, 21 stories)

**New Coverage:**

- Complete order state machine (12 states)
- Pre-trade evaluation via K-03
- Maker-checker workflows
- Position tracking
- AI intent classification
- Fat-finger detection
- Order modification scenarios
- Order cancellation edge cases
- Partial fill handling
- State transition validation

#### D-06 Risk Engine (5 → 30 test cases, 21 stories)

**New Coverage:**

- Pre-trade risk evaluation
- Margin calculations
- Position limit checks
- Risk metric calculations
- Pre-trade via K-03 (ARB D.2)
- No hardcoded SEBON (ARB D.5)

#### D-07 Compliance Engine (5 → 25 test cases, 17 stories)

**New Coverage:**

- Regulatory screening
- Compliance rule evaluation
- Rule registration with K-03 (ARB D.2)
- Blocking and escalation
- Compliance reporting

#### D-02 EMS (5 → 25 test cases, 22 stories)

**New Coverage:**

- Order routing algorithms
- Exchange adapter integration
- Execution recording
- Smart order routing
- Latency budgets (ARB D.1)

#### D-09 Post-Trade (5 → 25 test cases, 18 stories)

**New Coverage:**

- Settlement obligation creation
- Trade confirmation flows
- Clearing processing
- Settlement tracking
- K-16 ledger integration

#### D-03 PMS (5 → 20 test cases, 13 stories)

**New Coverage:**

- Holdings calculation
- P&L computation
- Performance tracking
- Projection rebuild from K-05

#### D-05 Pricing Engine (5 → 20 test cases, 12 stories)

**New Coverage:**

- Instrument valuation
- Mark-to-market calculations
- Pricing model application
- Price update propagation

### Phase 4/5: Operational Hardening (40 → 100 test cases)

#### Regulatory Modules (20 → 50 test cases)

- D-10 Regulatory Reporting: Report generation, submission, deadlines
- D-12 Corporate Actions: Entitlements, exceptions, notifications
- D-13 Client Money Reconciliation: Segregation, breaks, compliance
- D-14 Sanctions Screening: Screening, escalation, overrides

#### Operational Modules (20 → 50 test cases)

- W-01 Workflow Orchestration: Workflow orchestration, approvals
- W-02 Client Onboarding: KYC, risk assessment, account creation
- O-01 Operator Console: Runbooks, escalations, incident response
- P-01 Pack Certification: Validation, rejection, certificates
- R-01 Regulator Portal: Access control, evidence views
- R-02 Incident Response: Detection, clustering, escalation

#### Testing Infrastructure (10 → 30 test cases)

- T-01 Integration Testing: Regression, orchestration, coverage
- T-02 Chaos Engineering: Fault injection, resilience validation

---

## Test Case Categories for Expansion

### 1. State Transition Tests

Every module with a state machine needs comprehensive state transition coverage:

- Valid state transitions (happy path)
- Invalid state transitions (should fail)
- Self-transitions (no-op scenarios)
- Concurrent state change attempts
- State transition event emission

### 2. Edge Case Tests

- Boundary value analysis (min/max values)
- Null/empty input handling
- Invalid format handling
- Race conditions
- Resource exhaustion scenarios

### 3. Error Handling Tests

- Network failures
- Service unavailability
- Timeout scenarios
- Data corruption
- Recovery procedures

### 4. Performance Tests

- Throughput benchmarks
- Latency percentiles (P50, P95, P99)
- Load testing scenarios
- Stress testing limits
- Scalability verification

### 5. Security Tests

- Authentication bypass attempts
- Authorization edge cases
- Data exposure prevention
- Injection attack prevention
- Audit trail completeness

### 6. Integration Tests

- Cross-module data flow
- Event propagation verification
- API contract compliance
- Database transaction boundaries
- External system interactions

### 7. Resilience Tests

- Circuit breaker activation
- Retry and backoff behavior
- Fallback mechanism verification
- Partial failure handling
- Recovery time objectives

### 8. Compliance Tests

- Regulatory requirement verification
- Data retention enforcement
- Audit requirement compliance
- Reporting accuracy
- Evidence preservation

---

## Implementation Priority

### Wave 1: Critical Path (Week 1)

1. Expand K-05 Event Bus (60 TCs)
2. Expand D-01 OMS (40 TCs)
3. Expand D-04 Market Data (35 TCs)

### Wave 2: Foundation (Week 2)

4. Expand K-02 Configuration Engine (55 TCs)
5. Expand K-07 Audit Framework (50 TCs)
6. Expand K-16 Ledger Framework (25 TCs)

### Wave 3: Trading Core (Week 3)

7. Expand D-06 Risk Engine (30 TCs)
8. Expand D-07 Compliance (25 TCs)
9. Expand D-02 EMS (25 TCs)
10. Expand D-11 Reference Data (25 TCs)

### Wave 4: Remaining Modules (Week 4)

11. Expand Phase 2 remaining modules
12. Expand Phase 4/5 operational modules
13. Add comprehensive state matrices
14. Update all YAML appendices

---

## Success Criteria

- [ ] 600+ total test cases documented
- [ ] Every story has corresponding test coverage
- [ ] All state transitions documented
- [ ] All edge cases identified
- [ ] All error scenarios covered
- [ ] Performance targets validated
- [ ] Security scenarios comprehensive
- [ ] YAML appendices complete and consistent
- [ ] Coverage matrices show 100% story coverage
- [ ] Review report updated with new coverage stats

---

**Expansion Plan Approved:** March 10, 2026  
**Target Completion:** 4 weeks (by April 7, 2026)  
**Estimated Effort:** 160 hours
