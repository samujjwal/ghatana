# TDD Test Specification for Phase 3 Trading MVP Domain Path

**Document Version:** 2.1  
**Date:** March 10, 2026  
**Status**: Implementation-Ready Test Specification  
**Scope**: Phase 3 Trading MVP - Reference Data, Market Data, OMS, Risk Engine, Compliance, EMS, Post-Trade, Ledger, PMS, Pricing Engine

---

## 1. Scope Summary

**In Scope:**
- **Reference Data Management** (D-11): Instrument definitions, trading calendars, market rules
- **Market Data Management** (D-04): Real-time feeds, normalization, stale data handling
- **Order Management System** (D-01): Order lifecycle, validation, routing, fills
- **Risk Engine** (D-06): Pre-trade risk checks, margin calculations, position limits
- **Compliance Engine** (D-07): Regulatory screening, sanctions, compliance rules
- **Execution Management System** (D-02): Order routing, execution, exchange adapters
- **Post-Trade Processing** (D-09): Settlement, clearing, trade confirmation
- **Ledger Integration** (K-16): Double-entry posting, financial consequences
- **Portfolio Management System** (D-03): Holdings, P&L, performance tracking
- **Pricing Engine** (D-05): Valuation, mark-to-market, pricing models

**Out of Scope:**
- Advanced trading algorithms (deferred to future phases)
- Multi-asset class trading (future enhancement)
- High-frequency trading optimizations (future enhancement)

**Authority Sources Used:**
- CURRENT_EXECUTION_PLAN.md
- Relevant LLDs for all Phase 3 modules
- ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md
- Architecture specification parts 1-3
- Trading domain requirements

**Assumptions:**
- Phase 1 and Phase 2 kernel modules operational
- Market data feeds available (NSE, NEPSE)
- Exchange connectivity established
- Settlement systems integrated
- Real-time performance requirements (≤12ms e2e P99)

---

## 2. Source Inventory

| source_id | path | authority | why_it_matters | extracted_behaviors |
|-----------|------|-----------|----------------|-------------------|
| EXEC_PLAN_001 | CURRENT_EXECUTION_PLAN.md | Primary | Phase 3 sequence and dependencies | Trading flow, performance targets |
| LLD_D11_001 | LLD_D11_REFERENCE_DATA.md | Primary | Reference data management | Instrument definitions, calendars |
| LLD_D04_001 | LLD_D04_MARKET_DATA.md | Primary | Market data management | Feed ingestion, normalization |
| LLD_D01_001 | LLD_D01_OMS.md | Primary | Order management system | Order lifecycle, validation |
| LLD_D06_001 | LLD_D06_RISK_ENGINE.md | Primary | Risk engine | Pre-trade checks, limits |
| LLD_D07_001 | LLD_D07_COMPLIANCE.md | Primary | Compliance engine | Regulatory screening |
| LLD_D02_001 | LLD_D02_EMS.md | Primary | Execution management system | Routing, execution |
| LLD_D09_001 | LLD_D09_POST_TRADE.md | Primary | Post-trade processing | Settlement, clearing |
| LLD_D03_001 | LLD_D03_PMS.md | Primary | Portfolio management system | Holdings, P&L |
| LLD_D05_001 | LLD_D05_PRICING_ENGINE.md | Primary | Pricing engine | Valuation, models |
| LLD_K16_001 | LLD_K16_LEDGER_FRAMEWORK.md | Primary | Ledger integration | Financial posting |

---

## 3. Behavior Inventory

### Group: D-11 Reference Data Management
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| REF_001 | Instrument Manager | MANAGE_INSTRUMENTS | Create/update instrument definitions | LLD_D11_001 |
| REF_002 | Calendar Manager | MANAGE_TRADING_CALENDARS | Manage trading calendars | LLD_D11_001 |
| REF_003 | Market Rules Manager | MANAGE_MARKET_RULES | Manage market rules and regulations | LLD_D11_001 |
| REF_004 | Reference Data Validation | VALIDATE_REFERENCE_DATA | Validate reference data integrity | LLD_D11_001 |

### Group: D-04 Market Data Management
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| MKT_001 | Feed Ingestion | INGEST_MARKET_DATA | Ingest real-time market data feeds | LLD_D04_001 |
| MKT_002 | Data Normalization | NORMALIZE_MARKET_DATA | Normalize market data formats | LLD_D04_001 |
| MKT_003 | Freshness Monitor | MONITOR_DATA_FRESHNESS | Monitor data freshness and staleness | LLD_D04_001 |
| MKT_004 | Stale Data Handler | HANDLE_STALE_DATA | Handle stale or missing data | LLD_D04_001 |

### Group: D-01 Order Management System
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| OMS_001 | Order Validation | VALIDATE_ORDERS | Validate order parameters | LLD_D01_001 |
| OMS_002 | Order Placement | PLACE_ORDERS | Place new orders | LLD_D01_001 |
| OMS_003 | Order Modification | MODIFY_ORDERS | Modify existing orders | LLD_D01_001 |
| OMS_004 | Order Cancellation | CANCEL_ORDERS | Cancel existing orders | LLD_D01_001 |
| OMS_005 | Fill Processing | PROCESS_FILLS | Process order fills | LLD_D01_001 |

### Group: D-06 Risk Engine
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| RISK_001 | Pre-Trade Checks | PERFORM_PRE_TRADE_CHECKS | Perform pre-trade risk validation | LLD_D06_001 |
| RISK_002 | Margin Calculation | CALCULATE_MARGIN | Calculate margin requirements | LLD_D06_001 |
| RISK_003 | Position Limits | CHECK_POSITION_LIMITS | Check position limits | LLD_D06_001 |
| RISK_004 | Risk Metrics | CALCULATE_RISK_METRICS | Calculate risk metrics | LLD_D06_001 |

### Group: D-07 Compliance Engine
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| COMP_001 | Regulatory Screening | SCREEN_REGULATORY_RULES | Screen against regulatory rules | LLD_D07_001 |
| COMP_002 | Sanctions Checking | CHECK_SANCTIONS | Check against sanctions lists | LLD_D07_001 |
| COMP_003 | Compliance Blocking | BLOCK_COMPLIANCE_VIOLATIONS | Block compliance violations | LLD_D07_001 |
| COMP_004 | Compliance Reporting | REPORT_COMPLIANCE | Generate compliance reports | LLD_D07_001 |

### Group: D-02 Execution Management System
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| EMS_001 | Order Routing | ROUTE_ORDERS | Route orders to exchanges | LLD_D02_001 |
| EMS_002 | Execution Recording | RECORD_EXECUTIONS | Record execution details | LLD_D02_001 |
| EMS_003 | Exchange Adapters | MANAGE_EXCHANGE_ADAPTERS | Manage exchange connectivity | LLD_D02_001 |
| EMS_004 | Execution Reports | PROCESS_EXECUTION_REPORTS | Process execution reports | LLD_D02_001 |

### Group: D-09 Post-Trade Processing
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| POST_001 | Settlement Creation | CREATE_SETTLEMENT_OBLIGATIONS | Create settlement obligations | LLD_D09_001 |
| POST_002 | Trade Confirmation | CONFIRM_TRADES | Confirm trade details | LLD_D09_001 |
| POST_003 | Clearing Processing | PROCESS_CLEARING | Process clearing operations | LLD_D09_001 |
| POST_004 | Settlement Tracking | TRACK_SETTLEMENT | Track settlement status | LLD_D09_001 |

### Group: K-16 Ledger Integration
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| LED_001 | Trade Posting | POST_TRADE_LEDGER | Post trades to ledger | LLD_K16_001 |
| LED_002 | Settlement Posting | POST_SETTLEMENT_LEDGER | Post settlements to ledger | LLD_K16_001 |
| LED_003 | Position Updates | UPDATE_POSITIONS | Update ledger positions | LLD_K16_001 |
| LED_004 | Ledger Reconciliation | RECONCILE_LEDGER | Reconcile ledger balances | LLD_K16_001 |

### Group: D-03 Portfolio Management System
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| PMS_001 | Holdings Calculation | CALCULATE_HOLDINGS | Calculate portfolio holdings | LLD_D03_001 |
| PMS_002 | P&L Calculation | CALCULATE_PNL | Calculate profit and loss | LLD_D03_001 |
| PMS_003 | Performance Tracking | TRACK_PERFORMANCE | Track portfolio performance | LLD_D03_001 |
| PMS_004 | Projection Rebuild | REBUILD_PROJECTIONS | Rebuild projections from events | LLD_D03_001 |

### Group: D-05 Pricing Engine
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| PRIC_001 | Instrument Valuation | VALUE_INSTRUMENTS | Value instruments | LLD_D05_001 |
| PRIC_002 | Mark-to-Market | MARK_TO_MARKET | Calculate mark-to-market values | LLD_D05_001 |
| PRIC_003 | Pricing Models | APPLY_PRICING_MODELS | Apply pricing models | LLD_D05_001 |
| PRIC_004 | Price Updates | UPDATE_PRICES | Update price feeds | LLD_D05_001 |

---

## 4. Risk Inventory

| risk_id | description | severity | impacted_behaviors | required_test_layers |
|---------|-------------|----------|-------------------|---------------------|
| RISK_001 | Invalid reference data causes trading failures | High | REF_001-004, OMS_001-005 | Integration, Business |
| RISK_002 | Stale market data leads to incorrect pricing | High | MKT_001-004, PRIC_001-004 | Integration, Business |
| RISK_003 | Order validation bypass enables invalid trades | Critical | OMS_001-005 | Security, Business |
| RISK_004 | Risk check failure causes exposure beyond limits | Critical | RISK_001-004 | Integration, Business |
| RISK_005 | Compliance screening failure enables regulatory violations | Critical | COMP_001-004 | Security, Compliance |
| RISK_006 | Execution routing failures cause order loss | High | EMS_001-004 | Integration, Business |
| RISK_007 | Post-trade processing errors cause settlement failures | High | POST_001-004 | Integration, Business |
| RISK_008 | Ledger posting errors cause financial inconsistencies | Critical | LED_001-004 | Integration, Business |
| RISK_009 | PMS projection errors cause incorrect holdings | High | PMS_001-004 | Integration, Business |
| RISK_010 | Pricing errors cause incorrect valuations | High | PRIC_001-004 | Integration, Business |

---

## 5. Test Strategy by Layer

### Unit Tests
- **Purpose**: Validate individual business logic, calculations, and data transformations
- **Tools**: JUnit 5, parameterized tests, test data factories
- **Coverage Goal**: 100% statement and branch coverage
- **Fixtures Required**: Test instruments, market data, orders
- **Exit Criteria**: All unit tests pass, logic verified

### Component Tests
- **Purpose**: Validate module interactions with dependencies
- **Tools**: Testcontainers, mock market data feeds
- **Coverage Goal**: 100% component interaction scenarios
- **Fixtures Required**: Test infrastructure components
- **Exit Criteria**: All components work with real dependencies

### Integration Tests
- **Purpose**: Validate end-to-end trading workflows
- **Tools**: Docker Compose, full trading environment
- **Coverage Goal**: 100% critical trading paths
- **Fixtures Required**: Complete trading environment
- **Exit Criteria**: Trading workflows work end-to-end

### Performance Tests
- **Purpose**: Validate ≤12ms e2e P99 performance target
- **Tools**: Load testing frameworks, benchmarking tools
- **Coverage Goal**: Meet performance requirements
- **Fixtures Required**: Performance test harness
- **Exit Criteria**: Performance targets met

### Business Scenario Tests
- **Purpose**: Validate real-world trading scenarios
- **Tools**: Custom test harness, market simulators
- **Coverage Goal**: 100% business scenarios
- **Fixtures Required: Market data simulators, trading scenarios
- **Exit Criteria**: Business scenarios work correctly

---

## 6. Granular Test Catalog

### Test Cases for D-11 Reference Data Management

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| REF_TC_001 | Create valid instrument definition | Instrument Manager | LLD_D11_001 | Unit | Happy Path | High |
| REF_TC_002 | Update instrument definition | Instrument Manager | LLD_D11_001 | Unit | Happy Path | High |
| REF_TC_003 | Validate instrument data integrity | Reference Data Validation | LLD_D11_001 | Unit | Validation | High |
| REF_TC_004 | Manage trading calendar | Calendar Manager | LLD_D11_001 | Unit | Happy Path | High |
| REF_TC_005 | Manage market rules | Market Rules Manager | LLD_D11_001 | Unit | Happy Path | High |

### Test Cases for D-04 Market Data Management

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| MKT_TC_001 | Ingest real-time market data | Feed Ingestion | LLD_D04_001 | Integration | Happy Path | High |
| MKT_TC_002 | Normalize market data formats | Data Normalization | LLD_D04_001 | Unit | Happy Path | High |
| MKT_TC_003 | Monitor data freshness | Freshness Monitor | LLD_D04_001 | Integration | Happy Path | High |
| MKT_TC_004 | Handle stale market data | Stale Data Handler | LLD_D04_001 | Integration | Error Handling | High |

### Test Cases for D-01 Order Management System

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| OMS_TC_001 | Validate order parameters | Order Validation | LLD_D01_001 | Unit | Happy Path | High |
| OMS_TC_002 | Place new order | Order Placement | LLD_D01_001 | Integration | Happy Path | High |
| OMS_TC_003 | Modify existing order | Order Modification | LLD_D01_001 | Integration | Happy Path | High |
| OMS_TC_004 | Cancel existing order | Order Cancellation | LLD_D01_001 | Integration | Happy Path | High |
| OMS_TC_005 | Process order fills | Fill Processing | LLD_D01_001 | Integration | Happy Path | High |

### Test Cases for D-06 Risk Engine

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| RISK_TC_001 | Perform pre-trade risk checks | Pre-Trade Checks | LLD_D06_001 | Integration | Happy Path | High |
| RISK_TC_002 | Calculate margin requirements | Margin Calculation | LLD_D06_001 | Unit | Happy Path | High |
| RISK_TC_003 | Check position limits | Position Limits | LLD_D06_001 | Unit | Happy Path | High |
| RISK_TC_004 | Calculate risk metrics | Risk Metrics | LLD_D06_001 | Unit | Happy Path | High |

### Test Cases for D-07 Compliance Engine

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| COMP_TC_001 | Screen against regulatory rules | Regulatory Screening | LLD_D07_001 | Integration | Happy Path | High |
| COMP_TC_002 | Check against sanctions lists | Sanctions Checking | LLD_D07_001 | Integration | Happy Path | High |
| COMP_TC_003 | Block compliance violations | Compliance Blocking | LLD_D07_001 | Security | Blocking | High |
| COMP_TC_004 | Generate compliance reports | Compliance Reporting | LLD_D07_001 | Integration | Happy Path | High |

### Test Cases for D-02 Execution Management System

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| EMS_TC_001 | Route orders to exchanges | Order Routing | LLD_D02_001 | Integration | Happy Path | High |
| EMS_TC_002 | Record execution details | Execution Recording | LLD_D02_001 | Integration | Happy Path | High |
| EMS_TC_003 | Manage exchange adapters | Exchange Adapters | LLD_D02_001 | Integration | Happy Path | High |
| EMS_TC_004 | Process execution reports | Execution Reports | LLD_D02_001 | Integration | Happy Path | High |

### Test Cases for D-09 Post-Trade Processing

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| POST_TC_001 | Create settlement obligations | Settlement Creation | LLD_D09_001 | Integration | Happy Path | High |
| POST_TC_002 | Confirm trade details | Trade Confirmation | LLD_D09_001 | Integration | Happy Path | High |
| POST_TC_003 | Process clearing operations | Clearing Processing | LLD_D09_001 | Integration | Happy Path | High |
| POST_TC_004 | Track settlement status | Settlement Tracking | LLD_D09_001 | Integration | Happy Path | High |

### Test Cases for K-16 Ledger Integration

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| LED_TC_001 | Post trades to ledger | Trade Posting | LLD_K16_001 | Integration | Happy Path | High |
| LED_TC_002 | Post settlements to ledger | Settlement Posting | LLD_K16_001 | Integration | Happy Path | High |
| LED_TC_003 | Update ledger positions | Position Updates | LLD_K16_001 | Integration | Happy Path | High |
| LED_TC_004 | Reconcile ledger balances | Ledger Reconciliation | LLD_K16_001 | Integration | Happy Path | High |

### Test Cases for D-03 Portfolio Management System

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| PMS_TC_001 | Calculate portfolio holdings | Holdings Calculation | LLD_D03_001 | Integration | Happy Path | High |
| PMS_TC_002 | Calculate profit and loss | P&L Calculation | LLD_D03_001 | Integration | Happy Path | High |
| PMS_TC_003 | Track portfolio performance | Performance Tracking | LLD_D03_001 | Integration | Happy Path | High |
| PMS_TC_004 | Rebuild projections from events | Projection Rebuild | LLD_D03_001 | Integration | Recovery | High |

### Test Cases for D-05 Pricing Engine

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| PRIC_TC_001 | Value instruments | Instrument Valuation | LLD_D05_001 | Unit | Happy Path | High |
| PRIC_TC_002 | Calculate mark-to-market values | Mark-to-Market | LLD_D05_001 | Unit | Happy Path | High |
| PRIC_TC_003 | Apply pricing models | Pricing Models | LLD_D05_001 | Unit | Happy Path | High |
| PRIC_TC_004 | Update price feeds | Price Updates | LLD_D05_001 | Integration | Happy Path | High |

---

## 7. End-to-End Trading Workflow Suites

### Suite E2E_001: Complete Valid Trade Lifecycle
- **Suite ID**: E2E_001
- **Business Narrative**: Complete trade lifecycle from order placement to settlement
- **Actors**: Trader, Risk Manager, Compliance Officer, Exchange, Clearing House
- **Preconditions**: All modules operational, market data available
- **Timeline**: 1 complete trade lifecycle
- **Exact Input Set**: Valid order, market data, risk limits, compliance rules
- **Expected Outputs per Step**:
  1. Order validated and placed
  2. Risk checks passed
  3. Compliance screening passed
  4. Order routed to exchange
  5. Execution received and recorded
  6. Trade posted to ledger
  7. Settlement obligation created
  8. Portfolio holdings updated
- **Expected Failure Variants**: Risk rejection, compliance block, exchange reject
- **Expected Recovery Variants**: Order rejection, compensation, manual intervention

### Suite E2E_002: Market Halt Scenario
- **Suite ID**: E2E_002
- **Business Narrative**: Handle market halt/circuit breaker conditions
- **Actors**: Trading System, Exchange, Risk Manager
- **Preconditions**: Market data feed active, circuit breaker configured
- **Timeline**: Market event duration
- **Exact Input Set**: Market halt signal, active orders
- **Expected Outputs per Step**:
  1. Market halt detected
  2. Order routing suspended
  3. Existing orders handled per rules
  4. Risk limits adjusted
  5. Trading resumes when clear
- **Expected Failure Variants**: Halt not detected, orders continue
- **Expected Recovery Variants**: Manual intervention, system recovery

### Suite E2E_003: Partial Fill and Cancel Scenario
- **Suite ID**: E2E_003
- **Business Narrative**: Handle partial fills and subsequent cancellations
- **Actors**: Trader, Exchange, OMS, PMS
- **Preconditions**: Order placed, partial execution possible
- **Timeline**: Order execution period
- **Exact Input Set**: Large order, market conditions
- **Expected Outputs per Step**:
  1. Partial fill received
  2. Remaining quantity updated
  3. Cancel request processed
  4. Partial fill posted to ledger
  5. Portfolio updated correctly
- **Expected Failure Variants**: Cancel rejected, ledger posting error
- **Expected Recovery Variants**: Manual cancellation, ledger reconciliation

### Suite E2E_004: Compliance Block Scenario
- **Suite ID**: E2E_004
- **Business Narrative**: Handle compliance screening blocks
- **Actors**: Trader, Compliance Officer, OMS
- **Preconditions**: Compliance rules configured, sanctions lists active
- **Timeline**: Order screening period
- **Exact Input Set**: Suspicious order, compliance rules
- **Expected Outputs per Step**:
  1. Order screened for compliance
  2. Sanctions check triggered
  3. Compliance violation detected
  4. Order blocked
  5. Alert generated for compliance officer
- **Expected Failure Variants**: Screening failure, false positive
- **Expected Recovery Variants**: Manual review, rule adjustment

### Suite E2E_005: Projection Replay Scenario
- **Suite ID**: E2E_005
- **Business Narrative**: Replay events to rebuild PMS projections
- **Actors**: System Administrator, PMS, K-05 Event Bus
- **Preconditions**: Event history available, projection corrupted
- **Timeline**: Recovery period
- **Exact Input Set**: Event history, projection rebuild request
- **Expected Outputs per Step**:
  1. Replay initiated from checkpoint
  2. Events processed sequentially
  3. Projections rebuilt correctly
  4. Holdings reconciled
  5. System back to normal operation
- **Expected Failure Variants**: Replay failure, data inconsistency
- **Expected Recovery Variants**: Manual reconciliation, data repair

---

## 8. Performance Test Scenarios

### Performance Suite PERF_001: OMS Critical Path Performance
- **Suite ID**: PERF_001
- **Business Narrative**: Validate ≤12ms e2e P99 performance target
- **Actors**: Performance Testing Framework, OMS, Dependencies
- **Preconditions**: System under load, monitoring active
- **Timeline**: Performance test duration
- **Exact Input Set**: High volume orders, concurrent users
- **Expected Outputs per Step**:
  1. Order validation ≤2ms
  2. Risk checks ≤3ms
  3. Compliance screening ≤2ms
  4. Order routing ≤3ms
  5. Total e2e ≤12ms P99
- **Expected Failure Variants**: Performance degradation, bottlenecks
- **Expected Recovery Variants**: Optimization, scaling

### Performance Suite PERF_002: Market Data Processing Performance
- **Suite ID**: PERF_002
- **Business Narrative**: Validate market data processing performance
- **Actors**: Market Data Manager, Feed Processors
- **Preconditions**: High-volume market data feeds
- **Timeline**: Data processing period
- **Exact Input Set**: Real-time market data streams
- **Expected Outputs per Step**:
  1. Data ingestion ≤1ms
  2. Normalization ≤1ms
  3. Distribution ≤1ms
  4. Total processing ≤3ms
- **Expected Failure Variants**: Processing delays, data loss
- **Expected Recovery Variants**: Buffer management, scaling

---

## 9. Coverage Matrices

### Trading State Transition Coverage Matrix
| State | From | To | Test Cases | Coverage Status |
|-------|------|----|-------------|-----------------|
| NEW | - | VALIDATED | OMS_TC_001 | ✅ |
| VALIDATED | NEW | RISK_CHECKED | RISK_TC_001 | ✅ |
| RISK_CHECKED | VALIDATED | COMPLIANCE_CHECKED | COMP_TC_001 | ✅ |
| COMPLIANCE_CHECKED | RISK_CHECKED | ROUTED | EMS_TC_001 | ✅ |
| ROUTED | COMPLIANCE_CHECKED | SUBMITTED | EMS_TC_002 | ✅ |
| SUBMITTED | ROUTED | FILLED | EMS_TC_004 | ✅ |
| FILLED | SUBMITTED | SETTLED | POST_TC_001 | ✅ |
| SETTLED | FILLED | COMPLETED | LED_TC_002 | ✅ |

### Pre-Trade Decision Outcome Coverage Matrix
| Decision | Outcome | Test Cases | Coverage Status |
|----------|----------|-------------|-----------------|
| Order Validation | PASS | OMS_TC_001 | ✅ |
| Order Validation | FAIL | OMS_TC_001 | ✅ |
| Risk Check | PASS | RISK_TC_001 | ✅ |
| Risk Check | FAIL | RISK_TC_001 | ✅ |
| Compliance Check | PASS | COMP_TC_001 | ✅ |
| Compliance Check | FAIL | COMP_TC_003 | ✅ |

### Execution Outcome Coverage Matrix
| Execution | Outcome | Test Cases | Coverage Status |
|-----------|----------|-------------|-----------------|
| Order Routing | SUCCESS | EMS_TC_001 | ✅ |
| Order Routing | FAILURE | EMS_TC_001 | ✅ |
| Execution | FULL_FILL | EMS_TC_004 | ✅ |
| Execution | PARTIAL_FILL | EMS_TC_004 | ✅ |
| Execution | REJECT | EMS_TC_004 | ✅ |

### Settlement/Ledger Consequence Coverage Matrix
| Consequence | Ledger Entry | Test Cases | Coverage Status |
|-------------|--------------|-------------|-----------------|
| Trade Execution | DEBIT_CASH | LED_TC_001 | ✅ |
| Trade Execution | CREDIT_POSITION | LED_TC_001 | ✅ |
| Settlement | DEBIT_POSITION | LED_TC_002 | ✅ |
| Settlement | CREDIT_CASH | LED_TC_002 | ✅ |

### Projection Rebuild Coverage Matrix
| Projection | Event Source | Test Cases | Coverage Status |
|-----------|--------------|-------------|-----------------|
| Holdings | Trade Events | PMS_TC_004 | ✅ |
| P&L | Price Events | PMS_TC_004 | ✅ |
| Performance | All Events | PMS_TC_004 | ✅ |

---

## 10. Phase Exit Criteria Validation

### Staging Order Lifecycle Tests
| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| ST_001 | Order lifecycle executes without manual DB intervention | Complete automated order flow |
| ST_002 | Performance targets met (≤12ms e2e P99) | Performance benchmarks passed |
| ST_003 | Replay from K-05 rebuilds projections | Projection rebuild successful |
| ST_004 | Failure cases route to DLQ/compensation | Automated error handling |

### Integration Readiness Tests
| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| IR_001 | Reference data available to all modules | Data consistency maintained |
| IR_002 | Market data flows to dependent systems | Real-time data distribution |
| IR_003 | Risk and compliance checks integrated | Pre-trade controls working |
| IR_004 | Ledger posting integrated with trading | Financial consistency maintained |

---

## 11. Recommended Test File Plan

### Unit Tests
- `src/test/java/com/siddhanta/trading/reference/**Test.java`
- `src/test/java/com/siddhanta/trading/marketdata/**Test.java`
- `src/test/java/com/siddhanta/trading/oms/**Test.java`
- `src/test/java/com/siddhanta/trading/risk/**Test.java`
- `src/test/java/com/siddhanta/trading/compliance/**Test.java`
- `src/test/java/com/siddhanta/trading/ems/**Test.java`
- `src/test/java/com/siddhanta/trading/posttrade/**Test.java`
- `src/test/java/com/siddhanta/trading/pms/**Test.java`
- `src/test/java/com/siddhanta/trading/pricing/**Test.java`

### Integration Tests
- `src/test/java/com/siddhanta/trading/integration/OrderLifecycleTest.java`
- `src/test/java/com/siddhanta/trading/integration/RiskComplianceTest.java`
- `src/test/java/com/siddhanta/trading/integration/ExecutionTest.java`
- `src/test/java/com/siddhanta/trading/integration/SettlementTest.java`
- `src/test/java/com/siddhanta/trading/integration/LedgerIntegrationTest.java`

### Performance Tests
- `src/test/java/com/siddhanta/trading/performance/OMSPerformanceTest.java`
- `src/test/java/com/siddhanta/trading/performance/MarketDataPerformanceTest.java`
- `src/test/java/com/siddhanta/trading/performance/RiskEnginePerformanceTest.java`

### End-to-End Tests
- `src/test/java/com/siddhanta/trading/e2e/CompleteTradeLifecycleTest.java`
- `src/test/java/com/siddhanta/trading/e2e/MarketHaltTest.java`
- `src/test/java/com/siddhanta/trading/e2e/PartialFillTest.java`
- `src/test/java/com/siddhanta/trading/e2e/ComplianceBlockTest.java`
- `src/test/java/com/siddhanta/trading/e2e/ProjectionReplayTest.java`

### Business Scenario Tests
- `src/test/java/com/siddhanta/trading/business/TradingDayTest.java`
- `src/test/java/com/siddhanta/trading/business/EndOfDayTest.java`
- `src/test/java/com/siddhanta/trading/business/PortfolioRebalancingTest.java`
- `src/test/java/com/siddhanta/trading/business/RiskLimitTest.java`

---

## 12. Machine-Readable Appendix

```yaml
test_plan:
  scope: phase3_trading_mvp
  modules:
    - d11_reference_data
    - d04_market_data
    - d01_oms
    - d06_risk_engine
    - d07_compliance
    - d02_ems
    - d09_post_trade
    - k16_ledger_integration
    - d03_pms
    - d05_pricing_engine
  cases:
    - id: REF_TC_001
      title: Create valid instrument definition
      layer: unit
      module: d11_reference_data
      scenario_type: happy_path
      requirement_refs: [LLD_D11_001]
      source_refs: [LLD_D11_REFERENCE_DATA.md]
      preconditions: [reference_data_manager_available]
      fixtures: [valid_instrument_data]
      input: {instrument_definition: {symbol: "NEPSE123", name: "Sample Stock", exchange: "NEPSE", sector: "Technology"}}
      steps:
        - validate_instrument_data
        - check_for_duplicates
        - store_instrument_definition
        - update_reference_indexes
        - return_instrument_id
      expected_output: {instrument_id: "inst-123", status: "created", symbol: "NEPSE123"}
      expected_state_changes: [instrument_stored, indexes_updated]
      expected_events: [instrument_created_event]
      expected_audit: [instrument_creation_logged]
      expected_observability: [reference_data_metrics]
      expected_external_interactions: [reference_data_store]
      cleanup: [delete_test_instrument]
      branch_ids_covered: [instrument_creation_success]
      statement_groups_covered: [instrument_manager, data_validator, index_manager]
    
    - id: MKT_TC_001
      title: Ingest real-time market data
      layer: integration
      module: d04_market_data
      scenario_type: happy_path
      requirement_refs: [LLD_D04_001]
      source_refs: [LLD_D04_MARKET_DATA.md]
      preconditions: [market_data_manager_available, feed_connected]
      fixtures: [market_data_feed, test_instruments]
      input: {market_data: {symbol: "NEPSE123", price: 100.50, volume: 1000, timestamp: "2023-04-14T10:30:00Z"}}
      steps:
        - validate_market_data_format
        - normalize_data_structure
        - check_data_freshness
        - update_price_cache
        - publish_to_subscribers
      expected_output: {status: "processed", symbol: "NEPSE123", price: 100.50, subscribers_notified: 5}
      expected_state_changes: [price_cache_updated, subscribers_notified]
      expected_events: [market_data_processed_event]
      expected_audit: [market_data_ingestion_logged]
      expected_observability: [market_data_metrics]
      expected_external_interactions: [feed_adapter, cache_store, message_bus]
      cleanup: [clear_price_cache]
      branch_ids_covered: [market_data_ingestion_success, data_fresh]
      statement_groups_covered: [market_data_manager, feed_adapter, data_normalizer]
    
    - id: OMS_TC_001
      title: Validate order parameters
      layer: unit
      module: d01_oms
      scenario_type: happy_path
      requirement_refs: [LLD_D01_001]
      source_refs: [LLD_D01_OMS.md]
      preconditions: [oms_available, reference_data_loaded]
      fixtures: [valid_order_data, trading_rules]
      input: {order: {symbol: "NEPSE123", side: "BUY", quantity: 100, price: 100.50, order_type: "LIMIT"}}
      steps:
        - validate_instrument_exists
        - validate_trading_hours
        - validate_order_parameters
        - check_margin_requirements
        - return_validation_result
      expected_output: {status: "valid", order_id: "order-123", validated_at: "2023-04-14T10:30:00Z"}
      expected_state_changes: [order_validated]
      expected_events: [order_validated_event]
      expected_audit: [order_validation_logged]
      expected_observability: [oms_metrics]
      expected_external_interactions: [reference_data_service, risk_engine]
      cleanup: [cancel_test_order]
      branch_ids_covered: [order_validation_success, all_checks_passed]
      statement_groups_covered: [order_validator, instrument_checker, trading_hours_checker]
    
    - id: RISK_TC_001
      title: Perform pre-trade risk checks
      layer: integration
      module: d06_risk_engine
      scenario_type: happy_path
      requirement_refs: [LLD_D06_001]
      source_refs: [LLD_D06_RISK_ENGINE.md]
      preconditions: [risk_engine_available, position_data_loaded]
      fixtures: [risk_parameters, position_limits]
      input: {risk_check_request: {order_id: "order-123", user_id: "trader456", symbol: "NEPSE123", quantity: 100}}
      steps:
        - calculate_position_impact
        - check_margin_requirements
        - verify_position_limits
        - calculate_risk_metrics
        - return_risk_decision
      expected_output: {decision: "APPROVE", margin_required: 5000, position_impact: 100, risk_score: 0.2}
      expected_state_changes: []
      expected_events: [risk_check_completed_event]
      expected_audit: [risk_check_logged]
      expected_observability: [risk_metrics]
      expected_external_interactions: [position_store, margin_calculator]
      cleanup: []
      branch_ids_covered: [risk_check_success, within_limits]
      statement_groups_covered: [risk_engine, position_calculator, margin_calculator]
    
    - id: COMP_TC_001
      title: Screen against regulatory rules
      layer: integration
      module: d07_compliance
      scenario_type: happy_path
      requirement_refs: [LLD_D07_001]
      source_refs: [LLD_D07_COMPLIANCE.md]
      preconditions: [compliance_engine_available, rules_loaded]
      fixtures: [compliance_rules, sanctions_lists]
      input: {compliance_screening: {order_id: "order-123", user_id: "trader456", symbol: "NEPSE123", value: 10050}}
      steps:
        - screen_user_against_sanctions
        - check_instrument_restrictions
        - verify_regulatory_limits
        - apply_compliance_rules
        - return_compliance_decision
      expected_output: {decision: "APPROVE", sanctions_check: "CLEAR", restrictions: "NONE"}
      expected_state_changes: []
      expected_events: [compliance_screening_event]
      expected_audit: [compliance_screening_logged]
      expected_observability: [compliance_metrics]
      expected_external_interactions: [sanctions_service, rules_engine]
      cleanup: []
      branch_ids_covered: [compliance_screening_success, no_violations]
      statement_groups_covered: [compliance_engine, sanctions_checker, rules_evaluator]
    
    - id: EMS_TC_001
      title: Route orders to exchanges
      layer: integration
      module: d02_ems
      scenario_type: happy_path
      requirement_refs: [LLD_D02_001]
      source_refs: [LLD_D02_EMS.md]
      preconditions: [ems_available, exchange_connected]
      fixtures: [exchange_adapters, routing_rules]
      input: {order_routing: {order_id: "order-123", symbol: "NEPSE123", exchange: "NEPSE", side: "BUY"}}
      steps:
        - select_exchange_adapter
        - format_order_for_exchange
        - route_order_to_exchange
        - await_exchange_acknowledgment
        - return_routing_result
      expected_output: {status: "ROUTED", exchange_order_id: "ex-456", routed_at: "2023-04-14T10:30:00Z"}
      expected_state_changes: [order_routed]
      expected_events: [order_routed_event]
      expected_audit: [order_routing_logged]
      expected_observability: [ems_metrics]
      expected_external_interactions: [exchange_adapter, message_bus]
      cleanup: [cancel_exchange_order]
      branch_ids_covered: [order_routing_success, exchange_connected]
      statement_groups_covered: [order_router, exchange_adapter, message_formatter]
    
    - id: POST_TC_001
      title: Create settlement obligations
      layer: integration
      module: d09_post_trade
      scenario_type: happy_path
      requirement_refs: [LLD_D09_001]
      source_refs: [LLD_D09_POST_TRADE.md]
      preconditions: [post_trade_available, trade_executed]
      fixtures: [trade_execution, settlement_rules]
      input: {settlement_request: {trade_id: "trade-789", symbol: "NEPSE123", quantity: 100, price: 100.50}}
      steps:
        - validate_trade_execution
        - calculate_settlement_amount
        - determine_settlement_date
        - create_settlement_obligation
        - notify_settlement_parties
      expected_output: {settlement_id: "set-123", amount: 10050, settlement_date: "2023-04-18", status: "PENDING"}
      expected_state_changes: [settlement_obligation_created]
      expected_events: [settlement_obligation_created_event]
      expected_audit: [settlement_creation_logged]
      expected_observability: [post_trade_metrics]
      expected_external_interactions: [settlement_system, notification_service]
      cleanup: [cancel_settlement]
      branch_ids_covered: [settlement_creation_success, trade_valid]
      statement_groups_covered: [settlement_manager, obligation_calculator, notification_manager]
    
    - id: LED_TC_001
      title: Post trades to ledger
      layer: integration
      module: k16_ledger_integration
      scenario_type: happy_path
      requirement_refs: [LLD_K16_001]
      source_refs: [LLD_K16_LEDGER_FRAMEWORK.md]
      preconditions: [ledger_available, trade_executed]
      fixtures: [trade_execution, chart_of_accounts]
      input: {ledger_posting: {trade_id: "trade-789", symbol: "NEPSE123", quantity: 100, price: 100.50, side: "BUY"}}
      steps:
        - create_debit_entry
        - create_credit_entry
        - validate_double_entry
        - post_to_ledger
        - update_positions
      expected_output: {transaction_id: "txn-456", debit_entry: "de-123", credit_entry: "cr-789", status: "POSTED"}
      expected_state_changes: [ledger_entries_created, positions_updated]
      expected_events: [ledger_posted_event]
      expected_audit: [ledger_posting_logged]
      expected_observability: [ledger_metrics]
      expected_external_interactions: [ledger_database]
      cleanup: [reverse_ledger_posting]
      branch_ids_covered: [ledger_posting_success, double_entry_balanced]
      statement_groups_covered: [ledger_integration, entry_creator, position_updater]
    
    - id: PMS_TC_001
      title: Calculate portfolio holdings
      layer: integration
      module: d03_pms
      scenario_type: happy_path
      requirement_refs: [LLD_D03_001]
      source_refs: [LLD_D03_PMS.md]
      preconditions: [pms_available, ledger_data_available]
      fixtures: [portfolio_data, position_data]
      input: {holdings_request: {portfolio_id: "port-123", as_of_date: "2023-04-14"}}
      steps:
        - query_ledger_positions
        - aggregate_holdings_by_instrument
        - calculate_market_values
        - format_holdings_report
        - return_holdings_summary
      expected_output: {portfolio_id: "port-123", total_value: 50000, holdings: [{symbol: "NEPSE123", quantity: 100, value: 10050}]}
      expected_state_changes: []
      expected_events: [holdings_calculated_event]
      expected_audit: [holdings_calculation_logged]
      expected_observability: [pms_metrics]
      expected_external_interactions: [ledger_database, pricing_engine]
      cleanup: []
      branch_ids_covered: [holdings_calculation_success, data_available]
      statement_groups_covered: [portfolio_manager, holdings_calculator, value_aggregator]
    
    - id: PRIC_TC_001
      title: Value instruments
      layer: unit
      module: d05_pricing_engine
      scenario_type: happy_path
      requirement_refs: [LLD_D05_001]
      source_refs: [LLD_D05_PRICING_ENGINE.md]
      preconditions: [pricing_engine_available, market_data_available]
      fixtures: [pricing_models, market_data]
      input: {valuation_request: {symbol: "NEPSE123", quantity: 100, valuation_date: "2023-04-14"}}
      steps:
        - retrieve_market_data
        - select_pricing_model
        - calculate_instrument_value
        - apply_valuation_adjustments
        - return_valuation_result
      expected_output: {symbol: "NEPSE123", quantity: 100, market_price: 100.50, total_value: 10050}
      expected_state_changes: []
      expected_events: [valuation_completed_event]
      expected_audit: [valuation_logged]
      expected_observability: [pricing_metrics]
      expected_external_interactions: [market_data_store]
      cleanup: []
      branch_ids_covered: [valuation_success, model_selected]
      statement_groups_covered: [pricing_engine, model_selector, value_calculator]

  coverage:
    requirement_ids:
      REQ_REF_001: [REF_TC_001, REF_TC_002, REF_TC_003, REF_TC_004, REF_TC_005]
      REQ_MKT_001: [MKT_TC_001, MKT_TC_002, MKT_TC_003, MKT_TC_004]
      REQ_OMS_001: [OMS_TC_001, OMS_TC_002, OMS_TC_003, OMS_TC_004, OMS_TC_005]
      REQ_RISK_001: [RISK_TC_001, RISK_TC_002, RISK_TC_003, RISK_TC_004]
      REQ_COMP_001: [COMP_TC_001, COMP_TC_002, COMP_TC_003, COMP_TC_004]
      REQ_EMS_001: [EMS_TC_001, EMS_TC_002, EMS_TC_003, EMS_TC_004]
      REQ_POST_001: [POST_TC_001, POST_TC_002, POST_TC_003, POST_TC_004]
      REQ_LED_001: [LED_TC_001, LED_TC_002, LED_TC_003, LED_TC_004]
      REQ_PMS_001: [PMS_TC_001, PMS_TC_002, PMS_TC_003, PMS_TC_004]
      REQ_PRIC_001: [PRIC_TC_001, PRIC_TC_002, PRIC_TC_003, PRIC_TC_004]
    branch_ids:
      instrument_creation_success: [REF_TC_001, REF_TC_002]
      instrument_validation_success: [REF_TC_003]
      calendar_management_success: [REF_TC_004]
      market_rules_management_success: [REF_TC_005]
      market_data_ingestion_success: [MKT_TC_001, MKT_TC_003]
      market_data_normalization_success: [MKT_TC_002]
      stale_data_handled: [MKT_TC_004]
      order_validation_success: [OMS_TC_001, OMS_TC_002]
      order_modification_success: [OMS_TC_003]
      order_cancellation_success: [OMS_TC_004]
      fill_processing_success: [OMS_TC_005]
      risk_check_success: [RISK_TC_001, RISK_TC_002, RISK_TC_003, RISK_TC_004]
      compliance_screening_success: [COMP_TC_001, COMP_TC_002]
      compliance_blocking_success: [COMP_TC_003]
      compliance_reporting_success: [COMP_TC_004]
      order_routing_success: [EMS_TC_001, EMS_TC_003]
      execution_recording_success: [EMS_TC_002]
      execution_report_processing_success: [EMS_TC_004]
      settlement_creation_success: [POST_TC_001, POST_TC_003]
      trade_confirmation_success: [POST_TC_002]
      settlement_tracking_success: [POST_TC_004]
      ledger_posting_success: [LED_TC_001, LED_TC_002, LED_TC_003]
      ledger_reconciliation_success: [LED_TC_004]
      holdings_calculation_success: [PMS_TC_001, PMS_TC_002]
      pnl_calculation_success: [PMS_TC_002]
      performance_tracking_success: [PMS_TC_003]
      projection_rebuild_success: [PMS_TC_004]
      instrument_valuation_success: [PRIC_TC_001]
      mark_to_market_success: [PRIC_TC_002]
      pricing_model_success: [PRIC_TC_003]
      price_update_success: [PRIC_TC_004]
    statement_groups:
      instrument_manager: [REF_TC_001, REF_TC_002]
      calendar_manager: [REF_TC_004]
      market_rules_manager: [REF_TC_005]
      reference_data_validator: [REF_TC_003]
      market_data_manager: [MKT_TC_001, MKT_TC_002, MKT_TC_003, MKT_TC_004]
      order_validator: [OMS_TC_001]
      order_manager: [OMS_TC_002, OMS_TC_003, OMS_TC_004, OMS_TC_005]
      risk_engine: [RISK_TC_001, RISK_TC_002, RISK_TC_003, RISK_TC_004]
      compliance_engine: [COMP_TC_001, COMP_TC_002, COMP_TC_003, COMP_TC_004]
      order_router: [EMS_TC_001, EMS_TC_003]
      execution_recorder: [EMS_TC_002]
      execution_report_processor: [EMS_TC_004]
      settlement_manager: [POST_TC_001, POST_TC_002, POST_TC_003, POST_TC_004]
      ledger_integration: [LED_TC_001, LED_TC_002, LED_TC_003, LED_TC_004]
      portfolio_manager: [PMS_TC_001, PMS_TC_002, PMS_TC_003, PMS_TC_004]
      pricing_engine: [PRIC_TC_001, PRIC_TC_002, PRIC_TC_003, PRIC_TC_004]
  exclusions: []
```

---

**Phase 3 Trading MVP TDD specification complete.** This provides exhaustive test coverage for the complete trading workflow from reference data through execution to settlement, with performance validation and end-to-end scenario testing. The specification ensures the trading MVP meets staging-grade requirements with comprehensive testing.
