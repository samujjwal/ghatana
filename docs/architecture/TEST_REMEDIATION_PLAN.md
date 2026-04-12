# Test Ecosystem Remediation Plan

**Status**: Active  
**Last Updated**: 2026-04-12  
**Purpose**: Systematic remediation of test ecosystem issues identified in comprehensive audit

---

## Executive Summary

This document provides a detailed, actionable plan to address critical gaps in the Ghatana repository's test ecosystem. The plan is organized by priority level and task category.

### Key Metrics
- **Total Tasks**: 47
- **Priority 0 (Critical)**: 3 tasks
- **Priority 1 (High)**: 4 tasks
- **Priority 2 (High)**: 4 tasks
- **Priority 3 (Medium)**: 3 tasks
- **Priority 4 (Medium)**: 4 tasks
- **Priority 5 (Low)**: 3 tasks
- **Priority 6 (Low)**: 3 tasks

---

## Priority 0: Incorrect or Misleading Tests

### Task 0.1: Fix Placeholder Assertions in Ghatana Studio Sections Tests

**File**: `platform/typescript/ghatana-studio/src/__tests__/sections.test.tsx`  
**Severity**: Critical  
**Estimated Effort**: 4 hours

#### Current State
- All section tests use `expect(true).toBe(true)` placeholder assertions
- Tests only verify components render without errors
- No functional validation of studio sections

#### Required Actions
1. Identify core functionality for each studio section (Builder, Theme, Governance, Registry)
2. Implement actual assertions for each section's core functionality
3. Add negative case tests (invalid inputs, permission denied scenarios)
4. Verify cross-section workflows

#### Success Criteria
- All placeholder assertions replaced with functional validations
- Each section has at least 5 meaningful test cases
- Cross-section workflow tests added
- All tests pass with >80% code coverage

---

### Task 0.2: Add Actual Codegen Output Validation in UI Builder

**File**: `platform/typescript/ui-builder/src/core/__tests__/codegen.test.ts`  
**Severity**: Critical  
**Estimated Effort**: 8 hours

#### Current State
- Tests verify code structure but not actual generated code output
- Codegen is critical but output is never validated for correctness

#### Required Actions
1. Create sample BuilderDocument fixtures
2. Implement code generation validation tests (TypeScript compilation, semantic correctness)
3. Add round-trip tests (generate → parse → compare)
4. Test edge cases (empty documents, circular references, invalid contracts)

#### Success Criteria
- Generated code is syntactically valid (TypeScript compilation)
- Generated code is semantically correct (matches input document)
- Round-trip tests pass for all sample documents
- Coverage >90% for codegen module

---

### Task 0.3: Add Actual Renderer Output Validation in UI Builder

**File**: `platform/typescript/ui-builder/src/react/__tests__/renderer.test.tsx`  
**Severity**: Critical  
**Estimated Effort**: 6 hours

#### Current State
- Tests verify component structure but not actual rendering behavior
- Renderer is critical but output is never validated

#### Required Actions
1. Implement actual rendering tests using @testing-library/react
2. Validate DOM output (HTML structure, ARIA attributes, class names)
3. Test data binding resolution
4. Test event binding execution
5. Test slot rendering

#### Success Criteria
- All renderer tests use actual DOM validation
- Data bindings resolve and display correctly
- Event handlers execute as expected
- Slots render in correct locations
- Accessibility attributes are present
- Coverage >85% for renderer module

---

## Priority 1: Missing Critical-Path/Workflow Coverage

### Task 1.1: Add Contract Test Suites for All Public APIs

**Scope**: All platform packages with public APIs  
**Severity**: High  
**Estimated Effort**: 16 hours

#### Current State
- No validation of API contracts, event schemas, or serialization
- Contract breakages can go undetected until runtime

#### Required Actions
1. Identify all public API contracts (OpenAPI, event schemas, GraphQL, Protobuf)
2. Create contract test structure in platform/typescript/contract-tests/
3. Implement API contract validation
4. Implement event schema validation
5. Implement serialization/deserialization tests
6. Add contract regression detection in CI

#### Success Criteria
- All public APIs have contract validation
- All event schemas have validation
- All serialization boundaries have tests
- Contract changes detected in CI
- Coverage >95% for contract tests

---

### Task 1.2: Add API E2E Tests for Backend Services

**Scope**: TutorPutor, YAPPC, DCMAAR backend services  
**Severity**: High  
**Estimated Effort**: 24 hours

#### Current State
- Only UI E2E tests exist (Playwright)
- Backend APIs have no end-to-end validation

#### Required Actions
1. Set up API E2E test infrastructure (test DB, test server)
2. Implement API E2E tests for TutorPutor (experience CRUD, auto-revision, AI generation, analytics, auth)
3. Implement API E2E tests for YAPPC (intent-to-shape, shape validation, code generation, persistence, collaboration)
4. Implement API E2E tests for DCMAAR (device registration, telemetry ingestion, alerts, notifications, policies)
5. Add database state validation
6. Add error scenario tests

#### Success Criteria
- Each backend service has at least 10 API E2E tests
- All critical API endpoints covered
- Database interactions validated
- Error scenarios tested
- Tests run in <5 minutes per service
- Coverage >80% for API endpoints

---

### Task 1.3: Add Real DB Integration Tests

**Scope**: All packages with DB dependencies  
**Severity**: High  
**Estimated Effort**: 20 hours

#### Current State
- Most integration tests use mocks instead of real DB
- No validation of actual database interactions

#### Required Actions
1. Set up Testcontainers infrastructure (Java and TypeScript)
2. Create TypeScript DB test helpers (setup, teardown, transaction rollback)
3. Implement real DB integration tests for ds-registry
4. Implement DB tests for TutorPutor services
5. Implement DB tests for YAPPC
6. Add transaction rollback strategy for isolation
7. Test concurrent access

#### Success Criteria
- All packages with DB dependencies have real DB integration tests
- Tests use transaction rollback for isolation
- Concurrent access scenarios tested
- SQL queries validated against real schema
- Tests run in <2 minutes per package
- Coverage >75% for DB operations

---

### Task 1.4: Add YAPPC Full Workflow E2E Tests

**Scope**: YAPPC product  
**Severity**: High  
**Estimated Effort**: 32 hours

#### Current State
- YAPPC is core product but lacks comprehensive E2E
- Only component adapter tests exist

#### Required Actions
1. Identify critical YAPPC workflows (Intent→Shape→Validate→Generate→Run, collaborative editing, version control)
2. Set up E2E test infrastructure with Playwright
3. Implement Intent to Shape workflow tests
4. Implement Shape to Validate workflow tests
5. Implement Validate to Generate workflow tests
6. Implement Generate to Run workflow tests
7. Implement Collaborative editing tests
8. Implement Version control tests

#### Success Criteria
- All 7 critical workflows have E2E tests
- Each workflow has at least 5 test scenarios
- Tests run in <10 minutes total
- Coverage >70% for critical paths
- Flaky test rate <5%

---

## Priority 2: Misclassified Tests and Execution-Tier Problems

### Task 2.1: Implement Explicit Test Taxonomy with Tags/Folders

**Scope**: All packages  
**Severity**: High  
**Estimated Effort**: 12 hours

#### Current State
- No explicit test taxonomy
- Cannot predict execution cost or infra needs

#### Required Actions
1. Define test taxonomy (unit, integration, integration-browser, contract, api-e2e, e2e, smoke)
2. Reorganize test folders by category
3. Add test tags for categorization (@test.type, @test.execution, @test.infra)
4. Update vitest configs to respect taxonomy
5. Create taxonomy documentation

#### Success Criteria
- All packages reorganized with clear taxonomy
- Test tags added to all test files
- Vitest configs updated to respect taxonomy
- Taxonomy documentation created
- Developers can predict test cost by location

---

### Task 2.2: Add Tiered Test Scripts

**Scope**: Root package.json and all packages  
**Severity**: High  
**Estimated Effort**: 4 hours

#### Current State
- Single `pnpm test` command runs everything
- No way to run fast vs. slow tests separately

#### Required Actions
1. Add tiered test scripts to root package.json (test:unit, test:integration, test:contract, test:api-e2e, test:e2e)
2. Add tiered test scripts to each package.json
3. Update turbo.json to support tiered execution
4. Add pre-commit hooks for fast tests

#### Success Criteria
- Tiered test scripts added to root and all packages
- test:unit runs in <30 seconds
- test:integration runs in <5 minutes
- test:e2e runs in <30 minutes
- Pre-commit hooks run only unit tests

---

### Task 2.3: Implement Staged CI

**Scope**: CI workflows  
**Severity**: High  
**Estimated Effort**: 8 hours

#### Current State
- CI runs all tests together in single job
- No staged validation

#### Required Actions
1. Create staged CI workflow (unit → integration → contract → api-e2e → e2e)
2. Add workflow for quick PR feedback (unit + type + lint)
3. Add workflow for release validation (full suite + smoke tests)

#### Success Criteria
- Unit tests run on every commit (<5 min)
- Integration tests run after unit passes (<10 min)
- Contract tests run after integration passes (<5 min)
- API E2E tests run on main branch (<20 min)
- UI E2E tests run on main branch (<30 min)
- PRs get quick feedback in <5 min

---

### Task 2.4: Reclassify Mislabeled Integration Tests

**Scope**: ds-registry, device-health, and other packages  
**Severity**: High  
**Estimated Effort**: 6 hours

#### Current State
- Some integration tests are actually unit tests with mocks
- Cannot predict execution cost from labels

#### Required Actions
1. Audit all tests labeled as integration
2. Evaluate each test (uses real DB? real external services? real wiring?)
3. Reclassify tests (move misclassified unit tests to unit folder)
4. Update test file headers to reflect actual category
5. Add real integration tests where missing

#### Success Criteria
- All integration tests use real wiring or infra
- All unit tests are pure logic with no infra
- Test labels match actual execution cost
- No tests mislabeled as integration

---

## Priority 3: Integration/Contract/Cross-Layer Gaps

### Task 3.1: Add Cross-Service Workflow Tests

**Scope**: Product integrations between services  
**Severity**: Medium  
**Estimated Effort**: 16 hours

#### Current State
- No tests validate workflows across services

#### Required Actions
1. Identify critical cross-service workflows (DCMAAR, TutorPutor, YAPPC, Data Cloud)
2. Create cross-service test infrastructure
3. Implement DCMAAR workflow tests (telemetry → dashboard → analytics)
4. Implement TutorPutor workflow tests (content → auto-revision → analytics)
5. Implement YAPPC workflow tests (intent → shape → codegen)
6. Implement Data Cloud workflow tests (registry → ingestion → query)

#### Success Criteria
- All critical cross-service workflows have tests
- Tests validate end-to-end data flow
- Tests verify error handling across services
- Tests run in <5 minutes per workflow

---

### Task 3.2: Add Event Schema Validation Tests

**Scope**: platform-events package  
**Severity**: Medium  
**Estimated Effort**: 8 hours

#### Current State
- Platform events have no contract validation

#### Required Actions
1. Extract event schemas to explicit Zod definitions
2. Create event schema validation tests
3. Add schema evolution tests
4. Add schema versioning tests

#### Success Criteria
- All event schemas have explicit Zod definitions
- All event schemas have validation tests
- Schema evolution tests added
- Breaking changes detected in CI

---

### Task 3.3: Add Serialization/Deserialization Tests

**Scope**: All packages with external APIs  
**Severity**: Medium  
**Estimated Effort**: 12 hours

#### Current State
- No validation of data serialization boundaries

#### Required Actions
1. Identify all serialization boundaries (API, events, DB, cache, message queue)
2. Create serialization test infrastructure
3. Implement API serialization tests
4. Implement event serialization tests
5. Implement database record serialization tests
6. Add binary serialization tests if applicable

#### Success Criteria
- All serialization boundaries have tests
- Serialization/deserialization round-trips work
- Type safety maintained through serialization
- Coverage >90% for serialization code

---

## Priority 4: Fixture/Seed/Config Consistency Issues

### Task 4.1: Create Shared Fixture Library

**Scope**: All packages  
**Severity**: Medium  
**Estimated Effort**: 16 hours

#### Current State
- No shared fixtures across packages
- Duplication of fixture code

#### Required Actions
1. Create platform-testing package structure
2. Create React component fixtures (Button, TextField, Card, etc.)
3. Create document fixtures (empty, simple, complex)
4. Create event fixtures (autonomy events, security policies, privacy policies)
5. Create token fixtures (minimal, complete)
6. Create render helpers

#### Success Criteria
- Platform-testing package created
- All common fixtures implemented
- Fixture duplication reduced by >50%
- New tests can be written 30% faster

---

### Task 4.2: Create Shared Vitest Config

**Scope**: All packages with vitest  
**Severity**: Medium  
**Estimated Effort**: 8 hours

#### Current State
- 46 separate vitest.config.ts files with duplication

#### Required Actions
1. Create shared vitest base config in monorepo root
2. Create environment-specific configs (node, jsdom, react)
3. Update package configs to extend base using mergeConfig
4. Create config documentation

#### Success Criteria
- Base configs created in monorepo root
- All package configs updated to extend base
- Config duplication reduced by >70%
- Consistent settings across packages

---

### Task 4.3: Implement Seed Data Factory

**Scope**: All packages with test data needs  
**Severity**: Medium  
**Estimated Effort**: 12 hours

#### Current State
- Ad-hoc test data creation, hard to maintain

#### Required Actions
1. Create seed data factory in platform-testing using faker-js
2. Implement deterministic generators for common entities (users, documents, events, tokens)
3. Add seed management (setSeed, resetSeed)
4. Create specialized factories for each domain

#### Success Criteria
- Seed data factory implemented
- Deterministic generation for all common entities
- Test data consistency improved
- New test data can be generated 40% faster

---

### Task 4.4: Standardize Test Setup/Teardown Patterns

**Scope**: All test suites  
**Severity**: Medium  
**Estimated Effort**: 8 hours

#### Current State
- Inconsistent use of beforeEach, afterEach, beforeAll, afterAll

#### Required Actions
1. Document lifecycle patterns (when to use each hook)
2. Create shared setup/teardown helpers
3. Add examples in documentation
4. Audit and update existing test suites

#### Success Criteria
- Lifecycle patterns documented
- Shared helpers created
- Existing suites updated
- Better test isolation

---

## Priority 5: Redundancy and Cleanup

### Task 5.1: Split Bloated Test Files

**Scope**: auto-revision service tests and others  
**Severity**: Low  
**Estimated Effort**: 6 hours

#### Current State
- Some test files cover too many concerns (339 lines)

#### Required Actions
1. Identify test files >200 lines
2. Split by concern (drift detection, severity, monitoring, generation)
3. Update imports and references

#### Success Criteria
- No test file exceeds 200 lines
- Better organization
- Easier maintenance

---

### Task 5.2: Remove Duplicate ID Generation Tests

**Scope**: Multiple packages  
**Severity**: Low  
**Estimated Effort**: 4 hours

#### Current State
- Same ID generation logic tested in multiple places

#### Required Actions
1. Identify duplicate ID generation tests
2. Remove duplicates from packages
3. Keep tests only in platform-events
4. Update documentation

#### Success Criteria
- Duplicate tests removed
- ID generation tested once
- Faster test runs

---

### Task 5.3: Use Parameterized Tests

**Scope**: platform-events validation tests  
**Severity**: Low  
**Estimated Effort**: 4 hours

#### Current State
- Repetitive test patterns for similar validation logic

#### Required Actions
1. Identify repetitive test patterns
2. Convert to parameterized tests using vitest's test.each
3. Add test case arrays

#### Success Criteria
- Repetitive patterns converted to parameterized tests
- More concise tests
- Easier to add new test cases

---

## Priority 6: Taxonomy/Scripts/CI Standardization

### Task 6.1: Standardize Test File Naming

**Scope**: All test files  
**Severity**: Low  
**Estimated Effort**: 4 hours

#### Current State
- Inconsistent naming (.test.ts vs .spec.ts)

#### Required Actions
1. Audit all test file names
2. Standardize on .test.ts for unit/integration
3. Standardize on .e2e.spec.ts for E2E
4. Rename files

#### Success Criteria
- Consistent naming conventions
- Clearer file organization

---

### Task 6.2: Standardize Folder Structure

**Scope**: All packages  
**Severity**: Low  
**Estimated Effort**: 6 hours

#### Current State
- Inconsistent organization (__tests__, test, tests)

#### Required Actions
1. Audit all test folder structures
2. Standardize on __tests__/unit/, __tests__/integration/, __tests__/e2e/
3. Reorganize folders
4. Update configs

#### Success Criteria
- Consistent folder structure
- Clear categorization
- Predictable test location

---

### Task 6.3: Implement Test Sharding for E2E

**Scope**: E2E test suites  
**Severity**: Low  
**Estimated Effort**: 8 hours

#### Current State
- No parallelization strategy for E2E tests

#### Required Actions
1. Configure Playwright sharding
2. Update CI to run shards in parallel
3. Add test execution time reporting
4. Optimize shard distribution

#### Success Criteria
- E2E tests sharded
- Parallel execution in CI
- E2E runtime reduced by >50%
- Execution time reporting added

---

## Task Summary by Priority

### Priority 0 (Critical) - 3 tasks
- Fix placeholder assertions in ghatana-studio (4h)
- Add codegen output validation (8h)
- Add renderer output validation (6h)
**Total Effort**: 18 hours

### Priority 1 (High) - 4 tasks
- Add contract test suites (16h)
- Add API E2E tests (24h)
- Add real DB integration tests (20h)
- Add YAPPC full workflow E2E (32h)
**Total Effort**: 92 hours

### Priority 2 (High) - 4 tasks
- Implement test taxonomy (12h)
- Add tiered test scripts (4h)
- Implement staged CI (8h)
- Reclassify mislabeled tests (6h)
**Total Effort**: 30 hours

### Priority 3 (Medium) - 3 tasks
- Add cross-service workflow tests (16h)
- Add event schema validation (8h)
- Add serialization tests (12h)
**Total Effort**: 36 hours

### Priority 4 (Medium) - 4 tasks
- Create shared fixture library (16h)
- Create shared vitest config (8h)
- Implement seed data factory (12h)
- Standardize setup/teardown (8h)
**Total Effort**: 44 hours

### Priority 5 (Low) - 3 tasks
- Split bloated test files (6h)
- Remove duplicate tests (4h)
- Use parameterized tests (4h)
**Total Effort**: 14 hours

### Priority 6 (Low) - 3 tasks
- Standardize test file naming (4h)
- Standardize folder structure (6h)
- Implement test sharding (8h)
**Total Effort**: 18 hours

### Grand Total
**Total Tasks**: 47  
**Total Estimated Effort**: 252 hours (~6 weeks with 1 FTE)

---

## Execution Roadmap

### Phase 1: Critical Fixes (Week 1)
- Complete all Priority 0 tasks (18 hours)
- Goal: Eliminate false confidence from placeholder tests

### Phase 2: Critical Coverage (Weeks 2-4)
- Complete Priority 1 tasks (92 hours)
- Goal: Add missing critical-path and workflow coverage

### Phase 3: Taxonomy & Execution (Week 5)
- Complete Priority 2 tasks (30 hours)
- Goal: Enable tiered execution and staged CI

### Phase 4: Integration & Contracts (Week 6)
- Complete Priority 3 tasks (36 hours)
- Goal: Fill integration and contract gaps

### Phase 5: Infrastructure (Week 7)
- Complete Priority 4 tasks (44 hours)
- Goal: Standardize fixtures, configs, and seeds

### Phase 6: Cleanup & Optimization (Week 8)
- Complete Priority 5 and 6 tasks (32 hours)
- Goal: Reduce redundancy and optimize execution

---

## Success Metrics

### Before Remediation
- Contract coverage: 0%
- API E2E coverage: 0%
- Real DB integration: <10%
- Test taxonomy: None
- Tiered execution: None
- Staged CI: None

### After Remediation
- Contract coverage: >95%
- API E2E coverage: >80%
- Real DB integration: >75%
- Test taxonomy: Complete
- Tiered execution: Complete
- Staged CI: Complete
- Release confidence: High

---

## Related Documents

- [Test Audit Report](./TEST_AUDIT_REPORT.md)
- [Unified Platform Implementation Task Tracker](./UNIFIED_PLATFORM_IMPLEMENTATION_TASK_TRACKER.md)
- [Coding Instructions](../../.windsurf/rules/coding-instructions.md)
