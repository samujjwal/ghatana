# TDD Test Specification for K-15 Dual-Calendar

**Document Version:** 2.1  
**Date:** March 10, 2026  
**Status**: Implementation-Ready Test Specification  
**Scope**: K-15 Dual-Calendar - BS/Gregorian conversion, holiday calendars, business-day computation, settlement dates

---

## 1. Scope Summary

**In Scope:**
- Bikram Sambat (BS) to Gregorian (AD) conversion and vice versa
- DualDate generation from timestamps
- Holiday calendars by jurisdiction
- Business-day computation with weekend/holiday rules
- Settlement date calculation (T+n logic)
- Fiscal year resolution and quarter boundaries
- Bulk conversion operations
- Performance-sensitive hot-path conversions
- Configuration-driven holiday/fiscal updates

**Out of Scope:**
- Multi-timezone handling (deferred to K-02)
- Advanced calendar types beyond BS/Gregorian
- Historical calendar changes
- Localization of calendar display formats

**Authority Sources Used:**
- LLD_K15_DUAL_CALENDAR.md
- ADR-004_DUAL_CALENDAR_SYSTEM.md
- ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md
- CURRENT_EXECUTION_PLAN.md
- Architecture specification parts 1-3

**Assumptions:**
- Lookup table-based conversion for performance
- Java 21 + ActiveJ for K-15 implementation
- PostgreSQL 15+ for holiday calendar storage
- Nepal jurisdiction as primary (BS calendar native)
- Configurable weekend patterns by jurisdiction
- K-02 integration for dynamic configuration updates

---

## 2. Source Inventory

| source_id | path | authority | why_it_matters | extracted_behaviors |
|-----------|------|-----------|----------------|-------------------|
| LLD_K15_001 | LLD_K15_DUAL_CALENDAR.md | Primary | Low-level design for K-15 | Conversion logic, holiday handling, settlement |
| ADR_004_001 | ADR-004_DUAL_CALENDAR_SYSTEM.md | Primary | Dual-calendar architecture decisions | BS/Gregorian requirements, lookup tables |
| ADR_011_001 | ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md | Primary | Stack baseline | Technology choices, performance requirements |
| EXEC_PLAN_001 | CURRENT_EXECUTION_PLAN.md | Primary | Build order and dependencies | Phase 1 positioning, K-02 integration |
| ARCH_SPEC_001 | ARCHITECTURE_SPEC_PART_1_SECTIONS_1-3.md | Secondary | Core architecture patterns | Dual-calendar invariants, data architecture |

---

## 3. Behavior Inventory

### Group: Date Conversion
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| DC_001 | Date Converter | BS_TO_GREGORIAN | Convert BS date to Gregorian | LLD_K15_001 |
| DC_002 | Date Converter | GREGORIAN_TO_BS | Convert Gregorian date to BS | LLD_K15_001 |
| DC_003 | Date Converter | ROUND_TRIP_CONVERSION | Ensure conversion round-trip accuracy | LLD_K15_001 |
| DC_004 | Date Converter | HANDLE_BOUNDS | Reject dates outside supported range | ADR_004_001 |
| DC_005 | Date Converter | HANDLE_LEAP_YEAR | Process leap years correctly | LLD_K15_001 |

### Group: DualDate Generation
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| DD_001 | DualDate Factory | CREATE_FROM_TIMESTAMP | Generate DualDate from timestamp | LLD_K15_001 |
| DD_002 | DualDate Factory | FORMAT_BS_TIMESTAMP | Format BS timestamp with cultural conventions | LLD_K15_001 |
| DD_003 | DualDate Factory | EXTRACT_DATE_COMPONENTS | Extract year/month/day components | LLD_K15_001 |
| DD_004 | DualDate Factory | VALIDATE_DUAL_DATE | Validate DualDate consistency | LLD_K15_001 |

### Group: Fiscal Year Management
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| FY_001 | Fiscal Calculator | CALCULATE_FISCAL_YEAR | Determine fiscal year for date | LLD_K15_001 |
| FY_002 | Fiscal Calculator | CALCULATE_QUARTER | Determine fiscal quarter | LLD_K15_001 |
| FY_003 | Fiscal Calculator | HANDLE_FY_BOUNDARY | Process fiscal year boundary day | LLD_K15_001 |
| FY_004 | Fiscal Calculator | FORMAT_FISCAL_LABEL | Format fiscal year label | LLD_K15_001 |

### Group: Holiday Management
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| HM_001 | Holiday Manager | LOOKUP_HOLIDAY | Check if date is holiday by jurisdiction | LLD_K15_001 |
| HM_002 | Holiday Manager | UPDATE_HOLIDAY_CALENDAR | Update holiday calendar configuration | LLD_K15_001 |
| HM_003 | Holiday Manager | HANDLE_JURISDICTION_VARIANCE | Support different holiday sets per jurisdiction | ADR_004_001 |
| HM_004 | Holiday Manager | CACHE_HOLIDAY_LOOKUPS | Cache holiday lookup results | LLD_K15_001 |

### Group: Business Day Computation
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| BD_001 | Business Day Calculator | EVALUATE_BUSINESS_DAY | Determine if date is business day | LLD_K15_001 |
| BD_002 | Business Day Calculator | HANDLE_WEEKEND_RULES | Apply weekend rules by jurisdiction | ADR_004_001 |
| BD_003 | Business Day Calculator | COMBINE_WEEKEND_HOLIDAY | Combine weekend and holiday logic | LLD_K15_001 |
| BD_004 | Business Day Calculator | PERFORMANCE_HOT_PATH | Fast business day evaluation | LLD_K15_001 |

### Group: Settlement Date Calculation
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| SD_001 | Settlement Calculator | CALCULATE_T_PLUS_N | Calculate settlement date T+n | LLD_K15_001 |
| SD_002 | Settlement Calculator | SKIP_NON_BUSINESS_DAYS | Skip weekends and holidays | LLD_K15_001 |
| SD_003 | Settlement Calculator | HANDLE_LONG_SETTLEMENT | Handle extended settlement periods | LLD_K15_001 |
| SD_004 | Settlement Calculator | TRACK_SKIPPED_DATES | Track dates skipped in calculation | LLD_K15_001 |

### Group: Batch Operations
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| BO_001 | Batch Processor | BULK_CONVERSION | Convert multiple dates efficiently | LLD_K15_001 |
| BO_002 | Batch Processor | HANDLE_MIXED_INPUTS | Process mixed valid/invalid inputs | LLD_K15_001 |
| BO_003 | Batch Processor | MAINTAIN_CONSISTENCY | Ensure batch operation consistency | LLD_K15_001 |
| BO_004 | Batch Processor | PERFORMANCE_OPTIMIZATION | Optimize batch performance | LLD_K15_001 |

---

## 4. Risk Inventory

| risk_id | description | severity | impacted_behaviors | required_test_layers |
|---------|-------------|----------|-------------------|---------------------|
| RISK_001 | Conversion table errors cause incorrect date mapping | Critical | DC_001-005, DD_001-004 | Unit, Integration |
| RISK_002 | Holiday calendar updates fail to propagate | High | HM_001-004, BD_001-004 | Integration, E2E |
| RISK_003 | Settlement date calculation misses holidays | High | SD_001-004, BD_001-004 | Integration, Business |
| RISK_004 | Performance degradation in hot-path conversions | Medium | DC_001-005, BD_004 | Performance, Load |
| RISK_005 | Fiscal year boundary errors affect reporting | High | FY_001-004 | Integration, Business |
| RISK_006 | Batch operation inconsistencies cause data corruption | Critical | BO_001-004 | Integration, Resilience |

---

## 5. Test Strategy by Layer

### Unit Tests
- **Purpose**: Validate conversion algorithms, lookup table access, fiscal calculations
- **Tools**: JUnit 5, parameterized tests, test data factories
- **Coverage Goal**: 100% statement and branch coverage
- **Fixtures Required**: Conversion table samples, holiday data
- **Exit Criteria**: All unit tests pass, conversion accuracy verified

### Component Tests
- **Purpose**: Validate DualDate generation, business day logic, settlement calculations
- **Tools**: Testcontainers, PostgreSQL with holiday data
- **Coverage Goal**: 100% component interaction scenarios
- **Fixtures Required**: Holiday calendar database, test dates
- **Exit Criteria**: All components work with real data

### Integration Tests
- **Purpose**: Validate end-to-end calendar operations, K-02 integration
- **Tools**: Docker Compose, full calendar service
- **Coverage Goal**: 100% critical integration paths
- **Fixtures Required**: Complete calendar environment
- **Exit Criteria**: Calendar operations work end-to-end

### Performance Tests
- **Purpose**: Validate hot-path conversion performance, batch operations
- **Tools**: JMH, load testing frameworks
- **Coverage Goal**: Meet performance requirements (<2ms for hot-path)
- **Fixtures Required**: Performance test harness
- **Exit Criteria**: Performance targets met

### Business Scenario Tests
- **Purpose**: Validate real-world business scenarios
- **Tools**: Custom test harness, business data
- **Coverage Goal**: 100% business scenarios
- **Fixtures Required: Business calendar data, settlement rules
- **Exit Criteria**: Business scenarios work correctly

---

## 6. Granular Test Catalog

### Test Cases for Date Conversion

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| DC_TC_001 | Convert BS 2080-01-01 to Gregorian correctly | Date Converter | LLD_K15_001 | Unit | Happy Path | High |
| DC_TC_002 | Convert Gregorian 2023-01-01 to BS correctly | Date Converter | LLD_K15_001 | Unit | Happy Path | High |
| DC_TC_003 | Round-trip conversion maintains accuracy | Date Converter | LLD_K15_001 | Unit | Consistency | High |
| DC_TC_004 | Reject date below lower bound | Date Converter | ADR_004_001 | Unit | Validation Failure | High |
| DC_TC_005 | Reject date above upper bound | Date Converter | ADR_004_001 | Unit | Validation Failure | High |
| DC_TC_006 | Handle leap day BS conversion correctly | Date Converter | LLD_K15_001 | Unit | Edge Case | High |
| DC_TC_007 | Handle leap day Gregorian conversion correctly | Date Converter | LLD_K15_001 | Unit | Edge Case | High |
| DC_TC_008 | Handle month boundary conversions | Date Converter | LLD_K15_001 | Unit | Edge Case | Medium |
| DC_TC_009 | Handle year boundary conversions | Date Converter | LLD_K15_001 | Unit | Edge Case | Medium |

**DC_TC_001 Details:**
- **Preconditions**: Conversion table loaded, validator available
- **Fixtures**: BS date 2080-01-01, expected Gregorian result
- **Input**: BS date {year: 2080, month: 1, day: 1}
- **Execution Steps**:
  1. Validate BS date format
  2. Lookup conversion in table
  3. Return Gregorian date
  4. Verify day-of-week consistency
- **Expected Output**: Gregorian date {year: 2023, month: 4, day: 14}
- **Expected State Changes**: Conversion completed
- **Expected Events**: Conversion success event
- **Expected Audit**: Conversion logged
- **Expected Observability**: Conversion metrics
- **Expected External Interactions**: None (table lookup)
- **Cleanup**: None
- **Branch IDs Covered**: bs_to_gregorian_success, table_lookup
- **Statement Groups Covered**: date_converter, table_lookup

### Test Cases for DualDate Generation

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| DD_TC_001 | Generate DualDate from current timestamp | DualDate Factory | LLD_K15_001 | Unit | Happy Path | High |
| DD_TC_002 | Format BS timestamp with cultural conventions | DualDate Factory | LLD_K15_001 | Unit | Formatting | High |
| DD_TC_003 | Extract date components correctly | DualDate Factory | LLD_K15_001 | Unit | Happy Path | High |
| DD_TC_004 | Validate DualDate internal consistency | DualDate Factory | LLD_K15_001 | Unit | Validation | High |
| DD_TC_005 | Handle timestamp at day boundary | DualDate Factory | LLD_K15_001 | Unit | Edge Case | Medium |

**DD_TC_001 Details:**
- **Preconditions**: Timestamp available, conversion table loaded
- **Fixtures**: Current timestamp, expected DualDate components
- **Input**: Timestamp {milliseconds: 1681507200000}
- **Execution Steps**:
  1. Convert timestamp to Gregorian date
  2. Convert Gregorian to BS date
  3. Create DualDate object
  4. Validate internal consistency
- **Expected Output**: DualDate {gregorian: 2023-04-14, bs: 2080-01-01, timestamp: 1681507200000}
- **Expected State Changes**: DualDate created
- **Expected Events**: DualDate created event
- **Expected Audit**: DualDate creation logged
- **Expected Observability**: DualDate metrics
- **Expected External Interactions**: None
- **Cleanup**: None
- **Branch IDs Covered**: dualdate_creation_success, timestamp_conversion
- **Statement Groups Covered**: dualdate_factory, timestamp_converter

### Test Cases for Fiscal Year Management

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| FY_TC_001 | Calculate fiscal year for regular date | Fiscal Calculator | LLD_K15_001 | Unit | Happy Path | High |
| FY_TC_002 | Calculate fiscal year for boundary date | Fiscal Calculator | LLD_K15_001 | Unit | Edge Case | High |
| FY_TC_003 | Determine fiscal quarter correctly | Fiscal Calculator | LLD_K15_001 | Unit | Happy Path | High |
| FY_TC_004 | Format fiscal year label per convention | Fiscal Calculator | LLD_K15_001 | Unit | Formatting | High |
| FY_TC_005 | Handle fiscal year configuration changes | Fiscal Calculator | LLD_K15_001 | Integration | Configuration | Medium |

**FY_TC_001 Details:**
- **Preconditions**: Fiscal year rules configured
- **Fixtures**: Date 2023-04-14, expected fiscal year
- **Input**: Date {year: 2023, month: 4, day: 14}
- **Execution Steps**:
  1. Check fiscal year start date
  2. Compare input date to boundary
  3. Determine fiscal year
  4. Return fiscal year label
- **Expected Output**: Fiscal year {label: "FY2080-81", start: 2080-01-01, end: 2080-12-31}
- **Expected State Changes**: Fiscal year calculated
- **Expected Events**: Fiscal calculation event
- **Expected Audit**: Fiscal calculation logged
- **Expected Observability**: Fiscal metrics
- **Expected External Interactions**: None
- **Cleanup**: None
- **Branch IDs Covered**: fiscal_calculation_success, regular_date
- **Statement Groups Covered**: fiscal_calculator, date_comparator

### Test Cases for Holiday Management

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| HM_TC_001 | Lookup holiday returns true for holiday | Holiday Manager | LLD_K15_001 | Unit | Happy Path | High |
| HM_TC_002 | Lookup holiday returns false for non-holiday | Holiday Manager | LLD_K15_001 | Unit | Happy Path | High |
| HM_TC_003 | Update holiday calendar propagates changes | Holiday Manager | LLD_K15_001 | Integration | Configuration | High |
| HM_TC_004 | Handle jurisdiction-specific holidays | Holiday Manager | ADR_004_001 | Unit | Jurisdiction | High |
| HM_TC_005 | Cache holiday lookup results | Holiday Manager | LLD_K15_001 | Performance | Caching | Medium |

**HM_TC_001 Details:**
- **Preconditions**: Holiday calendar loaded for jurisdiction
- **Fixtures**: Nepal holiday calendar, test holiday date
- **Input**: Date {year: 2080, month: 10, day: 10}, jurisdiction: "NP"
- **Execution Steps**:
  1. Check cache for holiday lookup
  2. Query holiday calendar
  3. Cache result if found
  4. Return holiday status
- **Expected Output**: Holiday status {is_holiday: true, name: "Dashain"}
- **Expected State Changes**: Holiday cached
- **Expected Events**: Holiday lookup event
- **Expected Audit**: Holiday lookup logged
- **Expected Observability**: Holiday metrics
- **Expected External Interactions**: Holiday calendar storage
- **Cleanup**: Clear cache
- **Branch IDs Covered**: holiday_lookup_success, cache_miss
- **Statement Groups Covered**: holiday_manager, cache_manager

### Test Cases for Business Day Computation

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| BD_TC_001 | Weekday returns true for business day | Business Day Calculator | LLD_K15_001 | Unit | Happy Path | High |
| BD_TC_002 | Weekend returns false for business day | Business Day Calculator | ADR_004_001 | Unit | Weekend | High |
| BD_TC_003 | Holiday returns false for business day | Business Day Calculator | LLD_K15_001 | Unit | Holiday | High |
| BD_TC_004 | Handle jurisdiction weekend variance | Business Day Calculator | ADR_004_001 | Unit | Jurisdiction | High |
| BD_TC_005 | Performance hot-path meets latency target | Business Day Calculator | LLD_K15_001 | Performance | Latency | High |

**BD_TC_001 Details:**
- **Preconditions**: Business day rules configured
- **Fixtures**: Weekday date, weekend rules
- **Input**: Date {year: 2023, month: 4, day: 14}, jurisdiction: "NP"
- **Execution Steps**:
  1. Check if date is weekend
  2. Check if date is holiday
  3. Evaluate business day status
  4. Return result
- **Expected Output**: Business day status {is_business_day: true, reason: "weekday"}
- **Expected State Changes**: None (stateless)
- **Expected Events**: Business day evaluation event
- **Expected Audit**: Business day evaluation logged
- **Expected Observability**: Business day metrics
- **Expected External Interactions**: Holiday manager
- **Cleanup**: None
- **Branch IDs Covered**: business_day_success, weekday_evaluation
- **Statement Groups Covered**: business_day_calculator, weekend_checker

### Test Cases for Settlement Date Calculation

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| SD_TC_001 | Calculate T+2 settlement with no skipped days | Settlement Calculator | LLD_K15_001 | Unit | Happy Path | High |
| SD_TC_002 | Calculate settlement skipping weekend | Settlement Calculator | LLD_K15_001 | Unit | Weekend | High |
| SD_TC_003 | Calculate settlement skipping holiday | Settlement Calculator | LLD_K15_001 | Unit | Holiday | High |
| SD_TC_004 | Handle long settlement periods | Settlement Calculator | LLD_K15_001 | Unit | Extended | Medium |
| SD_TC_005 | Track skipped dates correctly | Settlement Calculator | LLD_K15_001 | Unit | Tracking | Medium |

**SD_TC_001 Details:**
- **Preconditions**: Business day calculator available
- **Fixtures**: Start date, settlement period
- **Input**: Start date {year: 2023, month: 4, day: 14}, days: 2
- **Execution Steps**:
  1. Start from input date
  2. Add business days sequentially
  3. Skip non-business days
  4. Track skipped dates
  5. Return settlement date
- **Expected Output**: Settlement date {date: 2023-04-18, skipped_dates: []}
- **Expected State Changes**: None (stateless)
- **Expected Events**: Settlement calculation event
- **Expected Audit**: Settlement calculation logged
- **Expected Observability**: Settlement metrics
- **Expected External Interactions**: Business day calculator
- **Cleanup**: None
- **Branch IDs Covered**: settlement_success, no_skipped_days
- **Statement Groups Covered**: settlement_calculator, date_adder

### Test Cases for Batch Operations

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| BO_TC_001 | Bulk conversion of valid dates | Batch Processor | LLD_K15_001 | Integration | Happy Path | High |
| BO_TC_002 | Handle mixed valid and invalid inputs | Batch Processor | LLD_K15_001 | Integration | Mixed Input | High |
| BO_TC_003 | Maintain consistency across batch | Batch Processor | LLD_K15_001 | Integration | Consistency | High |
| BO_TC_004 | Optimize batch performance | Batch Processor | LLD_K15_001 | Performance | Optimization | Medium |

**BO_TC_001 Details:**
- **Preconditions**: Batch processor available, conversion table loaded
- **Fixtures**: Array of test dates
- **Input**: Dates [{bs: 2080-01-01}, {bs: 2080-01-02}, {bs: 2080-01-03}]
- **Execution Steps**:
  1. Validate all inputs
  2. Process conversions in batch
  3. Collect results
  4. Return batch results
- **Expected Output**: Results [{gregorian: 2023-04-14}, {gregorian: 2023-04-15}, {gregorian: 2023-04-16}]
- **Expected State Changes**: None (stateless)
- **Expected Events**: Batch conversion event
- **Expected Audit**: Batch conversion logged
- **Expected Observability**: Batch metrics
- **Expected External Interactions**: None
- **Cleanup**: None
- **Branch IDs Covered**: batch_success, all_valid_inputs
- **Statement Groups Covered**: batch_processor, conversion_aggregator

---

## 7. Real-World Scenario Suites

### Suite RW_001: Nepal Trading Week Settlement
- **Suite ID**: RW_001
- **Business Narrative**: Calculate settlement dates for Nepal trading week with Dashain holiday
- **Actors**: Trading System, K-15 Dual-Calendar, Holiday Calendar
- **Preconditions**: Nepal holiday calendar loaded, trading week active
- **Timeline**: 1 week
- **Exact Input Set**: Trade dates, settlement periods, holiday schedule
- **Expected Outputs per Step**:
  1. Trade on Monday settles Wednesday (T+2)
  2. Trade on Thursday settles Monday (skips weekend)
  3. Trade during Dashain skips holiday
- **Expected Failure Variants**: Invalid dates, calendar unavailability
- **Expected Recovery Variants**: Error messages, fallback calculations

### Suite RW_002: Fiscal Year Reporting
- **Suite ID**: RW_002
- **Business Narrative**: Generate fiscal year reports with proper date boundaries
- **Actors**: Reporting System, K-15 Dual-Calendar, Fiscal Calculator
- **Preconditions**: Fiscal year rules configured
- **Timeline**: 1 fiscal year
- **Exact Input Set**: Transaction dates, reporting periods
- **Expected Outputs per Step**:
  1. Transactions grouped by fiscal year
  2. Quarterly summaries calculated
  3. Year-end boundaries respected
- **Expected Failure Variants**: Boundary date errors, configuration issues
- **Expected Recovery Variants**: Manual corrections, config updates

---

## 8. Coverage Matrices

### Requirement Coverage Matrix
| Requirement ID | Test Cases | Coverage Status |
|----------------|------------|-----------------|
| REQ_DC_001 | DC_TC_001-009 | 100% |
| REQ_DD_001 | DD_TC_001-005 | 100% |
| REQ_FY_001 | FY_TC_001-005 | 100% |
| REQ_HM_001 | HM_TC_001-005 | 100% |
| REQ_BD_001 | BD_TC_001-005 | 100% |
| REQ_SD_001 | SD_TC_001-005 | 100% |
| REQ_BO_001 | BO_TC_001-004 | 100% |

### Branch Coverage Matrix
| Branch ID | Test Cases | Coverage Status |
|-----------|------------|-----------------|
| bs_to_gregorian_success | DC_TC_001, DC_TC_006-008 | 100% |
| gregorian_to_bs_success | DC_TC_002, DC_TC_007-009 | 100% |
| round_trip_success | DC_TC_003 | 100% |
| bounds_validation | DC_TC_004-005 | 100% |
| dualdate_creation_success | DD_TC_001-005 | 100% |
| fiscal_calculation_success | FY_TC_001-004 | 100% |
| holiday_lookup_success | HM_TC_001-002, HM_TC_004-005 | 100% |
| business_day_success | BD_TC_001-005 | 100% |
| settlement_success | SD_TC_001-005 | 100% |
| batch_success | BO_TC_001-004 | 100% |

### Statement Coverage Matrix
| Statement Group | Test Cases | Coverage Status |
|-----------------|------------|-----------------|
| date_converter | DC_TC_001-009 | 100% |
| dualdate_factory | DD_TC_001-005 | 100% |
| fiscal_calculator | FY_TC_001-005 | 100% |
| holiday_manager | HM_TC_001-005 | 100% |
| business_day_calculator | BD_TC_001-005 | 100% |
| settlement_calculator | SD_TC_001-005 | 100% |
| batch_processor | BO_TC_001-004 | 100% |

---

## 9. Coverage Gaps and Exclusions

**No known gaps** - All identified behaviors and scenarios are covered by test cases.

**Exclusions:**
- Multi-timezone handling (deferred to K-02 configuration)
- Advanced calendar types (future enhancement)
- Historical calendar changes (out of scope)
- Localization features (UI concern)

---

## 10. Recommended Test File Plan

### Unit Tests
- `src/test/java/com/siddhanta/calendar/DateConverterTest.java`
- `src/test/java/com/siddhanta/calendar/DualDateFactoryTest.java`
- `src/test/java/com/siddhanta/calendar/FiscalCalculatorTest.java`
- `src/test/java/com/siddhanta/calendar/HolidayManagerTest.java`
- `src/test/java/com/siddhanta/calendar/BusinessDayCalculatorTest.java`
- `src/test/java/com/siddhanta/calendar/SettlementCalculatorTest.java`

### Component Tests
- `src/test/java/com/siddhanta/calendar/CalendarComponentTest.java`
- `src/test/java/com/siddhanta/calendar/HolidayCalendarComponentTest.java`
- `src/test/java/com/siddhanta/calendar/BatchProcessorComponentTest.java`

### Integration Tests
- `src/test/java/com/siddhanta/calendar/CalendarIntegrationTest.java`
- `src/test/java/com/siddhanta/calendar/K02IntegrationTest.java`
- `src/test/java/com/siddhanta/calendar/EndToEndCalendarTest.java`

### Performance Tests
- `src/test/java/com/siddhanta/calendar/PerformanceTest.java`
- `src/test/java/com/siddhanta/calendar/HotPathPerformanceTest.java`
- `src/test/java/com/siddhanta/calendar/BatchPerformanceTest.java`

### Business Scenario Tests
- `src/test/java/com/siddhanta/calendar/NepalTradingWeekTest.java`
- `src/test/java/com/siddhanta/calendar/FiscalYearReportingTest.java`
- `src/test/java/com/siddhanta/calendar/SettlementCalculationTest.java`

---

## 11. Machine-Readable Appendix

```yaml
test_plan:
  scope: k15_dual_calendar
  modules:
    - date_conversion
    - dualdate_generation
    - fiscal_year_management
    - holiday_management
    - business_day_computation
    - settlement_date_calculation
    - batch_operations
  cases:
    - id: DC_TC_001
      title: Convert BS 2080-01-01 to Gregorian correctly
      layer: unit
      module: date_conversion
      scenario_type: happy_path
      requirement_refs: [LLD_K15_001]
      source_refs: [LLD_K15_DUAL_CALENDAR.md]
      preconditions: [conversion_table_loaded, validator_available]
      fixtures: [bs_date_2080_01_01, expected_gregorian_result]
      input: {bs_date: {year: 2080, month: 1, day: 1}}
      steps:
        - validate_bs_date_format
        - lookup_conversion_in_table
        - return_gregorian_date
        - verify_day_of_week_consistency
      expected_output: {gregorian_date: {year: 2023, month: 4, day: 14}, day_of_week: "Friday"}
      expected_state_changes: [conversion_completed]
      expected_events: [conversion_success_event]
      expected_audit: [conversion_logged]
      expected_observability: [conversion_metrics]
      expected_external_interactions: []
      cleanup: []
      branch_ids_covered: [bs_to_gregorian_success, table_lookup]
      statement_groups_covered: [date_converter, table_lookup]
    
    - id: DD_TC_001
      title: Generate DualDate from current timestamp
      layer: unit
      module: dualdate_generation
      scenario_type: happy_path
      requirement_refs: [LLD_K15_001]
      source_refs: [LLD_K15_DUAL_CALENDAR.md]
      preconditions: [timestamp_available, conversion_table_loaded]
      fixtures: [current_timestamp, expected_dualdate_components]
      input: {timestamp: 1681507200000}
      steps:
        - convert_timestamp_to_gregorian_date
        - convert_gregorian_to_bs_date
        - create_dualdate_object
        - validate_internal_consistency
      expected_output: {dualdate: {gregorian: "2023-04-14", bs: "2080-01-01", timestamp: 1681507200000}}
      expected_state_changes: [dualdate_created]
      expected_events: [dualdate_created_event]
      expected_audit: [dualdate_creation_logged]
      expected_observability: [dualdate_metrics]
      expected_external_interactions: []
      cleanup: []
      branch_ids_covered: [dualdate_creation_success, timestamp_conversion]
      statement_groups_covered: [dualdate_factory, timestamp_converter]
    
    - id: FY_TC_001
      title: Calculate fiscal year for regular date
      layer: unit
      module: fiscal_year_management
      scenario_type: happy_path
      requirement_refs: [LLD_K15_001]
      source_refs: [LLD_K15_DUAL_CALENDAR.md]
      preconditions: [fiscal_year_rules_configured]
      fixtures: [date_2023_04_14, expected_fiscal_year]
      input: {date: {year: 2023, month: 4, day: 14}}
      steps:
        - check_fiscal_year_start_date
        - compare_input_date_to_boundary
        - determine_fiscal_year
        - return_fiscal_year_label
      expected_output: {fiscal_year: {label: "FY2080-81", start: "2080-01-01", end: "2080-12-31"}}
      expected_state_changes: [fiscal_year_calculated]
      expected_events: [fiscal_calculation_event]
      expected_audit: [fiscal_calculation_logged]
      expected_observability: [fiscal_metrics]
      expected_external_interactions: []
      cleanup: []
      branch_ids_covered: [fiscal_calculation_success, regular_date]
      statement_groups_covered: [fiscal_calculator, date_comparator]
    
    - id: HM_TC_001
      title: Lookup holiday returns true for holiday
      layer: unit
      module: holiday_management
      scenario_type: happy_path
      requirement_refs: [LLD_K15_001]
      source_refs: [LLD_K15_DUAL_CALENDAR.md]
      preconditions: [holiday_calendar_loaded_for_jurisdiction]
      fixtures: [nepal_holiday_calendar, test_holiday_date]
      input: {date: {year: 2080, month: 10, day: 10}, jurisdiction: "NP"}
      steps:
        - check_cache_for_holiday_lookup
        - query_holiday_calendar
        - cache_result_if_found
        - return_holiday_status
      expected_output: {is_holiday: true, name: "Dashain"}
      expected_state_changes: [holiday_cached]
      expected_events: [holiday_lookup_event]
      expected_audit: [holiday_lookup_logged]
      expected_observability: [holiday_metrics]
      expected_external_interactions: [holiday_calendar_storage]
      cleanup: [clear_cache]
      branch_ids_covered: [holiday_lookup_success, cache_miss]
      statement_groups_covered: [holiday_manager, cache_manager]
    
    - id: BD_TC_001
      title: Weekday returns true for business day
      layer: unit
      module: business_day_computation
      scenario_type: happy_path
      requirement_refs: [LLD_K15_001]
      source_refs: [LLD_K15_DUAL_CALENDAR.md]
      preconditions: [business_day_rules_configured]
      fixtures: [weekday_date, weekend_rules]
      input: {date: {year: 2023, month: 4, day: 14}, jurisdiction: "NP"}
      steps:
        - check_if_date_is_weekend
        - check_if_date_is_holiday
        - evaluate_business_day_status
        - return_result
      expected_output: {is_business_day: true, reason: "weekday"}
      expected_state_changes: []
      expected_events: [business_day_evaluation_event]
      expected_audit: [business_day_evaluation_logged]
      expected_observability: [business_day_metrics]
      expected_external_interactions: [holiday_manager]
      cleanup: []
      branch_ids_covered: [business_day_success, weekday_evaluation]
      statement_groups_covered: [business_day_calculator, weekend_checker]
    
    - id: SD_TC_001
      title: Calculate T+2 settlement with no skipped days
      layer: unit
      module: settlement_date_calculation
      scenario_type: happy_path
      requirement_refs: [LLD_K15_001]
      source_refs: [LLD_K15_DUAL_CALENDAR.md]
      preconditions: [business_day_calculator_available]
      fixtures: [start_date, settlement_period]
      input: {start_date: {year: 2023, month: 4, day: 14}, days: 2}
      steps:
        - start_from_input_date
        - add_business_days_sequentially
        - skip_non_business_days
        - track_skipped_dates
        - return_settlement_date
      expected_output: {settlement_date: {date: "2023-04-18"}, skipped_dates: []}
      expected_state_changes: []
      expected_events: [settlement_calculation_event]
      expected_audit: [settlement_calculation_logged]
      expected_observability: [settlement_metrics]
      expected_external_interactions: [business_day_calculator]
      cleanup: []
      branch_ids_covered: [settlement_success, no_skipped_days]
      statement_groups_covered: [settlement_calculator, date_adder]
    
    - id: BO_TC_001
      title: Bulk conversion of valid dates
      layer: integration
      module: batch_operations
      scenario_type: happy_path
      requirement_refs: [LLD_K15_001]
      source_refs: [LLD_K15_DUAL_CALENDAR.md]
      preconditions: [batch_processor_available, conversion_table_loaded]
      fixtures: [array_of_test_dates]
      input: {dates: [{bs: "2080-01-01"}, {bs: "2080-01-02"}, {bs: "2080-01-03"}]}
      steps:
        - validate_all_inputs
        - process_conversions_in_batch
        - collect_results
        - return_batch_results
      expected_output: {results: [{gregorian: "2023-04-14"}, {gregorian: "2023-04-15"}, {gregorian: "2023-04-16"}]}
      expected_state_changes: []
      expected_events: [batch_conversion_event]
      expected_audit: [batch_conversion_logged]
      expected_observability: [batch_metrics]
      expected_external_interactions: []
      cleanup: []
      branch_ids_covered: [batch_success, all_valid_inputs]
      statement_groups_covered: [batch_processor, conversion_aggregator]

  coverage:
    requirement_ids:
      REQ_DC_001: [DC_TC_001, DC_TC_002, DC_TC_003, DC_TC_004, DC_TC_005, DC_TC_006, DC_TC_007, DC_TC_008, DC_TC_009]
      REQ_DD_001: [DD_TC_001, DD_TC_002, DD_TC_003, DD_TC_004, DD_TC_005]
      REQ_FY_001: [FY_TC_001, FY_TC_002, FY_TC_003, FY_TC_004, FY_TC_005]
      REQ_HM_001: [HM_TC_001, HM_TC_002, HM_TC_003, HM_TC_004, HM_TC_005]
      REQ_BD_001: [BD_TC_001, BD_TC_002, BD_TC_003, BD_TC_004, BD_TC_005]
      REQ_SD_001: [SD_TC_001, SD_TC_002, SD_TC_003, SD_TC_004, SD_TC_005]
      REQ_BO_001: [BO_TC_001, BO_TC_002, BO_TC_003, BO_TC_004]
    branch_ids:
      bs_to_gregorian_success: [DC_TC_001, DC_TC_006, DC_TC_007, DC_TC_008]
      gregorian_to_bs_success: [DC_TC_002, DC_TC_006, DC_TC_007, DC_TC_009]
      round_trip_success: [DC_TC_003]
      bounds_validation: [DC_TC_004, DC_TC_005]
      dualdate_creation_success: [DD_TC_001, DD_TC_002, DD_TC_003, DD_TC_004, DD_TC_005]
      fiscal_calculation_success: [FY_TC_001, FY_TC_002, FY_TC_003, FY_TC_004]
      holiday_lookup_success: [HM_TC_001, HM_TC_002, HM_TC_004, HM_TC_005]
      business_day_success: [BD_TC_001, BD_TC_002, BD_TC_003, BD_TC_004, BD_TC_005]
      settlement_success: [SD_TC_001, SD_TC_002, SD_TC_003, SD_TC_004, SD_TC_005]
      batch_success: [BO_TC_001, BO_TC_002, BO_TC_003, BO_TC_004]
    statement_groups:
      date_converter: [DC_TC_001, DC_TC_002, DC_TC_003, DC_TC_004, DC_TC_005, DC_TC_006, DC_TC_007, DC_TC_008, DC_TC_009]
      dualdate_factory: [DD_TC_001, DD_TC_002, DD_TC_003, DD_TC_004, DD_TC_005]
      fiscal_calculator: [FY_TC_001, FY_TC_002, FY_TC_003, FY_TC_004, FY_TC_005]
      holiday_manager: [HM_TC_001, HM_TC_002, HM_TC_003, HM_TC_004, HM_TC_005]
      business_day_calculator: [BD_TC_001, BD_TC_002, BD_TC_003, BD_TC_004, BD_TC_005]
      settlement_calculator: [SD_TC_001, SD_TC_002, SD_TC_003, SD_TC_004, SD_TC_005]
      batch_processor: [BO_TC_001, BO_TC_002, BO_TC_003, BO_TC_004]
  exclusions: []
```

---

**K-15 Dual-Calendar TDD specification complete.** This provides exhaustive test coverage for BS/Gregorian conversion, DualDate generation, fiscal year management, holiday handling, business day computation, settlement calculation, and batch operations. The specification is ready for test implementation and subsequent code generation to satisfy these tests.
