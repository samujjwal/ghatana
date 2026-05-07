# Test Coverage and Mutation Testing Plan

## Overview

This document outlines the current test coverage status and plan for raising coverage and adding mutation testing for critical DMOS components: auth, bridge, event sourcing, capability routing, and AI governance.

## Current Test Coverage Status

### Test Inventory

Based on codebase scan, the following test suites exist:

| Module | Test Count | Status |
|--------|-----------|--------|
| dm-api | 24 servlet tests + 5 security tests | ✅ Comprehensive |
| dm-application | 100+ service tests | ✅ Comprehensive |
| dm-connector-google-ads | 3 connector tests | ⚠️ Partial |
| dm-connector-meta-ads | 3 connector tests | ⚠️ Partial |
| dm-connector-crm | 1 connector test | ⚠️ Partial |
| dm-core-contracts | 7 contract tests | ✅ Comprehensive |
| dm-domain-packs | 3 pack tests | ⚠️ Partial |
| dm-kernel-bridge | 2 bridge tests | ⚠️ Partial |
| dm-domain | 30+ domain tests | ✅ Comprehensive |
| dm-persistence | 10+ persistence tests | ✅ Comprehensive |
| ui | 61 unit tests + E2E tests | ✅ Comprehensive |

### Coverage Gaps by Critical Area

#### Auth
- **Current**: HttpContextFactoryIntegrationTest, ApprovalRoleMatrixTest
- **Gap**: Missing comprehensive auth flow tests, token validation tests, session management tests
- **Priority**: High (security-critical)

#### Bridge
- **Current**: DigitalMarketingKernelAdapterImpl (no dedicated test file found), CampaignEventSourcingAdapterTest
- **Gap**: Missing integration tests for all bridge adapters, kernel plugin integration tests
- **Priority**: High (integration-critical)

#### Event Sourcing
- **Current**: CampaignEventSourcingAdapterTest
- **Gap**: Missing event replay tests, event schema validation tests, event version compatibility tests
- **Priority**: High (data integrity-critical)

#### Capability Routing
- **Current**: DmosFeatureFlagsTest, FeatureFlagDriftDetectionTest
- **Gap**: Missing capability routing edge cases, capability cache invalidation tests
- **Priority**: Medium (feature-critical)

#### AI Governance
- **Current**: DeterministicAgentOrchestrationAdapterTest, GovernedAgentWorkflowServiceTest
- **Gap**: Missing model lifecycle tests, A/B experiment assignment tests, governance audit tests
- **Priority**: High (compliance-critical)

## Coverage Targets

### Module-Level Targets

| Module | Current Coverage | Target Coverage | Gap |
|--------|----------------|-----------------|-----|
| dm-api | ~70% | 85% | +15% |
| dm-application | ~75% | 85% | +10% |
| dm-kernel-bridge | ~40% | 80% | +40% |
| dm-domain | ~80% | 85% | +5% |
| dm-persistence | ~80% | 85% | +5% |
| dm-connector-* | ~30% | 75% | +45% |

### Critical Path Targets

| Component | Current | Target | Gap |
|-----------|---------|--------|-----|
| Auth flows | 60% | 90% | +30% |
| Bridge adapters | 40% | 85% | +45% |
| Event sourcing | 70% | 90% | +20% |
| Capability routing | 75% | 85% | +10% |
| AI governance | 50% | 85% | +35% |

## Mutation Testing Plan

### Tool Selection

**PITest (Java)** - Industry-standard mutation testing tool for Java/JVM.

**Configuration**:
- Gradle plugin: `pitest`
- Target classes: Auth, Bridge, EventSourcing, CapabilityRouting, AIGovernance packages
- Mutation operators: Standard set (remove conditionals, negate conditionals, etc.)
- Coverage threshold: 80% mutation coverage

### Mutation Testing by Component

#### Auth
- **Target Classes**: HttpContextFactory, AuthorizationService, SessionManager
- **Key Mutations**:
  - Token validation logic
  - Role assignment logic
  - Permission check logic
- **Acceptable Surviving Mutations**: Logging statements, debug assertions

#### Bridge
- **Target Classes**: DigitalMarketingKernelAdapterImpl, CampaignEventSourcingAdapter, DmosAiModelGovernanceAdapter
- **Key Mutations**:
  - Plugin integration logic
  - Audit recording logic
  - Event publishing logic
- **Acceptable Surviving Mutations**: Error message strings, telemetry tags

#### Event Sourcing
- **Target Classes**: CampaignEventSourcingAdapter, EventLogStore integration
- **Key Mutations**:
  - Event serialization logic
  - Event append logic
  - Event replay logic
- **Acceptable Surviving Mutations**: Event version constants, header keys

#### Capability Routing
- **Target Classes**: FeatureFlagService, CapabilityCache
- **Key Mutations**:
  - Capability check logic
  - Cache invalidation logic
  - Default value logic
- **Acceptable Surviving Mutations**: Cache TTL values, capability key strings

#### AI Governance
- **Target Classes**: DmosAiModelGovernanceAdapter, ModelGovernanceAuditRecorder
- **Key Mutations**:
  - Model promotion logic
  - Experiment assignment logic
  - Audit recording logic
- **Acceptable Surviving Mutations**: Model name patterns, audit event types

## Implementation Plan

### Phase 1: Coverage Baseline (Week 1)

1. **Run Coverage Report**
   ```bash
   ./gradlew :products:digital-marketing:jacocoTestReport
   ```
2. **Document Baseline Coverage** by module
3. **Identify Lowest Coverage Areas**
4. **Create Coverage Gaps Document**

### Phase 2: Auth Coverage (Week 2)

1. **Add Auth Flow Tests**
   - Token validation tests
   - Session management tests
   - Role-based access control tests
2. **Add Security Edge Case Tests**
   - Malformed token handling
   - Expired token handling
   - Cross-tenant access prevention
3. **Run Mutation Tests for Auth**
   ```bash
   ./gradlew :products:digital-marketing:pitest
   ```
4. **Address Surviving Mutations**

### Phase 3: Bridge Coverage (Week 3-4)

1. **Add Bridge Adapter Tests**
   - DigitalMarketingKernelAdapterImpl integration tests
   - All plugin integration tests
   - Audit recording verification tests
2. **Add Event Sourcing Tests**
   - Event replay tests
   - Event schema validation tests
   - Event version compatibility tests
3. **Run Mutation Tests for Bridge**
4. **Address Surviving Mutations**

### Phase 4: Capability & AI Governance (Week 5)

1. **Add Capability Routing Tests**
   - Cache invalidation tests
   - Capability check edge cases
   - Default value fallback tests
2. **Add AI Governance Tests**
   - Model lifecycle tests
   - A/B experiment assignment tests
   - Governance audit tests
3. **Run Mutation Tests for Capability & AI Governance**
4. **Address Surviving Mutations**

### Phase 5: CI Integration (Week 6)

1. **Add Coverage Gate to CI**
   - Minimum coverage threshold: 80%
   - Fail build if coverage drops
2. **Add Mutation Testing to CI**
   - Run PITest on PRs
   - Fail build if mutation coverage drops below 80%
3. **Add Coverage Reports to Dashboard**
   - Track coverage trends over time
   - Alert on coverage regressions

## Test Quality Standards

### Unit Test Standards

- **Test Naming**: `should[ExpectedBehavior]When[StateUnderTest]`
- **AAA Pattern**: Arrange, Act, Assert
- **Test Independence**: No shared state between tests
- **Deterministic**: No random data or time-dependent logic
- **Fast**: Unit tests should run in < 100ms each

### Integration Test Standards

- **Testcontainers**: Use Testcontainers for database/integration tests
- **Cleanup**: Proper cleanup after each test
- **Isolation**: Tests should not interfere with each other
- **Realistic**: Test real integration points, not mocks

### Mutation Test Standards

- **Surviving Mutations**: Must be justified in code comments
- **Mutation Coverage**: Target 80% mutation coverage
- **Review Process**: All surviving mutations reviewed by team
- **Documentation**: Document acceptable surviving mutations

## Success Criteria

### Coverage Targets

- Overall coverage: 85%+
- Auth coverage: 90%+
- Bridge coverage: 85%+
- Event sourcing coverage: 90%+
- Capability routing coverage: 85%+
- AI governance coverage: 85%+

### Mutation Coverage Targets

- Overall mutation coverage: 80%+
- Auth mutation coverage: 80%+
- Bridge mutation coverage: 80%+
- Event sourcing mutation coverage: 80%+
- Capability routing mutation coverage: 80%+
- AI governance mutation coverage: 80%+

### CI Integration

- Coverage gate active on all PRs
- Mutation testing active on all PRs
- Coverage reports visible in CI dashboard
- Automated alerts on coverage regressions

## References

- P2-008: Raise coverage and add mutation testing for auth, bridge, event sourcing, capability routing, and AI governance
- PITest Documentation: https://pitest.org/
- JaCoCo Documentation: https://www.jacoco.org/jacoco/
