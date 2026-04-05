# Platform Test Implementation Framework
**Comprehensive Plan to Close All Test & Feature Gaps**

**Created**: 2026-04-04  
**Scope**: 47 platform modules (28 Java, 14 TypeScript, 3 contracts, 1 agent-catalog)  
**Goal**: 100% coverage, 0 🔴 markers, 0 🟡 markers in PLATFORM_TEST_AUDIT.md  
**Effort**: ~300 hours across 10 weeks (4 implementation phases)

---

## 1. Pre-Implementation Checklist (Week 1)

### 1.1 Stakeholder Sign-Off Required
- [ ] Platform Engineering Lead approval
- [ ] Architecture Team Lead approval
- [ ] QA Lead approval
- [ ] Timeline and resource allocation confirmed

### 1.2 Infrastructure Setup
- [ ] Test data factory templates created (JSON schema)
- [ ] TestContainers configuration validated for all DB/Kafka/Redis
- [ ] Mock frameworks configured (Mockito + TestContainers combo)
- [ ] CI/CD coverage gates configured (>80% minimum)
- [ ] Coverage reporting tool validated (JaCoCo for Java, nyc for TS)
- [ ] Performance testing framework selected (JMH for Java)
- [ ] Load testing framework selected (k6 or similar)
- [ ] Security testing tools configured (OWASP ZAP integration)

### 1.3 Patterns & Templates Created
- [ ] Java unit test template (with lenient stubbing pattern)
- [ ] Java integration test template (with EventloopTestBase)
- [ ] TypeScript unit test template (Vitest with strict types)
- [ ] TypeScript integration test template (Testcontainers)
- [ ] E2E test template (Playwright + API testing)
- [ ] Test data builder patterns documented
- [ ] Mock/stub patterns documented
- [ ] Error scenario patterns documented

### 1.4 Documentation Requirements
- [ ] Vision template created for modules
- [ ] Requirements template created
- [ ] Architecture decision template created
- [ ] README template for each module type

---

## 2. Module Classification & Priority

### 2.1 Tier 1: Zero-Test Modules (CRITICAL - Weeks 2-5)

**9 modules with 0% coverage — must be completed first**

#### Group A: Auth & Security (Week 2-3)

| Module | Implementation Scope | Key Features | Test Count |
|--------|---------------------|--------------|-----------|
| **identity** | Auth (JWT/OAuth2), RBAC, MFA, session management | TenantContext propagation, token lifecycle | 45 unit + 12 integration |
| **security** | Encryption, signing, key rotation, audit trails | Cipher operations, key management, compliance | 38 unit + 10 integration |
| **security-analytics** | Security event detection, anomaly detection, risk scoring | Pattern matching, threshold detection, alerting | 35 unit + 8 integration |

**Test Template Structure:**
```java
// 1. Error scenarios (invalid input, expired tokens, unauthorized)
// 2. State transitions (authenticated → unauthorized, lockout)
// 3. Integration with governance (policy enforcement)
// 4. Concurrency scenarios (token refresh race conditions)
// 5. Observability (security event logging)
```

#### Group B: Workflow & Orchestration (Week 3-4)

| Module | Implementation Scope | Key Features | Test Count |
|--------|---------------------|--------------|-----------|
| **runtime** | Service orchestration, process lifecycle, resource management | Process pools, graceful shutdown, restart logic | 42 unit + 14 integration |
| **incident-response** | Incident automation, detection, escalation, recovery | Alert routing, incident state machine, remediation | 40 unit + 12 integration |
| **policy-as-code** | Policy evaluation engine, condition evaluation, enforcement | Policy AST, evaluation strategy, caching | 48 unit + 15 integration |

**Test Template:**
```java
// 1. Policy parsing and validation
// 2. Policy evaluation with complex conditions
// 3. Policy chains and composition
// 4. Failure modes and recovery
// 5. Performance under concurrent evaluation
```

#### Group C: Extensions & Tools (Week 4-5)

| Module | Implementation Scope | Key Features | Test Count |
|--------|---------------------|--------------|-----------|
| **plugin** | Plugin loading, lifecycle, isolation, sandboxing | Plugin registry, version management, dependency resolution | 44 unit + 13 integration |
| **tool-runtime** | Tool execution, output capture, resource limits, sandboxing | Tool invocation, environment setup, result parsing | 40 unit + 11 integration |
| **observability** | Metrics, tracing, logging, health checks | Prometheus export, OpenTelemetry, structured logging | 52 unit + 16 integration |

**Test Template:**
```java
// 1. Plugin/Tool discovery and registration
// 2. Execution with resource limits
// 3. Error handling and cleanup
// 4. Integration with metrics/tracing
// 5. Security isolation and sandboxing
```

**Total Tier 1: 384 unit tests + 111 integration tests = 495 tests**

### 2.2 Tier 2: Partial-Coverage Modules (HIGH - Weeks 6-9)

**19 modules with 40-85% coverage — enhance existing coverage**

#### Group D: Core Platform (Week 6-7)

| Module | Current | Target | Gap | Test Addition |
|--------|---------|--------|-----|---|
| **core** | ~75% | 95% | Error edge cases | +15 unit tests |
| **agent-core** | ~70% | 95% | Integration flows | +20 unit + 8 integration |
| **workflow** | ~85% | 95% | Concurrent execution | +12 unit + 10 integration |
| **database** | ~80% | 95% | Failure modes | +18 unit + 12 integration |
| **governance** | ~60% | 95% | Complex policies | +25 unit + 10 integration |

#### Group E: Infrastructure (Week 7-8)

| Module | Current | Target | Gap | Test Addition |
|--------|---------|--------|-----|---|
| **http** | ~65% | 95% | WebSocket, retry/CB | +22 unit + 10 integration |
| **kernel** | ~85% | 95% | Cross-module | +18 unit + 8 integration |
| **cache** | No README | 95% | Missing everything | +35 unit + 12 integration |
| **connectors** | 70% | 95% | Edge cases | +16 unit + 8 integration |
| **audio-video** | 75% | 95% | Performance | +14 unit + 6 integration |

#### Group F: Data & Persistence (Week 8-9)

| Module | Current | Target | Gap | Test Addition |
|--------|---------|--------|-----|---|
| **agent-memory** | No tests | 95% | Memory operations | +28 unit + 10 integration |
| **audit** | No tests | 95% | Audit logging | +24 unit + 8 integration |
| **billing** | No tests | 95% | Transaction logic | +30 unit + 12 integration |
| **domain** | No tests | 95% | Entity models | +20 unit + 6 integration |
| **distributed-cache** | No tests | 95% | Cache consensus | +32 unit + 14 integration |
| **kernel-persistence** | No tests | 95% | Persistence layer | +26 unit + 10 integration |
| **data-governance** | No tests | 95% | Governance policies | +22 unit + 8 integration |
| **config** | 70% | 95% | Dynamic config | +18 unit + 8 integration |
| **ai-integration** | 60% | 95% | LLM behavior | +25 unit + 12 integration |

**Total Tier 2: 327 unit tests + 159 integration tests = 486 tests**

### 2.3 Tier 3: TypeScript Packages (MEDIUM - Weeks 10-12)

**14 packages with 40-100% coverage — add behavioral/E2E tests**

| Package | Current | Target | Gap | Test Addition |
|---------|---------|--------|-----|---|
| **design-system** | ~40% export tests | 95% | Behavioral + A11y | +85 component tests + 25 a11y + 20 integration |
| **realtime** | ~60% unit | 95% | Reconnection + ordering | +18 unit + 15 integration |
| **api** | ~70% | 95% | Middleware chains | +12 unit + 10 integration |
| **canvas** | ~70% | 95% | Performance tests | +16 unit + 12 integration |
| **theme** | ~80% | 95% | Theme switching | +10 unit + 8 integration |
| **token** | No tests | 95% | Token validation | +15 unit + 5 integration |
| **i18n** | No tests | 95% | i18n logic | +18 unit + 8 integration |
| **charts** | No tests | 95% | Chart rendering | +35 unit + 12 integration |
| **code-editor** | No tests | 95% | Editor behavior | +40 unit + 15 integration |
| **sso-client** | No tests | 95% | Token lifecycle | +22 unit + 10 integration |
| **platform-shell** | No tests | 95% | Shell layout | +28 unit + 12 integration |
| **ui-integration** | No tests | 95% | API integration | +24 unit + 10 integration |
| **accessibility-audit** | No tests | 95% | Audit logic | +20 unit + 8 integration |
| **contracts** | Partial | 95% | Schema validation | +16 unit + 6 integration |

**Total Tier 3: 359 TS tests + 172 integration tests = 531 tests**

### 2.4 Tier 4: Integration & Cross-Module (AMBITIOUS - Weeks 9-14)

**35+ cross-module interaction tests**

| Interaction | Type | Test Count |
|-------------|------|-----------|
| Agent → Workflow | E2E | 8 tests |
| Workflow → Database → Cache | E2E | 10 tests |
| Agent → AI Integration | E2E | 6 tests |
| Governance → HTTP Filters | E2E | 7 tests |
| Kernel → All modules | E2E | 12 tests |
| Design System → API Client | E2E | 6 tests |
| Realtime → API Client | E2E | 5 tests |
| Canvas → Design System | E2E | 6 tests |

**Total Tier 4: 60 E2E tests**

---

## 3. Implementation Sequencing

### 3.1 Phase 1A: Foundation (Weeks 2-3)
1. Implement `identity` module (TBD)
2. Implement `security` module (TBD)
3. Implement `security-analytics` module (TBD)
4. **Validation**: All 3 modules at 95%+ coverage, green build

### 3.1B: Workflow & Orchestration (Weeks 3-4)
5. Implement `runtime` module (TBD)
6. Implement `incident-response` module (TBD)
7. Implement `policy-as-code` module (TBD)
8. **Validation**: All 3 modules at 95%+ coverage, green build

### 3.1C: Extensions & Tools (Weeks 4-5)
9. Implement `plugin` module (TBD)
10. Implement `tool-runtime` module (TBD)
11. Implement `observability` module (TBD)
12. **Validation**: All 3 modules at 95%+ coverage, green build, E2E validation

### 3.2 Phase 2A: Core Platform (Weeks 6-7)
13. Enhance `core` module (+15 tests)
14. Enhance `agent-core` module (+28 tests)
15. Enhance `workflow` module (+22 tests)
16. Enhance `database` module (+30 tests)
17. Enhance `governance` module (+35 tests)
18. **Validation**: All at 95%+ coverage, integration tests pass

### 3.2B: Infrastructure (Weeks 7-8)
19. Enhance `http` module (+32 tests)
20. Enhance `kernel` module (+26 tests)
21. Enhance `cache` module (+47 tests)
22. Enhance `connectors` module (+24 tests)
23. Enhance `audio-video` module (+20 tests)
24. **Validation**: All at 95%+ coverage

### 3.2C: Data Layer (Weeks 8-9)
25. Implement full `agent-memory` module (+38 tests)
26. Implement full `audit` module (+32 tests)
27. Implement full `billing` module (+42 tests)
28. Implement full `domain` module (+26 tests)
29. Implement full `distributed-cache` module (+46 tests)
30. Implement full `kernel-persistence` module (+36 tests)
31. Implement full `data-governance` module (+30 tests)
32. Enhance `config` module (+26 tests)
33. Enhance `ai-integration` module (+37 tests)
34. **Validation**: All at 95%+ coverage

### 3.3 Phase 3: TypeScript Packages (Weeks 10-12)
35-48. Enhance all 14 TypeScript packages per Tier 3 above
49. **Validation**: All TypeScript at 95%+ coverage, E2E tests green

### 3.4 Phase 4: Integration & E2E (Weeks 13-14)
50-86. Implement 60 cross-module E2E tests (Tier 4)
87. **Validation**: All E2E tests green, full stack working

---

## 4. Test Implementation Patterns

### 4.1 Java Unit Test Pattern

```java
/**
 * @doc.type test
 * @doc.purpose Validates [ClassName] behavior with focus on [concern]
 * @doc.layer unit
 * @doc.pattern Test
 */
@DisplayName("[ClassName] Unit Tests")
@ExtendWith(MockitoExtension.class)
class ObjectiveTest {
    
    @Mock
    private Dependency dependency;
    
    private ClassUnderTest service;
    
    @BeforeEach
    void setUp() {
        service = new ClassUnderTest(dependency);
        // Lenient stubbing for optional mocks
        lenient().when(dependency.operation()).thenReturn(Promise.of(result));
    }
    
    @Nested
    @DisplayName("Success Scenarios")
    class SuccessScenarios {
        @Test
        void shouldHandleValidInput() {
            // GIVEN: valid input
            Input input = new Input("valid");
            
            // WHEN: operation is called
            Result result = service.operation(input);
            
            // THEN: validate output and side effects
            assertThat(result).isEqualTo(expectedResult);
            verify(dependency).validate(input);
        }
    }
    
    @Nested
    @DisplayName("Failure Scenarios")
    class FailureScenarios {
        @Test
        void shouldHandleInvalidInput() {
            // GIVEN: invalid input
            Input input = new Input(null);
            
            // WHEN/THEN: validation fails
            assertThatThrownBy(() -> service.operation(input))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("invalid");
        }
    }
    
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        @Test
        void shouldHandleNullDependency() { /* test */ }
        
        @Test
        void shouldHandleBoundaryValues() { /* test */ }
        
        @Test
        void shouldHandleConcurrentAccess() { /* test */ }
    }
}
```

### 4.2 Java Integration Test Pattern

```java
/**
 * @doc.type test
 * @doc.purpose Validates [ClassName] integration with [Dependency]
 * @doc.layer integration
 * @doc.pattern Test
 */
@DisplayName("[ClassName] Integration Tests")
@ExtendWith(MockitoExtension.class)
class IntegrationTest extends EventloopTestBase {
    
    private static final PostgreSQLContainer<?> postgreSQL = 
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"));
    
    static {
        postgreSQL.start();
    }
    
    private RealDependency realDependency;
    private ClassUnderTest service;
    
    @BeforeEach
    void setUp() {
        realDependency = new RealDependency(postgreSQL.getJdbcUrl());
        service = new ClassUnderTest(realDependency);
    }
    
    @Test
    void shouldPersistAndRetrieve() {
        Promise<Result> promise = service.operation(input);
        
        Result result = runPromise(() -> promise);
        
        assertThat(result).isNotNull();
        assertThat(realDependency.retrieve(result.id())).isEqualTo(result);
    }
    
    @AfterEach
    void tearDown() {
        realDependency.close();
    }
}
```

### 4.3 TypeScript Unit Test Pattern

```typescript
/**
 * Component/Module: [ComponentName]
 * Purpose: Validates [ComponentName] behavior
 * Layer: Unit
 * Coverage Target: 100% line, branch, function
 */

import { describe, it, beforeEach, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ComponentName, Props } from './ComponentName';

describe('ComponentName', () => {
  let mockDependency: Mock<any>;
  let props: Props;
  
  beforeEach(() => {
    mockDependency = vi.fn();
    props = {
      title: 'Test',
      onAction: vi.fn(),
    };
  });
  
  describe('Success Scenarios', () => {
    it('should render with valid props', () => {
      render(<ComponentName {...props} />);
      expect(screen.getByText('Test')).toBeInTheDocument();
    });
    
    it('should call onAction when button clicked', async () => {
      render(<ComponentName {...props} />);
      const button = screen.getByRole('button');
      await userEvent.click(button);
      expect(props.onAction).toHaveBeenCalled();
    });
  });
  
  describe('Failure Scenarios', () => {
    it('should handle missing required props', () => {
      const invalidProps = { title: undefined };
      expect(() => <ComponentName {...invalidProps} />)
        .toThrow('Title is required');
    });
  });
  
  describe('Edge Cases', () => {
    it('should handle null children', () => {
      render(<ComponentName {...props}>{null}</ComponentName>);
      expect(screen.getByText('Test')).toBeInTheDocument();
    });
    
    it('should handle long text content', () => {
      const longTitle = 'A'.repeat(1000);
      render(<ComponentName {...props} title={longTitle} />);
      expect(screen.getByText(longTitle)).toBeInTheDocument();
    });
  });
  
  describe('Accessibility', () => {
    it('should have proper ARIA labels', () => {
      render(<ComponentName {...props} />);
      expect(screen.getByRole('button')).toHaveAttribute('aria-label');
    });
    
    it('should be keyboard navigable', async () => {
      render(<ComponentName {...props} />);
      const button = screen.getByRole('button');
      button.focus();
      expect(button).toHaveFocus();
    });
  });
});
```

### 4.4 E2E Test Pattern (Playwright)

```typescript
/**
 * E2E Test: [Feature Name]
 * User Flow: [User Goal]
 * Expected Outcome: [Success Condition]
 */

import { test, expect } from '@playwright/test';

test.describe('Agent Creation Flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:3000');
    await page.fill('[data-testid="login-email"]', 'user@example.com');
    await page.fill('[data-testid="login-password"]', 'password');
    await page.click('[data-testid="login-button"]');
    await page.waitForURL('**/dashboard');
  });
  
  test('should create agent successfully', async ({ page }) => {
    // Navigate to agent creation
    await page.click('[data-testid="create-agent-button"]');
    await page.waitForURL('**/agents/new');
    
    // Fill form
    await page.fill('[data-testid="agent-name"]', 'Test Agent');
    await page.selectOption('[data-testid="agent-type"]', 'DETERMINISTIC');
    await page.click('[data-testid="create-button"]');
    
    // Verify success
    await page.waitForURL('**/agents/**');
    await expect(page.locator('[data-testid="agent-name"]')).toContainText('Test Agent');
    await expect(page.locator('[data-testid="success-message"]')).toBeVisible();
  });
  
  test('should validate required fields', async ({ page }) => {
    await page.click('[data-testid="create-agent-button"]');
    await page.click('[data-testid="create-button"]');
    
    await expect(page.locator('[data-testid="error-message"]')).toContainText('Name is required');
  });
});
```

---

## 5. Module-by-Module Implementation Checklist

### 5.1 Template for Each Module

For each of the 47 modules, follow this checklist:

**Module: [NAME]**

**Status**: [ ] Not Started | [ ] In Progress | [ ] Complete

#### Documentation (Week X)
- [ ] README.md with vision, purpose, key abstractions, usage examples
- [ ] @doc.* tags on all public classes
- [ ] Architecture decision record (if complex)
- [ ] API contracts documented
- [ ] Error codes documented

#### Unit Tests (Week X)
- [ ] Success scenarios (happy path)
- [ ] Failure scenarios (errors, exceptions)
- [ ] Edge cases (null, empty, boundary values)
- [ ] Concurrency scenarios (if applicable)
- [ ] Observability validation (logs, metrics, traces)

#### Integration Tests (Week X)
- [ ] Dependency interactions
- [ ] External system interactions (DB, cache, messaging)
- [ ] State persistence and recovery
- [ ] Failure modes and recovery
- [ ] Performance baselines

#### E2E Tests (Week X)
- [ ] Full feature workflows
- [ ] User-facing behavior
- [ ] Error scenarios
- [ ] Performance under load

#### Code Quality (Week X)
- [ ] Zero lint warnings
- [ ] 95%+ code coverage
- [ ] All types fully specified
- [ ] No `any` types
- [ ] Consistent formatting
- [ ] Build passes clean

---

## 6. Validation & CI/CD Strategy

### 6.1 Pre-Merge Validation
```bash
# Local validation before commit
./gradlew clean build \
  -x integrationTest \
  :platform:coverage \
  checkArchtect checkDoc

# Full CI validation
./gradlew clean build \
  :platform:test \
  :platform:integrationTest \
  :platform:coverage \
  :platform:jacocoTestReport \
  checkArchtect checkDoc checkSonar
```

### 6.2 Coverage Gate Enforcement
- **Minimum**: 80% overall
- **Per-module minimum**: 80% new code
- **Per-module target**: 95% where feasible
- **Failing coverage**: Blocks merge

### 6.3 Test Quality Gates
- **Zero flaky tests**: Re-run 3 times, all pass
- **Performance regression**: Max 10% slowdown
- **All tests deterministic**: No randomness, no sleep()

### 6.4 Periodic Audit Updates
- **Weekly**: Run full test suite, update coverage percentages
- **Weekly**: Run chaos tests, document failures
- **Bi-weekly**: Update PLATFORM_TEST_AUDIT.md status
- **Monthly**: Full audit report with trends

---

## 7. Resource & Timeline

### 7.1 Estimated Effort by Phase

| Phase | Weeks | Hours | Deliverables | Status |
|-------|-------|-------|--------------|--------|
| **Pre-Implementation** | 1 | 40 | Setup, templates, planning | ⏳ |
| **Tier 1: Zero-Test** | 4 | 160 | 9 modules, 495 tests | 📋 |
| **Tier 2: Partial** | 4 | 140 | 19 modules, 486 tests | 📋 |
| **Tier 3: TypeScript** | 3 | 120 | 14 packages, 531 tests | 📋 |
| **Tier 4: Integration** | 2 | 80 | 60 E2E tests | 📋 |
| **Final Validation** | 2 | 60 | Coverage 100%, all pass | 📋 |
| **TOTAL** | **16 weeks** | **600 hours** | **1,752 tests** | 📋 |

*Note: Assumes 2 engineers working full-time, some parallel work possible*

### 7.2 Team Assignments

#### Java Platform Team
- Identity module owner
- Security modules owner
- Core modules owner
- Data layer owner

#### TypeScript Platform Team
- Design system owner
- UI integration owner
- Theme/tokens owner
- Realtime/API owner

#### QA & Test Infrastructure
- Test infrastructure setup
- E2E test strategy
- Coverage reporting
- CI/CD validation

#### Architecture & Review
- Design review of test architecture
- Coverage validation
- Governance enforcement

---

## 8. Success Criteria

### 8.1 Definition of Done (Per Module)

For each module to be considered COMPLETE:

1. ✅ **Coverage**: 95%+ line coverage (minimum 80% for new code)
2. ✅ **Tests**: All test types present (unit, integration, E2E as applicable)
3. ✅ **Quality**: Zero flaky tests, all deterministic
4. ✅ **Documentation**: Vision, requirements, API contracts documented
5. ✅ **Code Quality**: Zero lint warnings, all types specified, no `any` types
6. ✅ **Build**: Clean build pass with no warnings
7. ✅ **Performance**: Baseline performance metrics established
8. ✅ **Security**: All security scenarios tested
9. ✅ **Observability**: Metrics, logging, tracing validated
10. ✅ **Accessibility**: Accessible components have WCAG 2.1 AA tests

### 8.2 Project Success Criteria

- [ ] All 47 modules with ≥95% coverage
- [ ] All 9 zero-test modules have comprehensive test suites
- [ ] All 19 partial modules enhanced to ≥95% coverage
- [ ] All 14 TypeScript packages have behavioral tests
- [ ] 60+ E2E tests for cross-module interactions
- [ ] PLATFORM_TEST_AUDIT.md completely updated with ✅ marks
- [ ] Zero 🔴 markers remaining
- [ ] Zero 🟡 markers for critical flows
- [ ] All builds green across CI/CD
- [ ] Stakeholder sign-off on completion

---

## 9. Document Updates & Milestones

### 9.1 PLATFORM_TEST_AUDIT.md Updates

As each module completes implementation:

```markdown
| Module | Progress | Coverage | Tests Added | Status |
|--------|----------|----------|------------|--------|
| identity | ✅ Week 2 | 95% | 57 | ✅ COMPLETE |
| security | ✅ Week 3 | 96% | 48 | ✅ COMPLETE |
| ...
```

### 9.2 Session Memory Updates

- Update `/memories/session/platform-test-closure-2026-04.md` with weekly progress
- Mark todos as completed
- Document any blockers or changes

### 9.3 Repository Memory Updates

- Create module-specific notes as implementation progresses
- Document patterns that worked well
- Record gotchas for future reference

---

## 10. Next Immediate Actions

### To Begin Week 1 (April 8, 2026)

1. [ ] Get stakeholder sign-off on this framework
2. [ ] Assign module owners (9 for Tier 1)
3. [ ] Create GitHub epic for test implementation work
4. [ ] Set up test data factories
5. [ ] Create test templates in shared locations
6. [ ] Schedule first module kick-off meeting
7. [ ] Queue Phase 1 module implementations

---

**Status**: ✅ Framework complete, ready for stakeholder review  
**Next Step**: Present to stakeholders for approval → Begin Week 1 implementation
